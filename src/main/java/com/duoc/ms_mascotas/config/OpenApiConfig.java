package com.duoc.ms_mascotas.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI mascotasOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("API de Mascotas")
                        .description("API para registrar mascotas perdidas y extraviadas")
                        .version("v1"));
    }
}