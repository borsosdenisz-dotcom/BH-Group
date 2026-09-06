# Cinematic background videos

This directory is where approved hero/background video clips go.
`CinematicBackgroundVideo` (see
`frontend/components/marketing/cinematic-background-video.tsx`) only plays
video when `srcMp4`/`srcWebm` is passed - otherwise it just renders the
poster image, so every entry below can be swapped for real BH footage with
a one-line prop change and no other code changes.

## home-hero.mp4 — TEMPORARY DEMO, replace before merging

Wired into the homepage hero (`frontend/components/marketing/hero-section.tsx`).
This is a **placeholder demo clip**, not BH Group's own footage:

- Source: "Video of a House Interior" by Kindel Media on Pexels
  (https://www.pexels.com/video/video-of-a-house-interior-7578546/)
- License: Pexels License — free for commercial and non-commercial use,
  no attribution required (attribution given here anyway as good practice)
- Downloaded variant: 1280×720, 30fps, ~6.6 MB
- Purpose: lets you actually see the cinematic-video behavior end to end.
  Swap this file (or just the `srcMp4` path in `hero-section.tsx`) for a
  real BH Group clip before this goes live - do not ship third-party stock
  footage as if it represents BH's own properties.

## Still needed from BH

1. **Replace `home-hero.mp4`** with real BH footage (premium apartment
   interior, slow camera movement, city/architecture, hospitality
   atmosphere). ~5-10s, natural loop, a few MB.
2. `owner-management.mp4` / `owner-management.webm` — pentru-proprietari
   hero (`frontend/app/pentru-proprietari/page.tsx`). Housekeeping,
   property inspection, check-in prep. Should read as clearly distinct
   from the homepage clip, not a repeat. Currently poster-only (no demo
   clip wired in).
3. `login.mp4` / `login.webm` — auth layout
   (`frontend/app/(auth)/layout.tsx`). Optional; same interior/property
   direction as the above, desktop-focused (mobile never mounts video,
   see `mobileFallback`). Currently poster-only.

Do not add stock footage from Airbnb, Booking.com, Guesty, Hostaway, hotel
brands, or agencies without clear usage rights.
