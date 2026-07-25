package com.example.card.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文件的封面資料（標題、版本、聯絡人）。
 * API 清單由 springdoc 自動掃描產生，這裡只補機器掃不出來的資訊。
 * 文件位置：/swagger-ui/index.html（JSON 原始檔在 /v3/api-docs）
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Card 微服務 REST API 文件",
                description = "卡片資料的 CRUD API",
                version = "v1",   // API 版本，與 springdoc 版本無關
                contact = @Contact(
                        name = "Tony Shangkuan",
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
