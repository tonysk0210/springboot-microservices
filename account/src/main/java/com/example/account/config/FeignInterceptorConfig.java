package com.example.account.config;

import com.example.account.filters.CorrelationIdFilter;
import feign.RequestInterceptor;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 將 account 的 correlation ID 加入 Feign 請求，讓 loan 與 card 能追蹤同一個請求。
 */
@Configuration
public class FeignInterceptorConfig {

    /**
     * Feign 呼叫下游前，將目前請求的 correlation ID 加入 HTTP Header。
     */
    @Bean
    public RequestInterceptor correlationIdInterceptor() {
        return template -> {
            // 從 MDC 讀取 CorrelationIdFilter 儲存的 ID。
            String id = MDC.get(CorrelationIdFilter.MDC_KEY);
            if (id != null) {
                // Feign 不會自動轉發 Header，這裡明確傳給 loan／card。
                template.header(CorrelationIdFilter.CORRELATION_ID, id);
            }
        };
    }
}
