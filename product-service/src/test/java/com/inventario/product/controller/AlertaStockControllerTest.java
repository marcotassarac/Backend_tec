package com.inventario.product.controller;

import com.inventario.product.event.AlertaStockEvent;
import com.inventario.product.service.AlertaStockEstadoService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AlertaStockControllerTest {

    @Test
    void vigentes_retornaLasAlertasDelEstado() throws Exception {
        AlertaStockEstadoService estado = mock(AlertaStockEstadoService.class);
        when(estado.listarVigentes()).thenReturn(List.of(
                new AlertaStockEvent("a", "AGOTADO", 2L, "PROD-2", "Mouse", 0, 5, "evt", null)));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AlertaStockController(estado)).build();

        mockMvc.perform(get("/api/productos/alertas-stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nivel").value("AGOTADO"))
                .andExpect(jsonPath("$[0].productoId").value(2));
    }
}
