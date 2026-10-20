package com.inventario.inventory.service;

import com.inventario.inventory.client.ProductClient;
import com.inventario.inventory.dto.MovimientoRequest;
import com.inventario.inventory.dto.MovimientoResponse;
import com.inventario.inventory.dto.ProductoDto;
import com.inventario.inventory.entity.MovimientoInventario;
import com.inventario.inventory.exception.BusinessException;
import com.inventario.inventory.exception.ResourceNotFoundException;
import com.inventario.inventory.repository.MovimientoInventarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias de InventoryService: se aisla la clase mockeando sus dos dependencias
 * (ProductClient y MovimientoInventarioRepository), para probar solo la logica de negocio
 * de registrarMovimiento sin depender de una base de datos real ni de product-service.
 */
@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private MovimientoInventarioRepository movimientoRepository;

    @Mock
    private ProductClient productClient;

    @InjectMocks
    private InventoryService inventoryService;

    private final String bearerToken = "Bearer token-de-prueba";

    @BeforeEach
    void setUp() {
    }

    @Test
    void registrarMovimiento_conDatosValidos_debeGuardarYRetornarElMovimiento() {
        MovimientoRequest request = new MovimientoRequest(1L, "ENTRADA", 5, "Reposicion de stock");
        ProductoDto producto = new ProductoDto(1L, "PROD-001", "Notebook", BigDecimal.valueOf(450000), 10, 5, true);
        ProductoDto productoActualizado = new ProductoDto(1L, "PROD-001", "Notebook", BigDecimal.valueOf(450000), 15, 5, true);

        when(productClient.obtenerProducto(1L, bearerToken)).thenReturn(producto);
        when(productClient.ajustarStock(1L, "ENTRADA", 5, bearerToken)).thenReturn(productoActualizado);
        when(movimientoRepository.save(any(MovimientoInventario.class)))
                .thenAnswer(invocation -> {
                    MovimientoInventario m = invocation.getArgument(0);
                    m.setId(100L);
                    return m;
                });

        MovimientoResponse response = inventoryService.registrarMovimiento(request, bearerToken, "operador");

        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getProductoCodigo()).isEqualTo("PROD-001");
        assertThat(response.getTipoMovimiento()).isEqualTo("ENTRADA");
        assertThat(response.getCantidad()).isEqualTo(5);
        assertThat(response.getStockResultante()).isEqualTo(15);
        assertThat(response.getUsuario()).isEqualTo("operador");
        verify(movimientoRepository).save(any(MovimientoInventario.class));
    }

    @Test
    void registrarMovimiento_conTipoDeMovimientoInvalido_debeLanzarBusinessException() {
        MovimientoRequest request = new MovimientoRequest(1L, "TRANSFERENCIA", 5, "Motivo cualquiera");

        assertThatThrownBy(() -> inventoryService.registrarMovimiento(request, bearerToken, "operador"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Tipo de movimiento invalido");

        verifyNoInteractions(productClient, movimientoRepository);
    }

    @Test
    void registrarMovimiento_conProductoInexistente_debeLanzarResourceNotFoundException() {
        MovimientoRequest request = new MovimientoRequest(99L, "SALIDA", 2, "Venta");
        when(productClient.obtenerProducto(99L, bearerToken)).thenReturn(null);

        assertThatThrownBy(() -> inventoryService.registrarMovimiento(request, bearerToken, "operador"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Producto no encontrado");
    }

    @Test
    void registrarMovimiento_conProductoInactivo_debeLanzarBusinessException() {
        MovimientoRequest request = new MovimientoRequest(2L, "SALIDA", 1, "Venta");
        ProductoDto productoInactivo = new ProductoDto(2L, "PROD-002", "Mouse", BigDecimal.TEN, 3, 1, false);
        when(productClient.obtenerProducto(2L, bearerToken)).thenReturn(productoInactivo);

        assertThatThrownBy(() -> inventoryService.registrarMovimiento(request, bearerToken, "operador"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("producto inactivo");
    }

    @Test
    void listar_sinFiltroDeProducto_debeConsultarTodosOrdenadosPorFecha() {
        when(movimientoRepository.findAllByOrderByFechaDesc()).thenReturn(java.util.List.of());

        inventoryService.listar(null);

        verify(movimientoRepository).findAllByOrderByFechaDesc();
    }
}
