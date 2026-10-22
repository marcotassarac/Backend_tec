package com.inventario.report.consumer;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cubre cada rama de validacion del evento que llega por cola.notificaciones:
 * la logica decide si el mensaje es valido (se registra) o invalido (se rechaza,
 * terminando en la DLQ segun la config de RabbitMQ).
 */
class ReportConsumerTest {

    private final ReportConsumer consumer = new ReportConsumer();

    private Map<String, Object> eventoValido() {
        Map<String, Object> evento = new HashMap<>();
        evento.put("productoId", 1L);
        evento.put("tipo", "ENTRADA");
        evento.put("codigo", "PROD-001");
        evento.put("nombre", "Notebook");
        evento.put("stock", 10);
        evento.put("fecha", "2026-10-20T10:00:00");
        return evento;
    }

    @Test
    void registrarKardex_conEventoValido_noDebeLanzarExcepcion() {
        assertThatCode(() -> consumer.registrarKardex(eventoValido()))
                .doesNotThrowAnyException();
    }

    @Test
    void registrarKardex_conProductoIdNoNumerico_debeLanzarIllegalArgumentException() {
        Map<String, Object> evento = eventoValido();
        evento.put("productoId", "no-es-un-numero");

        assertThatThrownBy(() -> consumer.registrarKardex(evento))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registrarKardex_conProductoIdCero_debeLanzarIllegalArgumentException() {
        Map<String, Object> evento = eventoValido();
        evento.put("productoId", 0L);

        assertThatThrownBy(() -> consumer.registrarKardex(evento))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registrarKardex_conProductoIdNegativo_debeLanzarIllegalArgumentException() {
        Map<String, Object> evento = eventoValido();
        evento.put("productoId", -5L);

        assertThatThrownBy(() -> consumer.registrarKardex(evento))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registrarKardex_conTipoNoString_debeLanzarIllegalArgumentException() {
        Map<String, Object> evento = eventoValido();
        evento.put("tipo", 123);

        assertThatThrownBy(() -> consumer.registrarKardex(evento))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registrarKardex_conTipoEnBlanco_debeLanzarIllegalArgumentException() {
        Map<String, Object> evento = eventoValido();
        evento.put("tipo", "   ");

        assertThatThrownBy(() -> consumer.registrarKardex(evento))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
