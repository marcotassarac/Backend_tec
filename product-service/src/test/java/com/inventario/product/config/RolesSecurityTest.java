package com.inventario.product.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Reglas por rol con tokens firmados igual que auth-service (claim "rol"). */
@SpringBootTest
@AutoConfigureMockMvc
class RolesSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SecurityConfig securityConfig;

    @Value("${jwt.secret}")
    private String secret;

    private String token(String rol) throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder().subject("usuario")
                .expirationTime(new Date(System.currentTimeMillis() + 60_000));
        if (rol != null) {
            claims.claim("rol", rol);
        }
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS512), claims.build());
        jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return "Bearer " + jwt.serialize();
    }

    @Test
    void operador_recibe403() throws Exception {
        mockMvc.perform(delete("/api/productos/987654").header("Authorization", token("OPERADOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void tokenSinRol_recibe403() throws Exception {
        mockMvc.perform(delete("/api/productos/987654").header("Authorization", token(null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_pasaElControlDeAcceso() throws Exception {
        mockMvc.perform(delete("/api/productos/987654").header("Authorization", token("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void convertidor_traduceElClaimRolARoleConPrefijo() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS512").subject("u").claim("rol", "ADMIN")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();

        assertThat(securityConfig.jwtAuthenticationConverter().convert(jwt).getAuthorities())
                .extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ADMIN");
    }
}
