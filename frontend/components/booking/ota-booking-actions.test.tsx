import { describe, expect, it } from "vitest"
import { renderWithProviders, screen, userEvent } from "@/test/utils"
import type { PublicPropertyResponse } from "@/lib/api/types"
import { OtaBookingActions } from "./ota-booking-actions"

function property(overrides: Partial<PublicPropertyResponse> = {}): PublicPropertyResponse {
  return {
    id: "prop-1",
    name: "Apartament Central",
    description: null,
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
    airbnbListingUrl: null,
    bookingListingUrl: null,
    ...overrides,
  }
}

describe("OtaBookingActions", () => {
  it("shows only Airbnb when only its URL is configured", () => {
    renderWithProviders(
      <OtaBookingActions property={property({ airbnbListingUrl: "https://www.airbnb.com/rooms/123" })} />
    )

    const airbnb = screen.getByRole("link", { name: "Verifică disponibilitatea pe Airbnb" })
    expect(airbnb).toHaveAttribute("href", "https://www.airbnb.com/rooms/123")
    expect(airbnb).toHaveAttribute("target", "_blank")
    expect(airbnb).toHaveAttribute("rel", "noopener noreferrer")
    expect(screen.queryByRole("link", { name: /Booking\.com/ })).not.toBeInTheDocument()
  })

  it("shows only Booking.com when only its URL is configured", () => {
    renderWithProviders(
      <OtaBookingActions property={property({ bookingListingUrl: "https://www.booking.com/hotel/ro/test.html" })} />
    )

    expect(screen.getByRole("link", { name: "Verifică disponibilitatea pe Booking.com" })).toHaveAttribute(
      "href",
      "https://www.booking.com/hotel/ro/test.html"
    )
    expect(screen.queryByRole("link", { name: /Airbnb/ })).not.toBeInTheDocument()
  })

  it("shows both configured external listings", () => {
    renderWithProviders(
      <OtaBookingActions
        property={property({
          airbnbListingUrl: "https://www.airbnb.com/rooms/123",
          bookingListingUrl: "https://www.booking.com/hotel/ro/test.html",
        })}
      />
    )

    expect(screen.getByRole("link", { name: "Verifică disponibilitatea pe Airbnb" })).toBeInTheDocument()
    expect(screen.getByRole("link", { name: "Verifică disponibilitatea pe Booking.com" })).toBeInTheDocument()
  })

  it("opens the existing lead dialog when no external listing exists", async () => {
    const user = userEvent.setup()
    renderWithProviders(<OtaBookingActions property={property()} />)

    await user.click(screen.getByRole("button", { name: "Solicită disponibilitatea" }))

    expect(screen.getByRole("dialog")).toBeInTheDocument()
    expect(screen.getByRole("heading", { name: "Solicită disponibilitatea" })).toBeInTheDocument()
    expect(screen.getByDisplayValue(/Apartament Central/)).toBeInTheDocument()
  })

  it("never exposes an external booking link for a demo property", () => {
    renderWithProviders(
      <OtaBookingActions
        property={property({ demo: true, airbnbListingUrl: "https://www.airbnb.com/rooms/123" })}
      />
    )

    expect(screen.getByText(/Anunț demonstrativ/)).toBeInTheDocument()
    expect(screen.queryByRole("link", { name: /Airbnb|Booking\.com/ })).not.toBeInTheDocument()
  })
})
