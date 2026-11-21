package com.inventario.report.producer;

import com.inventario.report.event.AlertaStockEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class AlertaStockPublisher {

    private static final Logger log = LoggerFactory.getLogger(AlertaStockPublisher.class);
    private static final long TIMEOUT_SEGUNDOS = 10;

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic;

    public AlertaStockPublisher(KafkaTemplate<String, Object> kafkaTemplate,
                                @Value("${app.kafka.topics.alertas-stock}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    /**
     * Envio sincrono a proposito: se ejecuta dentro del consumidor de movimientos, y si la alerta
     * no se puede publicar la excepcion hace que el error handler reintente el evento de origen
     * (y finalmente lo mande al DLT) en vez de perder la alerta en silencio.
     */
    public void publicar(AlertaStockEvent alerta) {
        try {
            SendResult<String, Object> resultado = kafkaTemplate
                    .send(topic, String.valueOf(alerta.productoId()), alerta)
                    .get(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS);
            log.info("Alerta {} del producto {} publicada en {} particion {} offset {}",
                    alerta.nivel(), alerta.productoId(), topic,
                    resultado.getRecordMetadata().partition(), resultado.getRecordMetadata().offset());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Publicacion de alerta interrumpida", ex);
        } catch (ExecutionException | TimeoutException ex) {
            throw new IllegalStateException("No se pudo publicar la alerta del producto " + alerta.productoId(), ex);
        }
    }
}
