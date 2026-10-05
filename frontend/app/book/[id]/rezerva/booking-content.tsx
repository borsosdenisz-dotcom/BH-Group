"use client"

import { Suspense, useState } from "react"
import Link from "next/link"
import { useSearchParams } from "next/navigation"
import { ArrowLeft, Loader2 } from "lucide-react"
import { Skeleton } from "@/components/ui/skeleton"
import { BookingForm } from "@/components/booking/booking-form"
import { OnlineBookingUnavailable } from "@/components/booking/online-booking-unavailable"
import { usePaymentConfig, usePublicProperty } from "@/hooks/use-public-booking"
import { redirectToExternal } from "@/lib/redirect"
import type { PublicBookingCheckoutResponse } from "@/lib/api/types"

/**
 * Public booking is card-only. Submitting the form holds the dates and opens
 * Stripe Checkout in a single backend call, and the guest goes straight
 * there. Without online card payment there is no public booking at all:
 * the form is not shown, nothing is held, and the guest gets the contact
 * details instead.
 */
function BookingInner({ id }: { id: string }) {
  const searchParams = useSearchParams()
  const { data: property, isLoading } = usePublicProperty(id)
  const { data: paymentConfig, isLoading: paymentConfigLoading } = usePaymentConfig()
  const [redirecting, setRedirecting] = useState(false)
  const onlineBookingAvailable = paymentConfig?.cardPaymentsEnabled === true

  function handleBookingHeld(booking: PublicBookingCheckoutResponse) {
    setRedirecting(true)
    redirectToExternal(booking.checkoutUrl)
  }

  if (isLoading || paymentConfigLoading || !property) {
    return (
      <div className="mx-auto flex max-w-2xl flex-col gap-6">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-96 w-full" />
      </div>
    )
  }

  if (redirecting) {
    return (
      <div
        className="mx-auto flex max-w-lg flex-col items-center gap-4 text-center"
        role="status"
        aria-live="polite"
      >
        <Loader2 className="size-12 animate-spin text-muted-foreground" />
        <h1 className="text-2xl font-semibold tracking-tight">Te redirecționăm către plata securizată</h1>
        <p className="text-sm text-muted-foreground">
          Plata e procesată de Stripe. Datele cardului nu ajung pe serverele noastre.
        </p>
      </div>
    )
  }

  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-6">
      <div>
        <Link
          href={`/book/${id}`}
          className="mb-4 inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"
        >
          <ArrowLeft className="size-3.5" />
          Înapoi la {property.name}
        </Link>
        <h1 className="text-xl font-semibold tracking-tight">Rezervă — {property.name}</h1>
        {onlineBookingAvailable && (
          <p className="mt-1 text-sm text-muted-foreground">
            Plata se face online, cu cardul. După ce completezi datele, reținem perioada pentru tine și te
            trimitem la plata securizată Stripe. Rezervarea se confirmă automat imediat ce plata este confirmată.
          </p>
        )}
      </div>
      {onlineBookingAvailable ? (
        <BookingForm
          property={property}
          defaultCheckIn={searchParams.get("checkIn") ?? undefined}
          defaultCheckOut={searchParams.get("checkOut") ?? undefined}
          defaultGuests={searchParams.get("guests") ? Number(searchParams.get("guests")) : undefined}
          submitLabel="Continuă către plată"
          busy={redirecting}
          onSuccess={handleBookingHeld}
        />
      ) : (
        <OnlineBookingUnavailable />
      )}
    </div>
  )
}

export function BookingContent({ id }: { id: string }) {
  return (
    <Suspense fallback={null}>
      <BookingInner id={id} />
    </Suspense>
  )
}
