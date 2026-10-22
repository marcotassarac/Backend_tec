package com.inventario.report.client;

import com.inventario.report.dto.ProductoDto;
import com.inventario.report.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceClientTest {

    @Mock
    private RestTemplate restTemplate;

    private ProductServiceClient client;

    private final String bearerToken = "Bearer token-de-prueba";
    private final String baseUrl = "http://localhost:8083";

    @BeforeEach
    void setUp() {
        client = new ProductServiceClient(restTemplate);
        ReflectionTestUtils.setField(client, "productServiceUrl", baseUrl);
    }

    @Test
    void listarProductos_conRespuestaValida_debeRetornarLaLista() {
        ProductoDto[] body = new ProductoDto[]{
                new ProductoDto(1L, "PROD-001", "Notebook", null, 1L, "Tecnologia",
                        BigDecimal.TEN, 5, 1, false, true)
        };
        when(restTemplate.exchange(eq(baseUrl + "/api/productos"), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(ProductoDto[].class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

        assertThat(client.listarProductos(bearerToken)).hasSize(1);
    }

    @Test
    void listarProductos_conCuerpoNulo_debeRetornarListaVacia() {
        when(restTemplate.exchange(eq(baseUrl + "/api/productos"), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(ProductoDto[].class)))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

        assertThat(client.listarProductos(bearerToken)).isEmpty();
    }

    @Test
    void obtenerProducto_existente_debeRetornarlo() {
        ProductoDto producto = new ProductoDto(1L, "PROD-001", "Notebook", null, 1L, "Tecnologia",
                BigDecimal.TEN, 5, 1, false, true);
        when(restTemplate.exchange(eq(baseUrl + "/api/productos/1"), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(ProductoDto.class)))
                .thenReturn(new ResponseEntity<>(producto, HttpStatus.OK));

        assertThat(client.obtenerProducto(1L, bearerToken).getCodigo()).isEqualTo("PROD-001");
    }

    @Test
    void obtenerProducto_inexistente_debeLanzarResourceNotFoundException() {
        when(restTemplate.exchange(eq(baseUrl + "/api/productos/999"), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(ProductoDto.class)))
                .thenThrow(HttpClientErrorException.NotFound.create(
                        HttpStatus.NOT_FOUND, "Not Found", null, null, null));

        assertThatThrownBy(() -> client.obtenerProducto(999L, bearerToken))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }
}
