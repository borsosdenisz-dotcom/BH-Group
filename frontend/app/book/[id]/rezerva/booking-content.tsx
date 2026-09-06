"use client"

import { Suspense, useState } from "react"
import Link from "next/link"
import { useSearchParams } from "next/navigation"
import { ArrowLeft, CheckCircle2 } from "lucide-react"
import { buttonVariants } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { BookingForm } from "@/components/booking/booking-form"
import { usePublicProperty } from "@/hooks/use-public-booking"
import { cn } from "@/lib/utils"
import type { PublicReservationResponse } from "@/lib/api/types"

function BookingInner({ id }: { id: string }) {
  const searchParams = useSearchParams()
  const { data: property, isLoading } = usePublicProperty(id)
  const [confirmedReservation, setConfirmedReservation] = useState<PublicReservationResponse | null>(null)

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
        <h1 className="text-2xl font-semibold tracking-tight">Cererea a fost primită și așteaptă confirmarea</h1>
        <p className="text-sm text-muted-foreground">
          Am trimis un email la <strong>{confirmedReservation.guestEmail}</strong> cu detaliile cererii
          și un link pentru a o gestiona. Echipa noastră o confirmă manual, iar tu primești un nou
          email imediat ce e aprobată.
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
        <Link
          href={`/manage-booking/${confirmedReservation.managementToken}`}
          className={cn(buttonVariants(), "w-full")}
        >
          Gestionează rezervarea
        </Link>
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
          Trimiți o cerere de rezervare — nu e o confirmare instant. Echipa noastră o confirmă
          manual și primești un email imediat ce e aprobată.
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
