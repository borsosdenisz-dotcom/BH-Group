package com.bhstays.pms.repository;

import com.bhstays.pms.domain.PaymentProvider;
import com.bhstays.pms.domain.PaymentWebhookEvent;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, UUID> {

    Optional<PaymentWebhookEvent> findByProviderAndExternalEventId(PaymentProvider provider, String externalEventId);

    /**
     * Records a delivery in the inbox unless that (provider, event id) is
     * already there. Returns 1 for a first delivery and 0 for a duplicate.
     *
     * <p>{@code ON CONFLICT DO NOTHING} rather than catching a unique-index
     * violation: a failed INSERT aborts the surrounding Postgres
     * transaction, whereas this leaves it usable. A concurrent duplicate
     * blocks on the index until the first delivery commits (then returns 0)
     * or rolls back (then inserts and is processed itself).
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into payment_webhook_events (id, provider, external_event_id, event_type, payload, received_at)
            values (gen_random_uuid(), cast(:provider as payment_provider), :eventId, :eventType, :payload, now())
            on conflict (provider, external_event_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("provider") String provider,
                       @Param("eventId") String eventId,
                       @Param("eventType") String eventType,
                       @Param("payload") String payload);

    @Modifying
    @Query(value = """
            update payment_webhook_events
               set processed_at = now(), processing_error = :processingError
             where provider = cast(:provider as payment_provider) and external_event_id = :eventId
            """, nativeQuery = true)
    int markProcessed(@Param("provider") String provider,
                      @Param("eventId") String eventId,
                      @Param("processingError") String processingError);
}
