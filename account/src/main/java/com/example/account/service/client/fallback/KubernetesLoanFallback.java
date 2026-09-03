package com.example.account.service.client.fallback;

import com.example.account.dto.LoanDto;
import com.example.account.service.client.KubernetesLoanFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Kubernetes Loan 呼叫失敗時回傳空資料，讓 Account 查詢仍可完成。
 */
@Slf4j
@Component
public class KubernetesLoanFallback implements KubernetesLoanFeignClient {

    @Override
    public ResponseEntity<LoanDto> fetchLoanDetails(String mobileNumber, String loadBalancingSource) {
        log.warn("Kubernetes loan 服務無法使用，貸款資料以 null 回傳（mobileNumber={}）", mobileNumber);
        return ResponseEntity.ok(null);
    }
}
