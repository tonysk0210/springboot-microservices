package com.example.account.service.client;

import com.example.account.dto.LoanDto;
import com.example.account.service.client.fallback.KubernetesLoanFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 直接呼叫 loan Service，不透過 Eureka 尋找實例。
 * Compose／Kubernetes 由環境變數提供 {@code http://loan:8090}；
 * IntelliJ 未設定時使用預設的 {@code http://localhost:8090}。
 * 因此 Eureka 未啟動時仍可呼叫 (不走 Eureka)，但 loan Service 必須可連線；失敗時由 fallback 回傳 null。不影響 Account 聚合查詢的完成。
 */
@FeignClient(
        name = "loanKubernetes",
        contextId = "kubernetesLoanFeignClient",
        url = "${kubernetes.loan.base-url:http://localhost:8090}",
        fallback = KubernetesLoanFallback.class
)
public interface KubernetesLoanFeignClient {

    @GetMapping("/api/fetch-loan")
    ResponseEntity<LoanDto> fetchLoanDetails(
            @RequestParam String mobileNumber,
            @RequestHeader("X-Downstream-Load-Balancing-Source") String loadBalancingSource);
}
