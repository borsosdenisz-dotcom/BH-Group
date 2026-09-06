import Image from "next/image"
import Link from "next/link"
import { ArrowUpRight, Bath, BedDouble, Building2, MapPin, Users } from "lucide-react"
import type { PublicPropertySummaryResponse } from "@/lib/api/types"
import { PROPERTY_TYPE_LABELS } from "@/lib/property-labels"

function formatPrice(value: number) {
  return new Intl.NumberFormat("ro-RO", { maximumFractionDigits: 0 }).format(value)
}

export function PublicPropertyCard({ property, searchParams }: { property: PublicPropertySummaryResponse; searchParams?: string }) {
  return (
    <Link
      href={`/book/${property.id}${searchParams ? `?${searchParams}` : ""}`}
      className="premium-card group block overflow-hidden border border-border/80 bg-card focus-visible:ring-2 focus-visible:ring-ring/30"
      aria-label={`Vezi ${property.name} din ${property.city}`}
    >
      <div className="relative aspect-[4/3] w-full overflow-hidden bg-muted">
        {property.coverPhotoUrl ? (
          <Image src={property.coverPhotoUrl} alt={property.name} fill sizes="(min-width: 1280px) 25vw, (min-width: 640px) 50vw, 100vw" className="object-cover transition-transform duration-500 group-hover:scale-[1.025]" />
        ) : (
          <div className="flex size-full items-center justify-center text-muted-foreground"><Building2 className="size-9" aria-hidden="true" /></div>
        )}
        <div className="absolute inset-x-0 bottom-0 h-24 bg-gradient-to-t from-navy/65 to-transparent" />
        <span className="absolute bottom-3 left-3 border border-white/20 bg-navy/82 px-2.5 py-1 text-xs font-semibold text-white backdrop-blur-sm">
          {PROPERTY_TYPE_LABELS[property.propertyType]}
        </span>
      </div>
      <div className="p-5">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0"><h2 className="truncate font-heading text-xl font-semibold leading-tight">{property.name}</h2><p className="mt-1.5 flex items-center gap-1.5 text-sm text-muted-foreground"><MapPin className="size-3.5 shrink-0 text-primary" />{property.city}{property.county ? `, ${property.county}` : ""}</p></div>
          <ArrowUpRight className="mt-1 size-4 shrink-0 text-muted-foreground transition-transform group-hover:-translate-y-0.5 group-hover:translate-x-0.5 group-hover:text-primary" />
        </div>
        <div className="mt-5 grid grid-cols-3 gap-2 border-y border-border/70 py-3 text-xs text-muted-foreground">
          <span className="flex items-center gap-1.5"><BedDouble className="size-3.5" />{property.bedrooms} dorm.</span>
          <span className="flex items-center gap-1.5"><Bath className="size-3.5" />{property.bathrooms} băi</span>
          <span className="flex items-center gap-1.5"><Users className="size-3.5" />{property.maxGuests} oaspeți</span>
        </div>
        <div className="mt-4 flex items-end justify-between gap-3">
          <span className="text-xs text-muted-foreground">Preț pe noapte</span>
          <span className="tabular-nums text-right font-semibold">{property.basePricePerNight != null ? <>{formatPrice(property.basePricePerNight)} {property.currency}</> : "La cerere"}</span>
        </div>
      </div>
    </Link>
  )
}
