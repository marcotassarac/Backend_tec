package com.inventario.inventory.consumer;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas de InventoryConsumer: ejercita directamente el metodo que procesa el mensaje
 * de RabbitMQ (sin un broker real), cubriendo el camino feliz y los casos invalidos que
 * la propia clase documenta como derivados a cola.inventario.dlq.
 */
class InventoryConsumerTest {

    private final InventoryConsumer consumer = new InventoryConsumer();

    @Test
    void procesarEvento_conEventoDeMovimientoValido_noDebeLanzarExcepcion() {
        Map<String, Object> evento = new HashMap<>();
        evento.put("productoId", 1L);
        evento.put("cantidad", 5);
        evento.put("tipo", "ENTRADA");
        evento.put("codigo", "PROD-001");

        assertThatCode(() -> consumer.procesarEvento(evento)).doesNotThrowAnyException();
    }

    @Test
    void procesarEvento_conEventoDeProductoValido_debeUsarElCampoStock() {
        Map<String, Object> evento = new HashMap<>();
        evento.put("productoId", 2L);
        evento.put("stock", 20);

        assertThatCode(() -> consumer.procesarEvento(evento)).doesNotThrowAnyException();
    }

    @Test
    void procesarEvento_conProductoIdFaltante_debeLanzarIllegalArgumentException() {
        Map<String, Object> evento = new HashMap<>();
        evento.put("cantidad", 5);

        assertThatThrownBy(() -> consumer.procesarEvento(evento))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("productoId");
    }

    @Test
    void procesarEvento_conProductoIdNoNumerico_debeLanzarIllegalArgumentException() {
        Map<String, Object> evento = new HashMap<>();
        evento.put("productoId", "no-es-numero");
        evento.put("cantidad", 5);

        assertThatThrownBy(() -> consumer.procesarEvento(evento))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("obligatorio y debe ser numerico");
    }

    @Test
    void procesarEvento_conProductoIdCero_debeLanzarIllegalArgumentException() {
        Map<String, Object> evento = new HashMap<>();
        evento.put("productoId", 0L);
        evento.put("cantidad", 5);

        assertThatThrownBy(() -> consumer.procesarEvento(evento))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Evento de inventario invalido");
    }

    @Test
    void procesarEvento_conCantidadCero_debeLanzarIllegalArgumentException() {
        Map<String, Object> evento = new HashMap<>();
        evento.put("productoId", 1L);
        evento.put("cantidad", 0);

        assertThatThrownBy(() -> consumer.procesarEvento(evento))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Evento de inventario invalido");
    }

    @Test
    void procesarEvento_conCampoCantidadNoNumerico_debeLanzarIllegalArgumentException() {
        Map<String, Object> evento = new HashMap<>();
        evento.put("productoId", 1L);
        evento.put("cantidad", "mucho");

        assertThatThrownBy(() -> consumer.procesarEvento(evento))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("obligatorio y debe ser numerico");
    }
}
