package com.example.account.service.client;

import com.example.account.dto.CardDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 呼叫 card 服務的宣告式客戶端。
 */
@FeignClient(name = "card")
public interface CardFeignClient {

    @GetMapping("/api/fetch-card")
    ResponseEntity<CardDto> fetchCardDetails(@RequestParam String mobileNumber);
}
