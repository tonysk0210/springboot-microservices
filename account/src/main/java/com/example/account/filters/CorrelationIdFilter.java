package com.example.account.filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 將上游的 correlation ID 放入 MDC，讓本服務的 log 自動帶上相同 ID。
 * MDC 是目前執行緒的日誌上下文，只在本服務有效；跨服務呼叫時要由 Feign interceptor 將 ID 放入 HTTP Header。
 * MDC 將 correlation ID 暫存在目前執行緒，供該執行緒執行期間的 log 使用；它不會自動跨執行緒或跨服務傳遞。
 */
// 優先執行，確保後續 Filter 與 Controller 的 log 都有 ID。
@Order(Ordered.HIGHEST_PRECEDENCE)
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    // 必須與 Gateway 使用相同的 Header 名稱。
    public static final String CORRELATION_ID = "X-Gateway-Correlation-Id";

    // logging pattern 透過 %X{X-Gateway-Correlation-Id} 讀取此值。
    public static final String MDC_KEY = "X-Gateway-Correlation-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {

        // 從請求 Header 讀取上游傳來的 ID。
        String id = request.getHeader(CORRELATION_ID);

        // 沒有 ID 時自行產生，直接呼叫本服務也能追蹤。
        MDC.put(MDC_KEY, (id != null) ? id : UUID.randomUUID().toString());
        try {
            // 後續處理期間的 log 都會帶上此 ID。
            chain.doFilter(request, response);
        } finally {
            // 清除 MDC，避免 Tomcat 重用執行緒時沿用上一個請求的 ID。
            MDC.remove(MDC_KEY);
        }
    }
}
