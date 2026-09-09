package com.example.messageservice.functions;

import com.example.messageservice.dto.AccountMsgDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Function;

/**
 * Spring Cloud Function 訊息處理器，不是 REST Controller。
 * email() 與 sms() 會依 application.yaml 串接，作為 RabbitMQ 的通知流程；
 * kafkaEmailSms() 則提供相同邏輯的 Kafka 流程。前一個函式的輸出必須接得上下一個函式的輸入。
 * 開發測試也可用 HTTP 呼叫：POST http://localhost:9010/email 或 /sms，並傳入 AccountMsgDto JSON；
 * 完整的 email() → sms() 串接由 RabbitMQ binding 觸發，{@code emailsms-in-0} 不是 HTTP URL。
 * 正式通知流程仍由 RabbitMQ／Kafka 訊息觸發。
 */
@Slf4j
@Configuration
public class MessageFunctions {

    /**
     * email 處理函式；在 RabbitMQ 流程中原樣傳給下一個 sms() 函式。
     */
    @Bean
    public Function<AccountMsgDto, AccountMsgDto> email() {
        return accountsMsgDto -> sendEmail(accountsMsgDto, "RabbitMQ");
    }

    /**
     * sms 處理函式；在 RabbitMQ 流程最後回傳帳號，送到輸出佇列通知 Account 已完成。
     */
    @Bean
    public Function<AccountMsgDto, Integer> sms() {
        return accountsMsgDto -> sendSms(accountsMsgDto, "RabbitMQ");
    }

    /**
     * Kafka 版通知處理器，使用相同的 email／sms 邏輯並標示 Kafka 來源。
     */
    @Bean
    public Function<AccountMsgDto, Integer> kafkaEmailSms() {
        return accountsMsgDto -> sendSms(sendEmail(accountsMsgDto, "Kafka"), "Kafka");
    }


    private AccountMsgDto sendEmail(AccountMsgDto accountsMsgDto, String source) {
        log.info("{} 寄送 email，內容：{}", source, accountsMsgDto);
        return accountsMsgDto;
    }

    private Integer sendSms(AccountMsgDto accountsMsgDto, String source) {
        log.info("{} 寄送 sms，內容：{}", source, accountsMsgDto);
        return accountsMsgDto.accountNumber();
    }
}
