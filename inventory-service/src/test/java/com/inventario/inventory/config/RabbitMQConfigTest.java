package com.inventario.inventory.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifica que la topologia RabbitMQ (colas, exchange, binding) quede declarada como se espera. */
class RabbitMQConfigTest {

    private final RabbitMQConfig config = new RabbitMQConfig();

    @Test
    void pedidosDeadLetterExchange_debeSerDurableConElNombreEsperado() {
        DirectExchange exchange = config.pedidosDeadLetterExchange();

        assertThat(exchange.getName()).isEqualTo(RabbitMQConfig.DEAD_LETTER_EXCHANGE);
        assertThat(exchange.isDurable()).isTrue();
    }

    @Test
    void colaInventario_debeApuntarAlDeadLetterExchangeConSuRoutingKey() {
        Queue cola = config.colaInventario();

        assertThat(cola.getName()).isEqualTo(RabbitMQConfig.COLA_INVENTARIO);
        assertThat(cola.getArguments())
                .containsEntry("x-dead-letter-exchange", RabbitMQConfig.DEAD_LETTER_EXCHANGE)
                .containsEntry("x-dead-letter-routing-key", RabbitMQConfig.COLA_INVENTARIO_DLQ);
    }

    @Test
    void colaInventarioDlq_debeSerDurableConElNombreEsperado() {
        Queue dlq = config.colaInventarioDlq();

        assertThat(dlq.getName()).isEqualTo(RabbitMQConfig.COLA_INVENTARIO_DLQ);
        assertThat(dlq.isDurable()).isTrue();
    }

    @Test
    void inventarioDlqBinding_debeEnlazarLaColaDlqConElExchange() {
        Binding binding = config.inventarioDlqBinding();

        assertThat(binding.getDestination()).isEqualTo(RabbitMQConfig.COLA_INVENTARIO_DLQ);
        assertThat(binding.getExchange()).isEqualTo(RabbitMQConfig.DEAD_LETTER_EXCHANGE);
        assertThat(binding.getRoutingKey()).isEqualTo(RabbitMQConfig.COLA_INVENTARIO_DLQ);
    }

    @Test
    void jsonMessageConverter_debeRetornarUnConversorJackson() {
        assertThat(config.jsonMessageConverter()).isInstanceOf(Jackson2JsonMessageConverter.class);
    }
}
