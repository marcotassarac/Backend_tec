package com.inventario.inventory.config;

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
 * Declara la cola que consume este servicio con los mismos argumentos que product-service
 * (si difieren, RabbitMQ rechaza la declaracion), para que el listener pueda arrancar
 * aunque product-service aun no haya creado la topologia.
 */
@Configuration
public class RabbitMQConfig {

    public static final String DEAD_LETTER_EXCHANGE = "pedidos.dlx";
    public static final String COLA_INVENTARIO = "cola.inventario";
    public static final String COLA_INVENTARIO_DLQ = "cola.inventario.dlq";

    @Bean
    public DirectExchange pedidosDeadLetterExchange() {
        return ExchangeBuilder.directExchange(DEAD_LETTER_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue colaInventario() {
        return QueueBuilder.durable(COLA_INVENTARIO)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(COLA_INVENTARIO_DLQ)
                .build();
    }

    @Bean
    public Queue colaInventarioDlq() {
        return QueueBuilder.durable(COLA_INVENTARIO_DLQ).build();
    }

    @Bean
    public Binding inventarioDlqBinding() {
        return BindingBuilder.bind(colaInventarioDlq()).to(pedidosDeadLetterExchange()).with(COLA_INVENTARIO_DLQ);
    }

    /** Convierte el JSON que publica product-service a objetos Java. */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
