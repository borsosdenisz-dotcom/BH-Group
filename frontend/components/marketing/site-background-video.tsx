"use client"

import { CinematicBackgroundVideo } from "@/components/marketing/cinematic-background-video"
import { useCinematicBackgroundEnabled } from "@/hooks/use-cinematic-background-enabled"

interface SiteBackgroundVideoProps {
  srcMp4?: string
  poster?: string
}

const DEFAULT_POSTER =
  "https://images.unsplash.com/photo-1522708323590-d24dbb6b0267?w=2000&q=80&auto=format&fit=crop"
const DEFAULT_SRC_MP4 = "/videos/home-hero.mp4"

/**
 * Site-wide fixed cinematic video background for public pages - one video,
 * behind all scrolling content on every public page, instead of a separate
 * clip embedded per hero section (avoids decoding multiple video instances
 * on the same page). Sections with their own opaque surface color (bg-card,
 * bg-navy, etc.) simply paint over it, same as the site's existing
 * contour/glow decor layer - it only shows through transparent sections.
 *
 * overlayClassName tints toward the page's own --background token (adapts
 * automatically to light/dark) so body text stays legible; sections that
 * need extra contrast (e.g. a hero with large white text) layer their own
 * additional gradient on top locally.
 *
 * Desktop-only: renders nothing at all (not even the poster) on mobile/
 * tablet/reduced-motion/save-data, per useCinematicBackgroundEnabled - the
 * page falls back to whatever it renders without this mounted.
 */
export function SiteBackgroundVideo({
  srcMp4 = DEFAULT_SRC_MP4,
  poster = DEFAULT_POSTER,
}: SiteBackgroundVideoProps) {
  const enabled = useCinematicBackgroundEnabled()
  if (!enabled) return null

  return (
    <CinematicBackgroundVideo
      srcMp4={srcMp4}
      poster={poster}
      posterClassName="kb-image-loop"
      className="fixed inset-0 -z-10"
      overlayClassName="bg-background/80"
      priority
    />
  )
}
