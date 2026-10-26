package com.tiendaropa.compras.service;

import com.tiendaropa.compras.config.WebClientConfig;
import com.tiendaropa.compras.metrics.ComprasMetrics;
import com.tiendaropa.compras.model.ProductoDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.concurrent.TimeoutException;

/**
 * Cliente HTTP para comunicarse con el catalogo-service.
 * Maneja timeouts y errores de forma controlada.
 */
@Service
public class CatalogoClient {

    private static final Logger log = LoggerFactory.getLogger(CatalogoClient.class);

    @Autowired
    private WebClient catalogoWebClient;

    @Autowired
    private WebClientConfig webClientConfig;

    @Autowired
    private ComprasMetrics comprasMetrics;

    /**
     * Consulta un producto por ID al catalogo-service.
     * Si no responde en el timeout configurado, registra el error y retorna vacío.
     */
    public Optional<ProductoDTO> obtenerProducto(String productoId) {
        try {
            ProductoDTO producto = catalogoWebClient
                    .get()
                    .uri("/api/catalogo/productos/{id}", productoId)
                    .retrieve()
                    .bodyToMono(ProductoDTO.class)
                    .timeout(webClientConfig.getTimeout())
                    .onErrorResume(TimeoutException.class, ex -> {
                        log.error("[CatalogoClient] TIMEOUT al consultar producto {}. " +
                                "El catalogo-service no respondió en {}s",
                                productoId, webClientConfig.getTimeout().getSeconds());
                        comprasMetrics.getTimeoutsCatalogoTotal().increment();
                        return Mono.empty();
                    })
                    .onErrorResume(WebClientRequestException.class, ex -> {
                        log.error("[CatalogoClient] ERROR de conexión al catalogo-service: {}",
                                ex.getMessage());
                        comprasMetrics.getErroresComunicacionTotal().increment();
                        return Mono.empty();
                    })
                    .onErrorResume(ex -> {
                        log.error("[CatalogoClient] ERROR inesperado consultando producto {}: {}",
                                productoId, ex.getMessage());
                        comprasMetrics.getErroresComunicacionTotal().increment();
                        return Mono.empty();
                    })
                    .block();

            return Optional.ofNullable(producto);

        } catch (Exception e) {
            log.error("[CatalogoClient] Excepción no controlada: {}", e.getMessage());
            comprasMetrics.getErroresComunicacionTotal().increment();
            return Optional.empty();
        }
    }

    /**
     * Solicita reducción de stock al catalogo-service tras confirmar una compra.
     */
    public boolean reducirStock(String productoId, int cantidad) {
        try {
            String resultado = catalogoWebClient
                    .post()
                    .uri("/api/catalogo/productos/{id}/reducir-stock?cantidad={cantidad}",
                            productoId, cantidad)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(webClientConfig.getTimeout())
                    .onErrorResume(TimeoutException.class, ex -> {
                        log.error("[CatalogoClient] TIMEOUT reduciendo stock de {}", productoId);
                        comprasMetrics.getTimeoutsCatalogoTotal().increment();
                        return Mono.just("ERROR_TIMEOUT");
                    })
                    .onErrorResume(ex -> {
                        log.error("[CatalogoClient] ERROR reduciendo stock: {}", ex.getMessage());
                        comprasMetrics.getErroresComunicacionTotal().increment();
                        return Mono.just("ERROR");
                    })
                    .block();

            return resultado != null && !resultado.startsWith("ERROR");

        } catch (Exception e) {
            log.error("[CatalogoClient] Excepción reduciendo stock: {}", e.getMessage());
            return false;
        }
    }
}
