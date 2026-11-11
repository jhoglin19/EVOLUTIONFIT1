package com.evolutionfit.modules.cash;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.evolutionfit.common.exception.BusinessException;
import com.evolutionfit.common.exception.ConflictException;
import com.evolutionfit.common.exception.ResourceNotFoundException;
import com.evolutionfit.modules.cash.dto.CashClosingResponse;
import com.evolutionfit.modules.cash.dto.CountRequest;
import com.evolutionfit.modules.cash.dto.WithdrawalRequest;
import com.evolutionfit.modules.cash.dto.WithdrawalResponse;
import com.evolutionfit.modules.payments.PaymentMethod;
import com.evolutionfit.modules.payments.dto.PaymentResponse;

import org.junit.jupiter.api.Test;

/** Caja con solicitudes simultáneas contra MySQL real: ni días duplicados, ni dinero perdido o contado dos veces. */
class CashConcurrencyTest extends CashMySqlTestSupport {

    /** Lanza las tareas a la vez; devuelve los resultados y cuenta los rechazos esperados. */
    private <T> List<T> simultaneously(List<Callable<T>> tasks, AtomicInteger rejected) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (Callable<T> task : tasks) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return task.call();
            }));
        }
        ready.await();
        go.countDown();
        List<T> results = new ArrayList<>();
        for (Future<T> f : futures) {
            try {
                results.add(f.get(120, TimeUnit.SECONDS));
            } catch (ExecutionException e) {
                assertThat(e.getCause()).isInstanceOfAny(ConflictException.class, BusinessException.class,
                        ResourceNotFoundException.class);
                rejected.incrementAndGet();
            }
        }
        pool.shutdown();
        return results;
    }

    private static <T> List<Callable<T>> times(int n, Callable<T> task) {
        List<Callable<T>> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(task);
        }
        return list;
    }

    @Test
    void startupAndSchedulerCatchingUpAtTheSameTimeCreateOneClosingPerDay() throws Exception {
        LocalDate start = startCash();
        at(start.atTime(10, 0));
        PaymentResponse p = pay(PaymentMethod.CASH);
        at(start.plusDays(4).atTime(9, 0));

        List<Integer> closed = simultaneously(times(8, closingService::closeDueDays), new AtomicInteger());

        assertThat(closed.stream().mapToInt(Integer::intValue).sum()).isEqualTo(4);
        assertThat(jdbc.queryForObject("select count(*) from cash_closings", Long.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("select count(distinct cash_date) from cash_closings", Long.class)).isEqualTo(4);
        assertThat(closing(start).getCashIncome()).isEqualByComparingTo("90000");
        assertThat(jdbc.queryForObject("select cash_closing_id from payments where id = ?", Long.class, p.id()))
                .isEqualTo(closing(start).getId());
    }

    @Test
    void paymentsSavedWhileTheTenPmClosingRunsAreCountedExactlyOnce() throws Exception {
        LocalDate start = startCash();
        int n = 12;
        List<Long> memberships = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            memberships.add(pendingMembership());
        }
        at(start.atTime(21, 59, 59));
        CountDownLatch half = new CountDownLatch(n / 2);
        AtomicInteger next = new AtomicInteger();
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            tasks.add(() -> {
                PaymentResponse r = pay(memberships.get(next.getAndIncrement()), PaymentMethod.CASH);
                half.countDown();
                return r;
            });
        }
        for (int i = 0; i < 3; i++) {
            tasks.add(() -> {
                half.await(); // a mitad de los cobros llega el corte y el cierre corre en tres hilos
                at(start.atTime(22, 0, 0, 500_000_000));
                return closingService.closeDueDays();
            });
        }

        simultaneously(tasks, new AtomicInteger());
        at(start.plusDays(1).atTime(22, 0));
        closingService.closeDueDays();

        assertThat(jdbc.queryForObject("select count(*) from payments where membership_id in ("
                + String.join(",", memberships.stream().map(String::valueOf).toList()) + ") and cash_closing_id is null",
                Long.class)).isZero();
        BigDecimal counted = jdbc.queryForObject("select coalesce(sum(cash_income),0) from cash_closings", BigDecimal.class);
        assertThat(counted).isEqualByComparingTo(MONTHLY.multiply(BigDecimal.valueOf(n)));
        // Nada cobrado después de las 10 p.m. quedó en el primer día.
        assertThat(jdbc.queryForObject("select count(*) from payments where cash_closing_id = ? and paid_at >= ?",
                Long.class, closing(start).getId(), start.atTime(22, 0))).isZero();
    }

    @Test
    void twoSimultaneousCountsOfTheSameDayRegisterOnlyOne() throws Exception {
        LocalDate start = startCash();
        at(start.atTime(9, 0));
        pay(PaymentMethod.CASH);
        at(start.atTime(22, 0));
        closingService.closeDueDays();
        Long id = closing(start).getId();
        long before = audits("CASH_CLOSING", "COUNT");
        CountRequest request = new CountRequest(new BigDecimal("140000"), null, new BigDecimal("140000"));
        AtomicInteger rejected = new AtomicInteger();

        List<CashClosingResponse> ok = simultaneously(times(5, () -> countService.count(id, request, reception)), rejected);

        assertThat(ok).hasSize(1);
        assertThat(rejected.get()).isEqualTo(4);
        assertThat(audits("CASH_CLOSING", "COUNT") - before).isEqualTo(1);
        assertThat(closing(start).getStatus()).isEqualTo(CashClosingStatus.COUNTED);
    }

    @Test
    void simultaneousWithdrawalsNeverTakeMoreThanTheCashInTheBox() throws Exception {
        LocalDate start = startCash();
        at(start.atTime(9, 0));
        AtomicInteger rejected = new AtomicInteger();

        List<WithdrawalResponse> ok = simultaneously(times(8, () -> withdraw("10000")), rejected);

        assertThat(ok).hasSize(5); // base de 50.000
        assertThat(rejected.get()).isEqualTo(3);
        assertThat(cashService.today(reception).summary().expectedCash()).isEqualByComparingTo("0");
    }

    @Test
    void aDoubleClickOnAWithdrawalRegistersItOnce() throws Exception {
        startCash();
        WithdrawalRequest request = new WithdrawalRequest(UUID.randomUUID().toString(), new BigDecimal("2500"),
                WithdrawalCategory.OTHER, "Bolsas de basura");

        List<WithdrawalResponse> results = simultaneously(times(6,
                () -> cashService.registerWithdrawal(request, reception)), new AtomicInteger());

        assertThat(results).hasSize(6);
        assertThat(results.stream().map(WithdrawalResponse::id).distinct()).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from cash_movements where request_id = ?", Long.class,
                request.requestId())).isEqualTo(1);
    }
}
