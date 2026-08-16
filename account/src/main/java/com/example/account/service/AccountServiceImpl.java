package com.example.account.service;

import com.example.account.dto.AccountDto;
import com.example.account.dto.AccountMsgDto;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)                                       // ← 預設 readOnly，讀取方法自動繼承
public class AccountServiceImpl implements IAccountService {

    private final AccountRepo accountRepo;
    private final CustomerRepo customerRepo;

    /**
     * 主動送訊息到 RabbitMQ 用的。
     * <p>
     * 🔑 跟 messageservice 那種「宣告 Function bean 等訊息上門」相反 ——
     * 這裡是我們自己決定何時要送，所以用 StreamBridge。
     */
    private final StreamBridge streamBridge;

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
        Account savedAccount = accountRepo.save(createNewAccount(savedCustomer));
        // 5. 通知 messageservice 去寄信 / 發簡訊（非同步，不等它做完）
        sendCommunication(savedAccount, savedCustomer);
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


    /**
     * 收到 messageservice 的回報後，把帳戶標記成「已通知」。
     * <p>
     * ⚠ 這裡「不是」用 orElseThrow —— 訊息是非同步來的，帳號可能已經被刪掉了，
     * 那不算錯誤。拋例外的話訊息會被退回 queue 一直重試，變成無限迴圈。
     */
    @Transactional
    @Override
    public boolean updateCommunicationStatus(Integer accountNumber) {
        // 1. 檢查帳戶編號是否為空
        if (accountNumber == null) {
            return false;
        }
        // 2. 根據帳戶編號查找帳戶
        return accountRepo.findById(accountNumber)
                .map(account -> {
                    // 3. 將帳戶標記成「已通知」
                    account.setCommunicationSw(true);
                    // 4. 更新帳戶資料
                    accountRepo.save(account);
                    log.info("帳號 {} 已標記為「已通知」", accountNumber);
                    return true;
                })
                .orElseGet(() -> {
                    log.warn("收到通知回報，但找不到帳號 {} —— 可能已被刪除，略過", accountNumber);
                    return false;
                });
    }

    // ///////////////
    // helper method
    // ///////////////

    /**
     * 丟一則「請通知這位客戶」的訊息到 RabbitMQ。
     * <p>
     * ⚠ 第一個參數必須跟 application.yaml 的 binding 名稱
     * {@code spring.cloud.stream.bindings.accountSendCommunication-out-0} 完全一致。
     * 打錯字「不會報錯」—— StreamBridge 會臨時建一個同名的 binding，
     * 訊息就被送到一個沒人監聽的 exchange，安靜地消失。
     * <p>
     * ⚠ 這裡是在交易「還沒 commit」時就送出去的。如果後續發生例外導致
     * rollback，訊息已經飛出去了，會通知一個實際上不存在的帳戶。
     * 正式系統要嘛用 {@code @TransactionalEventListener(AFTER_COMMIT)}，
     * 要嘛把訊息先寫進同一個交易的資料表再由排程送出（outbox pattern）。
     * 本專案是學習用途，維持最簡單的寫法。
     * <p>
     * ⚠ 回傳的 boolean 只代表「有沒有交給 RabbitMQ」，不代表對方處理成功 ——
     * 那正是非同步的本質：送出去就不管了。
     */
    private void sendCommunication(Account account, Customer customer) {
        AccountMsgDto msg = new AccountMsgDto(
                account.getAccountNumber(), customer.getName(),
                customer.getEmail(), customer.getMobileNumber());

        log.info("Account 送出通知訊息到 RabbitMQ：{}", msg);
        // 送訊息到 RabbitMQ，交給 messageservice 去寄信 / 發簡訊
        boolean sent = streamBridge.send("accountSendCommunication-out-0", msg); // ← 這個名稱要跟 Account application.yaml 的 binding 名稱完全一致
        log.info("Account 通知訊息是否送達 RabbitMQ broker：{}", sent);
    }

    private Account createNewAccount(Customer customer) {
        Account newAccount = new Account();
        newAccount.setCustomerId(customer.getCustomerId());
        int randomAccNumber = 1_000_000_000 + new Random().nextInt(9_000_000);
        newAccount.setAccountNumber(randomAccNumber);
        newAccount.setAccountType("SAVINGS");
        newAccount.setBranchAddress("123 Main St, New York, USA");
        return newAccount;
    }
}
