package com.inventario.report.consumer;

import com.inventario.report.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ReportConsumer {

    private static final Logger log = LoggerFactory.getLogger(ReportConsumer.class);

    /**
     * Registra en los logs la actualizacion del Kardex por cada evento de cola.notificaciones.
     * Si el evento trae datos invalidos lanza IllegalArgumentException: el listener lo rechaza
     * sin reencolar (default-requeue-rejected=false) y RabbitMQ lo deriva a cola.notificaciones.dlq.
     */
    @RabbitListener(queues = RabbitMQConfig.COLA_NOTIFICACIONES)
    public void registrarKardex(Map<String, Object> evento) {
        log.info("Evento recibido en {}: {}", RabbitMQConfig.COLA_NOTIFICACIONES, evento);

        Object productoId = evento.get("productoId");
        Object tipo = evento.get("tipo");

        if (!(productoId instanceof Number id) || id.longValue() <= 0
                || !(tipo instanceof String t) || t.isBlank()) {
            log.error("[GRAVE] Evento de Kardex invalido (productoId={}, tipo={}). Se envia a {}",
                    productoId, tipo, RabbitMQConfig.COLA_NOTIFICACIONES_DLQ);
            throw new IllegalArgumentException("Evento de Kardex invalido: se requiere productoId > 0 y tipo");
        }

        log.info("Kardex actualizado: {} | producto {} ({} - {}) | stock={} | fecha={}",
                tipo, productoId, evento.get("codigo"), evento.get("nombre"),
                evento.get("stock"), evento.get("fecha"));
    }
}
