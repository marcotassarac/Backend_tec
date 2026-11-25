package com.inventario.report.controller;

import com.inventario.report.dto.NotificacionItem;
import com.inventario.report.service.BuzonMovimientosService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificacionControllerTest {

    @Test
    void notificacionesYAuditoria_devuelvenLoQueTieneElBuzon() throws Exception {
        BuzonMovimientosService buzon = new BuzonMovimientosService();
        buzon.agregarNotificacionAdmin(new NotificacionItem("operador", "OPERADOR", "SALIDA", "PROD-001",
                "Notebook", 1, 9, "2026-11-17T10:00", LocalDateTime.now()));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new NotificacionController(buzon)).build();

        mockMvc.perform(get("/api/notificaciones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productoCodigo").value("PROD-001"));
        mockMvc.perform(get("/api/auditoria"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
