/**
 * Pure helpers behind LeafletMap, kept free of Leaflet so they can be
 * tested in jsdom.
 */

export interface LatLngPoint {
  id: string
  lat: number
  lng: number
}

/** Closest zoom a multi-marker view may reach - a street, not a single building. */
export const MAX_FIT_ZOOM = 15
/** Zoom for a single location (one marker, or several at the same spot). */
export const SINGLE_LOCATION_ZOOM = 14
/** Horizontal distance between price pins that sit on the exact same coordinates. */
export const COINCIDENT_PIN_SPACING_PX = 76

export function hasCoordinates<T extends { lat: number | null | undefined; lng: number | null | undefined }>(
  marker: T
): marker is T & { lat: number; lng: number } {
  return Number.isFinite(marker.lat) && Number.isFinite(marker.lng)
}

/**
 * How to frame the markers. Several markers on one spot have a zero-area
 * bounding box; fitting that zooms to the tile layer's maximum (19), where
 * the map is a few pale building outlines and reads as blank - so a single
 * location is centred at a sensible zoom instead.
 */
export function viewFor(
  markers: LatLngPoint[]
):
  | { kind: "none" }
  | { kind: "center"; lat: number; lng: number; zoom: number }
  | { kind: "fit"; points: [number, number][]; maxZoom: number } {
  if (markers.length === 0) return { kind: "none" }
  const first = markers[0]
  const allSameSpot = markers.every((m) => m.lat === first.lat && m.lng === first.lng)
  if (allSameSpot) {
    return { kind: "center", lat: first.lat, lng: first.lng, zoom: SINGLE_LOCATION_ZOOM }
  }
  return { kind: "fit", points: markers.map((m) => [m.lat, m.lng]), maxZoom: MAX_FIT_ZOOM }
}

/**
 * Horizontal pixel offset for each marker, so pins that share exact
 * coordinates sit side by side instead of hiding each other. Markers alone at
 * their spot get 0.
 */
export function coincidentOffsets(markers: LatLngPoint[]): Map<string, number> {
  const groups = new Map<string, string[]>()
  for (const m of markers) {
    const key = `${m.lat.toFixed(6)},${m.lng.toFixed(6)}`
    groups.set(key, [...(groups.get(key) ?? []), m.id])
  }
  const offsets = new Map<string, number>()
  for (const ids of groups.values()) {
    ids.forEach((id, index) => {
      offsets.set(id, (index - (ids.length - 1) / 2) * COINCIDENT_PIN_SPACING_PX)
    })
  }
  return offsets
}

export function escapeHtml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;")
}
