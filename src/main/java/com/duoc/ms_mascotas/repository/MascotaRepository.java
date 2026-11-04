package com.duoc.ms_mascotas.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.duoc.ms_mascotas.model.Estado;
import com.duoc.ms_mascotas.model.Mascota;

public interface MascotaRepository extends MongoRepository<Mascota, String> {

    Page<Mascota> findByEstado(Estado estado, Pageable pageable);

    Page<Mascota> findByUsuarioId(String usuarioId, Pageable pageable);

    /* ¿algun OTRO reporte usa esta foto? (la foto puede estar guardada como clave o como URL del bucket) */
    boolean existsByFotografiaInAndIdMascotaNot(java.util.Collection<String> formas, String idMascota);
}
