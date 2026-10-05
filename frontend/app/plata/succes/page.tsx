"use client"

import { Suspense } from "react"
import Link from "next/link"
import { useSearchParams } from "next/navigation"
import { AlertCircle, CheckCircle2, Clock, CreditCard, Loader2, XCircle } from "lucide-react"
import { Button, buttonVariants } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { useBookingPaymentStatus, useStartCardCheckout } from "@/hooks/use-public-booking"
import { bookingPaymentOutcome } from "@/lib/booking-payment-state"
import { cn } from "@/lib/utils"

/**
 * Where Stripe sends the guest after checkout. Arriving here proves nothing
 * - the booking is confirmed only by Stripe's signed webhook - so this page
 * never claims success on its own: it keeps reading the booking back from
 * the backend and reports exactly what the backend says.
 */
function PaymentSuccessInner() {
  const token = useSearchParams().get("token") ?? ""
  const { data: reservation, isLoading, isError } = useBookingPaymentStatus(token)
  const startCheckout = useStartCardCheckout()
  const checkoutBusy = startCheckout.isPending || startCheckout.isSuccess

  if (!token || isError) {
    return (
      <div className="mx-auto flex max-w-lg flex-col items-center gap-4 text-center">
        <AlertCircle className="size-12 text-muted-foreground" />
        <h1 className="text-2xl font-semibold tracking-tight">Nu am găsit rezervarea</h1>
        <p className="text-sm text-muted-foreground">
          Verifică emailul primit la rezervare pentru linkul de gestionare.
        </p>
        <Link href="/book" className="text-sm text-muted-foreground hover:text-foreground">
          Înapoi la căutare
        </Link>
      </div>
    )
  }

  if (isLoading || !reservation) {
    return (
      <div className="mx-auto flex max-w-lg flex-col items-center gap-4">
        <Skeleton className="size-12 rounded-full" />
        <Skeleton className="h-7 w-64" />
        <Skeleton className="h-5 w-72" />
      </div>
    )
  }

  const outcome = bookingPaymentOutcome(reservation)

  const header = {
    verifying: {
      icon: <Loader2 className="size-12 animate-spin text-muted-foreground" />,
      title: "Verificăm plata",
      body: (
        <p className="flex items-start gap-2 text-left text-sm text-muted-foreground">
          <Clock className="mt-0.5 size-4 shrink-0" />
          <span>
            Așteptăm confirmarea plății de la procesatorul de plăți — de obicei durează câteva secunde.
            Pagina se actualizează singură; poți verifica și emailul.
          </span>
        </p>
      ),
    },
    confirmed: {
      icon: <CheckCircle2 className="size-12 text-emerald-500" />,
      title: "Rezervare confirmată",
      body: (
        <p className="text-sm text-muted-foreground">
          Plata a fost confirmată și rezervarea ta este <strong>confirmată</strong>. Ți-am trimis un email
          cu detaliile și linkul de gestionare.
        </p>
      ),
    },
    failed: {
      icon: <XCircle className="size-12 text-destructive" />,
      title: "Plata nu a reușit",
      body: (
        <p className="text-sm text-muted-foreground">
          Banca a refuzat plata, iar rezervarea nu este confirmată. Ținem perioada reținută încă puțin —
          poți încerca din nou cu un alt card.
        </p>
      ),
    },
    expired: {
      icon: <XCircle className="size-12 text-muted-foreground" />,
      title: "Plata a expirat",
      body: (
        <p className="text-sm text-muted-foreground">
          Nu am primit o plată confirmată la timp, așa că rezervarea nu mai este activă și perioada a fost
          eliberată. Poți reface oricând o rezervare nouă.
        </p>
      ),
    },
    refunded: {
      icon: <AlertCircle className="size-12 text-amber-500" />,
      title: "Plata a fost rambursată",
      body: (
        <p className="text-sm text-muted-foreground">
          Plata a ajuns după ce perioada fusese eliberată, așa că rezervarea nu a putut fi confirmată și
          suma ți-a fost rambursată integral.
        </p>
      ),
    },
  }[outcome]

  return (
    <div className="mx-auto flex max-w-lg flex-col items-center gap-4 text-center">
      <div role="status" aria-live="polite" className="flex flex-col items-center gap-4">
        {header.icon}
        <h1 className="text-2xl font-semibold tracking-tight">{header.title}</h1>
        {header.body}
      </div>

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

      {outcome === "failed" && (
        <Button className="w-full" size="lg" disabled={checkoutBusy} onClick={() => startCheckout.mutate(token)}>
          <CreditCard className="size-4" />
          {checkoutBusy ? "Se deschide plata..." : "Încearcă din nou plata"}
        </Button>
      )}

      {outcome === "expired" || outcome === "refunded" ? (
        <Link href="/book" className={cn(buttonVariants(), "w-full")}>
          Fă o rezervare nouă
        </Link>
      ) : (
        <Link
          href={`/manage-booking/${token}`}
          className={cn(buttonVariants({ variant: outcome === "failed" ? "outline" : "default" }), "w-full")}
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

export default function PaymentSuccessPage() {
  return (
    <Suspense fallback={null}>
      <PaymentSuccessInner />
    </Suspense>
  )
}
