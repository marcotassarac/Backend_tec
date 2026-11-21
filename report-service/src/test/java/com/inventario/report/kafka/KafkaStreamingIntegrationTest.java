package com.inventario.report.kafka;

import com.inventario.report.service.EstadisticasTiempoRealService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Flujo real contra un broker Kafka embebido: inventario.movimientos -> 2 consumer groups ->
 * estadisticas + inventario.alertas-stock, y los eventos invalidos o corruptos terminan en el DLT.
 */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "app.kafka.retry.intervalo-ms=50"
})
@EmbeddedKafka(partitions = 3, topics = {
        "inventario.movimientos", "inventario.movimientos.DLT", "inventario.alertas-stock"})
@DirtiesContext
class KafkaStreamingIntegrationTest {

    private static final String MOVIMIENTOS = "inventario.movimientos";

    @Autowired
    private EmbeddedKafkaBroker broker;
    @Autowired
    private EstadisticasTiempoRealService estadisticas;
    @Autowired
    private KafkaListenerEndpointRegistry registry;

    private KafkaTemplate<String, String> productor;
    private final List<Consumer<?, ?>> consumidores = new ArrayList<>();

    @BeforeEach
    void setUp() {
        for (MessageListenerContainer container : registry.getListenerContainers()) {
            ContainerTestUtils.waitForAssignment(container, 3);
        }
        Map<String, Object> props = KafkaTestUtils.producerProps(broker);
        productor = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props,
                new StringSerializer(), new StringSerializer()));
    }

    @AfterEach
    void tearDown() {
        consumidores.forEach(Consumer::close);
    }

    private <V> Consumer<String, V> consumidor(String topic, org.apache.kafka.common.serialization.Deserializer<V> valores) {
        Map<String, Object> props = KafkaTestUtils.consumerProps("test-" + UUID.randomUUID(), "false", broker);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        Consumer<String, V> consumer = new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), valores)
                .createConsumer();
        broker.consumeFromAnEmbeddedTopic(consumer, topic);
        consumidores.add(consumer);
        return consumer;
    }

    private ConsumerRecord<String, byte[]> esperarRegistro(Consumer<String, byte[]> consumer, String clave) {
        List<ConsumerRecord<String, byte[]>> encontrados = new ArrayList<>();
        await().atMost(Duration.ofSeconds(20)).until(() -> {
            KafkaTestUtils.getRecords(consumer, Duration.ofMillis(500))
                    .forEach(r -> { if (clave.equals(r.key())) encontrados.add(r); });
            return !encontrados.isEmpty();
        });
        return encontrados.get(0);
    }

    private String movimiento(String eventId, long productoId, String tipo, int cantidad, int stock) {
        return """
                {"eventId":"%s","tipoEvento":"MOVIMIENTO_REGISTRADO","movimientoId":1,"productoId":%d,
                 "productoCodigo":"PROD-%d","productoNombre":"Producto","tipoMovimiento":"%s","cantidad":%d,
                 "stockResultante":%d,"stockMinimo":5,"usuario":"operador","fecha":"2026-11-13T10:00:00"}
                """.formatted(eventId, productoId, productoId, tipo, cantidad, stock);
    }

    @Test
    void movimientoValido_actualizaEstadisticasYPublicaAlertaDeStockBajo() {
        Consumer<String, byte[]> alertas = consumidor("inventario.alertas-stock", new ByteArrayDeserializer());
        long antes = estadisticas.obtener().eventosProcesados();

        productor.send(MOVIMIENTOS, "77", movimiento("ok-77", 77, "SALIDA", 3, 2));

        await().atMost(Duration.ofSeconds(20))
                .until(() -> estadisticas.obtener().eventosProcesados() > antes);
        ConsumerRecord<String, byte[]> alerta = esperarRegistro(alertas, "77");
        String json = new String(alerta.value(), StandardCharsets.UTF_8);
        assertThat(json).contains("\"nivel\":\"BAJO\"").contains("\"eventoOrigenId\":\"ok-77\"");
    }

    @Test
    void movimientoInvalido_noSeReintentaYTerminaEnElDlt() {
        Consumer<String, byte[]> dlt = consumidor(MOVIMIENTOS + ".DLT", new ByteArrayDeserializer());

        productor.send(MOVIMIENTOS, "88", movimiento("bad-88", 88, "SALIDA", 0, 2));

        ConsumerRecord<String, byte[]> registro = esperarRegistro(dlt, "88");
        String motivo = new String(registro.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_MESSAGE).value(),
                StandardCharsets.UTF_8);
        assertThat(motivo).contains("cantidad");
        assertThat(new String(registro.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC).value(),
                StandardCharsets.UTF_8)).isEqualTo(MOVIMIENTOS);
    }

    @Test
    void mensajeConJsonCorrupto_seReenviaAlDltConSusBytesOriginales() {
        Consumer<String, byte[]> dlt = consumidor(MOVIMIENTOS + ".DLT", new ByteArrayDeserializer());

        productor.send(MOVIMIENTOS, "99", "{esto-no-es-json");

        ConsumerRecord<String, byte[]> registro = esperarRegistro(dlt, "99");
        assertThat(new String(registro.value(), StandardCharsets.UTF_8)).isEqualTo("{esto-no-es-json");
    }
}
