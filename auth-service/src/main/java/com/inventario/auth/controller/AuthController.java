package com.inventario.auth.controller;

import com.inventario.auth.dto.LoginRequest;
import com.inventario.auth.dto.LoginResponse;
import com.inventario.auth.dto.UsuarioResponse;
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

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /** Permite al frontend validar la sesion vigente y recuperar los datos del usuario logeado. */
    @GetMapping("/me")
    public ResponseEntity<UsuarioResponse> me(HttpServletRequest request) {
        String username = (String) request.getAttribute("username");
        return ResponseEntity.ok(authService.getByUsername(username));
    }
}
