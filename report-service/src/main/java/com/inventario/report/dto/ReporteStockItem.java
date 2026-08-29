package com.inventario.report.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReporteStockItem {
    private Long productoId;
    private String codigo;
    private String nombre;
    private String categoriaNombre;
    private BigDecimal precio;
    private Integer stock;
    private Integer stockMinimo;
    private boolean stockBajo;
    private BigDecimal valorTotal;
}
