package com.inventario.product.controller;

import com.inventario.product.dto.AjusteStockRequest;
import com.inventario.product.dto.ProductoRequest;
import com.inventario.product.dto.ProductoResponse;
import com.inventario.product.service.ProductoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/productos")
public class ProductoController {

    private final ProductoService productoService;

    public ProductoController(ProductoService productoService) {
        this.productoService = productoService;
    }

    @GetMapping
    public ResponseEntity<List<ProductoResponse>> listar(HttpServletRequest request) {
        return ResponseEntity.ok(productoService.listar(request.getHeader("Authorization")));
    }

    /** Usado por category-service para no eliminar una categoria que todavia tiene productos. */
    @GetMapping("/categoria/{categoriaId}/existe")
    public ResponseEntity<Map<String, Boolean>> categoriaTieneProductos(@PathVariable Long categoriaId) {
        return ResponseEntity.ok(Map.of("existe", productoService.categoriaTieneProductos(categoriaId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductoResponse> obtenerPorId(@PathVariable Long id, HttpServletRequest request) {
        return ResponseEntity.ok(productoService.obtenerPorId(id, request.getHeader("Authorization")));
    }

    @PostMapping
    public ResponseEntity<ProductoResponse> crear(@Valid @RequestBody ProductoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productoService.crear(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductoResponse> actualizar(@PathVariable Long id, @Valid @RequestBody ProductoRequest request) {
        return ResponseEntity.ok(productoService.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        productoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    /** Endpoint interno invocado por inventory-service al registrar un movimiento de stock. */
    @PostMapping("/{id}/ajustar-stock")
    public ResponseEntity<ProductoResponse> ajustarStock(@PathVariable Long id, @Valid @RequestBody AjusteStockRequest request) {
        return ResponseEntity.ok(productoService.ajustarStock(id, request));
    }
}
