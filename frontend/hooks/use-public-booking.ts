"use client"

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { toast } from "sonner"
import {
  publicApi,
  type PublicBookingPayload,
  type PublicBookingUpdatePayload,
  type PublicPropertySearchParams,
} from "@/lib/api/public"
import { ApiError } from "@/lib/api/types"
import { bookingPaymentOutcome, isFinalOutcome } from "@/lib/booking-payment-state"
import { redirectToExternal } from "@/lib/redirect"

function errorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiError) return error.message
  return fallback
}

export function usePublicProperties(params: PublicPropertySearchParams) {
  return useQuery({
    queryKey: ["public-properties", params],
    queryFn: () => publicApi.searchProperties(params),
  })
}

export function usePublicProperty(id: string) {
  return useQuery({
    queryKey: ["public-property", id],
    queryFn: () => publicApi.getProperty(id),
    enabled: !!id,
  })
}

export function usePublicAvailability(propertyId: string, checkIn: string, checkOut: string) {
  return useQuery({
    queryKey: ["public-availability", propertyId, checkIn, checkOut],
    queryFn: () => publicApi.checkAvailability(propertyId, checkIn, checkOut),
    enabled: !!propertyId && !!checkIn && !!checkOut,
  })
}

export function usePublicQuote(propertyId: string, checkIn: string, checkOut: string, guests: number) {
  return useQuery({
    queryKey: ["public-quote", propertyId, checkIn, checkOut, guests],
    queryFn: () => publicApi.getQuote(propertyId, checkIn, checkOut, guests),
    enabled: !!propertyId && !!checkIn && !!checkOut && guests > 0,
  })
}

export function usePublicCalendar(propertyId: string, from: string, to: string) {
  return useQuery({
    queryKey: ["public-calendar", propertyId, from, to],
    queryFn: () => publicApi.getCalendar(propertyId, from, to),
    enabled: !!propertyId,
  })
}

export function useCreatePublicBooking() {
  return useMutation({
    mutationFn: (payload: PublicBookingPayload) => publicApi.createBooking(payload),
    onError: (error) => {
      toast.error(errorMessage(error, "Rezervarea nu a putut fi finalizată"))
    },
  })
}

export function usePaymentConfig() {
  return useQuery({
    queryKey: ["public-payment-config"],
    queryFn: () => publicApi.getPaymentConfig(),
    staleTime: 5 * 60 * 1000,
  })
}

/**
 * Starts a hosted Stripe Checkout session and sends the guest to Stripe's
 * own page - the card form never renders on our domain. Callers should keep
 * their button disabled while `isPending || isSuccess`: after success the
 * page is already navigating away. The backend also hands a repeated
 * request the same open session, so a second click can never open another.
 */
export function useStartCardCheckout() {
  return useMutation({
    mutationFn: (token: string) => publicApi.startCardCheckout(token),
    onSuccess: (session) => {
      redirectToExternal(session.checkoutUrl)
    },
    onError: (error) => {
      toast.error(errorMessage(error, "Plata cu cardul nu a putut fi inițiată"))
    },
  })
}

export function useBookingByToken(token: string) {
  return useQuery({
    queryKey: ["booking-manage", token],
    queryFn: () => publicApi.getBookingByToken(token),
    enabled: !!token,
    retry: false,
  })
}

const PAYMENT_POLL_INTERVAL_MS = 3000

/**
 * The booking as the backend sees it after the guest returns from Stripe,
 * re-read every few seconds until the outcome is final (confirmed by the
 * webhook, expired, or refunded). Read-only: polling never confirms anything.
 */
export function useBookingPaymentStatus(token: string) {
  return useQuery({
    queryKey: ["booking-manage", token],
    queryFn: () => publicApi.getBookingByToken(token),
    enabled: !!token,
    retry: false,
    refetchInterval: (query) => {
      const reservation = query.state.data
      if (!reservation) return query.state.error ? false : PAYMENT_POLL_INTERVAL_MS
      return isFinalOutcome(bookingPaymentOutcome(reservation)) ? false : PAYMENT_POLL_INTERVAL_MS
    },
  })
}

export function useUpdateBookingByToken(token: string) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (payload: PublicBookingUpdatePayload) => publicApi.updateBookingByToken(token, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["booking-manage", token] })
      toast.success("Rezervarea a fost actualizată")
    },
    onError: (error) => {
      toast.error(errorMessage(error, "Actualizarea rezervării a eșuat"))
    },
  })
}

export function useBookingCancellationQuote(token: string, enabled: boolean) {
  return useQuery({
    queryKey: ["booking-cancellation-quote", token],
    queryFn: () => publicApi.getCancellationQuoteByToken(token),
    enabled: enabled && !!token,
  })
}

export function useCancelBookingByToken(token: string) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: () => publicApi.cancelBookingByToken(token),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["booking-manage", token] })
      toast.success("Rezervarea a fost anulată")
    },
    onError: (error) => {
      toast.error(errorMessage(error, "Anularea rezervării a eșuat"))
    },
  })
}

export function useLateCheckoutByToken(token: string) {
  return useQuery({
    queryKey: ["booking-late-checkout", token],
    queryFn: () => publicApi.getLateCheckoutByToken(token),
    enabled: !!token,
  })
}

export function useRequestLateCheckoutByToken(token: string) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (guestNote?: string) => publicApi.requestLateCheckoutByToken(token, guestNote),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["booking-late-checkout", token] })
      toast.success("Cererea de check-out târziu a fost trimisă")
    },
    onError: (error) => {
      toast.error(errorMessage(error, "Trimiterea cererii a eșuat"))
    },
  })
}
