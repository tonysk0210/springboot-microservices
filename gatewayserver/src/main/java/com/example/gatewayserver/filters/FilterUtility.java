package com.example.gatewayserver.filters;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.util.List;

/**
 * correlation-id 的讀寫工具，給 RequestTraceFilter 和 ResponseTraceFilter 共用。
 * <p>
 * ServerWebExchange = 一次呼叫的「請求 + 回應」打包成一個物件（相當於 Servlet 的 request + response 合體），另外還有 attributes Map 存路由等內部資料。
 * <p>
 * 請求不可變、回應可變 —— 這是兩個 filter 寫法不同的原因，不是風格差異：請求已經送來了改不了，要用 mutate() 複製一份；回應還沒送出，可以直接 add。
 */
@Component
public class FilterUtility {

    // 名字要跟下游服務講好 —— 它們也要用同一個字串才撈得到。
    public static final String CORRELATION_ID = "myBank-correlation-id";

    /**
     * 從請求標頭撈 correlation-id，沒有就回 null。
     */
    public String getCorrelationId(HttpHeaders requestHeaders) {
        // 標頭是「一個名字對多個值」，所以拿到的是 List，取第一個。
        List<String> values = requestHeaders.get(CORRELATION_ID);
        return (values != null) ? values.stream().findFirst().orElse(null) : null;
    }

    /**
     * 把 correlation-id 寫進請求標頭，回傳「新的」exchange —— 呼叫端一定要接回傳值。
     */
    public ServerWebExchange setCorrelationId(ServerWebExchange exchange, String correlationId) {
        return exchange.mutate()
                .request(exchange.getRequest().mutate().header(CORRELATION_ID, correlationId).build())
                .build();
    }

}
