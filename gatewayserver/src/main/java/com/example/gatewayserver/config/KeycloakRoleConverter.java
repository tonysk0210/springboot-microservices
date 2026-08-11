package com.example.gatewayserver.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 把 Keycloak 的角色翻譯成 Spring Security 認得的格式。
 * <p>
 * ⚠ 不接這個轉換器的話，{@code hasRole("LOANS")} 永遠是 false ——
 * token 裡明明有角色卻一直 403，是 Keycloak + Spring Security 最常見的坑。
 *
 * <pre>
 * Keycloak 發的 JWT 長這樣：
 *     {
 *       "realm_access": { "roles": ["LOANS", "ACCOUNTS", "default-roles-master"] },
 *       "sub": "...", "exp": ...
 *     }
 *
 * Spring Security 要的是一堆 GrantedAuthority：
 *     ROLE_LOANS, ROLE_ACCOUNTS, ROLE_default-roles-master
 * </pre>
 * <p>
 * 🔑 兩個對不上的地方：
 * <ol>
 *   <li>角色藏在 {@code realm_access.roles} 這個巢狀結構裡，Spring 不知道要去那裡撈</li>
 *   <li>Spring 的 {@code hasRole("X")} 實際比對的是 {@code "ROLE_X"}，前綴要自己加</li>
 * </ol>
 * <p>
 * ⚠ 這個類別「不是」Spring bean（沒有 @Component）—— 由 SecurityConfig 直接 new 出來，
 * 因為它只是一個純函式，沒有任何依賴。
 */
public class KeycloakRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt source) {
        // 1. 從 JWT 的 claims 撈出 realm_access 這一段
        //    ⚠ 型別是 Map<String,Object> —— JWT 解析後就是巢狀的 Map，只能硬轉。
        Map<String, Object> realmAccess = (Map<String, Object>) source.getClaims().get("realm_access");

        // 2. 沒有角色就回空清單（不是 null）
        //    ⚠ 回 null 會讓 Spring Security 拋 NPE。
        //    什麼時候會沒有：client 沒指派任何 realm role，或用的是 client role
        //    （那會在 resource_access 底下，這個轉換器撈不到）。
        if (realmAccess == null || realmAccess.isEmpty()) {
            return new ArrayList<>();
        }

        // 3. 每個角色名稱前面加上 ROLE_ 前綴，包成 GrantedAuthority
        //    🔑 hasRole("LOANS") 在底層比對的是 "ROLE_LOANS"，這個前綴是 Spring 的慣例。
        //    ⚠ 如果 SecurityConfig 改用 hasAuthority("LOANS")，就「不要」加前綴 ——
        //      hasAuthority 是原字串比對，加了反而對不上。
        return ((List<String>) realmAccess.get("roles"))
                .stream()
                .map(roleName -> "ROLE_" + roleName)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }
}
