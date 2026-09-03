package com.example.account.controller;

import com.example.account.dto.CustomerAccLoanCardDetailDto;
import com.example.account.dto.ErrorResponseDto;
import com.example.account.service.ICustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "客戶整合查詢 API",
        description = "跨服務彙整：account 自己的帳戶資料，加上向 loan / card 取得的部分"
)
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api", produces = MediaType.APPLICATION_JSON_VALUE)   // 全部 API 統一回 application/json
public class CustomerController {

    private final ICustomerService customerService;

    @Operation(
            summary = "透過 Eureka 查詢客戶完整資料",
            description = "以手機號碼一次取回帳戶、貸款、信用卡三份資料。"
                    + "帳戶來自 account 自己的資料庫，貸款與信用卡經由 Feign 呼叫 loan / card 服務取得；"
                    + "下游服務無資料或暫時不可用時，會由 Feign fallback 處理，對應欄位回傳 null。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查詢成功；loan 或 card 無資料或暫時不可用時，對應欄位為 null",
                    headers = @Header(name = "X-Cross-Service-Discovery",
                            description = "跨服務查找方式，固定為 eureka",
                            schema = @Schema(type = "string", example = "eureka")),
                    content = @Content(schema = @Schema(implementation = CustomerAccLoanCardDetailDto.class))),
            @ApiResponse(responseCode = "400", description = "手機號碼格式不正確",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "查無此手機號碼的客戶或帳戶",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "500", description = "Account 發生未預期的伺服器錯誤",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @GetMapping("/fetch-customerAccLoanCardDetail-eureka")
    public ResponseEntity<CustomerAccLoanCardDetailDto> fetchCustomerAccLoanCardDetailEureka(@RequestParam
                                                                                             @Pattern(regexp = "(^$|[0-9]{10})", message = "手機號碼必須為 10 位數字")
                                                                                             String mobileNumber) {

        CustomerAccLoanCardDetailDto detailDto = customerService.fetchCustomerAccLoanCardDetailEurekaDto(mobileNumber);
        return ResponseEntity
                .status(HttpStatus.OK)
                .header("X-Cross-Service-Discovery", "eureka")
                .body(detailDto);
    }

    @Operation(
            summary = "透過 Kubernetes Service 查詢客戶完整資料",
            description = "功能與一般整合查詢相同，但 loan / card 直接透過 Kubernetes Service DNS 呼叫，"
                    + "不使用 Eureka 取得服務實例。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查詢成功；回應 Header 可確認使用 Kubernetes Service",
                    headers = @Header(name = "X-Cross-Service-Discovery",
                            description = "跨服務查找方式，固定為 kubernetes-service",
                            schema = @Schema(type = "string", example = "kubernetes-service")),
                    content = @Content(schema = @Schema(implementation = CustomerAccLoanCardDetailDto.class))),
            @ApiResponse(responseCode = "400", description = "手機號碼格式不正確",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "查無此手機號碼的客戶或帳戶",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "500", description = "Account 發生未預期的伺服器錯誤",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class)))
    })
    @GetMapping("/fetch-customerAccLoanCardDetail-k8s")
    public ResponseEntity<CustomerAccLoanCardDetailDto> fetchCustomerAccLoanCardDetailKubernetes(@RequestParam
                                                                                                 @Pattern(regexp = "(^$|[0-9]{10})", message = "手機號碼必須為 10 位數字")
                                                                                                 String mobileNumber) {
        CustomerAccLoanCardDetailDto detailDto = customerService.fetchCustomerAccLoanCardDetailKubernetesDto(mobileNumber);
        return ResponseEntity
                .status(HttpStatus.OK)
                .header("X-Cross-Service-Discovery", "kubernetes-service")
                .body(detailDto);
    }
}
