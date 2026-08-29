package com.inventario.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Representa la respuesta de product-service, solo con los campos que inventory-service necesita. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductoDto {
    private Long id;
    private String codigo;
    private String nombre;
    private BigDecimal precio;
    private Integer stock;
    private Integer stockMinimo;
    private boolean activo;
}
