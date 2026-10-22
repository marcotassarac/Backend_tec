package com.inventario.product.config;

import com.inventario.product.repository.ProductoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataSeederTest {

    @Mock
    private ProductoRepository productoRepository;

    @Test
    void run_sinProductosExistentes_debeSembrarLosCuatroProductosDeEjemplo() throws Exception {
        when(productoRepository.count()).thenReturn(0L);
        DataSeeder seeder = new DataSeeder(productoRepository);

        seeder.run();

        verify(productoRepository, times(4)).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void run_conProductosYaExistentes_noDebeSembrarNada() throws Exception {
        when(productoRepository.count()).thenReturn(5L);
        DataSeeder seeder = new DataSeeder(productoRepository);

        seeder.run();

        verify(productoRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
