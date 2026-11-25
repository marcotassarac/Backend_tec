package com.inventario.report.service;

import com.inventario.report.dto.NotificacionItem;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/** Guarda en memoria los ultimos movimientos recibidos por cada cola, del mas reciente al mas antiguo. */
@Service
public class BuzonMovimientosService {

    static final int MAXIMO_POR_LISTA = 200;

    private final Deque<NotificacionItem> notificacionesAdmin = new ConcurrentLinkedDeque<>();
    private final Deque<NotificacionItem> auditoria = new ConcurrentLinkedDeque<>();

    public void agregarNotificacionAdmin(NotificacionItem item) {
        agregar(notificacionesAdmin, item);
    }

    public void agregarAuditoria(NotificacionItem item) {
        agregar(auditoria, item);
    }

    public List<NotificacionItem> notificacionesAdmin() {
        return new ArrayList<>(notificacionesAdmin);
    }

    public List<NotificacionItem> auditoria() {
        return new ArrayList<>(auditoria);
    }

    private void agregar(Deque<NotificacionItem> lista, NotificacionItem item) {
        lista.addFirst(item);
        while (lista.size() > MAXIMO_POR_LISTA) {
            lista.pollLast();
        }
    }
}
