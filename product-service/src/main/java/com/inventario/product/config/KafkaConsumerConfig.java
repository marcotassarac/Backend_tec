package com.inventario.product.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventario.product.event.AlertaStockEvent;
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
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.LinkedHashMap;
import java.util.Map;

/** Consumidor de inventario.alertas-stock con el mismo esquema de errores que report-service. */
@Configuration
public class KafkaConsumerConfig {

    public static final String GRUPO_ALERTAS = "product-alertas-stock";
    public static final String SUFIJO_DLT = ".DLT";

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    @Bean
    public ConsumerFactory<String, AlertaStockEvent> alertaConsumerFactory(KafkaProperties kafkaProperties,
                                                                          ObjectMapper objectMapper) {
        JsonDeserializer<AlertaStockEvent> json = new JsonDeserializer<>(AlertaStockEvent.class, objectMapper, false);
        return new DefaultKafkaConsumerFactory<>(kafkaProperties.buildConsumerProperties(null),
                new StringDeserializer(), new ErrorHandlingDeserializer<>(json));
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(KafkaProperties kafkaProperties, ObjectMapper objectMapper) {
        JsonSerializer<Object> json = new JsonSerializer<>(objectMapper);
        json.setAddTypeInfo(false);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(kafkaProperties.buildProducerProperties(null),
                new StringSerializer(), json));
    }

    @Bean
    public KafkaTemplate<String, byte[]> dltBytesTemplate(KafkaProperties kafkaProperties) {
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(kafkaProperties.buildProducerProperties(null),
                new StringSerializer(), new ByteArraySerializer()));
    }

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
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        handler.setRetryListeners((registro, ex, intento) ->
                log.warn("Reintento {} de {}-{}@{}: {}", intento, registro.topic(), registro.partition(),
                        registro.offset(), ex.getMessage()));
        return handler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, AlertaStockEvent> alertasListenerFactory(
            ConsumerFactory<String, AlertaStockEvent> alertaConsumerFactory,
            DefaultErrorHandler kafkaErrorHandler,
            @Value("${app.kafka.listeners.concurrency:3}") int concurrencia,
            @Value("${app.kafka.listeners.auto-startup:true}") boolean autoStartup) {
        ConcurrentKafkaListenerContainerFactory<String, AlertaStockEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(alertaConsumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        factory.setConcurrency(concurrencia);
        factory.setAutoStartup(autoStartup);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }
}
