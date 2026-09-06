"use client"

import Link from "next/link"
import { Button, buttonVariants } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { cn } from "@/lib/utils"
import type { PriceQuoteResponse, PublicPropertyResponse } from "@/lib/api/types"

function formatPrice(value: number) {
  return new Intl.NumberFormat("ro-RO", { maximumFractionDigits: 0 }).format(value)
}

interface MobileBookingBarProps {
  property: PublicPropertyResponse
  checkIn: string | null
  checkOut: string | null
  quote: PriceQuoteResponse | undefined
  isQuoteLoading: boolean
  bookingHref: string
}

export function MobileBookingBar({
  property,
  checkIn,
  checkOut,
  quote,
  isQuoteLoading,
  bookingHref,
}: MobileBookingBarProps) {
  const hasDates = !!checkIn && !!checkOut

  const ctaState = !hasDates
    ? "no-dates"
    : isQuoteLoading
      ? "loading"
      : quote && !quote.available
        ? "unavailable"
        : quote?.available
          ? "available"
          : "loading"

  return (
    <div
      className="fixed inset-x-0 bottom-0 z-40 border-t border-border/60 bg-background/95 px-4 py-3 backdrop-blur-sm lg:hidden"
      style={{ paddingBottom: "max(0.75rem, env(safe-area-inset-bottom))" }}
    >
      <div className="flex items-center justify-between gap-4">
        <div className="flex flex-col">
          {hasDates && isQuoteLoading ? (
            <Skeleton className="h-6 w-24" />
          ) : hasDates && quote?.available && quote.totalAmount != null ? (
            <>
              <span className="text-base font-semibold">
                {formatPrice(quote.totalAmount)} {quote.currency}
              </span>
              <span className="text-xs text-muted-foreground">
                {quote.nights} {quote.nights === 1 ? "noapte" : "nopți"}
              </span>
            </>
          ) : property.basePricePerNight != null ? (
            <>
              <span className="text-base font-semibold">
                de la {formatPrice(property.basePricePerNight)} {property.currency}
              </span>
              <span className="text-xs text-muted-foreground">/ noapte</span>
            </>
          ) : (
            <span className="text-sm text-muted-foreground">Selectează perioada</span>
          )}
        </div>

        {ctaState === "available" ? (
          <Link href={bookingHref} className={cn(buttonVariants(), "shrink-0")}>
            Rezervă
          </Link>
        ) : (
          <Button disabled className="shrink-0">
            {ctaState === "unavailable"
              ? "Indisponibilă"
              : ctaState === "loading"
                ? "..."
                : "Selectează perioada"}
          </Button>
        )}
      </div>
    </div>
  )
}
