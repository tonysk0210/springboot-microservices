package com.example.account.config;

import com.example.account.filters.CorrelationIdFilter;
import feign.RequestInterceptor;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign 的 interceptor 設定 —— 攔截「送出去」的請求，統一補標頭。
 */
@Configuration
public class FeignInterceptorConfig {

    /**
     * Feign 送出請求前，把 correlation-id 抄到新請求的標頭上。
     * <p>
     * ⚠ Feign 不會自動轉發標頭 —— 它送出的是一個「全新的」HTTP 請求，跟 account
     * 收到的那個毫無關係，你不明講它什麼都不帶。
     *
     * <pre>
     * 沒有這個 interceptor：                有這個 interceptor：
     *
     *   [account,AAA]                        [account,AAA]
     *        │ header 空的                        │ header: AAA
     *        ▼                                    ▼
     *   [loan,BBB]  ← 撈不到，自己生新的       [loan,AAA]
     *   [card,CCC]  ← 又一組                  [card,AAA]
     *
     *   grep AAA 只找得到 account            grep AAA 撈出整條 ✅
     * </pre>
     * <p>
     * Feign 是同步呼叫、跟 Controller 同一條執行緒，所以 MDC 撈得到。
     * ⚠ 改成 @Async 或 reactive 的話這裡會是 null，得改用其他方式傳遞。
     */
    @Bean
    public RequestInterceptor correlationIdInterceptor() {
        return template -> {
            // MDC 是 CorrelationIdFilter 放的，這裡只是抄過來
            String id = MDC.get(CorrelationIdFilter.MDC_KEY);
            if (id != null) {
                template.header(CorrelationIdFilter.CORRELATION_ID, id);
            }
        };
    }
}
