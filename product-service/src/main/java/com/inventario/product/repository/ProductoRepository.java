package com.inventario.product.repository;

import com.inventario.product.entity.Producto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductoRepository extends JpaRepository<Producto, Long> {
    boolean existsByCodigoIgnoreCase(String codigo);
    Optional<Producto> findByCodigoIgnoreCase(String codigo);
}
