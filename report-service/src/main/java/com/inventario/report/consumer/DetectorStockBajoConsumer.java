package com.inventario.report.consumer;

import com.inventario.report.config.KafkaConfig;
import com.inventario.report.event.MovimientoEvent;
import com.inventario.report.service.AlertaStockService;
import com.inventario.report.service.MovimientoEventValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Grupo report-detector-alertas: lee el mismo topico que el grupo de estadisticas pero con
 * offsets propios, asi cada responsabilidad avanza a su ritmo y un fallo en una no frena a la otra.
 */
@Component
public class DetectorStockBajoConsumer {

    private static final Logger log = LoggerFactory.getLogger(DetectorStockBajoConsumer.class);

    private final MovimientoEventValidator validator;
    private final AlertaStockService alertaStockService;

    public DetectorStockBajoConsumer(MovimientoEventValidator validator, AlertaStockService alertaStockService) {
        this.validator = validator;
        this.alertaStockService = alertaStockService;
    }

    @KafkaListener(id = KafkaConfig.GRUPO_DETECTOR_ALERTAS,
            groupId = KafkaConfig.GRUPO_DETECTOR_ALERTAS,
            clientIdPrefix = KafkaConfig.GRUPO_DETECTOR_ALERTAS,
            topics = "${app.kafka.topics.movimientos}",
            containerFactory = "movimientosListenerFactory")
    public void consumir(@Payload MovimientoEvent evento,
                         @Header(KafkaHeaders.RECEIVED_PARTITION) int particion,
                         @Header(KafkaHeaders.OFFSET) long offset) {
        validator.validar(evento);
        alertaStockService.evaluar(evento).ifPresentOrElse(
                alerta -> log.warn("[{}] particion {} offset {}: producto {} en estado {} (stock {}, minimo {})",
                        KafkaConfig.GRUPO_DETECTOR_ALERTAS, particion, offset, evento.productoCodigo(),
                        alerta.nivel(), alerta.stockActual(), alerta.stockMinimo()),
                () -> log.debug("[{}] particion {} offset {}: producto {} sin cambio de estado",
                        KafkaConfig.GRUPO_DETECTOR_ALERTAS, particion, offset, evento.productoCodigo()));
    }
}
