package com.tiendaropa.compras.repository;

import com.tiendaropa.compras.model.Compra;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class CompraRepository {

    private final Map<String, Compra> compras = new ConcurrentHashMap<>();
    private final AtomicLong contador = new AtomicLong(1);

    public Compra crear(Compra compra) {
        String id = "C" + String.format("%04d", contador.getAndIncrement());
        compra.setId(id);
        compras.put(id, compra);
        return compra;
    }

    public Collection<Compra> findAll() {
        return compras.values();
    }

    public Optional<Compra> findById(String id) {
        return Optional.ofNullable(compras.get(id));
    }

    public List<Compra> findByEstado(String estado) {
        List<Compra> resultado = new ArrayList<>();
        for (Compra c : compras.values()) {
            if (c.getEstado().equalsIgnoreCase(estado)) {
                resultado.add(c);
            }
        }
        return resultado;
    }

    public long contarPorEstado(String estado) {
        return compras.values().stream()
                .filter(c -> c.getEstado().equalsIgnoreCase(estado))
                .count();
    }

    public double totalVentas() {
        return compras.values().stream()
                .filter(c -> "CONFIRMADA".equals(c.getEstado()))
                .mapToDouble(Compra::getTotal)
                .sum();
    }
}
