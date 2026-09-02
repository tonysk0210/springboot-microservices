package com.example.account.dto;

import lombok.Data;

/**
 * Loan 服務回傳的資料，供 {@code LoanFeignClient} 反序列化使用。
 */
@Data
public class LoanDto {

    private String mobileNumber;

    private String loanNumber;

    private String loanType;

    private int totalLoan;

    private int amountPaid;

    private int outstandingAmount;
}
