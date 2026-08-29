package com.inventario.product.config;

import com.inventario.product.entity.Producto;
import com.inventario.product.repository.ProductoRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class DataSeeder implements CommandLineRunner {

    private final ProductoRepository productoRepository;

    public DataSeeder(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    @Override
    public void run(String... args) {
        if (productoRepository.count() == 0) {
            productoRepository.save(Producto.builder()
                    .codigo("PROD-001").nombre("Notebook 14 pulgadas").descripcion("Notebook para oficina")
                    .categoriaId(1L).precio(new BigDecimal("450000")).stock(15).stockMinimo(5).build());
            productoRepository.save(Producto.builder()
                    .codigo("PROD-002").nombre("Mouse inalambrico").descripcion("Mouse optico USB")
                    .categoriaId(1L).precio(new BigDecimal("8990")).stock(40).stockMinimo(10).build());
            productoRepository.save(Producto.builder()
                    .codigo("PROD-003").nombre("Resma papel carta").descripcion("Resma 500 hojas")
                    .categoriaId(2L).precio(new BigDecimal("3490")).stock(8).stockMinimo(10).build());
            productoRepository.save(Producto.builder()
                    .codigo("PROD-004").nombre("Alcohol gel 500ml").descripcion("Alcohol gel antibacterial")
                    .categoriaId(3L).precio(new BigDecimal("2500")).stock(3).stockMinimo(15).build());
        }
    }
}
