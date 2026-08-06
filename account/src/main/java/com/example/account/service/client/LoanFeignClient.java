package com.example.account.service.client;

import com.example.account.dto.LoanDto;
import com.example.account.service.client.fallback.LoanFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 呼叫 loan 服務的宣告式客戶端 —— 只寫介面，實作由 Feign 在啟動時產生成 Bean。
 *
 * <p>⚠ 需要主類別的 {@code @EnableFeignClients}（AccountApplication 已加）。
 * 少了它介面不會被掃到，而且沒有錯誤訊息，只有注入時的 NoSuchBeanDefinitionException。
 */
// name = Eureka 上的服務名（= loan 的 spring.application.name），必須是單數 "loan"。
// ⚠ 寫錯的症狀是「啟動正常，呼叫才炸」：No servers available for service: xxx
// 🔑 加 url 就跳過服務發現，直接打固定位址（適合呼叫外部 API）：
//    @FeignClient(name = "loan", url = "${loan.base-url:http://localhost:8090}")
@FeignClient(name = "loan", fallback = LoanFallback.class)
public interface LoanFeignClient {

    // ⚠ 路徑要完整，含 loan 那邊 @RequestMapping("/api") 的前綴 —— Feign 不會幫你補。
    // ⚠ @RequestParam 不可省略：Feign 會把「沒有註解的參數」當成請求 body，而 GET 不該有 body。
    @GetMapping("/api/fetch-loan")
    ResponseEntity<LoanDto> fetchLoanDetails(@RequestParam String mobileNumber);
}
