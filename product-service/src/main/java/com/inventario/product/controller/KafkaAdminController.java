package com.inventario.product.controller;

import com.inventario.product.dto.kafka.KafkaAdminDtos.ActualizarConfigRequest;
import com.inventario.product.dto.kafka.KafkaAdminDtos.AumentarParticionesRequest;
import com.inventario.product.dto.kafka.KafkaAdminDtos.ConsumerGroupDetalle;
import com.inventario.product.dto.kafka.KafkaAdminDtos.ConsumerGroupResumen;
import com.inventario.product.dto.kafka.KafkaAdminDtos.CrearTopicoRequest;
import com.inventario.product.dto.kafka.KafkaAdminDtos.ReiniciarOffsetsRequest;
import com.inventario.product.dto.kafka.KafkaAdminDtos.TopicoDetalle;
import com.inventario.product.dto.kafka.KafkaAdminDtos.TopicoResumen;
import com.inventario.product.service.KafkaAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Administracion de la plataforma Kafka: topicos, configuraciones, particiones y consumer groups. */
@RestController
@RequestMapping("/api/v1/admin/kafka")
public class KafkaAdminController {

    private final KafkaAdminService service;

    public KafkaAdminController(KafkaAdminService service) {
        this.service = service;
    }

    @GetMapping("/topics")
    public ResponseEntity<List<TopicoResumen>> listarTopicos() {
        return ResponseEntity.ok(service.listarTopicos());
    }

    @GetMapping("/topics/{nombre}")
    public ResponseEntity<TopicoDetalle> describirTopico(@PathVariable String nombre) {
        return ResponseEntity.ok(service.describirTopico(nombre));
    }

    @PostMapping("/topics")
    public ResponseEntity<TopicoResumen> crearTopico(@Valid @RequestBody CrearTopicoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crearTopico(request));
    }

    @PutMapping("/topics/{nombre}/config")
    public ResponseEntity<TopicoDetalle> actualizarConfiguracion(@PathVariable String nombre,
                                                                 @Valid @RequestBody ActualizarConfigRequest request) {
        return ResponseEntity.ok(service.actualizarConfiguracion(nombre, request.configuraciones()));
    }

    @PutMapping("/topics/{nombre}/partitions")
    public ResponseEntity<TopicoDetalle> aumentarParticiones(@PathVariable String nombre,
                                                             @Valid @RequestBody AumentarParticionesRequest request) {
        return ResponseEntity.ok(service.aumentarParticiones(nombre, request.total()));
    }

    @DeleteMapping("/topics/{nombre}")
    public ResponseEntity<Void> eliminarTopico(@PathVariable String nombre) {
        service.eliminarTopico(nombre);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/consumer-groups")
    public ResponseEntity<List<ConsumerGroupResumen>> listarConsumerGroups() {
        return ResponseEntity.ok(service.listarConsumerGroups());
    }

    @GetMapping("/consumer-groups/{groupId}")
    public ResponseEntity<ConsumerGroupDetalle> describirConsumerGroup(@PathVariable String groupId) {
        return ResponseEntity.ok(service.describirConsumerGroup(groupId));
    }

    @PostMapping("/consumer-groups/{groupId}/reset-offsets")
    public ResponseEntity<ConsumerGroupDetalle> reiniciarOffsets(@PathVariable String groupId,
                                                                 @Valid @RequestBody ReiniciarOffsetsRequest request) {
        return ResponseEntity.ok(service.reiniciarOffsets(groupId, request.topico(), request.posicion()));
    }

    @DeleteMapping("/consumer-groups/{groupId}")
    public ResponseEntity<Void> eliminarConsumerGroup(@PathVariable String groupId) {
        service.eliminarConsumerGroup(groupId);
        return ResponseEntity.noContent().build();
    }
}
