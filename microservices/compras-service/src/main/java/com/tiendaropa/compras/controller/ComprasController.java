package com.tiendaropa.compras.controller;

import com.tiendaropa.compras.metrics.ComprasMetrics;
import com.tiendaropa.compras.model.Compra;
import com.tiendaropa.compras.model.ProductoDTO;
import com.tiendaropa.compras.repository.CompraRepository;
import com.tiendaropa.compras.service.CatalogoClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/compras")
public class ComprasController {

    private static final Logger log = LoggerFactory.getLogger(ComprasController.class);

    @Autowired
    private CompraRepository compraRepository;

    @Autowired
    private CatalogoClient catalogoClient;

    @Autowired
    private ComprasMetrics comprasMetrics;

    /**
     * POST /api/compras
     * Crea una nueva compra. Consulta el catálogo para validar disponibilidad.
     * Esta es la operación principal que demuestra comunicación entre servicios.
     */
    @PostMapping
    public ResponseEntity<?> realizarCompra(@RequestBody Compra solicitud) {
        return comprasMetrics.getProcesamientoTimer().record(() -> {

            log.info("Procesando compra: producto={}, cantidad={}, cliente={}",
                    solicitud.getProductoId(), solicitud.getCantidad(), solicitud.getClienteNombre());

            // 1. Consultar catalogo-service para verificar el producto
            Optional<ProductoDTO> productoOpt = catalogoClient.obtenerProducto(solicitud.getProductoId());

            if (productoOpt.isEmpty()) {
                // No se pudo comunicar con el catálogo (timeout o error)
                Compra compraRechazada = compraRepository.crear(solicitud);
                compraRechazada.setEstado("RECHAZADA");
                compraRechazada.setMotivoRechazo("catalogo-service no disponible");
                comprasMetrics.getComprasRechazadasTotal().increment();

                log.warn("Compra rechazada: catálogo no disponible. productoId={}",
                        solicitud.getProductoId());

                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of(
                                "error", "El servicio de catálogo no está disponible",
                                "compra", compraRechazada
                        ));
            }

            ProductoDTO producto = productoOpt.get();

            // 2. Verificar stock suficiente
            if (!producto.isDisponible() || producto.getStock() < solicitud.getCantidad()) {
                Compra compraRechazada = compraRepository.crear(solicitud);
                compraRechazada.setEstado("RECHAZADA");
                compraRechazada.setMotivoRechazo("Stock insuficiente. Disponible: " + producto.getStock());
                comprasMetrics.getComprasRechazadasTotal().increment();

                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of(
                                "error", "Stock insuficiente",
                                "stockDisponible", producto.getStock(),
                                "solicitado", solicitud.getCantidad(),
                                "compra", compraRechazada
                        ));
            }

            // 3. Confirmar compra
            Compra compra = compraRepository.crear(solicitud);
            compra.setPrecioUnitario(producto.getPrecio());
            compra.setEstado("CONFIRMADA");

            // 4. Reducir stock en el catálogo
            boolean stockReducido = catalogoClient.reducirStock(solicitud.getProductoId(),
                    solicitud.getCantidad());
            if (!stockReducido) {
                log.warn("No se pudo reducir el stock para producto={}", solicitud.getProductoId());
            }

            comprasMetrics.getComprasRealizadasTotal().increment();
            log.info("Compra confirmada: id={}, total={}", compra.getId(), compra.getTotal());

            return ResponseEntity.status(HttpStatus.CREATED).body(compra);
        });
    }

    /**
     * GET /api/compras
     * Lista todas las compras registradas.
     */
    @GetMapping
    public ResponseEntity<Collection<Compra>> listarCompras() {
        return ResponseEntity.ok(compraRepository.findAll());
    }

    /**
     * GET /api/compras/{id}
     * Consulta una compra por su ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Compra> obtenerCompra(@PathVariable String id) {
        return compraRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/compras/estado/{estado}
     * Filtra compras por estado: PENDIENTE, CONFIRMADA, RECHAZADA.
     */
    @GetMapping("/estado/{estado}")
    public ResponseEntity<List<Compra>> listarPorEstado(@PathVariable String estado) {
        return ResponseEntity.ok(compraRepository.findByEstado(estado));
    }

    /**
     * GET /api/compras/resumen
     * Devuelve un resumen de negocio: totales por estado e ingresos.
     */
    @GetMapping("/resumen")
    public ResponseEntity<Map<String, Object>> resumen() {
        return ResponseEntity.ok(Map.of(
                "confirmadas", compraRepository.contarPorEstado("CONFIRMADA"),
                "rechazadas", compraRepository.contarPorEstado("RECHAZADA"),
                "pendientes", compraRepository.contarPorEstado("PENDIENTE"),
                "ingresosTotales", compraRepository.totalVentas()
        ));
    }

    /**
     * GET /api/compras/health
     * Health check propio, responde rápido sin dependencias externas.
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("compras-service UP");
    }
}
