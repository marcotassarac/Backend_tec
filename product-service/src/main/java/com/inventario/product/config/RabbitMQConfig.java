package com.inventario.product.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topologia de RabbitMQ para los eventos de producto:
 * - pedidos.direct: entrega a una cola concreta usando su nombre como routing key.
 * - pedidos.topic: difusion por patron (producto.#) a ambas colas.
 * - pedidos.dlx: recibe los mensajes rechazados o expirados y los deja en la DLQ de cada cola.
 */
@Configuration
public class RabbitMQConfig {

    public static final String TOPIC_EXCHANGE = "pedidos.topic";
    public static final String DIRECT_EXCHANGE = "pedidos.direct";
    public static final String DEAD_LETTER_EXCHANGE = "pedidos.dlx";

    public static final String COLA_INVENTARIO = "cola.inventario";
    public static final String COLA_NOTIFICACIONES = "cola.notificaciones";
    public static final String COLA_INVENTARIO_DLQ = "cola.inventario.dlq";
    public static final String COLA_NOTIFICACIONES_DLQ = "cola.notificaciones.dlq";

    public static final String TOPIC_ROUTING_PRODUCTO = "producto.#";

    // ---------- Exchanges ----------

    @Bean
    public TopicExchange pedidosTopicExchange() {
        return ExchangeBuilder.topicExchange(TOPIC_EXCHANGE).durable(true).build();
    }

    @Bean
    public DirectExchange pedidosDirectExchange() {
        return ExchangeBuilder.directExchange(DIRECT_EXCHANGE).durable(true).build();
    }

    @Bean
    public DirectExchange pedidosDeadLetterExchange() {
        return ExchangeBuilder.directExchange(DEAD_LETTER_EXCHANGE).durable(true).build();
    }

    // ---------- Colas principales (con dead-lettering hacia pedidos.dlx) ----------

    @Bean
    public Queue colaInventario() {
        return QueueBuilder.durable(COLA_INVENTARIO)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(COLA_INVENTARIO_DLQ)
                .build();
    }

    @Bean
    public Queue colaNotificaciones() {
        return QueueBuilder.durable(COLA_NOTIFICACIONES)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(COLA_NOTIFICACIONES_DLQ)
                .build();
    }

    // ---------- Dead Letter Queues ----------

    @Bean
    public Queue colaInventarioDlq() {
        return QueueBuilder.durable(COLA_INVENTARIO_DLQ).build();
    }

    @Bean
    public Queue colaNotificacionesDlq() {
        return QueueBuilder.durable(COLA_NOTIFICACIONES_DLQ).build();
    }

    // ---------- Bindings: direct ----------

    @Bean
    public Binding inventarioDirectBinding() {
        return BindingBuilder.bind(colaInventario()).to(pedidosDirectExchange()).with(COLA_INVENTARIO);
    }

    @Bean
    public Binding notificacionesDirectBinding() {
        return BindingBuilder.bind(colaNotificaciones()).to(pedidosDirectExchange()).with(COLA_NOTIFICACIONES);
    }

    // ---------- Bindings: topic ----------

    @Bean
    public Binding inventarioTopicBinding() {
        return BindingBuilder.bind(colaInventario()).to(pedidosTopicExchange()).with(TOPIC_ROUTING_PRODUCTO);
    }

    @Bean
    public Binding notificacionesTopicBinding() {
        return BindingBuilder.bind(colaNotificaciones()).to(pedidosTopicExchange()).with(TOPIC_ROUTING_PRODUCTO);
    }

    // ---------- Bindings: dead letter ----------

    @Bean
    public Binding inventarioDlqBinding() {
        return BindingBuilder.bind(colaInventarioDlq()).to(pedidosDeadLetterExchange()).with(COLA_INVENTARIO_DLQ);
    }

    @Bean
    public Binding notificacionesDlqBinding() {
        return BindingBuilder.bind(colaNotificacionesDlq()).to(pedidosDeadLetterExchange()).with(COLA_NOTIFICACIONES_DLQ);
    }

    /** Serializa los eventos como JSON en vez de serializacion Java. */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
