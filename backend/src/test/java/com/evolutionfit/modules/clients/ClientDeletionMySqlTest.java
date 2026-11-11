package com.evolutionfit.modules.clients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.evolutionfit.common.exception.ConflictException;
import com.evolutionfit.modules.clients.dto.ClientRequest;
import com.evolutionfit.modules.memberships.MembershipService;
import com.evolutionfit.modules.memberships.dto.CreateMembershipRequest;
import com.evolutionfit.modules.plans.Plan;
import com.evolutionfit.modules.plans.PlanRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Borrado permanente de clientes contra MySQL 8.4 real (Flyway V1–V7): solo sin membresías, pagos ni
 * accesos; con historial se rechaza y el cliente queda intacto. Datos ficticios.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "JWT_SECRET=clave-ficticia-solo-para-pruebas-de-clientes-0123456789",
        "BOOTSTRAP_ADMIN_PASSWORD=",
        "app.cash.scheduler-enabled=false"
})
class ClientDeletionMySqlTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private ClientService clientService;
    @Autowired
    private MembershipService membershipService;
    @Autowired
    private PlanRepository planRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private Long admin() {
        jdbc.update("insert ignore into roles(id,name) values (1,'ADMIN'),(2,'RECEPTIONIST')");
        jdbc.update("insert ignore into users(id,role_id,username,password_hash,first_name,last_name) values "
                + "(70,1,'admin_clientes','x','Admin','Prueba')");
        return 70L;
    }

    private Long newClient(String document) {
        return clientService.create(new ClientRequest("CC", document, "Cliente", "Ficticio", null, "3001234567", null,
                null, null)).id();
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    @Test
    void aClientWithoutHistoryIsDeletedPermanentlyWithAudit() {
        Long adminId = admin();
        Long id = newClient("880001");

        clientService.delete(id, adminId);

        assertThat(count("select count(*) from clients where id = ?", id)).isZero();
        assertThat(jdbc.queryForObject("select json_unquote(json_extract(before_data, '$.documentNumber')) from audit_logs"
                + " where entity_type = 'CLIENT' and action = 'DELETE' and entity_id = ? and user_id = ?", String.class,
                id, adminId)).isEqualTo("880001");
    }

    @Test
    void aClientWithAMembershipIsNotDeletedEvenIfItIsPendingOrCancelled() {
        Long adminId = admin();
        Long id = newClient("880002");
        Plan plan = new Plan();
        plan.setName("Mensual borrado");
        plan.setDurationDays(30);
        plan.setPrice(new java.math.BigDecimal("90000"));
        Long planId = planRepository.save(plan).getId();
        Long membershipId = membershipService.create(new CreateMembershipRequest(id, planId, null, null), adminId).id();
        jdbc.update("update memberships set status = 'CANCELLED', cancellation_reason = 'Prueba' where id = ?", membershipId);

        assertThatThrownBy(() -> clientService.delete(id, adminId)).isInstanceOf(ConflictException.class)
                .hasMessageContaining("1 membresía(s)");
        assertThat(count("select count(*) from clients where id = ?", id)).isEqualTo(1);
        assertThat(count("select count(*) from audit_logs where entity_type = 'CLIENT' and action = 'DELETE'"
                + " and entity_id = ?", id)).isZero();
    }

    @Test
    void aClientWithAnAccessLogIsNotDeleted() {
        Long adminId = admin();
        Long id = newClient("880003");
        jdbc.update("insert into access_logs(client_id, document_number, decision) values (?, '880003', 'DENIED')",
                id);

        assertThatThrownBy(() -> clientService.delete(id, adminId)).isInstanceOf(ConflictException.class)
                .hasMessageContaining("1 acceso(s)");
        assertThat(count("select count(*) from clients where id = ?", id)).isEqualTo(1);
    }
}
