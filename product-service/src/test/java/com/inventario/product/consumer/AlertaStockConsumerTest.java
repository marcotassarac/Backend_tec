package com.inventario.product.consumer;

import com.inventario.product.event.AlertaStockEvent;
import com.inventario.product.service.AlertaStockEstadoService;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.ConsumerSeekAware.ConsumerSeekCallback;

import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AlertaStockConsumerTest {

    private final AlertaStockEstadoService estado = mock(AlertaStockEstadoService.class);
    private final AlertaStockConsumer consumer = new AlertaStockConsumer(estado);

    @Test
    void consumir_aplicaLaAlertaAlEstado() {
        AlertaStockEvent alerta = new AlertaStockEvent("a", "BAJO", 1L, "PROD-1", "Producto", 2, 5, "evt",
                LocalDateTime.now());

        consumer.consumir(alerta, 0, 3L);

        verify(estado).aplicar(alerta);
    }

    @Test
    void alRecibirParticiones_vuelveAlInicioParaReconstruirElEstado() {
        ConsumerSeekCallback callback = mock(ConsumerSeekCallback.class);
        Map<TopicPartition, Long> asignadas = Map.of(new TopicPartition("inventario.alertas-stock", 1), 10L);

        consumer.onPartitionsAssigned(asignadas, callback);

        verify(callback).seekToBeginning(asignadas.keySet());
    }
}
