package com.inventario.product.config;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import java.time.Duration;

/**
 * product-service es el administrador de la plataforma Kafka: al arrancar, KafkaAdmin crea (o
 * deja como estan) estos topicos. Cada flujo tiene la configuracion que su uso requiere:
 *
 * - inventario.movimientos: historial de eventos. cleanup=delete con retencion de 7 dias,
 *   suficiente para reprocesar o crear un consumer group nuevo que lea la semana completa.
 * - inventario.alertas-stock: estado actual por producto. cleanup=compact conserva solo el
 *   ultimo evento de cada clave (productoId), asi el topico nunca crece mas que el catalogo.
 *
 * Ambos usan 3 particiones (hasta 3 consumidores en paralelo por grupo) y factor de replicacion 3
 * con min.insync.replicas=2: con acks=all se tolera la caida de 1 broker sin perder eventos.
 */
@Configuration
public class KafkaTopicsConfig {

    public static final int PARTICIONES = 3;
    public static final int REPLICAS = 3;
    public static final String MIN_INSYNC = "2";

    /** Cliente de administracion que usa KafkaAdminService, con los mismos brokers que KafkaAdmin. */
    @Bean(destroyMethod = "close")
    public Admin kafkaAdminClient(KafkaAdmin kafkaAdmin) {
        return Admin.create(kafkaAdmin.getConfigurationProperties());
    }

    @Bean
    public NewTopic topicoMovimientos(@Value("${app.kafka.topics.movimientos}") String nombre) {
        return TopicBuilder.name(nombre)
                .partitions(PARTICIONES)
                .replicas(REPLICAS)
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(Duration.ofDays(7).toMillis()))
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, MIN_INSYNC)
                .build();
    }

    @Bean
    public NewTopic topicoAlertasStock(@Value("${app.kafka.topics.alertas-stock}") String nombre) {
        return TopicBuilder.name(nombre)
                .partitions(PARTICIONES)
                .replicas(REPLICAS)
                .compact()
                .config(TopicConfig.MIN_COMPACTION_LAG_MS_CONFIG, String.valueOf(Duration.ofMinutes(1).toMillis()))
                .config(TopicConfig.SEGMENT_MS_CONFIG, String.valueOf(Duration.ofHours(1).toMillis()))
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, MIN_INSYNC)
                .build();
    }

    /** Los DLT replican las particiones de su topico (el recoverer reenvia a la misma particion). */
    @Bean
    public NewTopic topicoMovimientosDlt(@Value("${app.kafka.topics.movimientos}") String nombre) {
        return dlt(nombre);
    }

    @Bean
    public NewTopic topicoAlertasStockDlt(@Value("${app.kafka.topics.alertas-stock}") String nombre) {
        return dlt(nombre);
    }

    private NewTopic dlt(String topicoOrigen) {
        return TopicBuilder.name(topicoOrigen + KafkaConsumerConfig.SUFIJO_DLT)
                .partitions(PARTICIONES)
                .replicas(REPLICAS)
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(Duration.ofDays(14).toMillis()))
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, MIN_INSYNC)
                .build();
    }
}
