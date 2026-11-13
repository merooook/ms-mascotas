package com.duoc.ms_mascotas.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Secreto de servicio (INTERNAL_API_TOKEN) compartido con el BFF, ms-alertas y ms-chat.
 *
 * Sirve para dos cosas: proteger /internal/** y confiar en los headers de identidad que arma el BFF a partir del
 * id_token (X-User-Email). Sin secreto (o con uno corto) el servicio NO arranca: antes solo avisaba con un warn,
 * rechazaba todo lo interno y ms-alertas y ms-chat mostraban "servicio no disponible" sin pista de la causa.
 */
@Component
public class TokenInterno {

    public static final String HEADER = "X-Internal-Token";
    private static final int LARGO_MINIMO = 32;

    private final byte[] secreto;

    public TokenInterno(@Value("${internal.api-token:}") String secreto) {
        if (secreto == null || secreto.strip().length() < LARGO_MINIMO) {
            throw new IllegalStateException(
                    "INTERNAL_API_TOKEN no esta definido (minimo " + LARGO_MINIMO + " caracteres): "
                            + "los servicios internos no podrian consultar este servicio");
        }
        this.secreto = secreto.strip().getBytes(StandardCharsets.UTF_8);
    }

    /** Comparación en tiempo constante. */
    public boolean valido(String presentado) {
        return presentado != null
                && MessageDigest.isEqual(secreto, presentado.strip().getBytes(StandardCharsets.UTF_8));
    }
}
