import { beforeEach, describe, expect, it, vi } from "vitest"
import { renderWithProviders, screen, userEvent, waitFor } from "@/test/utils"
import { mockSearchParams } from "@/test/mocks/next-navigation"
import { publicReservation } from "@/test/fixtures/public-reservation"
import PaymentSuccessPage from "./page"

const { getBookingByToken, startCardCheckout, redirectToExternal } = vi.hoisted(() => ({
  getBookingByToken: vi.fn(),
  startCardCheckout: vi.fn(),
  redirectToExternal: vi.fn(),
}))
vi.mock("@/lib/api/public", () => ({ publicApi: { getBookingByToken, startCardCheckout } }))
vi.mock("@/lib/redirect", () => ({ redirectToExternal }))

describe("PaymentSuccessPage", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockSearchParams.value = new URLSearchParams("token=tok-123")
  })

  it("shows the payment as being verified, not confirmed, while the backend still holds the booking", async () => {
    getBookingByToken.mockResolvedValue(publicReservation({ status: "PENDING", cardPaymentStatus: "PENDING" }))

    renderWithProviders(<PaymentSuccessPage />)

    expect(await screen.findByRole("heading", { name: "Verificăm plata" })).toBeInTheDocument()
    expect(screen.queryByRole("heading", { name: "Rezervare confirmată" })).not.toBeInTheDocument()
    expect(getBookingByToken).toHaveBeenCalledWith("tok-123")
  })

  it("declares the booking confirmed only once the backend reports CONFIRMED", async () => {
    getBookingByToken
      .mockResolvedValueOnce(publicReservation({ status: "PENDING", cardPaymentStatus: "PENDING" }))
      .mockResolvedValue(
        publicReservation({ status: "CONFIRMED", cardPaymentStatus: "SUCCEEDED", holdExpiresAt: null })
      )

    renderWithProviders(<PaymentSuccessPage />)

    expect(await screen.findByRole("heading", { name: "Verificăm plata" })).toBeInTheDocument()
    expect(
      await screen.findByRole("heading", { name: "Rezervare confirmată" }, { timeout: 6000 })
    ).toBeInTheDocument()
  }, 10000)

  it("reports an expired payment and offers a new booking", async () => {
    getBookingByToken.mockResolvedValue(publicReservation({ status: "CANCELLED", cardPaymentStatus: "CANCELLED" }))

    renderWithProviders(<PaymentSuccessPage />)

    expect(await screen.findByRole("heading", { name: "Plata a expirat" })).toBeInTheDocument()
    expect(screen.getByRole("link", { name: "Fă o rezervare nouă" })).toBeInTheDocument()
  })

  it("reports a declined card and opens only one new checkout however often the guest clicks", async () => {
    getBookingByToken.mockResolvedValue(publicReservation({ status: "PENDING", cardPaymentStatus: "FAILED" }))
    startCardCheckout.mockResolvedValue({
      checkoutUrl: "https://checkout.stripe.com/c/pay/cs_1",
      amount: 500,
      currency: "RON",
    })
    const user = userEvent.setup()

    renderWithProviders(<PaymentSuccessPage />)

    const retry = await screen.findByRole("button", { name: "Încearcă din nou plata" })
    expect(screen.getByRole("heading", { name: "Plata nu a reușit" })).toBeInTheDocument()
    await user.click(retry)
    await user.click(retry)

    await waitFor(() => expect(redirectToExternal).toHaveBeenCalledWith("https://checkout.stripe.com/c/pay/cs_1"))
    expect(startCardCheckout).toHaveBeenCalledTimes(1)
    expect(screen.getByRole("button", { name: "Se deschide plata..." })).toBeDisabled()
  })

  it("cannot reach the confirmed state without a token to look up", () => {
    mockSearchParams.value = new URLSearchParams()

    renderWithProviders(<PaymentSuccessPage />)

    expect(screen.getByRole("heading", { name: "Nu am găsit rezervarea" })).toBeInTheDocument()
    expect(getBookingByToken).not.toHaveBeenCalled()
  })
})
