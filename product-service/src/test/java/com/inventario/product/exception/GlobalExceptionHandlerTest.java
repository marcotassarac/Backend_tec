package com.inventario.product.exception;

import com.inventario.product.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private HttpServletRequest mockRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/productos/1");
        return request;
    }

    @Test
    void handleNotFound_debeRetornar404ConElMensaje() {
        ResponseEntity<ErrorResponse> response = handler.handleNotFound(
                new ResourceNotFoundException("Producto no encontrado con id 1"), mockRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessage()).isEqualTo("Producto no encontrado con id 1");
        assertThat(response.getBody().getPath()).isEqualTo("/api/productos/1");
    }

    @Test
    void handleBusiness_debeRetornar400ConElMensaje() {
        ResponseEntity<ErrorResponse> response = handler.handleBusiness(
                new BusinessException("Ya existe un producto con el codigo PROD-100"), mockRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).contains("Ya existe un producto");
    }

    @Test
    void handleValidation_debeUnirLosErroresDeCadaCampo() {
        FieldError error1 = new FieldError("productoRequest", "codigo", "El codigo es obligatorio");
        FieldError error2 = new FieldError("productoRequest", "nombre", "El nombre es obligatorio");
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(error1, error2));
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex, mockRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage())
                .isEqualTo("codigo: El codigo es obligatorio; nombre: El nombre es obligatorio");
    }

    @Test
    void handleGeneric_debeRetornar500ConElMensajeDeLaExcepcion() {
        ResponseEntity<ErrorResponse> response = handler.handleGeneric(
                new RuntimeException("error inesperado"), mockRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage()).isEqualTo("error inesperado");
    }
}
