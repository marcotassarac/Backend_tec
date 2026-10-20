package com.inventario.auth.service;

import com.inventario.auth.dto.LoginRequest;
import com.inventario.auth.dto.LoginResponse;
import com.inventario.auth.dto.TwoFactorVerifyRequest;
import com.inventario.auth.entity.RolUsuario;
import com.inventario.auth.entity.Usuario;
import com.inventario.auth.exception.BadCredentialsException;
import com.inventario.auth.repository.UsuarioRepository;
import com.inventario.auth.security.JwtUtil;
import com.inventario.auth.twofactor.PendingTwoFactorStore;
import com.inventario.auth.twofactor.TotpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias de AuthService. UsuarioRepository y JwtUtil se mockean (son las
 * dependencias con efectos externos: base de datos y firma criptografica); en cambio
 * PendingTwoFactorStore y TotpService se usan REALES (son logica pura en memoria), para
 * probar el flujo de 2FA de punta a punta tal como corre en produccion.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private JwtUtil jwtUtil;

    private final TotpService totpService = new TotpService();
    private final PendingTwoFactorStore pendingTwoFactorStore = new PendingTwoFactorStore();
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(usuarioRepository, jwtUtil, totpService, pendingTwoFactorStore);
    }

    private Usuario usuarioBase(boolean totpEnabled) {
        return Usuario.builder()
                .id(1L).username("operador").password(passwordEncoder.encode("clave123"))
                .nombreCompleto("Operador de Bodega").rol(RolUsuario.OPERADOR)
                .activo(true).totpEnabled(totpEnabled).build();
    }

    @Test
    void login_conCredencialesValidasYSin2FA_debeRetornarTokenDirecto() {
        Usuario usuario = usuarioBase(false);
        when(usuarioRepository.findByUsername("operador")).thenReturn(Optional.of(usuario));
        when(jwtUtil.generateToken(usuario)).thenReturn("jwt-simulado");
        when(jwtUtil.getExpirationMs()).thenReturn(3600000L);

        LoginResponse response = authService.login(new LoginRequest("operador", "clave123"));

        assertThat(response.isRequiresTwoFactor()).isFalse();
        assertThat(response.getToken()).isEqualTo("jwt-simulado");
        assertThat(response.getRol()).isEqualTo("OPERADOR");
    }

    @Test
    void login_conUsuarioInexistente_debeLanzarBadCredentialsException() {
        when(usuarioRepository.findByUsername("fantasma")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("fantasma", "x")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_conPasswordIncorrecta_debeLanzarBadCredentialsException() {
        when(usuarioRepository.findByUsername("operador")).thenReturn(Optional.of(usuarioBase(false)));

        assertThatThrownBy(() -> authService.login(new LoginRequest("operador", "clave-mala")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_conUsuarioInactivo_debeLanzarBadCredentialsException() {
        Usuario usuario = usuarioBase(false);
        usuario.setActivo(false);
        when(usuarioRepository.findByUsername("operador")).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> authService.login(new LoginRequest("operador", "clave123")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("inactivo");
    }

    @Test
    void login_conUsuarioCon2FAHabilitado_debeRetornarPendienteSinToken() {
        when(usuarioRepository.findByUsername("operador")).thenReturn(Optional.of(usuarioBase(true)));

        LoginResponse response = authService.login(new LoginRequest("operador", "clave123"));

        assertThat(response.isRequiresTwoFactor()).isTrue();
        assertThat(response.getPendingSessionId()).isNotBlank();
        assertThat(response.getToken()).isNull();
    }

    @Test
    void verificarSegundoFactor_conCodigoCorrecto_debeRetornarTokenFinal() throws Exception {
        Usuario usuario = usuarioBase(true);
        usuario.setTotpSecret(totpService.generarSecreto());
        when(usuarioRepository.findByUsername("operador")).thenReturn(Optional.of(usuario));
        when(jwtUtil.generateToken(usuario)).thenReturn("jwt-final");
        when(jwtUtil.getExpirationMs()).thenReturn(3600000L);

        LoginResponse primerFactor = authService.login(new LoginRequest("operador", "clave123"));
        String codigoReal = generarCodigoParaStepActual(usuario.getTotpSecret());

        LoginResponse response = authService.verificarSegundoFactor(
                new TwoFactorVerifyRequest(primerFactor.getPendingSessionId(), codigoReal));

        assertThat(response.isRequiresTwoFactor()).isFalse();
        assertThat(response.getToken()).isEqualTo("jwt-final");
    }

    @Test
    void verificarSegundoFactor_conCodigoIncorrecto_debeLanzarBadCredentialsException() {
        Usuario usuario = usuarioBase(true);
        usuario.setTotpSecret(totpService.generarSecreto());
        when(usuarioRepository.findByUsername("operador")).thenReturn(Optional.of(usuario));

        LoginResponse primerFactor = authService.login(new LoginRequest("operador", "clave123"));

        assertThatThrownBy(() -> authService.verificarSegundoFactor(
                new TwoFactorVerifyRequest(primerFactor.getPendingSessionId(), "000000")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Codigo incorrecto");
    }

    @Test
    void verificarSegundoFactor_conSessionIdInexistente_debeLanzarBadCredentialsException() {
        assertThatThrownBy(() -> authService.verificarSegundoFactor(
                new TwoFactorVerifyRequest("sesion-que-no-existe", "123456")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("expiro");
    }

    @Test
    void verificarSegundoFactor_despuesDeCincoIntentosFallidos_debeInvalidarLaSesion() {
        Usuario usuario = usuarioBase(true);
        usuario.setTotpSecret(totpService.generarSecreto());
        when(usuarioRepository.findByUsername("operador")).thenReturn(Optional.of(usuario));

        LoginResponse primerFactor = authService.login(new LoginRequest("operador", "clave123"));
        TwoFactorVerifyRequest intentoFallido = new TwoFactorVerifyRequest(primerFactor.getPendingSessionId(), "000000");

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> authService.verificarSegundoFactor(intentoFallido))
                    .isInstanceOf(BadCredentialsException.class);
        }

        // Al sexto intento la sesion ya deberia estar bloqueada/expirada por exceso de intentos
        assertThatThrownBy(() -> authService.verificarSegundoFactor(intentoFallido))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("expiro");
    }

    private String generarCodigoParaStepActual(String secretoBase32) throws Exception {
        long currentStep = System.currentTimeMillis() / 1000L / 30L;
        org.apache.commons.codec.binary.Base32 base32 = new org.apache.commons.codec.binary.Base32();
        byte[] secretBytes = base32.decode(secretoBase32);

        Method metodo = TotpService.class.getDeclaredMethod("generarCodigoParaStep", byte[].class, long.class);
        metodo.setAccessible(true);
        return (String) metodo.invoke(totpService, secretBytes, currentStep);
    }
}
