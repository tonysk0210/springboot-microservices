package com.example.account.functions;

import com.example.account.service.IAccountService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

@Slf4j
@Configuration
public class AccountFunctions {

    /**
     * 接收 RabbitMQ 的通知完成訊息，更新 Account 狀態。Bean 名稱需與 definition 和 binding 前綴一致。
     */
    @Bean
    public Consumer<Integer> accountReceiveCommunication(IAccountService accountsService) {
        return accountNumber -> {
            log.info("收到 RabbitMQ 通知完成訊息，更新帳戶狀態，帳號：{}", accountNumber);
            accountsService.updateCommunicationStatus(accountNumber);
        };
    }

    /**
     * 接收 MessageService 透過 Kafka 回傳的通知完成事件。
     */
    @Bean
    public Consumer<Integer> accountReceiveKafkaCommunication(IAccountService accountsService) {
        return accountNumber -> {
            log.info("收到 Kafka 通知完成事件，更新帳戶狀態，帳號：{}", accountNumber);
            accountsService.updateCommunicationStatus(accountNumber);
        };
    }
}
