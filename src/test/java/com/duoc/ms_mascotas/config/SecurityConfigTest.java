package com.duoc.ms_mascotas.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class SecurityConfigTest {

    @Test
    void agregaAutoridadAdministrativaSoloParaElGrupoDeCognito() {
        var converter = new SecurityConfig().jwtAuthenticationConverter();
        Jwt administrador = Jwt.withTokenValue("admin")
                .header("alg", "none")
                .claim("cognito:groups", List.of("Administradores"))
                .build();
        Jwt usuario = Jwt.withTokenValue("user")
                .header("alg", "none")
                .claim("cognito:groups", List.of("Usuarios"))
                .build();

        assertThat(converter.convert(administrador).getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_ADMINISTRADOR");
        assertThat(converter.convert(usuario).getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .doesNotContain("ROLE_ADMINISTRADOR");
    }
}
