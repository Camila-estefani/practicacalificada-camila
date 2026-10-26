package com.tiendaropa.compras.model;

/**
 * DTO para recibir datos del catálogo-service vía HTTP.
 */
public class ProductoDTO {

    private String id;
    private String nombre;
    private String categoria;
    private double precio;
    private int stock;
    private String talla;
    private boolean disponible;

    public ProductoDTO() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public double getPrecio() { return precio; }
    public void setPrecio(double precio) { this.precio = precio; }

    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }

    public String getTalla() { return talla; }
    public void setTalla(String talla) { this.talla = talla; }

    public boolean isDisponible() { return disponible; }
    public void setDisponible(boolean disponible) { this.disponible = disponible; }
}
