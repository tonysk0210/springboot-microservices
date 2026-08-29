package com.example.account.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文件的基本資訊；API 清單由 Springdoc 自動掃描產生。
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Account 微服務 REST API 文件",
                description = "帳戶 CRUD、跨服務整合查詢與 Resilience4j 測試 API",
                version = "v1",   // API 版本，與 springdoc 版本無關
                contact = @Contact(
                        name = "Anthony Shangkuan",
                        email = "anthony.shangkuan@gmail.com"
                ),
                license = @License(
                        name = "Apache 2.0",
                        url = "https://www.apache.org/licenses/LICENSE-2.0"
                )
        )
)
public class OpenApiConfig {
}
