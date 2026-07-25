package com.example.loan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

@Schema(description = "貸款資料")
@Data
public class LoanDto {

    @Schema(description = "10 位數字的手機號碼", example = "0912345678")
    @NotBlank(message = "手機號碼不得為空值或空白")
    @Pattern(regexp = "(^$|[0-9]{10})", message = "手機號碼必須為 10 位數字")
    private String mobileNumber;

    @Schema(description = "10 位數字的貸款編號，建立貸款時自動產生", example = "1123456789")
    @NotBlank(message = "貸款編號不得為空值或空白")
    @Pattern(regexp = "(^$|[0-9]{10})", message = "貸款編號必須為 10 位數字")
    private String loanNumber;

    @Schema(description = "貸款類型", example = "住宅貸款")
    @NotBlank(message = "貸款類型不得為空值或空白")
    private String loanType;

    @Schema(description = "貸款總金額", example = "100000")
    @Positive(message = "貸款總金額必須大於零")
    private int totalLoan;

    @Schema(description = "已償還金額", example = "5000")
    @PositiveOrZero(message = "已償還金額必須大於或等於零")
    private int amountPaid;

    @Schema(description = "未償還金額", example = "95000")
    @PositiveOrZero(message = "未償還金額必須大於或等於零")
    private int outstandingAmount;
}
