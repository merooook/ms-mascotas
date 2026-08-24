package com.duoc.ms_mascotas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.duoc.ms_mascotas.model.Estado;
import com.duoc.ms_mascotas.model.Mascota;

public interface MascotaRepository extends JpaRepository<Mascota, Long>{

	Page<Mascota> findByEstado(Estado estado, Pageable pageable);

	Page<Mascota> findByDuenoId(String duenoId, Pageable pageable);
}
