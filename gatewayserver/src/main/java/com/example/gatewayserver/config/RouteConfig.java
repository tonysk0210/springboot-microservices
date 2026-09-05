package com.example.gatewayserver.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
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
                                // 3. 標示 Gateway 透過 Eureka 尋找 Account。
                                .addResponseHeader("X-Gateway-Discovery-Mode", "eureka")
                                // 4. Account 無法連線時，轉發到 Gateway 的 fallback 端點。
                                .circuitBreaker(config -> config
                                        .setName("accountCircuitBreaker")
                                        .setFallbackUri("forward:/contactSupport")
                                        // Account 回傳 503 時，將此狀態計入 Circuit Breaker 失敗次數。
                                        .addStatusCode("SERVICE_UNAVAILABLE"))
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
                                // 1. 標示 Gateway 透過 Eureka 尋找 Loan。
                                .addResponseHeader("X-Gateway-Discovery-Mode", "eureka")
                                // 2. Client 只呼叫一次；Gateway 遇到可重試的失敗時，會自動重試 GET 請求最多 3 次。
                                .retry(retryConfig -> retryConfig
                                        .setMethods(HttpMethod.GET)
                                        .setRetries(3)
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
                                // 1. 標示 Gateway 透過 Eureka 尋找 Card。
                                .addResponseHeader("X-Gateway-Discovery-Mode", "eureka")
                                // 2. 以 Redis 令牌桶限制請求速率，避免 Card 被過量請求壓垮。
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

    /*
     * Gateway RateLimiter 設定：RedisRateLimiter 定義令牌規則，KeyResolver 依使用者分組計算限流；兩者共同限制 Card 路由的請求速率。
     */

    /**
     * Redis 令牌桶限流器：每秒補充 1 個令牌，最多累積 1 個，每次請求消耗 1 個。計數存於 Redis，讓多個 Gateway instance 共用限流狀態。
     * Redis 令牌桶會讓每個使用者每秒最多通過 1 個請求，超過時回傳 429 Too Many Requests。
     */
    @Bean
    public RedisRateLimiter redisRateLimiter() {
        return new RedisRateLimiter(1, 1, 1);
    }

    /**
     * 以 request 的 user Header 分開計算限流；未提供時共用 anonymous key。
     */
    @Bean
    KeyResolver userKeyResolver() {
        return exchange -> Mono.justOrEmpty(exchange.getRequest().getHeaders().getFirst("user"))
                .defaultIfEmpty("anonymous");
    }
}
