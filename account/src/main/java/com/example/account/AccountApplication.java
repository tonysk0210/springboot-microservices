package com.example.account;

import com.example.account.dto.AccountContactInfoDto;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

// OpenAPI 文件的封面資料放在 config/OpenApiConfig
@SpringBootApplication
@EnableJpaAuditing(auditorAwareRef = "auditAwareImpl")
// 把 @ConfigurationProperties 類別註冊成 bean，否則注入時會找不到
@EnableConfigurationProperties(AccountContactInfoDto.class)
public class AccountApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountApplication.class, args);
    }

}
