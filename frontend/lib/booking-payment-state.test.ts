import { describe, expect, it } from "vitest"
import { bookingPaymentOutcome, isFinalOutcome } from "@/lib/booking-payment-state"
import { publicReservation } from "@/test/fixtures/public-reservation"

const now = new Date("2031-01-01T12:00:00Z")
const later = "2031-01-01T12:20:00Z"
const earlier = "2031-01-01T11:50:00Z"

describe("bookingPaymentOutcome", () => {
  it("is confirmed only when the backend says the booking is CONFIRMED", () => {
    expect(bookingPaymentOutcome(publicReservation({ status: "CONFIRMED", holdExpiresAt: null }), now)).toBe(
      "confirmed"
    )
  })

  it("keeps verifying while the booking is still held and the card payment is in flight", () => {
    for (const cardPaymentStatus of [null, "PENDING", "PROCESSING"] as const) {
      expect(
        bookingPaymentOutcome(publicReservation({ status: "PENDING", holdExpiresAt: later, cardPaymentStatus }), now)
      ).toBe("verifying")
    }
  })

  it("never treats a captured payment as a confirmed booking on its own", () => {
    expect(
      bookingPaymentOutcome(
        publicReservation({ status: "PENDING", holdExpiresAt: later, cardPaymentStatus: "SUCCEEDED" }),
        now
      )
    ).toBe("verifying")
  })

  it("reports a declined card as failed while the hold lasts", () => {
    expect(
      bookingPaymentOutcome(
        publicReservation({ status: "PENDING", holdExpiresAt: later, cardPaymentStatus: "FAILED" }),
        now
      )
    ).toBe("failed")
  })

  it("reports an expired hold or a cancelled booking as expired", () => {
    expect(bookingPaymentOutcome(publicReservation({ status: "PENDING", holdExpiresAt: earlier }), now)).toBe(
      "expired"
    )
    expect(
      bookingPaymentOutcome(publicReservation({ status: "CANCELLED", cardPaymentStatus: "CANCELLED" }), now)
    ).toBe("expired")
  })

  it("reports a late payment that was refunded", () => {
    expect(
      bookingPaymentOutcome(publicReservation({ status: "CANCELLED", cardPaymentStatus: "REFUNDED" }), now)
    ).toBe("refunded")
  })
})

describe("isFinalOutcome", () => {
  it("stops polling only for outcomes that can no longer change", () => {
    expect(isFinalOutcome("confirmed")).toBe(true)
    expect(isFinalOutcome("expired")).toBe(true)
    expect(isFinalOutcome("refunded")).toBe(true)
    expect(isFinalOutcome("verifying")).toBe(false)
    expect(isFinalOutcome("failed")).toBe(false)
  })
})
