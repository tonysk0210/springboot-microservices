package com.example.account.service.client.fallback;

import com.example.account.dto.CardDto;
import com.example.account.service.client.CardFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Card 發生錯誤時回傳空資料，讓 Account 聚合查詢仍可完成。
 * 必須實作與 {@code CardFeignClient} 完全相同的方法簽名，Feign 才能在呼叫失敗時改用此 fallback。
 */
@Slf4j
@Component
public class CardFallback implements CardFeignClient {

    @Override
    public ResponseEntity<CardDto> fetchCardDetails(String mobileNumber, String loadBalancingSource) {
        log.warn("card 服務無法使用，信用卡資料以 null 回傳（mobileNumber={}）", mobileNumber);
        return ResponseEntity.ok(null);
    }
}
