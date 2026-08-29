package com.inventario.report.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductoDto {
    private Long id;
    private String codigo;
    private String nombre;
    private String descripcion;
    private Long categoriaId;
    private String categoriaNombre;
    private BigDecimal precio;
    private Integer stock;
    private Integer stockMinimo;
    private boolean stockBajo;
    private boolean activo;
}
