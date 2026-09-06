import { describe, expect, it, vi } from "vitest"
import { screen, renderWithProviders } from "@/test/utils"
import { PropertyBookingCard } from "./property-booking-card"
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

function renderCard(overrides: Partial<React.ComponentProps<typeof PropertyBookingCard>> = {}) {
  const onGuestsChange = vi.fn()
  const onScrollToCalendar = vi.fn()
  const utils = renderWithProviders(
    <PropertyBookingCard
      property={baseProperty}
      checkIn={null}
      checkOut={null}
      guests={2}
      quote={undefined}
      isQuoteLoading={false}
      bookingHref={bookingHref}
      onGuestsChange={onGuestsChange}
      onScrollToCalendar={onScrollToCalendar}
      {...overrides}
    />
  )
  return { ...utils, onGuestsChange, onScrollToCalendar }
}

describe("PropertyBookingCard", () => {
  describe("CTA state", () => {
    it("is disabled with 'Selectează perioada' when no dates are selected", () => {
      renderCard({ checkIn: null, checkOut: null })
      const cta = screen.getByRole("button", { name: "Selectează perioada" })
      expect(cta).toBeDisabled()
    })

    it("is disabled while the quote is loading", () => {
      renderCard({ checkIn: "2026-09-01", checkOut: "2026-09-05", isQuoteLoading: true, quote: undefined })
      const cta = screen.getByRole("button", { name: "Se calculează..." })
      expect(cta).toBeDisabled()
    })

    it("is disabled and shows the unavailable reason when the quote is unavailable", () => {
      renderCard({
        checkIn: "2026-09-01",
        checkOut: "2026-09-05",
        quote: { ...baseQuote, available: false, unavailableReason: "Perioadă deja rezervată" },
      })
      const cta = screen.getByRole("button", { name: "Perioadă indisponibilă" })
      expect(cta).toBeDisabled()
      expect(screen.getByText("Perioadă deja rezervată")).toBeInTheDocument()
    })

    it("falls back to a generic message when the quote is unavailable without a reason", () => {
      renderCard({
        checkIn: "2026-09-01",
        checkOut: "2026-09-05",
        quote: { ...baseQuote, available: false, unavailableReason: null },
      })
      expect(screen.getByText("Perioada selectată nu este disponibilă.")).toBeInTheDocument()
    })

    it("renders an enabled 'Rezervă' link to bookingHref when the quote is available", () => {
      renderCard({ checkIn: "2026-09-01", checkOut: "2026-09-05", quote: baseQuote })
      const cta = screen.getByRole("link", { name: "Rezervă" })
      expect(cta).toHaveAttribute("href", bookingHref)
    })
  })

  describe("guest stepper", () => {
    it("disables the decrement button at 1 guest and never calls onGuestsChange below 1", async () => {
      const { onGuestsChange } = renderCard({ guests: 1 })
      const decrement = screen.getByRole("button", { name: "Scade numărul de oaspeți" })
      expect(decrement).toBeDisabled()
      expect(onGuestsChange).not.toHaveBeenCalled()
    })

    it("disables the increment button at maxGuests and never calls onGuestsChange above it", () => {
      const { onGuestsChange } = renderCard({ guests: 4 })
      const increment = screen.getByRole("button", { name: "Crește numărul de oaspeți" })
      expect(increment).toBeDisabled()
      expect(onGuestsChange).not.toHaveBeenCalled()
    })

    it("calls onGuestsChange with guests-1 / guests+1 when enabled", async () => {
      const { onGuestsChange } = renderCard({ guests: 2 })
      screen.getByRole("button", { name: "Scade numărul de oaspeți" }).click()
      expect(onGuestsChange).toHaveBeenCalledWith(1)
      screen.getByRole("button", { name: "Crește numărul de oaspeți" }).click()
      expect(onGuestsChange).toHaveBeenCalledWith(3)
    })
  })

  describe("price header", () => {
    it("shows basePricePerNight when the property has one", () => {
      renderCard({ property: { ...baseProperty, basePricePerNight: 300 } })
      expect(screen.getByText("300 RON")).toBeInTheDocument()
      expect(screen.getByText("/ noapte")).toBeInTheDocument()
    })

    it("shows a derived '(estimat)' price when basePricePerNight is missing but a quote exists", () => {
      renderCard({
        property: { ...baseProperty, basePricePerNight: null },
        checkIn: "2026-09-01",
        checkOut: "2026-09-05",
        quote: { ...baseQuote, totalAmount: 800, nights: 4 },
      })
      expect(screen.getByText("200 RON")).toBeInTheDocument()
      expect(screen.getByText("/ noapte (estimat)")).toBeInTheDocument()
    })

    it("shows a neutral prompt when there is neither a base price nor a quote", () => {
      renderCard({ property: { ...baseProperty, basePricePerNight: null }, quote: undefined })
      expect(screen.getByText("Selectează perioada pentru preț")).toBeInTheDocument()
    })
  })

  describe("price breakdown", () => {
    it("hides cleaning fee, extra guest fee, and discount when they are zero/absent", () => {
      renderCard({
        checkIn: "2026-09-01",
        checkOut: "2026-09-05",
        quote: { ...baseQuote, cleaningFee: 0, extraGuestFee: 0, discountAmount: 0 },
      })
      expect(screen.queryByText("Taxă curățenie")).not.toBeInTheDocument()
      expect(screen.queryByText("Taxă oaspeți suplimentari")).not.toBeInTheDocument()
      expect(screen.queryByText(/Reducere/)).not.toBeInTheDocument()
      expect(screen.getByText("Total")).toBeInTheDocument()
      // Subtotal and Total both legitimately read "1.200 RON" here since there are no fees/discount.
      expect(screen.getAllByText("1.200 RON")).toHaveLength(2)
    })

    it("shows cleaning fee, extra guest fee, and discount only when each is greater than zero", () => {
      renderCard({
        checkIn: "2026-09-01",
        checkOut: "2026-09-05",
        quote: {
          ...baseQuote,
          cleaningFee: 50,
          extraGuestFee: 30,
          discountAmount: 100,
          discountPercent: 10,
        },
      })
      expect(screen.getByText("Taxă curățenie")).toBeInTheDocument()
      expect(screen.getByText("Taxă oaspeți suplimentari")).toBeInTheDocument()
      expect(screen.getByText("Reducere (10%)")).toBeInTheDocument()
    })
  })
})
