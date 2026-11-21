package com.inventario.report.service;

import com.inventario.report.event.MovimientoEvent;
import com.inventario.report.exception.EventoInvalidoException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Valida el payload antes de procesarlo, para que un evento corrupto no altere los reportes. */
@Component
public class MovimientoEventValidator {

    private static final Set<String> TIPOS_VALIDOS = Set.of("ENTRADA", "SALIDA");

    public void validar(MovimientoEvent evento) {
        if (evento == null) {
            throw new EventoInvalidoException("Evento de movimiento vacio");
        }
        List<String> errores = new ArrayList<>();
        if (evento.eventId() == null || evento.eventId().isBlank()) {
            errores.add("eventId");
        }
        if (evento.productoId() == null || evento.productoId() <= 0) {
            errores.add("productoId");
        }
        if (evento.tipoMovimiento() == null || !TIPOS_VALIDOS.contains(evento.tipoMovimiento())) {
            errores.add("tipoMovimiento");
        }
        if (evento.cantidad() == null || evento.cantidad() <= 0) {
            errores.add("cantidad");
        }
        if (evento.stockResultante() == null || evento.stockResultante() < 0) {
            errores.add("stockResultante");
        }
        if (evento.fecha() == null) {
            errores.add("fecha");
        }
        if (!errores.isEmpty()) {
            throw new EventoInvalidoException("Evento " + evento.eventId() + " invalido, campos con error: " + errores);
        }
    }
}
