package com.evolutionfit.modules.memberships;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.evolutionfit.common.exception.ConflictException;
import com.evolutionfit.modules.clients.Client;
import com.evolutionfit.modules.clients.ClientRepository;
import com.evolutionfit.modules.memberships.dto.CreateMembershipRequest;
import com.evolutionfit.modules.memberships.dto.MigratedMembershipRequest;
import com.evolutionfit.modules.plans.Plan;
import com.evolutionfit.modules.plans.PlanRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Regla "una sola activa y una sola siguiente por cliente" contra solicitudes simultáneas, en MySQL 8.4
 * real (Flyway aplica V1, V2 y V3 al arrancar). Sin el bloqueo sobre el cliente, varias solicitudes leen
 * "no tiene membresías" al mismo tiempo y todas se guardan.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "JWT_SECRET=clave-ficticia-solo-para-pruebas-de-concurrencia-0123456789",
        "BOOTSTRAP_ADMIN_PASSWORD="
})
class MembershipConcurrencyTest {

    private static final int REQUESTS = 6;

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Autowired
    private MembershipService membershipService;
    @Autowired
    private MembershipRepository membershipRepository;
    @Autowired
    private ClientRepository clientRepository;
    @Autowired
    private PlanRepository planRepository;
    @Autowired
    private Clock clock;

    private Client newClient(String document) {
        Client c = new Client();
        c.setDocumentType("CC");
        c.setDocumentNumber(document);
        c.setFirstName("Ana");
        c.setLastName("Pérez");
        return clientRepository.save(c);
    }

    private Plan plan() {
        return planRepository.findAll().stream().findFirst().orElseGet(() -> {
            Plan p = new Plan();
            p.setName("Mensual");
            p.setDurationDays(30);
            p.setPrice(new BigDecimal("90000"));
            return planRepository.save(p);
        });
    }

    /** Lanza todas las solicitudes a la vez y cuenta cuántas se guardaron y cuántas se rechazaron. */
    private int[] runSimultaneously(Callable<?> request) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(REQUESTS);
        CountDownLatch ready = new CountDownLatch(REQUESTS);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < REQUESTS; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return request.call();
            }));
        }
        ready.await();
        go.countDown();
        int ok = 0;
        int conflicts = 0;
        for (Future<?> f : futures) {
            try {
                f.get(30, TimeUnit.SECONDS);
                ok++;
            } catch (java.util.concurrent.ExecutionException e) {
                assertThat(e.getCause()).isInstanceOf(ConflictException.class);
                conflicts++;
            }
        }
        pool.shutdown();
        return new int[] {ok, conflicts};
    }

    @Test
    void simultaneousSalesForTheSameClientCreateOnlyOne() throws Exception {
        Client client = newClient("900001");
        Long planId = plan().getId();

        int[] result = runSimultaneously(() ->
                membershipService.create(new CreateMembershipRequest(client.getId(), planId, null, null), null));

        assertThat(result).containsExactly(1, REQUESTS - 1);
        assertThat(membershipRepository.findByClientIdOrderByStartDateDescIdDesc(client.getId())).hasSize(1);
    }

    @Test
    void simultaneousMigrationsForTheSameClientCreateOnlyOne() throws Exception {
        Client client = newClient("900002");
        Long planId = plan().getId();
        LocalDate today = LocalDate.now(clock);

        int[] result = runSimultaneously(() -> membershipService.createMigrated(new MigratedMembershipRequest(
                client.getId(), planId, today.minusDays(10), today.plusDays(20), null, "ID 77", false, null), null));

        assertThat(result).containsExactly(1, REQUESTS - 1);
        assertThat(membershipRepository.findByClientIdOrderByStartDateDescIdDesc(client.getId())).hasSize(1);
    }

    @Test
    void simultaneousRequestsForDifferentClientsAllSucceed() throws Exception {
        Long planId = plan().getId();
        List<Long> clientIds = new ArrayList<>();
        for (int i = 0; i < REQUESTS; i++) {
            clientIds.add(newClient("91000" + i).getId());
        }
        java.util.concurrent.atomic.AtomicInteger index = new java.util.concurrent.atomic.AtomicInteger();

        int[] result = runSimultaneously(() -> membershipService.create(
                new CreateMembershipRequest(clientIds.get(index.getAndIncrement()), planId, null, null), null));

        assertThat(result).containsExactly(REQUESTS, 0);
    }
}
