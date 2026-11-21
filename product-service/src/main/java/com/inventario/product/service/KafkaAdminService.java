package com.inventario.product.service;

import com.inventario.product.dto.kafka.KafkaAdminDtos.ConsumerGroupDetalle;
import com.inventario.product.dto.kafka.KafkaAdminDtos.ConsumerGroupResumen;
import com.inventario.product.dto.kafka.KafkaAdminDtos.CrearTopicoRequest;
import com.inventario.product.dto.kafka.KafkaAdminDtos.LagParticion;
import com.inventario.product.dto.kafka.KafkaAdminDtos.MiembroGrupo;
import com.inventario.product.dto.kafka.KafkaAdminDtos.ParticionInfo;
import com.inventario.product.dto.kafka.KafkaAdminDtos.TopicoDetalle;
import com.inventario.product.dto.kafka.KafkaAdminDtos.TopicoResumen;
import com.inventario.product.exception.KafkaAdminException;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo;
import org.apache.kafka.clients.admin.NewPartitions;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.errors.GroupIdNotFoundException;
import org.apache.kafka.common.errors.GroupNotEmptyException;
import org.apache.kafka.common.errors.InvalidConfigurationException;
import org.apache.kafka.common.errors.InvalidPartitionsException;
import org.apache.kafka.common.errors.InvalidReplicationFactorException;
import org.apache.kafka.common.errors.InvalidRequestException;
import org.apache.kafka.common.errors.InvalidTopicException;
import org.apache.kafka.common.errors.PolicyViolationException;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.errors.UnknownMemberIdException;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Capa de gestion sobre el cluster Kafka usando la AdminClient oficial: topicos, configuraciones,
 * particiones y consumer groups (con su lag). Traduce los errores de Kafka a codigos HTTP.
 */
@Service
public class KafkaAdminService {

    static final long TIMEOUT_SEGUNDOS = 10;

    /** Configuraciones que siempre se muestran al describir un topico, aunque tengan su valor por defecto. */
    static final Set<String> CONFIGS_CLAVE = Set.of("cleanup.policy", "retention.ms", "retention.bytes",
            "min.insync.replicas", "segment.ms", "min.compaction.lag.ms", "max.message.bytes");

    private static final Map<Class<? extends Throwable>, HttpStatus> ESTADOS_POR_ERROR = new LinkedHashMap<>();

    static {
        ESTADOS_POR_ERROR.put(UnknownTopicOrPartitionException.class, HttpStatus.NOT_FOUND);
        ESTADOS_POR_ERROR.put(GroupIdNotFoundException.class, HttpStatus.NOT_FOUND);
        ESTADOS_POR_ERROR.put(TopicExistsException.class, HttpStatus.CONFLICT);
        ESTADOS_POR_ERROR.put(GroupNotEmptyException.class, HttpStatus.CONFLICT);
        ESTADOS_POR_ERROR.put(UnknownMemberIdException.class, HttpStatus.CONFLICT);
        ESTADOS_POR_ERROR.put(InvalidPartitionsException.class, HttpStatus.BAD_REQUEST);
        ESTADOS_POR_ERROR.put(InvalidReplicationFactorException.class, HttpStatus.BAD_REQUEST);
        ESTADOS_POR_ERROR.put(InvalidConfigurationException.class, HttpStatus.BAD_REQUEST);
        ESTADOS_POR_ERROR.put(InvalidTopicException.class, HttpStatus.BAD_REQUEST);
        ESTADOS_POR_ERROR.put(InvalidRequestException.class, HttpStatus.BAD_REQUEST);
        ESTADOS_POR_ERROR.put(PolicyViolationException.class, HttpStatus.BAD_REQUEST);
    }

    private final Admin admin;

    public KafkaAdminService(Admin kafkaAdminClient) {
        this.admin = kafkaAdminClient;
    }

    // ---------------- Topicos ----------------

