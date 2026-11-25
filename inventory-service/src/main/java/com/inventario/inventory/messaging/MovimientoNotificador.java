package com.inventario.inventory.messaging;

import com.inventario.inventory.config.RabbitMQConfig;
import com.inventario.inventory.dto.MovimientoResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Avisa por RabbitMQ cada movimiento registrado, con la routing key movimiento.<rol>: report-service
 * arma con eso el buzon del ADMIN (solo movimientos de operadores) y la auditoria completa.
 */
@Component
public class MovimientoNotificador {

    private static final Logger log = LoggerFactory.getLogger(MovimientoNotificador.class);

    private final RabbitTemplate rabbitTemplate;

    public MovimientoNotificador(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /** Si RabbitMQ no esta disponible solo se registra: el movimiento ya quedo guardado. */
    public void notificar(MovimientoResponse movimiento, String rol) {
        String rolUsuario = rol != null ? rol : "DESCONOCIDO";
        Map<String, Object> evento = new LinkedHashMap<>();
        evento.put("usuario", movimiento.getUsuario());
        evento.put("rolUsuario", rolUsuario);
        evento.put("tipoMovimiento", movimiento.getTipoMovimiento());
        evento.put("productoCodigo", movimiento.getProductoCodigo());
        evento.put("productoNombre", movimiento.getProductoNombre());
        evento.put("cantidad", movimiento.getCantidad());
        evento.put("stockResultante", movimiento.getStockResultante());
        evento.put("fechaMovimiento", String.valueOf(movimiento.getFecha()));
        String routingKey = "movimiento." + rolUsuario.toLowerCase(Locale.ROOT);
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.MOVIMIENTOS_EXCHANGE, routingKey, evento);
            log.info("Movimiento {} notificado con routing key {}", movimiento.getId(), routingKey);
        } catch (AmqpException ex) {
            log.error("No se pudo notificar el movimiento {}: {}", movimiento.getId(), ex.getMessage());
        }
    }
}
