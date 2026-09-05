package com.example.gatewayserver.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 處理斷路器 Circuit Breaker 的內部 fallback 請求。
 */
@RestController
public class FallbackController {

    @RequestMapping("/contactSupport")
    public Mono<ResponseEntity<String>> contactSupport() {
        return Mono.just(ResponseEntity
                // 1. 以 503 明確表示下游服務暫時不可用。
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                // 2. 建議呼叫端 10 秒後重試。
                .header("Retry-After", "10")
                // 3. 指定 UTF-8 以正確顯示中文。
                .contentType(new MediaType(MediaType.TEXT_PLAIN, java.nio.charset.StandardCharsets.UTF_8))
                .body("系統忙碌中，請稍後再試，或聯絡客服協助處理。"));
    }
}
