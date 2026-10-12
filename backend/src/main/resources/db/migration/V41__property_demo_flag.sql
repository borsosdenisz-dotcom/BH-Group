-- ============================================================
-- BH Stays PMS — Demo properties
--
-- A demo property is a showcase listing: it is visible on the public
-- site, but it can never be quoted, booked or paid for. The backend
-- enforces this on every public booking/payment path; the flag here is
-- the single source of truth those checks read.
--
-- Existing properties stay bookable (DEFAULT FALSE).
-- ============================================================

ALTER TABLE properties
    ADD COLUMN demo BOOLEAN NOT NULL DEFAULT FALSE;
