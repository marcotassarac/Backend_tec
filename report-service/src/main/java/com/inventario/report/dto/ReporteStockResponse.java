package com.inventario.report.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReporteStockResponse {
    private List<ReporteStockItem> items;
    private long totalProductos;
    private long productosBajoStock;
    private BigDecimal valorTotalInventario;
}
