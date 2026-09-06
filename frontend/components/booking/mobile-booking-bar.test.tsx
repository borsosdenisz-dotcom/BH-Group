import { describe, expect, it } from "vitest"
import { screen, renderWithProviders } from "@/test/utils"
import { MobileBookingBar } from "./mobile-booking-bar"
import type { PriceQuoteResponse, PublicPropertyResponse } from "@/lib/api/types"

const baseProperty: PublicPropertyResponse = {
  id: "prop-1",
  name: "Apartament Test",
  description: null,
  propertyType: "APARTMENT",
  addressLine: null,
  city: "Cluj-Napoca",
  county: null,
  country: "România",
  latitude: null,
  longitude: null,
  exactLocation: false,
  bedrooms: 2,
  bathrooms: 1,
  maxGuests: 4,
  minStayNights: null,
  maxStayNights: null,
  sizeSqm: null,
  basePricePerNight: 300,
  currency: "RON",
  checkInTime: "14:00:00",
  checkOutTime: "11:00:00",
  facilities: [],
  photos: [],
}

const baseQuote: PriceQuoteResponse = {
  available: true,
  unavailableReason: null,
  checkInDate: "2026-09-01",
  checkOutDate: "2026-09-05",
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

const bookingHref = "/book/prop-1/rezerva?checkIn=2026-09-01&checkOut=2026-09-05&guests=2"

function renderBar(overrides: Partial<React.ComponentProps<typeof MobileBookingBar>> = {}) {
  return renderWithProviders(
    <MobileBookingBar
      property={baseProperty}
      checkIn={null}
      checkOut={null}
      quote={undefined}
      isQuoteLoading={false}
      bookingHref={bookingHref}
      {...overrides}
    />
  )
}

describe("MobileBookingBar", () => {
  it("shows the total and night count when a valid quote is available", () => {
    renderBar({ checkIn: "2026-09-01", checkOut: "2026-09-05", quote: baseQuote })
    expect(screen.getByText("1.200 RON")).toBeInTheDocument()
    expect(screen.getByText("4 nopți")).toBeInTheDocument()
    expect(screen.getByRole("link", { name: "Rezervă" })).toHaveAttribute("href", bookingHref)
  })

  it("shows 'de la X/noapte' when no dates are selected but a base price exists", () => {
    renderBar({ checkIn: null, checkOut: null })
    expect(screen.getByText("de la 300 RON")).toBeInTheDocument()
    expect(screen.getByText("/ noapte")).toBeInTheDocument()
  })

  it("shows 'Selectează perioada' when there is no base price and no dates", () => {
    renderBar({ property: { ...baseProperty, basePricePerNight: null }, checkIn: null, checkOut: null })
    // Both the price placeholder and the disabled CTA legitimately say the same thing here.
    expect(screen.getByText("Selectează perioada", { selector: "span" })).toBeInTheDocument()
    expect(screen.getByText("Selectează perioada", { selector: "button" })).toBeInTheDocument()
  })

  it("disables the CTA with a distinct label (not 'Rezervă') when no dates are selected", () => {
    renderBar({ checkIn: null, checkOut: null })
    const cta = screen.getByRole("button", { name: "Selectează perioada" })
    expect(cta).toBeDisabled()
    expect(screen.queryByRole("button", { name: "Rezervă" })).not.toBeInTheDocument()
  })

  it("disables the CTA while the quote is loading", () => {
    renderBar({ checkIn: "2026-09-01", checkOut: "2026-09-05", isQuoteLoading: true, quote: undefined })
    expect(screen.getByRole("button", { name: "..." })).toBeDisabled()
  })

  it("disables the CTA with 'Indisponibilă' when the quote is unavailable", () => {
    renderBar({
      checkIn: "2026-09-01",
      checkOut: "2026-09-05",
      quote: { ...baseQuote, available: false, unavailableReason: "Ocupat" },
    })
    expect(screen.getByRole("button", { name: "Indisponibilă" })).toBeDisabled()
  })

  it("enables the CTA as a link only when the quote is available", () => {
    renderBar({ checkIn: "2026-09-01", checkOut: "2026-09-05", quote: baseQuote })
    const cta = screen.getByRole("link", { name: "Rezervă" })
    expect(cta).toHaveAttribute("href", bookingHref)
  })
})
