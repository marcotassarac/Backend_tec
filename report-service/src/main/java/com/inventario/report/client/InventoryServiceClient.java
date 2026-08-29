package com.inventario.report.client;

import com.inventario.report.dto.MovimientoDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Optional;

@Component
public class InventoryServiceClient {

    private final RestTemplate restTemplate;

    @Value("${services.inventory-service.url}")
    private String inventoryServiceUrl;

    public InventoryServiceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public List<MovimientoDto> listarMovimientos(Long productoId, String bearerToken) {
        HttpEntity<Void> entity = new HttpEntity<>(authHeaders(bearerToken));

        String url = UriComponentsBuilder.fromHttpUrl(inventoryServiceUrl + "/api/movimientos")
                .queryParamIfPresent("productoId", Optional.ofNullable(productoId))
                .toUriString();

        MovimientoDto[] movimientos = restTemplate.exchange(
                url, HttpMethod.GET, entity, MovimientoDto[].class).getBody();
        return movimientos != null ? List.of(movimientos) : List.of();
    }

    private HttpHeaders authHeaders(String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", bearerToken);
        return headers;
    }
}
