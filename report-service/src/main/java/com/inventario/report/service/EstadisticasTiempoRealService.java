package com.inventario.report.service;

import com.inventario.report.dto.EstadisticasTiempoRealResponse;
import com.inventario.report.dto.EstadisticasTiempoRealResponse.MovimientoReciente;
import com.inventario.report.dto.EstadisticasTiempoRealResponse.ResumenProductoTiempoReal;
import com.inventario.report.event.MovimientoEvent;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Agregados en memoria que se actualizan con cada evento del stream. Lo invocan 3 hilos
 * consumidores a la vez (uno por particion), por eso solo usa estructuras concurrentes.
 */
@Service
public class EstadisticasTiempoRealService {

    static final int MAX_RECIENTES = 20;
    static final int MAX_IDS_RECORDADOS = 10_000;

    private final AtomicLong eventosProcesados = new AtomicLong();
    private final AtomicLong duplicadosDescartados = new AtomicLong();
    private final AtomicLong totalEntradas = new AtomicLong();
    private final AtomicLong totalSalidas = new AtomicLong();
    private final AtomicLong unidadesIngresadas = new AtomicLong();
    private final AtomicLong unidadesDespachadas = new AtomicLong();
    private final Map<Long, ResumenProductoTiempoReal> porProducto = new ConcurrentHashMap<>();
    private final Deque<MovimientoReciente> recientes = new ConcurrentLinkedDeque<>();
    private final Set<String> idsProcesados = Collections.synchronizedSet(Collections.newSetFromMap(
            new LinkedHashMap<>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > MAX_IDS_RECORDADOS;
                }
            }));
    private volatile LocalDateTime ultimaActualizacion;

    /**
     * Kafka garantiza entrega at-least-once: tras un reintento o un rebalanceo el mismo evento
     * puede llegar dos veces. Se recuerdan los ultimos eventId para no contarlo doble.
     *
     * @return false si el evento ya habia sido procesado
     */
    public boolean registrar(MovimientoEvent evento) {
        if (!idsProcesados.add(evento.eventId())) {
            duplicadosDescartados.incrementAndGet();
            return false;
        }

        boolean esEntrada = "ENTRADA".equals(evento.tipoMovimiento());
        long cantidad = evento.cantidad();
        eventosProcesados.incrementAndGet();
        if (esEntrada) {
            totalEntradas.incrementAndGet();
            unidadesIngresadas.addAndGet(cantidad);
        } else {
            totalSalidas.incrementAndGet();
            unidadesDespachadas.addAndGet(cantidad);
        }

        porProducto.merge(evento.productoId(), resumenInicial(evento, esEntrada),
                (actual, nuevo) -> acumular(actual, nuevo));

        recientes.addFirst(new MovimientoReciente(evento.eventId(), evento.productoId(), evento.productoCodigo(),
                evento.tipoMovimiento(), evento.cantidad(), evento.stockResultante(), evento.usuario(), evento.fecha()));
        while (recientes.size() > MAX_RECIENTES) {
            recientes.pollLast();
        }

        ultimaActualizacion = LocalDateTime.now();
        return true;
    }

    public EstadisticasTiempoRealResponse obtener() {
        var productos = new ArrayList<>(porProducto.values());
        productos.sort(Comparator.comparing(ResumenProductoTiempoReal::productoId));
        return new EstadisticasTiempoRealResponse(
                eventosProcesados.get(),
                duplicadosDescartados.get(),
                totalEntradas.get(),
                totalSalidas.get(),
                unidadesIngresadas.get(),
                unidadesDespachadas.get(),
                ultimaActualizacion,
                productos,
                new ArrayList<>(recientes));
    }

    private ResumenProductoTiempoReal resumenInicial(MovimientoEvent e, boolean esEntrada) {
        return new ResumenProductoTiempoReal(e.productoId(), e.productoCodigo(), e.productoNombre(), 1,
                esEntrada ? e.cantidad() : 0, esEntrada ? 0 : e.cantidad(), e.stockResultante(), e.fecha());
    }

    /** El stock vigente es el del movimiento mas reciente: los eventos de una particion pueden reprocesarse. */
    private ResumenProductoTiempoReal acumular(ResumenProductoTiempoReal actual, ResumenProductoTiempoReal nuevo) {
        boolean nuevoEsPosterior = !nuevo.ultimoMovimiento().isBefore(actual.ultimoMovimiento());
        return new ResumenProductoTiempoReal(
                actual.productoId(),
                nuevo.productoCodigo(),
                nuevo.productoNombre(),
                actual.movimientos() + 1,
                actual.unidadesIngresadas() + nuevo.unidadesIngresadas(),
                actual.unidadesDespachadas() + nuevo.unidadesDespachadas(),
                nuevoEsPosterior ? nuevo.stockActual() : actual.stockActual(),
                nuevoEsPosterior ? nuevo.ultimoMovimiento() : actual.ultimoMovimiento());
    }
}
