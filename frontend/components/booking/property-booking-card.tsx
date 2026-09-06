"use client"

import Link from "next/link"
import { AnimatePresence, motion, useReducedMotion } from "motion/react"
import { Calendar, Minus, Plus } from "lucide-react"
import { Button, buttonVariants } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { parseLocalDate } from "@/lib/date"
import { cn } from "@/lib/utils"
import type { PriceQuoteResponse, PublicPropertyResponse } from "@/lib/api/types"

function formatPrice(value: number) {
  return new Intl.NumberFormat("ro-RO", { maximumFractionDigits: 0 }).format(value)
}

function formatShortDate(iso: string) {
  return parseLocalDate(iso).toLocaleDateString("ro-RO", { day: "numeric", month: "short" })
}

interface PropertyBookingCardProps {
  property: PublicPropertyResponse
  checkIn: string | null
  checkOut: string | null
  guests: number
  quote: PriceQuoteResponse | undefined
  isQuoteLoading: boolean
  bookingHref: string
  onGuestsChange: (guests: number) => void
  onScrollToCalendar: () => void
  className?: string
}

export function PropertyBookingCard({
  property,
  checkIn,
  checkOut,
  guests,
  quote,
  isQuoteLoading,
  bookingHref,
  onGuestsChange,
  onScrollToCalendar,
  className,
}: PropertyBookingCardProps) {
  const reduceMotion = useReducedMotion()
  const hasDates = !!checkIn && !!checkOut

  const derivedPerNight =
    !property.basePricePerNight && quote?.totalAmount != null && quote.nights > 0
      ? quote.totalAmount / quote.nights
      : null

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
    <Card className={cn("gap-4 py-5", className)}>
      <CardContent className="flex flex-col gap-5 px-5">
        <div>
          {property.basePricePerNight != null ? (
            <p className="flex items-baseline gap-1">
              <span className="text-2xl font-semibold tracking-tight">
                {formatPrice(property.basePricePerNight)} {property.currency}
              </span>
              <span className="text-sm text-muted-foreground">/ noapte</span>
            </p>
          ) : derivedPerNight != null ? (
            <p className="flex items-baseline gap-1">
              <span className="text-2xl font-semibold tracking-tight">
                {formatPrice(derivedPerNight)} {quote?.currency}
              </span>
              <span className="text-sm text-muted-foreground">/ noapte (estimat)</span>
            </p>
          ) : (
            <p className="text-base font-medium text-muted-foreground">Selectează perioada pentru preț</p>
          )}
        </div>

        <div className="grid grid-cols-2 gap-2">
          <button
            type="button"
            onClick={onScrollToCalendar}
            className="flex flex-col gap-0.5 rounded-lg border border-border/60 px-3 py-2 text-left transition-colors hover:border-border hover:bg-accent/40"
          >
            <span className="text-[11px] font-medium uppercase tracking-wide text-muted-foreground">Sosire</span>
            <span className="text-sm font-medium">{checkIn ? formatShortDate(checkIn) : "Alege data"}</span>
          </button>
          <button
            type="button"
            onClick={onScrollToCalendar}
            className="flex flex-col gap-0.5 rounded-lg border border-border/60 px-3 py-2 text-left transition-colors hover:border-border hover:bg-accent/40"
          >
            <span className="text-[11px] font-medium uppercase tracking-wide text-muted-foreground">Plecare</span>
            <span className="text-sm font-medium">{checkOut ? formatShortDate(checkOut) : "Alege data"}</span>
          </button>
        </div>

        <div className="flex flex-col gap-1.5 rounded-lg border border-border/60 px-3 py-2.5">
          <div className="flex items-center justify-between">
            <span className="text-[11px] font-medium uppercase tracking-wide text-muted-foreground">Oaspeți</span>
            <div className="flex items-center gap-3">
              <Button
                type="button"
                size="icon"
                variant="outline"
                className="size-7"
                disabled={guests <= 1}
                onClick={() => onGuestsChange(guests - 1)}
                aria-label="Scade numărul de oaspeți"
              >
                <Minus className="size-3.5" />
              </Button>
              <span className="w-4 text-center text-sm font-medium" aria-live="polite">
                {guests}
              </span>
              <Button
                type="button"
                size="icon"
                variant="outline"
                className="size-7"
                disabled={guests >= property.maxGuests}
                onClick={() => onGuestsChange(guests + 1)}
                aria-label="Crește numărul de oaspeți"
              >
                <Plus className="size-3.5" />
              </Button>
            </div>
          </div>
          <p className="text-xs text-muted-foreground">Maxim {property.maxGuests} oaspeți</p>
        </div>

        <AnimatePresence mode="wait">
          {hasDates && (
            <motion.div
              key={ctaState + (quote?.totalAmount ?? "")}
              initial={reduceMotion ? undefined : { opacity: 0, y: 4 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.2 }}
              className="flex flex-col gap-2 border-t border-border/60 pt-4"
            >
              {isQuoteLoading ? (
                <div className="flex flex-col gap-2">
                  <Skeleton className="h-4 w-full" />
                  <Skeleton className="h-4 w-2/3" />
                  <Skeleton className="h-5 w-full" />
                </div>
              ) : quote?.available ? (
                <div className="flex flex-col gap-1.5 text-sm">
                  {quote.subtotal != null && (
                    <div className="flex justify-between text-muted-foreground">
                      <span>
                        Subtotal · {quote.nights} {quote.nights === 1 ? "noapte" : "nopți"}
                      </span>
                      <span>
                        {formatPrice(quote.subtotal)} {quote.currency}
                      </span>
                    </div>
                  )}
                  {quote.cleaningFee != null && quote.cleaningFee > 0 && (
                    <div className="flex justify-between text-muted-foreground">
                      <span>Taxă curățenie</span>
                      <span>
                        {formatPrice(quote.cleaningFee)} {quote.currency}
                      </span>
                    </div>
                  )}
                  {quote.extraGuestFee != null && quote.extraGuestFee > 0 && (
                    <div className="flex justify-between text-muted-foreground">
                      <span>Taxă oaspeți suplimentari</span>
                      <span>
                        {formatPrice(quote.extraGuestFee)} {quote.currency}
                      </span>
                    </div>
                  )}
                  {quote.discountAmount != null && quote.discountAmount > 0 && (
                    <div className="flex justify-between text-emerald-600 dark:text-emerald-400">
                      <span>Reducere{quote.discountPercent ? ` (${quote.discountPercent}%)` : ""}</span>
                      <span>
                        −{formatPrice(quote.discountAmount)} {quote.currency}
                      </span>
                    </div>
                  )}
                  {quote.totalAmount != null && (
                    <div className="mt-1 flex justify-between border-t border-border/60 pt-2 text-base font-semibold">
                      <span>Total</span>
                      <span>
                        {formatPrice(quote.totalAmount)} {quote.currency}
                      </span>
                    </div>
                  )}
                </div>
              ) : quote && !quote.available ? (
                <div className="flex items-center gap-2 rounded-lg bg-muted/60 px-3 py-2.5 text-sm text-muted-foreground">
                  <Calendar className="size-4 shrink-0" />
                  {quote.unavailableReason || "Perioada selectată nu este disponibilă."}
                </div>
              ) : null}
            </motion.div>
          )}
        </AnimatePresence>

        {ctaState === "available" ? (
          <Link href={bookingHref} className={cn(buttonVariants({ size: "lg" }), "w-full")}>
            Rezervă
          </Link>
        ) : (
          <Button size="lg" disabled className="w-full">
            {ctaState === "no-dates"
              ? "Selectează perioada"
              : ctaState === "unavailable"
                ? "Perioadă indisponibilă"
                : "Se calculează..."}
          </Button>
        )}

        <p className="text-center text-xs text-muted-foreground">
          Trimiți o cerere de rezervare — echipa o confirmă manual, nu e o rezervare instantă.
        </p>
      </CardContent>
    </Card>
  )
}
