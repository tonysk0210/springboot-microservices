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
import org.springframework.transaction.annotation.Transactional;

import java.util.Random;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)                                       // ← 預設 readOnly，讀取方法自動繼承
public class AccountServiceImpl implements IAccountService {

    private final AccountRepo accountRepo;
    private final CustomerRepo customerRepo;

    @Transactional
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

    @Transactional(readOnly = true)
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

    @Transactional
    @Override
    public boolean updateAccount(CustomerDto customerDto) {
        boolean isUpdated = false;
        // 1. 取得帳戶 DTO
        AccountDto accountsDto = customerDto.getAccountDto();
        // 2. 檢查帳戶 DTO 是否為空
        if (accountsDto != null) {
            // 3. 根據帳戶編號查找帳戶
            Account account = accountRepo.findById(accountsDto.getAccountNumber())
                    .orElseThrow(() ->
                            new ResourceNotFoundException("Account", "AccountNumber", accountsDto.getAccountNumber().toString())
                    );
            // 4. 將帳戶 DTO 轉換成帳戶物件並更新帳戶資料
            AccountMapper.mapToAccount(accountsDto, account);
            account = accountRepo.save(account);

            // 5. 根據客戶 ID 查找客戶
            Integer customerId = account.getCustomerId();
            Customer customer = customerRepo.findById(customerId)
                    .orElseThrow(() ->
                            new ResourceNotFoundException("Customer", "CustomerID", customerId.toString())
                    );

            // 6. 將客戶 DTO 轉換成客戶物件並更新客戶資料
            CustomerMapper.mapToCustomer(customerDto, customer);
            customerRepo.save(customer);
            isUpdated = true;
        }
        return isUpdated;
    }

    @Transactional
    @Override
    public boolean deleteAccount(String mobileNumber) {
        // 1. 根據手機號碼查找客戶
        Customer customer = customerRepo.findByMobileNumber(mobileNumber)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Customer", "mobileNumber", mobileNumber)
                );
        // 2. 刪除客戶的帳戶 & 客戶資料
        accountRepo.deleteByCustomerId(customer.getCustomerId());
        customerRepo.deleteById(customer.getCustomerId());
        return true;
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
