package com.inventario.report.producer;

import com.inventario.report.event.AlertaStockEvent;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertaStockPublisherTest {

    private static final String TOPIC = "inventario.alertas-stock";

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private final AlertaStockEvent alerta = new AlertaStockEvent("a-1", AlertaStockEvent.NIVEL_BAJO, 9L, "PROD-009",
            "Mouse", 2, 5, "evt-9", LocalDateTime.now());

    @Test
    void publicar_usaElProductoIdComoClave() {
        RecordMetadata metadata = new RecordMetadata(new TopicPartition(TOPIC, 1), 3L, 0, 0L, 0, 0);
        SendResult<String, Object> resultado = new SendResult<>(new ProducerRecord<>(TOPIC, "9", alerta), metadata);
        when(kafkaTemplate.send(TOPIC, "9", alerta)).thenReturn(CompletableFuture.completedFuture(resultado));

        new AlertaStockPublisher(kafkaTemplate, TOPIC).publicar(alerta);

        verify(kafkaTemplate).send(TOPIC, "9", alerta);
    }

    @Test
    void publicar_siElBrokerFalla_lanzaExcepcionParaQueSeReintente() {
        when(kafkaTemplate.send(TOPIC, "9", alerta))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("sin lider")));

        assertThatThrownBy(() -> new AlertaStockPublisher(kafkaTemplate, TOPIC).publicar(alerta))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("producto 9");
    }

    @Test
    @SuppressWarnings("unchecked")
    void publicar_siSeAcabaElTiempo_lanzaExcepcion() throws Exception {
        CompletableFuture<SendResult<String, Object>> future = mock(CompletableFuture.class);
        when(future.get(anyLong(), any(TimeUnit.class))).thenThrow(new TimeoutException("lento"));
        when(kafkaTemplate.send(TOPIC, "9", alerta)).thenReturn(future);

        assertThatThrownBy(() -> new AlertaStockPublisher(kafkaTemplate, TOPIC).publicar(alerta))
                .isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(TimeoutException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publicar_siSeInterrumpeElHilo_restauraLaBanderaDeInterrupcion() throws Exception {
        CompletableFuture<SendResult<String, Object>> future = mock(CompletableFuture.class);
        when(future.get(anyLong(), any(TimeUnit.class))).thenThrow(new InterruptedException("apagado"));
        when(kafkaTemplate.send(TOPIC, "9", alerta)).thenReturn(future);

        assertThatThrownBy(() -> new AlertaStockPublisher(kafkaTemplate, TOPIC).publicar(alerta))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("interrumpida");
        assertThat(Thread.interrupted()).isTrue();
    }
}
