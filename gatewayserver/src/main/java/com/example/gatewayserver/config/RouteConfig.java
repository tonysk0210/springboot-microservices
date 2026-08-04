package com.example.gatewayserver.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 對外路由（Java DSL 版）。
 *
 * <p>🔑 這裡跟 application.yaml 的 {@code spring.cloud.gateway.server.webflux.routes}
 * 是「同一件事的兩個入口」—— 兩者最後都變成同一個 {@code RouteDefinition} 物件，
 * {@code /actuator/gateway/routes} 看起來一樣。⚠ 但兩邊都寫的話會「疊加」不是覆蓋，
 * 所以 yaml 那段已經註解掉了。
 *
 * <p>設計原則：對外路徑自己決定，不跟服務名綁在一起。好處是改
 * {@code spring.application.name} 不會動到對外 API，而且沒列在這裡的服務
 * （configserver / eurekaserver / gatewayserver 自己）進不來。
 *
 * <p>⚠ 選 Java DSL 的代價：路由變成「程式碼」，改一條路徑要重新編譯 + 重建 image +
 * 重新部署。yaml 版可以搬到 Config Server，打個 busrefresh 就生效。
 * gatewayserver 的 {@code spring-cloud-starter-bus-amqp} 就是為那個情境加的，
 * 用 Java DSL 的話那個依賴目前只剩「接收 log 層級變更」的用途。
 */
@Configuration
public class RouteConfig {

    @Bean
    public RouteLocator bankRouteConfig(RouteLocatorBuilder builder) {
        return builder.routes()

                // ── account ──────────────────────────────────────────────────
                //  對外   /bank/account/api/fetch-account
                //         ^^^^^^^^^^^^^ 只有這段被砍掉
                //  送出   /api/fetch-account        ← 正好對上 @RequestMapping("/api")
                .route(p -> p
                        .path("/bank/account/**")
                        // 底層是 path.replaceAll(模式, 替換)（見 RewritePathGatewayFilterFactory
                        // 原始碼第 74 行），所以是「取代符合的片段」不是整條比對。
                        //
                        //   /bank/account/api/fetch-account
                        //   /bank/account/(?<segment>.*)      ← segment 抓到 api/fetch-account
                        //   /${segment}                      → /api/fetch-account
                        //
                        //   (...)         群組，把括號裡抓到的記下來
                        //   ?<segment>    給群組取名
                        //   .*            任何字元、任意長度
                        //   ${segment}    在替換字串裡取回
                        //
                        // 🔑 純粹砍前綴的話不用群組也行 —— rewritePath("/bank/account", "")
                        //    效果一樣。用群組版的理由是它「能重組」，而且是官方與課程的
                        //    慣例寫法，例如要插版本號時只有它做得到：
                        //        rewritePath("/bank/account/(?<segment>.*)", "/v2/${segment}")
                        //
                        // ⚠ 等價的 yaml 寫法是 filters: [ StripPrefix=2 ]，完全不碰正規表示式，
                        //   只數「砍幾段」。固定前綴的情況下最不容易寫錯，代價是不能重組。
                        .filters(f -> f.rewritePath("/bank/account/(?<segment>.*)", "/${segment}"))
                        // lb:// = 去 Eureka 查有哪些實例，交給 LoadBalancer 挑一台。
                        // ⚠ 名字要對上 spring.application.name（本專案是單數 account）。
                        //   寫錯的症狀是「啟動正常，呼叫才炸」：No servers available for service: xxx
                        .uri("lb://ACCOUNT"))

                // ── loan ─────────────────────────────────────────────────────
                .route(p -> p
                        .path("/bank/loan/**")
                        .filters(f -> f.rewritePath("/bank/loan/(?<segment>.*)", "/${segment}"))
                        .uri("lb://LOAN"))

                // ── card ─────────────────────────────────────────────────────
                .route(p -> p
                        .path("/bank/card/**")
                        .filters(f -> f.rewritePath("/bank/card/(?<segment>.*)", "/${segment}"))
                        .uri("lb://CARD"))

                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ⚠⚠ 常見範例裡的一個坑：不要這樣加時間戳 ⚠⚠
    //
    //      .filters(f -> f.rewritePath(...)
    //              .addResponseHeader("X-Response-Time", LocalDateTime.now().toString()))
    //
    //  這個 lambda 只在「建立路由表時」執行一次，LocalDateTime.now() 當場就被算成
    //  一個固定字串。結果是每個回應都拿到「Gateway 啟動的時間」，不是回應時間：
    //      第 1 個請求    X-Response-Time: 2026-08-04T21:31:21.760
    //      第 100 個請求  X-Response-Time: 2026-08-04T21:31:21.760   ← 一模一樣
    //
    //  🔑 判準：回應頭的值「會變」就不能用 addResponseHeader —— 那個過濾器是為
    //     固定值設計的（X-Powered-By 之類）。要每次請求都算就得寫 GlobalFilter：
    //
    //      @Bean
    //      GlobalFilter responseTimeFilter() {
    //          return (exchange, chain) -> chain.filter(exchange)
    //                  .then(Mono.fromRunnable(() -> exchange.getResponse().getHeaders()
    //                          .add("X-Response-Time", LocalDateTime.now().toString())));
    //      }
    // ─────────────────────────────────────────────────────────────────────────
}
