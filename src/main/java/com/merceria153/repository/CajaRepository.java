package com.merceria153.repository;

import com.merceria153.model.Caja;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CajaRepository extends JpaRepository<Caja, String> {
    List<Caja> findByEstadoOrderByCreatedAtDesc(String estado);
    Optional<Caja> findFirstByEstadoOrderByCreatedAtDesc(String estado);
}