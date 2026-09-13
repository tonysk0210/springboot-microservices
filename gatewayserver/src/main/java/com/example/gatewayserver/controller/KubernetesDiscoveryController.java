package com.example.gatewayserver.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 提供 Demo API，查詢 Spring Cloud Kubernetes Discovery Server 的服務清單。
 *
 * <p>此 Controller 只負責查詢並回傳清單，不會改變既有 Eureka 路由，也不會參與 Kubernetes Service DNS 的業務流量轉送。</p>
 *
 * <p>此 API 僅在 Kubernetes 環境中有效：必須設定
 * {@code K8S_DISCOVERY_SERVER_URL}，且 Discovery Server 正常運作。
 * Compose 未設定該網址時，會回傳 502。</p>
 */
@RestController
@RequestMapping("/k8s-service-discovery")
@Tag(name = "Kubernetes Discovery Demo", description = "查詢 Kubernetes Discovery Server 找到的服務")
public class KubernetesDiscoveryController {

    private final WebClient webClient;
    private final String discoveryServerUrl;

    @Autowired
    public KubernetesDiscoveryController(
            @Value("${K8S_DISCOVERY_SERVER_URL:}") String discoveryServerUrl) {
        // Kubernetes Discovery Server 位址由環境變數提供；未設定時，查詢會回傳 502。
        this.webClient = WebClient.builder().build();
        this.discoveryServerUrl = discoveryServerUrl;
    }

    /**
     * 查詢所有服務。
     * 對外路徑：GET /k8s-service-discovery/apps
     */
    @Operation(
            summary = "查詢 Kubernetes 服務清單",
            description = "呼叫 Spring Cloud Kubernetes Discovery Server 的 /apps，"
                    + "回傳目前找到的所有 Kubernetes Service，例如 account、loan 與 card。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "成功取得服務清單"),
            @ApiResponse(responseCode = "502", description = "Discovery Server 未設定或目前無法連線")
    })
    @GetMapping(value = "/apps", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<String>> getAllApps() {
        return queryDiscoveryServer("/apps");
    }

    /**
     * 查詢流程：呼叫 Discovery Server 的 /apps，保留它的 HTTP 狀態與 JSON 回應。
     * WebClient 以非同步方式呼叫，不阻塞 Gateway 執行緒。
     */
    private Mono<ResponseEntity<String>> queryDiscoveryServer(String path) {
        if (discoveryServerUrl == null || discoveryServerUrl.isBlank()) {
            return Mono.just(unavailable("尚未設定 K8S_DISCOVERY_SERVER_URL"));
        }

        // 將相對路徑接到 Discovery Server 基底網址。
        String uri = discoveryServerUrl.replaceAll("/$", "") + path;

        return webClient.get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON)
                // 不自行解析內容，直接把 Discovery Server 的 JSON 回傳給呼叫端。
                .exchangeToMono(response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("{}")
                        .map(body -> ResponseEntity.status(response.statusCode())
                                .contentType(response.headers().contentType()
                                        .orElse(MediaType.APPLICATION_JSON))
                                .body(body)))
                // Discovery Server 無法連線時，回傳 502，而不是讓例外直接冒出。
                .onErrorResume(ex -> Mono.just(unavailable(
                        "Kubernetes Discovery Server 暫時無法使用")));
    }

    /**
     * 建立統一的 502 回應，表示 Gateway 無法取得 Discovery Server 資料。
     */
    private ResponseEntity<String> unavailable(String message) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"" + message + "\"}");
    }
}
