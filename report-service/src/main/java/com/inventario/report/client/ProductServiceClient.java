package com.inventario.report.client;

import com.inventario.report.dto.ProductoDto;
import com.inventario.report.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Component
public class ProductServiceClient {

    private final RestTemplate restTemplate;

    @Value("${services.product-service.url}")
    private String productServiceUrl;

    public ProductServiceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public List<ProductoDto> listarProductos(String bearerToken) {
        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(bearerToken));
        ProductoDto[] productos = restTemplate.exchange(
                productServiceUrl + "/api/productos",
                HttpMethod.GET, entity, ProductoDto[].class).getBody();
        return productos != null ? List.of(productos) : List.of();
    }

    public ProductoDto obtenerProducto(Long id, String bearerToken) {
        try {
            HttpEntity<Void> entity = new HttpEntity<>(authHeaders(bearerToken));
            return restTemplate.exchange(
                    productServiceUrl + "/api/productos/" + id,
                    HttpMethod.GET, entity, ProductoDto.class).getBody();
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ResourceNotFoundException("Producto no encontrado con id " + id);
        }
    }

    private HttpHeaders authHeaders(String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", bearerToken);
        return headers;
    }
}
