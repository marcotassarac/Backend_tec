package com.inventario.category.exception;

import com.inventario.category.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Correccion DEF-09 (UAT Ciclo 1, CA-30): ningun error expone detalles tecnicos al usuario. */
class GlobalExceptionHandlerErroresTecnicosTest {

    private GlobalExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/prueba");
    }

    @Test
    void handleDataIntegrity_debeRetornar409ConMensajeComprensibleSinSql() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "could not execute statement [Unique index or primary key violation: PUBLIC.TABLA]");

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrity(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage())
                .contains("conflicto con datos existentes")
                .doesNotContainIgnoringCase("sql")
                .doesNotContain("PUBLIC");
    }

    @Test
    void handleNoResource_debeRetornar404RecursoNoEncontrado() {
        ResponseEntity<ErrorResponse> response = handler.handleNoResource(
                new NoResourceFoundException(HttpMethod.GET, "api/no-existe"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessage()).isEqualTo("Recurso no encontrado");
    }

    @Test
    void handleNotReadable_debeRetornar400FormatoInvalido() {
        ResponseEntity<ErrorResponse> response = handler.handleNotReadable(
                new HttpMessageNotReadableException("JSON parse error: Unexpected character", mock(HttpInputMessage.class)),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("La solicitud tiene un formato invalido");
    }

    @Test
    void handleGeneric_noDebeExponerElMensajeDeLaExcepcion() {
        ResponseEntity<ErrorResponse> response = handler.handleGeneric(
                new IllegalStateException("NullPointer en com.inventario.Clase linea 42"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage()).isEqualTo(GlobalExceptionHandler.MENSAJE_ERROR_INTERNO);
    }
}
