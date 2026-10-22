package com.inventario.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventario.auth.dto.LoginRequest;
import com.inventario.auth.dto.LoginResponse;
import com.inventario.auth.dto.TwoFactorCodeRequest;
import com.inventario.auth.dto.TwoFactorSetupResponse;
import com.inventario.auth.dto.TwoFactorStatusResponse;
import com.inventario.auth.dto.TwoFactorVerifyRequest;
import com.inventario.auth.dto.UsuarioResponse;
import com.inventario.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas del controller via MockMvc standalone (sin levantar el contexto completo de
 * Spring): se mockea AuthService y se usa un filtro de prueba que simula al JwtAuthFilter
 * real, dejando "username" como atributo del request tal como lo hace el filtro en produccion.
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        AuthController controller = new AuthController(authService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilter((request, response, chain) -> {
                    ((HttpServletRequest) request).setAttribute("username", "operador");
                    chain.doFilter(request, response);
                }, "/*")
                .build();
    }

    @Test
    void login_debeDelegarEnElServicioYRetornar200() throws Exception {
        when(authService.login(any())).thenReturn(LoginResponse.builder().token("jwt").build());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("operador", "clave123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt"));
    }

    @Test
    void verificarSegundoFactor_debeDelegarEnElServicioYRetornar200() throws Exception {
        when(authService.verificarSegundoFactor(any())).thenReturn(LoginResponse.builder().token("jwt-final").build());

        mockMvc.perform(post("/api/auth/2fa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TwoFactorVerifyRequest("sesion-1", "123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-final"));
    }

    @Test
    void me_debeRetornarLosDatosDelUsuarioAutenticado() throws Exception {
        when(authService.getByUsername("operador")).thenReturn(
                UsuarioResponse.builder().username("operador").rol("OPERADOR").activo(true).build());

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("operador"));
    }

    @Test
    void iniciarSegundoFactor_debeRetornarElSecretoYElQr() throws Exception {
        when(authService.iniciarConfiguracionSegundoFactor("operador")).thenReturn(
                TwoFactorSetupResponse.builder().secret("SECRETO").otpAuthUrl("otpauth://totp/x").qrCodeImageBase64("data:x").build());

        mockMvc.perform(post("/api/auth/2fa/setup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").value("SECRETO"));
    }

    @Test
    void activarSegundoFactor_debeDelegarEnElServicioConElBodyRecibido() throws Exception {
        when(authService.confirmarSegundoFactor(anyString(), any())).thenReturn(
                TwoFactorStatusResponse.builder().totpEnabled(true).build());

        mockMvc.perform(post("/api/auth/2fa/enable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TwoFactorCodeRequest("123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totpEnabled").value(true));
    }

    @Test
    void desactivarSegundoFactor_debeDelegarEnElServicio() throws Exception {
        when(authService.desactivarSegundoFactor("operador")).thenReturn(
                TwoFactorStatusResponse.builder().totpEnabled(false).build());

        mockMvc.perform(post("/api/auth/2fa/disable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totpEnabled").value(false));
    }

    @Test
    void estadoSegundoFactor_debeDelegarEnElServicio() throws Exception {
        when(authService.obtenerEstadoSegundoFactor("operador")).thenReturn(
                TwoFactorStatusResponse.builder().totpEnabled(true).build());

        mockMvc.perform(get("/api/auth/2fa/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totpEnabled").value(true));
    }
}
