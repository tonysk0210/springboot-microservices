package com.example.gatewayserver.filters;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.util.List;

/**
 * 共用 correlation ID Header 的讀取與寫入工具，供兩個追蹤 Filter 使用。
 */
@Component
public class FilterUtility {

    // Gateway 與下游服務必須使用相同的 Header 名稱。
    public static final String CORRELATION_ID = "X-GatewayServer-custom-correlation-id";

    /**
     * 從請求 Header 讀取 correlation ID；不存在時回傳 null。
     */
    public String getCorrelationId(HttpHeaders requestHeaders) {
        // 同一個 Header 可能有多個相同的 key，這裡只取第一個。
        List<String> values = requestHeaders.get(CORRELATION_ID);
        return (values != null) ? values.stream().findFirst().orElse(null) : null;
    }

    /**
     * 將 correlation ID 寫入請求，並回傳新的 exchange。
     */
    public ServerWebExchange setCorrelationId(ServerWebExchange exchange, String correlationId) {
        // Request 在 WebFlux、Servlet 都不能直接修改，所以必須使用 mutate() 建立副本。
        return exchange.mutate()
                .request(exchange.getRequest().mutate().header(CORRELATION_ID, correlationId).build())
                .build();
    }

}
