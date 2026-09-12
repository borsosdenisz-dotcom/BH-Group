"use client"

import { Suspense } from "react"
import Link from "next/link"
import { useSearchParams } from "next/navigation"
import { CreditCard, XCircle } from "lucide-react"
import { Button, buttonVariants } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { useBookingByToken, useStartCardCheckout } from "@/hooks/use-public-booking"
import { cn } from "@/lib/utils"

/**
 * Where Stripe sends the guest if they back out of the payment page. Nothing
 * was charged; the booking keeps its hold until it expires, so the useful
 * thing to offer here is a second run at paying.
 */
function PaymentCancelledInner() {
  const token = useSearchParams().get("token") ?? ""
  const { data: reservation, isLoading } = useBookingByToken(token)
  const startCheckout = useStartCardCheckout()

  const stillPayable = reservation?.status === "PENDING"

  return (
    <div className="mx-auto flex max-w-lg flex-col items-center gap-4 text-center">
      <XCircle className="size-12 text-muted-foreground" />
      <h1 className="text-2xl font-semibold tracking-tight">Plata a fost întreruptă</h1>

      {isLoading ? (
        <Skeleton className="h-5 w-72" />
      ) : stillPayable ? (
        <p className="text-sm text-muted-foreground">
          Nu ți-am debitat nimic. Ținem perioada rezervată încă puțin — poți relua plata acum, sau
          ne poți scrie din pagina rezervării dacă preferi transferul bancar.
        </p>
      ) : (
        <p className="text-sm text-muted-foreground">
          Nu ți-am debitat nimic. Rezervarea nu mai este activă, dar poți relua oricând o
          rezervare nouă pentru aceleași date.
        </p>
      )}

      {stillPayable && token && (
        <Button className="w-full" size="lg" disabled={startCheckout.isPending} onClick={() => startCheckout.mutate(token)}>
          <CreditCard className="size-4" />
          {startCheckout.isPending ? "Se deschide plata..." : "Reia plata cu cardul"}
        </Button>
      )}

      {token && (
        <Link
          href={`/manage-booking/${token}`}
          className={cn(buttonVariants({ variant: stillPayable ? "outline" : "default" }), "w-full")}
        >
          Vezi rezervarea
        </Link>
      )}
      <Link href="/book" className="text-sm text-muted-foreground hover:text-foreground">
        Înapoi la căutare
      </Link>
    </div>
  )
}

export default function PaymentCancelledPage() {
  return (
    <Suspense fallback={null}>
      <PaymentCancelledInner />
    </Suspense>
  )
}
