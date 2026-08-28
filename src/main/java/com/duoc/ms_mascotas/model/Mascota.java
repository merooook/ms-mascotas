package com.duoc.ms_mascotas.model;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Document(collection = "mascotas")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Mascota {

    @Id
    private String idMascota;

    @Indexed
    private String usuarioId;

    private TipoMascota tipoMascota;

    private String nombre;

    private String color;

    private String fotografia;

    private Estado estado;

    @GeoSpatialIndexed
    private GeoJsonPoint ubicacion;

    private LocalDateTime fecha;

    private String descripcion;

    @Builder.Default
    private Map<String, Object> caracteristicas = new HashMap<>();
}
