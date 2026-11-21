package com.inventario.product.kafka;

import com.inventario.product.dto.kafka.KafkaAdminDtos.ConsumerGroupDetalle;
import com.inventario.product.dto.kafka.KafkaAdminDtos.ConsumerGroupResumen;
import com.inventario.product.dto.kafka.KafkaAdminDtos.CrearTopicoRequest;
import com.inventario.product.dto.kafka.KafkaAdminDtos.ParticionInfo;
import com.inventario.product.dto.kafka.KafkaAdminDtos.TopicoDetalle;
import com.inventario.product.dto.kafka.KafkaAdminDtos.TopicoResumen;
import com.inventario.product.event.AlertaStockEvent;
import com.inventario.product.exception.KafkaAdminException;
import com.inventario.product.service.AlertaStockEstadoService;
import com.inventario.product.service.KafkaAdminService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Plataforma Kafka real con 3 brokers embebidos: los topicos que declara la app, el consumidor
 * de alertas (con su DLT) y cada operacion del microservicio administrador.
 */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.admin.auto-create=true",
        "app.kafka.listeners.auto-startup=true",
        "app.kafka.retry.intervalo-ms=50",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
@EmbeddedKafka(count = 3)
@DirtiesContext
class KafkaPlataformaIntegrationTest {

    private static final String ALERTAS = "inventario.alertas-stock";

    @Autowired
    private EmbeddedKafkaBroker broker;
    @Autowired
    private KafkaAdminService adminService;
    @Autowired
    private AlertaStockEstadoService estadoService;
    @Autowired
    private KafkaListenerEndpointRegistry registry;

    private KafkaTemplate<String, String> productor;
    private final List<Consumer<?, ?>> consumidores = new ArrayList<>();

