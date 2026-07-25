package com.example.account.audit;

import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 提供 BaseEntity 的 @CreatedBy / @LastModifiedBy 要寫入的值。
 * 目前無登入機制，固定填服務名稱；日後接上 Spring Security 可改抓登入者。
 */
@Component("auditAwareImpl")
public class AuditAwareImpl implements AuditorAware<String> {

    @Override
    public Optional<String> getCurrentAuditor() {
        // createdBy 是 nullable = false，不能回 Optional.empty()
        return Optional.of("ACCOUNT_MS");
    }

}
