package com.inventario.category.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventario.category.dto.CategoriaRequest;
import com.inventario.category.dto.CategoriaResponse;
import com.inventario.category.exception.BusinessException;
import com.inventario.category.exception.ResourceNotFoundException;
import com.inventario.category.service.CategoriaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Pruebas del controller con MockMvc, filtros de seguridad deshabilitados: la
 * autenticacion/autorizacion real se prueba por separado contra SecurityConfig.
 */
@WebMvcTest(CategoriaController.class)
@AutoConfigureMockMvc(addFilters = false)
class CategoriaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CategoriaService categoriaService;

    @Test
    void listar_debeRetornar200ConElListado() throws Exception {
        CategoriaResponse response = CategoriaResponse.builder().id(1L).nombre("Electronica").activo(true).build();
        when(categoriaService.listar()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/categorias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Electronica"));
    }

    @Test
    void obtenerPorId_conIdExistente_debeRetornar200() throws Exception {
        CategoriaResponse response = CategoriaResponse.builder().id(1L).nombre("Electronica").activo(true).build();
        when(categoriaService.obtenerPorId(1L)).thenReturn(response);

        mockMvc.perform(get("/api/categorias/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void obtenerPorId_conIdInexistente_debeRetornar404() throws Exception {
        when(categoriaService.obtenerPorId(99L)).thenThrow(new ResourceNotFoundException("Categoria no encontrada con id 99"));

        mockMvc.perform(get("/api/categorias/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Categoria no encontrada con id 99"));
    }

    @Test
    void crear_conDatosValidos_debeRetornar201() throws Exception {
        CategoriaRequest request = new CategoriaRequest("Hogar", "Articulos de hogar", true);
        CategoriaResponse response = CategoriaResponse.builder().id(2L).nombre("Hogar").activo(true).build();
        when(categoriaService.crear(any(CategoriaRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/categorias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2));
    }

    @Test
    void crear_conNombreEnBlanco_debeRetornar400PorValidacion() throws Exception {
        CategoriaRequest request = new CategoriaRequest("", null, true);

        mockMvc.perform(post("/api/categorias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("nombre")));
    }

    @Test
    void crear_conNombreDuplicado_debeRetornar400PorBusinessException() throws Exception {
        CategoriaRequest request = new CategoriaRequest("Electronica", null, true);
        when(categoriaService.crear(any(CategoriaRequest.class)))
                .thenThrow(new BusinessException("Ya existe una categoria con ese nombre"));

        mockMvc.perform(post("/api/categorias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ya existe una categoria con ese nombre"));
    }

    @Test
    void actualizar_conDatosValidos_debeRetornar200() throws Exception {
        CategoriaRequest request = new CategoriaRequest("Nuevo nombre", null, true);
        CategoriaResponse response = CategoriaResponse.builder().id(1L).nombre("Nuevo nombre").activo(true).build();
        when(categoriaService.actualizar(anyLong(), any(CategoriaRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/categorias/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Nuevo nombre"));
    }

    @Test
    void eliminar_conIdExistente_debeRetornar204() throws Exception {
        doNothing().when(categoriaService).eliminar(1L);

        mockMvc.perform(delete("/api/categorias/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void cualquierEndpoint_cuandoElServicioLanzaExcepcionGenerica_debeRetornar500() throws Exception {
        when(categoriaService.listar()).thenThrow(new RuntimeException("fallo inesperado de base de datos"));

        mockMvc.perform(get("/api/categorias"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("fallo inesperado de base de datos"));
    }
}
