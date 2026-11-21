package com.inventario.report.service;

import com.inventario.report.event.MovimientoEvent;
import com.inventario.report.exception.EventoInvalidoException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MovimientoEventValidatorTest {

    private final MovimientoEventValidator validator = new MovimientoEventValidator();

    @Test
    void validar_conEventoCompleto_noLanzaExcepcion() {
        MovimientoEvent evento = new MovimientoEvent("evt-1", "MOVIMIENTO_REGISTRADO", 1L, 7L, "PROD-007", "Teclado",
                "SALIDA", 2, 0, 5, "operador", LocalDateTime.now());

        assertThatCode(() -> validator.validar(evento)).doesNotThrowAnyException();
    }

    @Test
    void validar_conEventoNulo_lanzaEventoInvalido() {
        assertThatThrownBy(() -> validator.validar(null))
                .isInstanceOf(EventoInvalidoException.class)
                .hasMessageContaining("vacio");
    }

    @Test
    void validar_conTodosLosCamposInvalidos_listaCadaCampo() {
        MovimientoEvent evento = new MovimientoEvent(" ", null, null, 0L, null, null,
                "TRASPASO", 0, -1, null, null, null);

        assertThatThrownBy(() -> validator.validar(evento))
                .isInstanceOf(EventoInvalidoException.class)
                .hasMessageContaining("eventId")
                .hasMessageContaining("productoId")
                .hasMessageContaining("tipoMovimiento")
                .hasMessageContaining("cantidad")
                .hasMessageContaining("stockResultante")
                .hasMessageContaining("fecha");
    }

    @Test
    void validar_conCamposNulos_tambienLosRechaza() {
        MovimientoEvent evento = new MovimientoEvent(null, null, null, null, null, null,
                null, null, null, null, null, LocalDateTime.now());

        assertThatThrownBy(() -> validator.validar(evento))
                .isInstanceOf(EventoInvalidoException.class)
                .hasMessageContaining("eventId")
                .hasMessageContaining("productoId")
                .hasMessageContaining("tipoMovimiento")
                .hasMessageContaining("cantidad")
                .hasMessageContaining("stockResultante");
    }
}
