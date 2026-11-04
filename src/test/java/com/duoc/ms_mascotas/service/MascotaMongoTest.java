package com.duoc.ms_mascotas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.duoc.ms_mascotas.messaging.OutboxEventos;
import com.duoc.ms_mascotas.model.Estado;
import com.duoc.ms_mascotas.model.Mascota;
import com.duoc.ms_mascotas.repository.MascotaRepository;

/**
 * Lo que los mocks no pueden validar se prueba contra un Mongo real: que la confirmación de reunificación gane una
 * sola vez aunque lleguen dos clics a la vez, y que la consulta por foto encuentre a "otro" reporte. Usa la base
 * "sanos_mascotas_test" de un Mongo en localhost:27017; si no hay uno (por ejemplo en CI) las pruebas se omiten.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.mongodb.uri=mongodb://localhost:27017/sanos_mascotas_test?serverSelectionTimeoutMS=1500")
class MascotaMongoTest {

    @Autowired
    private MascotaService servicio;
    @Autowired
    private MascotaRepository repositorio;
    @Autowired
    private MongoTemplate mongo;

    @MockitoBean
    private OutboxEventos eventos;

    @BeforeEach
    void requiereMongo() {
        boolean disponible;
        try {
            mongo.getDb().runCommand(new Document("ping", 1));
            disponible = true;
        } catch (RuntimeException e) {
            disponible = false;
        }
        assumeTrue(disponible, "No hay un Mongo en localhost:27017: se omiten las pruebas contra Mongo real");
        mongo.getDb().drop();
    }

    private Mascota conToken(String token) {
        return repositorio.save(Mascota.builder().usuarioId("sub-dueno").emailContacto("dueno@example.com")
                .nombre("Firu").estado(Estado.EXTRAVIADO).tipoReporte(Estado.EXTRAVIADO)
                .tokenReunificacion(token).tokenReunificacionExpira(LocalDateTime.now().plusDays(1)).build());
    }

    @Test
    void dosClicsAlMismoTiempoSoloReunificanUnaVezYPublicanUnSoloEvento() throws Exception {
        Mascota m = conToken("tok-123");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<MascotaService.ResultadoConfirmacion>> resultados = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            resultados.add(pool.submit(() -> {
                salida.await();
                return servicio.confirmarReunificacion(m.getIdMascota(), "tok-123");
            }));
        }
        salida.countDown();
        List<MascotaService.ResultadoConfirmacion> obtenidos = new ArrayList<>();
        for (Future<MascotaService.ResultadoConfirmacion> f : resultados) {
            obtenidos.add(f.get());
        }
        pool.shutdown();

        assertThat(obtenidos).containsExactlyInAnyOrder(
                MascotaService.ResultadoConfirmacion.CONFIRMADA, MascotaService.ResultadoConfirmacion.YA_REUNIFICADA);
        verify(eventos, times(1)).publicar(eq(MascotaService.TIPO_EVENTO_MASCOTA_REUNIFICADA), any(), eq(m.getIdMascota()));
        Mascota despues = repositorio.findById(m.getIdMascota()).orElseThrow();
        assertThat(despues.getEstado()).isEqualTo(Estado.REUNIFICADO);
        assertThat(despues.getTokenReunificacion()).isNull();
    }

    @Test
    void unTokenIncorrectoNoCambiaNadaEnMongo() {
        Mascota m = conToken("tok-real");

        var resultado = servicio.confirmarReunificacion(m.getIdMascota(), "tok-falso");

        assertThat(resultado).isEqualTo(MascotaService.ResultadoConfirmacion.TOKEN_INVALIDO);
        assertThat(repositorio.findById(m.getIdMascota()).orElseThrow().getEstado()).isEqualTo(Estado.EXTRAVIADO);
    }

    @Test
    void laConsultaPorFotoEncuentraSoloAOtrosReportes() {
        String clave = "mascotas/0123456789abcdef/123e4567-e89b-12d3-a456-426614174000-foto.jpg";
        List<String> formas = List.of(clave, "https://b.s3.amazonaws.com/" + clave);
        Mascota a = repositorio.save(Mascota.builder().nombre("A").fotografia(clave).build());
        Mascota b = repositorio.save(Mascota.builder().nombre("B").fotografia("https://b.s3.amazonaws.com/" + clave).build());

        /* la guardada como clave y la guardada como URL se reconocen entre sí; un reporte no se cuenta a sí mismo */
        assertThat(repositorio.existsByFotografiaInAndIdMascotaNot(formas, a.getIdMascota())).isTrue();
        repositorio.deleteById(b.getIdMascota());
        assertThat(repositorio.existsByFotografiaInAndIdMascotaNot(formas, a.getIdMascota())).isFalse();
    }
}
