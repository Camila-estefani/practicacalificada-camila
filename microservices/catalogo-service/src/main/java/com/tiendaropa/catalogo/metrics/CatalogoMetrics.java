package com.tiendaropa.catalogo.metrics;

import com.tiendaropa.catalogo.repository.ProductoRepository;
import io.micrometer.core.instrument.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Registro centralizado de métricas del catálogo.
 * - Counter: catalogo_consultas_total, catalogo_stock_reducido_total
 * - Gauge:   catalogo_productos_disponibles, catalogo_delay_artificial_ms
 * - Timer:   catalogo_consulta_producto_duration
 */
@Component
public class CatalogoMetrics {

    private final MeterRegistry registry;
    private final ProductoRepository productoRepository;

    @Value("${catalogo.artificial.delay.ms:0}")
    private long delayMs;

    // Counters
    private Counter consultasTotal;
    private Counter stockReducidoTotal;
    private Counter consultasCategoriaTotal;
    private Counter erroresTotal;

    // Timer
    private Timer consultaProductoTimer;

    @Autowired
    public CatalogoMetrics(MeterRegistry registry, ProductoRepository productoRepository) {
        this.registry = registry;
        this.productoRepository = productoRepository;
    }

    @PostConstruct
    public void init() {
        // Counter: total de consultas al catálogo
        consultasTotal = Counter.builder("catalogo_consultas_total")
                .description("Número total de consultas al catálogo de productos")
                .tag("servicio", "catalogo-service")
                .register(registry);

        // Counter: veces que se redujo stock (compra confirmada)
        stockReducidoTotal = Counter.builder("catalogo_stock_reducido_total")
                .description("Número de veces que se redujo stock por una compra")
                .tag("servicio", "catalogo-service")
                .register(registry);

        // Counter: consultas por categoría
        consultasCategoriaTotal = Counter.builder("catalogo_consultas_categoria_total")
                .description("Número de consultas filtradas por categoría")
                .tag("servicio", "catalogo-service")
                .register(registry);

        // Counter: errores en catálogo
        erroresTotal = Counter.builder("catalogo_errores_total")
                .description("Número de errores en el catálogo")
                .tag("servicio", "catalogo-service")
                .register(registry);

        // Gauge: productos disponibles en tiempo real
        Gauge.builder("catalogo_productos_disponibles", productoRepository, ProductoRepository::contarDisponibles)
                .description("Número de productos actualmente con stock > 0")
                .tag("servicio", "catalogo-service")
                .register(registry);

        // Gauge: delay artificial (para Casuística 2)
        Gauge.builder("catalogo_delay_artificial_ms", this, CatalogoMetrics::getDelayMs)
                .description("Delay artificial configurado en ms para simular lentitud")
                .tag("servicio", "catalogo-service")
                .register(registry);

        // Timer: latencia de consulta de producto por ID
        consultaProductoTimer = Timer.builder("catalogo_consulta_producto_duration")
                .description("Tiempo de respuesta al consultar un producto por ID")
                .tag("servicio", "catalogo-service")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    public Counter getConsultasTotal() { return consultasTotal; }
    public Counter getStockReducidoTotal() { return stockReducidoTotal; }
    public Counter getConsultasCategoriaTotal() { return consultasCategoriaTotal; }
    public Counter getErroresTotal() { return erroresTotal; }
    public Timer getConsultaProductoTimer() { return consultaProductoTimer; }

    public double getDelayMs() { return delayMs; }
    public void setDelayMs(long delayMs) { this.delayMs = delayMs; }
}
