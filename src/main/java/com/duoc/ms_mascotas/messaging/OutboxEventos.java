package com.duoc.ms_mascotas.messaging;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.duoc.ms_mascotas.model.EventoPendiente;
import com.duoc.ms_mascotas.repository.EventoPendienteRepository;

/**
 * Publica eventos en SNS y, si falla, los guarda en Mongo para reintentarlos con espera creciente.
 *
 * Es una "bandeja de reintentos", no un outbox transaccional: el evento se guarda DESPUÉS de que el cambio ya
 * quedó en Mongo, así que cubre la caída o lentitud de SNS (el caso real: credenciales del lab vencidas), pero no
 * un reinicio justo entre el guardado del reporte y el intento de publicar. Una sola instancia reintenta a la vez
 * de forma segura; con varias podría publicarse un evento dos veces, y los consumidores toleran duplicados.
 */
@Component
public class OutboxEventos {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventos.class);

    static final int LOTE = 20;
    static final int MAX_INTENTOS = 50;
    private static final Duration ESPERA_BASE = Duration.ofSeconds(30);
    private static final Duration ESPERA_MAXIMA = Duration.ofHours(1);

    private final SnsEventPublisher publisher;
    private final EventoPendienteRepository repositorio;
    private final Clock reloj;

    public OutboxEventos(SnsEventPublisher publisher, EventoPendienteRepository repositorio, Clock reloj) {
        this.publisher = publisher;
        this.repositorio = repositorio;
        this.reloj = reloj;
    }

    /** Intenta publicar; si falla lo deja para reintento. Nunca lanza: el cambio ya está guardado. */
    public void publicar(String tipoEvento, Object evento, String mascotaId) {
        String json;
        try {
            json = publisher.serializar(tipoEvento, evento);
        } catch (RuntimeException e) {
            log.error("No se pudo serializar el evento {} de la mascota {}", tipoEvento, mascotaId, e);
            return;
        }
        try {
            publisher.publicarJson(tipoEvento, json);
            log.info("Evento {} publicado: idMascota={}", tipoEvento, mascotaId);
        } catch (RuntimeException e) {
            log.error("No se pudo publicar el evento {} de la mascota {}; queda para reintento", tipoEvento, mascotaId, e);
            guardarParaReintento(tipoEvento, json, mascotaId);
        }
    }

    private void guardarParaReintento(String tipoEvento, String json, String mascotaId) {
        try {
            Instant ahora = reloj.instant();
            repositorio.save(EventoPendiente.builder()
                    .tipoEvento(tipoEvento).mascotaId(mascotaId).payloadJson(json).intentos(1)
                    .creadoEn(ahora).proximoIntento(ahora.plus(ESPERA_BASE)).build());
        } catch (RuntimeException e) {
            /* ni siquiera Mongo respondió: queda solo el log de arriba */
            log.error("No se pudo guardar el evento {} de la mascota {} para reintento", tipoEvento, mascotaId, e);
        }
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void reintentar() {
        Instant ahora = reloj.instant();
        List<EventoPendiente> pendientes = repositorio
                .findByDescartadoFalseAndProximoIntentoLessThanEqualOrderByCreadoEnAsc(ahora, PageRequest.of(0, LOTE));
        for (EventoPendiente evento : pendientes) {
            try {
                publisher.publicarJson(evento.getTipoEvento(), evento.getPayloadJson());
                repositorio.deleteById(evento.getId());
                log.info("Evento {} reenviado tras {} intento(s): idMascota={}",
                        evento.getTipoEvento(), evento.getIntentos(), evento.getMascotaId());
            } catch (RuntimeException e) {
                registrarFallo(evento, ahora, e);
            }
        }
    }

    private void registrarFallo(EventoPendiente evento, Instant ahora, RuntimeException e) {
        int intentos = evento.getIntentos() + 1;
        evento.setIntentos(intentos);
        if (intentos >= MAX_INTENTOS) {
            evento.setDescartado(true);
            log.error("Evento {} de la mascota {} descartado tras {} intentos; requiere revision manual",
                    evento.getTipoEvento(), evento.getMascotaId(), intentos);
        } else {
            long segundos = Math.min(ESPERA_BASE.toSeconds() * (1L << Math.min(intentos - 1, 10)), ESPERA_MAXIMA.toSeconds());
            evento.setProximoIntento(ahora.plusSeconds(segundos));
            log.warn("Reintento {} del evento {} fallo ({}); proximo en {} s",
                    intentos, evento.getTipoEvento(), e.getClass().getSimpleName(), segundos);
        }
        try {
            repositorio.save(evento);
        } catch (RuntimeException ex) {
            log.error("No se pudo actualizar el evento pendiente {}", evento.getId(), ex);
        }
    }
}
