package com.inventario.product.service;

import com.inventario.product.event.AlertaStockEvent;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlertaStockEstadoServiceTest {

    private final AlertaStockEstadoService service = new AlertaStockEstadoService();

    private AlertaStockEvent alerta(Long productoId, String nivel, Integer stock) {
        return new AlertaStockEvent("a", nivel, productoId, "PROD", "Producto", stock, 5, "evt", LocalDateTime.now());
    }

    @Test
    void aplicar_alertasBajoYAgotado_quedanVigentesOrdenadasPorStock() {
        service.aplicar(alerta(1L, "BAJO", 4));
        service.aplicar(alerta(2L, "AGOTADO", 0));
        service.aplicar(alerta(3L, "BAJO", 4));

        assertThat(service.listarVigentes()).extracting(AlertaStockEvent::productoId).containsExactly(2L, 1L, 3L);
    }

    @Test
    void aplicar_nuevoEstadoDelMismoProducto_reemplazaAlAnterior() {
        service.aplicar(alerta(1L, "BAJO", 4));
        service.aplicar(alerta(1L, "AGOTADO", 0));

        assertThat(service.listarVigentes()).singleElement()
                .satisfies(a -> assertThat(a.nivel()).isEqualTo("AGOTADO"));
    }

    @Test
    void aplicar_estadoNormal_cierraLaAlerta() {
        service.aplicar(alerta(1L, "BAJO", 4));
        service.aplicar(alerta(1L, AlertaStockEvent.NIVEL_NORMAL, 20));

        assertThat(service.listarVigentes()).isEmpty();
    }

    @Test
    void aplicar_alertasIncompletas_seRechazan() {
        assertThatThrownBy(() -> service.aplicar(alerta(null, "BAJO", 1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.aplicar(alerta(0L, "BAJO", 1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.aplicar(alerta(1L, null, 1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.aplicar(alerta(1L, "BAJO", null))).isInstanceOf(IllegalArgumentException.class);
    }
}
