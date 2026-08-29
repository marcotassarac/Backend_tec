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

import java.util.Map;

/**
 * Cliente HTTP hacia category-service. Se usa para enriquecer los productos con el
 * nombre de su categoria. Si el servicio de categorias no responde, se degrada con
 * gracia (no rompe el listado de productos).
 */
@Component
public class CategoryClient {

    private static final Logger log = LoggerFactory.getLogger(CategoryClient.class);

    private final RestTemplate restTemplate;

    @Value("${services.category-service.url}")
    private String categoryServiceUrl;

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
}
