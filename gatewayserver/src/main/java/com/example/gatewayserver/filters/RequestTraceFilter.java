package com.example.gatewayserver.filters;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 進來的請求沒有 correlation-id 就產生一個，塞進請求標頭往後傳 ——
 * 讓同一次呼叫在 Gateway / account / loan / card 的 log 能用同一個 ID 串起來。
 */
// @Order 不能省 —— ResponseTraceFilter 要讀這裡塞的 ID，本 filter 必須先跑。沒設的话两个都排到最后，相对顺序不确定 → 有時撈得到、有時 null。
@Order(1)
@Component
@Slf4j
@RequiredArgsConstructor
public class RequestTraceFilter implements GlobalFilter {

    private final FilterUtility filterUtility;

    /**
     * 去程（chain.filter 之前）：確保請求帶著 correlation-id。
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, @NonNull GatewayFilterChain chain) {

        // 1. 取出這次請求的標頭
        HttpHeaders requestHeaders = exchange.getRequest().getHeaders();

        if (isCorrelationIdPresent(requestHeaders)) {
            // 2a. 上游已經帶了就沿用，不覆蓋 —— 這樣整條鏈才是同一個 ID
            log.debug("沿用上游 Request Header 帶進來的 correlation-id：{}", filterUtility.getCorrelationId(requestHeaders));
        } else {
            // 2b. 沒有就產生一個，寫進請求標頭，回傳的是「新的 exchange」，必須接回變數（說明見 FilterUtility）
            String correlationID = generateCorrelationId();

            exchange = filterUtility.setCorrelationId(exchange, correlationID);
            log.debug("產生新的 correlation-id：{} 寫入 Request Header", correlationID);
        }

        // 3. 把（可能換過的）exchange 交給下一個 filter，最終送到後端服務
        return chain.filter(exchange);
    }

    private boolean isCorrelationIdPresent(HttpHeaders requestHeaders) {
        return filterUtility.getCorrelationId(requestHeaders) != null;
    }

    private String generateCorrelationId() {
        return java.util.UUID.randomUUID().toString();
    }

}
