package com.inventario.product.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.stereotype.Service;

/** Encapsula el uso de AmqpAdmin para administrar colas RabbitMQ en tiempo de ejecucion. */
@Service
public class RabbitAdminService {

    private static final Logger log = LoggerFactory.getLogger(RabbitAdminService.class);

    private final AmqpAdmin amqpAdmin;

    public RabbitAdminService(AmqpAdmin amqpAdmin) {
        this.amqpAdmin = amqpAdmin;
    }

    /** Declara una cola durable y devuelve el nombre con el que quedo registrada. */
    public String crearCola(String queueName) {
        validarNombreCola(queueName);
        String nombre = amqpAdmin.declareQueue(QueueBuilder.durable(queueName).build());
        log.info("Cola {} creada", nombre);
        return nombre;
    }

    public void eliminarCola(String queueName) {
        validarNombreCola(queueName);
        amqpAdmin.deleteQueue(queueName);
        log.info("Cola {} eliminada", queueName);
    }

    /** El nombre es obligatorio, no puede estar en blanco ni contener espacios. */
    private void validarNombreCola(String queueName) {
        if (queueName == null || queueName.isBlank()) {
            throw new IllegalArgumentException("El nombre de la cola es obligatorio");
        }
        if (queueName.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("El nombre de la cola no puede contener espacios: '" + queueName + "'");
        }
    }
}
