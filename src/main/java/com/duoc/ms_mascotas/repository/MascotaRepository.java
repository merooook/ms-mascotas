package com.duoc.ms_mascotas.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.duoc.ms_mascotas.model.Estado;
import com.duoc.ms_mascotas.model.Mascota;

public interface MascotaRepository extends JpaRepository<Mascota, Long> {

    Page<Mascota> findByEstado(Estado estado, Pageable pageable);

    Page<Mascota> findByUsuarioId(String usuarioId, Pageable pageable);

    @Query(value = """
            SELECT *
            FROM mascota m
            WHERE (:estado IS NULL OR m.estado = :estado)
              AND (:tipoMascota IS NULL OR m.tipo_mascota = :tipoMascota)
              AND (:color IS NULL OR m.color = :color)
            """, nativeQuery = true)
    List<Mascota> filtrarMascotas(
            @Param("estado") String estado,
            @Param("tipoMascota") String tipoMascota,
            @Param("color") String color
    );
}
