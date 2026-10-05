package com.bhstays.pms.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bhstays.pms.domain.Reservation;
import com.bhstays.pms.domain.ReservationStatus;
public interface ReservationRepository extends JpaRepository<Reservation, UUID>,
        JpaSpecificationExecutor<Reservation> {

    List<Reservation> findByGuestEmailIgnoreCase(String guestEmail);

    @Query("""
            select r from Reservation r
            where r.property.id = :propertyId
              and r.status not in :excludedStatuses
              and r.checkInDate < :checkOutDate
              and r.checkOutDate > :checkInDate
              and (:excludeId is null or r.id <> :excludeId)
            """)
    List<Reservation> findOverlapping(@Param("propertyId") UUID propertyId,
                                       @Param("checkInDate") LocalDate checkInDate,
                                       @Param("checkOutDate") LocalDate checkOutDate,
                                       @Param("excludeId") UUID excludeId,
                                       @Param("excludedStatuses") Collection<ReservationStatus> excludedStatuses);

    @Query("""
            select r from Reservation r
            where r.property.id = :propertyId
              and r.status not in :excludedStatuses
              and r.checkInDate < :to
              and r.checkOutDate > :from
            order by r.checkInDate asc
            """)
    List<Reservation> findCalendarEntries(@Param("propertyId") UUID propertyId,
                                           @Param("from") LocalDate from,
                                           @Param("to") LocalDate to,
                                           @Param("excludedStatuses") Collection<ReservationStatus> excludedStatuses);

    long countByPropertyIdAndStatus(UUID propertyId, ReservationStatus status);

    /** Used to block late checkout when the next guest already checks in the same day (no cleaning buffer). */
    boolean existsByPropertyIdAndCheckInDateAndStatusNotIn(
            UUID propertyId, LocalDate checkInDate, Collection<ReservationStatus> excludedStatuses);

    long countByStatus(ReservationStatus status);

    @Query("select coalesce(sum(r.totalAmount), 0) from Reservation r where r.status not in :excludedStatuses")
    BigDecimal sumTotalRevenue(@Param("excludedStatuses") Collection<ReservationStatus> excludedStatuses);

    @Query("""
            select coalesce(sum(r.totalAmount), 0) from Reservation r
            where r.property.id = :propertyId
              and r.status not in :excludedStatuses
            """)
    BigDecimal sumRevenueForProperty(@Param("propertyId") UUID propertyId,
                                      @Param("excludedStatuses") Collection<ReservationStatus> excludedStatuses);

    @Query("""
            select r from Reservation r
            where r.status not in :excludedStatuses
              and r.checkInDate >= :from
            order by r.checkInDate asc
            """)
    List<Reservation> findUpcoming(@Param("from") LocalDate from,
                                    @Param("excludedStatuses") Collection<ReservationStatus> excludedStatuses,
                                    Pageable pageable);

    Optional<Reservation> findByManagementToken(String managementToken);

    /**
     * Row-locked reads for the paths that move a held booking forward or
     * release it - opening a checkout session, the Stripe webhook, and the
     * hold-expiry job - so they serialise instead of overwriting each other.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.managementToken = :token")
    Optional<Reservation> findByManagementTokenForUpdate(@Param("token") String managementToken);

    Optional<Reservation> findByIdempotencyKey(String idempotencyKey);

    Optional<Reservation> findByExternalUid(String externalUid);

    List<Reservation> findByPropertyIdAndExternalUidIsNotNull(UUID propertyId);

    @Query("""
            select r from Reservation r
            where r.property.id = :propertyId
              and r.status not in :excludedStatuses
            order by r.checkInDate asc
            """)
    List<Reservation> findActiveForExport(@Param("propertyId") UUID propertyId,
                                           @Param("excludedStatuses") Collection<ReservationStatus> excludedStatuses);

    /**
     * Locked so the expiry job cannot cancel a hold a webhook is confirming
     * at the same instant: Postgres re-checks the status after the lock is
     * granted, so a row the webhook just confirmed drops out of the result.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r from Reservation r
            where r.status = :status
              and r.holdExpiresAt is not null
              and r.holdExpiresAt < :now
            """)
    List<Reservation> findExpiredHolds(@Param("status") ReservationStatus status, @Param("now") Instant now);

    @Query("""
            select r from Reservation r
            where r.status = :status
              and r.checkInDate = :checkInDate
              and r.accessCode is not null
              and r.accessCodeSentAt is null
              and r.guestEmail is not null
            """)
    List<Reservation> findPendingCheckinInstructions(@Param("status") ReservationStatus status,
                                                       @Param("checkInDate") LocalDate checkInDate);
}
