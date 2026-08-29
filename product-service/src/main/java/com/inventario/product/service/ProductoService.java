package com.inventario.product.service;

import com.inventario.product.client.CategoryClient;
import com.inventario.product.dto.AjusteStockRequest;
import com.inventario.product.dto.ProductoRequest;
import com.inventario.product.dto.ProductoResponse;
import com.inventario.product.entity.Producto;
import com.inventario.product.exception.BusinessException;
import com.inventario.product.exception.ResourceNotFoundException;
import com.inventario.product.repository.ProductoRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final CategoryClient categoryClient;

    public ProductoService(ProductoRepository productoRepository, CategoryClient categoryClient) {
        this.productoRepository = productoRepository;
        this.categoryClient = categoryClient;
    }

    public List<ProductoResponse> listar(String bearerToken) {
        return productoRepository.findAll().stream()
                .map(p -> toResponse(p, bearerToken))
                .toList();
    }

    public ProductoResponse obtenerPorId(Long id, String bearerToken) {
        return toResponse(buscarOFallar(id), bearerToken);
    }

    public ProductoResponse crear(ProductoRequest request) {
        if (productoRepository.existsByCodigoIgnoreCase(request.getCodigo())) {
            throw new BusinessException("Ya existe un producto con el codigo " + request.getCodigo());
        }
        Producto producto = Producto.builder()
                .codigo(request.getCodigo())
                .nombre(request.getNombre())
                .descripcion(request.getDescripcion())
                .categoriaId(request.getCategoriaId())
                .precio(request.getPrecio())
                .stock(request.getStock())
                .stockMinimo(request.getStockMinimo())
                .activo(request.getActivo() == null || request.getActivo())
                .build();
        return toResponse(productoRepository.save(producto), null);
    }

    public ProductoResponse actualizar(Long id, ProductoRequest request) {
        Producto producto = buscarOFallar(id);
        producto.setCodigo(request.getCodigo());
        producto.setNombre(request.getNombre());
        producto.setDescripcion(request.getDescripcion());
        producto.setCategoriaId(request.getCategoriaId());
        producto.setPrecio(request.getPrecio());
        producto.setStock(request.getStock());
        producto.setStockMinimo(request.getStockMinimo());
        if (request.getActivo() != null) {
            producto.setActivo(request.getActivo());
        }
        producto.setFechaActualizacion(LocalDateTime.now());
        return toResponse(productoRepository.save(producto), null);
    }

    public void eliminar(Long id) {
        Producto producto = buscarOFallar(id);
        productoRepository.delete(producto);
    }

    /** Usado por inventory-service para reflejar un movimiento de entrada/salida en el stock. */
    public ProductoResponse ajustarStock(Long id, AjusteStockRequest request) {
        Producto producto = buscarOFallar(id);
        String tipo = request.getTipoMovimiento().toUpperCase();

        if ("ENTRADA".equals(tipo)) {
            producto.setStock(producto.getStock() + request.getCantidad());
        } else if ("SALIDA".equals(tipo)) {
            if (producto.getStock() < request.getCantidad()) {
                throw new BusinessException("Stock insuficiente. Stock actual: " + producto.getStock());
            }
            producto.setStock(producto.getStock() - request.getCantidad());
        } else {
            throw new BusinessException("Tipo de movimiento invalido: " + request.getTipoMovimiento());
        }

        producto.setFechaActualizacion(LocalDateTime.now());
        return toResponse(productoRepository.save(producto), null);
    }

    private Producto buscarOFallar(Long id) {
        return productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con id " + id));
    }

    private ProductoResponse toResponse(Producto producto, String bearerToken) {
        String categoriaNombre = bearerToken != null
                ? categoryClient.obtenerNombreCategoria(producto.getCategoriaId(), bearerToken)
                : null;

        return ProductoResponse.builder()
                .id(producto.getId())
                .codigo(producto.getCodigo())
                .nombre(producto.getNombre())
                .descripcion(producto.getDescripcion())
                .categoriaId(producto.getCategoriaId())
                .categoriaNombre(categoriaNombre)
                .precio(producto.getPrecio())
                .stock(producto.getStock())
                .stockMinimo(producto.getStockMinimo())
                .stockBajo(producto.getStock() < producto.getStockMinimo())
                .activo(producto.isActivo())
                .fechaCreacion(producto.getFechaCreacion())
                .fechaActualizacion(producto.getFechaActualizacion())
                .build();
    }
}
