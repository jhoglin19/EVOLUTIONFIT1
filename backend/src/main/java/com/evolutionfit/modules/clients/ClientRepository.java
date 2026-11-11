package com.evolutionfit.modules.clients;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClientRepository extends JpaRepository<Client, Long> {

    boolean existsByDocumentNumber(String documentNumber);

    /**
     * SELECT ... FOR UPDATE sobre el cliente. Las operaciones que cambian sus membresías lo toman al inicio
     * de la transacción, así dos solicitudes simultáneas se ejecutan una después de la otra y no pueden
     * crear dos membresías activas o siguientes.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Client c where c.id = :id")
    Optional<Client> findByIdForUpdate(@Param("id") Long id);

    boolean existsByDocumentNumberAndIdNot(String documentNumber, Long id);

    // ---- historial: un cliente con historial no se borra, solo se desactiva ----

    @Query(value = "select count(*) from memberships where client_id = :id", nativeQuery = true)
    long countMemberships(@Param("id") Long id);

    @Query(value = "select count(*) from payments p join memberships m on m.id = p.membership_id where m.client_id = :id",
            nativeQuery = true)
    long countPayments(@Param("id") Long id);

    @Query(value = "select count(*) from access_logs where client_id = :id", nativeQuery = true)
    long countAccessLogs(@Param("id") Long id);

    /** {@code document} es el término normalizado, para que "1.234" encuentre "1234". */
    @Query("""
            select c from Client c
            where (:active is null or c.active = :active)
              and (:q = ''
                   or c.documentNumber like concat('%', :document, '%')
                   or lower(concat(c.firstName, ' ', c.lastName)) like lower(concat('%', :q, '%'))
                   or lower(concat(c.lastName, ' ', c.firstName)) like lower(concat('%', :q, '%'))
                   or coalesce(c.phone, '') like concat('%', :q, '%'))
            """)
    Page<Client> search(@Param("q") String q,
                        @Param("document") String document,
                        @Param("active") Boolean active,
                        Pageable pageable);
}
