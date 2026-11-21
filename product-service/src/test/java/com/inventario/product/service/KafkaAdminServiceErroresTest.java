package com.inventario.product.service;

import com.inventario.product.dto.kafka.KafkaAdminDtos.ConsumerGroupDetalle;
import com.inventario.product.dto.kafka.KafkaAdminDtos.TopicoDetalle;
import com.inventario.product.exception.KafkaAdminException;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.admin.DeleteTopicsResult;
import org.apache.kafka.clients.admin.DescribeConfigsResult;
import org.apache.kafka.clients.admin.DescribeConsumerGroupsResult;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.admin.ListConsumerGroupsResult;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.ConsumerGroupState;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.TopicPartitionInfo;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.internals.KafkaFutureImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Casos que un broker real no produce a voluntad: timeouts, interrupciones y respuestas incompletas. */
@ExtendWith(MockitoExtension.class)
class KafkaAdminServiceErroresTest {

    @Mock
    private Admin admin;

    private KafkaAdminService service() {
        return new KafkaAdminService(admin);
    }

    @SuppressWarnings("unchecked")
    private void listarTopicosDevuelve(KafkaFuture<Set<String>> future) {
        ListTopicsResult resultado = mock(ListTopicsResult.class);
        when(resultado.names()).thenReturn(future);
        when(admin.listTopics()).thenReturn(resultado);
    }

    @Test
    @SuppressWarnings("unchecked")
    void siKafkaNoRespondeATiempo_respondeServiceUnavailable() throws Exception {
        KafkaFuture<Set<String>> future = mock(KafkaFuture.class);
        when(future.get(anyLong(), any(TimeUnit.class))).thenThrow(new TimeoutException());
        listarTopicosDevuelve(future);

        assertThatThrownBy(() -> service().listarTopicos())
                .isInstanceOfSatisfying(KafkaAdminException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(ex.getMessage()).contains("no respondio a tiempo");
                });
    }

    @Test
    @SuppressWarnings("unchecked")
    void siElHiloSeInterrumpe_respondeServiceUnavailableYRestauraLaBandera() throws Exception {
        KafkaFuture<Set<String>> future = mock(KafkaFuture.class);
        when(future.get(anyLong(), any(TimeUnit.class))).thenThrow(new InterruptedException());
        listarTopicosDevuelve(future);

        assertThatThrownBy(() -> service().listarTopicos())
                .isInstanceOfSatisfying(KafkaAdminException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    void errorDeKafkaNoClasificado_respondeBadGateway() {
        KafkaFutureImpl<Set<String>> future = new KafkaFutureImpl<>();
        future.completeExceptionally(new IllegalStateException("algo raro"));
        listarTopicosDevuelve(future);

        assertThatThrownBy(() -> service().listarTopicos())
                .isInstanceOfSatisfying(KafkaAdminException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(ex.getMessage()).contains("algo raro");
                });
    }

    @Test
    void describirTopico_conParticionSinLider_informaLiderNulo() {
        Node nodo = new Node(1, "kafka1", 29092);
        TopicDescription descripcion = new TopicDescription("t", false,
                List.of(new TopicPartitionInfo(0, null, List.of(nodo), List.of())));
        DescribeTopicsResult topicos = mock(DescribeTopicsResult.class);
        when(topicos.allTopicNames()).thenReturn(KafkaFuture.completedFuture(Map.of("t", descripcion)));
        when(admin.describeTopics(anyCollection())).thenReturn(topicos);

        ConfigResource recurso = new ConfigResource(ConfigResource.Type.TOPIC, "t");
        ConfigEntry sinValor = mock(ConfigEntry.class);
        when(sinValor.value()).thenReturn(null);
        when(sinValor.name()).thenReturn("retention.ms");
        ConfigEntry porDefectoNoClave = new ConfigEntry("compression.type", "producer");
        Config config = new Config(List.of(new ConfigEntry("cleanup.policy", "delete"), sinValor, porDefectoNoClave));
        DescribeConfigsResult configs = mock(DescribeConfigsResult.class);
        when(configs.all()).thenReturn(KafkaFuture.completedFuture(Map.of(recurso, config)));
        when(admin.describeConfigs(anyCollection())).thenReturn(configs);

        TopicoDetalle detalle = service().describirTopico("t");

        assertThat(detalle.particiones()).singleElement().satisfies(p -> {
            assertThat(p.lider()).isNull();
            assertThat(p.replicas()).containsExactly(1);
            assertThat(p.enSincronia()).isEmpty();
        });
        assertThat(detalle.configuraciones()).containsOnlyKeys("cleanup.policy");
    }

    @Test
    void eliminarTopico_delegaEnLaAdminClient() {
        DeleteTopicsResult resultado = mock(DeleteTopicsResult.class);
        when(resultado.all()).thenReturn(KafkaFuture.completedFuture(null));
        when(admin.deleteTopics(List.of("t"))).thenReturn(resultado);

        service().eliminarTopico("t");

        verify(admin).deleteTopics(List.of("t"));
    }

    @Test
    void listarConsumerGroups_sinEstadoConocido_loMarcaComoDesconocido() {
        ListConsumerGroupsResult resultado = mock(ListConsumerGroupsResult.class);
        when(resultado.all()).thenReturn(KafkaFuture.completedFuture(List.of(new ConsumerGroupListing("g", true))));
        when(admin.listConsumerGroups()).thenReturn(resultado);

        assertThat(service().listarConsumerGroups()).singleElement()
                .satisfies(g -> assertThat(g.estado()).isEqualTo("DESCONOCIDO"));
    }

    @Test
    void describirConsumerGroup_ignoraParticionesSinOffsetConfirmado() {
        DescribeConsumerGroupsResult grupos = mock(DescribeConsumerGroupsResult.class);
        when(grupos.all()).thenReturn(KafkaFuture.completedFuture(Map.of("g", new ConsumerGroupDescription("g", true,
                List.of(), "", ConsumerGroupState.EMPTY, new Node(1, "kafka1", 29092)))));
        when(admin.describeConsumerGroups(anyCollection())).thenReturn(grupos);

        Map<TopicPartition, OffsetAndMetadata> confirmados = new HashMap<>();
        confirmados.put(new TopicPartition("t", 0), null);
        ListConsumerGroupOffsetsResult offsets = mock(ListConsumerGroupOffsetsResult.class);
        when(offsets.partitionsToOffsetAndMetadata()).thenReturn(KafkaFuture.completedFuture(confirmados));
        when(admin.listConsumerGroupOffsets(anyString())).thenReturn(offsets);

        ListOffsetsResult finales = mock(ListOffsetsResult.class);
        when(finales.all()).thenReturn(KafkaFuture.completedFuture(Map.of()));
        when(admin.listOffsets(anyMap())).thenReturn(finales);

        ConsumerGroupDetalle detalle = service().describirConsumerGroup("g");

        assertThat(detalle.offsets()).isEmpty();
        assertThat(detalle.lagTotal()).isZero();
        assertThat(detalle.estado()).isEqualTo("EMPTY");
    }
}
