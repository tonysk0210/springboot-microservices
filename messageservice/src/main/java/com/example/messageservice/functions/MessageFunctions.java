package com.example.messageservice.functions;

import com.example.messageservice.dto.AccountMsgDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Function;

/**
 * 訊息處理函式 —— 不是 REST 端點，是 Spring Cloud Function 的處理器。
 * <p>
 * 設定成 {@code spring.cloud.function.definition: email|sms} 之後會串成一條：
 * <pre>
 *     RabbitMQ ──▶ email() ──▶ sms() ──▶ RabbitMQ
 *     AccountMsgDto  AccountMsgDto   Integer
 * </pre>
 * 🔑 前一個的回傳型別必須是後一個的參數型別，否則啟動就失敗。
 */
@Slf4j
@Configuration
public class MessageFunctions {

    /**
     * 模擬寄信。
     * ⚠ 回傳型別刻意跟輸入一樣 —— 為了讓後面的 sms() 接得下去。
     */
    @Bean
    public Function<AccountMsgDto, AccountMsgDto> email() {
        return accountsMsgDto -> {
            log.info("寄送 email，內容：{}", accountsMsgDto);
            return accountsMsgDto;
        };
    }

    /**
     * 模擬發簡訊，並回傳帳號。
     * 🔑 這是整條鏈的最後一站，回傳值會被丟到輸出佇列，
     * 讓 account 服務知道「這個帳號通知發完了」。
     */
    @Bean
    public Function<AccountMsgDto, Integer> sms() {
        return accountsMsgDto -> {
            log.info("寄送簡訊，內容：{}", accountsMsgDto);
            return accountsMsgDto.accountNumber();
        };
    }
}
