package com.example.account.controller;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Resilience4j Retry 與 Rate Limiter 的學習測試端點。
 */
@Tag(name = "Resilience4j 測試 API", description = "Retry 重試與 Rate Limiter 限流的行為測試")
@Slf4j
@RestController
@RequestMapping("/api")
public class ResilienceTestController {

    // ==================== Retry 測試 API 與 fallback ====================

    @Operation(
            summary = "測試 Retry 重試機制",
            description = "此端點刻意拋出例外，Resilience4j 依 testRetry 設定重試；重試耗盡後回傳 503。"
    )
    @ApiResponse(responseCode = "503", description = "重試耗盡，服務暫時無法使用",
            content = @Content(mediaType = MediaType.TEXT_PLAIN_VALUE,
                    schema = @Schema(implementation = String.class),
                    examples = @ExampleObject(value = "服務暫時無法使用，請稍後再試")))
    // 套用名為 testRetry 的重試設定；方法持續拋出例外且重試耗盡後，才呼叫 fallback。
    // 這是 Resilience4j 原生的 @Retry，與 gateway 路由上的 .retry(...) 是兩套不同機制。
    @Retry(name = "testRetry", fallbackMethod = "testRetryFallback")
    @GetMapping(value = "/test-retry", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> testRetry() {
        // 刻意固定拋出例外，讓 Resilience4j 執行重試並測試 fallback。
        log.info("測試 retry 呼叫 testRetry()");
        throw new RuntimeException("測試 retry 呼叫 testRetry()");
    }

    // ↑ testRetry 的 fallback：重試耗盡後由 Resilience4j 呼叫。
    public ResponseEntity<String> testRetryFallback(Throwable throwable) {
        log.warn("測試 retry 重試耗盡，回傳 fallback 預設值：{}", throwable.toString());
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("服務暫時無法使用，請稍後再試");
    }

    // ==================== Rate Limiter 測試 API 與 fallback ====================

    @Operation(
            summary = "測試 Rate Limiter 限流機制",
            description = "額度足夠時回傳 200；超過 testRateLimiter 設定的額度時，直接回傳 429。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "請求通過限流檢查",
                    content = @Content(mediaType = MediaType.TEXT_PLAIN_VALUE,
                            schema = @Schema(implementation = String.class),
                            examples = @ExampleObject(value = "測試 rate limiter 呼叫 testRateLimiter()"))),
            @ApiResponse(responseCode = "429", description = "請求過於頻繁，已被限流",
                    content = @Content(mediaType = MediaType.TEXT_PLAIN_VALUE,
                            schema = @Schema(implementation = String.class),
                            examples = @ExampleObject(value = "請求過於頻繁，請稍後再試")))
    })
    // 套用名為 testRateLimiter 的限流設定；額度用完時直接走 fallback，方法本體不執行。
    // 此設定在單一 Account instance 的記憶體中計數；多個 Pod 時每個 Pod 分別計算額度。
    @RateLimiter(name = "testRateLimiter", fallbackMethod = "testRateLimiterFallback")
    @GetMapping(value = "/test-rate-limiter", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> testRateLimiter() {
        // 額度足夠時回傳 200；額度用完時直接進 fallback 並回傳 429。
        log.info("測試 rate limiter 呼叫 testRateLimiter()");
        return ResponseEntity.ok("測試 rate limiter 呼叫 testRateLimiter()");
    }

    // ↑ testRateLimiter 的 fallback：額度用完後由 Resilience4j 呼叫。
    public ResponseEntity<String> testRateLimiterFallback(Throwable throwable) {
        log.warn("測試 rate limiter 額度用完，回傳 fallback 預設值：{}", throwable.toString());
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body("請求過於頻繁，請稍後再試");
    }
}
