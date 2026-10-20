package com.inventario.report.service;

import com.inventario.report.client.InventoryServiceClient;
import com.inventario.report.client.ProductServiceClient;
import com.inventario.report.dto.KardexResponse;
import com.inventario.report.dto.MovimientoDto;
import com.inventario.report.dto.ProductoDto;
import com.inventario.report.dto.ReporteStockResponse;
import com.inventario.report.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ProductServiceClient productServiceClient;

    @Mock
    private InventoryServiceClient inventoryServiceClient;

    @InjectMocks
    private ReportService reportService;

    private final String bearerToken = "Bearer token-de-prueba";

    @Test
    void reporteStockActual_debeCalcularValorTotalYStockBajoCorrectamente() {
        ProductoDto p1 = new ProductoDto(1L, "PROD-001", "Notebook", null, 1L, "Tecnologia",
                BigDecimal.valueOf(500000), 10, 2, false, true);
        ProductoDto p2 = new ProductoDto(2L, "PROD-002", "Mouse", null, 1L, "Tecnologia",
                BigDecimal.valueOf(5000), 1, 5, true, true);
        when(productServiceClient.listarProductos(bearerToken)).thenReturn(List.of(p1, p2));

        ReporteStockResponse response = reportService.reporteStockActual(bearerToken);

        assertThat(response.getTotalProductos()).isEqualTo(2);
        assertThat(response.getProductosBajoStock()).isEqualTo(1);
        // 10 * 500000 + 1 * 5000 = 5.005.000
        assertThat(response.getValorTotalInventario()).isEqualByComparingTo(BigDecimal.valueOf(5005000));
    }

    @Test
    void reporteStockActual_sinProductos_debeRetornarTotalesEnCero() {
        when(productServiceClient.listarProductos(bearerToken)).thenReturn(List.of());

        ReporteStockResponse response = reportService.reporteStockActual(bearerToken);

        assertThat(response.getTotalProductos()).isZero();
        assertThat(response.getProductosBajoStock()).isZero();
        assertThat(response.getValorTotalInventario()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void kardexPorProducto_debeCombinarProductoYMovimientos() {
        ProductoDto producto = new ProductoDto(1L, "PROD-001", "Notebook", null, 1L, "Tecnologia",
                BigDecimal.valueOf(500000), 8, 2, false, true);
        MovimientoDto movimiento = new MovimientoDto(1L, 1L, "PROD-001", "Notebook", "SALIDA", 2,
                "Venta", "operador", 8, "2026-10-20T10:00:00");
        when(productServiceClient.obtenerProducto(1L, bearerToken)).thenReturn(producto);
        when(inventoryServiceClient.listarMovimientos(1L, bearerToken)).thenReturn(List.of(movimiento));

        KardexResponse response = reportService.kardexPorProducto(1L, bearerToken);

        assertThat(response.getProducto().getCodigo()).isEqualTo("PROD-001");
        assertThat(response.getMovimientos()).hasSize(1);
        assertThat(response.getMovimientos().get(0).getTipoMovimiento()).isEqualTo("SALIDA");
    }

    @Test
    void kardexPorProducto_conProductoInexistente_debePropagarResourceNotFoundException() {
        when(productServiceClient.obtenerProducto(999L, bearerToken))
                .thenThrow(new ResourceNotFoundException("Producto no encontrado con id 999"));

        assertThatThrownBy(() -> reportService.kardexPorProducto(999L, bearerToken))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
