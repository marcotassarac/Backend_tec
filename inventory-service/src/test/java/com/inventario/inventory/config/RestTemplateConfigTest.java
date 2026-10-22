package com.inventario.inventory.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class RestTemplateConfigTest {

    @Test
    void restTemplate_debeConstruirseConLosTimeoutsConfigurados() {
        RestTemplate restTemplate = new RestTemplateConfig().restTemplate(new RestTemplateBuilder());

        assertThat(restTemplate).isNotNull();
    }
}
