package com.inventario.uat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/**
 * Prepara los datos de cada escenario de UAT por la API (el "Dado ..."), para que la parte
 * evaluada (el "Cuando ... Entonces ...") se ejecute siempre por la interfaz, como un usuario.
 */
final class ApiUat {

    static final String AUTH = "http://localhost:8081";
    static final String CATEGORY = "http://localhost:8082";
    static final String PRODUCT = "http://localhost:8083";
    static final String INVENTORY = "http://localhost:8084";

    private static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private static final ObjectMapper JSON = new ObjectMapper();

    private ApiUat() {
    }

    record Respuesta(int status, JsonNode body) {
    }

    static String token(String usuario, String password) {
        Respuesta r = enviar("POST", AUTH + "/api/auth/login", null,
                Map.of("username", usuario, "password", password));
        if (r.status() != 200 || r.body().path("token").isMissingNode() || r.body().path("token").isNull()) {
            throw new IllegalStateException("No se pudo obtener token para " + usuario + ": " + r.body());
        }
        return r.body().path("token").asText();
    }

    /**
     * Deja la cuenta sin 2FA antes de una corrida (por si una corrida anterior quedo a medias entre
     * CA-06 y CA-08), usando el secreto que guardo CA-06.
     */
    static void desactivar2faSiQuedoActivo(String usuario, String password, java.nio.file.Path archivoSecreto) {
        Respuesta login = enviar("POST", AUTH + "/api/auth/login", null,
                Map.of("username", usuario, "password", password));
        if (!login.body().path("requiresTwoFactor").asBoolean(false)) {
            return;
        }
        try {
            String secreto = java.nio.file.Files.readString(archivoSecreto).trim();
            Totp.esperarVentanaSegura();
            Respuesta verificado = enviar("POST", AUTH + "/api/auth/2fa/verify", null, Map.of(
                    "pendingSessionId", login.body().path("pendingSessionId").asText(), "code", Totp.codigo(secreto)));
            enviar("POST", AUTH + "/api/auth/2fa/disable", verificado.body().path("token").asText(), null);
        } catch (IOException ex) {
            throw new IllegalStateException(usuario + " tiene 2FA activo y no se encontro el secreto", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    static long primeraCategoriaId(String token) {
        return enviar("GET", CATEGORY + "/api/categorias", token, null).body().get(0).path("id").asLong();
    }

    static long crearCategoria(String token, String nombre) {
        return enviar("POST", CATEGORY + "/api/categorias", token,
                Map.of("nombre", nombre, "descripcion", "Creada para UAT", "activo", true)).body().path("id").asLong();
    }

    static long crearProducto(String token, String codigo, long categoriaId, int stock, int stockMinimo, boolean activo) {
        Respuesta r = enviar("POST", PRODUCT + "/api/productos", token, Map.of(
                "codigo", codigo, "nombre", "Producto " + codigo, "descripcion", "Creado para UAT",
                "categoriaId", categoriaId, "precio", 1000, "stock", stock, "stockMinimo", stockMinimo,
                "activo", activo));
        if (r.status() != 201) {
            throw new IllegalStateException("No se pudo crear el producto " + codigo + ": " + r.body());
        }
        return r.body().path("id").asLong();
    }

    static void registrarMovimiento(String token, long productoId, String tipo, int cantidad, String motivo) {
        Respuesta r = enviar("POST", INVENTORY + "/api/movimientos", token, Map.of(
                "productoId", productoId, "tipoMovimiento", tipo, "cantidad", cantidad, "motivo", motivo));
        if (r.status() != 201) {
            throw new IllegalStateException("No se pudo registrar el movimiento: " + r.body());
        }
    }

    static JsonNode producto(String token, long id) {
        return enviar("GET", PRODUCT + "/api/productos/" + id, token, null).body();
    }

    static Respuesta enviar(String metodo, String url, String token, Object cuerpo) {
        try {
            HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/json");
            if (token != null) {
                req.header("Authorization", "Bearer " + token);
            }
            req.method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(cuerpo)));
            HttpResponse<String> resp = HTTP.send(req.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode body = resp.body() == null || resp.body().isBlank()
                    ? JSON.createObjectNode() : JSON.readTree(resp.body());
            return new Respuesta(resp.statusCode(), body);
        } catch (IOException ex) {
            throw new IllegalStateException("Error llamando a " + url, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    static HttpResponse<String> getFrontend(String url) {
        try {
            return HTTP.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }
}
