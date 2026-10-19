package com.inventario.product.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Encapsula todo el uso de AmqpAdmin para administrar colas, exchanges y bindings
 * de RabbitMQ en tiempo de ejecucion. Los controladores nunca llaman a AmqpAdmin.
 */
@Service
public class RabbitAdminService {

    private static final Logger log = LoggerFactory.getLogger(RabbitAdminService.class);

    private final AmqpAdmin amqpAdmin;

    public RabbitAdminService(AmqpAdmin amqpAdmin) {
        this.amqpAdmin = amqpAdmin;
    }

    // ---------- Colas ----------

    /** Declara una cola durable y devuelve el nombre con el que quedo registrada. */
    public String crearCola(String queueName) {
        validarNombre(queueName, "cola");
        String nombre = amqpAdmin.declareQueue(QueueBuilder.durable(queueName).build());
        log.info("Cola {} creada", nombre);
        return nombre;
    }

    public void eliminarCola(String queueName) {
        validarNombre(queueName, "cola");
        amqpAdmin.deleteQueue(queueName);
        log.info("Cola {} eliminada", queueName);
    }

    // ---------- Exchanges ----------

    /** Declara un exchange durable del tipo indicado (direct, topic o fanout). */
    public void crearExchange(String exchangeName, String tipo) {
        validarNombre(exchangeName, "exchange");
        amqpAdmin.declareExchange(construirExchange(exchangeName, tipo));
        log.info("Exchange {} ({}) creado", exchangeName, tipo);
    }

    public void eliminarExchange(String exchangeName) {
        validarNombre(exchangeName, "exchange");
        amqpAdmin.deleteExchange(exchangeName);
        log.info("Exchange {} eliminado", exchangeName);
    }

    // ---------- Bindings ----------

    /** Enlaza una cola existente a un exchange existente con la routing key indicada. */
    public void crearBinding(String queueName, String exchangeName, String routingKey) {
        Binding binding = construirBinding(queueName, exchangeName, routingKey);
        amqpAdmin.declareBinding(binding);
        log.info("Binding {} -> {} ({}) creado", exchangeName, queueName, binding.getRoutingKey());
    }

    public void eliminarBinding(String queueName, String exchangeName, String routingKey) {
        Binding binding = construirBinding(queueName, exchangeName, routingKey);
        amqpAdmin.removeBinding(binding);
        log.info("Binding {} -> {} ({}) eliminado", exchangeName, queueName, binding.getRoutingKey());
    }

    // ---------- Helpers ----------

    private Exchange construirExchange(String exchangeName, String tipo) {
        String tipoNormalizado = tipo == null ? "" : tipo.trim().toLowerCase(Locale.ROOT);
        return switch (tipoNormalizado) {
            case "direct" -> ExchangeBuilder.directExchange(exchangeName).durable(true).build();
            case "topic" -> ExchangeBuilder.topicExchange(exchangeName).durable(true).build();
            case "fanout" -> ExchangeBuilder.fanoutExchange(exchangeName).durable(true).build();
            default -> throw new IllegalArgumentException(
                    "Tipo de exchange invalido: '" + tipo + "'. Valores permitidos: direct, topic, fanout");
        };
    }

    private Binding construirBinding(String queueName, String exchangeName, String routingKey) {
        validarNombre(queueName, "cola");
        validarNombre(exchangeName, "exchange");
        // Una routing key vacia es valida (por ejemplo en exchanges fanout), pero no puede tener espacios
        String clave = routingKey == null ? "" : routingKey;
        if (clave.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("La routing key no puede contener espacios: '" + clave + "'");
        }
        return new Binding(queueName, Binding.DestinationType.QUEUE, exchangeName, clave, null);
    }

    /** El nombre es obligatorio, no puede estar en blanco ni contener espacios. */
    private void validarNombre(String nombre, String recurso) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre de la " + recurso + " es obligatorio");
        }
        if (nombre.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException(
                    "El nombre de la " + recurso + " no puede contener espacios: '" + nombre + "'");
        }
    }
}
