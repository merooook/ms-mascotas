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

    // Revierte la decisión del 30-ago-2026 de no persistir el correo: sin él,
    // ms-alertas no tenía de dónde sacar el destinatario para el flujo de
    // Contacto (GET /internal/mascotas/{id}/contacto ya lo esperaba). Nunca
    // se expone en MascotaResponseDTO (la respuesta pública) — solo lo
    // devuelve ese endpoint interno.
    private String emailContacto;

    private TipoMascota tipoMascota;

    private String nombre;

    private String fotografia;

    private Estado estado;

    // Origen del reporte (EXTRAVIADO o ENCONTRADO), fijado una sola vez al crear
    // y que NUNCA cambia — a diferencia de "estado", que sí se sobrescribe con
    // PATCH /estado. Sin esto, al reunificar una mascota se pierde para siempre
    // si originalmente fue reportada como perdida o encontrada, y R-N°8 (perdidas
    // vs encontradas vs reunidas, en números y porcentajes) no se puede calcular.
    // Nullable a propósito: los documentos creados antes de este campo no lo
    // tienen (ver mapToResponseDTO() para el valor por defecto que se les asigna).
    private Estado tipoReporte;

    @GeoSpatialIndexed
    private GeoJsonPoint ubicacion;

    // Campo propio (no dentro de caracteristicas) porque es un filtro
    // central del producto — R-N°5 pide buscar mascotas por comuna, y un
    // Map dinámico no es cómodo de indexar ni de filtrar con Criteria.
    @Indexed
    private String comuna;

    private LocalDateTime fecha;

    private String descripcion;

    @Builder.Default
    private Map<String, Object> caracteristicas = new HashMap<>();
}
