package com.inventario.report.service;

import com.inventario.report.dto.NotificacionItem;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class BuzonMovimientosServiceTest {

    private NotificacionItem item(String codigo) {
        return new NotificacionItem("operador", "OPERADOR", "SALIDA", codigo, "Producto", 1, 5,
                "2026-11-17T10:00", LocalDateTime.now());
    }

    @Test
    void lasListasMuestranPrimeroLoMasReciente() {
        BuzonMovimientosService buzon = new BuzonMovimientosService();
        buzon.agregarNotificacionAdmin(item("A"));
        buzon.agregarNotificacionAdmin(item("B"));
        buzon.agregarAuditoria(item("C"));

        assertThat(buzon.notificacionesAdmin()).extracting(NotificacionItem::productoCodigo).containsExactly("B", "A");
        assertThat(buzon.auditoria()).extracting(NotificacionItem::productoCodigo).containsExactly("C");
    }

    @Test
    void cadaListaConservaComoMaximoDoscientosEventos() {
        BuzonMovimientosService buzon = new BuzonMovimientosService();
        for (int i = 0; i < BuzonMovimientosService.MAXIMO_POR_LISTA + 10; i++) {
            buzon.agregarAuditoria(item("P" + i));
        }

        assertThat(buzon.auditoria()).hasSize(BuzonMovimientosService.MAXIMO_POR_LISTA);
        assertThat(buzon.auditoria().get(0).productoCodigo()).isEqualTo("P209");
    }
}
