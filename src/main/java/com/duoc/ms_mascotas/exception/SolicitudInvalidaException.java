package com.duoc.ms_mascotas.exception;

/**
 * Entrada inválida detectada por las reglas del propio servicio. Su mensaje está pensado para quien llama y sale
 * tal cual en el 400. Se distingue de IllegalArgumentException, que también lanzan las librerías: esos mensajes
 * internos ya no se devuelven al cliente.
 */
public class SolicitudInvalidaException extends RuntimeException {

    public SolicitudInvalidaException(String mensaje) {
        super(mensaje);
    }
}
