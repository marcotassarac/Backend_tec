package com.inventario.inventory.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas directas de SecurityConfig: cubren tanto los beans (jwtDecoder, CORS) como los
 * metodos privados que escriben la respuesta de error (401/403), invocandolos por
 * reflexion ya que el camino 403 no es alcanzable hoy via HTTP real (no hay reglas por rol).
 */
class SecurityConfigTest {

    private SecurityConfig securityConfig;

    @BeforeEach
    void setUp() {
        securityConfig = new SecurityConfig();
        ReflectionTestUtils.setField(securityConfig, "jwtSecret",
                "ClaveSecretaSuperSeguraParaElProyectoGestorDeInventarioCambiarEnProduccion2026");
        ReflectionTestUtils.setField(securityConfig, "allowedOrigins", "http://localhost:3000");
    }

    @Test
    void jwtDecoder_debeConstruirUnDecoderHS512ConElSecretoConfigurado() {
        JwtDecoder decoder = securityConfig.jwtDecoder();

        assertThat(decoder).isInstanceOf(NimbusJwtDecoder.class);
    }

    @Test
    void corsConfigurationSource_debePermitirElOrigenConfiguradoYLosMetodosEsperados() {
        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/movimientos");

        CorsConfiguration configuration = source.getCorsConfiguration(request);

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins()).containsExactly("http://localhost:3000");
        assertThat(configuration.getAllowedMethods()).contains("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS");
        assertThat(configuration.getAllowCredentials()).isTrue();
    }

    @Test
    void escribirNoAutorizado_debeEscribirRespuesta401ConElCuerpoEsperado() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/movimientos");
        MockHttpServletResponse response = new MockHttpServletResponse();

        invokePrivado("escribirNoAutorizado", HttpServletRequest.class, HttpServletResponse.class, Exception.class)
                .invoke(securityConfig, request, response, new RuntimeException("sin token"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("No autorizado").contains("/api/movimientos");
    }

    @Test
    void escribirProhibido_debeEscribirRespuesta403ConElCuerpoEsperado() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/movimientos");
        MockHttpServletResponse response = new MockHttpServletResponse();

        invokePrivado("escribirProhibido", HttpServletRequest.class, HttpServletResponse.class, Exception.class)
                .invoke(securityConfig, request, response, new RuntimeException("sin permisos"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("Prohibido").contains("No tienes permisos");
    }

    private Method invokePrivado(String nombre, Class<?>... parametros) throws NoSuchMethodException {
        Method metodo = SecurityConfig.class.getDeclaredMethod(nombre, parametros);
        metodo.setAccessible(true);
        return metodo;
    }
}
