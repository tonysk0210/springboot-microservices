package com.example.card.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

@Schema(description = "卡片資料")
@Data
public class CardDto {

    @Schema(description = "10 位數字的手機號碼", example = "0912345678")
    @NotBlank(message = "手機號碼不得為空值或空白")
    @Pattern(regexp = "(^$|[0-9]{10})", message = "手機號碼必須為 10 位數字")
    private String mobileNumber;

    @Schema(description = "10 位數字的卡號，建立卡片時自動產生", example = "1123456789")
    @NotBlank(message = "卡號不得為空值或空白")
    @Pattern(regexp = "(^$|[0-9]{10})", message = "卡號必須為 10 位數字")
    private String cardNumber;

    @Schema(description = "卡片類型", example = "Credit Card")
    @NotBlank(message = "卡片類型不得為空值或空白")
    private String cardType;

    @Schema(description = "信用額度", example = "100000")
    @Positive(message = "信用額度必須大於零")
    private int totalLimit;

    @Schema(description = "已使用金額", example = "5000")
    @PositiveOrZero(message = "已使用金額必須大於或等於零")
    private int amountUsed;

    @Schema(description = "可用金額", example = "95000")
    @PositiveOrZero(message = "可用金額必須大於或等於零")
    private int availableAmount;

}
