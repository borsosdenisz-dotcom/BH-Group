import { beforeEach, describe, expect, it, vi } from "vitest"
import { renderWithProviders, screen, userEvent } from "@/test/utils"
import { mockSearchParams } from "@/test/mocks/next-navigation"
import { publicReservation } from "@/test/fixtures/public-reservation"
import type { PublicBookingCheckoutResponse } from "@/lib/api/types"
import { BookingContent } from "./booking-content"

const { getProperty, getPaymentConfig, redirectToExternal, bookingFormRendered } = vi.hoisted(() => ({
  getProperty: vi.fn(),
  getPaymentConfig: vi.fn(),
  redirectToExternal: vi.fn(),
  bookingFormRendered: vi.fn(),
}))
vi.mock("@/lib/api/public", () => ({ publicApi: { getProperty, getPaymentConfig } }))
vi.mock("@/lib/redirect", () => ({ redirectToExternal }))

const heldBooking: PublicBookingCheckoutResponse = {
  reservation: publicReservation(),
  checkoutUrl: "https://checkout.stripe.com/c/pay/cs_1",
  amount: 500,
  currency: "RON",
}

// The real form (fields, availability, quote, the ONLINE_CARD payload) is
// covered elsewhere; here it only matters what happens around it.
vi.mock("@/components/booking/booking-form", () => ({
  BookingForm: (props: {
    onSuccess: (booking: PublicBookingCheckoutResponse) => void
    submitLabel?: string
    busy?: boolean
  }) => {
    bookingFormRendered()
    return (
      <button type="button" disabled={props.busy} onClick={() => props.onSuccess(heldBooking)}>
        {props.submitLabel}
      </button>
    )
  },
}))

describe("BookingContent", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockSearchParams.value = new URLSearchParams()
    getProperty.mockResolvedValue({ id: "prop-1", name: "Apartament Test" })
  })

  it("offers card as the only way to pay when Stripe is active - no transfer, on-arrival or manual approval", async () => {
    getPaymentConfig.mockResolvedValue({ cardPaymentsEnabled: true, publishableKey: "pk_test" })

    renderWithProviders(<BookingContent id="prop-1" />)

    expect(await screen.findByRole("button", { name: "Continuă către plată" })).toBeEnabled()
    expect(screen.getByText(/Plata se face online, cu cardul/)).toBeInTheDocument()
    expect(screen.queryByText(/transfer/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/la sosire/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/confirmă manual|aprobat|așteaptă confirmarea/i)).not.toBeInTheDocument()
  })

  it("sends the guest straight to Stripe Checkout once the booking is held, and locks the form", async () => {
    getPaymentConfig.mockResolvedValue({ cardPaymentsEnabled: true, publishableKey: "pk_test" })
    const user = userEvent.setup()

    renderWithProviders(<BookingContent id="prop-1" />)
    await user.click(await screen.findByRole("button", { name: "Continuă către plată" }))

    expect(redirectToExternal).toHaveBeenCalledTimes(1)
    expect(redirectToExternal).toHaveBeenCalledWith("https://checkout.stripe.com/c/pay/cs_1")
    expect(
      screen.getByRole("heading", { name: "Te redirecționăm către plata securizată" })
    ).toBeInTheDocument()
    expect(screen.queryByRole("button", { name: "Continuă către plată" })).not.toBeInTheDocument()
  })

  it("disables booking entirely and shows the contact details when card payment is unavailable", async () => {
    getPaymentConfig.mockResolvedValue({ cardPaymentsEnabled: false, publishableKey: null })

    renderWithProviders(<BookingContent id="prop-1" />)

    expect(await screen.findByText("Rezervarea online nu este momentan disponibilă.")).toBeInTheDocument()
    expect(screen.getByRole("link", { name: "formularul de contact" })).toBeInTheDocument()
    expect(bookingFormRendered).not.toHaveBeenCalled()
    expect(screen.queryByRole("button", { name: /Continuă|Trimite/ })).not.toBeInTheDocument()
    expect(redirectToExternal).not.toHaveBeenCalled()
  })

  it("treats a failure to load the payment configuration as unavailable, never as a manual booking", async () => {
    getPaymentConfig.mockRejectedValue(new Error("network"))

    renderWithProviders(<BookingContent id="prop-1" />)

    expect(await screen.findByText("Rezervarea online nu este momentan disponibilă.")).toBeInTheDocument()
    expect(bookingFormRendered).not.toHaveBeenCalled()
  })
})
