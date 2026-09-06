import Image from "next/image"
import Link from "next/link"
import { Bath, BedDouble, Building2, MapPin, Users } from "lucide-react"
import type { PublicPropertySummaryResponse } from "@/lib/api/types"
import { FACILITY_LABELS, PROPERTY_TYPE_LABELS } from "@/lib/property-labels"

function formatPrice(value: number) {
  return new Intl.NumberFormat("ro-RO", { maximumFractionDigits: 0 }).format(value)
}

export function PublicPropertyCard({
  property,
  searchParams,
}: {
  property: PublicPropertySummaryResponse
  searchParams?: string
}) {
  return (
    <Link
      href={`/book/${property.id}${searchParams ? `?${searchParams}` : ""}`}
      className="tilt-card group block overflow-hidden rounded-2xl border border-border/60 bg-card shadow-sm"
    >
      <div className="relative aspect-[4/3] w-full overflow-hidden bg-muted">
        {property.coverPhotoUrl ? (
          <Image
            src={property.coverPhotoUrl}
            alt={property.name}
            fill
            sizes="(min-width: 1280px) 25vw, (min-width: 1024px) 33vw, (min-width: 640px) 50vw, 100vw"
            className="object-cover transition-transform duration-700 ease-[cubic-bezier(0.16,1,0.3,1)] group-hover:scale-110"
          />
        ) : (
          <div className="flex size-full items-center justify-center text-muted-foreground">
            <Building2 className="size-8" />
          </div>
        )}
        <div className="absolute inset-0 bg-gradient-to-t from-black/50 via-transparent to-transparent opacity-0 transition-opacity duration-500 group-hover:opacity-100" />
        {property.basePricePerNight != null && (
          <div className="absolute left-3 top-3 rounded-full bg-white/95 px-3 py-1.5 shadow-sm backdrop-blur-sm">
            <p className="text-[10px] font-medium uppercase tracking-wide text-neutral-500">Preț de la</p>
            <p className="text-sm font-semibold text-neutral-900">
              {formatPrice(property.basePricePerNight)} {property.currency}
            </p>
          </div>
        )}
      </div>
      <div className="flex flex-col gap-2 p-5">
        <h3 className="font-medium leading-tight">{property.name}</h3>
        <p className="flex items-center gap-1 text-sm text-muted-foreground">
          <MapPin className="size-3.5 text-primary" />
          {property.city}
          {property.county ? `, ${property.county}` : ""}
        </p>
        <div className="mt-1 flex flex-wrap items-center gap-3 text-xs text-muted-foreground">
          <span className="flex items-center gap-1">
            <BedDouble className="size-3.5" />
            {property.bedrooms} {property.bedrooms === 1 ? "dormitor" : "dormitoare"}
          </span>
          <span className="flex items-center gap-1">
            <Bath className="size-3.5" />
            {property.bathrooms} {property.bathrooms === 1 ? "baie" : "băi"}
          </span>
          <span className="flex items-center gap-1">
            <Users className="size-3.5" />
            {property.maxGuests} oaspeți
          </span>
        </div>
        <span className="mt-1 w-fit rounded-full bg-primary/10 px-2.5 py-1 text-[11px] font-medium text-primary">
          {PROPERTY_TYPE_LABELS[property.propertyType]}
        </span>
        {property.facilities.length > 0 && (
          <div className="flex flex-wrap gap-1.5">
            {property.facilities.slice(0, 3).map((facility) => (
              <span
                key={facility}
                className="rounded-full bg-muted px-2 py-0.5 text-[10px] text-muted-foreground"
              >
                {FACILITY_LABELS[facility]}
              </span>
            ))}
            {property.facilities.length > 3 && (
              <span className="rounded-full bg-muted px-2 py-0.5 text-[10px] text-muted-foreground">
                +{property.facilities.length - 3}
              </span>
            )}
          </div>
        )}
      </div>
    </Link>
  )
}
