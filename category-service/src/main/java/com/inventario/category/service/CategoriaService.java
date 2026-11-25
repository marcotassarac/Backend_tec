package com.inventario.category.service;

import com.inventario.category.client.ProductClient;
import com.inventario.category.dto.CategoriaRequest;
import com.inventario.category.dto.CategoriaResponse;
import com.inventario.category.entity.Categoria;
import com.inventario.category.exception.BusinessException;
import com.inventario.category.exception.ResourceNotFoundException;
import com.inventario.category.repository.CategoriaRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;
    private final ProductClient productClient;

    public CategoriaService(CategoriaRepository categoriaRepository, ProductClient productClient) {
        this.categoriaRepository = categoriaRepository;
        this.productClient = productClient;
    }

    public List<CategoriaResponse> listar() {
        return categoriaRepository.findAll().stream().map(this::toResponse).toList();
    }

    public CategoriaResponse obtenerPorId(Long id) {
        return toResponse(buscarOFallar(id));
    }

    public CategoriaResponse crear(CategoriaRequest request) {
        if (categoriaRepository.existsByNombreIgnoreCase(request.getNombre())) {
            throw new BusinessException("Ya existe una categoria con ese nombre");
        }
        Categoria categoria = Categoria.builder()
                .nombre(request.getNombre())
                .descripcion(request.getDescripcion())
                .activo(request.getActivo() == null || request.getActivo())
                .build();
        return toResponse(categoriaRepository.save(categoria));
    }

    public CategoriaResponse actualizar(Long id, CategoriaRequest request) {
        Categoria categoria = buscarOFallar(id);
        if (categoriaRepository.existsByNombreIgnoreCaseAndIdNot(request.getNombre(), id)) {
            throw new BusinessException("Ya existe una categoria con ese nombre");
        }
        categoria.setNombre(request.getNombre());
        categoria.setDescripcion(request.getDescripcion());
        if (request.getActivo() != null) {
            categoria.setActivo(request.getActivo());
        }
        return toResponse(categoriaRepository.save(categoria));
    }

    public void eliminar(Long id, String bearerToken) {
        Categoria categoria = buscarOFallar(id);
        if (productClient.categoriaTieneProductos(id, bearerToken)) {
            throw new BusinessException("No se puede eliminar la categoria \"" + categoria.getNombre()
                    + "\" porque tiene productos asociados. Desactivala o reasigna sus productos primero");
        }
        categoriaRepository.delete(categoria);
    }

    private Categoria buscarOFallar(Long id) {
        return categoriaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria no encontrada con id " + id));
    }

    private CategoriaResponse toResponse(Categoria categoria) {
        return CategoriaResponse.builder()
                .id(categoria.getId())
                .nombre(categoria.getNombre())
                .descripcion(categoria.getDescripcion())
                .activo(categoria.isActivo())
                .fechaCreacion(categoria.getFechaCreacion())
                .build();
    }
}
