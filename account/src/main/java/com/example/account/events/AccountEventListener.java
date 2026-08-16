package com.example.account.events;

import com.example.account.dto.AccountMsgDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 帳戶建立完成後，通知 messageservice 去寄信 / 發簡訊。
 * <p>
 * 🔑 <b>為什麼要繞這一圈，不直接在 Service 裡呼叫 streamBridge.send()</b>
 * <p>
 * 原本的寫法是在 {@code @Transactional} 的方法「裡面」送訊息：
 * <pre>
 *     RabbitMQ 掛掉 → send() 丟例外 → 例外傳出 createAccount()
 *                  → 交易回滾 → 客戶和帳戶都沒建成 → API 回 500
 * </pre>
 * 也就是「通知寄不出去」害得「開戶失敗」—— 完全違背當初選非同步的理由
 * （寄信是副作用，不該拖垮主流程）。實測過：只開 MySQL 不開 RabbitMQ，
 * create-account 回 500 且資料庫是空的。
 * <p>
 * 改成 {@code AFTER_COMMIT} 之後：
 * <pre>
 *     交易先 commit（帳戶確實建好了）→ 才送訊息
 *     RabbitMQ 掛掉 → 只有通知沒發出去，帳戶還在
 * </pre>
 * <p>
 * ⚠ 這不是完美方案 —— 帳戶建好但通知永遠沒送出去的情況仍然可能發生，
 * 而且沒有重試。要「兩邊都保證」得用 outbox pattern：訊息先寫進同一個
 * 交易的資料表，另外用排程掃出來送。本專案是學習用途，停在這一層。
 * <p>
 * ⚠ {@code @TransactionalEventListener} 只在「發布事件時正處於交易中」才會被觸發。
 * 如果哪天有人在沒有交易的地方發這個事件，它會被「安靜地丟掉」——
 * 不報錯、也不執行。要改成沒交易也執行的話是 {@code fallbackExecution = true}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountEventListener {

    private final StreamBridge streamBridge;

    // ── 這個 annotation 做兩件事 ─────────────────────────────────────────────
    //  ① 誰來觸發：看「參數型別」。有人 publishEvent 一個 AccountMsgDto 就呼叫這裡。
    //     ⚠ 型別對不上就完全不會被觸發，而且不報錯。
    //  ② 什麼時候觸發：AFTER_COMMIT = 等交易 commit 成功之後。
    //     所以這裡失敗已經沒有東西可以回滾了 —— 這正是修正的重點。
    //
    //  其他可選的時間點：BEFORE_COMMIT / AFTER_ROLLBACK / AFTER_COMPLETION。
    //  ⚠ AFTER_COMMIT 本來就是預設值，明寫出來是因為「刻意選在 commit 之後」
    //    是這個類別存在的唯一理由，不該靠讀者去記預設值。
    // -------------------------------------------------------------------------
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccountCreated(AccountMsgDto msg) {
        // ⚠ 這個 try 不能省。AFTER_COMMIT 的例外「會」往上傳回呼叫端，
        //   雖然資料已經 commit 不會回滾了，但使用者還是會收到 500。
        //   吞掉它才是真正的 fire-and-forget —— 代價是失敗只留在 log 裡。
        try {
            log.info("Account 送出通知訊息到 RabbitMQ：{}", msg);
            boolean sent = streamBridge.send("accountSendCommunication-out-0", msg);
            log.info("Account 通知訊息是否送達 RabbitMQ broker：{}", sent);
        } catch (Exception e) {
            // ⚠ 帳戶已經建好了，這裡失敗「不該」讓 API 失敗。
            //   但也代表這筆通知就此遺失 —— 之後要靠 communication_sw 還是 null
            //   來找出「開戶了卻沒收到通知」的帳戶。
            log.error("通知訊息送不出去（帳戶已建立，不受影響）：{}", msg, e);
        }
    }
}
