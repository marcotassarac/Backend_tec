package com.inventario.inventory.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventario.inventory.event.MovimientoEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaProducerConfigTest {

    @Test
    void producerFactory_debeTomarLosBrokersYAcksDeLasPropiedades() {
        KafkaProperties properties = new KafkaProperties();
        properties.setBootstrapServers(List.of("localhost:9092", "localhost:9093", "localhost:9094"));
        properties.getProducer().setAcks("all");

        KafkaProducerConfig config = new KafkaProducerConfig();
        ProducerFactory<String, MovimientoEvent> factory =
                config.movimientoProducerFactory(properties, new ObjectMapper());
        KafkaTemplate<String, MovimientoEvent> template = config.movimientoKafkaTemplate(factory);

        assertThat(factory).isInstanceOf(DefaultKafkaProducerFactory.class);
        assertThat(factory.getConfigurationProperties())
                .containsEntry(ProducerConfig.ACKS_CONFIG, "all")
                .containsEntry(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                        List.of("localhost:9092", "localhost:9093", "localhost:9094"));
        assertThat(template.getProducerFactory()).isSameAs(factory);
    }
}
