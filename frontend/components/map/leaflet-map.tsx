"use client"

import { useEffect, useRef } from "react"
import "leaflet/dist/leaflet.css"
import { cn } from "@/lib/utils"

export interface MapMarker {
  id: string
  lat: number
  lng: number
  label: string
  href?: string
}

const MARKER_ICON_BASE = "https://unpkg.com/leaflet@1.9.4/dist/images"

export default function LeafletMap({
  markers,
  height,
  activeId,
}: {
  markers: MapMarker[]
  height?: number
  activeId?: string | null
}) {
  const containerRef = useRef<HTMLDivElement>(null)
  const mapRef = useRef<import("leaflet").Map | null>(null)
  const markersLayerRef = useRef<import("leaflet").LayerGroup | null>(null)
  const markerRefsRef = useRef<Map<string, import("leaflet").Marker>>(new Map())
  const iconsRef = useRef<{
    default: import("leaflet").Icon
    active: import("leaflet").Icon
  } | null>(null)

  useEffect(() => {
    let cancelled = false

    async function init() {
      const L = (await import("leaflet")).default

      if (cancelled || !containerRef.current) return

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

      if (!mapRef.current) {
        mapRef.current = L.map(containerRef.current, { scrollWheelZoom: false })
        L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
          attribution: "&copy; OpenStreetMap contributors",
          maxZoom: 19,
        }).addTo(mapRef.current)
        markersLayerRef.current = L.layerGroup().addTo(mapRef.current)
      }

      const map = mapRef.current
      const markersLayer = markersLayerRef.current
      if (!markersLayer) return

      markersLayer.clearLayers()
      markerRefsRef.current.clear()

      const validMarkers = markers.filter(
        (m) => Number.isFinite(m.lat) && Number.isFinite(m.lng)
      )

      if (validMarkers.length === 0) return

      validMarkers.forEach((marker) => {
        const leafletMarker = L.marker([marker.lat, marker.lng], { icon: icons.default }).addTo(
          markersLayer
        )
        const popupContent = marker.href
          ? `<a href="${marker.href}" style="font-weight:500;">${marker.label}</a>`
          : `<span style="font-weight:500;">${marker.label}</span>`
        leafletMarker.bindPopup(popupContent)
        markerRefsRef.current.set(marker.id, leafletMarker)
      })

      if (validMarkers.length === 1) {
        map.setView([validMarkers[0].lat, validMarkers[0].lng], 14)
      } else {
        const bounds = L.latLngBounds(validMarkers.map((m) => [m.lat, m.lng]))
        map.fitBounds(bounds, { padding: [32, 32] })
      }
    }

    init()

    return () => {
      cancelled = true
    }
  }, [markers])

  useEffect(() => {
    const map = mapRef.current
    const icons = iconsRef.current
    if (!map || !icons) return

    markerRefsRef.current.forEach((marker, id) => {
      marker.setIcon(id === activeId ? icons.active : icons.default)
      marker.setZIndexOffset(id === activeId ? 1000 : 0)
    })

    if (activeId) {
      const active = markerRefsRef.current.get(activeId)
      if (active) {
        map.panTo(active.getLatLng(), { animate: true })
      }
    }
  }, [activeId])

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
        !height && "h-full"
      )}
    />
  )
}
