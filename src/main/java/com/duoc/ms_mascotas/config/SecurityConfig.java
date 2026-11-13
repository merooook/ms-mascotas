package com.duoc.ms_mascotas.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authorization.AuthorizationDecision;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
    private final TokenInterno tokenInterno;

    public SecurityConfig(TokenInterno tokenInterno) {
        this.tokenInterno = tokenInterno;
    }

    // Swagger expone todo el contrato de la API: solo en local (SWAGGER_ENABLED=true), nunca por defecto
    @Value("${springdoc.swagger-ui.enabled:false}")
    private boolean swaggerHabilitado;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Value("${cognito.client-id}")
    private String clientId;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html")
                    .access((autenticacion, contexto) -> new AuthorizationDecision(swaggerHabilitado))
                // Llamadas de servicio a servicio (ms-alertas, ms-chat): no llevan JWT de usuario, pero sí el
                // secreto compartido en X-Internal-Token. Antes era permitAll y solo protegía la red.
                .requestMatchers("/internal/**").access((authentication, contexto) ->
                        new AuthorizationDecision(tokenInterno.valido(contexto.getRequest().getHeader(TokenInterno.HEADER))))
                .requestMatchers("/mascotas/admin", "/mascotas/admin/**").hasRole("ADMINISTRADOR")
                // OJO con el orden: /mascotas/{id} de abajo también matchearía
                // literalmente "/mascotas/mis-mascotas" (un solo segmento), así
                // que esta regla más específica tiene que ir ANTES para que
                // gane ella y ese endpoint (datos del propio usuario) siga
                // exigiendo JWT.
                .requestMatchers(HttpMethod.GET, "/mascotas/mis-mascotas").authenticated()
                // presigned-url también cae en /mascotas/{id}: sin esta regla era público y cualquiera podía
                // pedir URLs firmadas de subida a S3 (5 MB cada una, sin límite de cantidad)
                .requestMatchers(HttpMethod.GET, "/mascotas/presigned-url").authenticated()
                // Listado y detalle son de acceso libre para invitados (misma
                // decisión de UX que ms-alertas: GET /alertas, /alertas/zona).
                .requestMatchers(HttpMethod.GET, "/mascotas", "/mascotas/{id}").permitAll()
                // Público a propósito: lo abre quien hace clic en el link del
                // correo de contacto, sin sesión. El token (no el JWT) es la
                // autorización acá — ver MascotaService.confirmarReunificacion.
                .requestMatchers(HttpMethod.POST, "/mascotas/{id}/confirmar-reunificacion").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                    .decoder(jwtDecoder())
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }

    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>(scopes.convert(jwt));
            List<String> groups = jwt.getClaimAsStringList("cognito:groups");
            if (groups != null && groups.contains("Administradores")) {
                authorities.add(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"));
            }
            return authorities;
        });
        return converter;
    }

    private NimbusJwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();

        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> withClientId = clientIdValidator();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, withClientId));

        return decoder;
    }

    private OAuth2TokenValidator<Jwt> clientIdValidator() {
        return token -> {
            boolean correctUse = "access".equals(token.getClaimAsString("token_use"));
            boolean correctClient = clientId.equals(token.getClaimAsString("client_id"));
            if (correctUse && correctClient) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_token", "El token no fue emitido para esta aplicación", null));
        };
    }
}
