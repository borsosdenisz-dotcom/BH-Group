"use client"

import { Suspense, useRef } from "react"
import { usePathname, useRouter, useSearchParams } from "next/navigation"
import { MapPin } from "lucide-react"
import { motion, useReducedMotion } from "motion/react"
import { Card, CardContent } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { AvailabilityCalendar } from "@/components/booking/availability-calendar"
import { MobileBookingBar } from "@/components/booking/mobile-booking-bar"
import { PropertyAmenities } from "@/components/booking/property-amenities"
import { PropertyBookingCard } from "@/components/booking/property-booking-card"
import { PropertyFacts } from "@/components/booking/property-facts"
import { PropertyGallery } from "@/components/booking/property-gallery"
import { PropertyLocation } from "@/components/booking/property-location"
import { usePublicProperty, usePublicQuote } from "@/hooks/use-public-booking"
import { PROPERTY_TYPE_LABELS } from "@/lib/property-labels"

function PropertyDetailInner({ id }: { id: string }) {
  const router = useRouter()
  const pathname = usePathname()
  const searchParams = useSearchParams()
  const { data: property, isLoading } = usePublicProperty(id)
  const availabilityRef = useRef<HTMLDivElement>(null)
  const reduceMotion = useReducedMotion()

  const checkIn = searchParams.get("checkIn")
  const checkOut = searchParams.get("checkOut")
  const rawGuests = Number(searchParams.get("guests"))
  const guests = property
    ? Math.min(Math.max(rawGuests || 1, 1), property.maxGuests)
    : rawGuests || 1

  // Single source of truth for checkIn/checkOut/guests: the URL search
  // params. The calendar and the booking card both read the same values
  // from here and both write back through this same function, so there is
  // never a second, out-of-sync copy of the selection.
  function updateSelection(next: { checkIn?: string | null; checkOut?: string | null; guests?: number }) {
    const params = new URLSearchParams(searchParams.toString())
    const merged = { checkIn, checkOut, guests, ...next }

    if (merged.checkIn) params.set("checkIn", merged.checkIn)
    else params.delete("checkIn")

    if (merged.checkOut) params.set("checkOut", merged.checkOut)
    else params.delete("checkOut")

    if (merged.guests) params.set("guests", String(merged.guests))
    else params.delete("guests")

    router.replace(`${pathname}?${params.toString()}`, { scroll: false })
  }

  const quoteQuery = usePublicQuote(property?.id ?? "", checkIn ?? "", checkOut ?? "", guests)

  function scrollToAvailability() {
    availabilityRef.current?.scrollIntoView({
      behavior: reduceMotion ? "auto" : "smooth",
      block: "center",
    })
  }

  if (isLoading || !property) {
    return (
      <div className="mx-auto flex max-w-7xl flex-col gap-8">
        <Skeleton className="h-[280px] w-full rounded-2xl sm:h-[360px] lg:h-[440px]" />
        <div className="flex flex-col gap-3">
          <Skeleton className="h-8 w-72" />
          <Skeleton className="h-5 w-48" />
        </div>
        <div className="grid gap-10 lg:grid-cols-[minmax(0,1fr)_380px]">
          <Skeleton className="h-96 w-full" />
          <Skeleton className="h-96 w-full rounded-2xl" />
        </div>
      </div>
    )
  }

  const bookingHref = `/book/${id}/rezerva${searchParams.toString() ? `?${searchParams.toString()}` : ""}`

  return (
    <motion.div
      initial={reduceMotion ? undefined : { opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4, ease: "easeOut" }}
      className="mx-auto flex max-w-7xl flex-col gap-8 pb-8 lg:pb-0"
    >
      <PropertyGallery photos={property.photos} propertyName={property.name} />

      <div className="flex flex-col gap-3">
        <h1 className="font-heading text-2xl font-semibold tracking-tight sm:text-3xl">{property.name}</h1>
        <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-muted-foreground">
          <span className="flex items-center gap-1">
            <MapPin className="size-3.5" />
            {property.city}
            {property.county ? `, ${property.county}` : ""}
          </span>
          <span className="text-border">·</span>
          <span>{PROPERTY_TYPE_LABELS[property.propertyType]}</span>
          {property.sizeSqm != null && (
            <>
              <span className="text-border">·</span>
              <span>{property.sizeSqm} m²</span>
            </>
          )}
        </p>
        <PropertyFacts property={property} />
      </div>

      <div className="grid gap-10 lg:grid-cols-[minmax(0,1fr)_380px] lg:items-start">
        <aside className="lg:order-2 lg:sticky lg:top-24">
          <PropertyBookingCard
            property={property}
            checkIn={checkIn}
            checkOut={checkOut}
            guests={guests}
            quote={quoteQuery.data}
            isQuoteLoading={quoteQuery.isLoading}
            bookingHref={bookingHref}
            onGuestsChange={(next) => updateSelection({ guests: next })}
            onScrollToCalendar={scrollToAvailability}
          />
        </aside>

        <div className="flex flex-col gap-8 lg:order-1">
          {property.description && (
            <Card>
              <CardContent className="flex flex-col gap-3">
                <h2 className="font-heading text-lg font-semibold tracking-tight">Despre acest loc</h2>
                <p className="max-w-prose whitespace-pre-line text-[15px] leading-relaxed text-muted-foreground">
                  {property.description}
                </p>
              </CardContent>
            </Card>
          )}

          <Card>
            <CardContent>
              <PropertyAmenities facilities={property.facilities} />
            </CardContent>
          </Card>

          <Card>
            <CardContent>
              <PropertyLocation property={property} />
            </CardContent>
          </Card>

          <div ref={availabilityRef} className="scroll-mt-24">
            <Card>
              <CardContent className="flex flex-col gap-4">
                <h2 className="font-heading text-lg font-semibold tracking-tight">Disponibilitate</h2>
                <AvailabilityCalendar
                  propertyId={property.id}
                  minStayNights={property.minStayNights}
                  maxStayNights={property.maxStayNights}
                  checkIn={checkIn}
                  checkOut={checkOut}
                  onSelect={(range) => updateSelection(range)}
                />
              </CardContent>
            </Card>
          </div>
        </div>
      </div>

      <MobileBookingBar
        property={property}
        checkIn={checkIn}
        checkOut={checkOut}
        quote={quoteQuery.data}
        isQuoteLoading={quoteQuery.isLoading}
        bookingHref={bookingHref}
      />
      {/* Compensates for the fixed mobile bar so the last section isn't hidden behind it. */}
      <div className="h-24 lg:hidden" aria-hidden />
    </motion.div>
  )
}

export function PropertyDetailContent({ id }: { id: string }) {
  return (
    <Suspense fallback={null}>
      <PropertyDetailInner id={id} />
    </Suspense>
  )
}
