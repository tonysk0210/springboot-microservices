package com.example.gatewayserver.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 定義 Gateway 對外路由，包含路徑改寫、服務發現與 fallback。
 * 對外路徑可自行設計，但改寫後必須符合下游服務的 API 路徑。
 */
@Configuration
public class RouteConfig {

    @Bean
    public RouteLocator bankRouteConfig(
            RouteLocatorBuilder builder,
            @Value("${ACCOUNT_DIRECT_BASE_URL:http://localhost:8080}") String accountDirectBaseUrl) {
        return builder.routes()

                /* -----------------------------------------------------------------
                 * API 1：/bank/account/**
                 * 透過 Eureka／LoadBalancer 尋找 Account 實例。
                 * ----------------------------------------------------------------- */
                .route(p -> p
                        .path("/bank/account/**")
                        .filters(f -> f
                                // 1. 移除 /bank/account 前綴，保留後續路徑轉給 Account。
                                .rewritePath("/bank/account/(?<segment>.*)", "/${segment}")
                                // 2. 在回應 header 加上 X-Response-Time，方便觀察。
                                .addResponseHeader("X-Response-Time", LocalDateTime.now().toString())
                                // 標示 Gateway 透過 Eureka 尋找 Account。
                                .addResponseHeader("X-Gateway-Discovery-Mode", "eureka")
                                // 3. Account 無法連線時，轉發到 Gateway 的 fallback 端點。
                                .circuitBreaker(config -> config
                                        .setName("accountCircuitBreaker")
                                        .setFallbackUri("forward:/contactSupport"))
                        )
                        // lb:// 透過 Eureka／LoadBalancer 尋找 account 實例。
                        .uri("lb://ACCOUNT"))

                /* -----------------------------------------------------------------
                 * API 2：/bank/loan/**
                 * 透過 Eureka／LoadBalancer 尋找 Loan 實例。
                 * ----------------------------------------------------------------- */
                .route(p -> p
                        .path("/bank/loan/**")
                        .filters(f -> f
                                .rewritePath("/bank/loan/(?<segment>.*)", "/${segment}")
                                .addResponseHeader("X-Response-Time", LocalDateTime.now().toString())
                                // 標示 Gateway 透過 Eureka 尋找 Loan。
                                .addResponseHeader("X-Gateway-Discovery-Mode", "eureka")
                                // 1. Gateway 連線失敗時，GET 請求最多重試 3 次。
                                .retry(retryConfig -> retryConfig
                                        .setRetries(3)
                                        .setMethods(HttpMethod.GET)
                                        .setBackoff(
                                                // 第一次重試前等待 100 毫秒。
                                                Duration.ofMillis(100),
                                                // 每次重試的等待時間最多 1 秒。
                                                Duration.ofMillis(1000),
                                                // 每次等待時間乘以 2，形成指數退避。
                                                2,
                                                // 以下一次等待時間為基準繼續倍增：100、200、400 毫秒……
                                                true)))
                        // lb:// 透過 Eureka／LoadBalancer 尋找 loan 實例。
                        .uri("lb://LOAN"))

                /* -----------------------------------------------------------------
                 * API 3：/bank/card/**
                 * 透過 Eureka／LoadBalancer 尋找 Card 實例。
                 * ----------------------------------------------------------------- */
                .route(p -> p
                        .path("/bank/card/**")
                        .filters(f -> f
                                .rewritePath("/bank/card/(?<segment>.*)", "/${segment}")
                                .addResponseHeader("X-Response-Time", LocalDateTime.now().toString())
                                // 標示 Gateway 透過 Eureka 尋找 Card。
                                .addResponseHeader("X-Gateway-Discovery-Mode", "eureka")
                                // 1. 以 Redis 令牌桶限制請求速率，避免 Card 被過量請求壓垮。
                                .requestRateLimiter(config -> config
                                        // 使用每秒補充速率、桶容量與單次請求成本的設定。
                                        .setRateLimiter(redisRateLimiter())
                                        // 依 user header 分組；不同 user 使用不同限流桶。
                                        .setKeyResolver(userKeyResolver())))
                        // lb:// 透過 Eureka／LoadBalancer 尋找 card 實例。
                        .uri("lb://CARD"))

                /* -----------------------------------------------------------------
                 * API 4：/k8s/account/**
                 * 不經 Eureka，直接使用 account Service DNS 尋找 Ready Pod。
                 * 例：/k8s/account/api/... → account:8080/api/...
                 * ----------------------------------------------------------------- */
                .route(p -> p
                        .path("/k8s/account/**")
                        .filters(f -> f
                                .rewritePath("/k8s/account/(?<segment>.*)", "/${segment}")
                                .addResponseHeader("X-Gateway-Discovery-Mode", "service-dns"))
                        .uri(accountDirectBaseUrl)) // 直接使用服務 DNS 找 account，不經 Eureka。

                .build();
    }

    /**
     * Redis 令牌桶限流器：每秒補充 1 個令牌，桶上限為 1，每次請求消耗 1 個令牌。
     */
    @Bean
    public RedisRateLimiter redisRateLimiter() {
        return new RedisRateLimiter(1, 1, 1);
    }

    /**
     * 以 request 的 user header 作為限流 key；未提供時統一使用 anonymous。
     */
    @Bean
    KeyResolver userKeyResolver() {
        return exchange -> Mono.justOrEmpty(exchange.getRequest().getHeaders().getFirst("user"))
                .defaultIfEmpty("anonymous");
    }
}
