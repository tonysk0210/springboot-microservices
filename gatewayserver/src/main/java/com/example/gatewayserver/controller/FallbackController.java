package com.example.gatewayserver.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Fallback API", description = "下游服務無法使用時的統一備援回應")
public class FallbackController {

    @Operation(
            summary = "回傳服務暫時不可用訊息",
            description = "當下游服務連線失敗或斷路器開啟時，由 Gateway 轉送至此 fallback endpoint。"
    )
    @ApiResponse(
            responseCode = "503",
            description = "下游服務暫時無法使用",
            content = @Content(
                    mediaType = MediaType.TEXT_PLAIN_VALUE,
                    schema = @Schema(type = "string", example = "系統忙碌中，請稍後再試，或聯絡客服協助處理。")
            )
    )
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
