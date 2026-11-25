package com.inventario.inventory.controller;

import com.inventario.inventory.messaging.MovimientoNotificador;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventario.inventory.config.SecurityConfig;
import com.inventario.inventory.dto.MovimientoRequest;
import com.inventario.inventory.dto.MovimientoResponse;
import com.inventario.inventory.exception.BusinessException;
import com.inventario.inventory.exception.ResourceNotFoundException;
import com.inventario.inventory.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de MovimientoController a nivel de capa web (MockMvc), con InventoryService
 * mockeado. Se importa la SecurityConfig real para ejercitar tambien el filtro de
 * seguridad real (JWT, CORS, manejo de 401) en vez de deshabilitarlo.
 */
@WebMvcTest(MovimientoController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "jwt.secret=ClaveSecretaSuperSeguraParaElProyectoGestorDeInventarioCambiarEnProduccion2026",
        "app.cors.allowed-origins=http://localhost:3000"
})
class MovimientoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InventoryService inventoryService;

    @MockBean
    private MovimientoNotificador notificador;

    @Test
    void registrar_conTokenValido_debeRetornar201() throws Exception {
        MovimientoRequest request = new MovimientoRequest(1L, "ENTRADA", 5, "Reposicion");
        var response = MovimientoResponse.builder()
                .id(10L)
                .productoId(1L)
                .productoCodigo("PROD-001")
                .productoNombre("Notebook")
                .tipoMovimiento("ENTRADA")
                .cantidad(5)
                .motivo("Reposicion")
                .usuario("operador")
                .stockResultante(15)
                .fecha(LocalDateTime.now())
                .build();

        when(inventoryService.registrarMovimiento(any(), any(), eq("operador"))).thenReturn(response);

        mockMvc.perform(post("/api/movimientos")
                        .with(jwt().jwt(j -> j.subject("operador")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void registrar_sinToken_debeRetornar401() throws Exception {
        MovimientoRequest request = new MovimientoRequest(1L, "ENTRADA", 5, "Reposicion");

        mockMvc.perform(post("/api/movimientos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registrar_conCuerpoInvalido_debeRetornar400PorValidacion() throws Exception {
        // productoId nulo y tipoMovimiento vacio: dispara MethodArgumentNotValidException
        String cuerpoInvalido = "{\"productoId\":null,\"tipoMovimiento\":\"\",\"cantidad\":0}";

        mockMvc.perform(post("/api/movimientos")
                        .with(jwt().jwt(j -> j.subject("operador")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoInvalido))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registrar_conProductoInexistente_debeRetornar404() throws Exception {
        MovimientoRequest request = new MovimientoRequest(99L, "SALIDA", 1, "Venta");
        when(inventoryService.registrarMovimiento(any(), any(), anyString()))
                .thenThrow(new ResourceNotFoundException("Producto no encontrado con id 99"));

        mockMvc.perform(post("/api/movimientos")
                        .with(jwt().jwt(j -> j.subject("operador")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void registrar_conTipoInvalido_debeRetornar400PorBusinessException() throws Exception {
        MovimientoRequest request = new MovimientoRequest(1L, "TRANSFERENCIA", 1, "Motivo");
        when(inventoryService.registrarMovimiento(any(), any(), anyString()))
                .thenThrow(new BusinessException("Tipo de movimiento invalido. Use ENTRADA o SALIDA"));

        mockMvc.perform(post("/api/movimientos")
                        .with(jwt().jwt(j -> j.subject("operador")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registrar_conErrorInesperado_debeRetornar500() throws Exception {
        MovimientoRequest request = new MovimientoRequest(1L, "ENTRADA", 1, "Motivo");
        when(inventoryService.registrarMovimiento(any(), any(), anyString()))
                .thenThrow(new RuntimeException("fallo inesperado"));

        mockMvc.perform(post("/api/movimientos")
                        .with(jwt().jwt(j -> j.subject("operador")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void listar_sinFiltro_debeRetornar200ConListadoCompleto() throws Exception {
        when(inventoryService.listar(isNull())).thenReturn(List.of());

        mockMvc.perform(get("/api/movimientos").with(jwt()))
                .andExpect(status().isOk());
    }

    @Test
    void listar_conFiltroDeProducto_debeRetornar200ConListadoFiltrado() throws Exception {
        when(inventoryService.listar(5L)).thenReturn(List.of());

        mockMvc.perform(get("/api/movimientos").param("productoId", "5").with(jwt()))
                .andExpect(status().isOk());
    }
}
