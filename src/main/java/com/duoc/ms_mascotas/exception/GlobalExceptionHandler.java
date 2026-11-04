package com.duoc.ms_mascotas.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Respuestas de error uniformes, sin stack traces ni detalles internos en el body. Cada tipo de error
 * tiene su código: antes todo salía como 400 (incluso "no encontrada" y "sin permiso", lo que dejaba
 * averiguar qué ids existen y de quién son) y los errores de Spring caían en el 500 genérico.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        return response(HttpStatus.BAD_REQUEST, "Solicitud inválida", errors);
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<Map<String, Object>> handleNoEncontrado(RecursoNoEncontradoException exception) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), null);
    }

    @ExceptionHandler(AccesoDenegadoException.class)
    public ResponseEntity<Map<String, Object>> handleAccesoDenegado(AccesoDenegadoException exception) {
        return response(HttpStatus.FORBIDDEN, exception.getMessage(), null);
    }

    /* reglas de negocio y entradas inválidas que el servicio rechaza con un mensaje pensado para quien llama */
    @ExceptionHandler({IllegalArgumentException.class, MissingRequestHeaderException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception exception) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage(), null);
    }

    /* errores del cliente que Spring detecta antes de llegar al servicio (JSON mal formado, ?estado=FOO, etc.) */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HandlerMethodValidationException.class })
    public ResponseEntity<Map<String, Object>> handleSolicitudInvalida(Exception exception) {
        return response(HttpStatus.BAD_REQUEST, "Solicitud inválida", null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMetodoNoSoportado(HttpRequestMethodNotSupportedException exception) {
        return response(HttpStatus.METHOD_NOT_ALLOWED, "Método no soportado", null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleRutaInexistente(NoResourceFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "Recurso no encontrado", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception exception) {
        /* antes no se registraba nada y un 500 era imposible de diagnosticar */
        log.error("Error inesperado", exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor", null);
    }

    private ResponseEntity<Map<String, Object>> response(HttpStatus status, String message, Object details) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        if (details != null) {
            body.put("details", details);
        }
        return ResponseEntity.status(status).body(body);
    }
}
