package com.inventario.category.client;

import com.inventario.category.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductClientTest {

    private static final String URL = "http://localhost:8083/api/productos/categoria/3/existe";

    private RestTemplate restTemplate;
    private ProductClient client;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        client = new ProductClient(restTemplate);
        ReflectionTestUtils.setField(client, "productServiceUrl", "http://localhost:8083");
    }

    @Test
    void categoriaTieneProductos_cuandoProductServiceRespondeTrue() {
        when(restTemplate.exchange(eq(URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("existe", true)));

        assertThat(client.categoriaTieneProductos(3L, "Bearer t")).isTrue();
    }

    @Test
    void categoriaTieneProductos_cuandoProductServiceRespondeFalse() {
        when(restTemplate.exchange(eq(URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("existe", false)));

        assertThat(client.categoriaTieneProductos(3L, "Bearer t")).isFalse();
    }

    @Test
    void categoriaTieneProductos_conRespuestaVacia_esFalse() {
        when(restTemplate.exchange(eq(URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok().build());

        assertThat(client.categoriaTieneProductos(3L, "Bearer t")).isFalse();
    }

    @Test
    void categoriaTieneProductos_siProductServiceNoResponde_rechazaLaOperacion() {
        when(restTemplate.exchange(eq(URL), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        assertThatThrownBy(() -> client.categoriaTieneProductos(3L, "Bearer t"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No fue posible verificar");
    }
}
