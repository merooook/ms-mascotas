package com.duoc.ms_mascotas.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Evento que no se pudo publicar en SNS (caída, credenciales vencidas del lab...) y se reintenta después. Sin
 * esto un evento perdido dejaba el reporte sin alerta, o una alerta activa y el correo de cierre sin enviar, para
 * siempre. No contiene datos personales: solo el tipo de evento y el JSON (ids, coordenadas o correo del reporte
 * para "mascota.reunificada"), y se borra al publicarse.
 */
@Document(collection = "eventos_pendientes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventoPendiente {

    @Id
    private String id;
    private String tipoEvento;
    private String mascotaId;
    private String payloadJson;
    private int intentos;
    private Instant creadoEn;
    @Indexed
    private Instant proximoIntento;
    /* true cuando se agotaron los intentos: queda para revisión manual y deja de reintentarse */
    private boolean descartado;
}
