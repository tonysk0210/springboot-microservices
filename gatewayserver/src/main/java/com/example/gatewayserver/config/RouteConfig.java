package com.example.gatewayserver.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
                        )
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
