package com.inventario.report.service;

import com.inventario.report.event.AlertaStockEvent;
import com.inventario.report.event.MovimientoEvent;
import com.inventario.report.producer.AlertaStockPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Regla de negocio que transforma el stream de movimientos en un stream de alertas:
 * - stock en 0                     -> AGOTADO
 * - stock en o bajo el minimo      -> BAJO
 * - entrada que deja stock sobre el minimo -> NORMAL (cierra la alerta vigente)
 * Una salida que deja stock sobre el minimo no cambia el estado y no genera evento.
 */
@Service
public class AlertaStockService {

    private final AlertaStockPublisher publisher;

    public AlertaStockService(AlertaStockPublisher publisher) {
        this.publisher = publisher;
    }

    public Optional<AlertaStockEvent> evaluar(MovimientoEvent evento) {
        String nivel = calcularNivel(evento);
        if (nivel == null) {
            return Optional.empty();
        }
        AlertaStockEvent alerta = new AlertaStockEvent(
                UUID.randomUUID().toString(),
                nivel,
                evento.productoId(),
                evento.productoCodigo(),
                evento.productoNombre(),
                evento.stockResultante(),
                evento.stockMinimo(),
                evento.eventId(),
                LocalDateTime.now());
        publisher.publicar(alerta);
        return Optional.of(alerta);
    }

    private String calcularNivel(MovimientoEvent evento) {
        int stock = evento.stockResultante();
        int minimo = evento.stockMinimo() != null ? evento.stockMinimo() : 0;
        if (stock == 0) {
            return AlertaStockEvent.NIVEL_AGOTADO;
        }
        if (stock <= minimo) {
            return AlertaStockEvent.NIVEL_BAJO;
        }
        if ("ENTRADA".equals(evento.tipoMovimiento())) {
            return AlertaStockEvent.NIVEL_NORMAL;
        }
        return null;
    }
}
