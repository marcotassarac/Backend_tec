package com.inventario.product.event;

import java.time.LocalDateTime;

/** Copia local del contrato que report-service publica en inventario.alertas-stock. */
public record AlertaStockEvent(
        String eventId,
        String nivel,
        Long productoId,
        String productoCodigo,
        String productoNombre,
        Integer stockActual,
        Integer stockMinimo,
        String eventoOrigenId,
        LocalDateTime fecha
) {
    public static final String NIVEL_NORMAL = "NORMAL";
}
