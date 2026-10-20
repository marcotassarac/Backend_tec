package com.inventario.inventory.repository;

import com.inventario.inventory.entity.MovimientoInventario;
import com.inventario.inventory.entity.TipoMovimiento;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de integracion: usa una base H2 en memoria real (vía @DataJpaTest), no mocks,
 * para verificar que MovimientoInventarioRepository persiste y consulta correctamente
 * contra un motor de base de datos de verdad.
 */
@DataJpaTest
class MovimientoInventarioRepositoryTest {

    @Autowired
    private MovimientoInventarioRepository repository;

    @Test
    void guardarYRecuperar_debePersistirElMovimientoCorrectamente() {
        MovimientoInventario movimiento = MovimientoInventario.builder()
                .productoId(1L)
                .productoCodigo("PROD-001")
                .productoNombre("Notebook 14 pulgadas")
                .tipoMovimiento(TipoMovimiento.SALIDA)
                .cantidad(3)
                .motivo("Venta mostrador")
                .usuario("operador")
                .stockResultante(7)
                .fecha(LocalDateTime.now())
                .build();

        MovimientoInventario guardado = repository.save(movimiento);

        assertThat(guardado.getId()).isNotNull();
        MovimientoInventario recuperado = repository.findById(guardado.getId()).orElseThrow();
        assertThat(recuperado.getProductoCodigo()).isEqualTo("PROD-001");
        assertThat(recuperado.getTipoMovimiento()).isEqualTo(TipoMovimiento.SALIDA);
        assertThat(recuperado.getStockResultante()).isEqualTo(7);
    }

    @Test
    void findByProductoIdOrderByFechaDesc_debeFiltrarYOrdenarPorFechaDescendente() {
        LocalDateTime base = LocalDateTime.now().minusDays(1);
        repository.save(movimiento(1L, base));
        repository.save(movimiento(1L, base.plusHours(5)));
        repository.save(movimiento(2L, base.plusHours(2)));

        List<MovimientoInventario> resultado = repository.findByProductoIdOrderByFechaDesc(1L);

        assertThat(resultado).hasSize(2);
        assertThat(resultado.get(0).getFecha()).isAfter(resultado.get(1).getFecha());
        assertThat(resultado).allMatch(m -> m.getProductoId().equals(1L));
    }

    private MovimientoInventario movimiento(Long productoId, LocalDateTime fecha) {
        return MovimientoInventario.builder()
                .productoId(productoId)
                .productoCodigo("PROD-00" + productoId)
                .productoNombre("Producto " + productoId)
                .tipoMovimiento(TipoMovimiento.ENTRADA)
                .cantidad(1)
                .motivo("Prueba")
                .usuario("operador")
                .stockResultante(1)
                .fecha(fecha)
                .build();
    }
}
