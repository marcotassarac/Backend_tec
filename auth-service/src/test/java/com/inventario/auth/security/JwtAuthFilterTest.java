package com.inventario.auth.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;
    @Mock
    private Claims claims;

    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthFilter(jwtUtil);
    }

    @Test
    void shouldNotFilter_conRutasPublicas_debeRetornarTrue() {
        when(request.getMethod()).thenReturn("POST");

        when(request.getRequestURI()).thenReturn("/api/auth/login");
        assertThat(filter.shouldNotFilter(request)).isTrue();

        when(request.getRequestURI()).thenReturn("/api/auth/2fa/verify");
        assertThat(filter.shouldNotFilter(request)).isTrue();

        when(request.getRequestURI()).thenReturn("/h2-console/login.jsp");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_conMetodoOptions_debeRetornarTrue() {
        when(request.getMethod()).thenReturn("OPTIONS");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_conRutaProtegida_debeRetornarFalse() {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/auth/me");

        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    @Test
    void doFilterInternal_sinHeaderAuthorization_debeResponder401YNoContinuarLaCadena() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/api/auth/me");
        StringWriter writer = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(writer));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verifyNoInteractions(filterChain);
        assertThat(writer.toString()).contains("Token no proporcionado");
    }

    @Test
    void doFilterInternal_conHeaderSinBearer_debeResponder401() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Token abc123");
        when(request.getRequestURI()).thenReturn("/api/auth/me");
        StringWriter writer = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(writer));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verifyNoInteractions(filterChain);
    }

    @Test
    void doFilterInternal_conTokenInvalido_debeResponder401() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer token-invalido");
        when(request.getRequestURI()).thenReturn("/api/auth/me");
        when(jwtUtil.parseClaims("token-invalido")).thenThrow(new io.jsonwebtoken.security.SignatureException("invalido"));
        StringWriter writer = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(writer));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verifyNoInteractions(filterChain);
        assertThat(writer.toString()).contains("Token invalido o expirado");
    }

    @Test
    void doFilterInternal_conTokenValido_debeContinuarLaCadenaConLosAtributosSeteados() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer token-valido");
        when(jwtUtil.parseClaims("token-valido")).thenReturn(claims);
        when(claims.getSubject()).thenReturn("operador");
        when(claims.get("rol", String.class)).thenReturn("OPERADOR");
        when(claims.get("id", Long.class)).thenReturn(1L);

        filter.doFilterInternal(request, response, filterChain);

        verify(request).setAttribute("username", "operador");
        verify(request).setAttribute("rol", "OPERADOR");
        verify(request).setAttribute("userId", 1L);
        verify(filterChain).doFilter(request, response);
    }
}
