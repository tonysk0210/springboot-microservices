package com.example.gatewayserver.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * 未啟用 {@code auth} profile 時，放行所有請求供本機開發使用。
 * 必須提供這條 Security chain，避免 Spring Boot 自動啟用預設登入驗證。
 *
 * @see SecurityConfig 啟用 auth profile 時使用的安全設定
 */
@Slf4j
@Configuration
@EnableWebFluxSecurity
@Profile("!auth")
public class NoAuthSecurityConfig {

    @Bean
    public SecurityWebFilterChain permitAllFilterChain(ServerHttpSecurity http) {
        return http
                .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                // 停用 CSRF、HTTP Basic 與表單登入，避免仍出現登入要求。
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .build();
    }

    /** 提醒目前未啟用驗證，避免誤以為 API 已受保護。 */
    @PostConstruct
    void warnInsecure() {
        log.warn("""

                ⚠⚠⚠  驗證已關閉（未啟用 auth profile）—— 所有 API 無需 token 即可存取
                      這是「本機開發」的預設模式。要驗證有兩條路：
                        IntelliJ → Run Configuration 的 Active profiles 填 auth
                        容器     → docker compose up -d（容器一律驗證，沒有開關）
                """);
    }
}
