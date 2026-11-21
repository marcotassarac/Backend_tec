package com.inventario.product.consumer;

import com.inventario.product.config.KafkaConsumerConfig;
import com.inventario.product.event.AlertaStockEvent;
import com.inventario.product.service.AlertaStockEstadoService;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.AbstractConsumerSeekAware;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Al recibir sus particiones vuelve al inicio del topico: como es compactado, leerlo completo
 * reconstruye el estado vigente de todas las alertas aunque este servicio se haya reiniciado.
 */
@Component
public class AlertaStockConsumer extends AbstractConsumerSeekAware {

    private static final Logger log = LoggerFactory.getLogger(AlertaStockConsumer.class);

    private final AlertaStockEstadoService estadoService;

    public AlertaStockConsumer(AlertaStockEstadoService estadoService) {
        this.estadoService = estadoService;
    }

    @Override
    public void onPartitionsAssigned(Map<TopicPartition, Long> assignments, ConsumerSeekCallback callback) {
        super.onPartitionsAssigned(assignments, callback);
        callback.seekToBeginning(assignments.keySet());
        log.info("[{}] particiones asignadas {}: reconstruyendo alertas desde el inicio",
                KafkaConsumerConfig.GRUPO_ALERTAS, assignments.keySet());
    }

    @KafkaListener(id = KafkaConsumerConfig.GRUPO_ALERTAS,
            groupId = KafkaConsumerConfig.GRUPO_ALERTAS,
            clientIdPrefix = KafkaConsumerConfig.GRUPO_ALERTAS,
            topics = "${app.kafka.topics.alertas-stock}",
            containerFactory = "alertasListenerFactory")
    public void consumir(@Payload AlertaStockEvent alerta,
                         @Header(KafkaHeaders.RECEIVED_PARTITION) int particion,
                         @Header(KafkaHeaders.OFFSET) long offset) {
        estadoService.aplicar(alerta);
        log.info("[{}] particion {} offset {}: producto {} ({}) ahora en estado {} (stock {})",
                KafkaConsumerConfig.GRUPO_ALERTAS, particion, offset, alerta.productoId(), alerta.productoCodigo(),
                alerta.nivel(), alerta.stockActual());
    }
}
