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
 * Resilience4j Retry 與 RateLimiter 的測試端點。
 */
@Tag(name = "Resilience4j 測試 API", description = "Retry 重試與 Rate Limiter 限流的行為測試")
@Slf4j
@RestController
@RequestMapping("/api")
public class ResilienceTestController {

    // ==================== Retry 測試 ==================== 重試次數、等待時間與退避策略設定於 application.yaml 的 resilience4j.retry。
    @Operation(
            summary = "測試 Retry 重試機制",
            description = "此端點刻意拋出例外，Resilience4j 依 testRetry 設定重試；重試耗盡後回傳 503。"
    )
    @ApiResponse(responseCode = "503", description = "重試耗盡，服務暫時無法使用",
            content = @Content(mediaType = MediaType.TEXT_PLAIN_VALUE,
                    schema = @Schema(implementation = String.class),
                    examples = @ExampleObject(value = "服務暫時無法使用，請稍後再試")))
    // 自動重試 testRetry；重試耗盡後執行 testRetryFallback。這是 Account 內的 Resilience4j Retry，與 Gateway Retry 不同。
    @Retry(name = "testRetry", fallbackMethod = "testRetryFallback")
    @GetMapping(value = "/test-retry", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> testRetry() {
        // 刻意拋出例外，觸發重試與 fallback。
        log.info("測試 retry 呼叫 testRetry()");
        throw new RuntimeException("模擬服務暫時不可用，測試 Retry 機制");
    }

    // 重試耗盡後回傳 503。fallback 方法必須與原方法的參數相同，或多一個 Throwable 參數。
    public ResponseEntity<String> testRetryFallback(Throwable throwable) {
        log.warn("測試 retry 重試耗盡，回傳 fallback 預設值：{}", throwable.toString());
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("服務暫時無法使用，請稍後再試");
    }

    // ==================== RateLimiter 測試 ==================== 快速連續呼叫 /api/test-rate-limiter：有額度回傳 200，超過額度回傳 429。限流設定位於 application.yaml 的 resilience4j.ratelimiter。

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
    // 沒有額度時不執行方法本體，直接呼叫 fallback；目前額度各服務 instance 分開計算。
    @RateLimiter(name = "testRateLimiter", fallbackMethod = "testRateLimiterFallback")
    @GetMapping(value = "/test-rate-limiter", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> testRateLimiter() {
        // 請求通過限流後回傳 200。
        log.info("測試 rate limiter 呼叫 testRateLimiter()");
        return ResponseEntity.ok("請求通過 RateLimiter，服務正常回應");
    }

    // 額度用完時回傳 429。
    public ResponseEntity<String> testRateLimiterFallback(Throwable throwable) {
        log.warn("測試 rate limiter 額度用完，回傳 fallback 預設值：{}", throwable.toString());
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body("請求過於頻繁，請稍後再試");
    }
}
