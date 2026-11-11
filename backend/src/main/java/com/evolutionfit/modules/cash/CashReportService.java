package com.evolutionfit.modules.cash;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.evolutionfit.common.exception.BusinessException;
import com.evolutionfit.common.exception.ResourceNotFoundException;
import com.evolutionfit.modules.cash.dto.CashClosingDetailResponse;
import com.evolutionfit.modules.cash.dto.CashClosingResponse;
import com.evolutionfit.modules.cash.dto.MonthlyReportResponse;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Historial de cierres, detalle y reporte mensual (solo ADMIN, lo exige el controlador). */
@Service
public class CashReportService {

    private final CashClosingRepository closingRepository;
    private final CashLedgerRepository ledgerRepository;
    private final CashClosingMapper mapper;

    public CashReportService(CashClosingRepository closingRepository, CashLedgerRepository ledgerRepository,
                             CashClosingMapper mapper) {
        this.closingRepository = closingRepository;
        this.ledgerRepository = ledgerRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Page<CashClosingResponse> list(LocalDate from, LocalDate to, CashClosingStatus status, int page, int size) {
        Specification<CashClosing> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (from != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("cashDate"), from));
            }
            if (to != null) {
                p.add(cb.lessThanOrEqualTo(root.get("cashDate"), to));
            }
            if (status != null) {
                p.add(cb.equal(root.get("status"), status));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return closingRepository.findAll(spec, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by("cashDate").descending())).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public CashClosingDetailResponse detail(Long id) {
        CashClosing c = closingRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Cierre no encontrado"));
        CashLedger ledger = ledgerRepository.forClosing(id);
        return new CashClosingDetailResponse(mapper.toResponse(c), ledgerRepository.totals(List.of(id)),
                CashCalculator.movements(ledger), mapper.readMovements(c.getPostCutDetail()));
    }

    @Transactional(readOnly = true)
    public MonthlyReportResponse monthly(String month) {
        YearMonth ym;
        try {
            ym = YearMonth.parse(month);
        } catch (RuntimeException e) {
            throw new BusinessException("Mes no válido (formato AAAA-MM)");
        }
        List<CashClosing> closings = closingRepository.findByCashDateBetweenOrderByCashDateAsc(ym.atDay(1), ym.atEndOfMonth());
        List<Long> ids = closings.stream().map(CashClosing::getId).toList();
        return new MonthlyReportResponse(ym.toString(), closings.size(),
                sum(closings, CashClosing::getCashIncome), sum(closings, CashClosing::getCashVoided),
                sum(closings, CashClosing::getCashWithdrawals), sum(closings, CashClosing::getDifference),
                closings.stream().filter(c -> c.getStatus() == CashClosingStatus.PENDING_COUNT).count(),
                closings.stream().filter(c -> c.getStatus() == CashClosingStatus.PENDING_REVIEW).count(),
                ledgerRepository.totals(ids), ledgerRepository.withdrawalsByCategory(ids),
                closings.stream().map(mapper::toResponse).toList());
    }

    private static BigDecimal sum(List<CashClosing> closings, Function<CashClosing, BigDecimal> field) {
        return closings.stream().map(field).filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
