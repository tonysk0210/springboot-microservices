package com.example.account.controller;

import com.example.account.dto.CustomerDto;
import com.example.account.dto.ResponseDto;
import com.example.account.service.IAccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class AccountController {

    private final IAccountService accountService;

    @PostMapping("/create-account")
    public ResponseEntity<ResponseDto> createAccount(@RequestBody CustomerDto customerDto) {

        // 1. 建立帳號
        accountService.createAccount(customerDto);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new ResponseDto(
                        HttpStatus.CREATED.toString(),
                        "帳號建立成功"));
    }

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

    @PutMapping("/update-account")
    public ResponseEntity<ResponseDto> updateAccountDetails(@Valid @RequestBody CustomerDto customerDto) {

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
