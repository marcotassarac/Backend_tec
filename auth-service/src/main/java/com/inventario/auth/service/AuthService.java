package com.inventario.auth.service;

import com.inventario.auth.dto.*;
import com.inventario.auth.entity.Usuario;
import com.inventario.auth.exception.BadCredentialsException;
import com.inventario.auth.repository.UsuarioRepository;
import com.inventario.auth.security.JwtUtil;
import com.inventario.auth.twofactor.PendingTwoFactorStore;
import com.inventario.auth.twofactor.TotpService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final JwtUtil jwtUtil;
    private final TotpService totpService;
    private final PendingTwoFactorStore pendingTwoFactorStore;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(UsuarioRepository usuarioRepository, JwtUtil jwtUtil,
                        TotpService totpService, PendingTwoFactorStore pendingTwoFactorStore) {
        this.usuarioRepository = usuarioRepository;
        this.jwtUtil = jwtUtil;
        this.totpService = totpService;
        this.pendingTwoFactorStore = pendingTwoFactorStore;
    }

    /** Primer factor: usuario/contrasena. Si el usuario tiene 2FA activo, no entrega token todavia. */
    public LoginResponse login(LoginRequest request) {
        Usuario usuario = usuarioRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BadCredentialsException("Usuario o contrasena incorrectos"));

        if (!usuario.isActivo()) {
            throw new BadCredentialsException("El usuario se encuentra inactivo");
        }

        if (!passwordEncoder.matches(request.getPassword(), usuario.getPassword())) {
            throw new BadCredentialsException("Usuario o contrasena incorrectos");
        }

        if (usuario.isTotpEnabled()) {
            String pendingSessionId = pendingTwoFactorStore.crear(usuario.getUsername());
            return LoginResponse.builder()
                    .requiresTwoFactor(true)
                    .pendingSessionId(pendingSessionId)
                    .build();
        }

        return construirLoginResponse(usuario);
    }

    /** Segundo factor: valida el codigo TOTP de 6 digitos y recien ahi emite el JWT real. */
    public LoginResponse verificarSegundoFactor(TwoFactorVerifyRequest request) {
        String username = pendingTwoFactorStore.obtenerUsername(request.getPendingSessionId());
        if (username == null) {
            throw new BadCredentialsException("La sesion de verificacion expiro, vuelve a iniciar sesion");
        }

        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));

        if (!totpService.verificarCodigo(usuario.getTotpSecret(), request.getCode())) {
            pendingTwoFactorStore.registrarIntentoFallido(request.getPendingSessionId());
            throw new BadCredentialsException("Codigo incorrecto");
        }

        pendingTwoFactorStore.invalidar(request.getPendingSessionId());
        return construirLoginResponse(usuario);
    }

    /** Genera un secreto nuevo para el usuario autenticado y el QR para escanear con la app autenticadora. */
    public TwoFactorSetupResponse iniciarConfiguracionSegundoFactor(String username) {
        Usuario usuario = buscarPorUsername(username);

        String secreto = totpService.generarSecreto();
        usuario.setTotpSecret(secreto);
        usuario.setTotpEnabled(false);
        usuarioRepository.save(usuario);

        String otpAuthUrl = totpService.generarOtpAuthUrl(usuario.getUsername(), secreto);
        String qrBase64 = totpService.generarQrBase64(otpAuthUrl);

        return TwoFactorSetupResponse.builder()
                .secret(secreto)
                .otpAuthUrl(otpAuthUrl)
                .qrCodeImageBase64(qrBase64)
                .build();
    }

    /** Confirma la configuracion: si el primer codigo generado por la app es valido, se activa el 2FA. */
    public TwoFactorStatusResponse confirmarSegundoFactor(String username, TwoFactorCodeRequest request) {
        Usuario usuario = buscarPorUsername(username);

        if (usuario.getTotpSecret() == null) {
            throw new BadCredentialsException("Primero debes iniciar la configuracion del segundo factor");
        }
        if (!totpService.verificarCodigo(usuario.getTotpSecret(), request.getCode())) {
            throw new BadCredentialsException("Codigo incorrecto");
        }

        usuario.setTotpEnabled(true);
        usuarioRepository.save(usuario);
        return TwoFactorStatusResponse.builder().totpEnabled(true).build();
    }

    public TwoFactorStatusResponse desactivarSegundoFactor(String username) {
        Usuario usuario = buscarPorUsername(username);
        usuario.setTotpEnabled(false);
        usuario.setTotpSecret(null);
        usuarioRepository.save(usuario);
        return TwoFactorStatusResponse.builder().totpEnabled(false).build();
    }

    public TwoFactorStatusResponse obtenerEstadoSegundoFactor(String username) {
        Usuario usuario = buscarPorUsername(username);
        return TwoFactorStatusResponse.builder().totpEnabled(usuario.isTotpEnabled()).build();
    }

    public UsuarioResponse getByUsername(String username) {
        Usuario usuario = buscarPorUsername(username);
        return UsuarioResponse.builder()
                .id(usuario.getId())
                .username(usuario.getUsername())
                .nombreCompleto(usuario.getNombreCompleto())
                .email(usuario.getEmail())
                .rol(usuario.getRol().name())
                .activo(usuario.isActivo())
                .build();
    }

    private Usuario buscarPorUsername(String username) {
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));
    }

    private LoginResponse construirLoginResponse(Usuario usuario) {
        String token = jwtUtil.generateToken(usuario);
        return LoginResponse.builder()
                .requiresTwoFactor(false)
                .token(token)
                .tokenType("Bearer")
                .id(usuario.getId())
                .username(usuario.getUsername())
                .nombreCompleto(usuario.getNombreCompleto())
                .rol(usuario.getRol().name())
                .expiresInMs(jwtUtil.getExpirationMs())
                .build();
    }
}
