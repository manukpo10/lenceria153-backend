package com.merceria153.repository;

import com.merceria153.model.Venta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.util.List;

public interface VentaRepository extends JpaRepository<Venta, String> {
    List<Venta> findByFechaBetweenOrderByFechaDesc(LocalDateTime desde, LocalDateTime hasta);
    List<Venta> findByFechaGreaterThanEqualOrderByFechaDesc(LocalDateTime fecha);
    @Query("SELECT COALESCE(MAX(v.numeroVenta), 0) FROM Venta v")
    Long findMaxNumeroVenta();
}