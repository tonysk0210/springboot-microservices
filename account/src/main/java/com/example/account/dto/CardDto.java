package com.example.account.dto;

import lombok.Data;

/**
 * Card 服務回傳的資料，供 {@code CardFeignClient} 反序列化使用。
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
