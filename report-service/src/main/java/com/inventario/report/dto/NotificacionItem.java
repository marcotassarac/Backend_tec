package com.inventario.report.dto;

import java.time.LocalDateTime;

/** Movimiento recibido por la cola de mensajeria, tal como lo muestra la pantalla de Notificaciones. */
public record NotificacionItem(
        String usuario,
        String rolUsuario,
        String tipoMovimiento,
        String productoCodigo,
        String productoNombre,
        Integer cantidad,
        Integer stockResultante,
        String fechaMovimiento,
        LocalDateTime recibidoEn
) {
}
