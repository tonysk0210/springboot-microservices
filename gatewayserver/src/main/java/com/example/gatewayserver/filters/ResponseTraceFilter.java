package com.example.gatewayserver.filters;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import reactor.core.publisher.Mono;

/**
 * 處理下游回應，將 RequestTraceFilter 使用的 correlation ID 加入回應 Header。
 */
@Slf4j
@RequiredArgsConstructor
@Configuration
public class ResponseTraceFilter {

    private final FilterUtility filterUtility;

    /**
     * 套用到所有路由，讓呼叫端也能收到 correlation ID。
     */
    @Bean
    public GlobalFilter postGlobalFilter() {
        return (exchange, chain) -> {
            // RequestTraceFilter 已將 ID 放在請求 Header，先把它讀出來。
            HttpHeaders requestHeaders = exchange.getRequest().getHeaders();
            String correlationId = filterUtility.getCorrelationId(requestHeaders);

            // 在回應送出前寫入 Header，避免 response 已 committed 而無法修改。
            exchange.getResponse().beforeCommit(() -> {
                exchange.getResponse().getHeaders().set(FilterUtility.CORRELATION_ID, correlationId);
                log.debug("已將 correlation-id 寫入 Response Header (X-GatewayServer-custom-correlation-id)：{}", correlationId);
                return Mono.empty();
            });

            // 將請求交給後續 Filter，並轉送到下游服務。
            return chain.filter(exchange);
        };
    }
}
