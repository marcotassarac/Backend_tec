package com.inventario.category.service;

import com.inventario.category.client.ProductClient;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class CategoriaServiceTest {

    @Mock
    private CategoriaRepository categoriaRepository;

    @Mock
    private ProductClient productClient;

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

        assertThatThrownBy(() -> categoriaService.eliminar(404L, "Bearer token"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    /** DEF-02 (UAT Ciclo 1, CA-12): editar con el nombre de otra categoria se rechaza con mensaje claro. */
    @Test
    void actualizar_conNombreDuplicadoDeOtraCategoria_debeRechazarConMensajeClaro() {
        Categoria categoriaAEditar = Categoria.builder().id(2L).nombre("Perifericos").activo(true).build();
        when(categoriaRepository.findById(2L)).thenReturn(Optional.of(categoriaAEditar));
        when(categoriaRepository.existsByNombreIgnoreCaseAndIdNot("Electronica", 2L)).thenReturn(true);

        CategoriaRequest request = new CategoriaRequest("Electronica", null, true);

        assertThatThrownBy(() -> categoriaService.actualizar(2L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ya existe una categoria con ese nombre");
        verify(categoriaRepository, never()).save(any(Categoria.class));
    }

    @Test
    void listar_debeRetornarTodasLasCategoriasMapeadas() {
        Categoria c1 = Categoria.builder().id(1L).nombre("Electronica").activo(true).build();
        Categoria c2 = Categoria.builder().id(2L).nombre("Oficina").activo(true).build();
        when(categoriaRepository.findAll()).thenReturn(List.of(c1, c2));

        List<CategoriaResponse> resultado = categoriaService.listar();

        assertThat(resultado).hasSize(2);
        assertThat(resultado.get(0).getNombre()).isEqualTo("Electronica");
        assertThat(resultado.get(1).getNombre()).isEqualTo("Oficina");
    }

    @Test
    void obtenerPorId_conIdExistente_debeRetornarLaCategoria() {
        Categoria categoria = Categoria.builder().id(5L).nombre("Aseo").activo(true).build();
        when(categoriaRepository.findById(5L)).thenReturn(Optional.of(categoria));

        CategoriaResponse response = categoriaService.obtenerPorId(5L);

        assertThat(response.getId()).isEqualTo(5L);
        assertThat(response.getNombre()).isEqualTo("Aseo");
    }

    @Test
    void obtenerPorId_conIdInexistente_debeLanzarResourceNotFoundException() {
        when(categoriaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoriaService.obtenerPorId(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void actualizar_conNombreNoDuplicadoYActivoEnRequest_debeActualizarLaCategoria() {
        Categoria categoriaExistente = Categoria.builder().id(3L).nombre("Viejo").descripcion("desc vieja").activo(false).build();
        when(categoriaRepository.findById(3L)).thenReturn(Optional.of(categoriaExistente));
        when(categoriaRepository.save(any(Categoria.class))).thenAnswer(inv -> inv.getArgument(0));

        CategoriaRequest request = new CategoriaRequest("Nuevo", "desc nueva", true);

        CategoriaResponse response = categoriaService.actualizar(3L, request);

        assertThat(response.getNombre()).isEqualTo("Nuevo");
        assertThat(response.getDescripcion()).isEqualTo("desc nueva");
        assertThat(response.isActivo()).isTrue();
    }

    @Test
    void actualizar_conActivoNuloEnRequest_noDebeModificarElEstadoActivo() {
        Categoria categoriaExistente = Categoria.builder().id(4L).nombre("Viejo").activo(true).build();
        when(categoriaRepository.findById(4L)).thenReturn(Optional.of(categoriaExistente));
        when(categoriaRepository.save(any(Categoria.class))).thenAnswer(inv -> inv.getArgument(0));

        CategoriaRequest request = new CategoriaRequest("Nuevo", null, null);

        CategoriaResponse response = categoriaService.actualizar(4L, request);

        assertThat(response.isActivo()).isTrue();
    }

    @Test
    void crear_conActivoNulo_debeCrearComoActivaPorDefecto() {
        CategoriaRequest request = new CategoriaRequest("Jardin", null, null);
        when(categoriaRepository.save(any(Categoria.class))).thenAnswer(inv -> inv.getArgument(0));

        CategoriaResponse response = categoriaService.crear(request);

        assertThat(response.isActivo()).isTrue();
    }

    @Test
    void crear_conActivoFalse_debeCrearComoInactiva() {
        CategoriaRequest request = new CategoriaRequest("Descontinuados", null, false);
        when(categoriaRepository.save(any(Categoria.class))).thenAnswer(inv -> inv.getArgument(0));

        CategoriaResponse response = categoriaService.crear(request);

        assertThat(response.isActivo()).isFalse();
    }

    @Test
    void eliminar_conIdExistente_debeEliminarLaCategoria() {
        Categoria categoria = Categoria.builder().id(6L).nombre("Borrame").activo(true).build();
        when(categoriaRepository.findById(6L)).thenReturn(Optional.of(categoria));

        when(productClient.categoriaTieneProductos(6L, "Bearer token")).thenReturn(false);

        categoriaService.eliminar(6L, "Bearer token");

        verify(categoriaRepository).delete(categoria);
    }

    /** DEF-04 (UAT Ciclo 1, CA-13): una categoria con productos no se elimina. */
    @Test
    void eliminar_categoriaConProductos_debeRechazarSinBorrar() {
        Categoria categoria = Categoria.builder().id(7L).nombre("Ferreteria").activo(true).build();
        when(categoriaRepository.findById(7L)).thenReturn(Optional.of(categoria));
        when(productClient.categoriaTieneProductos(7L, "Bearer token")).thenReturn(true);

        assertThatThrownBy(() -> categoriaService.eliminar(7L, "Bearer token"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("tiene productos asociados");
        verify(categoriaRepository, never()).delete(any(Categoria.class));
    }
}
