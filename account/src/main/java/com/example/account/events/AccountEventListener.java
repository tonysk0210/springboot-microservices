package com.example.account.events;

import com.example.account.dto.AccountMsgDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 帳戶交易提交後，發布通知訊息給 MessageService；訊息失敗不回滾已建立的帳戶。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountEventListener {

    /**
     * 透過設定好的 output binding，將訊息發送到 RabbitMQ 與 Kafka。
     */
    private final StreamBridge streamBridge;

    // 接收 AccountMsgDto 事件，並在交易成功提交後執行。
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccountCreated(AccountMsgDto msg) {
        // 捕捉發送失敗，避免通知問題影響已完成的開戶請求；失敗只記錄在 log。
        try {
            // RabbitMQ：發送通知指令，交由 MessageService 處理。
            log.info("Account 準備發布 RabbitMQ 通知指令：{}", msg);
            boolean sent = streamBridge.send("accountSendCommunication-out-0", msg);
            log.info("RabbitMQ 通知指令是否已交給 output binding：{}", sent);

            // Kafka：發送可保留、可重播的開戶事件。
            log.info("Account 準備發布 Kafka 開戶事件：{}", msg);
            boolean published = streamBridge.send("accountSendKafkaCommunication-out-0", msg);
            log.info("Kafka 開戶事件是否已交給 output binding：{}", published);
        } catch (Exception e) {
            // ⚠ 帳戶已經建好了，這裡失敗「不該」讓 API 失敗。
            //   但也代表這筆通知就此遺失 —— 之後要靠 communication_sw 還是 null
            //   來找出「開戶了卻沒收到通知」的帳戶。
            log.error("帳戶建立後的訊息發布失敗（帳戶已建立，不受影響）：{}", msg, e);
        }
    }
}
