package com.inventario.report.client;

import com.inventario.report.dto.MovimientoDto;
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
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceClientTest {

    @Mock
    private RestTemplate restTemplate;

    private InventoryServiceClient client;

    private final String bearerToken = "Bearer token-de-prueba";
    private final String baseUrl = "http://localhost:8084";

    @BeforeEach
    void setUp() {
        client = new InventoryServiceClient(restTemplate);
        ReflectionTestUtils.setField(client, "inventoryServiceUrl", baseUrl);
    }

    @Test
    void listarMovimientos_conRespuestaValida_debeRetornarLaLista() {
        MovimientoDto[] body = new MovimientoDto[]{
                new MovimientoDto(1L, 1L, "PROD-001", "Notebook", "ENTRADA", 5,
                        "Compra", "operador", 5, "2026-10-20T10:00:00")
        };
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(MovimientoDto[].class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

        assertThat(client.listarMovimientos(1L, bearerToken)).hasSize(1);
    }

    @Test
    void listarMovimientos_conCuerpoNulo_debeRetornarListaVacia() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(MovimientoDto[].class)))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

        assertThat(client.listarMovimientos(1L, bearerToken)).isEmpty();
    }

    @Test
    void listarMovimientos_sinProductoId_debeConstruirUrlSinQueryParam() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(MovimientoDto[].class)))
                .thenReturn(new ResponseEntity<>(new MovimientoDto[0], HttpStatus.OK));

        assertThat(client.listarMovimientos(null, bearerToken)).isEmpty();
    }
}
