package com.inventario.report.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MovimientoDto {
    private Long id;
    private Long productoId;
    private String productoCodigo;
    private String productoNombre;
    private String tipoMovimiento;
    private Integer cantidad;
    private String motivo;
    private String usuario;
    private Integer stockResultante;
    private String fecha;
}
