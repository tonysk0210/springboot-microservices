package com.example.account.dto;

import lombok.Data;

/**
 * loan 服務回傳的貸款資料，供 {@code LoanFeignClient} 反序列化用。
 *
 * <p>⚠ 這是 loan 服務那份 {@code com.example.loan.dto.LoanDto} 的「客戶端副本」。
 * 兩份是各自獨立的類別，不共用程式碼 —— 這是微服務刻意的取捨：
 * <ul>
 *   <li>好處：loan 改自己的 DTO 不會強迫 account 一起重新編譯、重新部署</li>
 *   <li>代價：欄位名稱要靠人工同步，改錯只會在執行期得到 null，不會編譯錯誤</li>
 * </ul>
 *
 * <p>🔑 不要為了「不重複」就把它抽成共用的 jar 模組。那會讓所有服務綁在同一個版本上，
 * 等於把微服務退化成分散式的單體 —— 這是實務上最常見的架構失誤之一。
 *
 * <p>⚠ 刻意「不放」驗證註解（{@code @NotBlank} / {@code @Pattern}）：
 * 驗證是「收資料的一方」該做的事，loan 的 Controller 已經驗過了。
 * 這裡只負責把 JSON 轉成物件，多加驗證註解不會執行，只會誤導讀者。
 *
 * <p>Jackson 需要 setter 和無參建構子，兩者都由 Lombok 的 {@code @Data} 提供。
 * 欄位對應靠「名稱」，順序無關。JSON 多出來的欄位預設會被忽略。
 */
@Data
public class LoanDto {

    private String mobileNumber;

    private String loanNumber;

    private String loanType;

    private int totalLoan;

    private int amountPaid;

    private int outstandingAmount;
}
