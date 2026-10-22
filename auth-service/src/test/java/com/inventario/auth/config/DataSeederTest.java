package com.inventario.auth.config;

import com.inventario.auth.entity.Usuario;
import com.inventario.auth.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataSeederTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    private DataSeeder dataSeeder;

    @BeforeEach
    void setUp() {
        dataSeeder = new DataSeeder(usuarioRepository);
    }

    @Test
    void run_conBaseDeDatosVacia_debeCrearAdminYOperador() {
        when(usuarioRepository.existsByUsername("admin")).thenReturn(false);
        when(usuarioRepository.existsByUsername("operador")).thenReturn(false);

        dataSeeder.run();

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(Usuario::getUsername)
                .containsExactlyInAnyOrder("admin", "operador");
    }

    @Test
    void run_conUsuariosYaExistentes_noDebeCrearNada() {
        when(usuarioRepository.existsByUsername("admin")).thenReturn(true);
        when(usuarioRepository.existsByUsername("operador")).thenReturn(true);

        dataSeeder.run();

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void run_conSoloAdminExistente_debeCrearSoloAlOperador() {
        when(usuarioRepository.existsByUsername("admin")).thenReturn(true);
        when(usuarioRepository.existsByUsername("operador")).thenReturn(false);

        dataSeeder.run();

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("operador");
    }
}
