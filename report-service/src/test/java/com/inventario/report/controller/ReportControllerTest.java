package com.inventario.report.controller;

import com.inventario.report.dto.KardexResponse;
import com.inventario.report.dto.ReporteStockResponse;
import com.inventario.report.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de controller con MockMvc standalone (sin levantar el contexto de seguridad):
 * aisla la capa HTTP del resto del sistema, igual que las demas pruebas unitarias del proyecto.
 */
class ReportControllerTest {

    private MockMvc mockMvc;
    private ReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = Mockito.mock(ReportService.class);
        ReportController controller = new ReportController(reportService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void stockActual_debeRetornar200ConElReporteDelService() throws Exception {
        when(reportService.reporteStockActual(anyString()))
                .thenReturn(ReporteStockResponse.builder().totalProductos(0).build());

        mockMvc.perform(get("/api/reportes/stock-actual")
                        .header("Authorization", "Bearer token-de-prueba"))
                .andExpect(status().isOk());
    }

    @Test
    void kardex_debeRetornar200ConElKardexDelService() throws Exception {
        when(reportService.kardexPorProducto(anyLong(), anyString()))
                .thenReturn(KardexResponse.builder().build());

        mockMvc.perform(get("/api/reportes/kardex/1")
                        .header("Authorization", "Bearer token-de-prueba"))
                .andExpect(status().isOk());
    }
}
