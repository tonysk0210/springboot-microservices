package com.example.account.controller;

import com.example.account.dto.CustomerDto;
import com.example.account.dto.ErrorResponseDto;
import com.example.account.dto.ResponseDto;
import com.example.account.service.IAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(
        name = "帳戶 CRUD API",
        description = "帳戶與客戶資料的建立、查詢、更新、刪除"
)
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api", produces = MediaType.APPLICATION_JSON_VALUE)   // 全部 API 統一回 application/json
public class AccountController {

    private final IAccountService accountService;

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
                    content = @Content(schema = @Schema(implementation = ErrorResponseDto.class))),
            @ApiResponse(responseCode = "417", description = "刪除失敗",
                    content = @Content(schema = @Schema(implementation = ResponseDto.class)))
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

}
