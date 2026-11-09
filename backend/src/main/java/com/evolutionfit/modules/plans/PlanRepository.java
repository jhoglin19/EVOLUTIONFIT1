package com.evolutionfit.modules.plans;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlanRepository extends JpaRepository<Plan, Long> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    @Query("""
            select p from Plan p
            where (:active is null or p.active = :active)
              and (:q = '' or lower(p.name) like lower(concat('%', :q, '%')))
            """)
    Page<Plan> search(@Param("q") String q, @Param("active") Boolean active, Pageable pageable);
}
