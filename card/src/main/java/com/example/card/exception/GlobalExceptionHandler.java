package com.example.card.exception;

import com.example.card.dto.ErrorResponseDto;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * 全域例外處理器：把例外統一轉成 ErrorResponseDto JSON 回應。
 *
 * <p>繼承 ResponseEntityExceptionHandler 以接管 Spring MVC 內建的 20 種例外（405、415、400、404…），
 * 它們都會流經 handleExceptionInternal。要客製其中一種請用 @Override，不可用 @ExceptionHandler（會啟動失敗）。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * 父類 20 種內建例外的共同出口：統一改成 ErrorResponseDto 格式
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception,
                                                             Object body,
                                                             HttpHeaders headers,
                                                             HttpStatusCode status,
                                                             WebRequest webRequest) {

        // 1. 保留父類的保護：回應已送出就不要再寫，否則會拋 IllegalStateException
        if (webRequest instanceof ServletWebRequest servletWebRequest) {
            // 取得 HttpServletResponse 以檢查回應是否已送出
            HttpServletResponse response = servletWebRequest.getResponse();
            if (response != null && response.isCommitted()) {
                log.warn("回應已送出，忽略此例外: {}", exception.toString());
                return null;
            }
        }

        String message = extractMessage(exception);

        // 2. 分級記錄：4xx 是客戶端送錯，用 warn 不印堆疊；5xx 是系統問題，用 error 並帶完整堆疊
        if (status.is5xxServerError()) {
            log.error("MVC 例外 [{}] at [{}]: {}",
                    status, webRequest.getDescription(false), message, exception);
        } else {
            log.warn("MVC 例外 [{}] at [{}]: {}",
                    status, webRequest.getDescription(false), message);
        }

        // 3. 組裝回應訊息
        ErrorResponseDto errorResponseDTO = new ErrorResponseDto(
                webRequest.getDescription(false),
                HttpStatus.valueOf(status.value()),
                message,
                LocalDateTime.now()
        );
        return new ResponseEntity<>(errorResponseDTO, headers, status);
    }

    /**
     * 驗證類例外的 getMessage() 太冗長或只有固定字串，改抽出各欄位的 defaultMessage
     */
    private String extractMessage(Exception exception) {
        // @Valid @RequestBody 驗證失敗
        if (exception instanceof MethodArgumentNotValidException ex) {
            return ex.getBindingResult().getFieldErrors().stream()
                    .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                    .collect(Collectors.joining("; "));
        }
        // @RequestParam / @PathVariable 上的驗證註解未通過
        if (exception instanceof HandlerMethodValidationException ex) {
            return ex.getParameterValidationResults().stream()
                    .flatMap(validationResult -> {
                        String fieldName = validationResult.getMethodParameter().getParameterName();
                        return validationResult.getResolvableErrors().stream()
                                .map(error -> fieldName + ": " + error.getDefaultMessage());
                    })
                    .collect(Collectors.joining("; "));
        }
        return exception.getMessage();
    }

    /**
     * 400：業務規則違反 — 手機號碼已註冊
     */
    @ExceptionHandler(CardAlreadyExistsException.class)
    public ResponseEntity<ErrorResponseDto> handleCardAlreadyExistsException(CardAlreadyExistsException exception,
                                                                             WebRequest webRequest) {
        // 預期內的業務結果，記 warn 不印堆疊
        log.warn("業務規則違反 at [{}]: {}", webRequest.getDescription(false), exception.getMessage());

        ErrorResponseDto errorResponseDTO = new ErrorResponseDto(
                webRequest.getDescription(false),
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                LocalDateTime.now()
        );
        return new ResponseEntity<>(errorResponseDTO, HttpStatus.BAD_REQUEST);
    }

    /**
     * 404：資料庫查無資源
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleResourceNotFoundException(ResourceNotFoundException exception,
                                                                            WebRequest webRequest) {
        // 預期內的業務結果，記 warn 不印堆疊
        log.warn("查無資源 at [{}]: {}", webRequest.getDescription(false), exception.getMessage());

        ErrorResponseDto errorResponseDTO = new ErrorResponseDto(
                webRequest.getDescription(false),
                HttpStatus.NOT_FOUND,
                exception.getMessage(),
                LocalDateTime.now()
        );
        return new ResponseEntity<>(errorResponseDTO, HttpStatus.NOT_FOUND);
    }

    /**
     * 500：catch-all；log 完整例外，只回通用訊息避免洩漏內部細節
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleGlobalException(Exception exception,
                                                                  WebRequest webRequest) {
        log.error("未處理 exception at [{}]: {}",
                webRequest.getDescription(false), exception.getMessage(), exception);

        ErrorResponseDto errorResponseDTO = new ErrorResponseDto(
                webRequest.getDescription(false),
                HttpStatus.INTERNAL_SERVER_ERROR,
                "系統發生錯誤，請稍後再試",
                LocalDateTime.now()
        );
        return new ResponseEntity<>(errorResponseDTO, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
