"use client"

import { ExternalLink, MailQuestion } from "lucide-react"
import { LeadDialog } from "@/components/marketing/lead-dialog"
import { buttonVariants } from "@/components/ui/button"
import { cn } from "@/lib/utils"
import type { PublicPropertyResponse } from "@/lib/api/types"

export function OtaBookingActions({
  property,
  className,
}: {
  property: PublicPropertyResponse
  className?: string
}) {
  if (property.demo) {
    return (
      <p className={cn("rounded-lg bg-muted/60 px-3 py-2.5 text-center text-sm text-muted-foreground", className)}>
        Anunț demonstrativ: prețul este orientativ, iar proprietatea nu se poate rezerva.
      </p>
    )
  }

  const hasExternalListing = Boolean(property.airbnbListingUrl || property.bookingListingUrl)

  return (
    <div className={cn("flex flex-col gap-3", className)}>
      <div className="flex flex-col gap-2">
        {property.airbnbListingUrl && (
          <a
            href={property.airbnbListingUrl}
            target="_blank"
            rel="noopener noreferrer"
            className={cn(buttonVariants({ size: "lg" }), "w-full justify-between gap-3")}
          >
            <span>Verifică disponibilitatea pe Airbnb</span>
            <ExternalLink className="size-4 shrink-0" aria-hidden="true" />
          </a>
        )}
        {property.bookingListingUrl && (
          <a
            href={property.bookingListingUrl}
            target="_blank"
            rel="noopener noreferrer"
            className={cn(buttonVariants({ size: "lg", variant: "outline" }), "w-full justify-between gap-3")}
          >
            <span>Verifică disponibilitatea pe Booking.com</span>
            <ExternalLink className="size-4 shrink-0" aria-hidden="true" />
          </a>
        )}
        {!hasExternalListing && (
          <LeadDialog
            title="Solicită disponibilitatea"
            description={`Trimite-ne datele tale și perioada dorită pentru ${property.name}.`}
            defaultMessage={`Doresc să verific disponibilitatea pentru proprietatea „${property.name}”.`}
            submitLabel="Trimite solicitarea"
            trigger={
              <button type="button" className={cn(buttonVariants({ size: "lg" }), "w-full justify-between gap-3")}>
                <span>Solicită disponibilitatea</span>
                <MailQuestion className="size-4 shrink-0" aria-hidden="true" />
              </button>
            }
          />
        )}
      </div>
      {hasExternalListing && (
        <p className="text-center text-xs leading-relaxed text-muted-foreground">
          Linkurile deschid platforme externe, unde verifici disponibilitatea, prețul și condițiile finale.
        </p>
      )}
    </div>
  )
}
