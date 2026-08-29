package com.example.loan.exception;

import com.example.loan.dto.ErrorResponseDto;
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
 * 繼承 ResponseEntityExceptionHandler，讓 Spring MVC 的常見錯誤（例如驗證失敗、
 * 不支援的 HTTP 方法）也能統一回傳本專案的 ErrorResponseDto 格式，不必每種錯誤都自己處理。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Spring MVC 發生常見錯誤時會呼叫這裡，再統一改成 ErrorResponseDto 回傳。
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception,
                                                             Object body,
                                                             HttpHeaders headers,
                                                             HttpStatusCode status,
                                                             WebRequest webRequest) {

        // 前置：HTTP 回應已送出時不能再修改，直接停止處理。
        if (webRequest instanceof ServletWebRequest servletWebRequest) {
            // 取得目前的 HTTP response，檢查是否已送出。
            HttpServletResponse response = servletWebRequest.getResponse();
            if (response != null && response.isCommitted()) {
                log.warn("回應已送出，忽略此例外: {}", exception.toString());
                return null;
            }
        }

        // 1. 將例外整理成可回傳給 client 的易讀錯誤訊息。
        String message = extractMessage(exception);

        // 2. 印 log - 4xx 是 client 的 request 有問題；5xx 才印出完整錯誤堆疊以便除錯。
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
     * 將驗證例外轉成易讀的欄位錯誤訊息。
     */
    private String extractMessage(Exception exception) {
        // Request body DTO 驗證失敗。
        if (exception instanceof MethodArgumentNotValidException ex) {
            return ex.getBindingResult().getFieldErrors().stream()
                    .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                    .collect(Collectors.joining("; "));
        }
        // Request param 或 path variable 驗證失敗。
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
     * 此手機號碼已有貸款紀錄 - 400 Bad Request
     */
    @ExceptionHandler(LoanAlreadyExistsException.class)
    public ResponseEntity<ErrorResponseDto> handleLoanAlreadyExistsException(LoanAlreadyExistsException exception,
                                                                             WebRequest webRequest) {
        // 預期的 client error，不需印出 stack trace。
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
     * 找不到指定資源 - 404 Not Found
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleResourceNotFoundException(ResourceNotFoundException exception,
                                                                            WebRequest webRequest) {
        // 預期的 client error，不需印出 stack trace。
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
     * 處理未預期的系統錯誤 - 500 Internal Server Error
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleGlobalException(Exception exception,
                                                                  WebRequest webRequest) {
        log.error("其餘未被處理的 exception at [{}]: {}",
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
