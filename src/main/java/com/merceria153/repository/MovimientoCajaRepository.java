package com.merceria153.repository;

import com.merceria153.model.MovimientoCaja;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MovimientoCajaRepository extends JpaRepository<MovimientoCaja, String> {
    List<MovimientoCaja> findByCajaIdOrderByCreatedAtDesc(String cajaId);
}