package com.inventario.inventory.service;

import com.inventario.inventory.client.ProductClient;
import com.inventario.inventory.dto.MovimientoRequest;
import com.inventario.inventory.dto.MovimientoResponse;
import com.inventario.inventory.dto.ProductoDto;
import com.inventario.inventory.entity.MovimientoInventario;
import com.inventario.inventory.entity.TipoMovimiento;
import com.inventario.inventory.exception.BusinessException;
import com.inventario.inventory.exception.ResourceNotFoundException;
import com.inventario.inventory.event.MovimientoEvent;
import com.inventario.inventory.producer.MovimientoEventPublisher;
import com.inventario.inventory.repository.MovimientoInventarioRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Proceso de negocio: registrar un movimiento de inventario (entrada o salida).
 * Valida el producto y aplica el ajuste de stock en product-service antes de dejar
 * constancia del movimiento (kardex) en este microservicio.
 */
@Service
public class InventoryService {

    private final MovimientoInventarioRepository movimientoRepository;
    private final ProductClient productClient;
    private final MovimientoEventPublisher eventPublisher;

    public InventoryService(MovimientoInventarioRepository movimientoRepository, ProductClient productClient,
                            MovimientoEventPublisher eventPublisher) {
        this.movimientoRepository = movimientoRepository;
        this.productClient = productClient;
        this.eventPublisher = eventPublisher;
    }

    public MovimientoResponse registrarMovimiento(MovimientoRequest request, String bearerToken, String usuario) {
        TipoMovimiento tipo;
        try {
            tipo = TipoMovimiento.valueOf(request.getTipoMovimiento().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Tipo de movimiento invalido. Use ENTRADA o SALIDA");
        }

        ProductoDto producto = productClient.obtenerProducto(request.getProductoId(), bearerToken);
        if (producto == null) {
            throw new ResourceNotFoundException("Producto no encontrado con id " + request.getProductoId());
        }
        if (!producto.isActivo()) {
            throw new BusinessException("No se pueden registrar movimientos sobre un producto inactivo");
        }

        ProductoDto actualizado = productClient.ajustarStock(request.getProductoId(), tipo.name(), request.getCantidad(), bearerToken);

        MovimientoInventario movimiento = MovimientoInventario.builder()
                .productoId(producto.getId())
                .productoCodigo(producto.getCodigo())
                .productoNombre(producto.getNombre())
                .tipoMovimiento(tipo)
                .cantidad(request.getCantidad())
                .motivo(request.getMotivo())
                .usuario(usuario)
                .stockResultante(actualizado.getStock())
                .build();

        MovimientoInventario guardado = movimientoRepository.save(movimiento);
        eventPublisher.publicar(toEvent(guardado, producto.getStockMinimo()));
        return toResponse(guardado);
    }

    private MovimientoEvent toEvent(MovimientoInventario m, Integer stockMinimo) {
        return new MovimientoEvent(
                UUID.randomUUID().toString(),
                MovimientoEvent.TIPO_MOVIMIENTO_REGISTRADO,
                m.getId(),
                m.getProductoId(),
                m.getProductoCodigo(),
                m.getProductoNombre(),
                m.getTipoMovimiento().name(),
                m.getCantidad(),
                m.getStockResultante(),
                stockMinimo,
                m.getUsuario(),
                m.getFecha());
    }

    public List<MovimientoResponse> listar(Long productoId) {
        List<MovimientoInventario> movimientos = productoId != null
                ? movimientoRepository.findByProductoIdOrderByFechaDesc(productoId)
                : movimientoRepository.findAllByOrderByFechaDesc();
        return movimientos.stream().map(this::toResponse).toList();
    }

    private MovimientoResponse toResponse(MovimientoInventario m) {
        return MovimientoResponse.builder()
                .id(m.getId())
                .productoId(m.getProductoId())
                .productoCodigo(m.getProductoCodigo())
                .productoNombre(m.getProductoNombre())
                .tipoMovimiento(m.getTipoMovimiento().name())
                .cantidad(m.getCantidad())
                .motivo(m.getMotivo())
                .usuario(m.getUsuario())
                .stockResultante(m.getStockResultante())
                .fecha(m.getFecha())
                .build();
    }
}
