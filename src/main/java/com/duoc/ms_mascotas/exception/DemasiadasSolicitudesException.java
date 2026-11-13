package com.duoc.ms_mascotas.exception;

/** Se superó un límite de uso por usuario (reportes por día o URLs de subida por hora). */
public class DemasiadasSolicitudesException extends RuntimeException {

    private final long reintentarEnSegundos;

    public DemasiadasSolicitudesException(long reintentarEnSegundos) {
        super("Demasiadas solicitudes");
        this.reintentarEnSegundos = reintentarEnSegundos;
    }

    public long getReintentarEnSegundos() {
        return reintentarEnSegundos;
    }
}
