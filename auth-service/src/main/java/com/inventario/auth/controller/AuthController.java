package com.inventario.auth.controller;

import com.inventario.auth.dto.*;
import com.inventario.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** Primer factor. Si el usuario tiene 2FA activo, responde requiresTwoFactor=true sin token. */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /** Segundo factor: codigo TOTP de 6 digitos. Publico, ya que aun no existe un JWT en este punto. */
    @PostMapping("/2fa/verify")
    public ResponseEntity<LoginResponse> verificarSegundoFactor(@Valid @RequestBody TwoFactorVerifyRequest request) {
        return ResponseEntity.ok(authService.verificarSegundoFactor(request));
    }

    /** Permite al frontend validar la sesion vigente y recuperar los datos del usuario logeado. */
    @GetMapping("/me")
    public ResponseEntity<UsuarioResponse> me(HttpServletRequest request) {
        String username = (String) request.getAttribute("username");
        return ResponseEntity.ok(authService.getByUsername(username));
    }

    /** Requiere sesion activa: genera un secreto nuevo y el QR para escanear con Google/Microsoft Authenticator. */
    @PostMapping("/2fa/setup")
    public ResponseEntity<TwoFactorSetupResponse> iniciarSegundoFactor(HttpServletRequest request) {
        String username = (String) request.getAttribute("username");
        return ResponseEntity.ok(authService.iniciarConfiguracionSegundoFactor(username));
    }

    /** Confirma el setup con el primer codigo generado por la app y activa el 2FA para el usuario. */
    @PostMapping("/2fa/enable")
    public ResponseEntity<TwoFactorStatusResponse> activarSegundoFactor(HttpServletRequest request,
                                                                         @Valid @RequestBody TwoFactorCodeRequest body) {
        String username = (String) request.getAttribute("username");
        return ResponseEntity.ok(authService.confirmarSegundoFactor(username, body));
    }

    @PostMapping("/2fa/disable")
    public ResponseEntity<TwoFactorStatusResponse> desactivarSegundoFactor(HttpServletRequest request) {
        String username = (String) request.getAttribute("username");
        return ResponseEntity.ok(authService.desactivarSegundoFactor(username));
    }

    @GetMapping("/2fa/status")
    public ResponseEntity<TwoFactorStatusResponse> estadoSegundoFactor(HttpServletRequest request) {
        String username = (String) request.getAttribute("username");
        return ResponseEntity.ok(authService.obtenerEstadoSegundoFactor(username));
    }
}
