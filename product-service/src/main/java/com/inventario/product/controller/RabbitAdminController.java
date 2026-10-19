package com.inventario.product.controller;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Administracion basica de colas RabbitMQ en tiempo de ejecucion. */
@RestController
@RequestMapping("/api/v1/admin/rabbitmq/queues")
public class RabbitAdminController {

    private final AmqpAdmin amqpAdmin;

    public RabbitAdminController(AmqpAdmin amqpAdmin) {
        this.amqpAdmin = amqpAdmin;
    }

    @PostMapping("/{queueName}")
    public ResponseEntity<Map<String, String>> crearCola(@PathVariable String queueName) {
        String nombre = amqpAdmin.declareQueue(QueueBuilder.durable(queueName).build());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("queue", nombre, "status", "creada"));
    }

    @DeleteMapping("/{queueName}")
    public ResponseEntity<Void> eliminarCola(@PathVariable String queueName) {
        amqpAdmin.deleteQueue(queueName);
        return ResponseEntity.noContent().build();
    }
}
