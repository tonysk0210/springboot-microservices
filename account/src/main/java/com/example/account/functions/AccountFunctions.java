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
     * ⚠ 方法名稱 = bean 名稱 = binding 前綴，三者綁死：
     * <pre>
     *     accountReceiveCommunication            ← 這個方法名
     *     spring.cloud.function.definition       ← 要填一樣的
     *     accountReceiveCommunication-in-0        ← binding key 自動由它推導
     * </pre>
     * 改名時三處要一起改，漏一處「不會報錯」，只是訊息永遠不會進來。
     */
    @Bean
    public Consumer<Integer> accountReceiveCommunication(IAccountService accountsService) {
        return accountNumber -> {
            log.info("更新帳戶的通知狀態，帳號：{}", accountNumber);
            accountsService.updateCommunicationStatus(accountNumber);
        };
    }
}
