package com.example.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 客戶的完整資料 —— account 自己的資料，加上經由 Feign 向 loan / card 取得的部分。
 *
 * <p>⚠ 這是「只出不進」的回應 DTO，所以刻意不放驗證註解
 * （對照 {@link CustomerDto} 有放，因為它同時當建立帳戶的請求 body 用）。
 *
 * <p>⚠ loanDto / cardDto 可能是 null —— 該客戶沒有貸款或信用卡時，
 * 對方會回 404，account 這邊要決定是「整支失敗」還是「留 null 照樣回傳」。
 */
@Schema(description = "客戶的帳戶、貸款、信用卡完整資料")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CustomerAccLoanCardDetailDto {

    @Schema(description = "客戶姓名", example = "王小明")
    private String name;

    @Schema(description = "電子郵件", example = "ming@example.com")
    private String email;

    @Schema(description = "10 位數字的手機號碼", example = "0912345678")
    private String mobileNumber;

    @Schema(description = "此客戶的帳戶資料（來自 account 自己的資料庫）")
    private AccountDto accountDto;

    @Schema(description = "此客戶的貸款資料（經 Feign 向 loan 服務取得）")
    private LoanDto loanDto;

    @Schema(description = "此客戶的信用卡資料（經 Feign 向 card 服務取得）")
    private CardDto cardDto;
}
