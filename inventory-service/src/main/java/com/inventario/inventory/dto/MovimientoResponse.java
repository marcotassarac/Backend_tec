package com.inventario.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovimientoResponse {
    private Long id;
    private Long productoId;
    private String productoCodigo;
    private String productoNombre;
    private String tipoMovimiento;
    private Integer cantidad;
    private String motivo;
    private String usuario;
    private Integer stockResultante;
    private LocalDateTime fecha;
}
