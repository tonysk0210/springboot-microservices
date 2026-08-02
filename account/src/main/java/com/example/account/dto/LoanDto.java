package com.example.account.dto;

import lombok.Data;

/**
 * loan 服務回傳的貸款資料，供 {@code LoanFeignClient} 反序列化用。
 *
 * <p>這是 loan 那份 {@code com.example.loan.dto.LoanDto} 的「客戶端副本」，兩者刻意不共用。
 * 🔑 不要為了不重複就抽成共用 jar —— 那會讓所有服務綁在同一個版本上，
 * 等於把微服務退化成分散式的單體。代價是欄位名要人工同步，改錯只會在執行期得到 null。
 *
 * <p>⚠ 刻意不放驗證註解：驗證是收資料那方的責任，loan 的 Controller 已經做了。
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
