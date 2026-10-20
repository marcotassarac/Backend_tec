package com.inventario.product.repository;

import com.inventario.product.entity.Producto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prueba de integracion contra H2 real: confirma que el codigo es unico a nivel de base
 * de datos (@Column(unique = true)) y, de paso, evidencia el defecto real: como
 * ProductoService.actualizar() no valida duplicados antes de guardar (ver
 * ProductoServiceTest.actualizar_conCodigoDuplicadoDeOtroProducto_deberiaRechazarPeroNoLoHace),
 * lo que en produccion llega al cliente es esta excepcion de bajo nivel sin traducir,
 * en vez de un BusinessException con mensaje claro.
 */
@DataJpaTest
class ProductoRepositoryTest {

    @Autowired
    private ProductoRepository repository;

    @Test
    void guardarProducto_debePersistirYRecuperarCorrectamente() {
        Producto producto = Producto.builder()
                .codigo("PROD-900").nombre("Monitor").precio(BigDecimal.valueOf(120000))
                .categoriaId(1L).stock(8).stockMinimo(2).activo(true).build();

        Producto guardado = repository.save(producto);

        assertThat(guardado.getId()).isNotNull();
        assertThat(repository.existsByCodigoIgnoreCase("prod-900")).isTrue();
    }

    @Test
    void guardarDosProductosConElMismoCodigo_debeFallarPorRestriccionUnica() {
        repository.saveAndFlush(Producto.builder()
                .codigo("PROD-DUP").nombre("Producto A").precio(BigDecimal.ONE)
                .categoriaId(1L).stock(1).stockMinimo(0).activo(true).build());

        Producto segundo = Producto.builder()
                .codigo("PROD-DUP").nombre("Producto B").precio(BigDecimal.TEN)
                .categoriaId(1L).stock(1).stockMinimo(0).activo(true).build();

        assertThatThrownBy(() -> repository.saveAndFlush(segundo))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
