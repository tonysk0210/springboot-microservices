package com.example.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 對應 Config Server 上 config/account.yml 裡 account.* 那一組設定。
 * <p>
 * 欄位名不必跟 YAML 的 kebab-case 一致，relaxed binding 會自動對應：
 * contact-details → contactDetails、on-call-support → onCallSupport。
 * <p>
 * ⚠ 刻意用「可變 class + Lombok setter」而不是 record。record 走建構子綁定，
 * 不可變物件無法被就地重新填值，/actuator/refresh 動態刷新對它不會生效。
 */
@Schema(description = "由 Config Server 注入的設定資訊")
@Data
@ConfigurationProperties(prefix = "account")
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

        @Schema(description = "聯絡信箱", example = "account-support@eazybank.com")
        private String email;
    }
}
