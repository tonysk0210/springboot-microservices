package com.example.account.controller;

import com.example.account.dto.AccountContactInfoDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 提供目前服務實際綁定的設定資訊。 */
@Tag(name = "服務設定 API", description = "查詢由 Config Server 提供的服務設定")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
public class AccountConfigController {

    private final AccountContactInfoDto accountContactInfoDto;

    @Operation(
            summary = "查詢服務設定資訊",
            description = "回傳目前綁定的 account.* 設定。Config Server 從 Git 的 "
                    + "configyml/account.yml 載入；本機連不上時仍可啟動，若其他設定來源也未提供值，欄位才會是 null。"
    )
    @ApiResponse(responseCode = "200", description = "查詢成功",
            content = @Content(schema = @Schema(implementation = AccountContactInfoDto.class)))
    @GetMapping("/contact-info")
    public ResponseEntity<AccountContactInfoDto> getContactInfo() {
        return ResponseEntity.ok(accountContactInfoDto);
    }
}
