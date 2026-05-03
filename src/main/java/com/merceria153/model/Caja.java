package com.merceria153.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cajas")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Caja {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String nombre = "Caja Principal";

    @Column(nullable = false)
    private String estado = "cerrada"; // "abierta", "pendiente_cierre" o "cerrada"

    private BigDecimal montoApertura = BigDecimal.ZERO;
    private BigDecimal montoSistema = BigDecimal.ZERO;
    private BigDecimal montoRealTemporal = BigDecimal.ZERO;
    private BigDecimal diferencia = BigDecimal.ZERO;

    @Column(nullable = false)
    private String usuarioApertura;

    private LocalDateTime fechaApertura;

    private String usuarioCierre;
    private LocalDateTime fechaCierre;

    private LocalDateTime createdAt = LocalDateTime.now();
}