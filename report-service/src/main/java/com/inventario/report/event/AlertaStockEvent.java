package com.inventario.report.event;

import java.time.LocalDateTime;

/**
 * Estado de stock de un producto, publicado en inventario.alertas-stock con clave productoId.
 * El topico es compactado: Kafka conserva el ultimo estado de cada producto (BAJO, AGOTADO
 * o NORMAL), asi un consumidor nuevo reconstruye las alertas vigentes leyendo desde el inicio.
 */
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
    public static final String NIVEL_AGOTADO = "AGOTADO";
    public static final String NIVEL_BAJO = "BAJO";
    public static final String NIVEL_NORMAL = "NORMAL";
}