    public List<TopicoResumen> listarTopicos() {
        Set<String> nombres = esperar(admin.listTopics().names(), "listar los topicos");
        Map<String, TopicDescription> descripciones =
                esperar(admin.describeTopics(nombres).allTopicNames(), "describir los topicos");
        return descripciones.values().stream()
                .map(d -> new TopicoResumen(d.name(), d.partitions().size(),
                        d.partitions().get(0).replicas().size()))
                .sorted(Comparator.comparing(TopicoResumen::nombre))
                .toList();
    }

    public TopicoDetalle describirTopico(String nombre) {
        TopicDescription descripcion = esperar(admin.describeTopics(List.of(nombre)).allTopicNames(),
                "describir el topico " + nombre).get(nombre);
        ConfigResource recurso = new ConfigResource(ConfigResource.Type.TOPIC, nombre);
        Config config = esperar(admin.describeConfigs(List.of(recurso)).all(),
                "leer la configuracion de " + nombre).get(recurso);

        List<ParticionInfo> particiones = descripcion.partitions().stream()
                .map(p -> new ParticionInfo(p.partition(),
                        p.leader() != null ? p.leader().id() : null,
                        ids(p.replicas()), ids(p.isr())))
                .toList();
        Map<String, String> configuraciones = config.entries().stream()
                .filter(e -> e.value() != null)
                .filter(e -> CONFIGS_CLAVE.contains(e.name())
                        || e.source() == ConfigEntry.ConfigSource.DYNAMIC_TOPIC_CONFIG)
                .collect(Collectors.toMap(ConfigEntry::name, ConfigEntry::value, (a, b) -> a, TreeMap::new));
        return new TopicoDetalle(nombre, particiones, configuraciones);
    }

    public TopicoResumen crearTopico(CrearTopicoRequest request) {
        NewTopic topico = new NewTopic(request.nombre(), request.particiones(), request.replicas());
        if (request.configuraciones() != null) {
            topico.configs(request.configuraciones());
        }
        esperar(admin.createTopics(List.of(topico)).all(), "crear el topico " + request.nombre());
        return new TopicoResumen(request.nombre(), request.particiones(), request.replicas());
    }

    /** Cambia solo las claves indicadas (SET incremental); el resto de la configuracion se mantiene. */
    public TopicoDetalle actualizarConfiguracion(String nombre, Map<String, String> configuraciones) {
        ConfigResource recurso = new ConfigResource(ConfigResource.Type.TOPIC, nombre);
        List<AlterConfigOp> operaciones = configuraciones.entrySet().stream()
                .map(e -> new AlterConfigOp(new ConfigEntry(e.getKey(), e.getValue()), AlterConfigOp.OpType.SET))
                .toList();
        esperar(admin.incrementalAlterConfigs(Map.of(recurso, operaciones)).all(),
                "actualizar la configuracion de " + nombre);
        return describirTopico(nombre);
    }

    /** Kafka solo permite aumentar particiones: reducirlas romperia el orden por clave. */
    public TopicoDetalle aumentarParticiones(String nombre, int total) {
        esperar(admin.createPartitions(Map.of(nombre, NewPartitions.increaseTo(total))).all(),
                "aumentar las particiones de " + nombre);
        return describirTopico(nombre);
    }

    public void eliminarTopico(String nombre) {
        esperar(admin.deleteTopics(List.of(nombre)).all(), "eliminar el topico " + nombre);
    }

    // ---------------- Consumer groups ----------------

    public List<ConsumerGroupResumen> listarConsumerGroups() {
        return esperar(admin.listConsumerGroups().all(), "listar los consumer groups").stream()
                .map(g -> new ConsumerGroupResumen(g.groupId(), g.state().map(Enum::name).orElse("DESCONOCIDO")))
                .sorted(Comparator.comparing(ConsumerGroupResumen::groupId))
                .toList();
    }

