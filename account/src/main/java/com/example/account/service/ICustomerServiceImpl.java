package com.example.account.service;

import com.example.account.dto.AccountDto;
import com.example.account.dto.CardDto;
import com.example.account.dto.CustomerAccLoanCardDetailDto;
import com.example.account.dto.LoanDto;
import com.example.account.entity.Account;
import com.example.account.entity.Customer;
import com.example.account.exception.ResourceNotFoundException;
import com.example.account.mapper.AccountMapper;
import com.example.account.mapper.CustomerMapper;
import com.example.account.repository.AccountRepo;
import com.example.account.repository.CustomerRepo;
import com.example.account.service.client.CardFeignClient;
import com.example.account.service.client.LoanFeignClient;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ICustomerServiceImpl implements ICustomerService {

    private final AccountRepo accountRepo;
    private final CustomerRepo customerRepo;
    private final CardFeignClient cardFeignClient;
    private final LoanFeignClient loanFeignClient;

    @Override
    public CustomerAccLoanCardDetailDto fetchCustomerAccLoanCardDetailDto(String mobileNumber) {
        // 1. 根據手機號碼查找客戶
        Customer customer = customerRepo.findByMobileNumber(mobileNumber)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Customer", "mobileNumber", mobileNumber)
                );

        // 2. 根據客戶 ID 查找帳戶
        Account account = accountRepo.findByCustomerId(customer.getCustomerId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Account", "customerId", customer.getCustomerId().toString())
                );

        // 3. 組出客戶基本資料 + 本服務自己的帳戶資料
        CustomerAccLoanCardDetailDto detailDto = CustomerMapper.mapToCustomerAccLoanCardDetailDto(customer, new CustomerAccLoanCardDetailDto());
        detailDto.setAccountDto(AccountMapper.mapToAccountDto(account, new AccountDto()));

        // 4. 向 loan 服務要貸款資料（Feign → LoadBalancer → Eureka）
        detailDto.setLoanDto(fetchLoanOrNull(mobileNumber));

        // 5. 向 card 服務要信用卡資料
        detailDto.setCardDto(fetchCardOrNull(mobileNumber));

        return detailDto;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  「客戶沒有貸款 / 信用卡」是正常狀態，不是錯誤 —— 對方回 404 時留 null 就好，
    //  不該讓整支查詢失敗。CustomerAccLoanCardDetailDto 的欄位本來就允許 null。
    //
    //  ⚠ 只吞 NotFound。其他 FeignException（連不上、503、逾時）照樣往外拋 ——
    //    那些是真的有問題，吞掉會讓「服務掛了」看起來像「客戶沒辦貸款」。
    //
    //  ⚠ 不要改用 dismiss404: true。那會叫 Feign 把 404 的錯誤 JSON
    //    硬解碼成 LoanDto，欄位對不上又被 Jackson 忽略，結果是一個「全部欄位
    //    都是 null / 0 的假物件」而不是 null —— 比拋例外更難查。
    // ─────────────────────────────────────────────────────────────────────────
    private LoanDto fetchLoanOrNull(String mobileNumber) {
        try {
            return loanFeignClient.fetchLoanDetails(mobileNumber).getBody();
        } catch (FeignException.NotFound e) {
            log.info("客戶 {} 沒有貸款資料", mobileNumber);
            return null;
        }
    }

    private CardDto fetchCardOrNull(String mobileNumber) {
        try {
            return cardFeignClient.fetchCardDetails(mobileNumber).getBody();
        } catch (FeignException.NotFound e) {
            log.info("客戶 {} 沒有信用卡資料", mobileNumber);
            return null;
        }
    }
}
