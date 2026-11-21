package com.inventario.report.consumer;

import com.inventario.report.event.AlertaStockEvent;
import com.inventario.report.event.MovimientoEvent;
import com.inventario.report.exception.EventoInvalidoException;
import com.inventario.report.service.AlertaStockService;
import com.inventario.report.service.EstadisticasTiempoRealService;
import com.inventario.report.service.MovimientoEventValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Los consumidores solo validan y delegan: se prueba que respeten ese orden. */
@ExtendWith(MockitoExtension.class)
class KafkaConsumersTest {

    @Mock
    private MovimientoEventValidator validator;
    @Mock
    private EstadisticasTiempoRealService estadisticas;
    @Mock
    private AlertaStockService alertaStockService;

    private final MovimientoEvent evento = new MovimientoEvent("evt-1", "MOVIMIENTO_REGISTRADO", 1L, 7L, "PROD-007",
            "Teclado", "SALIDA", 2, 3, 5, "operador", LocalDateTime.now());

    @Test
    void estadisticas_eventoNuevo_seRegistra() {
        when(estadisticas.registrar(evento)).thenReturn(true);

        new EstadisticasMovimientosConsumer(validator, estadisticas).consumir(evento, 1, 10L);

        verify(validator).validar(evento);
        verify(estadisticas).registrar(evento);
    }

    @Test
    void estadisticas_eventoDuplicado_seDelegaIgualAlServicio() {
        when(estadisticas.registrar(evento)).thenReturn(false);

        new EstadisticasMovimientosConsumer(validator, estadisticas).consumir(evento, 1, 11L);

        verify(estadisticas).registrar(evento);
    }

    @Test
    void estadisticas_eventoInvalido_noLlegaAlServicio() {
        doThrow(new EventoInvalidoException("malo")).when(validator).validar(evento);

        assertThatThrownBy(() -> new EstadisticasMovimientosConsumer(validator, estadisticas).consumir(evento, 0, 1L))
                .isInstanceOf(EventoInvalidoException.class);
        verifyNoInteractions(estadisticas);
    }

    @Test
    void detector_conCambioDeEstado_evaluaYPublica() {
        AlertaStockEvent alerta = new AlertaStockEvent("a", AlertaStockEvent.NIVEL_BAJO, 7L, "PROD-007", "Teclado",
                3, 5, "evt-1", LocalDateTime.now());
        when(alertaStockService.evaluar(evento)).thenReturn(Optional.of(alerta));

        new DetectorStockBajoConsumer(validator, alertaStockService).consumir(evento, 2, 5L);

        verify(validator).validar(evento);
        verify(alertaStockService).evaluar(evento);
    }

    @Test
    void detector_sinCambioDeEstado_noFalla() {
        when(alertaStockService.evaluar(evento)).thenReturn(Optional.empty());

        new DetectorStockBajoConsumer(validator, alertaStockService).consumir(evento, 2, 6L);

        verify(alertaStockService).evaluar(evento);
    }

    @Test
    void detector_eventoInvalido_noEvalua() {
        doThrow(new EventoInvalidoException("malo")).when(validator).validar(evento);

        assertThatThrownBy(() -> new DetectorStockBajoConsumer(validator, alertaStockService).consumir(evento, 0, 1L))
                .isInstanceOf(EventoInvalidoException.class);
        verifyNoInteractions(alertaStockService);
    }
}
