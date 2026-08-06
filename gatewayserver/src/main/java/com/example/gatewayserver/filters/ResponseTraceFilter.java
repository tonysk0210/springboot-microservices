package com.example.gatewayserver.filters;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
@Configuration
public class ResponseTraceFilter {

    private final FilterUtility filterUtility;

    /**
     * 回程（chain.filter 之後）：把 correlation-id 補到回應標頭，讓呼叫端也拿得到。
     */
    @Bean
    public GlobalFilter postGlobalFilter() {
        // 1. 先讓整條 filter 鏈跑完（去程 → 後端服務 → 回應）
        return (exchange, chain) -> chain.filter(exchange)
                // 2. then(...) 裡的程式碼要等上面結束才執行 —— 這就是「回程」
                .then(Mono.fromRunnable(() -> {
                    // 3. 從請求標頭撈 ID（RequestTraceFilter 已經確保有了）
                    HttpHeaders requestHeaders = exchange.getRequest().getHeaders();
                    String correlationId = filterUtility.getCorrelationId(requestHeaders);

                    // 4. 寫進回應標頭。回應還沒送出去，可以直接 add，不用 mutate
                    exchange.getResponse().getHeaders().add(FilterUtility.CORRELATION_ID, correlationId);
                    log.debug("已將 correlation-id 寫入回應標頭：{}", correlationId);
                }));
    }
}