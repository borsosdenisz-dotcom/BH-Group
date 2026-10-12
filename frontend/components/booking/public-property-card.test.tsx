import { describe, expect, it } from "vitest"
import { renderWithProviders, screen } from "@/test/utils"
import type { PublicPropertySummaryResponse } from "@/lib/api/types"
import { PublicPropertyCard } from "./public-property-card"

function summary(overrides: Partial<PublicPropertySummaryResponse> = {}): PublicPropertySummaryResponse {
  return {
    id: "prop-1",
    name: "Apartament Test",
    city: "Cluj-Napoca",
    county: null,
    latitude: null,
    longitude: null,
    propertyType: "APARTMENT",
    bedrooms: 1,
    bathrooms: 1,
    maxGuests: 2,
    basePricePerNight: 300,
    currency: "RON",
    coverPhotoUrl: null,
    facilities: [],
    demo: false,
    ...overrides,
  }
}

describe("PublicPropertyCard", () => {
  it("labels a demo property 'Demo'", () => {
    renderWithProviders(<PublicPropertyCard property={summary({ demo: true })} />)

    expect(screen.getByText("Demo")).toBeInTheDocument()
  })

  it("has no demo label on a normal property", () => {
    renderWithProviders(<PublicPropertyCard property={summary()} />)

    expect(screen.queryByText("Demo")).not.toBeInTheDocument()
  })
})
