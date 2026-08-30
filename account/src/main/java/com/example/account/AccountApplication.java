package com.example.account;

import com.example.account.dto.AccountContactInfoDto;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing(auditorAwareRef = "auditAwareImpl") // 開啟整套 auditing 功能；auditAwareImpl 指定「誰建立／修改資料」要詢問哪一個 Bean
@EnableConfigurationProperties(AccountContactInfoDto.class) // 把 @ConfigurationProperties 類別註冊成 bean，否則注入時會找不到
@EnableFeignClients
public class AccountApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountApplication.class, args);
    }

}
