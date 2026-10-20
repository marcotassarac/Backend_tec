package com.inventario.category.service;

import com.inventario.category.dto.CategoriaRequest;
import com.inventario.category.dto.CategoriaResponse;
import com.inventario.category.entity.Categoria;
import com.inventario.category.exception.BusinessException;
import com.inventario.category.exception.ResourceNotFoundException;
import com.inventario.category.repository.CategoriaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoriaServiceTest {

    @Mock
    private CategoriaRepository categoriaRepository;

    @InjectMocks
    private CategoriaService categoriaService;

    @Test
    void crear_conNombreNuevo_debeGuardarLaCategoria() {
        CategoriaRequest request = new CategoriaRequest("Electronica", "Articulos electronicos", true);
        when(categoriaRepository.existsByNombreIgnoreCase("Electronica")).thenReturn(false);
        when(categoriaRepository.save(any(Categoria.class))).thenAnswer(inv -> {
            Categoria c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });

        CategoriaResponse response = categoriaService.crear(request);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getNombre()).isEqualTo("Electronica");
    }

    @Test
    void crear_conNombreYaExistente_debeLanzarBusinessException() {
        CategoriaRequest request = new CategoriaRequest("Electronica", null, true);
        when(categoriaRepository.existsByNombreIgnoreCase("Electronica")).thenReturn(true);

        assertThatThrownBy(() -> categoriaService.crear(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ya existe una categoria");
    }

    @Test
    void eliminar_conIdInexistente_debeLanzarResourceNotFoundException() {
        when(categoriaRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoriaService.eliminar(404L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    /**
     * DEFECTO REAL DETECTADO (mismo patron que en product-service): actualizar() nunca
     * verifica existsByNombreIgnoreCase, asi que permite dejar dos categorias con el
     * mismo nombre. Este test documenta el comportamiento correcto esperado y FALLA hoy
     * porque la implementacion actual no valida el duplicado al editar.
     */
    @Test
    void actualizar_conNombreDuplicadoDeOtraCategoria_deberiaRechazarPeroNoLoHace() {
        Categoria categoriaAEditar = Categoria.builder().id(2L).nombre("Perifericos").activo(true).build();
        when(categoriaRepository.findById(2L)).thenReturn(Optional.of(categoriaAEditar));
        // "Electronica" ya es el nombre de OTRA categoria (id 1)
        when(categoriaRepository.existsByNombreIgnoreCase("Electronica")).thenReturn(true);
        when(categoriaRepository.save(any(Categoria.class))).thenAnswer(inv -> inv.getArgument(0));

        CategoriaRequest request = new CategoriaRequest("Electronica", null, true);

        assertThatThrownBy(() -> categoriaService.actualizar(2L, request))
                .as("actualizar() deberia validar nombre duplicado igual que crear(), pero hoy no lo hace")
                .isInstanceOf(BusinessException.class);
    }
}
