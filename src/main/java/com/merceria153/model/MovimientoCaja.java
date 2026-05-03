package com.merceria153.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "movimientos_caja")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MovimientoCaja {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String cajaId;

    @Column(nullable = false)
    private String tipo; // apertura, cierre, ingreso, retiro, venta, gasto

    @Column(nullable = false)
    private BigDecimal monto;

    private String medioPago;

    @Column(nullable = false)
    private String descripcion;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String usuarioNombre;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}