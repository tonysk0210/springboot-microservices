package com.example.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "帳戶資料")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AccountDto {

    @Schema(description = "10 位數字的帳號", example = "1000000001")
    @Min(value = 1_000_000_000L, message = "帳號必須為 10 位數字")
    @NotNull(message = "帳號不得為空值") // 這裡使用 @NotNull 而不是 @NotBlank，因為帳號是 Integer 類型
    private Integer accountNumber;

    @Schema(description = "帳戶類型", example = "SAVINGS")
    @NotBlank(message = "帳戶類型不得為空值或空白")
    private String accountType;

    @Schema(description = "分行地址", example = "123 Main St, New York, USA")
    @NotBlank(message = "分行地址不得為空值或空白")
    private String branchAddress;
}
