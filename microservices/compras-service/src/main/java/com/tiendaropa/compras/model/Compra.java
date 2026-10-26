package com.tiendaropa.compras.model;

import java.time.LocalDateTime;

public class Compra {

    private String id;
    private String productoId;
    private String clienteNombre;
    private int cantidad;
    private double precioUnitario;
    private double total;
    private String estado;    // PENDIENTE, CONFIRMADA, RECHAZADA
    private LocalDateTime fechaCreacion;
    private String motivoRechazo;

    public Compra() {}

    public Compra(String id, String productoId, String clienteNombre, int cantidad) {
        this.id = id;
        this.productoId = productoId;
        this.clienteNombre = clienteNombre;
        this.cantidad = cantidad;
        this.estado = "PENDIENTE";
        this.fechaCreacion = LocalDateTime.now();
    }

    // Getters y Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getProductoId() { return productoId; }
    public void setProductoId(String productoId) { this.productoId = productoId; }

    public String getClienteNombre() { return clienteNombre; }
    public void setClienteNombre(String clienteNombre) { this.clienteNombre = clienteNombre; }

    public int getCantidad() { return cantidad; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }

    public double getPrecioUnitario() { return precioUnitario; }
    public void setPrecioUnitario(double precioUnitario) {
        this.precioUnitario = precioUnitario;
        this.total = precioUnitario * this.cantidad;
    }

    public double getTotal() { return total; }
    public void setTotal(double total) { this.total = total; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public String getMotivoRechazo() { return motivoRechazo; }
    public void setMotivoRechazo(String motivoRechazo) { this.motivoRechazo = motivoRechazo; }
}
