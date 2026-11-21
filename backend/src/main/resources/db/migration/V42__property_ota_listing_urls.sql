-- Optional public links for the OTA-first flow. Existing properties remain
-- compatible because both values are nullable and no values are inferred.
ALTER TABLE properties
    ADD COLUMN airbnb_listing_url VARCHAR(500),
    ADD COLUMN booking_listing_url VARCHAR(500);
