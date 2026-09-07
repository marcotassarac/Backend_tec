package com.inventario.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TwoFactorCodeRequest {

    @NotBlank(message = "El codigo es obligatorio")
    @Pattern(regexp = "\\d{6}", message = "El codigo debe tener 6 digitos")
    private String code;
}
