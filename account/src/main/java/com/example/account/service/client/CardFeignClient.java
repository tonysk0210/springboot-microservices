package com.example.account.service.client;

import com.example.account.dto.CardDto;
import com.example.account.service.client.fallback.CardFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 呼叫 Card 服務的宣告式客戶端。
 * name = "card" 必須對應 Card 的 spring.application.name（Eureka 上顯示為 CARD）。
 * Feign 會依此服務名稱從 Eureka 取得實例，再由 LoadBalancer 選擇一台。
 * Eureka 未啟動且沒有可用快取時，呼叫會失敗並改由 {@link CardFallback} 回傳 null。
 * <p>
 * 發生 HTTP 錯誤、連線／逾時失敗或斷路器開啟時，會執行 {@link CardFallback}；fallback 回傳空資料，讓 Account 仍可完成整合查詢。
 */
@FeignClient(name = "card", fallback = CardFallback.class)
public interface CardFeignClient {

    // 對應 CardController 的 @RequestMapping("/api") + @GetMapping("/fetch-card")。
    // 因此完整請求路徑為 GET /api/fetch-card。
    @GetMapping("/api/fetch-card")
    ResponseEntity<CardDto> fetchCardDetails(
            @RequestParam String mobileNumber,
            // 將呼叫端傳入的來源標記（目前為 eureka 或 service-dns）寫入 Header，供下游 log 觀測；這只是追蹤資訊，不會決定實際的服務尋找或 LoadBalancer。
            @RequestHeader("X-Downstream-Load-Balancing-Source") String loadBalancingSource);
}
