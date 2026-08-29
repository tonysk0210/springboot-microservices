package com.example.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 對應 Config Server 從 Git configyml/account.yml 載入的 account.* 設定。
 */
@Schema(description = "由 Config Server 注入的設定資訊")
@Data
@ConfigurationProperties(prefix = "account") // 這個 prefix 對應 config/account.yml 裡的 account: 開頭的那一組設定
public class AccountContactInfoDto {

    @Schema(description = "服務歡迎訊息", example = "歡迎使用帳戶相關 API（來自 configserver / classpath）")
    private String message;

    @Schema(description = "聯絡資訊")
    private ContactDetails contactDetails;

    @Schema(description = "值班支援電話")
    private List<String> onCallSupport;

    @Schema(description = "聯絡人資訊")
    @Data
    public static class ContactDetails {

        @Schema(description = "聯絡人姓名", example = "Tony Shangkuan - Developer")
        private String name;

        @Schema(description = "聯絡信箱", example = "account-support@bank.com")
        private String email;
    }
}
