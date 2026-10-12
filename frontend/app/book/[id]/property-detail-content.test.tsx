import { beforeEach, describe, expect, it, vi } from "vitest"
import { renderWithProviders, screen, waitFor } from "@/test/utils"
import { mockSearchParams } from "@/test/mocks/next-navigation"
import type { PriceQuoteResponse, PublicPropertyResponse } from "@/lib/api/types"
import { PropertyDetailContent } from "./property-detail-content"

const { getProperty, getPaymentConfig, getQuote } = vi.hoisted(() => ({
  getProperty: vi.fn(),
  getPaymentConfig: vi.fn(),
  getQuote: vi.fn(),
}))
vi.mock("@/lib/api/public", () => ({ publicApi: { getProperty, getPaymentConfig, getQuote } }))

// Gallery, map and calendar have their own tests; here only the booking
// affordances around them matter.
vi.mock("@/components/booking/property-gallery", () => ({ PropertyGallery: () => null }))
vi.mock("@/components/booking/property-location", () => ({ PropertyLocation: () => null }))
vi.mock("@/components/booking/availability-calendar", () => ({ AvailabilityCalendar: () => null }))

function property(overrides: Partial<PublicPropertyResponse> = {}): PublicPropertyResponse {
  return {
    id: "prop-1",
    name: "Apartament Test",
    description: "Un apartament luminos.",
    propertyType: "APARTMENT",
    addressLine: null,
    city: "Cluj-Napoca",
    county: null,
    country: "România",
    latitude: null,
    longitude: null,
    exactLocation: false,
    bedrooms: 1,
    bathrooms: 1,
    maxGuests: 2,
    minStayNights: null,
    maxStayNights: null,
    sizeSqm: null,
    basePricePerNight: 300,
    currency: "RON",
    checkInTime: "14:00:00",
    checkOutTime: "11:00:00",
    facilities: [],
    photos: [],
    demo: false,
    ...overrides,
  }
}

const availableQuote: PriceQuoteResponse = {
  available: true,
  unavailableReason: null,
  checkInDate: "2031-09-01",
  checkOutDate: "2031-09-05",
  nights: 4,
  subtotal: 1200,
  extraGuestFee: 0,
  cleaningFee: 0,
  discountPercent: null,
  discountAmount: 0,
  totalAmount: 1200,
  currency: "RON",
  minStayNights: null,
  maxStayNights: null,
}

describe("PropertyDetailContent", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // Dates already chosen - a bookable property would offer "Rezervă" now.
    mockSearchParams.value = new URLSearchParams("checkIn=2031-09-01&checkOut=2031-09-05&guests=2")
    getPaymentConfig.mockResolvedValue({ cardPaymentsEnabled: true, publishableKey: "pk_test" })
    getQuote.mockResolvedValue(availableQuote)
  })

  it("shows the demo banner and no way to book a demo property", async () => {
    getProperty.mockResolvedValue(property({ demo: true }))

    renderWithProviders(<PropertyDetailContent id="prop-1" />)

    expect(await screen.findByText("Anunț demonstrativ – nu se poate rezerva")).toBeInTheDocument()
    // The price stays as an example...
    expect(screen.getByText("300 RON")).toBeInTheDocument()
    // ...but there is no "Rezervă" anywhere, and no quote is requested.
    expect(screen.queryByRole("link", { name: "Rezervă" })).not.toBeInTheDocument()
    expect(screen.queryByRole("button", { name: "Rezervă" })).not.toBeInTheDocument()
    expect(screen.queryByText("Rezervă")).not.toBeInTheDocument()
    expect(getQuote).not.toHaveBeenCalled()
  })

  it("is unchanged for a normal property: no banner, and 'Rezervă' once the dates are quoted", async () => {
    getProperty.mockResolvedValue(property())

    renderWithProviders(<PropertyDetailContent id="prop-1" />)

    await waitFor(() => expect(screen.getAllByRole("link", { name: "Rezervă" }).length).toBeGreaterThan(0))
    expect(screen.queryByText("Anunț demonstrativ – nu se poate rezerva")).not.toBeInTheDocument()
    expect(getQuote).toHaveBeenCalled()
  })
})
