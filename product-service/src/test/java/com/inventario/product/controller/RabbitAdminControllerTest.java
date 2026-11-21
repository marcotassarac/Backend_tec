package com.inventario.product.controller;

import com.inventario.product.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RabbitAdminControllerTest {

    private static final String BASE = "/api/v1/admin/rabbitmq";

    private AmqpAdmin amqpAdmin;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        amqpAdmin = mock(AmqpAdmin.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new RabbitAdminController(amqpAdmin))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void crearCola_debeRetornar201ConElNombreDeLaCola() throws Exception {
        when(amqpAdmin.declareQueue(any())).thenReturn("cola.prueba");

        mockMvc.perform(post(BASE + "/queues/cola.prueba"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.queue").value("cola.prueba"))
                .andExpect(jsonPath("$.status").value("creada"));
    }

    @Test
    void eliminarCola_debeRetornar204() throws Exception {
        mockMvc.perform(delete(BASE + "/queues/cola.prueba"))
                .andExpect(status().isNoContent());

        verify(amqpAdmin).deleteQueue("cola.prueba");
    }

    @Test
    void crearExchange_porDefectoEsDirect() throws Exception {
        mockMvc.perform(post(BASE + "/exchanges/ex.prueba"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value(ExchangeTypes.DIRECT));
    }

    @Test
    void crearExchange_topicYFanout() throws Exception {
        mockMvc.perform(post(BASE + "/exchanges/ex.topic").param("tipo", "TOPIC"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value(ExchangeTypes.TOPIC));
        mockMvc.perform(post(BASE + "/exchanges/ex.fanout").param("tipo", "fanout"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value(ExchangeTypes.FANOUT));

        ArgumentCaptor<Exchange> declarados = ArgumentCaptor.forClass(Exchange.class);
        verify(amqpAdmin, times(2)).declareExchange(declarados.capture());
        assertThat(declarados.getAllValues()).allSatisfy(e -> assertThat(e.isDurable()).isTrue());
    }

    @Test
    void crearExchange_tipoInvalido_retorna400() throws Exception {
        mockMvc.perform(post(BASE + "/exchanges/ex.malo").param("tipo", "headers-raro"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(amqpAdmin);
    }

    @Test
    void eliminarExchange_retorna204() throws Exception {
        mockMvc.perform(delete(BASE + "/exchanges/ex.prueba")).andExpect(status().isNoContent());
        verify(amqpAdmin).deleteExchange("ex.prueba");
    }

    @Test
    void crearBinding_conRoutingKey() throws Exception {
        mockMvc.perform(post(BASE + "/bindings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cola\":\"cola.auditoria\",\"exchange\":\"pedidos.topic\",\"routingKey\":\"auditoria.#\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.routingKey").value("auditoria.#"));

        ArgumentCaptor<Binding> binding = ArgumentCaptor.forClass(Binding.class);
        verify(amqpAdmin).declareBinding(binding.capture());
        assertThat(binding.getValue().getDestination()).isEqualTo("cola.auditoria");
        assertThat(binding.getValue().getExchange()).isEqualTo("pedidos.topic");
        assertThat(binding.getValue().getRoutingKey()).isEqualTo("auditoria.#");
    }

    @Test
    void crearBinding_sinRoutingKey_usaCadenaVacia() throws Exception {
        mockMvc.perform(post(BASE + "/bindings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cola\":\"cola.x\",\"exchange\":\"ex.fanout\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.routingKey").value(""));
    }

    @Test
    void crearBinding_sinCola_retorna400() throws Exception {
        mockMvc.perform(post(BASE + "/bindings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exchange\":\"ex\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void eliminarBinding_retorna204() throws Exception {
        mockMvc.perform(delete(BASE + "/bindings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cola\":\"cola.x\",\"exchange\":\"ex\",\"routingKey\":\"k\"}"))
                .andExpect(status().isNoContent());
        verify(amqpAdmin).removeBinding(any(Binding.class));
    }
}
