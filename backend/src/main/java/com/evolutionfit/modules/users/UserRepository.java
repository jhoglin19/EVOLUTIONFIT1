package com.evolutionfit.modules.users;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = "role")
    Optional<User> findByUsername(String username);

    @EntityGraph(attributePaths = "role")
    Optional<User> findByEmail(String email);

    @EntityGraph(attributePaths = "role")
    Optional<User> findById(Long id);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    @EntityGraph(attributePaths = "role")
    @Query("""
            select u from User u
            where lower(u.username) like lower(concat('%', :q, '%'))
               or lower(u.firstName) like lower(concat('%', :q, '%'))
               or lower(u.lastName) like lower(concat('%', :q, '%'))
               or lower(coalesce(u.email, '')) like lower(concat('%', :q, '%'))
            """)
    Page<User> search(@Param("q") String q, Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("update User u set u.lastLoginAt = :at where u.id = :id")
    void updateLastLogin(@Param("id") Long id, @Param("at") LocalDateTime at);
}
