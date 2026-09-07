package com.inventario.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TwoFactorSetupResponse {
    private String secret;
    private String otpAuthUrl;
    private String qrCodeImageBase64;
}
