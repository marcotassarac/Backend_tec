package com.inventario.report.consumer;

import com.inventario.report.config.KafkaConfig;
import com.inventario.report.event.MovimientoEvent;
import com.inventario.report.service.EstadisticasTiempoRealService;
import com.inventario.report.service.MovimientoEventValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Grupo report-estadisticas: recibe todos los movimientos y mantiene los indicadores en vivo.
 * Solo traduce el mensaje de Kafka; la logica esta en el validador y en el servicio.
 */
@Component
public class EstadisticasMovimientosConsumer {

    private static final Logger log = LoggerFactory.getLogger(EstadisticasMovimientosConsumer.class);

    private final MovimientoEventValidator validator;
    private final EstadisticasTiempoRealService estadisticas;

    public EstadisticasMovimientosConsumer(MovimientoEventValidator validator, EstadisticasTiempoRealService estadisticas) {
        this.validator = validator;
        this.estadisticas = estadisticas;
    }

    @KafkaListener(id = KafkaConfig.GRUPO_ESTADISTICAS,
            groupId = KafkaConfig.GRUPO_ESTADISTICAS,
            clientIdPrefix = KafkaConfig.GRUPO_ESTADISTICAS,
            topics = "${app.kafka.topics.movimientos}",
            containerFactory = "movimientosListenerFactory")
    public void consumir(@Payload MovimientoEvent evento,
                         @Header(KafkaHeaders.RECEIVED_PARTITION) int particion,
                         @Header(KafkaHeaders.OFFSET) long offset) {
        validator.validar(evento);
        boolean nuevo = estadisticas.registrar(evento);
        log.info("[{}] particion {} offset {}: {} {} x{} del producto {} -> {}", KafkaConfig.GRUPO_ESTADISTICAS,
                particion, offset, evento.tipoMovimiento(), evento.productoCodigo(), evento.cantidad(),
                evento.productoId(), nuevo ? "estadisticas actualizadas" : "duplicado descartado");
    }
}
