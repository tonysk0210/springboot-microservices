package com.example.loan.controller;

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
public class ResilienceTestController {

    // 不使用 Resilience4j，直接回傳 503，供 Gateway route Retry 單獨測試。
    @GetMapping(value = "/test-gateway-retry", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> testGatewayRetry() {
        log.warn("Loan 測試 Gateway Retry：回傳 503");
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("測試 Gateway Retry");
    }
}
