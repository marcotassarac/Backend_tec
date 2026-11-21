package com.inventario.report.service;

import com.inventario.report.event.AlertaStockEvent;
import com.inventario.report.event.MovimientoEvent;
import com.inventario.report.producer.AlertaStockPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AlertaStockServiceTest {

    @Mock
    private AlertaStockPublisher publisher;

    @InjectMocks
    private AlertaStockService service;

    private MovimientoEvent evento(String tipo, int stock, Integer minimo) {
        return new MovimientoEvent("evt-9", "MOVIMIENTO_REGISTRADO", 3L, 9L, "PROD-009", "Mouse",
                tipo, 1, stock, minimo, "operador", LocalDateTime.now());
    }

    @Test
    void evaluar_stockEnCero_publicaAlertaAgotado() {
        Optional<AlertaStockEvent> alerta = service.evaluar(evento("SALIDA", 0, 5));

        assertThat(alerta).isPresent();
        assertThat(alerta.get().nivel()).isEqualTo(AlertaStockEvent.NIVEL_AGOTADO);
        assertThat(alerta.get().productoId()).isEqualTo(9L);
        assertThat(alerta.get().eventoOrigenId()).isEqualTo("evt-9");
        assertThat(alerta.get().eventId()).isNotBlank();
        verify(publisher).publicar(alerta.get());
    }

    @Test
    void evaluar_stockIgualAlMinimo_publicaAlertaBajo() {
        Optional<AlertaStockEvent> alerta = service.evaluar(evento("SALIDA", 5, 5));

        assertThat(alerta).map(AlertaStockEvent::nivel).contains(AlertaStockEvent.NIVEL_BAJO);
        verify(publisher).publicar(alerta.get());
    }

    @Test
    void evaluar_entradaQueSuperaElMinimo_publicaEstadoNormal() {
        Optional<AlertaStockEvent> alerta = service.evaluar(evento("ENTRADA", 20, 5));

        assertThat(alerta).map(AlertaStockEvent::nivel).contains(AlertaStockEvent.NIVEL_NORMAL);
        verify(publisher).publicar(alerta.get());
    }

    @Test
    void evaluar_salidaQueDejaStockSobreElMinimo_noPublicaNada() {
        assertThat(service.evaluar(evento("SALIDA", 12, 5))).isEmpty();
        verifyNoInteractions(publisher);
    }

    @Test
    void evaluar_productoSinStockMinimo_usaCeroComoMinimo() {
        assertThat(service.evaluar(evento("SALIDA", 1, null))).isEmpty();
        assertThat(service.evaluar(evento("SALIDA", 0, null)))
                .map(AlertaStockEvent::nivel).contains(AlertaStockEvent.NIVEL_AGOTADO);
    }
}
