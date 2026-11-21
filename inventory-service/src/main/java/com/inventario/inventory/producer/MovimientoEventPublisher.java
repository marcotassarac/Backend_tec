package com.inventario.inventory.producer;

import com.inventario.inventory.event.MovimientoEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class MovimientoEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(MovimientoEventPublisher.class);

    private final KafkaTemplate<String, MovimientoEvent> kafkaTemplate;
    private final String topic;

    public MovimientoEventPublisher(KafkaTemplate<String, MovimientoEvent> movimientoKafkaTemplate,
                                    @Value("${app.kafka.topics.movimientos}") String topic) {
        this.kafkaTemplate = movimientoKafkaTemplate;
        this.topic = topic;
    }

    /**
     * La clave es el productoId: todos los movimientos de un producto caen en la misma particion
     * y se consumen en orden. Si Kafka no esta disponible solo se registra el error: el movimiento
     * ya quedo guardado y el flujo HTTP/RabbitMQ existente no debe fallar por el streaming.
     */
    public void publicar(MovimientoEvent evento) {
        String clave = String.valueOf(evento.productoId());
        try {
            kafkaTemplate.send(topic, clave, evento).whenComplete((resultado, error) -> {
                if (error != null) {
                    log.error("No se pudo publicar el evento {} (producto {}) en {}: {}",
                            evento.eventId(), clave, topic, error.getMessage());
                } else {
                    log.info("Evento {} publicado en {} particion {} offset {}",
                            evento.eventId(), topic,
                            resultado.getRecordMetadata().partition(),
                            resultado.getRecordMetadata().offset());
                }
            });
        } catch (RuntimeException ex) {
            log.error("Kafka no disponible, evento {} (producto {}) no publicado: {}",
                    evento.eventId(), clave, ex.getMessage());
        }
    }
}
