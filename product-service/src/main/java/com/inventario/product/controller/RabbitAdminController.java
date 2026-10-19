package com.inventario.product.controller;

import com.inventario.product.service.RabbitAdminService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Administracion de RabbitMQ en tiempo de ejecucion. Solo expone los endpoints REST:
 * toda la logica (y el uso de AmqpAdmin) vive en RabbitAdminService.
 */
@RestController
@RequestMapping("/api/v1/admin/rabbitmq")
public class RabbitAdminController {

    private final RabbitAdminService rabbitAdminService;

    public RabbitAdminController(RabbitAdminService rabbitAdminService) {
        this.rabbitAdminService = rabbitAdminService;
    }

    // ---------- Colas ----------

    @PostMapping("/queues/{queueName}")
    public ResponseEntity<Map<String, String>> crearCola(@PathVariable String queueName) {
        String nombre = rabbitAdminService.crearCola(queueName);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("queue", nombre, "status", "creada"));
    }

    @DeleteMapping("/queues/{queueName}")
    public ResponseEntity<Void> eliminarCola(@PathVariable String queueName) {
        rabbitAdminService.eliminarCola(queueName);
        return ResponseEntity.noContent().build();
    }

    // ---------- Exchanges ----------

    @PostMapping("/exchanges/{exchangeName}")
    public ResponseEntity<Map<String, String>> crearExchange(@PathVariable String exchangeName,
                                                             @RequestParam(defaultValue = "direct") String type) {
        rabbitAdminService.crearExchange(exchangeName, type);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("exchange", exchangeName, "type", type, "status", "creado"));
    }

    @DeleteMapping("/exchanges/{exchangeName}")
    public ResponseEntity<Void> eliminarExchange(@PathVariable String exchangeName) {
        rabbitAdminService.eliminarExchange(exchangeName);
        return ResponseEntity.noContent().build();
    }

    // ---------- Bindings ----------

    @PostMapping("/bindings")
    public ResponseEntity<Map<String, String>> crearBinding(@RequestParam String queue,
                                                            @RequestParam String exchange,
                                                            @RequestParam(defaultValue = "") String routingKey) {
        rabbitAdminService.crearBinding(queue, exchange, routingKey);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("queue", queue, "exchange", exchange, "routingKey", routingKey, "status", "creado"));
    }

    @DeleteMapping("/bindings")
    public ResponseEntity<Void> eliminarBinding(@RequestParam String queue,
                                                @RequestParam String exchange,
                                                @RequestParam(defaultValue = "") String routingKey) {
        rabbitAdminService.eliminarBinding(queue, exchange, routingKey);
        return ResponseEntity.noContent().build();
    }
}
