package com.inventario.report.exception;

/** Evento con datos imposibles de procesar: no se reintenta, va directo al DLT. */
public class EventoInvalidoException extends RuntimeException {
    public EventoInvalidoException(String message) {
        super(message);
    }
}
