package com.duoc.ms_mascotas.service;

import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.services.s3.S3Client;

/**
 * Qué se acepta como "fotografía" de un reporte y cómo se borra al eliminarlo.
 *
 * Antes cualquier texto de hasta 500 caracteres valía: una URL externa (píxel de rastreo, imagen
 * inapropiada) quedaba pública y el navegador de cada visitante la cargaba, filtrando su IP. Ahora
 * solo se aceptan:
 *  - una clave que generó este servicio (S3Service: "mascotas/<uuid>-<archivo>"), sola o como URL del bucket;
 *  - las imágenes de relleno de los prefijos configurados (mascotas.foto.prefijos-externos), que el
 *    frontend usa cuando el reporte no lleva foto.
 */
@Service
public class FotografiaService {

    private static final Logger log = LoggerFactory.getLogger(FotografiaService.class);

    /* misma forma que S3Service.generarUrlFirmada: "mascotas/" + UUID + "-" + nombre de archivo validado */
    private static final Pattern CLAVE = Pattern.compile(
            "^mascotas/[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}-[a-zA-Z0-9][a-zA-Z0-9._-]{0,99}\\.(jpg|jpeg|png|webp)$");

    private final S3Client s3Client;
    private final String bucket;
    private final List<String> prefijosDelBucket;
    private final List<String> prefijosExternos;

    public FotografiaService(
            S3Client s3Client,
            @Value("${aws.s3.bucket-name}") String bucket,
            @Value("${aws.s3.region:us-east-1}") String region,
            @Value("${mascotas.foto.prefijos-externos:https://images.unsplash.com/}") List<String> prefijosExternos) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.prefijosDelBucket = List.of(
                "https://" + bucket + ".s3." + region + ".amazonaws.com/",
                "https://" + bucket + ".s3.amazonaws.com/");
        this.prefijosExternos = prefijosExternos;
    }

    /** null (sin foto) es válido; un texto en blanco o con un origen no permitido, no. */
    public boolean esPermitida(String fotografia) {
        if (fotografia == null) {
            return true;
        }
        if (extraerClave(fotografia) != null) {
            return true;
        }
        return fotografia.length() <= 500
                && fotografia.chars().noneMatch(Character::isWhitespace)
                && prefijosExternos.stream().anyMatch(p -> !p.isBlank() && fotografia.startsWith(p.strip()));
    }

    /** Borra la foto del bucket si es nuestra; las de relleno y las claves ajenas se ignoran. No lanza error. */
    public void eliminar(String fotografia) {
        String clave = extraerClave(fotografia);
        if (clave == null) {
            return;
        }
        try {
            s3Client.deleteObject(b -> b.bucket(bucket).key(clave));
            log.info("Foto eliminada del bucket: {}", clave);
        } catch (RuntimeException e) {
            /* el reporte ya se borró; que no se pueda limpiar la foto no debe romper la petición */
            log.warn("No se pudo eliminar la foto {} del bucket ({})", clave, e.getClass().getSimpleName());
        }
    }

    /* clave de S3 si el valor es una clave nuestra o la URL (sin firma) de nuestro bucket; si no, null */
    String extraerClave(String fotografia) {
        if (fotografia == null) {
            return null;
        }
        if (CLAVE.matcher(fotografia).matches()) {
            return fotografia;
        }
        for (String prefijo : prefijosDelBucket) {
            if (fotografia.startsWith(prefijo)) {
                String clave = fotografia.substring(prefijo.length());
                return CLAVE.matcher(clave).matches() ? clave : null;
            }
        }
        return null;
    }
}
