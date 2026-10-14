"use client"

import { useEffect, useRef } from "react"
import "leaflet/dist/leaflet.css"
import { cn } from "@/lib/utils"
import {
  coincidentOffsets,
  escapeHtml,
  hasCoordinates,
  viewFor,
  type LatLngPoint,
} from "@/components/map/map-markers"

export interface MapMarker {
  id: string
  lat: number
  lng: number
  label: string
  href?: string
  /** When set, the marker is drawn as a price pin showing this text instead of the default pin icon. */
  price?: string
}

type Leaflet = typeof import("leaflet")

const MARKER_ICON_BASE = "https://unpkg.com/leaflet@1.9.4/dist/images"

// Literal class lists (not built from fragments) so Tailwind picks them up.
const PIN_BASE =
  "pointer-events-auto cursor-pointer whitespace-nowrap rounded-full border px-2.5 py-1 text-xs font-semibold shadow-md transition-transform duration-150"
const PIN_IDLE = "border-border bg-background text-foreground"
const PIN_ACTIVE = "z-10 scale-110 border-primary bg-primary text-primary-foreground"

function setPinActive(element: HTMLElement | undefined, active: boolean) {
  const pin = element?.querySelector<HTMLElement>("[data-pin]")
  if (!pin) return
  pin.className = `${PIN_BASE} ${active ? PIN_ACTIVE : PIN_IDLE}`
}

