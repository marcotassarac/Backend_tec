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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryClientTest {

    @Mock
    private RestTemplate restTemplate;

    private CategoryClient buildClient() {
        CategoryClient client = new CategoryClient(restTemplate);
        ReflectionTestUtils.setField(client, "categoryServiceUrl", "http://localhost:8082");
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
}
