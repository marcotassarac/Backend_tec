package com.inventario.product.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventario.product.dto.AjusteStockRequest;
import com.inventario.product.dto.ProductoRequest;
import com.inventario.product.dto.ProductoResponse;
import com.inventario.product.service.ProductoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Pruebas de la capa web de ProductoController con MockMvc, aislando el servicio
 * (mockeado) y desactivando los filtros de seguridad: el flujo de autenticacion real
 * se prueba aparte en SecurityIntegrationTest.
 */
@WebMvcTest(controllers = ProductoController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProductoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductoService productoService;

    private ProductoResponse respuestaDeEjemplo() {
        return ProductoResponse.builder()
                .id(1L).codigo("PROD-100").nombre("Teclado")
                .precio(BigDecimal.TEN).stock(10).stockMinimo(2).stockBajo(false).activo(true)
                .build();
    }

    @Test
    void listar_debeRetornar200ConElListado() throws Exception {
        when(productoService.listar(any())).thenReturn(List.of(respuestaDeEjemplo()));

        mockMvc.perform(get("/api/productos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigo").value("PROD-100"));
    }

    @Test
    void obtenerPorId_debeRetornar200ConElProducto() throws Exception {
        when(productoService.obtenerPorId(anyLong(), any())).thenReturn(respuestaDeEjemplo());

        mockMvc.perform(get("/api/productos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void crear_conDatosValidos_debeRetornar201() throws Exception {
        when(productoService.crear(any())).thenReturn(respuestaDeEjemplo());
        ProductoRequest request = new ProductoRequest("PROD-100", "Teclado", "desc", 1L,
                BigDecimal.valueOf(25000), 10, 2, true);

        mockMvc.perform(post("/api/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value("PROD-100"));
    }

    @Test
    void crear_conDatosInvalidos_debeRetornar400() throws Exception {
        ProductoRequest request = new ProductoRequest("", "", null, null, null, null, null, null);

        mockMvc.perform(post("/api/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void actualizar_conDatosValidos_debeRetornar200() throws Exception {
        when(productoService.actualizar(anyLong(), any())).thenReturn(respuestaDeEjemplo());
        ProductoRequest request = new ProductoRequest("PROD-100", "Teclado", "desc", 1L,
                BigDecimal.valueOf(25000), 10, 2, true);

        mockMvc.perform(put("/api/productos/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void categoriaTieneProductos_debeResponderExiste() throws Exception {
        when(productoService.categoriaTieneProductos(5L)).thenReturn(true);

        mockMvc.perform(get("/api/productos/categoria/5/existe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.existe").value(true));
    }

    @Test
    void eliminar_debeRetornar204() throws Exception {
        mockMvc.perform(delete("/api/productos/1"))
                .andExpect(status().isNoContent());

        verify(productoService).eliminar(1L);
    }

    @Test
    void ajustarStock_conDatosValidos_debeRetornar200() throws Exception {
        when(productoService.ajustarStock(anyLong(), any())).thenReturn(respuestaDeEjemplo());
        AjusteStockRequest request = new AjusteStockRequest("ENTRADA", 5);

        mockMvc.perform(post("/api/productos/1/ajustar-stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }
}
