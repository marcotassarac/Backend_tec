package com.inventario.report.controller;

import com.inventario.report.dto.NotificacionItem;
import com.inventario.report.service.BuzonMovimientosService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Solo el rol ADMIN puede consultarlo (ver SecurityConfig). */
@RestController
public class NotificacionController {

    private final BuzonMovimientosService buzon;

    public NotificacionController(BuzonMovimientosService buzon) {
        this.buzon = buzon;
    }

    @GetMapping("/api/notificaciones")
    public ResponseEntity<List<NotificacionItem>> notificaciones() {
        return ResponseEntity.ok(buzon.notificacionesAdmin());
    }

    @GetMapping("/api/auditoria")
    public ResponseEntity<List<NotificacionItem>> auditoria() {
        return ResponseEntity.ok(buzon.auditoria());
    }
}
