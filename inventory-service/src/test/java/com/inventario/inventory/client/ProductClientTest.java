package com.inventario.inventory.client;

import com.inventario.inventory.dto.ProductoDto;
import com.inventario.inventory.exception.BusinessException;
import com.inventario.inventory.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Pruebas de ProductClient: se mockea RestTemplate para simular las respuestas y errores
 * reales que puede devolver product-service, sin necesitar el servicio levantado.
 */
@ExtendWith(MockitoExtension.class)
class ProductClientTest {

    @Mock
    private RestTemplate restTemplate;

    private ProductClient productClient;

    private final String bearerToken = "Bearer token-de-prueba";

    @BeforeEach
    void setUp() {
        productClient = new ProductClient(restTemplate);
        ReflectionTestUtils.setField(productClient, "productServiceUrl", "http://localhost:8083");
    }

    @Test
    void obtenerProducto_conRespuestaExitosa_debeRetornarElProducto() {
        ProductoDto producto = new ProductoDto(1L, "PROD-001", "Notebook", BigDecimal.TEN, 10, 2, true);
        when(restTemplate.exchange(eq("http://localhost:8083/api/productos/1"), eq(HttpMethod.GET),
                any(), eq(ProductoDto.class)))
                .thenReturn(ResponseEntity.ok(producto));

        ProductoDto resultado = productClient.obtenerProducto(1L, bearerToken);

        assertThat(resultado.getCodigo()).isEqualTo("PROD-001");
    }

    @Test
    void obtenerProducto_conProductoInexistente_debeLanzarResourceNotFoundException() {
        when(restTemplate.exchange(eq("http://localhost:8083/api/productos/99"), eq(HttpMethod.GET),
                any(), eq(ProductoDto.class)))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.NOT_FOUND, "Not Found", new HttpHeaders(), new byte[0], StandardCharsets.UTF_8));

        assertThatThrownBy(() -> productClient.obtenerProducto(99L, bearerToken))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Producto no encontrado");
    }

    @Test
    void obtenerProducto_conProductServiceCaido_debeLanzarBusinessException() {
        when(restTemplate.exchange(eq("http://localhost:8083/api/productos/1"), eq(HttpMethod.GET),
                any(), eq(ProductoDto.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        assertThatThrownBy(() -> productClient.obtenerProducto(1L, bearerToken))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No fue posible comunicarse con product-service");
    }

    @Test
    void ajustarStock_conRespuestaExitosa_debeRetornarElProductoActualizado() {
        ProductoDto actualizado = new ProductoDto(1L, "PROD-001", "Notebook", BigDecimal.TEN, 15, 2, true);
        when(restTemplate.exchange(eq("http://localhost:8083/api/productos/1/ajustar-stock"), eq(HttpMethod.POST),
                any(), eq(ProductoDto.class)))
                .thenReturn(ResponseEntity.ok(actualizado));

        ProductoDto resultado = productClient.ajustarStock(1L, "ENTRADA", 5, bearerToken);

        assertThat(resultado.getStock()).isEqualTo(15);
    }

    @Test
    void ajustarStock_conProductoInexistente_debeLanzarResourceNotFoundException() {
        when(restTemplate.exchange(eq("http://localhost:8083/api/productos/99/ajustar-stock"), eq(HttpMethod.POST),
                any(), eq(ProductoDto.class)))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.NOT_FOUND, "Not Found", new HttpHeaders(), new byte[0], StandardCharsets.UTF_8));

        assertThatThrownBy(() -> productClient.ajustarStock(99L, "SALIDA", 1, bearerToken))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void ajustarStock_conErrorDeNegocioConMensajeJson_debeExtraerElMensajeDelCuerpo() {
        String cuerpoError = "{\"message\":\"Stock insuficiente\"}";
        HttpServerErrorException error = HttpServerErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request", new HttpHeaders(),
                cuerpoError.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        when(restTemplate.exchange(eq("http://localhost:8083/api/productos/1/ajustar-stock"), eq(HttpMethod.POST),
                any(), eq(ProductoDto.class)))
                .thenThrow(error);

        assertThatThrownBy(() -> productClient.ajustarStock(1L, "SALIDA", 999, bearerToken))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Stock insuficiente");
    }

    @Test
    void ajustarStock_conErrorDeNegocioConJsonSinCampoMensaje_debeUsarMensajePorDefecto() {
        String cuerpoSinMensaje = "{\"error\":\"algo paso\"}";
        HttpServerErrorException error = HttpServerErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request", new HttpHeaders(),
                cuerpoSinMensaje.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        when(restTemplate.exchange(eq("http://localhost:8083/api/productos/1/ajustar-stock"), eq(HttpMethod.POST),
                any(), eq(ProductoDto.class)))
                .thenThrow(error);

        assertThatThrownBy(() -> productClient.ajustarStock(1L, "SALIDA", 1, bearerToken))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("product-service respondio con error");
    }

    @Test
    void ajustarStock_conErrorDeNegocioSinCuerpoParseable_debeUsarMensajePorDefecto() {
        HttpServerErrorException error = HttpServerErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", new HttpHeaders(),
                "no es json".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        when(restTemplate.exchange(eq("http://localhost:8083/api/productos/1/ajustar-stock"), eq(HttpMethod.POST),
                any(), eq(ProductoDto.class)))
                .thenThrow(error);

        assertThatThrownBy(() -> productClient.ajustarStock(1L, "SALIDA", 1, bearerToken))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("product-service respondio con error");
    }

    @Test
    void ajustarStock_conProductServiceCaido_debeLanzarBusinessException() {
        when(restTemplate.exchange(eq("http://localhost:8083/api/productos/1/ajustar-stock"), eq(HttpMethod.POST),
                any(), eq(ProductoDto.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        assertThatThrownBy(() -> productClient.ajustarStock(1L, "ENTRADA", 1, bearerToken))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No fue posible comunicarse con product-service");
    }
}
