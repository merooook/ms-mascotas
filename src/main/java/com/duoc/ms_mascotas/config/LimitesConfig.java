package com.duoc.ms_mascotas.config;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.duoc.ms_mascotas.service.LimitadorUso;

/** Cuotas por usuario: sin ellas una cuenta podía llenar la cola de moderación y subir archivos sin tope. */
@Configuration
public class LimitesConfig {

    @Bean
    Clock reloj() {
        return Clock.systemUTC();
    }

    /** Reportes nuevos por usuario cada 24 horas (cada uno genera moderación y eventos). */
    @Bean
    LimitadorUso limiteReportes(@Value("${mascotas.limite-reportes-por-dia:10}") int maximo, Clock reloj) {
        return new LimitadorUso(maximo, Duration.ofHours(24), reloj);
    }

    /** URLs firmadas de subida (5 MB cada una) por usuario cada hora. */
    @Bean
    LimitadorUso limiteSubidas(@Value("${mascotas.limite-subidas-por-hora:30}") int maximo, Clock reloj) {
        return new LimitadorUso(maximo, Duration.ofHours(1), reloj);
    }
}
