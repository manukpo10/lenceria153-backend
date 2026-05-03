package com.merceria153.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "productos")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Producto {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true)
    private String codigo;

    @Column(nullable = false)
    private String descripcion;

    @Column(nullable = false)
    private String rubro;

    private BigDecimal costo;

    @Column(nullable = false)
    private BigDecimal precio;

    private BigDecimal precioUnidad;
    private BigDecimal precioUnidadLista;
    private BigDecimal precioUnidadVenta;

    private BigDecimal precioVenta;

    @Column(nullable = false)
    private Integer pack = 1;

    private Integer unidad = 1;

    @Column(nullable = false)
    private Integer stock = 0;

    @Column(nullable = false)
    private Boolean activo = true;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;
}