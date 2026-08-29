package com.inventario.report.controller;

import com.inventario.report.dto.KardexResponse;
import com.inventario.report.dto.ReporteStockResponse;
import com.inventario.report.service.ReportService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reportes")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /** Reporte por pantalla: stock actual, alerta de stock bajo y valorizacion del inventario. */
    @GetMapping("/stock-actual")
    public ResponseEntity<ReporteStockResponse> stockActual(HttpServletRequest request) {
        return ResponseEntity.ok(reportService.reporteStockActual(request.getHeader("Authorization")));
    }

    /** Consulta por pantalla: kardex (historial de movimientos) de un producto especifico. */
    @GetMapping("/kardex/{productoId}")
    public ResponseEntity<KardexResponse> kardex(@PathVariable Long productoId, HttpServletRequest request) {
        return ResponseEntity.ok(reportService.kardexPorProducto(productoId, request.getHeader("Authorization")));
    }
}
