package com.inventario.auth.security;

import com.inventario.auth.entity.RolUsuario;
import com.inventario.auth.entity.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private final JwtUtil jwtUtil = new JwtUtil();

    @BeforeEach
    void setUp() throws Exception {
        setField("secret", "una-clave-secreta-de-pruebas-bien-larga-para-hmac-sha-256");
        setField("expirationMs", 3600000L);
    }

    private void setField(String name, Object value) throws Exception {
        Field field = JwtUtil.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(jwtUtil, value);
    }

    private Usuario usuario() {
        return Usuario.builder().id(1L).username("operador").nombreCompleto("Operador de Bodega")
                .rol(RolUsuario.OPERADOR).activo(true).build();
    }

    @Test
    void generateToken_debeProducirUnTokenQueSePuedeParsearDeVuelta() {
        String token = jwtUtil.generateToken(usuario());

        assertThat(token).isNotBlank();

        Claims claims = jwtUtil.parseClaims(token);
        assertThat(claims.getSubject()).isEqualTo("operador");
        assertThat(claims.get("rol", String.class)).isEqualTo("OPERADOR");
        assertThat(claims.get("nombreCompleto", String.class)).isEqualTo("Operador de Bodega");
    }

    @Test
    void parseClaims_conUnTokenInvalido_debeLanzarJwtException() {
        assertThatThrownBy(() -> jwtUtil.parseClaims("esto-no-es-un-jwt-valido"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void getExpirationMs_debeRetornarElValorConfigurado() {
        assertThat(jwtUtil.getExpirationMs()).isEqualTo(3600000L);
    }
}
