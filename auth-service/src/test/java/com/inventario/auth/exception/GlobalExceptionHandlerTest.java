package com.inventario.auth.exception;

import com.inventario.auth.dto.ErrorResponse;
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

    private HttpServletRequest requestMock() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/auth/login");
        return request;
    }

    @Test
    void handleBadCredentials_debeRetornar401ConElMensajeOriginal() {
        ResponseEntity<ErrorResponse> response = handler.handleBadCredentials(
                new BadCredentialsException("Usuario o contrasena incorrectos"), requestMock());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getMessage()).isEqualTo("Usuario o contrasena incorrectos");
        assertThat(response.getBody().getPath()).isEqualTo("/api/auth/login");
    }

    @Test
    void handleValidation_debeUnirLosErroresDeCadaCampo() {
        FieldError errorUsername = new FieldError("loginRequest", "username", "no puede estar vacio");
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(errorUsername));

        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex, requestMock());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).contains("username: no puede estar vacio");
    }

    @Test
    void handleGeneric_debeRetornar500SinExponerElMensajeTecnico() {
        ResponseEntity<ErrorResponse> response = handler.handleGeneric(
                new RuntimeException("fallo inesperado"), requestMock());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage())
                .isEqualTo(GlobalExceptionHandler.MENSAJE_ERROR_INTERNO)
                .doesNotContain("fallo inesperado");
    }
}
