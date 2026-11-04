package com.duoc.ms_mascotas.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.duoc.ms_mascotas.model.EventoPendiente;

public interface EventoPendienteRepository extends MongoRepository<EventoPendiente, String> {

    /* los más viejos primero: se conserva el orden en que ocurrieron los cambios */
    List<EventoPendiente> findByDescartadoFalseAndProximoIntentoLessThanEqualOrderByCreadoEnAsc(Instant ahora, Pageable pagina);

    long countByDescartadoFalse();

    long countByDescartadoTrue();
}
