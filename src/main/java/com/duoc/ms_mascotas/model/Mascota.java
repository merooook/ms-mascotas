package com.duoc.ms_mascotas.model;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "mascota")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Mascota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idMascota;

    @Column(name = "usuario_id", nullable = false)
    private String usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_mascota", nullable = false, length = 20)
    private TipoMascota tipoMascota;

    @Column(length = 100)
    private String nombre;

    @Column(length = 50)
    private String color;

    @Column(length = 255)
    private String fotografia;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado;

    @Column(length = 255)
    private String ubicacion;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @JdbcTypeCode(SqlTypes.JSON) // Hibernate: usar tipo JSON
    @Column(columnDefinition = "jsonb") // JPA: crear columna como JSONB
    @Builder.Default //por defecto hashmap vacío, no null
    private Map<String, Object> caracteristicas = new HashMap<>(); // Java: guardar cualquier estructura
}
