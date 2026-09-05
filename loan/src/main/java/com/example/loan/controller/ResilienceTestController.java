package com.example.loan.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gateway Retry 的測試端點。
 * 每次 Gateway 重試都會重新呼叫此端點；可透過 Loan console log 的次數，
 * 驗證首次呼叫加上最多 3 次重試，共最多 4 次請求。
 */
@Slf4j
@RestController
@RequestMapping("/api")
@Tag(name = "Gateway Retry 測試 API", description = "測試 Gateway 對 Loan 服務的自動重試")
public class ResilienceTestController {

    // 不使用 Resilience4j，直接回傳 503，供 Gateway route Retry 單獨測試。
    @Operation(
            summary = "測試 Gateway Retry",
            description = "端點固定回傳 503；透過 Gateway 呼叫時，可從 Loan console log 驗證自動重試。"
    )
    @ApiResponse(
            responseCode = "503",
            description = "模擬 Loan 服務暫時不可用",
            content = @Content(
                    mediaType = MediaType.TEXT_PLAIN_VALUE,
                    schema = @Schema(implementation = String.class),
                    examples = @ExampleObject(value = "測試 Gateway Retry")
            )
    )
    @GetMapping(value = "/test-gateway-retry", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> testGatewayRetry() {
        log.warn("Loan 測試 Gateway Retry：回傳 503");
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("測試 Gateway Retry");
    }
}
