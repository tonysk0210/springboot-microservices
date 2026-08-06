package com.example.card.filters;

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
 * 把上游傳下來的 correlation-id 放進 MDC，讓這次請求的每一行 log 都自動帶上它。
 * <p>
 * 來源有兩種：Gateway 直接打過來，或 account 用 Feign 呼叫過來（兩邊都會帶同一組 ID）。
 * <p>
 * 完整說明見 account 的同名檔案。
 */
// 要排最前面 —— 排在後面的話，前面那些 filter 印的 log 就沒有 ID。
@Order(Ordered.HIGHEST_PRECEDENCE)
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    // 要跟 gatewayserver 的 FilterUtility.CORRELATION_ID 一模一樣，不然撈不到。
    public static final String CORRELATION_ID = "myBank-correlation-id";

    // log pattern 裡 %X{...} 用的名字。
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // 1. 從請求標頭撈上游傳下來的 ID
        String id = request.getHeader(CORRELATION_ID);

        // 2. 放進 MDC。沒帶就自己生一個 —— 直接打 :9000 繞過 Gateway 測試時也追得到
        MDC.put(MDC_KEY, (id != null) ? id : UUID.randomUUID().toString());
        try {
            // 3. 交給後面的 filter 和 Controller。這段期間印的每一行 log 都會帶上 ID
            chain.doFilter(request, response);
        } finally {
            // 4. 一定要清。Tomcat 的執行緒會重複使用，不清的話下一個請求會沿用上一個的 ID
            MDC.remove(MDC_KEY);
        }
    }
}
