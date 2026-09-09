package com.example.messageservice.functions;

import com.example.messageservice.dto.AccountMsgDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Function;

/**
 * Spring Cloud Function 訊息處理器，不是 REST Controller。
 * email() 與 sms() 會依 application.yaml 的設定串接，依序處理 RabbitMQ 訊息；
 * kafkaEmailSms() 則提供相同流程的 Kafka 入口。前一個函式的輸出必須接得上下一個函式的輸入。
 * 開發測試也可用 HTTP 呼叫：POST http://localhost:9010/email 或 /sms，並傳入 AccountMsgDto JSON；
 * 完整的 email() → sms() 串接由 RabbitMQ binding 觸發，{@code emailsms-in-0} 不是 HTTP URL。
 * 正式通知流程仍由 RabbitMQ／Kafka 訊息觸發。
 */
@Slf4j
@Configuration
public class MessageFunctions {

    /**
     * 模擬寄送 email，原樣傳給下一個 sms() 函式。
     */
    @Bean
    public Function<AccountMsgDto, AccountMsgDto> email() {
        return accountsMsgDto -> {
            log.info("寄送 email，內容：{}", accountsMsgDto);
            return accountsMsgDto;
        };
    }

    /**
     * 模擬寄送簡訊；流程最後回傳帳號，送到輸出佇列通知 Account 已完成。
     */
    @Bean
    public Function<AccountMsgDto, Integer> sms() {
        return accountsMsgDto -> {
            log.info("寄送 sms，內容：{}", accountsMsgDto);
            return accountsMsgDto.accountNumber();
        };
    }

    /**
     * Kafka 版通知處理器，重用 email() 與 sms() 完成同一套通知流程。
     */
    @Bean
    public Function<AccountMsgDto, Integer> kafkaEmailSms() {
        return accountsMsgDto -> {
            log.info("Kafka 開始處理 sms & email 通知，內容：{}", accountsMsgDto);
            Integer accountNumber = sms().apply(email().apply(accountsMsgDto));
            log.info("Kafka 通知處理完成，回傳帳號：{}", accountNumber);
            return accountNumber;
        };
    }
}
