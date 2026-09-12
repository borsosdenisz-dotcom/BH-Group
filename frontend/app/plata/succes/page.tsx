"use client"

import { Suspense } from "react"
import Link from "next/link"
import { useSearchParams } from "next/navigation"
import { CheckCircle2, Clock } from "lucide-react"
import { buttonVariants } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { useBookingByToken } from "@/hooks/use-public-booking"
import { cn } from "@/lib/utils"

/**
 * Where Stripe sends the guest after a successful payment. The booking is
 * confirmed by the webhook, not by landing here - so this reads the real
 * status back and says plainly when confirmation is still a moment away.
 */
function PaymentSuccessInner() {
  const token = useSearchParams().get("token") ?? ""
  const { data: reservation, isLoading } = useBookingByToken(token)

  const confirmed = reservation?.status === "CONFIRMED"

  return (
    <div className="mx-auto flex max-w-lg flex-col items-center gap-4 text-center">
      <CheckCircle2 className="size-12 text-emerald-500" />
      <h1 className="text-2xl font-semibold tracking-tight">Plata a fost primită</h1>

      {isLoading ? (
        <Skeleton className="h-5 w-72" />
      ) : confirmed ? (
        <p className="text-sm text-muted-foreground">
          Rezervarea ta este <strong>confirmată</strong>. Ți-am trimis un email cu detaliile și
          linkul de gestionare.
        </p>
      ) : (
        <p className="flex items-start gap-2 text-left text-sm text-muted-foreground">
          <Clock className="mt-0.5 size-4 shrink-0" />
          <span>
            Confirmăm rezervarea imediat ce procesatorul ne trimite confirmarea plății — de
            obicei în câteva secunde. Reîncarcă pagina peste puțin sau verifică emailul.
          </span>
        </p>
      )}

      {reservation && (
        <Card className="w-full text-left">
          <CardContent className="flex flex-col gap-2 text-sm">
            <div className="flex justify-between">
              <span className="text-muted-foreground">Proprietate</span>
              <span className="font-medium">{reservation.propertyName}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-muted-foreground">Perioadă</span>
              <span className="font-medium">
                {reservation.checkInDate} → {reservation.checkOutDate}
              </span>
            </div>
            {reservation.totalAmount != null && (
              <div className="flex justify-between">
                <span className="text-muted-foreground">Sumă</span>
                <span className="font-medium">
                  {reservation.totalAmount} {reservation.currency}
                </span>
              </div>
            )}
          </CardContent>
        </Card>
      )}

      {token && (
        <Link href={`/manage-booking/${token}`} className={cn(buttonVariants(), "w-full")}>
          Vezi rezervarea
        </Link>
      )}
      <Link href="/book" className="text-sm text-muted-foreground hover:text-foreground">
        Înapoi la căutare
      </Link>
    </div>
  )
}

export default function PaymentSuccessPage() {
  return (
    <Suspense fallback={null}>
      <PaymentSuccessInner />
    </Suspense>
  )
}
