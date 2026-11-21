package com.inventario.product.controller;

import com.inventario.product.dto.kafka.KafkaAdminDtos.ConsumerGroupDetalle;
import com.inventario.product.dto.kafka.KafkaAdminDtos.ConsumerGroupResumen;
import com.inventario.product.dto.kafka.KafkaAdminDtos.CrearTopicoRequest;
import com.inventario.product.dto.kafka.KafkaAdminDtos.TopicoDetalle;
import com.inventario.product.dto.kafka.KafkaAdminDtos.TopicoResumen;
import com.inventario.product.exception.GlobalExceptionHandler;
import com.inventario.product.exception.KafkaAdminException;
import com.inventario.product.service.KafkaAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class KafkaAdminControllerTest {

    private static final String BASE = "/api/v1/admin/kafka";

    private KafkaAdminService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(KafkaAdminService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new KafkaAdminController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private final TopicoDetalle detalle = new TopicoDetalle("t", List.of(), Map.of("retention.ms", "1000"));

    @Test
    void listarTopicos_retorna200() throws Exception {
        when(service.listarTopicos()).thenReturn(List.of(new TopicoResumen("inventario.movimientos", 3, 3)));

        mockMvc.perform(get(BASE + "/topics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].particiones").value(3))
                .andExpect(jsonPath("$[0].replicas").value(3));
    }

    @Test
    void describirTopico_retorna200() throws Exception {
        when(service.describirTopico("t")).thenReturn(detalle);

        mockMvc.perform(get(BASE + "/topics/t"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configuraciones['retention.ms']").value("1000"));
    }

    @Test
    void crearTopico_valido_retorna201() throws Exception {
        when(service.crearTopico(any(CrearTopicoRequest.class))).thenReturn(new TopicoResumen("nuevo", 3, 3));

        mockMvc.perform(post(BASE + "/topics").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"nuevo\",\"particiones\":3,\"replicas\":3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("nuevo"));
    }

    @Test
    void crearTopico_conNombreInvalido_retorna400() throws Exception {
        mockMvc.perform(post(BASE + "/topics").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"con espacios\",\"particiones\":3,\"replicas\":3}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crearTopico_existente_retorna409() throws Exception {
        when(service.crearTopico(any(CrearTopicoRequest.class)))
                .thenThrow(new KafkaAdminException(HttpStatus.CONFLICT, "ya existe", null));

        mockMvc.perform(post(BASE + "/topics").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"dup\",\"particiones\":1,\"replicas\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("ya existe"));
    }

    @Test
    void actualizarConfiguracion_retorna200() throws Exception {
        when(service.actualizarConfiguracion("t", Map.of("retention.ms", "1000"))).thenReturn(detalle);

        mockMvc.perform(put(BASE + "/topics/t/config").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"configuraciones\":{\"retention.ms\":\"1000\"}}"))
                .andExpect(status().isOk());
    }

    @Test
    void aumentarParticiones_retorna200() throws Exception {
        when(service.aumentarParticiones("t", 6)).thenReturn(detalle);

        mockMvc.perform(put(BASE + "/topics/t/partitions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"total\":6}"))
                .andExpect(status().isOk());
    }

    @Test
    void eliminarTopico_retorna204() throws Exception {
        mockMvc.perform(delete(BASE + "/topics/t")).andExpect(status().isNoContent());
        verify(service).eliminarTopico("t");
    }

    @Test
    void consumerGroups_listarYDescribir() throws Exception {
        when(service.listarConsumerGroups()).thenReturn(List.of(new ConsumerGroupResumen("g", "STABLE")));
        when(service.describirConsumerGroup("g"))
                .thenReturn(new ConsumerGroupDetalle("g", "STABLE", List.of(), List.of(), 7));

        mockMvc.perform(get(BASE + "/consumer-groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estado").value("STABLE"));
        mockMvc.perform(get(BASE + "/consumer-groups/g"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lagTotal").value(7));
    }

    @Test
    void reiniciarOffsets_conPosicionValida_retorna200() throws Exception {
        when(service.reiniciarOffsets(eq("g"), eq("t"), eq("earliest")))
                .thenReturn(new ConsumerGroupDetalle("g", "EMPTY", List.of(), List.of(), 5));

        mockMvc.perform(post(BASE + "/consumer-groups/g/reset-offsets").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topico\":\"t\",\"posicion\":\"earliest\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lagTotal").value(5));
    }

    @Test
    void reiniciarOffsets_conPosicionInvalida_retorna400() throws Exception {
        mockMvc.perform(post(BASE + "/consumer-groups/g/reset-offsets").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topico\":\"t\",\"posicion\":\"ayer\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void eliminarConsumerGroup_retorna204() throws Exception {
        mockMvc.perform(delete(BASE + "/consumer-groups/g")).andExpect(status().isNoContent());
        verify(service).eliminarConsumerGroup("g");
    }
}
