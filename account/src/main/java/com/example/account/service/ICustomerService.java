package com.example.account.service;

import com.example.account.dto.CustomerAccLoanCardDetailDto;

public interface ICustomerService {

    CustomerAccLoanCardDetailDto fetchCustomerAccLoanCardDetailDto(String mobileNumber);
}
