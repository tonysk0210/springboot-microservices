package com.example.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AccountDto {

    @Pattern(regexp = "(^$|[0-9]{10})", message = "帳號必須為 10 位數字")
    @NotBlank(message = "帳號不得為空值或空白")
    private Integer accountNumber;

    @NotBlank(message = "帳戶類型不得為空值或空白")
    private String accountType;

    @NotBlank(message = "分行地址不得為空值或空白")
    private String branchAddress;
}

