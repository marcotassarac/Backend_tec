package com.inventario.report.service;

import com.inventario.report.client.InventoryServiceClient;
import com.inventario.report.client.ProductServiceClient;
import com.inventario.report.dto.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Reporte/consulta agregada. No mantiene datos propios: consulta en vivo
 * product-service (stock actual) e inventory-service (kardex de movimientos).
 */
@Service
public class ReportService {

    private final ProductServiceClient productServiceClient;
    private final InventoryServiceClient inventoryServiceClient;

    public ReportService(ProductServiceClient productServiceClient, InventoryServiceClient inventoryServiceClient) {
        this.productServiceClient = productServiceClient;
        this.inventoryServiceClient = inventoryServiceClient;
    }

    public ReporteStockResponse reporteStockActual(String bearerToken) {
        List<ProductoDto> productos = productServiceClient.listarProductos(bearerToken);

        List<ReporteStockItem> items = productos.stream()
                .map(p -> ReporteStockItem.builder()
                        .productoId(p.getId())
                        .codigo(p.getCodigo())
                        .nombre(p.getNombre())
                        .categoriaNombre(p.getCategoriaNombre())
                        .precio(p.getPrecio())
                        .stock(p.getStock())
                        .stockMinimo(p.getStockMinimo())
                        .stockBajo(p.isStockBajo())
                        .valorTotal(p.getPrecio().multiply(BigDecimal.valueOf(p.getStock())))
                        .build())
                .toList();

        BigDecimal valorTotalInventario = items.stream()
                .map(ReporteStockItem::getValorTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long productosBajoStock = items.stream().filter(ReporteStockItem::isStockBajo).count();

        return ReporteStockResponse.builder()
                .items(items)
                .totalProductos(items.size())
                .productosBajoStock(productosBajoStock)
                .valorTotalInventario(valorTotalInventario)
                .build();
    }

    public KardexResponse kardexPorProducto(Long productoId, String bearerToken) {
        ProductoDto producto = productServiceClient.obtenerProducto(productoId, bearerToken);
        List<MovimientoDto> movimientos = inventoryServiceClient.listarMovimientos(productoId, bearerToken);
        return KardexResponse.builder()
                .producto(producto)
                .movimientos(movimientos)
                .build();
    }
}
