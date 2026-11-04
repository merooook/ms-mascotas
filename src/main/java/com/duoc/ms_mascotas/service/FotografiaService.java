package com.duoc.ms_mascotas.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
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
 *  - una clave que generó este servicio para ESA persona (S3Service: "mascotas/<hash del sub>/<uuid>-<archivo>"),
 *    sola o como URL del bucket. La clave lleva el hash del sub de quien pidió la URL firmada: copiar la clave de
 *    otra persona (las fotos de los reportes aprobados son públicas) ya no sirve para asociarla a un reporte propio
 *    y, de paso, borrarla;
 *  - las imágenes de relleno de los prefijos configurados (mascotas.foto.prefijos-externos), que el
 *    frontend usa cuando el reporte no lleva foto.
 *
 * Aun así, el borrado del bucket lo decide MascotaService solo si ningún otro reporte usa la misma foto.
 */
@Service
public class FotografiaService {

    private static final Logger log = LoggerFactory.getLogger(FotografiaService.class);

    private static final String UUID_Y_ARCHIVO =
            "[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}-[a-zA-Z0-9][a-zA-Z0-9._-]{0,99}\\.(jpg|jpeg|png|webp)";

    /* misma forma que S3Service.generarUrlFirmada: "mascotas/" + hash(sub) + "/" + UUID + "-" + archivo validado */
    private static final Pattern CLAVE = Pattern.compile("^mascotas/([0-9a-f]{16})/" + UUID_Y_ARCHIVO + "$");

    /* forma anterior, sin dueño: se sigue reconociendo para poder borrarla, pero ya no se acepta en reportes nuevos */
    private static final Pattern CLAVE_ANTERIOR = Pattern.compile("^mascotas/" + UUID_Y_ARCHIVO + "$");

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

    /** Identificador del dueño dentro de la clave de S3: 16 caracteres del SHA-256 del sub (no se expone el sub). */
    public static String hashDe(String sub) {
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256").digest(sub.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumen, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    /**
     * null (sin foto) es válido. Una clave o URL de nuestro bucket solo vale si la generó este servicio para
     * "sub"; un texto en blanco o con un origen no permitido, no.
     */
    public boolean esPermitida(String fotografia, String sub) {
        if (fotografia == null) {
            return true;
        }
        String clave = extraerClave(fotografia);
        if (clave != null) {
            Matcher m = CLAVE.matcher(clave);
            return sub != null && m.matches() && m.group(1).equals(hashDe(sub));
        }
        return fotografia.length() <= 500
                && fotografia.chars().noneMatch(Character::isWhitespace)
                && prefijosExternos.stream().anyMatch(p -> !p.isBlank() && fotografia.startsWith(p.strip()));
    }

    /**
     * Las formas con que una foto nuestra puede estar guardada en un reporte (la clave sola, o la URL del bucket
     * con cualquiera de sus dos hosts). Vacío si no es una foto nuestra (relleno, texto ajeno o null).
     */
    public List<String> formasDe(String fotografia) {
        String clave = extraerClave(fotografia);
        if (clave == null) {
            return List.of();
        }
        List<String> formas = new ArrayList<>();
        formas.add(clave);
        prefijosDelBucket.forEach(p -> formas.add(p + clave));
        return formas;
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
        if (esClave(fotografia)) {
            return fotografia;
        }
        for (String prefijo : prefijosDelBucket) {
            if (fotografia.startsWith(prefijo)) {
                String clave = fotografia.substring(prefijo.length());
                return esClave(clave) ? clave : null;
            }
        }
        return null;
    }

    private static boolean esClave(String texto) {
        return CLAVE.matcher(texto).matches() || CLAVE_ANTERIOR.matcher(texto).matches();
    }
}
