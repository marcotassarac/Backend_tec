package com.inventario.product.controller;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RabbitAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
class RabbitAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AmqpAdmin amqpAdmin;

    @Test
    void crearCola_debeRetornar201ConElNombreDeLaCola() throws Exception {
        when(amqpAdmin.declareQueue(any())).thenReturn("cola.prueba");

        mockMvc.perform(post("/api/v1/admin/rabbitmq/queues/cola.prueba"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.queue").value("cola.prueba"))
                .andExpect(jsonPath("$.status").value("creada"));
    }

    @Test
    void eliminarCola_debeRetornar204() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/rabbitmq/queues/cola.prueba"))
                .andExpect(status().isNoContent());

        verify(amqpAdmin).deleteQueue("cola.prueba");
    }
}
