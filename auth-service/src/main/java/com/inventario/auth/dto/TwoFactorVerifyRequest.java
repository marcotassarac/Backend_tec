package com.inventario.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TwoFactorVerifyRequest {

    @NotBlank(message = "La sesion pendiente es obligatoria")
    private String pendingSessionId;

    @NotBlank(message = "El codigo es obligatorio")
    @Pattern(regexp = "\\d{6}", message = "El codigo debe tener 6 digitos")
    private String code;
}
