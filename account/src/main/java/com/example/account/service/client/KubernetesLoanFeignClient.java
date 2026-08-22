package com.example.account.service.client;

import com.example.account.dto.LoanDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 學習用對照客戶端：固定打 Kubernetes 的 loan Service，而非透過 Eureka 尋找實例。
 */
@FeignClient(
        name = "loanKubernetes",
        contextId = "kubernetesLoanFeignClient",
        url = "${kubernetes.loan.base-url:http://localhost:8090}"
)
public interface KubernetesLoanFeignClient {

    @GetMapping("/api/fetch-loan")
    ResponseEntity<LoanDto> fetchLoanDetails(@RequestParam String mobileNumber);
}
