package com.tiendaropa.catalogo.controller;

import com.tiendaropa.catalogo.metrics.CatalogoMetrics;
import com.tiendaropa.catalogo.model.Producto;
import com.tiendaropa.catalogo.repository.ProductoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/catalogo")
public class CatalogoController {

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private CatalogoMetrics catalogoMetrics;

    @Value("${catalogo.artificial.delay.ms:0}")
    private long delayMs;

    /**
     * GET /api/catalogo/productos
     * Lista todos los productos del catálogo.
     */
    @GetMapping("/productos")
    public ResponseEntity<Collection<Producto>> listarProductos() {
        aplicarDelayArtificial();
        catalogoMetrics.getConsultasTotal().increment();
        return ResponseEntity.ok(productoRepository.findAll());
    }

    /**
     * GET /api/catalogo/productos/{id}
     * Consulta un producto por su ID. Usado por compras-service.
     */
    @GetMapping("/productos/{id}")
    public ResponseEntity<Producto> obtenerProducto(@PathVariable String id) {
        return catalogoMetrics.getConsultaProductoTimer().record(() -> {
            aplicarDelayArtificial();
            catalogoMetrics.getConsultasTotal().increment();
            Optional<Producto> producto = productoRepository.findById(id);
            if (producto.isPresent()) {
                return ResponseEntity.ok(producto.get());
            } else {
                catalogoMetrics.getErroresTotal().increment();
                return ResponseEntity.<Producto>notFound().build();
            }
        });
    }

    /**
     * GET /api/catalogo/productos/categoria/{categoria}
     * Filtra productos por categoría.
     */
    @GetMapping("/productos/categoria/{categoria}")
    public ResponseEntity<List<Producto>> listarPorCategoria(@PathVariable String categoria) {
        aplicarDelayArtificial();
        catalogoMetrics.getConsultasCategoriaTotal().increment();
        List<Producto> resultado = productoRepository.findByCategoria(categoria);
        return ResponseEntity.ok(resultado);
    }

    /**
     * POST /api/catalogo/productos
     * Agrega un nuevo producto al catálogo.
     */
    @PostMapping("/productos")
    public ResponseEntity<Producto> agregarProducto(@RequestBody Producto producto) {
        Producto nuevo = productoRepository.agregar(producto);
        return ResponseEntity.status(HttpStatus.CREATED).body(nuevo);
    }

    /**
     * PUT /api/catalogo/productos/{id}
     * Actualiza un producto existente.
     */
    @PutMapping("/productos/{id}")
    public ResponseEntity<Producto> actualizarProducto(@PathVariable String id,
                                                        @RequestBody Producto datos) {
        Optional<Producto> actualizado = productoRepository.actualizar(id, datos);
        if (actualizado.isPresent()) {
            return ResponseEntity.ok(actualizado.get());
        }
        catalogoMetrics.getErroresTotal().increment();
        return ResponseEntity.notFound().build();
    }

    /**
     * POST /api/catalogo/productos/{id}/reducir-stock
     * Reduce el stock de un producto. Llamado internamente por compras-service.
     */
    @PostMapping("/productos/{id}/reducir-stock")
    public ResponseEntity<String> reducirStock(@PathVariable String id,
                                                @RequestParam int cantidad) {
        aplicarDelayArtificial();
        boolean exito = productoRepository.reducirStock(id, cantidad);
        if (exito) {
            catalogoMetrics.getStockReducidoTotal().increment();
            return ResponseEntity.ok("Stock reducido correctamente");
        } else {
            catalogoMetrics.getErroresTotal().increment();
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Stock insuficiente o producto no encontrado");
        }
    }

    /**
     * GET /api/catalogo/health
     * Health check propio, responde rápido sin dependencias externas.
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("catalogo-service UP");
    }

    // -----------------------------------------------------------------------
    // Método privado: aplica delay artificial si está configurado
    // -----------------------------------------------------------------------
    private void aplicarDelayArtificial() {
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
