package com.inventario.report.config;

import com.inventario.report.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Levanta el contexto completo de Spring Boot (incluye SecurityConfig, RabbitMQConfig y
 * RestTemplateConfig reales) para verificar el comportamiento real de seguridad ante
 * requests sin token. Las ramas de autorizacion granular (403) no son alcanzables por HTTP
 * real en este servicio -no hay reglas por rol, solo "autenticado si/no"- asi que se cubren
 * invocando el metodo privado directamente, misma tecnica de reflexion que ya usa el
 * proyecto para TotpService en auth-service.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.kafka.listeners.auto-startup=false")
class SecurityConfigIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SecurityConfig securityConfig;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private CorsConfigurationSource corsConfigurationSource;

    @Test
    void contextoCargaConLosBeansDeSeguridadRabbitYRestTemplate() {
        assertThat(jwtDecoder).isNotNull();
        assertThat(corsConfigurationSource).isNotNull();
    }

    @Test
    void requestSinToken_debeRetornar401ConCuerpoJsonPersonalizado() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/reportes/stock-actual", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("No autorizado");
    }

    @Test
    void requestConTokenInvalido_debeRetornar401() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer token-invalido-y-mal-formado");
        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/reportes/stock-actual",
                HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void escribirProhibido_debeEscribir403ConCuerpoJsonPersonalizado() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        java.io.StringWriter sw = new java.io.StringWriter();
        when(request.getRequestURI()).thenReturn("/api/reportes/stock-actual");
        when(response.getWriter()).thenReturn(new java.io.PrintWriter(sw));

        ReflectionTestUtils.invokeMethod(securityConfig, "escribirProhibido",
                request, response, new RuntimeException("acceso denegado de prueba"));

        verifyStatusSet(response);
        assertThat(sw.toString()).contains("Prohibido").contains("No tienes permisos");
    }

    private void verifyStatusSet(HttpServletResponse response) {
        org.mockito.Mockito.verify(response).setStatus(403);
    }
}
