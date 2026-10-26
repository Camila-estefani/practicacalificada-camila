package com.tiendaropa.catalogo.model;

public class Producto {

    private String id;
    private String nombre;
    private String categoria;   // e.g. "camisetas", "pantalones", "vestidos"
    private double precio;
    private int stock;
    private String talla;       // XS, S, M, L, XL
    private boolean disponible;

    public Producto() {}

    public Producto(String id, String nombre, String categoria,
                    double precio, int stock, String talla) {
        this.id = id;
        this.nombre = nombre;
        this.categoria = categoria;
        this.precio = precio;
        this.stock = stock;
        this.talla = talla;
        this.disponible = stock > 0;
    }

    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public double getPrecio() { return precio; }
    public void setPrecio(double precio) { this.precio = precio; }

    public int getStock() { return stock; }
    public void setStock(int stock) {
        this.stock = stock;
        this.disponible = stock > 0;
    }

    public String getTalla() { return talla; }
    public void setTalla(String talla) { this.talla = talla; }

    public boolean isDisponible() { return disponible; }
    public void setDisponible(boolean disponible) { this.disponible = disponible; }
}
