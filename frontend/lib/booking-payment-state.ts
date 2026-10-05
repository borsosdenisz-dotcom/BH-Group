import type { PublicReservationResponse } from "@/lib/api/types"

/**
 * What the guest should be told after coming back from Stripe. Only the
 * backend's own record counts: landing on the success page proves nothing,
 * so "confirmed" is reported solely when the booking itself is CONFIRMED
 * (which only the signed Stripe webhook can make it).
 */
export type BookingPaymentOutcome = "verifying" | "confirmed" | "failed" | "expired" | "refunded"

const CONFIRMED_STATUSES = new Set(["CONFIRMED", "CHECKED_IN", "CHECKED_OUT"])

export function bookingPaymentOutcome(
  reservation: Pick<PublicReservationResponse, "status" | "cardPaymentStatus" | "holdExpiresAt">,
  now: Date = new Date()
): BookingPaymentOutcome {
  if (CONFIRMED_STATUSES.has(reservation.status)) return "confirmed"

  if (reservation.status !== "PENDING") {
    // A payment that landed after the hold was released is refunded in full.
    return reservation.cardPaymentStatus === "REFUNDED" ? "refunded" : "expired"
  }

  if (reservation.holdExpiresAt && new Date(reservation.holdExpiresAt).getTime() < now.getTime()) {
    return "expired"
  }
  if (reservation.cardPaymentStatus === "FAILED") return "failed"
  return "verifying"
}

/** Outcomes after which polling the backend again can no longer change anything. */
export function isFinalOutcome(outcome: BookingPaymentOutcome) {
  return outcome === "confirmed" || outcome === "expired" || outcome === "refunded"
}
