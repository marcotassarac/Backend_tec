package com.inventario.report.consumer;

import com.inventario.report.dto.NotificacionItem;
import com.inventario.report.service.BuzonMovimientosService;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MovimientoNotificacionConsumerTest {

    private final BuzonMovimientosService buzon = new BuzonMovimientosService();
    private final MovimientoNotificacionConsumer consumer = new MovimientoNotificacionConsumer(buzon);

    private Map<String, Object> evento() {
        Map<String, Object> e = new HashMap<>();
        e.put("usuario", "operador");
        e.put("rolUsuario", "OPERADOR");
        e.put("tipoMovimiento", "SALIDA");
        e.put("productoCodigo", "PROD-001");
        e.put("productoNombre", "Notebook");
        e.put("cantidad", 2);
        e.put("stockResultante", 8);
        e.put("fechaMovimiento", "2026-11-17T10:00");
        return e;
    }

    @Test
    void recibirNotificacionAdmin_laAgregaAlBuzon() {
        consumer.recibirNotificacionAdmin(evento());

        NotificacionItem item = buzon.notificacionesAdmin().get(0);
        assertThat(item.usuario()).isEqualTo("operador");
        assertThat(item.cantidad()).isEqualTo(2);
        assertThat(item.recibidoEn()).isNotNull();
    }

    @Test
    void recibirAuditoria_toleraCamposOpcionalesVacios() {
        Map<String, Object> e = evento();
        e.remove("rolUsuario");
        e.remove("productoNombre");
        e.put("cantidad", "dos");

        consumer.recibirAuditoria(e);

        NotificacionItem item = buzon.auditoria().get(0);
        assertThat(item.rolUsuario()).isNull();
        assertThat(item.productoNombre()).isNull();
        assertThat(item.cantidad()).isNull();
    }

    @Test
    void eventoSinUsuarioTipoOProducto_seRechazaHaciaLaDlq() {
        for (String campo : new String[]{"usuario", "tipoMovimiento", "productoCodigo"}) {
            Map<String, Object> sinCampo = evento();
            sinCampo.remove(campo);
            assertThatThrownBy(() -> consumer.recibirAuditoria(sinCampo)).isInstanceOf(IllegalArgumentException.class);

            Map<String, Object> vacio = evento();
            vacio.put(campo, " ");
            assertThatThrownBy(() -> consumer.recibirNotificacionAdmin(vacio)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(buzon.auditoria()).isEmpty();
        assertThat(buzon.notificacionesAdmin()).isEmpty();
    }
}
