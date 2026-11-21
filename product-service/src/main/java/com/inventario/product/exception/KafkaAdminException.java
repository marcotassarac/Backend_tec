package com.inventario.product.exception;

import org.springframework.http.HttpStatus;

/** Error de una operacion de administracion de Kafka, con el codigo HTTP que corresponde a su causa. */
public class KafkaAdminException extends RuntimeException {

    private final HttpStatus status;

    public KafkaAdminException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
