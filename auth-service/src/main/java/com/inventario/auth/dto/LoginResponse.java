package com.inventario.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponse {
    /** true si el login todavia no termino y falta el segundo factor (TOTP). */
    @Builder.Default
    private boolean requiresTwoFactor = false;

    /** Presente solo cuando requiresTwoFactor=true: se usa para llamar a /api/auth/2fa/verify. */
    private String pendingSessionId;

    private String token;
    private String tokenType;
    private Long id;
    private String username;
    private String nombreCompleto;
    private String rol;
    private long expiresInMs;
}
