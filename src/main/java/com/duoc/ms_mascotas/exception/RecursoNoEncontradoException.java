package com.duoc.ms_mascotas.exception;

/** El reporte (u otro recurso) no existe. Se responde 404. */
public class RecursoNoEncontradoException extends RuntimeException {
    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
