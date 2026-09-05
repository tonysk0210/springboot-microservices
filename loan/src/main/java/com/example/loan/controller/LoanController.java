package com.example.loan.controller;


import com.example.loan.dto.ErrorResponseDto;
import com.example.loan.dto.LoanDto;
import com.example.loan.dto.ResponseDto;
import com.example.loan.service.ILoanService;
import io.swagger.v3.oas.annotations.Parameter;
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
        name = "貸款 CRUD API",                    // Swagger UI 的分組標題，取代預設的 loan-controller
        description = "貸款資料的建立、查詢、更新、刪除"   // 分組標題下方的說明
)
@RestController
@RequestMapping(path = "/api", produces = {MediaType.APPLICATION_JSON_VALUE})
@RequiredArgsConstructor
@Slf4j
public class LoanController {

    private final ILoanService loanService;

    @Operation(summary = "建立貸款", description = "以手機號碼建立一筆貸款，貸款編號自動產生")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "貸款建立成功",
                    content = @Content(schema = @Schema(implementation = ResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "手機號碼格式不正確，或此號碼已有貸款紀錄",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PostMapping("/create-loan")
    public ResponseEntity<ResponseDto> createLoan(@RequestParam
                                                  @Pattern(regexp = "(^$|[0-9]{10})", message = "手機號碼必須為 10 位數字")
                                                  String mobileNumber) {
        // 1. 建立貸款
        loanService.createLoan(mobileNumber);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new ResponseDto(
                        HttpStatus.CREATED.toString(),
                        "貸款建立成功"));
    }

    @Operation(summary = "查詢貸款", description = "以手機號碼查詢貸款資料；可由 Account 透過 Feign 呼叫。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查詢成功",
                    content = @Content(schema = @Schema(implementation = LoanDto.class))),
            @ApiResponse(responseCode = "400", description = "手機號碼格式不正確",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "查無此手機號碼的貸款紀錄",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @GetMapping("/fetch-loan")
    public ResponseEntity<LoanDto> fetchLoanDetails(@RequestParam
                                                    @Pattern(regexp = "(^$|[0-9]{10})", message = "手機號碼必須為 10 位數字")
                                                    String mobileNumber,
                                                    // Account 用 Feign 呼叫時會帶這個 Header；未提供時標記為 direct。
                                                    @Parameter(description = "上游 Account 標示的分流來源", example = "eureka") // Swagger UI 的說明文字
                                                    @RequestHeader(name = "X-Downstream-Load-Balancing-Source", defaultValue = "direct")
                                                    String loadBalancingSource) {
        // 觀測用：記錄處理請求的執行個體，以及請求進入的路徑。
        // HOSTNAME：Kubernetes 通常是 Pod 名稱；Docker 是容器 hostname；
        // IntelliJ 會使用作業系統提供的值，沒有設定時則顯示 local。
        log.info("分流觀測：貸款查詢由執行個體(hostname)={} 處理，load-balancing 選擇來源={}",
                System.getenv().getOrDefault("HOSTNAME", "localhost"), loadBalancingSource);

        LoanDto loanDto = loanService.fetchLoan(mobileNumber);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(loanDto);
    }

    @Operation(summary = "更新貸款", description = "以貸款編號為鍵，更新貸款資料")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "貸款更新成功",
                    content = @Content(schema = @Schema(implementation = ResponseDto.class))),
            @ApiResponse(responseCode = "417", description = "貸款更新失敗",
                    content = @Content(schema = @Schema(implementation = ResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "欄位驗證失敗",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "查無此貸款編號的紀錄",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @PutMapping("/update-loan")
    public ResponseEntity<ResponseDto> updateLoanDetails(@Valid @RequestBody LoanDto loanDto) {
        // 1. 更新貸款資料
        boolean isUpdated = loanService.updateLoan(loanDto);
        if (isUpdated) {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(new ResponseDto(HttpStatus.OK.toString(), "貸款更新成功"));
        } else {
            return ResponseEntity
                    .status(HttpStatus.EXPECTATION_FAILED)
                    .body(new ResponseDto(HttpStatus.EXPECTATION_FAILED.toString(), "貸款更新失敗"));
        }
    }

    @Operation(summary = "刪除貸款", description = "以手機號碼刪除貸款資料")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "貸款刪除成功",
                    content = @Content(schema = @Schema(implementation = ResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "手機號碼格式不正確",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "查無此手機號碼的貸款紀錄",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "417", description = "貸款刪除失敗",
                    content = @Content(schema = @Schema(implementation = ResponseDto.class)))
    })
    @DeleteMapping("/delete-loan")
    public ResponseEntity<ResponseDto> deleteLoanDetails(@RequestParam
                                                         @Pattern(regexp = "(^$|[0-9]{10})", message = "手機號碼必須為 10 位數字")
                                                         String mobileNumber) {
        // 1. 刪除貸款資料
        boolean isDeleted = loanService.deleteLoan(mobileNumber);
        if (isDeleted) {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(new ResponseDto(HttpStatus.OK.toString(), "貸款刪除成功"));
        } else {
            return ResponseEntity
                    .status(HttpStatus.EXPECTATION_FAILED)
                    .body(new ResponseDto(HttpStatus.EXPECTATION_FAILED.toString(), "貸款刪除失敗"));
        }
    }

}
