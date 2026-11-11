package com.evolutionfit.modules.cash;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface CashSettingsRepository extends JpaRepository<CashSettings, Short> {

    /**
     * SELECT ... FOR UPDATE de la única fila: es el candado de la caja. Lo toman el cierre, los retiros y
     * los cambios de configuración, así nunca corren a la vez (ni dos cierres del mismo día).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CashSettings s where s.id = 1")
    Optional<CashSettings> findForUpdate();
}
