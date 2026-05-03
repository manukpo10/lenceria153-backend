package com.merceria153.repository;

import com.merceria153.model.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ProductoRepository extends JpaRepository<Producto, String> {
    Optional<Producto> findByCodigo(String codigo);
    boolean existsByCodigo(String codigo);
    List<Producto> findByActivoTrue();
    List<Producto> findByActivoFalse();
    List<Producto> findByRubroAndActivoTrue(String rubro);
    List<Producto> findByDescripcionContainingIgnoreCaseAndActivoTrue(String descripcion);
    List<Producto> findByCodigoContainingAndActivoTrue(String codigo);
}