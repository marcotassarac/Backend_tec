package com.inventario.inventory.controller;

import com.inventario.inventory.dto.MovimientoRequest;
import com.inventario.inventory.dto.MovimientoResponse;
import com.inventario.inventory.service.InventoryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/movimientos")
public class MovimientoController {

    private final InventoryService inventoryService;

    public MovimientoController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping
    public ResponseEntity<MovimientoResponse> registrar(@Valid @RequestBody MovimientoRequest request,
                                                          HttpServletRequest httpRequest,
                                                          @AuthenticationPrincipal Jwt jwt) {
        String bearerToken = httpRequest.getHeader("Authorization");
        String usuario = jwt.getSubject();
        MovimientoResponse response = inventoryService.registrarMovimiento(request, bearerToken, usuario);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<MovimientoResponse>> listar(@RequestParam(required = false) Long productoId) {
        return ResponseEntity.ok(inventoryService.listar(productoId));
    }
}
