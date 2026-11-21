package com.inventario.report.service;

import com.inventario.report.dto.EstadisticasTiempoRealResponse;
import com.inventario.report.event.MovimientoEvent;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class EstadisticasTiempoRealServiceTest {

    private final EstadisticasTiempoRealService service = new EstadisticasTiempoRealService();
    private final LocalDateTime base = LocalDateTime.of(2026, 11, 13, 10, 0);

    private MovimientoEvent evento(String id, long productoId, String tipo, int cantidad, int stock, LocalDateTime fecha) {
        return new MovimientoEvent(id, "MOVIMIENTO_REGISTRADO", 1L, productoId, "PROD-" + productoId,
                "Producto " + productoId, tipo, cantidad, stock, 5, "operador", fecha);
    }

    @Test
    void obtener_sinEventos_retornaTodoEnCero() {
        EstadisticasTiempoRealResponse r = service.obtener();

        assertThat(r.eventosProcesados()).isZero();
        assertThat(r.productos()).isEmpty();
        assertThat(r.ultimosMovimientos()).isEmpty();
        assertThat(r.ultimaActualizacion()).isNull();
    }

    @Test
    void registrar_entradasYSalidas_acumulaTotalesYResumenPorProducto() {
        assertThat(service.registrar(evento("e1", 2, "ENTRADA", 10, 10, base))).isTrue();
        assertThat(service.registrar(evento("e2", 2, "SALIDA", 3, 7, base.plusMinutes(1)))).isTrue();
        assertThat(service.registrar(evento("e3", 1, "ENTRADA", 4, 4, base.plusMinutes(2)))).isTrue();

        EstadisticasTiempoRealResponse r = service.obtener();

        assertThat(r.eventosProcesados()).isEqualTo(3);
        assertThat(r.totalEntradas()).isEqualTo(2);
        assertThat(r.totalSalidas()).isEqualTo(1);
        assertThat(r.unidadesIngresadas()).isEqualTo(14);
        assertThat(r.unidadesDespachadas()).isEqualTo(3);
        assertThat(r.ultimaActualizacion()).isNotNull();
        assertThat(r.productos()).extracting(EstadisticasTiempoRealResponse.ResumenProductoTiempoReal::productoId)
                .containsExactly(1L, 2L);
        var producto2 = r.productos().get(1);
        assertThat(producto2.movimientos()).isEqualTo(2);
        assertThat(producto2.unidadesIngresadas()).isEqualTo(10);
        assertThat(producto2.unidadesDespachadas()).isEqualTo(3);
        assertThat(producto2.stockActual()).isEqualTo(7);
        assertThat(r.ultimosMovimientos()).extracting(EstadisticasTiempoRealResponse.MovimientoReciente::eventId)
                .containsExactly("e3", "e2", "e1");
    }

    @Test
    void registrar_eventoRepetido_seDescartaYNoSeCuentaDosVeces() {
        MovimientoEvent e = evento("dup", 3, "ENTRADA", 5, 5, base);

        assertThat(service.registrar(e)).isTrue();
        assertThat(service.registrar(e)).isFalse();

        EstadisticasTiempoRealResponse r = service.obtener();
        assertThat(r.eventosProcesados()).isEqualTo(1);
        assertThat(r.duplicadosDescartados()).isEqualTo(1);
        assertThat(r.unidadesIngresadas()).isEqualTo(5);
    }

    @Test
    void registrar_eventoMasAntiguoQueElUltimo_noPisaElStockVigente() {
        service.registrar(evento("nuevo", 4, "SALIDA", 1, 8, base.plusMinutes(5)));
        service.registrar(evento("viejo", 4, "SALIDA", 1, 9, base));

        var producto = service.obtener().productos().get(0);
        assertThat(producto.stockActual()).isEqualTo(8);
        assertThat(producto.ultimoMovimiento()).isEqualTo(base.plusMinutes(5));
        assertThat(producto.movimientos()).isEqualTo(2);
    }

    @Test
    void registrar_masDeVeinteEventos_soloConservaLosMasRecientes() {
        for (int i = 0; i < EstadisticasTiempoRealService.MAX_RECIENTES + 5; i++) {
            service.registrar(evento("e" + i, 1, "ENTRADA", 1, i + 1, base.plusSeconds(i)));
        }

        var recientes = service.obtener().ultimosMovimientos();
        assertThat(recientes).hasSize(EstadisticasTiempoRealService.MAX_RECIENTES);
        assertThat(recientes.get(0).eventId()).isEqualTo("e24");
    }

    @Test
    void registrar_superandoElLimiteDeIdsRecordados_olvidaLosMasAntiguos() {
        for (int i = 0; i <= EstadisticasTiempoRealService.MAX_IDS_RECORDADOS; i++) {
            service.registrar(evento("id-" + i, 1, "ENTRADA", 1, 1, base));
        }

        // id-0 ya salio de la ventana de deduplicacion, asi que se vuelve a aceptar
        assertThat(service.registrar(evento("id-0", 1, "ENTRADA", 1, 1, base))).isTrue();
    }

    @Test
    void registrar_desdeVariosHilos_noPierdeEventos() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(3);
        for (int i = 0; i < 300; i++) {
            int n = i;
            pool.submit(() -> service.registrar(evento("c" + n, n % 3 + 1, "ENTRADA", 2, 2, base)));
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        EstadisticasTiempoRealResponse r = service.obtener();
        assertThat(r.eventosProcesados()).isEqualTo(300);
        assertThat(r.unidadesIngresadas()).isEqualTo(600);
        assertThat(r.productos()).hasSize(3);
    }
}
