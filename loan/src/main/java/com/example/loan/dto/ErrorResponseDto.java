package com.example.loan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

@Schema(description = "錯誤回應")
@Data
@AllArgsConstructor
public class ErrorResponseDto {

    @Schema(description = "發生錯誤的 API 路徑", example = "uri=/api/fetch-loan")
    private String apiPath;

    @Schema(description = "HTTP 狀態碼", example = "404 NOT_FOUND")
    private HttpStatus errorCode;

    @Schema(description = "錯誤訊息", example = "找不到 Loan，mobileNumber 為 '0912345678'")
    private String errorMessage;

    @Schema(description = "錯誤發生時間", example = "2026-07-25T10:30:00")
    private LocalDateTime errorTime;
}
