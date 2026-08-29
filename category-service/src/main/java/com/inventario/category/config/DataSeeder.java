package com.inventario.category.config;

import com.inventario.category.entity.Categoria;
import com.inventario.category.repository.CategoriaRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {

    private final CategoriaRepository categoriaRepository;

    public DataSeeder(CategoriaRepository categoriaRepository) {
        this.categoriaRepository = categoriaRepository;
    }

    @Override
    public void run(String... args) {
        if (categoriaRepository.count() == 0) {
            categoriaRepository.save(Categoria.builder().nombre("Electronica").descripcion("Articulos electronicos y computacion").build());
            categoriaRepository.save(Categoria.builder().nombre("Oficina").descripcion("Utiles e insumos de oficina").build());
            categoriaRepository.save(Categoria.builder().nombre("Aseo").descripcion("Articulos de limpieza y aseo").build());
        }
    }
}
