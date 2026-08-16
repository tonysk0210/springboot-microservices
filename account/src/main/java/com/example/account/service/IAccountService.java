package com.example.account.service;

import com.example.account.dto.CustomerDto;

public interface IAccountService {

    void createAccount(CustomerDto customerDto);

    CustomerDto fetchAccount(String mobileNumber);

    boolean updateAccount(CustomerDto customerDto);

    boolean deleteAccount(String mobileNumber);

    /**
     * 把帳戶標記成「已通知」。
     * <p>
     * 由 {@code AccountFunctions.updateCommunication} 在收到 messageservice
     * 的回報訊息時呼叫 —— 是整個非同步流程的最後一站。
     */
    boolean updateCommunicationStatus(Integer accountNumber);
}
