package com.inventario.report.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventario.report.event.MovimientoEvent;
import com.inventario.report.exception.EventoInvalidoException;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cliente Kafka de report-service: consume inventario.movimientos con dos consumer groups
 * y publica en inventario.alertas-stock. Bootstrap servers, offsets y timeouts vienen de
 * spring.kafka.* en application.properties; aqui se definen serializacion y manejo de errores.
 */
@Configuration
public class KafkaConfig {

    public static final String GRUPO_ESTADISTICAS = "report-estadisticas";
    public static final String GRUPO_DETECTOR_ALERTAS = "report-detector-alertas";
    public static final String SUFIJO_DLT = ".DLT";

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    /**
     * ErrorHandlingDeserializer envuelve al JsonDeserializer: un mensaje con JSON corrupto no
     * bloquea la particion en un bucle de errores, llega al error handler y se manda al DLT.
     */
    @Bean
    public ConsumerFactory<String, MovimientoEvent> movimientoConsumerFactory(KafkaProperties kafkaProperties,
                                                                             ObjectMapper objectMapper) {
        JsonDeserializer<MovimientoEvent> json = new JsonDeserializer<>(MovimientoEvent.class, objectMapper, false);
        return new DefaultKafkaConsumerFactory<>(kafkaProperties.buildConsumerProperties(null),
                new StringDeserializer(), new ErrorHandlingDeserializer<>(json));
    }

    @Bean
    public ProducerFactory<String, Object> jsonProducerFactory(KafkaProperties kafkaProperties, ObjectMapper objectMapper) {
        JsonSerializer<Object> json = new JsonSerializer<>(objectMapper);
        json.setAddTypeInfo(false);
        return new DefaultKafkaProducerFactory<>(kafkaProperties.buildProducerProperties(null),
                new StringSerializer(), json);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> jsonProducerFactory) {
        return new KafkaTemplate<>(jsonProducerFactory);
    }

    /** Para reenviar al DLT los bytes originales de un mensaje que ni siquiera se pudo deserializar. */
    @Bean
    public KafkaTemplate<String, byte[]> dltBytesTemplate(KafkaProperties kafkaProperties) {
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(kafkaProperties.buildProducerProperties(null),
                new StringSerializer(), new ByteArraySerializer()));
    }

    /**
     * Reintenta (intentos totales configurables, con pausa fija) los errores transitorios, por
     * ejemplo no poder publicar una alerta. Lo que sigue fallando, o un evento invalido (que no
     * se reintenta), se publica en <topico>.DLT en la misma particion, con headers de la excepcion.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate,
                                                 KafkaTemplate<String, byte[]> dltBytesTemplate,
                                                 @Value("${app.kafka.retry.intentos:3}") long intentos,
                                                 @Value("${app.kafka.retry.intervalo-ms:1000}") long intervaloMs) {
        Map<Class<?>, KafkaOperations<?, ?>> templates = new LinkedHashMap<>();
        templates.put(byte[].class, dltBytesTemplate);
        templates.put(Object.class, kafkaTemplate);

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(templates,
                (registro, ex) -> {
                    log.error("[DLT] {}-{}@{} clave={} enviado a {}{}: {}", registro.topic(), registro.partition(),
                            registro.offset(), registro.key(), registro.topic(), SUFIJO_DLT, ex.getMessage());
                    return new TopicPartition(registro.topic() + SUFIJO_DLT, registro.partition());
                });

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(intervaloMs, intentos - 1));
        handler.addNotRetryableExceptions(EventoInvalidoException.class);
        handler.setRetryListeners((registro, ex, intento) ->
                log.warn("Reintento {} de {}-{}@{}: {}", intento, registro.topic(), registro.partition(),
                        registro.offset(), ex.getMessage()));
        return handler;
    }

    /**
     * Concurrencia 3 = un consumidor por particion dentro de cada grupo. AckMode.RECORD confirma
     * el offset tras procesar cada evento (at-least-once; los duplicados se descartan por eventId).
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, MovimientoEvent> movimientosListenerFactory(
            ConsumerFactory<String, MovimientoEvent> movimientoConsumerFactory,
            DefaultErrorHandler kafkaErrorHandler,
            @Value("${app.kafka.listeners.concurrency:3}") int concurrencia,
            @Value("${app.kafka.listeners.auto-startup:true}") boolean autoStartup) {
        ConcurrentKafkaListenerContainerFactory<String, MovimientoEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(movimientoConsumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        factory.setConcurrency(concurrencia);
        factory.setAutoStartup(autoStartup);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }
}
