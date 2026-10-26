import { afterAll, beforeAll, beforeEach, describe, expect, it, vi } from "vitest"
import { render, waitFor } from "@testing-library/react"
import LeafletMap from "./leaflet-map"

// Leaflet touches real DOM geometry on init, which jsdom does not provide.
// What matters here is what the component asks Leaflet to do.
const leaflet = vi.hoisted(() => {
  const map = {
    setView: vi.fn(),
    fitBounds: vi.fn(),
    panTo: vi.fn(),
    remove: vi.fn(),
    invalidateSize: vi.fn(),
    getBounds: vi.fn((): { contains: (point: unknown) => boolean } => ({ contains: () => true })),
  }
  const layer = { clearLayers: vi.fn() }
  const makeMarker = () => {
    const marker = {
      addTo: vi.fn(() => marker),
      bindPopup: vi.fn(),
      on: vi.fn(),
      setIcon: vi.fn(),
      setZIndexOffset: vi.fn(),
      getElement: vi.fn(() => undefined),
      getLatLng: vi.fn(),
    }
    return marker
  }
  return {
    map,
    layer,
    divIcon: vi.fn((options: unknown) => ({ divIcon: options })),
    marker: vi.fn(() => makeMarker()),
  }
})

vi.mock("leaflet", () => ({
  default: {
    map: () => leaflet.map,
    tileLayer: () => ({ addTo: vi.fn() }),
    layerGroup: () => ({ addTo: () => leaflet.layer }),
    icon: (options: unknown) => options,
    divIcon: leaflet.divIcon,
    marker: leaflet.marker,
    latLngBounds: (v: unknown) => v,
  },
}))

// jsdom lays nothing out; the map only frames markers once it has a size.
const sizeProps = ["clientWidth", "clientHeight"] as const
let layoutSize = 400
beforeAll(() => {
  for (const prop of sizeProps) {
    Object.defineProperty(HTMLElement.prototype, prop, { configurable: true, get: () => layoutSize })
  }
})
afterAll(() => {
  for (const prop of sizeProps) {
    delete (HTMLElement.prototype as unknown as Record<string, unknown>)[prop]
  }
})

beforeEach(() => {
  vi.clearAllMocks()
  leaflet.map.getBounds.mockImplementation(() => ({ contains: () => true }))
  layoutSize = 400
})

