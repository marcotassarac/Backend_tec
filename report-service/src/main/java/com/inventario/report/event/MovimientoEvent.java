package com.inventario.report.event;

import java.time.LocalDateTime;

/** Copia local del contrato publicado por inventory-service en inventario.movimientos. */
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
}
