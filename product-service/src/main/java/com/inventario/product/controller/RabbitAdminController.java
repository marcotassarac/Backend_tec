package com.inventario.product.controller;

import com.inventario.product.exception.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Administracion de RabbitMQ en tiempo de ejecucion: colas, exchanges y bindings. */
@RestController
@RequestMapping("/api/v1/admin/rabbitmq")
public class RabbitAdminController {

    public record BindingRequest(@NotBlank String cola, @NotBlank String exchange, String routingKey) {
    }

    private final AmqpAdmin amqpAdmin;

    public RabbitAdminController(AmqpAdmin amqpAdmin) {
        this.amqpAdmin = amqpAdmin;
    }

    // ---------- Colas ----------

    @PostMapping("/queues/{queueName}")
    public ResponseEntity<Map<String, String>> crearCola(@PathVariable String queueName) {
        String nombre = amqpAdmin.declareQueue(QueueBuilder.durable(queueName).build());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("queue", nombre, "status", "creada"));
    }

    @DeleteMapping("/queues/{queueName}")
    public ResponseEntity<Void> eliminarCola(@PathVariable String queueName) {
        amqpAdmin.deleteQueue(queueName);
        return ResponseEntity.noContent().build();
    }

    // ---------- Exchanges ----------

    @PostMapping("/exchanges/{exchangeName}")
    public ResponseEntity<Map<String, String>> crearExchange(@PathVariable String exchangeName,
                                                             @RequestParam(defaultValue = "direct") String tipo) {
        Exchange exchange = switch (tipo.toLowerCase()) {
            case "direct" -> ExchangeBuilder.directExchange(exchangeName).durable(true).build();
            case "topic" -> ExchangeBuilder.topicExchange(exchangeName).durable(true).build();
            case "fanout" -> ExchangeBuilder.fanoutExchange(exchangeName).durable(true).build();
            default -> throw new BusinessException("Tipo de exchange invalido: " + tipo + ". Use direct, topic o fanout");
        };
        amqpAdmin.declareExchange(exchange);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("exchange", exchangeName, "tipo", exchange.getType(), "status", "creado"));
    }

    @DeleteMapping("/exchanges/{exchangeName}")
    public ResponseEntity<Void> eliminarExchange(@PathVariable String exchangeName) {
        amqpAdmin.deleteExchange(exchangeName);
        return ResponseEntity.noContent().build();
    }

    // ---------- Bindings ----------

    @PostMapping("/bindings")
    public ResponseEntity<Map<String, String>> crearBinding(@Valid @RequestBody BindingRequest request) {
        amqpAdmin.declareBinding(toBinding(request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("cola", request.cola(), "exchange", request.exchange(),
                        "routingKey", routingKey(request), "status", "creado"));
    }

    @DeleteMapping("/bindings")
    public ResponseEntity<Void> eliminarBinding(@Valid @RequestBody BindingRequest request) {
        amqpAdmin.removeBinding(toBinding(request));
        return ResponseEntity.noContent().build();
    }

    private Binding toBinding(BindingRequest request) {
        return new Binding(request.cola(), Binding.DestinationType.QUEUE, request.exchange(),
                routingKey(request), null);
    }

    private String routingKey(BindingRequest request) {
        return request.routingKey() != null ? request.routingKey() : "";
    }
}
