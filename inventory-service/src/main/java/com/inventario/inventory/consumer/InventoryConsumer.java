package com.inventario.inventory.consumer;

import com.inventario.inventory.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class InventoryConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventoryConsumer.class);

    /**
     * Procesa los eventos de producto de cola.inventario. Si el evento trae datos invalidos
     * lanza IllegalArgumentException: el listener lo rechaza sin reencolar
     * (default-requeue-rejected=false) y RabbitMQ lo deriva a cola.inventario.dlq.
     */
    @RabbitListener(queues = RabbitMQConfig.COLA_INVENTARIO)
    public void procesarEvento(Map<String, Object> evento) {
        log.info("Evento recibido en {}: {}", RabbitMQConfig.COLA_INVENTARIO, evento);

        long productoId = leerNumero(evento, "productoId");
        // Los eventos de movimiento traen "cantidad"; los de producto solo traen "stock"
        String campoCantidad = evento.containsKey("cantidad") ? "cantidad" : "stock";
        long cantidad = leerNumero(evento, campoCantidad);

        if (productoId <= 0 || cantidad <= 0) {
            log.error("[GRAVE] Evento de inventario invalido (productoId={}, {}={}). Se envia a {}",
                    productoId, campoCantidad, cantidad, RabbitMQConfig.COLA_INVENTARIO_DLQ);
            throw new IllegalArgumentException("Evento de inventario invalido: productoId y "
                    + campoCantidad + " deben ser mayores a 0");
        }

        // Simulacion del procesamiento del evento
        log.info("Inventario procesado: evento {} del producto {} ({}), {}={}",
                evento.get("tipo"), productoId, evento.get("codigo"), campoCantidad, cantidad);
    }

    private long leerNumero(Map<String, Object> evento, String campo) {
        Object valor = evento.get(campo);
        if (valor instanceof Number numero) {
            return numero.longValue();
        }
        log.error("[GRAVE] Evento de inventario sin campo numerico '{}': {}", campo, evento);
        throw new IllegalArgumentException("El campo '" + campo + "' es obligatorio y debe ser numerico");
    }
}
