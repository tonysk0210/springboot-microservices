package com.example.account.mapper;

import com.example.account.dto.CustomerAccLoanCardDetailDto;
import com.example.account.dto.CustomerDto;
import com.example.account.entity.Customer;

public class CustomerMapper {

    /**
     * 只填客戶本身的三個欄位；accountDto / loanDto / cardDto 由呼叫端各自補上。
     */
    public static CustomerAccLoanCardDetailDto mapToCustomerAccLoanCardDetailDto(Customer customer, CustomerAccLoanCardDetailDto dto) {
        dto.setName(customer.getName());
        dto.setEmail(customer.getEmail());
        dto.setMobileNumber(customer.getMobileNumber());
        return dto;
    }

    public static CustomerDto mapToCustomerDto(Customer customer, CustomerDto customerDto) {
        customerDto.setName(customer.getName());
        customerDto.setEmail(customer.getEmail());
        customerDto.setMobileNumber(customer.getMobileNumber());
        return customerDto;
    }

    public static Customer mapToCustomer(CustomerDto customerDto, Customer customer) {
        customer.setName(customerDto.getName());
        customer.setEmail(customerDto.getEmail());
        customer.setMobileNumber(customerDto.getMobileNumber());
        return customer;
    }
}
