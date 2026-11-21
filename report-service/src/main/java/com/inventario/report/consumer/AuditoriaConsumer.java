package com.inventario.report.consumer;

import com.inventario.report.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AuditoriaConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaConsumer.class);

    /**
     * Pista de auditoria de los cambios de producto, recibidos por el exchange topic con la
     * routing key auditoria.producto.<tipo>. Un evento sin tipo o sin producto se rechaza y
     * RabbitMQ lo deriva a cola.auditoria.dlq.
     */
    @RabbitListener(queues = RabbitMQConfig.COLA_AUDITORIA)
    public void auditar(Map<String, Object> evento,
                        @Header(name = AmqpHeaders.RECEIVED_ROUTING_KEY, required = false) String routingKey) {
        Object productoId = evento.get("productoId");
        Object tipo = evento.get("tipo");

        if (!(productoId instanceof Number id) || id.longValue() <= 0
                || !(tipo instanceof String t) || t.isBlank()) {
            log.error("[GRAVE] Evento de auditoria invalido (routingKey={}, productoId={}, tipo={}). Se envia a {}",
                    routingKey, productoId, tipo, RabbitMQConfig.COLA_AUDITORIA_DLQ);
            throw new IllegalArgumentException("Evento de auditoria invalido: se requiere productoId > 0 y tipo");
        }

        log.info("[AUDITORIA] {} | routingKey={} | producto {} ({}) | stock={} | fecha={}",
                tipo, routingKey, productoId, evento.get("codigo"), evento.get("stock"), evento.get("fecha"));
    }
}
