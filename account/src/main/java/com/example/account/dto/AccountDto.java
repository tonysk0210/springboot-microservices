package com.example.account.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AccountDto {

    private Integer accountNumber;

    private String accountType;

    private String branchAddress;
}

