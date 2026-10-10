import { describe, expect, it, vi } from "vitest"
import { render } from "@testing-library/react"
import LeafletMap from "./leaflet-map"

// Leaflet touches real DOM geometry on init, which jsdom does not provide.
// Nothing here depends on the map actually drawing - the regression being
// pinned is a CSS property on the container.
vi.mock("leaflet", () => {
  const map = {
    setView: vi.fn(),
    fitBounds: vi.fn(),
    panTo: vi.fn(),
    remove: vi.fn(),
  }
  return {
    default: {
      map: () => map,
      tileLayer: () => ({ addTo: vi.fn() }),
      layerGroup: () => ({ addTo: () => ({ clearLayers: vi.fn() }) }),
      icon: (options: unknown) => options,
      marker: () => ({ addTo: vi.fn(), bindPopup: vi.fn(), setIcon: vi.fn() }),
      latLngBounds: (v: unknown) => v,
    },
  }
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
})
