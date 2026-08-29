package com.inventario.inventory.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventario.inventory.dto.ProductoDto;
import com.inventario.inventory.exception.BusinessException;
import com.inventario.inventory.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/** Cliente HTTP hacia product-service: consulta productos y aplica ajustes de stock. */
@Component
public class ProductClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${services.product-service.url}")
    private String productServiceUrl;

    public ProductClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public ProductoDto obtenerProducto(Long productoId, String bearerToken) {
        try {
            HttpEntity<Void> entity = new HttpEntity<>(authHeaders(bearerToken));
            return restTemplate.exchange(
                    productServiceUrl + "/api/productos/" + productoId,
                    HttpMethod.GET, entity, ProductoDto.class).getBody();
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ResourceNotFoundException("Producto no encontrado con id " + productoId);
        } catch (RestClientException ex) {
            throw new BusinessException("No fue posible comunicarse con product-service: " + ex.getMessage());
        }
    }

    public ProductoDto ajustarStock(Long productoId, String tipoMovimiento, Integer cantidad, String bearerToken) {
        try {
            HttpHeaders headers = authHeaders(bearerToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> body = Map.of("tipoMovimiento", tipoMovimiento, "cantidad", cantidad);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            return restTemplate.exchange(
                    productServiceUrl + "/api/productos/" + productoId + "/ajustar-stock",
                    HttpMethod.POST, entity, ProductoDto.class).getBody();
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ResourceNotFoundException("Producto no encontrado con id " + productoId);
        } catch (HttpStatusCodeException ex) {
            throw new BusinessException(extractMessage(ex));
        } catch (RestClientException ex) {
            throw new BusinessException("No fue posible comunicarse con product-service: " + ex.getMessage());
        }
    }

    private String extractMessage(HttpStatusCodeException ex) {
        try {
            JsonNode node = objectMapper.readTree(ex.getResponseBodyAsString());
            if (node.has("message")) {
                return node.get("message").asText();
            }
        } catch (Exception ignored) {
            // se usa el mensaje por defecto si el cuerpo no es parseable
        }
        return "product-service respondio con error: " + ex.getStatusCode();
    }

    private HttpHeaders authHeaders(String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", bearerToken);
        return headers;
    }
}
