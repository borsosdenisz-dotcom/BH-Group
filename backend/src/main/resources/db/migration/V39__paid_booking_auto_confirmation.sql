-- ============================================================
-- BH Group PMS — Auto-confirm direct bookings after a verified Stripe payment
--
-- A card payment is tied to exactly one hosted Checkout session, so the
-- signed webhook can be matched to the payment row it pays for by the
-- session id alone - never by anything the browser sends. Keeping the
-- session's URL and expiry also lets a repeated "pay" click reuse the
-- open session instead of opening a second one the guest could pay too,
-- and lets the booking hold be kept in step with the session's lifetime.
--
-- NEW_PAID_BOOKING is the admin notification raised once a booking has
-- been paid and confirmed (and only then).
-- ============================================================

ALTER TABLE payments
    ADD COLUMN checkout_session_id  VARCHAR(255),
    ADD COLUMN checkout_url         TEXT,
    ADD COLUMN checkout_expires_at  TIMESTAMPTZ;

CREATE UNIQUE INDEX ux_payments_checkout_session_id ON payments (checkout_session_id)
    WHERE checkout_session_id IS NOT NULL;

ALTER TYPE notification_type ADD VALUE 'NEW_PAID_BOOKING';
