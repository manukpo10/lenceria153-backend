package com.merceria153.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "ventas")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Venta {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    @Convert(converter = VentaItemsConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<VentaItem> items;

    @Column(nullable = false)
    private BigDecimal subtotal;

    private BigDecimal descuento = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal total;

    @Column(nullable = false)
    private String medioPago;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String usuarioNombre;

    private LocalDateTime createdAt = LocalDateTime.now();

    private Long numeroVenta;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VentaItem {
        private String productoId;
        private String codigo;
        private String descripcion;
        private Integer cantidad;
        private BigDecimal precio;
        private BigDecimal subtotal;
    }
}