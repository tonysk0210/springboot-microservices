package com.example.account.service.client.fallback;

import com.example.account.dto.CardDto;
import com.example.account.service.client.KubernetesCardFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Kubernetes Card 呼叫失敗時回傳空資料，讓 Account 查詢仍可完成。
 */
@Slf4j
@Component
public class KubernetesCardFallback implements KubernetesCardFeignClient {

    @Override
    public ResponseEntity<CardDto> fetchCardDetails(String mobileNumber, String loadBalancingSource) {
        log.warn("Kubernetes card 服務無法使用，信用卡資料以 null 回傳（mobileNumber={}）", mobileNumber);
        return ResponseEntity.ok(null);
    }
}
