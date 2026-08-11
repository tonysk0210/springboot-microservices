package com.example.gatewayserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 * Gateway 在 OAuth2 裡的角色是「資源伺服器」—— 只驗 token，不發 token（那是 Keycloak 的事）。
 * <p>
 * ⚠ 這裡用的是「reactive 版」的整套 API，因為 Gateway 跑在 WebFlux 上：
 * <pre>
 *     Servlet 陣營                    WebFlux 陣營（本檔案）
 *     EnableWebSecurity         →    EnableWebFluxSecurity
 *     HttpSecurity              →    ServerHttpSecurity
 *     SecurityFilterChain       →    SecurityWebFilterChain
 *     .authorizeHttpRequests()  →    .authorizeExchange()
 *     .requestMatchers()        →    .pathMatchers()
 * </pre>
 * 兩套不能混用，寫錯會直接找不到類別。
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
                .authorizeExchange(exchanges -> exchanges
                        // ⚠ 規則「由上往下比對，第一條符合就決定」，順序很重要。
                        //   最寬鬆的放最上面 → 下面的角色檢查永遠輪不到。
                        //   （課程原本第一行是 .pathMatchers(HttpMethod.GET).permitAll()，
                        //     而本專案的 API 幾乎全是 GET，那樣寫等於完全沒有保護。）

                        // 給 compose healthcheck 和 Prometheus 用，不能要求 token。
                        .pathMatchers("/actuator/**").permitAll()
                        // 斷路器跳開時 Gateway 內部 forward 到這裡，
                        // 擋掉的話使用者看到 401 而不是那句友善訊息。
                        .pathMatchers("/contactSupport").permitAll()

                        // 三條業務路由各要對應的角色。
                        // ⚠ 路徑要對上 RouteConfig 的 .path(...)，也就是「對外」的路徑，
                        //   不是 rewritePath 之後的 /api/...。
                        .pathMatchers("/bank/account/**").hasRole("ACCOUNTS")
                        .pathMatchers("/bank/loan/**").hasRole("LOANS")
                        .pathMatchers("/bank/card/**").hasRole("CARDS")

                        // ⚠ 這行不能省 —— reactive 版「沒有」隱含的預設拒絕，
                        //   沒寫的話沒對上任何規則的路徑會被放行。
                        .anyExchange().authenticated())

                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(grantedAuthoritiesExtractor())))

                // ⚠ 關掉 CSRF 是「對的」—— CSRF 攻擊靠瀏覽器自動帶 cookie，
                //   而 Bearer token 要手動放進標頭，瀏覽器不會自動帶，沒有 cookie 就沒有這個風險。
                //   用 session / cookie 的服務才需要開。
                .csrf(ServerHttpSecurity.CsrfSpec::disable);

        return http.build();
    }

    /**
     * 把 Keycloak 的 role 轉成 Spring Security 認得的格式。
     * <p>
     * ⚠ 不接這個轉換器的話，hasRole("ACCOUNTS") 永遠是 false ——
     * token 裡明明有角色卻一直 403，是 Keycloak + Spring Security 最常見的坑。
     * <pre>
     *     Keycloak 的 JWT：{"realm_access": {"roles": ["ACCOUNTS"]}}
     *     Spring 要的：     ROLE_ACCOUNTS
     * </pre>
     * 🔑 JwtAuthenticationConverter 本身是 Servlet 陣營的類別，但它只做純轉換、
     * 不碰 request / response，所以可以用 Adapter 包成 reactive 版重複使用。
     */
    private Converter<Jwt, Mono<AbstractAuthenticationToken>> grantedAuthoritiesExtractor() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRoleConverter());
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }

}
