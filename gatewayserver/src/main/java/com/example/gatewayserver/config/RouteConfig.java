package com.example.gatewayserver.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 對外路由。路徑自己決定
 */
@Configuration
public class RouteConfig {

    @Bean
    public RouteLocator bankRouteConfig(RouteLocatorBuilder builder) {
        return builder.routes()

                // /bank/account/api/fetch-account → /api/fetch-account
                .route(p -> p
                        .path("/bank/account/**")
                        // rewritePath 砍掉前綴，(?<segment>.*) 抓住要保留的尾巴。
                        .filters(f ->
                                f.rewritePath("/bank/account/(?<segment>.*)", "/${segment}")
                                        .addResponseHeader("X-Response-Time", LocalDateTime.now().toString())
                                        // 使用此名稱識別並套用 account 路由的斷路器設定。
                                        .circuitBreaker(config -> config.setName("accountCircuitBreaker")
                                                // 斷路時在 Gateway 內部轉發至替代回應端點。
                                                .setFallbackUri("forward:/contactSupport"))
                        )
                        // lb:// = 去 Eureka 查實例。名字要對上 spring.application.name
                        .uri("lb://ACCOUNT"))

                .route(p -> p
                        .path("/bank/loan/**")
                        .filters(f ->
                                f.rewritePath("/bank/loan/(?<segment>.*)", "/${segment}")
                                        .addResponseHeader("X-Response-Time", LocalDateTime.now().toString())
                                        // ⚠ 這是 Spring Cloud Gateway 自己的 retry（RetryGatewayFilterFactory），跟 account 用的 Resilience4j @Retry 是兩套不同的東西。
                                        .retry(retryConfig -> retryConfig.setRetries(3) // 重試次數（不含第一次，總共打 4 次）
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
                        )
                        .uri("lb://CARD"))

                .build();
    }
}
