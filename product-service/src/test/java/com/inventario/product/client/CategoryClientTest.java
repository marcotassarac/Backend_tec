package com.inventario.product.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryClientTest {

    @Mock
    private RestTemplate restTemplate;

    private CategoryClient buildClient() {
        CategoryClient client = new CategoryClient(restTemplate);
        ReflectionTestUtils.setField(client, "categoryServiceUrl", "http://localhost:8082");
        ReflectionTestUtils.setField(client, "cacheTtlMs", 30000L);
        return client;
    }

    @Test
    void obtenerNombreCategoria_conRespuestaValida_debeRetornarElNombre() {
        CategoryClient client = buildClient();
        Map<String, Object> body = Map.of("id", 5, "nombre", "Perifericos");
        when(restTemplate.exchange(eq("http://localhost:8082/api/categorias/5"), eq(HttpMethod.GET),
                any(), eq(Map.class))).thenReturn(ResponseEntity.ok(body));

        String nombre = client.obtenerNombreCategoria(5L, "Bearer token-x");

        assertThat(nombre).isEqualTo("Perifericos");
    }

    @Test
    void obtenerNombreCategoria_conRespuestaVacia_debeRetornarNull() {
        CategoryClient client = buildClient();
        when(restTemplate.exchange(eq("http://localhost:8082/api/categorias/5"), eq(HttpMethod.GET),
                any(), eq(Map.class))).thenReturn(ResponseEntity.ok(null));

        String nombre = client.obtenerNombreCategoria(5L, "Bearer token-x");

        assertThat(nombre).isNull();
    }

    @Test
    void obtenerNombreCategoria_conServicioCaido_debeRetornarNullSinPropagarLaExcepcion() {
        CategoryClient client = buildClient();
        when(restTemplate.exchange(eq("http://localhost:8082/api/categorias/5"), eq(HttpMethod.GET),
                any(), eq(Map.class))).thenThrow(new RestClientException("category-service no disponible"));

        String nombre = client.obtenerNombreCategoria(5L, "Bearer token-x");

        assertThat(nombre).isNull();
    }

    // ---- EST-01: listado completo de categorias en una sola llamada, con cache ----

    private void responderListado(Object cuerpo) {
        when(restTemplate.exchange(eq("http://localhost:8082/api/categorias"), eq(HttpMethod.GET),
                any(), eq(List.class))).thenReturn(ResponseEntity.ok((List) cuerpo));
    }

    @Test
    void obtenerNombresCategorias_conRespuestaValida_debeRetornarElMapaIdNombre() {
        CategoryClient client = buildClient();
        responderListado(List.of(Map.of("id", 5, "nombre", "Perifericos"), Map.of("id", 7, "nombre", "Audio")));

        Map<Long, String> nombres = client.obtenerNombresCategorias("Bearer token-x");

        assertThat(nombres).containsEntry(5L, "Perifericos").containsEntry(7L, "Audio").hasSize(2);
    }

    @Test
    void obtenerNombresCategorias_dosVecesSeguidas_debeLlamarAlServicioUnaSolaVez() {
        CategoryClient client = buildClient();
        responderListado(List.of(Map.of("id", 5, "nombre", "Perifericos")));

        client.obtenerNombresCategorias("Bearer token-x");
        Map<Long, String> segunda = client.obtenerNombresCategorias("Bearer token-x");

        assertThat(segunda).containsEntry(5L, "Perifericos");
        verify(restTemplate, times(1)).exchange(eq("http://localhost:8082/api/categorias"), eq(HttpMethod.GET),
                any(), eq(List.class));
    }

    @Test
    void obtenerNombresCategorias_conCacheVencido_debeVolverAConsultar() {
        CategoryClient client = buildClient();
        ReflectionTestUtils.setField(client, "cacheTtlMs", -1L);
        responderListado(List.of(Map.of("id", 5, "nombre", "Perifericos")));

        client.obtenerNombresCategorias("Bearer token-x");
        client.obtenerNombresCategorias("Bearer token-x");

        verify(restTemplate, times(2)).exchange(eq("http://localhost:8082/api/categorias"), eq(HttpMethod.GET),
                any(), eq(List.class));
    }

    @Test
    void obtenerNombresCategorias_conRespuestaVacia_debeRetornarMapaVacio() {
        CategoryClient client = buildClient();
        responderListado(null);

        assertThat(client.obtenerNombresCategorias("Bearer token-x")).isEmpty();
    }

    @Test
    void obtenerNombresCategorias_conServicioCaido_debeRetornarMapaVacioSinPropagarLaExcepcion() {
        CategoryClient client = buildClient();
        when(restTemplate.exchange(eq("http://localhost:8082/api/categorias"), eq(HttpMethod.GET),
                any(), eq(List.class))).thenThrow(new RestClientException("category-service no disponible"));

        assertThat(client.obtenerNombresCategorias("Bearer token-x")).isEmpty();
    }
}
