package com.inventario.report.consumer;

import com.inventario.report.config.RabbitMQConfig;
import com.inventario.report.dto.NotificacionItem;
import com.inventario.report.service.BuzonMovimientosService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
public class MovimientoNotificacionConsumer {

    private static final Logger log = LoggerFactory.getLogger(MovimientoNotificacionConsumer.class);

    private final BuzonMovimientosService buzon;

    public MovimientoNotificacionConsumer(BuzonMovimientosService buzon) {
        this.buzon = buzon;
    }

    @RabbitListener(queues = RabbitMQConfig.COLA_NOTIFICACIONES_ADMIN)
    public void recibirNotificacionAdmin(Map<String, Object> evento) {
        buzon.agregarNotificacionAdmin(convertir(evento, RabbitMQConfig.COLA_NOTIFICACIONES_ADMIN_DLQ));
        log.info("Notificacion para ADMIN: {} de {} ({})", evento.get("tipoMovimiento"),
                evento.get("productoCodigo"), evento.get("usuario"));
    }

    @RabbitListener(queues = RabbitMQConfig.COLA_AUDITORIA_MOVIMIENTOS)
    public void recibirAuditoria(Map<String, Object> evento) {
        buzon.agregarAuditoria(convertir(evento, RabbitMQConfig.COLA_AUDITORIA_MOVIMIENTOS_DLQ));
    }

    /** Un evento sin usuario, tipo o producto se rechaza y RabbitMQ lo deriva a la DLQ de su cola. */
    private NotificacionItem convertir(Map<String, Object> evento, String dlq) {
        if (!(evento.get("usuario") instanceof String usuario) || usuario.isBlank()
                || !(evento.get("tipoMovimiento") instanceof String tipo) || tipo.isBlank()
                || !(evento.get("productoCodigo") instanceof String codigo) || codigo.isBlank()) {
            log.error("[GRAVE] Evento de movimiento invalido: {}. Se envia a {}", evento, dlq);
            throw new IllegalArgumentException("Evento de movimiento invalido: faltan usuario, tipo o producto");
        }
        return new NotificacionItem(usuario, texto(evento.get("rolUsuario")), tipo, codigo,
                texto(evento.get("productoNombre")), numero(evento.get("cantidad")),
                numero(evento.get("stockResultante")), texto(evento.get("fechaMovimiento")), LocalDateTime.now());
    }

    private static String texto(Object valor) {
        return valor != null ? valor.toString() : null;
    }

    private static Integer numero(Object valor) {
        return valor instanceof Number n ? n.intValue() : null;
    }
}
