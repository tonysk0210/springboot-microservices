package com.example.account.controller;

import com.example.account.dto.AccountContactInfoDto;
import com.example.account.dto.CustomerDto;
import com.example.account.dto.ErrorResponseDto;
import com.example.account.dto.ResponseDto;
import com.example.account.service.IAccountService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(
        name = "帳戶 CRUD API",                      // Swagger UI 的分組標題，取代預設的 account-controller
        description = "帳戶與客戶資料的建立、查詢、更新、刪除"   // 分組標題下方的說明
)
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api", produces = MediaType.APPLICATION_JSON_VALUE)   // 全部 API 統一回 application/json
public class AccountController {

    private final IAccountService accountService;

    // 由 @ConfigurationProperties 綁好的設定 bean，內容來自 Config Server 的 config/account.yml
    private final AccountContactInfoDto accountContactInfoDto;

    @Operation(summary = "建立帳戶", description = "新增客戶並自動配發一組帳號")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "帳號建立成功",
                    content = @Content(schema = @Schema(implementation = ResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "欄位驗證失敗，或手機號碼已被註冊",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PostMapping("/create-account")
    public ResponseEntity<ResponseDto> createAccount(@RequestBody @Valid CustomerDto customerDto) {

        // 1. 建立帳號
        accountService.createAccount(customerDto);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new ResponseDto(
                        HttpStatus.CREATED.toString(),
                        "帳號建立成功"));
    }

    @Operation(summary = "查詢帳戶", description = "以手機號碼查詢客戶與其帳戶資料")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查詢成功",
                    content = @Content(schema = @Schema(implementation = CustomerDto.class))),
            @ApiResponse(responseCode = "400", description = "手機號碼格式不正確",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "查無此手機號碼的客戶或帳戶",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @GetMapping("/fetch-account")
    public ResponseEntity<CustomerDto> fetchAccountDetails(@RequestParam
                                                           @Pattern(regexp = "(^$|[0-9]{10})", message = "手機號碼必須為 10 位數字")
                                                           String mobileNumber) {
        // 1. 取得帳號資料
        CustomerDto customerDto = accountService.fetchAccount(mobileNumber);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(customerDto);
    }

    @Operation(summary = "更新帳戶", description = "以手機號碼為鍵，更新客戶與帳戶資料")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "更新成功",
                    content = @Content(schema = @Schema(implementation = ResponseDto.class))),
            @ApiResponse(responseCode = "417", description = "更新失敗",
                    content = @Content(schema = @Schema(implementation = ResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "欄位驗證失敗",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "查無此手機號碼的客戶或帳戶",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PutMapping("/update-account")
    public ResponseEntity<ResponseDto> updateAccountDetails(@RequestBody
                                                            @Valid
                                                            CustomerDto customerDto) {

        // 1. 更新帳號資料
        boolean isUpdated = accountService.updateAccount(customerDto);
        if (isUpdated) {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(new ResponseDto(HttpStatus.OK.toString(), "更新成功"));
        } else {
            return ResponseEntity
                    .status(HttpStatus.EXPECTATION_FAILED)
                    .body(new ResponseDto(HttpStatus.EXPECTATION_FAILED.toString(), "更新失敗"));
        }
    }

    @Operation(summary = "刪除帳戶", description = "以手機號碼刪除客戶與其帳戶資料")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "刪除成功",
                    content = @Content(schema = @Schema(implementation = ResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "手機號碼格式不正確",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "查無此手機號碼的客戶或帳戶",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @DeleteMapping("/delete-account")
    public ResponseEntity<ResponseDto> deleteAccountDetails(@RequestParam
                                                            @Pattern(regexp = "(^$|[0-9]{10})", message = "手機號碼必須為 10 位數字")
                                                            String mobileNumber) {
        boolean isDeleted = accountService.deleteAccount(mobileNumber);
        if (isDeleted) {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(new ResponseDto(HttpStatus.OK.toString(), "刪除成功"));
        } else {
            return ResponseEntity
                    .status(HttpStatus.EXPECTATION_FAILED)
                    .body(new ResponseDto(HttpStatus.EXPECTATION_FAILED.toString(), "刪除失敗"));
        }
    }

    @Operation(
            summary = "查詢服務設定資訊",
            description = "回傳 account.* 這組設定的實際生效值。設定來自 Config Server 的 "
                    + "config/account.yml；若 Config Server 沒開（本專案用 optional: 前綴，"
                    + "抓不到不會啟動失敗），本地 application.yaml 沒有這組值，欄位會是 null。"
    )
    @ApiResponse(responseCode = "200", description = "查詢成功",
            content = @Content(schema = @Schema(implementation = AccountContactInfoDto.class)))
    @GetMapping("/contact-info")
    public ResponseEntity<AccountContactInfoDto> getContactInfo() {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(accountContactInfoDto);
    }

    // 套用名為 testRetry 的重試設定；方法持續拋出例外且重試耗盡後，才呼叫 fallback。
    // ⚠ 這是 Resilience4j 原生的 @Retry（走 AOP，設定在 yaml 的 resilience4j.retry），跟 gateway 路由上的 .retry(...) 是兩套不同的東西 —— 那個是 Spring Cloud Gateway 自己的。
    //   Spring Cloud 只對 CircuitBreaker 做了抽象層，Retry 沒有，所以這裡直接用原生的。
    @Retry(name = "testRetry", fallbackMethod = "testRetryFallback")
    @GetMapping("/test-retry")
    public ResponseEntity<String> testRetry() {
        // 目前固定成功且不會拋出例外，因此實際上不會觸發重試。
        log.info("測試 retry 呼叫 testRetry()");
        throw new RuntimeException("測試 retry 呼叫 testRetry()");
        /*return ResponseEntity
                .status(HttpStatus.OK)
                .body("測試 retry 呼叫 testRetry()");*/
    }

    // 重試耗盡後走這裡。fallback 的回傳型別要跟原方法相同，並以 Throwable 接收最後的失敗原因。
    // ⚠ 回 503 而不是 200 —— 呼叫端和監控要分得出「重試都失敗了」和「正常結果」。
    public ResponseEntity<String> testRetryFallback(Throwable throwable) {
        log.warn("測試 retry 重試耗盡，回傳 fallback 預設值：{}", throwable.toString());
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("服務暫時無法使用，請稍後再試");
    }

    // 套用名為 testRateLimiter 的限流設定；額度用完時直接走 fallback，方法本體不執行。
    //
    // ⚠ 這是 Resilience4j 原生的 @RateLimiter，跟 gateway 路由上的 requestRateLimiter 不同：
    //       gateway 的      靠 KeyResolver 分人（每個 user 各自的額度），計數存 Redis
    //       這裡的          整個方法一個額度（不分誰打的），計數在記憶體
    //   所以多台 account 的話，每台各算各的 —— 這是原生版的先天限制。
    //
    // ⚠ 跟 @Retry 的差別：@Retry 是「失敗了再試」，@RateLimiter 是「太頻繁就不給打」。
    //   方法成功與否無關，純粹看呼叫頻率。
    @RateLimiter(name = "testRateLimiter", fallbackMethod = "testRateLimiterFallback")
    @GetMapping("/test-rate-limiter")
    public ResponseEntity<String> testRateLimiter() {
        log.info("測試 rate limiter 呼叫 testRateLimiter()");
        // ⚠ 這行讓「方法本身」失敗，用來對照：額度沒用完時走這裡（500），
        //   額度用完時「連進都進不來」直接走 fallback（429）——
        //   兩種狀況的狀態碼不同，一眼分得出是被限流還是方法自己爛掉。
        return ResponseEntity
                .status(HttpStatus.OK)
                .body("測試 rate limiter 呼叫 testRateLimiter()");
    }

    // 額度用完時走這裡。Throwable 會是 RequestNotPermitted。
    // ⚠ 回 429 而不是 200 —— 呼叫端和監控要分得出「被限流」和「正常結果」。
    public ResponseEntity<String> testRateLimiterFallback(Throwable throwable) {
        log.warn("測試 rate limiter 額度用完，回傳 fallback 預設值：{}", throwable.toString());
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body("請求過於頻繁，請稍後再試");
    }

}
