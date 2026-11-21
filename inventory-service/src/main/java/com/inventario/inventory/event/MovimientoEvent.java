package com.inventario.inventory.event;

import java.time.LocalDateTime;

/**
 * Evento publicado en inventario.movimientos por cada entrada o salida registrada.
 * Lleva el estado completo del movimiento (incluido stockMinimo) para que los consumidores
 * puedan calcular estadisticas o detectar stock bajo sin volver a llamar a product-service.
 * eventId permite a los consumidores descartar duplicados (entrega at-least-once).
 */
public record MovimientoEvent(
        String eventId,
        String tipoEvento,
        Long movimientoId,
        Long productoId,
        String productoCodigo,
        String productoNombre,
        String tipoMovimiento,
        Integer cantidad,
        Integer stockResultante,
        Integer stockMinimo,
        String usuario,
        LocalDateTime fecha
) {
    public static final String TIPO_MOVIMIENTO_REGISTRADO = "MOVIMIENTO_REGISTRADO";
}
