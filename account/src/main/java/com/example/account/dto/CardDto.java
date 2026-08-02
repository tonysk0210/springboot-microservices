package com.example.account.dto;

import lombok.Data;

/**
 * card 服務回傳的卡片資料，供 {@code CardFeignClient} 反序列化用。
 *
 * <p>設計說明與 {@link LoanDto} 完全相同（客戶端副本、不共用 jar、不放驗證註解），
 * 詳見該類別的註解。
 */
@Data
public class CardDto {

    private String mobileNumber;

    private String cardNumber;

    private String cardType;

    private int totalLimit;

    private int amountUsed;

    private int availableAmount;
}
