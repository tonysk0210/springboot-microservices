package com.example.loan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 對應 Config Server 從 Git configyml/loan.yml 載入的 loan.* 設定。
 */
@Schema(description = "由 Config Server 從 Git 載入的設定資訊")
@Data
@ConfigurationProperties(prefix = "loan") // 對應 loan.* 設定
public class LoanContactInfoDto {

    @Schema(description = "服務歡迎訊息", example = "歡迎使用貸款相關 API（來自 configserver / git）")
    private String message;

    @Schema(description = "native backend 專屬測試設定", example = "這個值來自 native backend")
    private String nativeProperty;

    @Schema(description = "聯絡資訊")
    private ContactDetails contactDetails;

    @Schema(description = "值班支援電話")
    private List<String> onCallSupport;

    @Schema(description = "聯絡人資訊")
    @Data
    public static class ContactDetails {

        @Schema(description = "聯絡人姓名", example = "Tony Shangkuan - Developer")
        private String name;

        @Schema(description = "聯絡信箱", example = "loan-support@bank.com")
        private String email;
    }
}
