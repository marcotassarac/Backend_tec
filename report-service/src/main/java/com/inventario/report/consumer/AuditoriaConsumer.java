package com.inventario.report.consumer;

import com.inventario.report.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AuditoriaConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaConsumer.class);

    /**
     * Deja un registro de auditoria por cada evento de cola.auditoria. Si el evento no trae
     * los datos minimos para auditarlo (tipo, productoId > 0, codigo y fecha) lanza
     * IllegalArgumentException: el listener lo rechaza sin reencolar
     * (default-requeue-rejected=false) y RabbitMQ lo deriva a cola.auditoria.dlq.
     */
    @RabbitListener(queues = RabbitMQConfig.COLA_AUDITORIA)
    public void registrarAuditoria(Map<String, Object> evento) {
        log.info("Evento recibido en {}: {}", RabbitMQConfig.COLA_AUDITORIA, evento);

        Object productoId = evento.get("productoId");
        Object tipo = evento.get("tipo");
        Object codigo = evento.get("codigo");
        Object fecha = evento.get("fecha");

        if (!(productoId instanceof Number id) || id.longValue() <= 0
                || esTextoVacio(tipo) || esTextoVacio(codigo) || esTextoVacio(fecha)) {
            log.error("[GRAVE] Evento de auditoria invalido (productoId={}, tipo={}, codigo={}, fecha={}). Se envia a {}",
                    productoId, tipo, codigo, fecha, RabbitMQConfig.COLA_AUDITORIA_DLQ);
            throw new IllegalArgumentException(
                    "Evento de auditoria invalido: se requiere productoId > 0, tipo, codigo y fecha");
        }

        log.info("Auditoria registrada: {} | producto {} ({}) | fecha={}", tipo, productoId, codigo, fecha);
    }

    private boolean esTextoVacio(Object valor) {
        return !(valor instanceof String texto) || texto.isBlank();
    }
}
