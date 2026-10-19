package com.inventario.report.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declara las colas que consume este servicio (cola.notificaciones y cola.auditoria) con los
 * mismos argumentos que product-service (si difieren, RabbitMQ rechaza la declaracion), para
 * que los listeners puedan arrancar aunque product-service aun no haya creado la topologia.
 */
@Configuration
public class RabbitMQConfig {

    public static final String DEAD_LETTER_EXCHANGE = "pedidos.dlx";
    public static final String COLA_NOTIFICACIONES = "cola.notificaciones";
    public static final String COLA_NOTIFICACIONES_DLQ = "cola.notificaciones.dlq";
    public static final String COLA_AUDITORIA = "cola.auditoria";
    public static final String COLA_AUDITORIA_DLQ = "cola.auditoria.dlq";

    @Bean
    public DirectExchange pedidosDeadLetterExchange() {
        return ExchangeBuilder.directExchange(DEAD_LETTER_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue colaNotificaciones() {
        return QueueBuilder.durable(COLA_NOTIFICACIONES)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(COLA_NOTIFICACIONES_DLQ)
                .build();
    }

    @Bean
    public Queue colaNotificacionesDlq() {
        return QueueBuilder.durable(COLA_NOTIFICACIONES_DLQ).build();
    }

    @Bean
    public Binding notificacionesDlqBinding() {
        return BindingBuilder.bind(colaNotificacionesDlq()).to(pedidosDeadLetterExchange()).with(COLA_NOTIFICACIONES_DLQ);
    }

    @Bean
    public Queue colaAuditoria() {
        return QueueBuilder.durable(COLA_AUDITORIA)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(COLA_AUDITORIA_DLQ)
                .build();
    }

    @Bean
    public Queue colaAuditoriaDlq() {
        return QueueBuilder.durable(COLA_AUDITORIA_DLQ).build();
    }

    @Bean
    public Binding auditoriaDlqBinding() {
        return BindingBuilder.bind(colaAuditoriaDlq()).to(pedidosDeadLetterExchange()).with(COLA_AUDITORIA_DLQ);
    }

    /** Convierte el JSON que publica product-service a objetos Java. */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
