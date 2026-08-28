package com.example.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "客戶資料與其帳戶")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CustomerDto {

    @Schema(description = "客戶姓名", example = "王小明")
    @NotBlank(message = "姓名不可為空")
    private String name;

    @Schema(description = "電子郵件", example = "ming@example.com")
    @Email(message = "Email 格式不正確")
    @NotBlank(message = "Email 不可為空")
    private String email;

    @Schema(description = "10 位數字的手機號碼", example = "0912345678")
    @Pattern(regexp = "(^$|[0-9]{10})", message = "手機號碼必須為 10 位數字")
    private String mobileNumber;

    @Schema(description = "此客戶的帳戶資料")
    @Valid // 確保 AccountDto 內的欄位也會被驗證
    private AccountDto accountDto;
}
