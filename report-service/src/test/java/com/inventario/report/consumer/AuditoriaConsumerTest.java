package com.inventario.report.consumer;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditoriaConsumerTest {

    private final AuditoriaConsumer consumer = new AuditoriaConsumer();

    @Test
    void auditar_eventoValido_seRegistra() {
        Map<String, Object> evento = Map.of("tipo", "PRODUCTO_CREADO", "productoId", 4, "codigo", "PROD-004",
                "stock", 10, "fecha", "2026-11-13T10:00");

        assertThatCode(() -> consumer.auditar(evento, "auditoria.producto.producto_creado"))
                .doesNotThrowAnyException();
    }

    @Test
    void auditar_sinProductoId_seRechazaHaciaLaDlq() {
        assertThatThrownBy(() -> consumer.auditar(Map.of("tipo", "PRODUCTO_CREADO"), "auditoria.producto.x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void auditar_conProductoIdCero_seRechaza() {
        assertThatThrownBy(() -> consumer.auditar(Map.of("tipo", "PRODUCTO_CREADO", "productoId", 0), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void auditar_sinTipo_seRechaza() {
        assertThatThrownBy(() -> consumer.auditar(Map.of("productoId", 4), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void auditar_conTipoVacio_seRechaza() {
        assertThatThrownBy(() -> consumer.auditar(Map.of("productoId", 4, "tipo", " "), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
