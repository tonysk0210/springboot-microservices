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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements IAccountService {

    private final AccountRepo accountRepo;
    private final CustomerRepo customerRepo;

    /**
     * 發布「帳戶建立完成」事件。
     * <p>
     * 🔑 這裡「不」直接送 RabbitMQ —— 真正送訊息的是
     * {@link com.example.account.events.AccountEventListener}，它掛在
     * {@code AFTER_COMMIT}，等交易 commit 成功才動作。
     * 這樣 RabbitMQ 掛掉時只會少一則通知，不會害開戶失敗（原因見那個類別）。
     */
    private final ApplicationEventPublisher events;

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
     * 發布「帳戶建立完成」事件。
     * <p>
     * 🔑 這裡「只是登記」，不會馬上送出去 —— 事件被暫存著，等外層的交易
     * commit 成功之後，{@link com.example.account.events.AccountEventListener}
     * 才會真的送到 RabbitMQ。
     * <p>
     * ⚠ 所以這個方法「不會失敗」，即使 RabbitMQ 掛著也一樣。
     * 這正是修正的重點：通知寄不出去不該害開戶失敗。
     */
    private void sendCommunication(Account account, Customer customer) {
        AccountMsgDto msg = new AccountMsgDto(
                account.getAccountNumber(), customer.getName(),
                customer.getEmail(), customer.getMobileNumber());

        // 這一行是「留言」不是「行動」—— 什麼都還沒送出去。
        //   ① Spring 看 msg 的型別（AccountMsgDto）
        //   ② 找到參數型別相符的監聽器 AccountEventListener.onAccountCreated
        //   ③ 那個監聽器標了 AFTER_COMMIT，所以先記著、不執行
        //   ④ 等交易 commit 成功之後，Spring 才回頭呼叫它送 RabbitMQ
        events.publishEvent(msg);
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