describe("LeafletMap", () => {
  /**
   * Leaflet hard-codes z-index 400-700 on its panes and 1000 on its zoom
   * controls, while nothing in this app goes above z-50. Without a stacking
   * context on the container those numbers compete with the whole page, and
   * the map draws on top of overlays - which is how a map ended up floating
   * over the photo lightbox. `isolate` scopes them to the container.
   */
  it("isolates its stacking context so Leaflet cannot paint over dialogs", () => {
    const { container } = render(
      <LeafletMap markers={[{ id: "a", lat: 46.77, lng: 23.59, label: "Cluj" }]} height={300} />
    )

    const mapContainer = container.firstElementChild
    expect(mapContainer).toHaveClass("isolate")
  })

  it("draws one price pin per located property and skips those without coordinates", async () => {
    render(
      <LeafletMap
        markers={[
          { id: "a", lat: 46.77, lng: 23.62, label: "A", price: "250 RON" },
          { id: "b", lat: Number.NaN, lng: Number.NaN, label: "B", price: "300 RON" },
          { id: "c", lat: 46.75, lng: 23.59, label: "C", price: "La cerere" },
        ]}
      />
    )

    await waitFor(() => expect(leaflet.marker).toHaveBeenCalledTimes(2))
    const pinHtml = leaflet.divIcon.mock.calls.map(([options]) => (options as { html: string }).html)
    expect(pinHtml).toHaveLength(2)
    expect(pinHtml[0]).toContain("250 RON")
    expect(pinHtml[1]).toContain("La cerere")
    expect(leaflet.map.fitBounds).toHaveBeenCalledWith(
      [
        [46.77, 23.62],
        [46.75, 23.59],
      ],
      expect.objectContaining({ maxZoom: 15 })
    )
  })

  it("centres two properties at the same address instead of zooming to the maximum", async () => {
    render(
      <LeafletMap
        markers={[
          { id: "a", lat: 46.7657106, lng: 23.6216338, label: "A", price: "250 RON" },
          { id: "b", lat: 46.7657106, lng: 23.6216338, label: "B", price: "300 RON" },
        ]}
      />
    )

    await waitFor(() => expect(leaflet.map.setView).toHaveBeenCalledWith([46.7657106, 23.6216338], 14))
    expect(leaflet.map.fitBounds).not.toHaveBeenCalled()
    // Both pins are drawn, pushed apart so neither hides the other.
    const offsets = leaflet.divIcon.mock.calls.map(([options]) =>
      /calc\(-50% \+ (-?\d+)px\)/.exec((options as { html: string }).html)?.[1]
    )
    expect(offsets).toEqual(["-38", "38"])
  })

  it("keeps the default pin icon when no price is given (single-property map)", async () => {
    render(<LeafletMap markers={[{ id: "a", lat: 46.77, lng: 23.62, label: "A" }]} height={300} />)

    await waitFor(() => expect(leaflet.marker).toHaveBeenCalledTimes(1))
    expect(leaflet.divIcon).not.toHaveBeenCalled()
    expect(leaflet.map.setView).toHaveBeenCalledWith([46.77, 23.62], 14)
  })

  it("escapes property names in popups", async () => {
    render(<LeafletMap markers={[{ id: "a", lat: 46.77, lng: 23.62, label: "<b>x</b>", href: "/book/a" }]} />)

    await waitFor(() => expect(leaflet.marker).toHaveBeenCalled())
    const marker = leaflet.marker.mock.results[0].value as { bindPopup: ReturnType<typeof vi.fn> }
    expect(marker.bindPopup).toHaveBeenCalledWith(expect.stringContaining("&lt;b&gt;x&lt;/b&gt;"))
  })

  /**
   * Below lg the listings map is mounted hidden (display: none) until "Arată
   * harta", so Leaflet never gets a center and zoom. A tap on a property card
   * still fires mouseenter on touch devices, which highlights its pin - and
   * Leaflet's getBounds() throws "Set map center and zoom first." on a map
   * with no view. That crashed /book on every phone instead of opening the
   * property.
   */
  it("highlights without panning while the map is hidden and has no view yet", async () => {
    layoutSize = 0
    leaflet.map.getBounds.mockImplementation(() => {
      throw new Error("Set map center and zoom first.")
    })
    const markers = [
      { id: "a", lat: 46.77, lng: 23.62, label: "A", price: "250 RON" },
      { id: "b", lat: 46.75, lng: 23.59, label: "B", price: "300 RON" },
    ]
    const { rerender } = render(<LeafletMap markers={markers} activeId={null} />)
    await waitFor(() => expect(leaflet.marker).toHaveBeenCalledTimes(2))
    expect(leaflet.map.fitBounds).not.toHaveBeenCalled()

    expect(() => rerender(<LeafletMap markers={markers} activeId="a" />)).not.toThrow()
    expect(leaflet.map.getBounds).not.toHaveBeenCalled()
    expect(leaflet.map.panTo).not.toHaveBeenCalled()
    const pinA = leaflet.marker.mock.results[0].value as { setZIndexOffset: ReturnType<typeof vi.fn> }
    expect(pinA.setZIndexOffset).toHaveBeenLastCalledWith(1000)
  })

  it("pans to an off-screen pin once the map has been framed", async () => {
    leaflet.map.getBounds.mockImplementation(() => ({ contains: () => false }))
    const markers = [
      { id: "a", lat: 46.77, lng: 23.62, label: "A", price: "250 RON" },
      { id: "b", lat: 46.75, lng: 23.59, label: "B", price: "300 RON" },
    ]
    const { rerender } = render(<LeafletMap markers={markers} activeId={null} />)
    await waitFor(() => expect(leaflet.map.fitBounds).toHaveBeenCalled())

    rerender(<LeafletMap markers={markers} activeId="b" />)
    expect(leaflet.map.panTo).toHaveBeenCalledTimes(1)
  })
})
