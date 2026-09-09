package com.example.gatewayserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Gateway 的 OAuth2 Resource Server：驗證 Keycloak JWT 並依角色保護路由。
 * 使用 WebFlux Security，僅在 {@code auth} profile 啟用；未啟用時由 {@link NoAuthSecurityConfig} 放行請求。
 * <pre>
 * Servlet Security             WebFlux Security（本類別）
 * EnableWebSecurity        →   EnableWebFluxSecurity
 * HttpSecurity             →   ServerHttpSecurity
 * SecurityFilterChain      →   SecurityWebFilterChain
 * authorizeHttpRequests()  →   authorizeExchange()
 * requestMatchers()        →   pathMatchers()
 * </pre>
 */
@Configuration
@EnableWebFluxSecurity
@Profile("auth")
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
                .authorizeExchange(exchanges -> exchanges
                        // 規則「由上往下比對，第一條符合就決定」，順序很重要會影響最終結果。

                        // Actuator 供健康檢查與 Prometheus 使用，不需 token。
                        .pathMatchers("/actuator/**").permitAll()
                        // Circuit Breaker fallback 的內部端點不需 token。
                        .pathMatchers("/contactSupport").permitAll()

                        // 業務路由依角色控管；使用 RouteConfig 的對外路徑。
                        .pathMatchers("/bank/account/**", "/k8s/account/**").hasRole("ACCOUNTS")
                        .pathMatchers("/bank/loan/**").hasRole("LOANS")
                        .pathMatchers("/bank/card/**").hasRole("CARDS")

                        // 其他未列出的路徑也必須通過 JWT 驗證。
                        .anyExchange().authenticated())

                // 啟用 Resource Server：從 Authorization Header 讀取 Bearer JWT，使用 Keycloak 公鑰驗證，並將 token 內的 realm roles 轉成 Spring Security 權限。
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(grantedAuthoritiesExtractor())))

                // API 使用 Bearer token，不依賴 Cookie，因此停用 CSRF。如果不關掉，瀏覽器會在 POST/PUT/DELETE 時被擋掉。
                .csrf(ServerHttpSecurity.CsrfSpec::disable);

        return http.build();
    }

    /**
     * 把 Keycloak Token 裡的角色轉成 Spring Security 看得懂的權限，讓 Gateway 可以用 hasRole(...) 判斷是否允許存取 API。
     */
    private Converter<Jwt, Mono<AbstractAuthenticationToken>> grantedAuthoritiesExtractor() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRoleConverter());
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }

}
