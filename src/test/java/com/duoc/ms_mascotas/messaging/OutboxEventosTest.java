package com.duoc.ms_mascotas.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import com.duoc.ms_mascotas.model.EventoPendiente;
import com.duoc.ms_mascotas.repository.EventoPendienteRepository;

class OutboxEventosTest {

    private static final Instant AHORA = Instant.parse("2026-11-03T12:00:00Z");

    private SnsEventPublisher publisher;
    private EventoPendienteRepository repositorio;
    private OutboxEventos outbox;

    @BeforeEach
    void preparar() {
        publisher = mock(SnsEventPublisher.class);
        repositorio = mock(EventoPendienteRepository.class);
        outbox = new OutboxEventos(publisher, repositorio, Clock.fixed(AHORA, ZoneOffset.UTC));
        when(publisher.serializar(any(), any())).thenReturn("{\"mascotaId\":\"m1\"}");
    }

    @Test
    void siSnsFuncionaPublicaYNoGuardaNada() {
        outbox.publicar("mascota.reportada", new Object(), "m1");

        verify(publisher).publicarJson("mascota.reportada", "{\"mascotaId\":\"m1\"}");
        verify(repositorio, never()).save(any());
    }

    @Test
    void siSnsFallaElEventoQuedaGuardadoParaReintentarYNoSePropagaElError() {
        doThrow(new RuntimeException("SNS caído")).when(publisher).publicarJson(any(), any());

        outbox.publicar("mascota.reunificada", new Object(), "m1");

        ArgumentCaptor<EventoPendiente> captor = ArgumentCaptor.forClass(EventoPendiente.class);
        verify(repositorio).save(captor.capture());
        EventoPendiente guardado = captor.getValue();
        assertThat(guardado.getTipoEvento()).isEqualTo("mascota.reunificada");
        assertThat(guardado.getMascotaId()).isEqualTo("m1");
        assertThat(guardado.getPayloadJson()).isEqualTo("{\"mascotaId\":\"m1\"}");
        assertThat(guardado.getProximoIntento()).isAfter(AHORA);
        assertThat(guardado.isDescartado()).isFalse();
    }

    @Test
    void siTampocoMongoRespondeNoSePropagaElError() {
        doThrow(new RuntimeException("SNS caído")).when(publisher).publicarJson(any(), any());
        doThrow(new RuntimeException("Mongo caído")).when(repositorio).save(any());

        outbox.publicar("mascota.reportada", new Object(), "m1");
    }

    @Test
    void siNoSePuedeSerializarNoSeIntentaNada() {
        when(publisher.serializar(any(), any())).thenThrow(new IllegalStateException("no serializable"));

        outbox.publicar("mascota.reportada", new Object(), "m1");

        verify(publisher, never()).publicarJson(any(), any());
        verify(repositorio, never()).save(any());
    }

    private EventoPendiente pendiente(String id, int intentos) {
        return EventoPendiente.builder().id(id).tipoEvento("mascota.reportada").mascotaId("m1")
                .payloadJson("{\"mascotaId\":\"m1\"}").intentos(intentos).creadoEn(AHORA.minusSeconds(600))
                .proximoIntento(AHORA.minusSeconds(1)).build();
    }

    @Test
    void elReintentoExitosoPublicaYBorraElEvento() {
        when(repositorio.findByDescartadoFalseAndProximoIntentoLessThanEqualOrderByCreadoEnAsc(eq(AHORA), any(Pageable.class)))
                .thenReturn(List.of(pendiente("e1", 1)));

        outbox.reintentar();

        verify(publisher).publicarJson("mascota.reportada", "{\"mascotaId\":\"m1\"}");
        verify(repositorio).deleteById("e1");
    }

    @Test
    void siElReintentoFallaSeEsperaMasEnElSiguiente() {
        EventoPendiente evento = pendiente("e1", 1);
        when(repositorio.findByDescartadoFalseAndProximoIntentoLessThanEqualOrderByCreadoEnAsc(eq(AHORA), any(Pageable.class)))
                .thenReturn(List.of(evento));
        doThrow(new RuntimeException("SNS caído")).when(publisher).publicarJson(any(), any());

        outbox.reintentar();

        verify(repositorio, never()).deleteById(any());
        assertThat(evento.getIntentos()).isEqualTo(2);
        Duration espera = Duration.between(AHORA, evento.getProximoIntento());
        assertThat(espera).isGreaterThanOrEqualTo(Duration.ofSeconds(60)).isLessThanOrEqualTo(Duration.ofHours(1));
        verify(repositorio).save(evento);
    }

    @Test
    void laEsperaCreceYNuncaPasaDeUnaHora() {
        EventoPendiente evento = pendiente("e1", 20);
        when(repositorio.findByDescartadoFalseAndProximoIntentoLessThanEqualOrderByCreadoEnAsc(eq(AHORA), any(Pageable.class)))
                .thenReturn(List.of(evento));
        doThrow(new RuntimeException("SNS caído")).when(publisher).publicarJson(any(), any());

        outbox.reintentar();

        assertThat(Duration.between(AHORA, evento.getProximoIntento())).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void tras50IntentosSeDescartaParaRevisionManual() {
        EventoPendiente evento = pendiente("e1", OutboxEventos.MAX_INTENTOS - 1);
        when(repositorio.findByDescartadoFalseAndProximoIntentoLessThanEqualOrderByCreadoEnAsc(eq(AHORA), any(Pageable.class)))
                .thenReturn(List.of(evento));
        doThrow(new RuntimeException("SNS caído")).when(publisher).publicarJson(any(), any());

        outbox.reintentar();

        assertThat(evento.isDescartado()).isTrue();
        verify(repositorio).save(evento);
        verify(repositorio, never()).deleteById(any());
    }

    @Test
    void unEventoQueFallaNoImpideReintentarLosSiguientes() {
        EventoPendiente malo = pendiente("e1", 1);
        malo.setPayloadJson("malo");
        EventoPendiente bueno = pendiente("e2", 1);
        when(repositorio.findByDescartadoFalseAndProximoIntentoLessThanEqualOrderByCreadoEnAsc(eq(AHORA), any(Pageable.class)))
                .thenReturn(List.of(malo, bueno));
        doThrow(new RuntimeException("falla")).when(publisher).publicarJson(any(), eq("malo"));

        outbox.reintentar();

        verify(repositorio).deleteById("e2");
        verify(repositorio, never()).deleteById("e1");
    }
}
