package com.evolutionfit.modules.cash.dto;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** startDateLocked: ya hay cierres y la fecha de inicio no se puede cambiar. */
public record CashSettingsResponse(BigDecimal openingBase, LocalDate startDate, boolean startDateLocked,
                                   List<DayOfWeek> openDays, LocalTime closingTime) {
}
