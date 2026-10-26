package com.tiendaropa.compras.metrics;

import com.tiendaropa.compras.repository.CompraRepository;
import io.micrometer.core.instrument.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Registro centralizado de métricas del servicio de compras.
 * - Counter: compras_realizadas_total, compras_rechazadas_total,
 *            compras_timeouts_catalogo_total, compras_errores_total
 * - Gauge:   compras_confirmadas_activas, compras_ingresos_total
 * - Timer:   compras_procesamiento_duration
 */
@Component
public class ComprasMetrics {

    private final MeterRegistry registry;
    private final CompraRepository compraRepository;

    // Counters
    private Counter comprasRealizadasTotal;
    private Counter comprasRechazadasTotal;
    private Counter timeoutsCatalogoTotal;
    private Counter erroresComunicacionTotal;

    // Timer
    private Timer procesamientoTimer;

    @Autowired
    public ComprasMetrics(MeterRegistry registry, CompraRepository compraRepository) {
        this.registry = registry;
        this.compraRepository = compraRepository;
    }

    @PostConstruct
    public void init() {
        // Counter: compras procesadas exitosamente
        comprasRealizadasTotal = Counter.builder("compras_realizadas_total")
                .description("Número total de compras confirmadas exitosamente")
                .tag("servicio", "compras-service")
                .register(registry);

        // Counter: compras rechazadas por stock insuficiente
        comprasRechazadasTotal = Counter.builder("compras_rechazadas_total")
                .description("Número de compras rechazadas por falta de stock u otros errores")
                .tag("servicio", "compras-service")
                .register(registry);

        // Counter: timeouts al comunicarse con catálogo
        timeoutsCatalogoTotal = Counter.builder("compras_timeouts_catalogo_total")
                .description("Número de timeouts al consultar el catalogo-service")
                .tag("servicio", "compras-service")
                .register(registry);

        // Counter: errores de comunicación con catálogo
        erroresComunicacionTotal = Counter.builder("compras_errores_comunicacion_total")
                .description("Número de errores de comunicación con el catalogo-service")
                .tag("servicio", "compras-service")
                .register(registry);

        // Gauge: compras confirmadas acumuladas (negocio)
        Gauge.builder("compras_confirmadas_activas", compraRepository,
                        repo -> repo.contarPorEstado("CONFIRMADA"))
                .description("Número de compras confirmadas en el sistema")
                .tag("servicio", "compras-service")
                .register(registry);

        // Gauge: ingresos totales acumulados en ventas confirmadas
        Gauge.builder("compras_ingresos_total", compraRepository, CompraRepository::totalVentas)
                .description("Suma total de ingresos por compras confirmadas")
                .tag("servicio", "compras-service")
                .register(registry);

        // Timer: tiempo de procesamiento de una compra completa
        procesamientoTimer = Timer.builder("compras_procesamiento_duration")
                .description("Tiempo total de procesamiento de una solicitud de compra")
                .tag("servicio", "compras-service")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    public Counter getComprasRealizadasTotal() { return comprasRealizadasTotal; }
    public Counter getComprasRechazadasTotal() { return comprasRechazadasTotal; }
    public Counter getTimeoutsCatalogoTotal() { return timeoutsCatalogoTotal; }
    public Counter getErroresComunicacionTotal() { return erroresComunicacionTotal; }
    public Timer getProcesamientoTimer() { return procesamientoTimer; }
}
