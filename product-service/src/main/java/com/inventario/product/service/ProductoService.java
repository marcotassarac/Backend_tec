package com.inventario.product.service;

import com.inventario.product.client.CategoryClient;
import com.inventario.product.config.RabbitMQConfig;
import com.inventario.product.dto.AjusteStockRequest;
import com.inventario.product.dto.ProductoRequest;
import com.inventario.product.dto.ProductoResponse;
import com.inventario.product.entity.Producto;
import com.inventario.product.exception.BusinessException;
import com.inventario.product.exception.ResourceNotFoundException;
import com.inventario.product.repository.ProductoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProductoService {

    private static final Logger log = LoggerFactory.getLogger(ProductoService.class);

    private final ProductoRepository productoRepository;
    private final CategoryClient categoryClient;
    private final RabbitTemplate rabbitTemplate;

    public ProductoService(ProductoRepository productoRepository, CategoryClient categoryClient,
                           RabbitTemplate rabbitTemplate) {
        this.productoRepository = productoRepository;
        this.categoryClient = categoryClient;
        this.rabbitTemplate = rabbitTemplate;
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
        Producto guardado = productoRepository.save(producto);
        publicarEvento("PRODUCTO_CREADO", guardado);
        return toResponse(guardado, null);
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

    /**
     * Simula un evento de dominio enviandolo a cola.inventario y cola.notificaciones
     * por pedidos.direct. Si RabbitMQ no esta disponible solo se registra el error,
     * para no hacer fallar una operacion que ya quedo guardada.
     */
    private void publicarEvento(String tipo, Producto producto) {
        Map<String, Object> evento = new LinkedHashMap<>();
        evento.put("tipo", tipo);
        evento.put("productoId", producto.getId());
        evento.put("codigo", producto.getCodigo());
        evento.put("nombre", producto.getNombre());
        evento.put("stock", producto.getStock());
        evento.put("fecha", LocalDateTime.now().toString());

        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.DIRECT_EXCHANGE, RabbitMQConfig.COLA_INVENTARIO, evento);
            rabbitTemplate.convertAndSend(RabbitMQConfig.DIRECT_EXCHANGE, RabbitMQConfig.COLA_NOTIFICACIONES, evento);
            log.info("Evento {} publicado para producto {}", tipo, producto.getId());
        } catch (AmqpException ex) {
            log.error("No se pudo publicar el evento {} del producto {}: {}", tipo, producto.getId(), ex.getMessage());
        }
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
