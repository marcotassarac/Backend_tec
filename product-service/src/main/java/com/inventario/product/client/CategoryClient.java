package com.inventario.product.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cliente HTTP hacia category-service. Se usa para enriquecer los productos con el
 * nombre de su categoria. Si el servicio de categorias no responde, se degrada con
 * gracia (no rompe el listado de productos).
 *
 * EST-01 (prueba de estres JMeter): el listado de productos ya no pide la categoria de
 * cada producto por separado (patron N+1). Se piden todas las categorias en una sola
 * llamada y se guardan unos segundos en memoria, porque cambian muy poco.
 */
@Component
public class CategoryClient {

    private static final Logger log = LoggerFactory.getLogger(CategoryClient.class);

    private final RestTemplate restTemplate;

    @Value("${services.category-service.url}")
    private String categoryServiceUrl;

    @Value("${services.category-service.cache-ttl-ms:30000}")
    private long cacheTtlMs;

    private volatile Map<Long, String> cacheNombres;
    private volatile long cacheExpiraEn;

    public CategoryClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @SuppressWarnings("unchecked")
    public String obtenerNombreCategoria(Long categoriaId, String bearerToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", bearerToken);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            Map<String, Object> categoria = restTemplate.exchange(
                    categoryServiceUrl + "/api/categorias/" + categoriaId,
                    HttpMethod.GET, entity, Map.class).getBody();

            return categoria != null ? String.valueOf(categoria.get("nombre")) : null;
        } catch (RestClientException ex) {
            log.warn("No se pudo obtener la categoria {}: {}", categoriaId, ex.getMessage());
            return null;
        }
    }

    /**
     * Devuelve id -> nombre de todas las categorias con una sola llamada a category-service.
     * Usa una copia en memoria mientras no venza (cacheTtlMs). Si el servicio no responde,
     * devuelve un mapa vacio y no guarda nada en cache.
     */
    @SuppressWarnings("unchecked")
    public Map<Long, String> obtenerNombresCategorias(String bearerToken) {
        Map<Long, String> enCache = cacheNombres;
        if (enCache != null && System.currentTimeMillis() < cacheExpiraEn) {
            return enCache;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", bearerToken);
            List<Map<String, Object>> categorias = restTemplate.exchange(
                    categoryServiceUrl + "/api/categorias",
                    HttpMethod.GET, new HttpEntity<>(headers), List.class).getBody();

            Map<Long, String> nombres = new HashMap<>();
            if (categorias != null) {
                for (Map<String, Object> categoria : categorias) {
                    nombres.put(Long.valueOf(String.valueOf(categoria.get("id"))), String.valueOf(categoria.get("nombre")));
                }
            }
            cacheNombres = Map.copyOf(nombres);
            cacheExpiraEn = System.currentTimeMillis() + cacheTtlMs;
            return cacheNombres;
        } catch (RestClientException ex) {
            log.warn("No se pudo obtener el listado de categorias: {}", ex.getMessage());
            return Map.of();
        }
    }
}
