package com.example.account.service.client;

import com.example.account.dto.CardDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 學習用對照客戶端：固定打 Kubernetes 的 card Service，而非透過 Eureka 尋找實例。
 */
@FeignClient(
        name = "cardKubernetes",
        contextId = "kubernetesCardFeignClient",
        url = "${kubernetes.card.base-url:http://localhost:9000}"
)
public interface KubernetesCardFeignClient {

    @GetMapping("/api/fetch-card")
    ResponseEntity<CardDto> fetchCardDetails(
            @RequestParam String mobileNumber,
            @RequestHeader("X-Load-Balancing-Source") String loadBalancingSource);
}
