package com.inventario.auth.twofactor;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guarda en memoria las sesiones que ya pasaron el primer factor (usuario/contrasena)
 * y estan esperando el codigo del segundo factor (TOTP). Nunca emite un JWT real hasta
 * que el codigo es validado, por lo que un id pendiente jamas sirve como token de acceso.
 */
@Component
public class PendingTwoFactorStore {

    private static final long EXPIRACION_MS = 2 * 60 * 1000L; // 2 minutos
    private static final int MAX_INTENTOS = 5;

    private record Sesion(String username, long expiraEn, int intentos) {
    }

    private final Map<String, Sesion> sesiones = new ConcurrentHashMap<>();

    public String crear(String username) {
        limpiarExpiradas();
        String id = UUID.randomUUID().toString();
        sesiones.put(id, new Sesion(username, Instant.now().toEpochMilli() + EXPIRACION_MS, 0));
        return id;
    }

    /** Devuelve el username asociado si la sesion existe y no ha expirado ni superado los intentos. */
    public String obtenerUsername(String pendingSessionId) {
        Sesion sesion = sesiones.get(pendingSessionId);
        if (sesion == null || Instant.now().toEpochMilli() > sesion.expiraEn() || sesion.intentos() >= MAX_INTENTOS) {
            sesiones.remove(pendingSessionId);
            return null;
        }
        return sesion.username();
    }

    public void registrarIntentoFallido(String pendingSessionId) {
        sesiones.computeIfPresent(pendingSessionId,
                (id, sesion) -> new Sesion(sesion.username(), sesion.expiraEn(), sesion.intentos() + 1));
    }

    public void invalidar(String pendingSessionId) {
        sesiones.remove(pendingSessionId);
    }

    private void limpiarExpiradas() {
        long ahora = Instant.now().toEpochMilli();
        sesiones.entrySet().removeIf(entry -> ahora > entry.getValue().expiraEn());
    }
}
