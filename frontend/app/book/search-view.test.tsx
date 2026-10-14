import { beforeEach, describe, expect, it, vi } from "vitest"
import { act, renderWithProviders, screen, userEvent, waitFor } from "@/test/utils"
import { mockSearchParams } from "@/test/mocks/next-navigation"
import type { MapMarker } from "@/components/map/leaflet-map"
import type { PublicPropertySummaryResponse } from "@/lib/api/types"
import { BookingSearchView } from "./search-view"

const { searchProperties, mapProps } = vi.hoisted(() => ({
  searchProperties: vi.fn(),
  mapProps: { current: null as null | {
    markers: MapMarker[]
    activeId?: string | null
    onMarkerHover?: (id: string | null) => void
    onMarkerClick?: (id: string) => void
  } },
}))
vi.mock("@/lib/api/public", () => ({ publicApi: { searchProperties } }))

// The real map needs a browser; here only what the page hands it matters.
vi.mock("@/components/map/leaflet-map", () => ({
  default: (props: NonNullable<typeof mapProps.current>) => {
    mapProps.current = props
    return <div data-testid="leaflet-map" />
  },
}))

function property(overrides: Partial<PublicPropertySummaryResponse>): PublicPropertySummaryResponse {
  return {
    id: "p",
    name: "Proprietate",
    city: "Cluj-Napoca",
    county: null,
    latitude: 46.77,
    longitude: 23.62,
    propertyType: "APARTMENT",
    bedrooms: 1,
    bathrooms: 1,
    maxGuests: 2,
    basePricePerNight: 250,
    currency: "RON",
    coverPhotoUrl: null,
    facilities: [],
    demo: false,
    ...overrides,
  }
}

const listing = [
  property({ id: "a", name: "Apartament A", basePricePerNight: 250 }),
  property({ id: "b", name: "Apartament fără locație", latitude: null, longitude: null }),
  property({ id: "c", name: "Apartament C", basePricePerNight: null, latitude: 46.75, longitude: 23.59 }),
]

function card(id: string) {
  return document.querySelector(`[data-property-card="${id}"]`) as HTMLElement
}

describe("BookingSearchView", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mapProps.current = null
    mockSearchParams.value = new URLSearchParams()
    searchProperties.mockResolvedValue({
      content: listing, page: 0, size: 12, totalElements: 3, totalPages: 1, first: true, last: true,
    })
  })

  it("lists every property but puts only located ones on the map, each with its price", async () => {
    renderWithProviders(<BookingSearchView />)

    expect(await screen.findByText("Apartament fără locație")).toBeInTheDocument()
    expect(screen.getByText("Apartament A")).toBeInTheDocument()
    expect(screen.getByText("Apartament C")).toBeInTheDocument()

    await waitFor(() => expect(mapProps.current).not.toBeNull())
    expect(mapProps.current!.markers.map((m) => [m.id, m.price])).toEqual([
      ["a", "250 RON"],
      ["c", "La cerere"],
    ])
  })

  it("highlights the card of a hovered pin, and the pin of a hovered card", async () => {
    const user = userEvent.setup()
    renderWithProviders(<BookingSearchView />)
    await screen.findByText("Apartament A")
    await waitFor(() => expect(mapProps.current).not.toBeNull())

    act(() => mapProps.current!.onMarkerHover!("c"))
    expect(card("c")).toHaveAttribute("data-active")
    expect(card("a")).not.toHaveAttribute("data-active")

    act(() => mapProps.current!.onMarkerHover!(null))
    expect(card("c")).not.toHaveAttribute("data-active")

    await user.hover(card("a"))
    expect(mapProps.current!.activeId).toBe("a")
    await user.unhover(card("a"))
    expect(mapProps.current!.activeId).toBeNull()
  })

  it("keeps a clicked pin's card highlighted after the pointer leaves", async () => {
    renderWithProviders(<BookingSearchView />)
    await screen.findByText("Apartament A")
    await waitFor(() => expect(mapProps.current).not.toBeNull())

    act(() => {
      mapProps.current!.onMarkerClick!("a")
      mapProps.current!.onMarkerHover!(null)
    })

    expect(card("a")).toHaveAttribute("data-active")
  })

  it("hides the map below desktop until 'Arată harta'", async () => {
    const user = userEvent.setup()
    renderWithProviders(<BookingSearchView />)
    await screen.findByText("Apartament A")

    const mapSection = screen.getByTestId("results-map")
    expect(mapSection).toHaveClass("hidden", "lg:block")

    await user.click(screen.getByRole("button", { name: "Arată harta" }))
    expect(mapSection).toHaveClass("block")
    expect(mapSection).not.toHaveClass("hidden")
    expect(screen.getByRole("button", { name: "Ascunde harta" })).toHaveAttribute("aria-expanded", "true")
  })
})
