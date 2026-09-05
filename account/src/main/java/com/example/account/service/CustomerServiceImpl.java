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
import com.example.account.service.client.KubernetesCardFeignClient;
import com.example.account.service.client.KubernetesLoanFeignClient;
import com.example.account.service.client.LoanFeignClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerServiceImpl implements ICustomerService {

    private final AccountRepo accountRepo;
    private final CustomerRepo customerRepo;
    private final CardFeignClient cardFeignClient;
    private final LoanFeignClient loanFeignClient;
    private final KubernetesCardFeignClient kubernetesCardFeignClient;
    private final KubernetesLoanFeignClient kubernetesLoanFeignClient;

    /*
     * ======================== Eureka 查詢 ========================
     * 透過 Feign 使用服務名稱，經 LoadBalancer 從 Eureka 找到 Loan / Card 實例。
     */
    @Override
    public CustomerAccLoanCardDetailDto fetchCustomerAccLoanCardDetailEurekaDto(String mobileNumber) {
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
        detailDto.setLoanDto(fetchEurekaLoanOrNull(mobileNumber));

        // 5. 向 card 服務要信用卡資料
        detailDto.setCardDto(fetchEurekaCardOrNull(mobileNumber));

        return detailDto;
    }

    // 404、連線失敗、503 或逾時都由 fallback 轉為 null，讓整合查詢仍可回傳帳戶資料。
    private LoanDto fetchEurekaLoanOrNull(String mobileNumber) {
        ResponseEntity<LoanDto> response = loanFeignClient.fetchLoanDetails(mobileNumber, "eureka");
        // response 或 body 為 null 都代表沒有資料；先防止 response 為 null 時呼叫 getBody()。
        return (response != null) ? response.getBody() : null;
    }

    private CardDto fetchEurekaCardOrNull(String mobileNumber) {
        ResponseEntity<CardDto> response = cardFeignClient.fetchCardDetails(mobileNumber, "eureka");
        return (response != null) ? response.getBody() : null;
    }

    /*
     * ===================== Kubernetes 查詢 ======================
     * Feign 直接呼叫 Kubernetes Service DNS，由 Service 將請求分流到 Pod。
     */
    /**
     * 與 {@link #fetchCustomerAccLoanCardDetailEurekaDto(String)} 回傳相同資料，
     * 但 loan / card 不查 Eureka，而是直接呼叫 Kubernetes Service DNS。
     */
    @Override
    public CustomerAccLoanCardDetailDto fetchCustomerAccLoanCardDetailKubernetesDto(String mobileNumber) {
        Customer customer = customerRepo.findByMobileNumber(mobileNumber)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Customer", "mobileNumber", mobileNumber)
                );

        Account account = accountRepo.findByCustomerId(customer.getCustomerId())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Account", "customerId", customer.getCustomerId().toString())
                );

        CustomerAccLoanCardDetailDto detailDto = CustomerMapper.mapToCustomerAccLoanCardDetailDto(customer, new CustomerAccLoanCardDetailDto());
        detailDto.setAccountDto(AccountMapper.mapToAccountDto(account, new AccountDto()));

        // Feign 的 url 指向 K8s Service；Service selector 會在 Ready 的 loan / card Pods 間分流。
        detailDto.setLoanDto(fetchKubernetesLoanOrNull(mobileNumber));
        detailDto.setCardDto(fetchKubernetesCardOrNull(mobileNumber));

        return detailDto;
    }


    private LoanDto fetchKubernetesLoanOrNull(String mobileNumber) {
        ResponseEntity<LoanDto> response = kubernetesLoanFeignClient.fetchLoanDetails(mobileNumber, "service-dns");
        return (response != null) ? response.getBody() : null;
    }

    private CardDto fetchKubernetesCardOrNull(String mobileNumber) {
        ResponseEntity<CardDto> response = kubernetesCardFeignClient.fetchCardDetails(mobileNumber, "service-dns");
        return (response != null) ? response.getBody() : null;
    }
}
