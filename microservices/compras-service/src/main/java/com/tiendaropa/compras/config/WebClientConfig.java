package com.tiendaropa.compras.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    @Value("${catalogo.service.url:http://localhost:8080}")
    private String catalogoUrl;

    @Value("${catalogo.service.timeout.seconds:3}")
    private int timeoutSeconds;

    /**
     * WebClient configurado con:
     * - URL base del catalogo-service
     * - Timeout de respuesta configurable (por defecto 3 segundos)
     */
    @Bean
    public WebClient catalogoWebClient() {
        return WebClient.builder()
                .baseUrl(catalogoUrl)
                .build();
    }

    public Duration getTimeout() {
        return Duration.ofSeconds(timeoutSeconds);
    }
}
