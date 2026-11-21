package com.inventario.report.controller;

import com.inventario.report.dto.EstadisticasTiempoRealResponse;
import com.inventario.report.service.EstadisticasTiempoRealService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StreamingReportControllerTest {

    @Test
    void estadisticas_retornaLaVistaEnVivoDelServicio() throws Exception {
        EstadisticasTiempoRealService service = mock(EstadisticasTiempoRealService.class);
        when(service.obtener()).thenReturn(new EstadisticasTiempoRealResponse(4, 1, 3, 1, 30, 2, null,
                List.of(), List.of()));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new StreamingReportController(service)).build();

        mockMvc.perform(get("/api/reportes/streaming/estadisticas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventosProcesados").value(4))
                .andExpect(jsonPath("$.duplicadosDescartados").value(1))
                .andExpect(jsonPath("$.unidadesIngresadas").value(30));
    }
}
