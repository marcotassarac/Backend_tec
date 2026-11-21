package com.inventario.product.service;

import com.inventario.product.event.AlertaStockEvent;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Alertas de stock vigentes, materializadas desde el topico compactado inventario.alertas-stock. */
@Service
public class AlertaStockEstadoService {

    private final Map<Long, AlertaStockEvent> vigentes = new ConcurrentHashMap<>();

    /** Un estado NORMAL cierra la alerta del producto; BAJO o AGOTADO la abre o la actualiza. */
    public void aplicar(AlertaStockEvent alerta) {
        if (alerta.productoId() == null || alerta.productoId() <= 0 || alerta.nivel() == null
                || alerta.stockActual() == null) {
            throw new IllegalArgumentException("Alerta de stock invalida: se requiere productoId > 0, nivel y stockActual");
        }
        if (AlertaStockEvent.NIVEL_NORMAL.equals(alerta.nivel())) {
            vigentes.remove(alerta.productoId());
        } else {
            vigentes.put(alerta.productoId(), alerta);
        }
    }

    public List<AlertaStockEvent> listarVigentes() {
        return vigentes.values().stream()
                .sorted(Comparator.comparing(AlertaStockEvent::stockActual).thenComparing(AlertaStockEvent::productoId))
                .toList();
    }
}
