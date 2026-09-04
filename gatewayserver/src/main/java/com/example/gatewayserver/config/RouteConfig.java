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
 * 對外路由。路徑自己決定
 */
@Configuration
public class RouteConfig {

    @Bean
    public RouteLocator bankRouteConfig(
            RouteLocatorBuilder builder,
            @Value("${ACCOUNT_DIRECT_BASE_URL:http://localhost:8080}") String accountDirectBaseUrl) {
        return builder.routes()

                // Kubernetes 對照路徑：不經 Eureka，直接交給 account Service 在叢集內選一個 Ready Pod。
                // 例：/k8s/account/api/fetch-customerAccLoanCardDetail-k8s → account:8080/api/...
                .route(p -> p
                        .path("/k8s/account/**")
                        .filters(f -> f
                                .rewritePath("/k8s/account/(?<segment>.*)", "/${segment}")
                                .addResponseHeader("X-Gateway-Discovery", "kubernetes-service"))
                        .uri(accountDirectBaseUrl)) // 直接使用服務 DNS 找 account，不經 Eureka。

                // /bank/account/api/fetch-account → /api/fetch-account
                .route(p -> p
                        .path("/bank/account/**")
                        // rewritePath 砍掉前綴，(?<segment>.*) 抓住要保留的尾巴。
                        .filters(f ->
                                f.rewritePath("/bank/account/(?<segment>.*)", "/${segment}")
                                        .addResponseHeader("X-Response-Time", LocalDateTime.now().toString())
                                        // 使用此名稱識別並套用 account 路由的斷路器設定。
                                        .circuitBreaker(config -> config
                                                .setName("accountCircuitBreaker") // 給此斷路器設定一個名稱
                                                .setFallbackUri("forward:/contactSupport")) // 斷路時在 Gateway 內部轉發至替代回應端點。
                        )
                        // lb:// = 去 Eureka 查實例。名字要對上 spring.application.name
                        .uri("lb://ACCOUNT"))

                .route(p -> p
                        .path("/bank/loan/**")
                        .filters(f ->
                                f.rewritePath("/bank/loan/(?<segment>.*)", "/${segment}")
                                        .addResponseHeader("X-Response-Time", LocalDateTime.now().toString())
                                        // ⚠ 這是 Spring Cloud Gateway 自己的 retry（RetryGatewayFilterFactory），跟 account 用的 Resilience4j @Retry 是兩套不同的東西。
                                        .retry(retryConfig -> retryConfig
                                                .setRetries(3) // 重試次數（不含第一次，總共打 4 次）
                                                .setMethods(HttpMethod.GET) // 只對 GET 方法重試
                                                .setBackoff(
                                                        // 第一次重試前等待 100 ms。
                                                        Duration.ofMillis(100),
                                                        // 單次等待時間最多 1 秒。(休息時間)
                                                        Duration.ofMillis(1000),
                                                        // 每次等待時間乘以 2。
                                                        2,
                                                        // 以上一次等待時間為基準計算下一次延遲。
                                                        true)))
                        .uri("lb://LOAN"))

                .route(p -> p
                        .path("/bank/card/**")
                        .filters(f ->
                                f.rewritePath("/bank/card/(?<segment>.*)", "/${segment}")
                                        .addResponseHeader("X-Response-Time", LocalDateTime.now().toString())
                                        // 對 card 路由套用 Redis 令牌桶限流。
                                        .requestRateLimiter(config -> config
                                                // 指定令牌補充速率、桶容量與每次請求成本。
                                                .setRateLimiter(redisRateLimiter())
                                                // 依 user header 產生 key，讓不同 key 使用各自的桶。
                                                .setKeyResolver(userKeyResolver())))
                        .uri("lb://CARD"))

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
