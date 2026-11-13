package com.duoc.ms_mascotas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

class FotografiaServiceTest {

    private static final String UUID = "123e4567-e89b-12d3-a456-426614174000";
    private static final String SUB = "sub-de-ana";
    private static final String OTRO_SUB = "sub-de-beto";
    /* la clave lleva el hash del sub de quien pidió la URL firmada */
    private static final String CLAVE = "mascotas/" + FotografiaService.hashDe(SUB) + "/" + UUID + "-foto.jpg";
    private static final String URL_BUCKET = "https://mi-bucket.s3.us-east-1.amazonaws.com/" + CLAVE;
    private static final String CLAVE_ANTERIOR = "mascotas/" + UUID + "-foto.jpg";
    private static final String RELLENO = "https://images.unsplash.com/photo-1547482354-89d4259dbc4b?w=600&h=600&fit=crop";

    private S3Client s3Client;
    private FotografiaService servicio;

    @BeforeEach
    void preparar() {
        s3Client = mock(S3Client.class);
        servicio = new FotografiaService(s3Client, "mi-bucket", "us-east-1", List.of("https://images.unsplash.com/"));
    }

    // ---------- qué se acepta ----------

    @Test
    void sinFotografiaEsValido() {
        assertThat(servicio.esPermitida(null, SUB)).isTrue();
    }

    @Test
    void aceptaLaClaveQueGeneroElServicioDeSubidaParaEsaPersona() {
        assertThat(servicio.esPermitida(CLAVE, SUB)).isTrue();
    }

    @Test
    void aceptaLaUrlSinFirmaDelBucketParaEsaPersona() {
        assertThat(servicio.esPermitida(URL_BUCKET, SUB)).isTrue();
        assertThat(servicio.esPermitida("https://mi-bucket.s3.amazonaws.com/" + CLAVE, SUB)).isTrue();
    }

    @Test
    void laClaveDeOtraPersonaNoSePuedeAsociarAUnReportePropio() {
        /* el hallazgo 1 de la V2: copiar la clave pública de la foto de otra persona y borrarla con un reporte propio */
        assertThat(servicio.esPermitida(CLAVE, OTRO_SUB)).isFalse();
        assertThat(servicio.esPermitida(URL_BUCKET, OTRO_SUB)).isFalse();
        assertThat(servicio.esPermitida(CLAVE, null)).isFalse();
    }

    @Test
    void laClaveSinDuenoDeLaFormaAnteriorYaNoSeAceptaEnReportesNuevos() {
        assertThat(servicio.esPermitida(CLAVE_ANTERIOR, SUB)).isFalse();
        assertThat(servicio.esPermitida("https://mi-bucket.s3.us-east-1.amazonaws.com/" + CLAVE_ANTERIOR, SUB)).isFalse();
    }

    @Test
    void elHashDelDuenoEsEstableDistintoPorPersonaYNoExponeElSub() {
        assertThat(FotografiaService.hashDe(SUB)).hasSize(16).matches("[0-9a-f]{16}");
        assertThat(FotografiaService.hashDe(SUB)).isEqualTo(FotografiaService.hashDe(SUB));
        assertThat(FotografiaService.hashDe(SUB)).isNotEqualTo(FotografiaService.hashDe(OTRO_SUB));
        assertThat(CLAVE).doesNotContain(SUB);
    }

    @Test
    void aceptaLaImagenDeRellenoQueUsaElFrontend() {
        assertThat(servicio.esPermitida(RELLENO, SUB)).isTrue();
    }

    @Test
    void rechazaOrigenesExternosYTextoLibre() {
        for (String mala : List.of(
                "https://sitio-malo.example/pixel.gif",
                "http://images.unsplash.com/foto.jpg",                /* http, no https */
                "https://images.unsplash.com.sitio-malo.example/x",   /* prefijo falso */
                "javascript:alert(1)",
                "cualquier texto",
                "",
                "   ",
                "https://images.unsplash.com/con espacio.jpg",
                "mascotas/../../secreto.jpg",
                "mascotas/no-es-uuid-foto.jpg",
                "mascotas/" + FotografiaService.hashDe(SUB) + "/../" + UUID + "-foto.jpg",
                "https://otro-bucket.s3.us-east-1.amazonaws.com/" + CLAVE,
                "https://mi-bucket.s3.us-east-1.amazonaws.com/otra-carpeta/" + UUID + "-foto.jpg",
                URL_BUCKET + "?X-Amz-Signature=abc")) {
            assertThat(servicio.esPermitida(mala, SUB)).as(mala).isFalse();
        }
    }

    @Test
    void rechazaUnaUrlExternaDemasiadoLarga() {
        assertThat(servicio.esPermitida("https://images.unsplash.com/" + "a".repeat(500), SUB)).isFalse();
    }

    // ---------- formas con que puede estar guardada ----------

    @Test
    void lasFormasDeUnaFotoNuestraSonLaClaveYLasUrlsDelBucket() {
        assertThat(servicio.formasDe(CLAVE)).containsExactlyInAnyOrder(CLAVE, URL_BUCKET, "https://mi-bucket.s3.amazonaws.com/" + CLAVE);
        /* guardada como URL, se reconoce la misma foto */
        assertThat(servicio.formasDe(URL_BUCKET)).contains(CLAVE);
    }

    @Test
    void unaFotoQueNoEsNuestraNoTieneFormas() {
        assertThat(servicio.formasDe(null)).isEmpty();
        assertThat(servicio.formasDe(RELLENO)).isEmpty();
        assertThat(servicio.formasDe("cualquier texto")).isEmpty();
    }

    // ---------- borrar ----------

    @SuppressWarnings("unchecked")
    private DeleteObjectRequest solicitudDeBorrado() {
        ArgumentCaptor<Consumer<DeleteObjectRequest.Builder>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(s3Client).deleteObject(captor.capture());
        DeleteObjectRequest.Builder builder = DeleteObjectRequest.builder();
        captor.getValue().accept(builder);
        return builder.build();
    }

    @Test
    void borraDelBucketLaFotoPropia() {
        servicio.eliminar(URL_BUCKET);

        DeleteObjectRequest solicitud = solicitudDeBorrado();
        assertThat(solicitud.bucket()).isEqualTo("mi-bucket");
        assertThat(solicitud.key()).isEqualTo(CLAVE);
    }

    @Test
    void borraTambienCuandoSeGuardoSoloLaClave() {
        servicio.eliminar(CLAVE);

        assertThat(solicitudDeBorrado().key()).isEqualTo(CLAVE);
    }

    @Test
    void sigueBorrandoLasFotosAntiguasSinDueno() {
        servicio.eliminar(CLAVE_ANTERIOR);

        assertThat(solicitudDeBorrado().key()).isEqualTo(CLAVE_ANTERIOR);
    }

    @Test
    @SuppressWarnings("unchecked")
    void noTocaElBucketConLasImagenesDeRellenoNiConValoresAjenos() {
        servicio.eliminar(RELLENO);
        servicio.eliminar("https://sitio-malo.example/x.png");
        servicio.eliminar("mascotas/../../secreto.jpg");
        servicio.eliminar(null);

        verify(s3Client, never()).deleteObject(any(Consumer.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void siElBorradoFallaNoLanzaError() {
        doThrow(new RuntimeException("sin credenciales")).when(s3Client).deleteObject(any(Consumer.class));

        servicio.eliminar(CLAVE);

        verify(s3Client).deleteObject(any(Consumer.class));
    }
}
