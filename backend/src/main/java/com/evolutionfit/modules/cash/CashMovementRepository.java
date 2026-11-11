package com.evolutionfit.modules.cash;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CashMovementRepository extends JpaRepository<CashMovement, Long>, JpaSpecificationExecutor<CashMovement> {

    Optional<CashMovement> findByRequestId(String requestId);
}
