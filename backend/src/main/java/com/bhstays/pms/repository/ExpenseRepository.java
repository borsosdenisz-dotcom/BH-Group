package com.bhstays.pms.repository;

import com.bhstays.pms.domain.Expense;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import com.bhstays.pms.repository.projection.PropertyCurrencyAmount;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExpenseRepository extends JpaRepository<Expense, UUID>, JpaSpecificationExecutor<Expense> {

    @Query("""
            select coalesce(sum(e.amount), 0) from Expense e
            where e.property.id = :propertyId
              and (:from is null or e.expenseDate >= :from)
              and (:to is null or e.expenseDate <= :to)
            """)
    BigDecimal sumForProperty(@Param("propertyId") UUID propertyId,
                               @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Same as {@link #sumForProperty}, grouped by currency so it never mixes amounts across currencies. */
    @Query("""
            select e.currency, coalesce(sum(e.amount), 0) from Expense e
            where e.property.id = :propertyId
              and (cast(:from as java.time.LocalDate) is null or e.expenseDate >= cast(:from as java.time.LocalDate))
              and (cast(:to as java.time.LocalDate) is null or e.expenseDate <= cast(:to as java.time.LocalDate))
            group by e.currency
            """)
    List<Object[]> sumForPropertyGroupedByCurrency(@Param("propertyId") UUID propertyId,
                                                     @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select coalesce(sum(e.amount), 0) from Expense e
            where e.property.id = :propertyId
              and e.chargeToOwner = true
              and (:from is null or e.expenseDate >= :from)
              and (:to is null or e.expenseDate <= :to)
            """)
    BigDecimal sumChargeableToOwnerForProperty(@Param("propertyId") UUID propertyId,
                                                @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Same as {@link #sumChargeableToOwnerForProperty}, grouped by currency. */
    @Query("""
            select e.currency, coalesce(sum(e.amount), 0) from Expense e
            where e.property.id = :propertyId
              and e.chargeToOwner = true
              and (cast(:from as java.time.LocalDate) is null or e.expenseDate >= cast(:from as java.time.LocalDate))
              and (cast(:to as java.time.LocalDate) is null or e.expenseDate <= cast(:to as java.time.LocalDate))
            group by e.currency
            """)
    List<Object[]> sumChargeableToOwnerForPropertyGroupedByCurrency(@Param("propertyId") UUID propertyId,
                                                                      @Param("from") LocalDate from,
                                                                      @Param("to") LocalDate to);

    /** Expenses of every property, summed per property and currency, in one query (both bounds required). */
    @Query("""
            select new com.bhstays.pms.repository.projection.PropertyCurrencyAmount(
                e.property.id, e.currency, coalesce(sum(e.amount), 0))
            from Expense e
            where e.expenseDate >= :from
              and e.expenseDate <= :to
            group by e.property.id, e.currency
            """)
    List<PropertyCurrencyAmount> sumGroupedByPropertyAndCurrency(@Param("from") LocalDate from,
                                                                @Param("to") LocalDate to);

    /** Owner-chargeable expenses of one owner's properties, per property and currency, in one query. */
    @Query("""
            select new com.bhstays.pms.repository.projection.PropertyCurrencyAmount(
                e.property.id, e.currency, coalesce(sum(e.amount), 0))
            from Expense e
            where e.property.owner.id = :ownerId
              and e.chargeToOwner = true
              and e.expenseDate >= :from
              and e.expenseDate <= :to
            group by e.property.id, e.currency
            """)
    List<PropertyCurrencyAmount> sumChargeableToOwnerGroupedByPropertyAndCurrency(@Param("ownerId") UUID ownerId,
                                                                                 @Param("from") LocalDate from,
                                                                                 @Param("to") LocalDate to);
}
