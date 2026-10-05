import type { PublicReservationResponse } from "@/lib/api/types"

export function publicReservation(overrides: Partial<PublicReservationResponse> = {}): PublicReservationResponse {
  return {
    id: "res-1",
    propertyName: "Apartament Test",
    propertyCity: "Cluj-Napoca",
    guestFirstName: "Ana",
    guestLastName: "Popescu",
    guestEmail: "ana@example.com",
    guestPhone: "0700000000",
    checkInDate: "2031-09-01",
    checkOutDate: "2031-09-05",
    numberOfGuests: 2,
    status: "PENDING",
    totalAmount: 500,
    currency: "RON",
    managementToken: "tok-123",
    lateCheckoutAvailable: false,
    lateCheckoutTime: null,
    lateCheckoutFee: null,
    holdExpiresAt: new Date(Date.now() + 30 * 60 * 1000).toISOString(),
    cardPaymentStatus: null,
    ...overrides,
  }
}
