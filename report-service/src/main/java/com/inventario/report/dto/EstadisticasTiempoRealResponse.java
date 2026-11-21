package com.inventario.report.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Vista del inventario calculada en vivo a partir del stream inventario.movimientos. */
public record EstadisticasTiempoRealResponse(
        long eventosProcesados,
        long duplicadosDescartados,
        long totalEntradas,
        long totalSalidas,
        long unidadesIngresadas,
        long unidadesDespachadas,
        LocalDateTime ultimaActualizacion,
        List<ResumenProductoTiempoReal> productos,
        List<MovimientoReciente> ultimosMovimientos
) {

    public record ResumenProductoTiempoReal(
            Long productoId,
            String productoCodigo,
            String productoNombre,
            long movimientos,
            long unidadesIngresadas,
            long unidadesDespachadas,
            Integer stockActual,
            LocalDateTime ultimoMovimiento
    ) {
    }

    public record MovimientoReciente(
            String eventId,
            Long productoId,
            String productoCodigo,
            String tipoMovimiento,
            Integer cantidad,
            Integer stockResultante,
            String usuario,
            LocalDateTime fecha
    ) {
    }
}
