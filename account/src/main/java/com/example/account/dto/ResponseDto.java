package com.example.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "操作結果回應")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResponseDto {

    @Schema(description = "HTTP 狀態碼", example = "201 CREATED")
    private String statusCode;

    @Schema(description = "結果訊息", example = "帳號建立成功")
    private String statusMsg;
}
