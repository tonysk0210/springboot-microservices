package com.example.loan.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class LoanDto {

    @NotBlank(message = "手機號碼不得為空值或空白")
    @Pattern(regexp = "(^$|[0-9]{10})", message = "手機號碼必須為 10 位數字")
    private String mobileNumber;

    @NotBlank(message = "貸款編號不得為空值或空白")
    @Pattern(regexp = "(^$|[0-9]{12})", message = "貸款編號必須為 12 位數字")
    private String loanNumber;

    @NotBlank(message = "貸款類型不得為空值或空白")
    private String loanType;

    @Positive(message = "貸款總金額必須大於零")
    private int totalLoan;

    @PositiveOrZero(message = "已償還金額必須大於或等於零")
    private int amountPaid;

    @PositiveOrZero(message = "未償還金額必須大於或等於零")
    private int outstandingAmount;
}
