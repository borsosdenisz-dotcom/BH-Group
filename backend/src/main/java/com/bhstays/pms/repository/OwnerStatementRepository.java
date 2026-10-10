package com.bhstays.pms.repository;

import com.bhstays.pms.domain.OwnerStatement;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OwnerStatementRepository extends JpaRepository<OwnerStatement, UUID>,
        JpaSpecificationExecutor<OwnerStatement> {

    /**
     * A statement of the owner in the currency whose period shares at least
     * one day with [periodStart, periodEnd] - the exact same period included.
     */
    @Query("""
            select s from OwnerStatement s
            where s.owner.id = :ownerId and s.currency = :currency
              and s.periodStart <= :periodEnd and s.periodEnd >= :periodStart
            order by s.periodStart
            limit 1
            """)
    Optional<OwnerStatement> findOverlapping(@Param("ownerId") UUID ownerId, @Param("currency") String currency,
                                             @Param("periodStart") LocalDate periodStart,
                                             @Param("periodEnd") LocalDate periodEnd);
}
