package com.inventario.category.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Prueba de extremo a extremo de la cadena de seguridad real (no se mockea Spring
 * Security): un request sin token real debe recibir 401, y uno con un JWT firmado
 * con el mismo secreto HS512 que usa auth-service debe pasar. El caso 403
 * (accessDeniedHandler) no es alcanzable con un request real porque esta
 * configuracion solo exige "autenticado", sin roles -- se prueba por reflexion
 * sobre el metodo privado, igual que se hace con TotpService en auth-service.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:category-security-test;DB_CLOSE_DELAY=-1"
})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Test
    void sinToken_debeRetornar401ConCuerpoDeErrorReal() throws Exception {
        mockMvc.perform(get("/api/categorias"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("No autorizado"))
                .andExpect(jsonPath("$.message").value("Token no proporcionado o invalido"));
    }

    @Test
    void conTokenValidoFirmadoConElMismoSecreto_debePermitirElAcceso() throws Exception {
        String token = generarTokenValido();

        mockMvc.perform(get("/api/categorias").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void conTokenMalFormado_debeRetornar401() throws Exception {
        mockMvc.perform(get("/api/categorias").header("Authorization", "Bearer token-invalido"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void escribirProhibido_debeEscribirCuerpoDeError403() throws Exception {
        SecurityConfig securityConfig = new SecurityConfig();
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getRequestURI()).thenReturn("/api/categorias/1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        Method metodo = SecurityConfig.class.getDeclaredMethod(
                "escribirProhibido", HttpServletRequest.class, jakarta.servlet.http.HttpServletResponse.class, Exception.class);
        metodo.setAccessible(true);
        metodo.invoke(securityConfig, request, response, new RuntimeException("sin permisos"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("Prohibido").contains("No tienes permisos");
    }

    private String generarTokenValido() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("admin")
                .expirationTime(new Date(System.currentTimeMillis() + 60_000))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS512), claims);
        jwt.sign(new MACSigner(jwtSecret.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}
