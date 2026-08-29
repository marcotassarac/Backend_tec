package com.inventario.report.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KardexResponse {
    private ProductoDto producto;
    private List<MovimientoDto> movimientos;
}
