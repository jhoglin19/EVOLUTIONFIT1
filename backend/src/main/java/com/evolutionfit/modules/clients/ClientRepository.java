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
