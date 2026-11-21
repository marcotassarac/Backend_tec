package com.inventario.report.controller;

import com.inventario.report.dto.EstadisticasTiempoRealResponse;
import com.inventario.report.service.EstadisticasTiempoRealService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reportes/streaming")
public class StreamingReportController {

    private final EstadisticasTiempoRealService estadisticas;

    public StreamingReportController(EstadisticasTiempoRealService estadisticas) {
        this.estadisticas = estadisticas;
    }

    /** Indicadores que se actualizan con cada evento de inventario.movimientos, sin consultar las bases de datos. */
    @GetMapping("/estadisticas")
    public ResponseEntity<EstadisticasTiempoRealResponse> estadisticas() {
        return ResponseEntity.ok(estadisticas.obtener());
    }
}
