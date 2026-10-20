package com.inventario.category.repository;

import com.inventario.category.entity.Categoria;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class CategoriaRepositoryTest {

    @Autowired
    private CategoriaRepository repository;

    @Test
    void guardarCategoria_debePersistirCorrectamente() {
        Categoria categoria = Categoria.builder().nombre("Hogar").descripcion("Articulos de hogar").activo(true).build();

        Categoria guardada = repository.save(categoria);

        assertThat(guardada.getId()).isNotNull();
        assertThat(repository.existsByNombreIgnoreCase("hogar")).isTrue();
    }

    @Test
    void guardarDosCategoriasConElMismoNombre_debeFallarPorRestriccionUnica() {
        repository.saveAndFlush(Categoria.builder().nombre("Oficina").activo(true).build());

        assertThatThrownBy(() -> repository.saveAndFlush(Categoria.builder().nombre("Oficina").activo(true).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
