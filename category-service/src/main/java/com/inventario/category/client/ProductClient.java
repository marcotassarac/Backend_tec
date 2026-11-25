package com.inventario.category.client;

import com.inventario.category.exception.BusinessException;
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

/** Cliente HTTP hacia product-service, reenviando el token del usuario. */
@Component
public class ProductClient {

    private static final Logger log = LoggerFactory.getLogger(ProductClient.class);

    private final RestTemplate restTemplate;

    @Value("${services.product-service.url}")
    private String productServiceUrl;

    public ProductClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Si product-service no responde se rechaza la operacion: es preferible no eliminar a
     * dejar productos apuntando a una categoria que ya no existe.
     */
    @SuppressWarnings("unchecked")
    public boolean categoriaTieneProductos(Long categoriaId, String bearerToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", bearerToken);
            Map<String, Object> respuesta = restTemplate.exchange(
                    productServiceUrl + "/api/productos/categoria/" + categoriaId + "/existe",
                    HttpMethod.GET, new HttpEntity<>(headers), Map.class).getBody();
            return respuesta != null && Boolean.TRUE.equals(respuesta.get("existe"));
        } catch (RestClientException ex) {
            log.error("No se pudo consultar product-service para la categoria {}: {}", categoriaId, ex.getMessage());
            throw new BusinessException(
                    "No fue posible verificar si la categoria tiene productos. Intenta nuevamente en unos minutos");
        }
    }
}
