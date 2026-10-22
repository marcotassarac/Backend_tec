package com.inventario.product.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Prueba unitaria de SecurityConfig: construye la clase directamente (sin contexto de
 * Spring) e invoca por reflexion sus metodos privados de manejo de error, ya que esos
 * solo se disparan en un escenario real de 401/403 dificil de forzar end-to-end. El
 * flujo completo (filtro de seguridad real validando un JWT) se prueba en
 * SecurityIntegrationTest.
 */
class SecurityConfigTest {

    private SecurityConfig buildConfig() {
        SecurityConfig config = new SecurityConfig();
        ReflectionTestUtils.setField(config, "jwtSecret",
                "ClaveSecretaSuperSeguraParaElProyectoGestorDeInventarioCambiarEnProduccion2026");
        ReflectionTestUtils.setField(config, "allowedOrigins", "http://localhost:3000,http://localhost:5173");
        return config;
    }

    @Test
    void jwtDecoder_debeConstruirUnDecoderHS512() {
        JwtDecoder decoder = buildConfig().jwtDecoder();

        assertThat(decoder).isNotNull();
    }

    @Test
    void corsConfigurationSource_debeRegistrarLosOrigenesPermitidos() {
        CorsConfigurationSource source = buildConfig().corsConfigurationSource();
        CorsConfiguration config = ((UrlBasedCorsConfigurationSource) source)
                .getCorsConfigurations().get("/**");

        assertThat(config.getAllowedOrigins()).containsExactly("http://localhost:3000", "http://localhost:5173");
        assertThat(config.getAllowedMethods()).contains("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS");
        assertThat(config.getAllowCredentials()).isTrue();
    }

    @Test
    void escribirNoAutorizado_debeEscribir401ConCuerpoJson() throws Exception {
        SecurityConfig config = buildConfig();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/productos");
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));

        invocarPrivado(config, "escribirNoAutorizado", request, response, new RuntimeException("sin token"));

        org.mockito.Mockito.verify(response).setStatus(401);
        assertThat(body.toString()).contains("No autorizado").contains("/api/productos");
    }

    @Test
    void escribirProhibido_debeEscribir403ConCuerpoJson() throws Exception {
        SecurityConfig config = buildConfig();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/productos");
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));

        invocarPrivado(config, "escribirProhibido", request, response, new RuntimeException("sin permisos"));

        org.mockito.Mockito.verify(response).setStatus(403);
        assertThat(body.toString()).contains("Prohibido").contains("No tienes permisos");
    }

    private void invocarPrivado(SecurityConfig config, String nombreMetodo, HttpServletRequest request,
                                 HttpServletResponse response, Exception ex) throws Exception {
        Method method = SecurityConfig.class.getDeclaredMethod(nombreMetodo,
                HttpServletRequest.class, HttpServletResponse.class, Exception.class);
        method.setAccessible(true);
        method.invoke(config, request, response, ex);
    }
}
