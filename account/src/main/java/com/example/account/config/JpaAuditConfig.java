package com.example.account.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;

/**
 * JPA 稽核設定。
 * 時間欄位（@CreatedDate、@LastModifiedDate）Spring 自動填；
 * 操作者欄位（@CreatedBy、@LastModifiedBy）要靠 AuditorAware 提供。
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")   // 操作者由 auditorProvider 提供
public class JpaAuditConfig {

    /**
     * 提供 @CreatedBy / @LastModifiedBy 的值。目前無登入機制，固定填服務名稱
     */
    @Bean("auditorProvider")
    public AuditorAware<String> auditorProvider() {
        // createdBy 是 nullable = false，不能回 Optional.empty()
        return () -> Optional.of("ACCOUNT_MS");
    }
}
