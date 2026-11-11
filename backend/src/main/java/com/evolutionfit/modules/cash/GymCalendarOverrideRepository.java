package com.evolutionfit.modules.cash;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GymCalendarOverrideRepository extends JpaRepository<GymCalendarOverride, LocalDate> {

    List<GymCalendarOverride> findByDayBetweenOrderByDayAsc(LocalDate from, LocalDate to);
}
