package com.inventario.inventory.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventario.inventory.event.MovimientoEvent;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

/**
 * Productor de inventario.movimientos. Bootstrap servers, acks=all, idempotencia y timeouts
 * vienen de application.properties (spring.kafka.producer.*); aqui se fijan los serializers:
 * clave String (productoId) y valor JSON sin headers de tipo, para que cada consumidor
 * lo deserialice a su propio DTO sin depender de los paquetes de este servicio.
 */
@Configuration
public class KafkaProducerConfig {

    @Bean
    public ProducerFactory<String, MovimientoEvent> movimientoProducerFactory(KafkaProperties kafkaProperties,
                                                                             ObjectMapper objectMapper) {
        JsonSerializer<MovimientoEvent> valueSerializer = new JsonSerializer<>(objectMapper);
        valueSerializer.setAddTypeInfo(false);
        return new DefaultKafkaProducerFactory<>(kafkaProperties.buildProducerProperties(null),
                new StringSerializer(), valueSerializer);
    }

    @Bean
    public KafkaTemplate<String, MovimientoEvent> movimientoKafkaTemplate(
            ProducerFactory<String, MovimientoEvent> movimientoProducerFactory) {
        return new KafkaTemplate<>(movimientoProducerFactory);
    }
}
