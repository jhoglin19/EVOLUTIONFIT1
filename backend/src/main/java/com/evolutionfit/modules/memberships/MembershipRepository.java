package com.evolutionfit.modules.memberships;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MembershipRepository extends JpaRepository<Membership, Long>, JpaSpecificationExecutor<Membership> {

    @EntityGraph(attributePaths = "client")
    List<Membership> findByClientIdOrderByStartDateDescIdDesc(Long clientId);

    @Override
    @EntityGraph(attributePaths = "client")
    Page<Membership> findAll(Specification<Membership> spec, Pageable pageable);

    /** Solo el id del cliente, sin cargar la membresía: se usa para bloquear al cliente antes de leerla. */
    @Query("select m.client.id from Membership m where m.id = :id")
    Optional<Long> findClientIdById(@Param("id") Long id);

    /** Para la sincronización diaria de la columna status. */
    List<Membership> findByStatusIn(Collection<MembershipStatus> statuses);
}
