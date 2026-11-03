package com.duoc.ms_mascotas.exception;

/** La persona autenticada no es la dueña del reporte. Se responde 403. */
public class AccesoDenegadoException extends RuntimeException {
    public AccesoDenegadoException(String mensaje) {
        super(mensaje);
    }
}
