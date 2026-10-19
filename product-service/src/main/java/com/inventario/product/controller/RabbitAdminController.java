package com.inventario.product.controller;

import com.inventario.product.service.RabbitAdminService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Administracion basica de colas RabbitMQ en tiempo de ejecucion. */
@RestController
@RequestMapping("/api/v1/admin/rabbitmq/queues")
public class RabbitAdminController {

    private final RabbitAdminService rabbitAdminService;

    public RabbitAdminController(RabbitAdminService rabbitAdminService) {
        this.rabbitAdminService = rabbitAdminService;
    }

    @PostMapping("/{queueName}")
    public ResponseEntity<Map<String, String>> crearCola(@PathVariable String queueName) {
        String nombre = rabbitAdminService.crearCola(queueName);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("queue", nombre, "status", "creada"));
    }

    @DeleteMapping("/{queueName}")
    public ResponseEntity<Void> eliminarCola(@PathVariable String queueName) {
        rabbitAdminService.eliminarCola(queueName);
        return ResponseEntity.noContent().build();
    }
}
