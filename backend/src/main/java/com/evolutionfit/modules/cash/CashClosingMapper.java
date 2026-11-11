package com.evolutionfit.modules.cash;

import java.util.List;

import com.evolutionfit.modules.cash.CashCalculator.Movement;
import com.evolutionfit.modules.cash.dto.CashClosingResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

@Component
class CashClosingMapper {

    private final CashLedgerRepository ledgerRepository;
    private final ObjectMapper objectMapper;

    CashClosingMapper(CashLedgerRepository ledgerRepository, ObjectMapper objectMapper) {
        this.ledgerRepository = ledgerRepository;
        this.objectMapper = objectMapper;
    }

    CashClosingResponse toResponse(CashClosing c) {
        return new CashClosingResponse(c.getId(), c.getCashDate(), c.getWindowStart(), c.getWindowEnd(), c.getStatus(),
                c.getClosingMode(), c.isWasOpenDay(), c.isHasCashActivity(), c.getOpeningBase(), c.getCashIncome(),
                c.getCashVoided(), c.getCashWithdrawals(), c.getExpectedCash(), c.getClosedAt(), c.getPostCutIncome(),
                c.getPostCutOutflow(), c.getAdjustedExpectedCash(), c.getCountedCash(), c.getDifference(),
                c.getCountNote(), ledgerRepository.userName(c.getCountedBy()), c.getCountedAt(),
                ledgerRepository.userName(c.getReviewedBy()), c.getReviewedAt(), c.getReviewNote());
    }

    String writeMovements(List<Movement> movements) {
        try {
            return objectMapper.writeValueAsString(movements);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo guardar el detalle del arqueo", e);
        }
    }

    List<Movement> readMovements(String json) {
        if (json == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Movement>>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo leer el detalle del arqueo", e);
        }
    }
}
