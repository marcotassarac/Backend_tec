package com.inventario.auth.twofactor;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cubre las ramas de PendingTwoFactorStore que no se alcanzan en el flujo normal de
 * AuthServiceTest (sesiones ya expiradas por tiempo, no solo por exceso de intentos).
 * Para simular el paso del tiempo sin esperar 2 minutos reales, se inserta directamente
 * por reflexion una Sesion ya vencida en el mapa interno.
 */
class PendingTwoFactorStoreTest {

    private final PendingTwoFactorStore store = new PendingTwoFactorStore();

    @SuppressWarnings("unchecked")
    private Map<String, Object> sesionesMap() throws Exception {
        Field field = PendingTwoFactorStore.class.getDeclaredField("sesiones");
        field.setAccessible(true);
        return (Map<String, Object>) field.get(store);
    }

    private Object nuevaSesionExpirada(String username) throws Exception {
        Class<?> sesionClass = Class.forName("com.inventario.auth.twofactor.PendingTwoFactorStore$Sesion");
        Constructor<?> constructor = sesionClass.getDeclaredConstructor(String.class, long.class, int.class);
        constructor.setAccessible(true);
        long yaVencido = System.currentTimeMillis() - 1000L;
        return constructor.newInstance(username, yaVencido, 0);
    }

    @Test
    void obtenerUsername_conSesionYaExpiradaPorTiempo_debeRetornarNullYEliminarla() throws Exception {
        Map<String, Object> sesiones = sesionesMap();
        sesiones.put("sesion-vencida", nuevaSesionExpirada("operador"));

        String resultado = store.obtenerUsername("sesion-vencida");

        assertThat(resultado).isNull();
        assertThat(sesiones).doesNotContainKey("sesion-vencida");
    }

    @Test
    void crear_debeLimpiarSesionesYaExpiradasAntesDeCrearLaNueva() throws Exception {
        Map<String, Object> sesiones = sesionesMap();
        sesiones.put("vieja-vencida", nuevaSesionExpirada("otro-usuario"));

        String nuevoId = store.crear("operador");

        assertThat(sesiones).doesNotContainKey("vieja-vencida");
        assertThat(sesiones).containsKey(nuevoId);
    }

    @Test
    void crear_debeConservarLasSesionesVigentesYSoloEliminarLasVencidas() throws Exception {
        Map<String, Object> sesiones = sesionesMap();
        sesiones.put("vieja-vencida", nuevaSesionExpirada("otro-usuario"));
        String idVigente = store.crear("usuario-vigente");

        store.crear("operador");

        assertThat(sesiones).doesNotContainKey("vieja-vencida");
        assertThat(sesiones).containsKey(idVigente);
    }

    @Test
    void obtenerUsername_conIdInexistente_debeRetornarNull() {
        assertThat(store.obtenerUsername("no-existe")).isNull();
    }

    @Test
    void registrarIntentoFallido_conIdInexistente_noDebeLanzarNiCrearNada() throws Exception {
        store.registrarIntentoFallido("no-existe");

        assertThat(sesionesMap()).isEmpty();
    }

    @Test
    void invalidar_debeEliminarLaSesionSiExiste() {
        String id = store.crear("operador");

        store.invalidar(id);

        assertThat(store.obtenerUsername(id)).isNull();
    }
}
