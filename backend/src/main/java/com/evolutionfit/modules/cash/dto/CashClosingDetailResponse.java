package com.evolutionfit.modules.cash.dto;

import java.util.List;

import com.evolutionfit.modules.cash.CashCalculator.Movement;
import com.evolutionfit.modules.cash.CashSummary.MethodTotal;

/** Detalle de un cierre para el ADMIN: informe por método, movimientos del día y lo posterior al corte. */
public record CashClosingDetailResponse(CashClosingResponse closing, List<MethodTotal> methods,
                                        List<Movement> movements, List<Movement> postCutMovements) {
}
