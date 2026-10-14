import { describe, expect, it } from "vitest"
import {
  COINCIDENT_PIN_SPACING_PX,
  MAX_FIT_ZOOM,
  SINGLE_LOCATION_ZOOM,
  coincidentOffsets,
  escapeHtml,
  hasCoordinates,
  viewFor,
} from "./map-markers"

describe("viewFor", () => {
  it("has nothing to frame without markers", () => {
    expect(viewFor([])).toEqual({ kind: "none" })
  })

  it("centres a single marker at a street-level zoom", () => {
    expect(viewFor([{ id: "a", lat: 46.77, lng: 23.62 }])).toEqual({
      kind: "center",
      lat: 46.77,
      lng: 23.62,
      zoom: SINGLE_LOCATION_ZOOM,
    })
  })

  // Regression: two listings at the same coordinates have a zero-area bounding
  // box, and fitting it zoomed to level 19 - a near-blank map.
  it("centres several markers on the same spot instead of fitting a zero-area box", () => {
    const view = viewFor([
      { id: "a", lat: 46.7657106, lng: 23.6216338 },
      { id: "b", lat: 46.7657106, lng: 23.6216338 },
    ])
    expect(view).toMatchObject({ kind: "center", zoom: SINGLE_LOCATION_ZOOM })
  })

  it("fits distinct markers, capped at a sensible zoom", () => {
    const view = viewFor([
      { id: "a", lat: 46.77, lng: 23.62 },
      { id: "b", lat: 46.75, lng: 23.59 },
    ])
    expect(view).toEqual({
      kind: "fit",
      points: [
        [46.77, 23.62],
        [46.75, 23.59],
      ],
      maxZoom: MAX_FIT_ZOOM,
    })
  })
})

describe("coincidentOffsets", () => {
  it("leaves markers alone at their spot unshifted", () => {
    const offsets = coincidentOffsets([
      { id: "a", lat: 46.77, lng: 23.62 },
      { id: "b", lat: 46.75, lng: 23.59 },
    ])
    expect(offsets.get("a")).toBe(0)
    expect(offsets.get("b")).toBe(0)
  })

  it("spreads markers sharing coordinates side by side, centred on the spot", () => {
    const offsets = coincidentOffsets([
      { id: "a", lat: 46.7657106, lng: 23.6216338 },
      { id: "b", lat: 46.7657106, lng: 23.6216338 },
      { id: "c", lat: 46.7657106, lng: 23.6216338 },
    ])
    expect([offsets.get("a"), offsets.get("b"), offsets.get("c")]).toEqual([
      -COINCIDENT_PIN_SPACING_PX,
      0,
      COINCIDENT_PIN_SPACING_PX,
    ])
  })
})

describe("hasCoordinates", () => {
  it("accepts finite numbers only", () => {
    expect(hasCoordinates({ lat: 46.7, lng: 23.6 })).toBe(true)
    expect(hasCoordinates({ lat: 0, lng: 0 })).toBe(true)
    expect(hasCoordinates({ lat: null, lng: 23.6 })).toBe(false)
    expect(hasCoordinates({ lat: 46.7, lng: undefined })).toBe(false)
    expect(hasCoordinates({ lat: Number.NaN, lng: 23.6 })).toBe(false)
  })
})

describe("escapeHtml", () => {
  it("escapes markup in names going into Leaflet HTML", () => {
    expect(escapeHtml(`<img src=x onerror="alert('x')">&`)).toBe(
      "&lt;img src=x onerror=&quot;alert(&#39;x&#39;)&quot;&gt;&amp;"
    )
  })
})
