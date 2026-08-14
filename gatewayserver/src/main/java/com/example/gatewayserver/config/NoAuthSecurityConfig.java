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
 * 沒帶 {@code auth} profile 時的「不驗證」模式 —— 全部放行。
 * <p>
 * ⚠ <b>這個類別不是可有可無的。</b>直覺上「不要驗證」＝「不要註冊 SecurityConfig」，
 * 但那樣做的結果不是放行，而是「全部被擋」：
 * <pre>
 *     spring-boot-starter-security 在 classpath
 *              ↓
 *     Boot 發現沒有任何 SecurityWebFilterChain bean
 *              ↓
 *     自動配一條預設的 —— 全部要求認證 + HTTP Basic 挑戰
 *              ↓
 *     每個請求都跳 401 + WWW-Authenticate: Basic，比原本更難用
 * </pre>
 * 所以必須「主動」提供一條放行的 chain 把預設那條頂掉。
 * <p>
 * 🔑 判斷是不是踩到這個坑：看回應有沒有 {@code WWW-Authenticate: Basic} 標頭。
 * 有的話代表這個類別沒生效，接手的是 Boot 的預設 chain。
 *
 * @see SecurityConfig 帶 auth profile 時生效的那一份
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
                // ⚠ 下面三個 disable 一個都不能省 —— 只寫 permitAll 的話，
                //   Boot 仍會掛上登入表單與 Basic 挑戰，瀏覽器一打就被導去登入頁。
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .build();
    }

    /**
     * 因為「不驗證」是本專案的預設值，這段警告是唯一的防呆 ——
     * 避免哪天以為有保護、其實是全開的。
     */
    @PostConstruct
    void warnInsecure() {
        log.warn("""

                ⚠⚠⚠  驗證已關閉（未啟用 auth profile）—— 所有 API 無需 token 即可存取
                      要開啟：./mvnw spring-boot:run -Dspring-boot.run.profiles=auth
                      容器版：在 .env 設 GATEWAY_PROFILE=auth，並加 --profile auth
                """);
    }
}
