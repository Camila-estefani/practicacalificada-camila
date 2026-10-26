package com.tiendaropa.catalogo.repository;

import com.tiendaropa.catalogo.model.Producto;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class ProductoRepository {

    private final Map<String, Producto> productos = new ConcurrentHashMap<>();

    public ProductoRepository() {
        // Datos iniciales en memoria
        agregar(new Producto("P001", "Camiseta Básica Blanca", "camisetas", 19.99, 50, "M"));
        agregar(new Producto("P002", "Camiseta Estampada Floral", "camisetas", 24.99, 30, "S"));
        agregar(new Producto("P003", "Pantalón Jean Skinny", "pantalones", 49.99, 20, "M"));
        agregar(new Producto("P004", "Pantalón Cargo Beige", "pantalones", 39.99, 15, "L"));
        agregar(new Producto("P005", "Vestido Verano Azul", "vestidos", 59.99, 10, "S"));
        agregar(new Producto("P006", "Vestido Casual Rojo", "vestidos", 44.99, 5, "M"));
        agregar(new Producto("P007", "Chaqueta Denim", "chaquetas", 79.99, 12, "L"));
        agregar(new Producto("P008", "Blusa Elegante Negra", "blusas", 34.99, 0, "XS")); // sin stock
    }

    public Collection<Producto> findAll() {
        return productos.values();
    }

    public Optional<Producto> findById(String id) {
        return Optional.ofNullable(productos.get(id));
    }

    public List<Producto> findByCategoria(String categoria) {
        List<Producto> resultado = new ArrayList<>();
        for (Producto p : productos.values()) {
            if (p.getCategoria().equalsIgnoreCase(categoria)) {
                resultado.add(p);
            }
        }
        return resultado;
    }

    public Producto agregar(Producto producto) {
        productos.put(producto.getId(), producto);
        return producto;
    }

    public Optional<Producto> actualizar(String id, Producto datos) {
        return Optional.ofNullable(productos.computeIfPresent(id, (k, existing) -> {
            existing.setNombre(datos.getNombre());
            existing.setPrecio(datos.getPrecio());
            existing.setStock(datos.getStock());
            existing.setTalla(datos.getTalla());
            return existing;
        }));
    }

    public boolean reducirStock(String id, int cantidad) {
        Producto p = productos.get(id);
        if (p != null && p.getStock() >= cantidad) {
            p.setStock(p.getStock() - cantidad);
            return true;
        }
        return false;
    }

    public long contarDisponibles() {
        return productos.values().stream().filter(Producto::isDisponible).count();
    }

    public long contarTotal() {
        return productos.size();
    }
}