    /**
     * Lag = ultimo offset del topico - offset confirmado por el grupo, por particion. Un lag que
     * crece indica que el grupo no alcanza a procesar lo que llega (faltan consumidores o uno esta caido).
     */
    public ConsumerGroupDetalle describirConsumerGroup(String groupId) {
        ConsumerGroupDescription descripcion = esperar(admin.describeConsumerGroups(List.of(groupId)).all(),
                "describir el grupo " + groupId).get(groupId);
        Map<TopicPartition, OffsetAndMetadata> confirmados = esperar(
                admin.listConsumerGroupOffsets(groupId).partitionsToOffsetAndMetadata(),
                "leer los offsets del grupo " + groupId);
        Map<TopicPartition, OffsetAndMetadata> validos = confirmados.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        Map<TopicPartition, ListOffsetsResultInfo> finales = esperar(
                admin.listOffsets(validos.keySet().stream()
                        .collect(Collectors.toMap(Function.identity(), tp -> OffsetSpec.latest()))).all(),
                "leer los offsets finales del grupo " + groupId);

        List<LagParticion> offsets = validos.entrySet().stream()
                .map(e -> {
                    long confirmado = e.getValue().offset();
                    long fin = finales.get(e.getKey()).offset();
                    return new LagParticion(e.getKey().topic(), e.getKey().partition(), confirmado, fin,
                            Math.max(0, fin - confirmado));
                })
                .sorted(Comparator.comparing(LagParticion::topico).thenComparing(LagParticion::particion))
                .toList();
        List<MiembroGrupo> miembros = descripcion.members().stream()
                .map(m -> new MiembroGrupo(m.clientId(), m.host(), m.assignment().topicPartitions().stream()
                        .map(tp -> tp.topic() + "-" + tp.partition()).sorted().toList()))
                .toList();
        long lagTotal = offsets.stream().mapToLong(LagParticion::lag).sum();
        return new ConsumerGroupDetalle(groupId, descripcion.state().name(), miembros, offsets, lagTotal);
    }

    /**
     * Mueve los offsets del grupo al inicio (reprocesar todo lo retenido) o al final (saltar el
     * atraso). Kafka exige que el grupo no tenga consumidores activos: si los tiene responde 409.
     */
    public ConsumerGroupDetalle reiniciarOffsets(String groupId, String topico, String posicion) {
        TopicDescription descripcion = esperar(admin.describeTopics(List.of(topico)).allTopicNames(),
                "describir el topico " + topico).get(topico);
        OffsetSpec spec = "earliest".equals(posicion) ? OffsetSpec.earliest() : OffsetSpec.latest();
        Map<TopicPartition, OffsetSpec> pedido = descripcion.partitions().stream()
                .collect(Collectors.toMap(p -> new TopicPartition(topico, p.partition()), p -> spec));
        Map<TopicPartition, ListOffsetsResultInfo> destino = esperar(admin.listOffsets(pedido).all(),
                "leer los offsets de " + topico);
        Map<TopicPartition, OffsetAndMetadata> nuevos = destino.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> new OffsetAndMetadata(e.getValue().offset())));
        esperar(admin.alterConsumerGroupOffsets(groupId, nuevos).all(), "mover los offsets del grupo " + groupId);
        return describirConsumerGroup(groupId);
    }

    public void eliminarConsumerGroup(String groupId) {
        esperar(admin.deleteConsumerGroups(List.of(groupId)).all(), "eliminar el grupo " + groupId);
    }

    // ---------------- Utilidades ----------------

    private static List<Integer> ids(List<Node> nodos) {
        return nodos.stream().map(Node::id).toList();
    }

    private <T> T esperar(KafkaFuture<T> future, String operacion) {
        try {
            return future.get(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new KafkaAdminException(HttpStatus.SERVICE_UNAVAILABLE, "Operacion interrumpida al " + operacion, ex);
        } catch (TimeoutException ex) {
            throw new KafkaAdminException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Kafka no respondio a tiempo al " + operacion, ex);
        } catch (ExecutionException ex) {
            Throwable causa = ex.getCause();
            HttpStatus status = ESTADOS_POR_ERROR.entrySet().stream()
                    .filter(e -> e.getKey().isInstance(causa))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(HttpStatus.BAD_GATEWAY);
            throw new KafkaAdminException(status, "No se pudo " + operacion + ": " + causa.getMessage(), causa);
        }
    }
}
