package com.example.gatewayserver.filters;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 確保每個請求都有 correlation ID，讓 Gateway 與下游服務的 log 可以串聯追蹤。
 *
 * Client Request
 *      ↓
 * RequestTraceFilter
 *      ├─ 已有 ID：沿用
 *      └─ 沒有 ID：產生新的 ID，放入 Gateway 內部請求
 *      ↓
 * Gateway → 下游服務
 *      ↓
 * ResponseTraceFilter：將相同 ID 放入回應 Header
 *      ↓
 * Client Response
 * <p>
 * {@link FilterUtility} 負責共用 Header 的讀寫；本 Filter 負責處理進站請求。
 *
 * 將 ID 放進 HTTP Header，是為了讓 Gateway 轉送請求時能把同一個 ID 傳給
 * account、loan、card 等下游服務。各服務再把 ID 寫入自己的 log，遇到錯誤或
 * 延遲時即可用同一個 ID 搜尋完整呼叫鏈；回應也帶回此 ID，方便 Client 回報問題。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RequestTraceFilter implements GlobalFilter, Ordered {

    private final FilterUtility filterUtility;

    /**
     * 確保請求帶有 correlation ID 後，再交給下一個 filter。
     */
    @Override
    public @NonNull Mono<Void> filter(ServerWebExchange exchange, @NonNull GatewayFilterChain chain) {

        // 1. 讀取目前請求的標頭。
        HttpHeaders requestHeaders = exchange.getRequest().getHeaders();

        if (isCorrelationIdPresent(requestHeaders)) {
            // 上游已有 ID，沿用以維持整條呼叫鏈一致。
            log.debug("沿用上游 Request Header 帶進來的 correlation-id：{}", filterUtility.getCorrelationId(requestHeaders));
        } else {
            // 沒有 ID 就建立一個，並放入新的 exchange。
            String correlationID = generateCorrelationId();

            exchange = filterUtility.setCorrelationId(exchange, correlationID);
            log.debug("產生新的 correlation-id：{} 寫入 Request Header", correlationID);
        }

        // 2. 將處理後的 exchange 傳給下一個 filter／下游服務。
        return chain.filter(exchange);
    }

    private boolean isCorrelationIdPresent(HttpHeaders requestHeaders) {
        return filterUtility.getCorrelationId(requestHeaders) != null;
    }

    private String generateCorrelationId() {
        return java.util.UUID.randomUUID().toString();
    }

    @Override
    public int getOrder() {
        return -1; // 讓此 GlobalFilter 優先於其他 GlobalFilter 與 route filter 執行
    }
}
