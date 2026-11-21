package com.inventario.product.controller;

import com.inventario.product.event.AlertaStockEvent;
import com.inventario.product.service.AlertaStockEstadoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/productos/alertas-stock")
public class AlertaStockController {

    private final AlertaStockEstadoService estadoService;

    public AlertaStockController(AlertaStockEstadoService estadoService) {
        this.estadoService = estadoService;
    }

    /** Productos en estado BAJO o AGOTADO segun el ultimo evento del stream, ordenados por stock. */
    @GetMapping
    public ResponseEntity<List<AlertaStockEvent>> vigentes() {
        return ResponseEntity.ok(estadoService.listarVigentes());
    }
}