export default function LeafletMap({
  markers,
  height,
  activeId,
  onMarkerHover,
  onMarkerClick,
  className,
}: {
  markers: MapMarker[]
  height?: number
  activeId?: string | null
  /** Pointer entered (id) or left (null) a marker. */
  onMarkerHover?: (id: string | null) => void
  onMarkerClick?: (id: string) => void
  className?: string
}) {
  const containerRef = useRef<HTMLDivElement>(null)
  const leafletRef = useRef<Leaflet | null>(null)
  const mapRef = useRef<import("leaflet").Map | null>(null)
  const markersLayerRef = useRef<import("leaflet").LayerGroup | null>(null)
  const markerRefsRef = useRef<Map<string, import("leaflet").Marker>>(new Map())
  // Ids drawn as price pins (vs. the default image icon). Tracked here rather
  // than read from the DOM: markers have no element until the map has a view.
  const pricePinIdsRef = useRef<Set<string>>(new Set())
  const pointsRef = useRef<LatLngPoint[]>([])
  const iconsRef = useRef<{
    default: import("leaflet").Icon
    active: import("leaflet").Icon
  } | null>(null)
  // Latest callbacks/active id, read from Leaflet event handlers without
  // rebuilding every marker when a parent re-renders.
  const onHoverRef = useRef(onMarkerHover)
  const onClickRef = useRef(onMarkerClick)
  const activeIdRef = useRef(activeId)
  useEffect(() => {
    onHoverRef.current = onMarkerHover
    onClickRef.current = onMarkerClick
    activeIdRef.current = activeId
  })

  /** Frames all markers - only once the container has a real size. */
  function applyView() {
    const map = mapRef.current
    const L = leafletRef.current
    const container = containerRef.current
    if (!map || !L || !container || container.clientWidth === 0 || container.clientHeight === 0) return
    const view = viewFor(pointsRef.current)
    if (view.kind === "center") {
      map.setView([view.lat, view.lng], view.zoom)
    } else if (view.kind === "fit") {
      map.fitBounds(L.latLngBounds(view.points), { padding: [48, 48], maxZoom: view.maxZoom })
    }
  }

  async function ensureMap() {
    if (mapRef.current && leafletRef.current) return true
    const L = (await import("leaflet")).default as unknown as Leaflet
    if (!containerRef.current) return false
    leafletRef.current = L
    if (!mapRef.current) {
      mapRef.current = L.map(containerRef.current, { scrollWheelZoom: false })
      L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
        attribution: "&copy; OpenStreetMap contributors",
        maxZoom: 19,
      }).addTo(mapRef.current)
      markersLayerRef.current = L.layerGroup().addTo(mapRef.current)
    }
    return true
  }

  useEffect(() => {
    let cancelled = false

    async function renderMarkers() {
      if (!(await ensureMap()) || cancelled) return
      const L = leafletRef.current!
      const markersLayer = markersLayerRef.current
      if (!markersLayer) return

      if (!iconsRef.current) {
        iconsRef.current = {
          default: L.icon({
            iconUrl: `${MARKER_ICON_BASE}/marker-icon.png`,
            iconRetinaUrl: `${MARKER_ICON_BASE}/marker-icon-2x.png`,
            shadowUrl: `${MARKER_ICON_BASE}/marker-shadow.png`,
            iconSize: [25, 41],
            iconAnchor: [12, 41],
            popupAnchor: [1, -34],
            shadowSize: [41, 41],
          }),
          active: L.icon({
            iconUrl: `${MARKER_ICON_BASE}/marker-icon-2x.png`,
            shadowUrl: `${MARKER_ICON_BASE}/marker-shadow.png`,
            iconSize: [31, 51],
            iconAnchor: [15, 51],
            popupAnchor: [1, -42],
            shadowSize: [41, 41],
            className: "hue-rotate-90",
          }),
        }
      }
      const icons = iconsRef.current

      markersLayer.clearLayers()
      markerRefsRef.current.clear()
      pricePinIdsRef.current.clear()

      // A property without coordinates is simply not drawn; it stays in the list.
      const validMarkers = markers.filter(hasCoordinates)
      pointsRef.current = validMarkers
      const offsets = coincidentOffsets(validMarkers)

      validMarkers.forEach((marker) => {
        const dx = offsets.get(marker.id) ?? 0
        const icon =
          marker.price != null
            ? L.divIcon({
                className: "",
                iconSize: [0, 0],
                iconAnchor: [0, 0],
                popupAnchor: [dx, -30],
                html: `<div style="position:absolute;transform:translate(calc(-50% + ${dx}px),calc(-100% - 4px))"><div data-pin class="${PIN_BASE} ${PIN_IDLE}">${escapeHtml(marker.price)}</div></div>`,
              })
            : icons.default
        const leafletMarker = L.marker([marker.lat, marker.lng], {
          icon,
          title: marker.label,
          riseOnHover: true,
        }).addTo(markersLayer)

        const label = escapeHtml(marker.label)
        leafletMarker.bindPopup(
          marker.href
            ? `<a href="${escapeHtml(marker.href)}" style="font-weight:500;">${label}</a>`
            : `<span style="font-weight:500;">${label}</span>`
        )
        leafletMarker.on("mouseover", () => onHoverRef.current?.(marker.id))
        leafletMarker.on("mouseout", () => onHoverRef.current?.(null))
        leafletMarker.on("click", () => onClickRef.current?.(marker.id))
        markerRefsRef.current.set(marker.id, leafletMarker)
        if (marker.price != null) pricePinIdsRef.current.add(marker.id)
      })

      applyView()
      highlight(activeIdRef.current, false)
    }

    renderMarkers()

    return () => {
      cancelled = true
    }
  }, [markers])

  function highlight(id: string | null | undefined, pan: boolean) {
    const map = mapRef.current
    const icons = iconsRef.current
    if (!map) return
    markerRefsRef.current.forEach((marker, markerId) => {
      const active = markerId === id
      if (pricePinIdsRef.current.has(markerId)) {
        setPinActive(marker.getElement(), active)
      } else if (icons) {
        marker.setIcon(active ? icons.active : icons.default)
      }
      marker.setZIndexOffset(active ? 1000 : 0)
    })
    if (pan && id) {
      const active = markerRefsRef.current.get(id)
      // Only move the map when the highlighted pin is out of view, so
      // hovering down the list does not keep dragging it around.
      if (active && !map.getBounds().contains(active.getLatLng())) {
        map.panTo(active.getLatLng(), { animate: true })
      }
    }
  }

  useEffect(() => {
    highlight(activeId, true)
  }, [activeId])

  useEffect(() => {
    const container = containerRef.current
    if (!container || typeof ResizeObserver === "undefined") return
    // Leaflet measures its container once at init. When the container is
    // resized - or shown after starting hidden, e.g. the mobile "Arată harta"
    // toggle - the map must re-measure, and re-frame if it had no size before.
    let hadSize = container.clientWidth > 0 && container.clientHeight > 0
    const observer = new ResizeObserver(() => {
      const map = mapRef.current
      if (!map) return
      const hasSize = container.clientWidth > 0 && container.clientHeight > 0
      map.invalidateSize()
      if (hasSize && !hadSize) applyView()
      hadSize = hasSize
    })
    observer.observe(container)
    return () => observer.disconnect()
  }, [])

  useEffect(() => {
    return () => {
      mapRef.current?.remove()
      mapRef.current = null
      markersLayerRef.current = null
    }
  }, [])

  return (
    <div
      ref={containerRef}
      style={height ? { height } : undefined}
      className={cn(
        // `isolate` is load-bearing, not cosmetic: Leaflet gives its panes
        // z-index 400-700 and its zoom controls 1000, while nothing in this
        // app goes above z-50. Without a stacking context here those numbers
        // compete with the whole page and the map draws over dialogs - which
        // is how the photo lightbox ended up with a map floating on top of it.
        // Isolating scopes Leaflet's z-indexes to this container.
        "isolate w-full overflow-hidden rounded-lg border border-border/60",
        !height && "h-full",
        className
      )}
    />
  )
}
