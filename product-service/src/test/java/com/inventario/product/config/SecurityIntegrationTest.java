package com.inventario.product.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba de integracion real contra el contexto completo de Spring: levanta la app
 * (base de datos H2 en memoria de test, RabbitMQ sin conexion real ya que las
 * conexiones de Spring AMQP son perezosas) y ejercita el SecurityFilterChain real,
 * tanto sin token (401, manejado por escribirNoAutorizado) como con un JWT HS512
 * valido firmado con el mismo secreto que usa auth-service.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    private static final String SECRET = "ClaveSecretaSuperSeguraParaElProyectoGestorDeInventarioCambiarEnProduccion2026";

    @Autowired
    private MockMvc mockMvc;

    private String generarTokenValido() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject("admin")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(5, ChronoUnit.MINUTES)))
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    @Test
    void endpointProtegido_sinToken_debeRetornar401() throws Exception {
        mockMvc.perform(get("/api/productos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void endpointProtegido_conTokenValido_debePermitirElAcceso() throws Exception {
        mockMvc.perform(get("/api/productos").header("Authorization", "Bearer " + generarTokenValido()))
                .andExpect(status().isOk());
    }

    @Test
    void endpointProtegido_conTokenInvalido_debeRetornar401() throws Exception {
        mockMvc.perform(get("/api/productos").header("Authorization", "Bearer token-invalido"))
                .andExpect(status().isUnauthorized());
    }
}
