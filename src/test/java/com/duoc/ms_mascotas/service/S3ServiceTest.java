package com.duoc.ms_mascotas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class S3ServiceTest {

    private S3Presigner s3Presigner;
    private S3Service s3Service;

    @BeforeEach
    void setUp() {
        s3Presigner = S3Presigner.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test-access-key", "test-secret-key")))
                .build();
        s3Service = new S3Service(s3Presigner);
        ReflectionTestUtils.setField(s3Service, "bucketName", "test-bucket");
    }

    @AfterEach
    void tearDown() {
        s3Presigner.close();
    }

    @Test
    void generaUrlFirmadaPutSinConectarseABucket() {
        var resultado = s3Service.generarUrlFirmada("foto.jpg", "image/jpeg", 12345);
        URI url = URI.create(resultado.get("url"));

        assertThat(resultado).containsKeys("url", "key");
        assertThat(resultado.get("key")).startsWith("mascotas/").endsWith("-foto.jpg");
        assertThat(url.getHost()).contains("test-bucket");
        assertThat(url.getQuery())
                .contains("X-Amz-Algorithm=AWS4-HMAC-SHA256")
                .contains("X-Amz-Signature=")
                .contains("X-Amz-Expires=300");
    }

    @Test
    void aceptaFormatosPermitidosYArchivoEnElLimiteDeCincoMb() {
        var jpeg = s3Service.generarUrlFirmada("foto.jpeg", "image/jpeg", 5 * 1024 * 1024);
        var png = s3Service.generarUrlFirmada("foto.png", "image/png", 12345);
        var webp = s3Service.generarUrlFirmada("foto.webp", "image/webp", 12345);

        assertThat(jpeg.get("url")).contains("X-Amz-Signature=");
        assertThat(jpeg.get("key")).endsWith("-foto.jpeg");
        assertThat(png.get("url")).contains("X-Amz-Signature=");
        assertThat(png.get("key")).endsWith("-foto.png");
        assertThat(webp.get("url")).contains("X-Amz-Signature=");
        assertThat(webp.get("key")).endsWith("-foto.webp");
    }

    @Test
    void rechazaTipoDeContenidoQueNoCoincideConExtension() {
        assertThatThrownBy(() -> s3Service.generarUrlFirmada("foto.jpg", "image/png", 12345))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Solo se permiten imágenes JPG, PNG o WEBP");
    }

    @Test
    void rechazaArchivosSobreElLimiteDeCincoMb() {
        assertThatThrownBy(() -> s3Service.generarUrlFirmada("foto.jpg", "image/jpeg", 5 * 1024 * 1024 + 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La imagen debe pesar entre 1 byte y 5 MB");
    }

        @Test
        void rechazaTamanosCeroYNegativos() {
        assertThatThrownBy(() -> s3Service.generarUrlFirmada("foto.jpg", "image/jpeg", 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("La imagen debe pesar entre 1 byte y 5 MB");
        assertThatThrownBy(() -> s3Service.generarUrlFirmada("foto.jpg", "image/jpeg", -1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("La imagen debe pesar entre 1 byte y 5 MB");
        }

    @Test
    void rechazaNombresDeArchivoConCaracteresNoPermitidos() {
        assertThatThrownBy(() -> s3Service.generarUrlFirmada("../foto.jpg", "image/jpeg", 12345))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Solo se permiten imágenes JPG, PNG o WEBP");
    }
}