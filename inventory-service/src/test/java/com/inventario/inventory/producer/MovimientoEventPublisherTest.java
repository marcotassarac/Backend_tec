package com.inventario.inventory.producer;

import com.inventario.inventory.event.MovimientoEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MovimientoEventPublisherTest {

    private static final String TOPIC = "inventario.movimientos";

    @Mock
    private KafkaTemplate<String, MovimientoEvent> kafkaTemplate;

    private MovimientoEvent evento() {
        return new MovimientoEvent("evt-1", MovimientoEvent.TIPO_MOVIMIENTO_REGISTRADO, 10L, 7L, "PROD-007",
                "Teclado", "SALIDA", 2, 3, 5, "operador", LocalDateTime.now());
    }

    @Test
    void publicar_debeUsarElProductoIdComoClaveYRegistrarLaParticion() {
        MovimientoEvent evento = evento();
        RecordMetadata metadata = new RecordMetadata(new TopicPartition(TOPIC, 2), 41L, 0, 0L, 0, 0);
        SendResult<String, MovimientoEvent> resultado =
                new SendResult<>(new ProducerRecord<>(TOPIC, "7", evento), metadata);
        when(kafkaTemplate.send(TOPIC, "7", evento)).thenReturn(CompletableFuture.completedFuture(resultado));

        new MovimientoEventPublisher(kafkaTemplate, TOPIC).publicar(evento);

        verify(kafkaTemplate).send(TOPIC, "7", evento);
    }

    @Test
    void publicar_siElBrokerRechazaElEnvio_soloRegistraElError() {
        MovimientoEvent evento = evento();
        when(kafkaTemplate.send(eq(TOPIC), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker caido")));

        assertThatCode(() -> new MovimientoEventPublisher(kafkaTemplate, TOPIC).publicar(evento))
                .doesNotThrowAnyException();
    }

    @Test
    void publicar_siKafkaNoEstaDisponible_noPropagaLaExcepcion() {
        MovimientoEvent evento = evento();
        when(kafkaTemplate.send(eq(TOPIC), anyString(), any()))
                .thenThrow(new org.springframework.kafka.KafkaException("timeout obteniendo metadata"));

        assertThatCode(() -> new MovimientoEventPublisher(kafkaTemplate, TOPIC).publicar(evento))
                .doesNotThrowAnyException();
    }
}
