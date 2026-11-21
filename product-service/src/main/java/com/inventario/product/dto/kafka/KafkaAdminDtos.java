package com.inventario.product.dto.kafka;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.Map;

/** Contratos de entrada y salida del administrador de Kafka. */
public final class KafkaAdminDtos {

    private KafkaAdminDtos() {
    }

    public record CrearTopicoRequest(
            @NotBlank @Pattern(regexp = "[a-zA-Z0-9._-]{1,249}", message = "solo letras, numeros, '.', '_' y '-'")
            String nombre,
            @Min(1) @Max(50) int particiones,
            @Min(1) @Max(3) short replicas,
            Map<String, String> configuraciones
    ) {
    }

    public record ActualizarConfigRequest(@NotEmpty Map<String, String> configuraciones) {
    }

    public record AumentarParticionesRequest(@Min(1) @Max(50) int total) {
    }

    public record ReiniciarOffsetsRequest(
            @NotBlank String topico,
            @NotBlank @Pattern(regexp = "earliest|latest", message = "debe ser earliest o latest") String posicion
    ) {
    }

    public record TopicoResumen(String nombre, int particiones, int replicas) {
    }

    public record ParticionInfo(int particion, Integer lider, List<Integer> replicas, List<Integer> enSincronia) {
    }

    public record TopicoDetalle(String nombre, List<ParticionInfo> particiones, Map<String, String> configuraciones) {
    }

    public record ConsumerGroupResumen(String groupId, String estado) {
    }

    public record MiembroGrupo(String clientId, String host, List<String> particionesAsignadas) {
    }

    public record LagParticion(String topico, int particion, long offsetConfirmado, long offsetFinal, long lag) {
    }

    public record ConsumerGroupDetalle(String groupId, String estado, List<MiembroGrupo> miembros,
                                       List<LagParticion> offsets, long lagTotal) {
    }
}
