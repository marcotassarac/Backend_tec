package com.inventario.report.exception;

import com.inventario.report.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleNotFound_debeRetornar404ConElMensajeYLaRuta() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/reportes/kardex/999");

        ResponseEntity<ErrorResponse> response = handler.handleNotFound(
                new ResourceNotFoundException("Producto no encontrado con id 999"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("999");
        assertThat(response.getBody().getPath()).isEqualTo("/api/reportes/kardex/999");
    }

    @Test
    void handleGeneric_debeRetornar500ConMensajeGenericoYLaRuta() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/reportes/stock-actual");

        ResponseEntity<ErrorResponse> response = handler.handleGeneric(
                new RuntimeException("fallo inesperado"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage())
                .isEqualTo(GlobalExceptionHandler.MENSAJE_ERROR_INTERNO)
                .doesNotContain("fallo inesperado");
    }
}
