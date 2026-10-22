package com.inventario.category.config;

import com.inventario.category.entity.Categoria;
import com.inventario.category.repository.CategoriaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataSeederTest {

    @Mock
    private CategoriaRepository categoriaRepository;

    @InjectMocks
    private DataSeeder dataSeeder;

    @Test
    void run_conRepositorioVacio_debeSembrarLasTresCategoriasIniciales() {
        when(categoriaRepository.count()).thenReturn(0L);

        dataSeeder.run();

        verify(categoriaRepository, times(3)).save(any(Categoria.class));
    }

    @Test
    void run_conDatosYaExistentes_noDebeSembrarNada() {
        when(categoriaRepository.count()).thenReturn(5L);

        dataSeeder.run();

        verify(categoriaRepository, never()).save(any(Categoria.class));
    }
}
