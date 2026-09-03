package com.example.account.service;

import com.example.account.dto.CustomerAccLoanCardDetailDto;

public interface ICustomerService {

    CustomerAccLoanCardDetailDto fetchCustomerAccLoanCardDetailEurekaDto(String mobileNumber);

    CustomerAccLoanCardDetailDto fetchCustomerAccLoanCardDetailKubernetesDto(String mobileNumber);
}
