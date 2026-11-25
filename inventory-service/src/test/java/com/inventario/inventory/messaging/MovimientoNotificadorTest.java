package com.inventario.inventory.messaging;

import com.inventario.inventory.config.RabbitMQConfig;
import com.inventario.inventory.dto.MovimientoResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MovimientoNotificadorTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final MovimientoNotificador notificador = new MovimientoNotificador(rabbitTemplate);

    private MovimientoResponse movimiento() {
        return MovimientoResponse.builder().id(5L).productoId(1L).productoCodigo("PROD-001")
                .productoNombre("Notebook").tipoMovimiento("SALIDA").cantidad(2).stockResultante(8)
                .usuario("operador").fecha(LocalDateTime.of(2026, 11, 17, 10, 0)).build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void notificar_publicaConRoutingKeyPorRol() {
        notificador.notificar(movimiento(), "OPERADOR");

        ArgumentCaptor<Object> evento = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.MOVIMIENTOS_EXCHANGE), eq("movimiento.operador"),
                evento.capture());
        Map<String, Object> cuerpo = (Map<String, Object>) evento.getValue();
        assertThat(cuerpo).containsEntry("usuario", "operador").containsEntry("rolUsuario", "OPERADOR")
                .containsEntry("productoCodigo", "PROD-001").containsEntry("stockResultante", 8)
                .containsEntry("fechaMovimiento", "2026-11-17T10:00");
    }

    @Test
    void notificar_sinRolEnElToken_usaDesconocido() {
        notificador.notificar(movimiento(), null);

        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.MOVIMIENTOS_EXCHANGE), eq("movimiento.desconocido"),
                any(Object.class));
    }

    @Test
    void notificar_siRabbitNoResponde_noPropagaElError() {
        doThrow(new AmqpConnectException(new RuntimeException("sin broker")))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        assertThatCode(() -> notificador.notificar(movimiento(), "ADMIN")).doesNotThrowAnyException();
    }
}
