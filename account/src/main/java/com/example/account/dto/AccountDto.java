package com.example.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "帳戶資料")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AccountDto {

    @Schema(description = "10 位數字的帳號", example = "1000000001")
    @Pattern(regexp = "(^$|[0-9]{10})", message = "帳號必須為 10 位數字")
    @NotBlank(message = "帳號不得為空值或空白")
    private Integer accountNumber;

    @Schema(description = "帳戶類型", example = "SAVINGS")
    @NotBlank(message = "帳戶類型不得為空值或空白")
    private String accountType;

    @Schema(description = "分行地址", example = "123 Main St, New York, USA")
    @NotBlank(message = "分行地址不得為空值或空白")
    private String branchAddress;
}
