package com.inventario.report.config;

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
 * Declara la cola que consume este servicio con los mismos argumentos que product-service
 * (si difieren, RabbitMQ rechaza la declaracion), para que el listener pueda arrancar
 * aunque product-service aun no haya creado la topologia.
 */
@Configuration
public class RabbitMQConfig {

    public static final String DEAD_LETTER_EXCHANGE = "pedidos.dlx";
    public static final String COLA_NOTIFICACIONES = "cola.notificaciones";
    public static final String COLA_NOTIFICACIONES_DLQ = "cola.notificaciones.dlq";

    public static final String MOVIMIENTOS_EXCHANGE = "movimientos.topic";
    public static final String COLA_NOTIFICACIONES_ADMIN = "cola.notificaciones-admin";
    public static final String COLA_NOTIFICACIONES_ADMIN_DLQ = "cola.notificaciones-admin.dlq";
    public static final String COLA_AUDITORIA_MOVIMIENTOS = "cola.auditoria-movimientos";
    public static final String COLA_AUDITORIA_MOVIMIENTOS_DLQ = "cola.auditoria-movimientos.dlq";
    /** Un aviso al ADMIN que nadie lee en 24 h ya no es util: expira y pasa a la DLQ. */
    public static final int TTL_NOTIFICACION_MS = 24 * 60 * 60 * 1000;

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

    // ---------- Movimientos de inventario (buzon ADMIN y auditoria) ----------

    @Bean
    public TopicExchange movimientosExchange() {
        return ExchangeBuilder.topicExchange(MOVIMIENTOS_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue colaNotificacionesAdmin() {
        return QueueBuilder.durable(COLA_NOTIFICACIONES_ADMIN)
                .ttl(TTL_NOTIFICACION_MS)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(COLA_NOTIFICACIONES_ADMIN_DLQ)
                .build();
    }

    @Bean
    public Queue colaNotificacionesAdminDlq() {
        return QueueBuilder.durable(COLA_NOTIFICACIONES_ADMIN_DLQ).build();
    }

    @Bean
    public Queue colaAuditoriaMovimientos() {
        return QueueBuilder.durable(COLA_AUDITORIA_MOVIMIENTOS)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(COLA_AUDITORIA_MOVIMIENTOS_DLQ)
                .build();
    }

    @Bean
    public Queue colaAuditoriaMovimientosDlq() {
        return QueueBuilder.durable(COLA_AUDITORIA_MOVIMIENTOS_DLQ).build();
    }

    /** Solo los movimientos de operadores llegan al buzon del ADMIN. */
    @Bean
    public Binding notificacionesAdminBinding() {
        return BindingBuilder.bind(colaNotificacionesAdmin()).to(movimientosExchange()).with("movimiento.operador");
    }

    @Bean
    public Binding auditoriaMovimientosBinding() {
        return BindingBuilder.bind(colaAuditoriaMovimientos()).to(movimientosExchange()).with("movimiento.#");
    }

    @Bean
    public Binding notificacionesAdminDlqBinding() {
        return BindingBuilder.bind(colaNotificacionesAdminDlq()).to(pedidosDeadLetterExchange())
                .with(COLA_NOTIFICACIONES_ADMIN_DLQ);
    }

    @Bean
    public Binding auditoriaMovimientosDlqBinding() {
        return BindingBuilder.bind(colaAuditoriaMovimientosDlq()).to(pedidosDeadLetterExchange())
                .with(COLA_AUDITORIA_MOVIMIENTOS_DLQ);
    }

    /** Convierte el JSON que publica product-service a objetos Java. */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
