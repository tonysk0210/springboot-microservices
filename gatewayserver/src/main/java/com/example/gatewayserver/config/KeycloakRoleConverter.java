package com.example.gatewayserver.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 將 Keycloak JWT 的 realm roles 轉成 Spring Security 權限，讓 Gateway 可以用 hasRole(...) 保護 API。
 */
public final class KeycloakRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt source) {
        // 1. 讀取 JWT 中的 realm_access.roles。
        if (!(source.getClaim("realm_access") instanceof Map<?, ?> realmAccess)
                || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return List.of();
        }

        // 2. 加上 ROLE_ 前綴，轉成 Spring Security 的權限物件。
        return roles.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(role -> !role.isBlank())
                .map(roleName -> "ROLE_" + roleName)
                .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                .toList();
    }
}
