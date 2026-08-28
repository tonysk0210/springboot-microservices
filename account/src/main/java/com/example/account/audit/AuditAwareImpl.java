package com.example.account.audit;

import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 提供稽核欄位要記錄的操作者。自動填入 @CreatedBy、@LastModifiedBy
 */
@Component("auditAwareImpl")
public class AuditAwareImpl implements AuditorAware<String> {

    @Override
    public @NonNull Optional<String> getCurrentAuditor() {
        // 目前沒有登入者資訊，先記錄服務名稱。
        return Optional.of("ACCOUNT_MS");
    }
}
