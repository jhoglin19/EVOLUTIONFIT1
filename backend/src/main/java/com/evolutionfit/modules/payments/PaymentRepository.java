package com.evolutionfit.modules.payments;

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

public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {

    @EntityGraph(attributePaths = {"membership", "membership.client"})
    Optional<Payment> findByRequestId(String requestId);

    @EntityGraph(attributePaths = {"membership", "membership.client"})
    List<Payment> findByMembershipClientIdOrderByPaidAtDescIdDesc(Long clientId);

    @Override
    @EntityGraph(attributePaths = {"membership", "membership.client"})
    Page<Payment> findAll(Specification<Payment> spec, Pageable pageable);

    @Query("select p.membership.client.id from Payment p where p.id = :id")
    Optional<Long> findClientIdById(@Param("id") Long id);
}
