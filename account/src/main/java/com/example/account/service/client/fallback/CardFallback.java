package com.example.account.service.client.fallback;

import com.example.account.dto.CardDto;
import com.example.account.service.client.CardFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * card 服務無法使用時的替代實作。完整說明見 {@link LoanFallback}。
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
