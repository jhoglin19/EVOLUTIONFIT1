package com.evolutionfit.modules.memberships;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MembershipFreezeRepository extends JpaRepository<MembershipFreeze, Long> {

    @Query("select f.membership.client.id from MembershipFreeze f where f.id = :id")
    Optional<Long> findClientIdById(@Param("id") Long id);

    List<MembershipFreeze> findByStatusIn(Collection<FreezeStatus> statuses);
}
