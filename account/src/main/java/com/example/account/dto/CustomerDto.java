package com.example.account.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CustomerDto {

    @NotBlank(message = "姓名不可為空")
    private String name;

    @Email(message = "Email 格式不正確")
    @NotBlank(message = "Email 不可為空")
    private String email;

    @Pattern(regexp = "(^$|[0-9]{10})", message = "手機號碼必須為 10 位數字")
    private String mobileNumber;

    private AccountDto accountDto;
}