    @BeforeEach
    void setUp() {
        for (MessageListenerContainer container : registry.getListenerContainers()) {
            ContainerTestUtils.waitForAssignment(container, 3);
        }
        productor = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(KafkaTestUtils.producerProps(broker),
                new StringSerializer(), new StringSerializer()));
    }

    @AfterEach
    void tearDown() {
        consumidores.forEach(Consumer::close);
    }

    private Consumer<String, byte[]> consumidor(String groupId, String... topicos) {
        Map<String, Object> props = KafkaTestUtils.consumerProps(groupId, "false", broker);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        Consumer<String, byte[]> consumer = new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(),
                new ByteArrayDeserializer()).createConsumer();
        consumer.subscribe(List.of(topicos));
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

    private String alerta(long productoId, String nivel, int stock) {
        return """
                {"eventId":"%s","nivel":"%s","productoId":%d,"productoCodigo":"PROD-%d","productoNombre":"Producto",
                 "stockActual":%d,"stockMinimo":5,"eventoOrigenId":"evt","fecha":"2026-11-13T10:00:00"}
                """.formatted(UUID.randomUUID(), nivel, productoId, productoId, stock);
    }

    // ---------- Topicos declarados por la app ----------

    @Test
    void topicosDeclarados_quedanCon3Particiones3ReplicasYConfiguracionPorFlujo() {
        assertThat(adminService.listarTopicos())
                .contains(new TopicoResumen("inventario.movimientos", 3, 3),
                        new TopicoResumen(ALERTAS, 3, 3),
                        new TopicoResumen("inventario.movimientos.DLT", 3, 3),
                        new TopicoResumen(ALERTAS + ".DLT", 3, 3));

        TopicoDetalle movimientos = adminService.describirTopico("inventario.movimientos");
        assertThat(movimientos.particiones()).hasSize(3)
                .allSatisfy(p -> {
                    assertThat(p.replicas()).hasSize(3);
                    assertThat(p.enSincronia()).hasSize(3);
                    assertThat(p.lider()).isNotNull();
                });
        assertThat(movimientos.configuraciones())
                .containsEntry("cleanup.policy", "delete")
                .containsEntry("retention.ms", "604800000")
                .containsEntry("min.insync.replicas", "2");

        assertThat(adminService.describirTopico(ALERTAS).configuraciones())
                .containsEntry("cleanup.policy", "compact")
                .containsEntry("min.compaction.lag.ms", "60000");
    }

    // ---------- Consumidor de alertas ----------

    @Test
    void alertas_abrenYCierranElEstadoDelProducto() {
        productor.send(ALERTAS, "5", alerta(5, "BAJO", 3));
        await().atMost(Duration.ofSeconds(20)).until(() ->
                estadoService.listarVigentes().stream().anyMatch(a -> a.productoId() == 5L));
        assertThat(estadoService.listarVigentes()).filteredOn(a -> a.productoId() == 5L)
                .extracting(AlertaStockEvent::nivel).containsExactly("BAJO");

        productor.send(ALERTAS, "5", alerta(5, "NORMAL", 30));
        await().atMost(Duration.ofSeconds(20)).until(() ->
                estadoService.listarVigentes().stream().noneMatch(a -> a.productoId() == 5L));
    }

    @Test
    void alertaInvalida_vaDirectoAlDlt() {
        Consumer<String, byte[]> dlt = consumidor("dlt-" + UUID.randomUUID(), ALERTAS + ".DLT");

        productor.send(ALERTAS, "6", alerta(0, "BAJO", 1));

        ConsumerRecord<String, byte[]> registro = esperarRegistro(dlt, "6");
        assertThat(new String(registro.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_MESSAGE).value(),
                StandardCharsets.UTF_8)).contains("productoId");
    }

    @Test
    void alertaConJsonCorrupto_conservaSusBytesEnElDlt() {
        Consumer<String, byte[]> dlt = consumidor("dlt-" + UUID.randomUUID(), ALERTAS + ".DLT");

        productor.send(ALERTAS, "7", "no-es-json");

        assertThat(new String(esperarRegistro(dlt, "7").value(), StandardCharsets.UTF_8)).isEqualTo("no-es-json");
    }

    // ---------- Administracion de topicos ----------

    @Test
    void administrador_creaActualizaYAmpliaTopicos() {
        String nombre = "prueba.admin." + UUID.randomUUID().toString().substring(0, 8);

        assertThat(adminService.crearTopico(new CrearTopicoRequest(nombre, 2, (short) 3,
                Map.of("retention.ms", "60000", "compression.type", "lz4"))))
                .isEqualTo(new TopicoResumen(nombre, 2, 3));
        assertThat(adminService.describirTopico(nombre).configuraciones())
                .containsEntry("retention.ms", "60000")
                .containsEntry("compression.type", "lz4");

        assertThat(adminService.actualizarConfiguracion(nombre, Map.of("retention.ms", "120000")).configuraciones())
                .containsEntry("retention.ms", "120000");

        assertThat(adminService.aumentarParticiones(nombre, 3).particiones())
                .extracting(ParticionInfo::particion).containsExactly(0, 1, 2);

        assertThatThrownBy(() -> adminService.crearTopico(new CrearTopicoRequest(nombre, 1, (short) 1, null)))
                .isInstanceOfSatisfying(KafkaAdminException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> adminService.aumentarParticiones(nombre, 1))
                .isInstanceOfSatisfying(KafkaAdminException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));

        // La eliminacion se prueba con mocks: en Windows, borrar un topico deja offline el log dir del
        // broker embebido (bug conocido de Kafka en Windows; el cluster Docker corre en Linux).
        assertThatThrownBy(() -> adminService.describirTopico(nombre + ".no-existe"))
                .isInstanceOfSatisfying(KafkaAdminException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void administrador_crearTopicoSinConfiguracionesExtra() {
        String nombre = "prueba.simple." + UUID.randomUUID().toString().substring(0, 8);

        adminService.crearTopico(new CrearTopicoRequest(nombre, 1, (short) 2, null));

        assertThat(adminService.describirTopico(nombre).particiones()).singleElement()
                .satisfies(p -> assertThat(p.replicas()).hasSize(2));
    }

    // ---------- Consumer groups ----------

    @Test
    void administrador_muestraLagYMueveOffsetsDeUnGrupoInactivo() {
        String topico = "prueba.lag." + UUID.randomUUID().toString().substring(0, 8);
        String grupo = "grupo-" + UUID.randomUUID().toString().substring(0, 8);
        adminService.crearTopico(new CrearTopicoRequest(topico, 1, (short) 3, null));
        for (int i = 0; i < 5; i++) {
            productor.send(topico, "k" + i, "mensaje " + i).join();
        }
        Map<String, Object> props = KafkaTestUtils.consumerProps(grupo, "false", broker);
        try (Consumer<String, byte[]> consumer = new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(),
                new ByteArrayDeserializer()).createConsumer()) {
            consumer.assign(List.of(new TopicPartition(topico, 0)));
            consumer.commitSync(Map.of(new TopicPartition(topico, 0), new OffsetAndMetadata(2)));
        }

        ConsumerGroupDetalle detalle = adminService.describirConsumerGroup(grupo);
        assertThat(detalle.lagTotal()).isEqualTo(3);
        assertThat(detalle.offsets()).singleElement().satisfies(o -> {
            assertThat(o.offsetConfirmado()).isEqualTo(2);
            assertThat(o.offsetFinal()).isEqualTo(5);
        });
        assertThat(detalle.miembros()).isEmpty();
        assertThat(adminService.listarConsumerGroups()).extracting(ConsumerGroupResumen::groupId)
                .contains(grupo, "product-alertas-stock");

        assertThat(adminService.reiniciarOffsets(grupo, topico, "latest").lagTotal()).isZero();
        assertThat(adminService.reiniciarOffsets(grupo, topico, "earliest").lagTotal()).isEqualTo(5);

        adminService.eliminarConsumerGroup(grupo);
        assertThat(adminService.listarConsumerGroups()).extracting(ConsumerGroupResumen::groupId)
                .doesNotContain(grupo);
    }

    @Test
    void administrador_grupoActivo_muestraSusMiembrosYNoPermiteMoverOffsets() {
        ConsumerGroupDetalle activo = adminService.describirConsumerGroup("product-alertas-stock");
        assertThat(activo.miembros()).hasSize(3);
        assertThat(activo.miembros()).flatExtracting(m -> m.particionesAsignadas())
                .containsExactlyInAnyOrder(ALERTAS + "-0", ALERTAS + "-1", ALERTAS + "-2");

        assertThatThrownBy(() -> adminService.reiniciarOffsets("product-alertas-stock", ALERTAS, "earliest"))
                .isInstanceOf(KafkaAdminException.class);
    }
}
