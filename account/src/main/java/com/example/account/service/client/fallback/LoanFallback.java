package com.example.account.service.client.fallback;

import com.example.account.dto.LoanDto;
import com.example.account.service.client.LoanFeignClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * loan 服務無法使用時的替代實作（斷路器跳開、連不上、逾時都會走這裡）。
 * <p>
 * ⚠ 不要回 {@code null} —— 呼叫端寫的是 {@code fetchLoanDetails(...).getBody()}，
 * 整個 ResponseEntity 是 null 會直接 NPE，fallback 等於白做。
 * 要回「200 但 body 是 null」，語意才是「查不到貸款資料」。
 */
@Slf4j
@Component
public class LoanFallback implements LoanFeignClient {

    @Override
    public ResponseEntity<LoanDto> fetchLoanDetails(String mobileNumber) {
        // ⚠ 一定要 log —— 不然「loan 服務掛了」跟「客戶真的沒貸款」在 log 上
        //   長得一模一樣，都是安靜地回 null。
        log.warn("loan 服務無法使用，貸款資料以 null 回傳（mobileNumber={}）", mobileNumber);
        return ResponseEntity.ok(null);
    }
}
