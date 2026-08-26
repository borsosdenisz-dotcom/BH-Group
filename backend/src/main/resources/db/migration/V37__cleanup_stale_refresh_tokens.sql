-- One-time hygiene pass: remove refresh tokens that are no longer usable
-- (revoked, or past their expiry) across all accounts. Active, still-valid
-- tokens are left untouched. This is a data cleanup, not a schema change -
-- new stale tokens continue to accumulate normally afterwards and are not
-- automatically purged by this migration alone.
DELETE FROM refresh_tokens
WHERE revoked = TRUE
   OR expires_at < now();
