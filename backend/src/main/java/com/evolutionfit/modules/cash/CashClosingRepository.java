package com.evolutionfit.modules.cash;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CashClosingRepository extends JpaRepository<CashClosing, Long>, JpaSpecificationExecutor<CashClosing> {

    Optional<CashClosing> findTopByOrderByCashDateDesc();

    Optional<CashClosing> findByCashDate(LocalDate cashDate);

    List<CashClosing> findByStatusOrderByCashDateAsc(CashClosingStatus status);

    long countByStatus(CashClosingStatus status);

    List<CashClosing> findByCashDateBetweenOrderByCashDateAsc(LocalDate from, LocalDate to);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CashClosing c where c.id = :id")
    Optional<CashClosing> findByIdForUpdate(@Param("id") Long id);
}
