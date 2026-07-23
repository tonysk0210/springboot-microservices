package com.example.account.service;

import com.example.account.dto.AccountDto;
import com.example.account.dto.CustomerDto;
import com.example.account.entity.Account;
import com.example.account.entity.Customer;
import com.example.account.exception.CustomerAlreadyExistsException;
import com.example.account.exception.ResourceNotFoundException;
import com.example.account.mapper.AccountMapper;
import com.example.account.mapper.CustomerMapper;
import com.example.account.repository.AccountRepo;
import com.example.account.repository.CustomerRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements IAccountService {

    private final AccountRepo accountRepo;
    private final CustomerRepo customerRepo;

    @Override
    public void createAccount(CustomerDto accountDto) {
        // 1. 將 CustomerDto 尋換成 Customer 物件
        Customer customer = CustomerMapper.mapToCustomer(accountDto, new Customer());

        // 2. 檢查手機號碼是否已存在
        customerRepo.findByMobileNumber(customer.getMobileNumber())
                .ifPresent(existingCustomer -> {
                    throw new CustomerAlreadyExistsException("此手機號碼已被註冊，客戶已存在");
                });

        // 3. 將 Customer 物件保存到資料庫
        Customer savedCustomer = customerRepo.save(customer);
        // 4. 創建新的帳戶並保存
        accountRepo.save(createNewAccount(savedCustomer));
    }

    @Override
    public CustomerDto fetchAccount(String mobileNumber) {
        // 1. 根據手機號碼查找客戶
        Customer customer = customerRepo.findByMobileNumber(mobileNumber)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Customer", "mobileNumber", mobileNumber)
                );

        // 2. 根據客戶 ID 查找帳戶
        Account accounts = accountRepo.findByCustomerId(customer.getCustomerId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Account", "customerId", customer.getCustomerId().toString())
                );

        // 3. 將客戶物件轉換成 CustomerDto
        CustomerDto customerDto = CustomerMapper.mapToCustomerDto(customer, new CustomerDto());
        // 4. 將帳戶物件轉換成 AccountDto 並設定到 CustomerDto 中
        customerDto.setAccountDto(AccountMapper.mapToAccountDto(accounts, new AccountDto()));
        // 5. 回傳 CustomerDto
        return customerDto;
    }


    // ///////////////
    // helper method
    // ///////////////
    private Account createNewAccount(Customer customer) {
        Account newAccount = new Account();
        newAccount.setCustomerId(customer.getCustomerId());
        int randomAccNumber = 1000000000 + new Random().nextInt(9000000);
        newAccount.setAccountNumber(randomAccNumber);
        newAccount.setAccountType("SAVINGS");
        newAccount.setBranchAddress("123 Main St, New York, USA");
        return newAccount;
    }
}
