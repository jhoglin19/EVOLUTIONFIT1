package com.evolutionfit.modules.cash.dto;

import java.math.BigDecimal;
import java.util.List;

import com.evolutionfit.modules.cash.CashCalculator.Movement;

/**
 * Lo que muestra el diálogo del arqueo. El esperado congelado no cambia; el ajustado suma el efectivo que
 * entró después del corte (ya está en la caja) y resta el que salió.
 */
public record CountPreviewResponse(CashClosingResponse closing, BigDecimal postCutIncome, BigDecimal postCutOutflow,
                                   BigDecimal expectedAdjustedCash, List<Movement> postCutMovements) {
}
