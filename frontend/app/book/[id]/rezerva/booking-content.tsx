"use client"

import { Suspense, useState } from "react"
import Link from "next/link"
import { useSearchParams } from "next/navigation"
import { ArrowLeft, CheckCircle2, CreditCard } from "lucide-react"
import { Button, buttonVariants } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { BookingForm } from "@/components/booking/booking-form"
import {
  usePaymentConfig,
  usePublicProperty,
  useStartCardCheckout,
} from "@/hooks/use-public-booking"
import { cn } from "@/lib/utils"
import type { PublicReservationResponse } from "@/lib/api/types"

function BookingInner({ id }: { id: string }) {
  const searchParams = useSearchParams()
  const { data: property, isLoading } = usePublicProperty(id)
  const { data: paymentConfig } = usePaymentConfig()
  const startCheckout = useStartCardCheckout()
  const [confirmedReservation, setConfirmedReservation] = useState<PublicReservationResponse | null>(null)
  const cardPaymentsEnabled = paymentConfig?.cardPaymentsEnabled ?? false

  if (isLoading || !property) {
    return (
      <div className="mx-auto flex max-w-2xl flex-col gap-6">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-96 w-full" />
      </div>
    )
  }

  if (confirmedReservation) {
    return (
      <div className="mx-auto flex max-w-lg flex-col items-center gap-4 text-center">
        <CheckCircle2 className="size-12 text-emerald-500" />
        <h1 className="text-2xl font-semibold tracking-tight">
          {cardPaymentsEnabled ? "Rezervarea ta e reținută — mai rămâne plata" : "Cererea a fost primită și așteaptă confirmarea"}
        </h1>
        <p className="text-sm text-muted-foreground">
          {cardPaymentsEnabled ? (
            <>
              Am trimis un email la <strong>{confirmedReservation.guestEmail}</strong> cu detaliile cererii.
              Ținem datele rezervate 15 minute: plătește cu cardul acum pentru confirmare imediată, sau
              alege plata prin transfer și echipa noastră te contactează.
            </>
          ) : (
            <>
              Am trimis un email la <strong>{confirmedReservation.guestEmail}</strong> cu detaliile cererii
              și un link pentru a o gestiona. Echipa noastră o confirmă manual, iar tu primești un nou
              email imediat ce e aprobată.
            </>
          )}
        </p>
        <Card className="w-full text-left">
          <CardContent className="flex flex-col gap-2 text-sm">
            <div className="flex justify-between">
              <span className="text-muted-foreground">Proprietate</span>
              <span className="font-medium">{confirmedReservation.propertyName}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-muted-foreground">Perioadă</span>
              <span className="font-medium">
                {confirmedReservation.checkInDate} → {confirmedReservation.checkOutDate}
              </span>
            </div>
            {confirmedReservation.totalAmount != null && (
              <div className="flex justify-between">
                <span className="text-muted-foreground">Sumă totală</span>
                <span className="font-medium">
                  {confirmedReservation.totalAmount} {confirmedReservation.currency}
                </span>
              </div>
            )}
          </CardContent>
        </Card>
        {cardPaymentsEnabled && (
          <Button
            className="w-full"
            size="lg"
            disabled={startCheckout.isPending}
            onClick={() => startCheckout.mutate(confirmedReservation.managementToken)}
          >
            <CreditCard className="size-4" />
            {startCheckout.isPending ? "Se deschide plata..." : "Plătește cu cardul"}
          </Button>
        )}
        <Link
          href={`/manage-booking/${confirmedReservation.managementToken}`}
          className={cn(buttonVariants({ variant: cardPaymentsEnabled ? "outline" : "default" }), "w-full")}
        >
          {cardPaymentsEnabled ? "Plătesc prin transfer bancar" : "Gestionează rezervarea"}
        </Link>
        {cardPaymentsEnabled && (
          <p className="text-xs text-muted-foreground">
            Plata e procesată securizat de Stripe. Datele cardului nu ajung pe serverele noastre.
          </p>
        )}
        <Link href="/book" className="text-sm text-muted-foreground hover:text-foreground">
          Înapoi la căutare
        </Link>
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
        <p className="mt-1 text-sm text-muted-foreground">
          {cardPaymentsEnabled
            ? "După ce trimiți datele, ținem perioada rezervată 15 minute și poți plăti cu cardul pentru confirmare imediată — sau alegi plata prin transfer bancar."
            : "Trimiți o cerere de rezervare — nu e o confirmare instant. Echipa noastră o confirmă manual și primești un email imediat ce e aprobată."}
        </p>
      </div>
      <BookingForm
        property={property}
        defaultCheckIn={searchParams.get("checkIn") ?? undefined}
        defaultCheckOut={searchParams.get("checkOut") ?? undefined}
        defaultGuests={searchParams.get("guests") ? Number(searchParams.get("guests")) : undefined}
        onSuccess={setConfirmedReservation}
      />
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
