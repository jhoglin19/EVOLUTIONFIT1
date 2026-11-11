package com.evolutionfit.modules.cash.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.evolutionfit.modules.cash.CashCalculator.Movement;
import com.evolutionfit.modules.cash.CashSummary;

/**
 * Día de caja en curso: lo que todavía no tiene cierre. started = false mientras el ADMIN no fije la fecha
 * de inicio (o si es futura). pendingReviews solo se informa al ADMIN. closedReason: por qué el gimnasio
 * está cerrado ese día (domingo, festivo o decisión del ADMIN); null si abre.
 */
public record CashTodayResponse(boolean started, LocalDate startDate, LocalDate cashDate, LocalDateTime windowStart,
                                LocalDateTime cutoffAt, boolean openDay, String closedReason,
                                CashSummary summary,
                                List<Movement> movements, long pendingCounts, Long pendingReviews) {
}
