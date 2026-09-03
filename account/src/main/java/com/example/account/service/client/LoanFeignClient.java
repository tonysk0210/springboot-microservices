package com.example.account.service.client;

import com.example.account.dto.LoanDto;
import com.example.account.service.client.fallback.LoanFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 呼叫 Loan 服務的宣告式客戶端。
 * name = "loan" 必須對應 Loan 的 spring.application.name（Eureka 上顯示為 LOAN）。
 * Feign 會依此服務名稱從 Eureka 取得實例，再由 LoadBalancer 選擇一台。
 * Eureka 未啟動且沒有可用快取時，呼叫會失敗並改由 {@link LoanFallback} 回傳 null。
 * <p>
 * 發生 HTTP 錯誤、連線／逾時失敗或斷路器開啟時，會執行 {@link LoanFallback}；fallback 回傳空資料，讓 Account 仍可完成整合查詢。
 */
@FeignClient(name = "loan", fallback = LoanFallback.class)
public interface LoanFeignClient {

    // 對應 LoanController 的 @RequestMapping("/api") + @GetMapping("/fetch-loan")。
    // 因此完整請求路徑為 GET /api/fetch-loan。
    @GetMapping("/api/fetch-loan")
    ResponseEntity<LoanDto> fetchLoanDetails(
            @RequestParam String mobileNumber,
            // Feign 會將呼叫端傳入的 eureka / k8s / direct 寫入 HTTP Header，供下游 log 觀測來源；此標記只用於追蹤，不會決定實際的 LoadBalancer。
            @RequestHeader("X-Load-Balancing-Source") String loadBalancingSource);
}
