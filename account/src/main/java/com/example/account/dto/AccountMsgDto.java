package com.example.account.dto;

/**
 * 送給 messageservice 的通知內容（帳戶建立成功後）。
 * <p>
 * ⚠ 欄位名稱與型別必須跟 messageservice 那份 {@code AccountMsgDto} 完全一致 ——
 * 中間隔著 JSON 序列化，對不上的欄位會靜靜地變成 null，不會有任何錯誤。
 * <p>
 * 🔑 兩邊各自維護一份是刻意的：微服務之間不共用程式碼，否則就變成編譯期耦合，
 * 改一邊要重建兩邊。代價就是上面那個「要自己保持同步」的風險。
 */
public record AccountMsgDto(Integer accountNumber, String name, String email, String mobileNumber) {
}

