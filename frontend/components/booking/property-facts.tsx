import { Bath, BedDouble, Clock, Users } from "lucide-react"
import type { PublicPropertyResponse } from "@/lib/api/types"

interface PropertyFactsProps {
  property: PublicPropertyResponse
}

/** Inline "N dormitoare · N băi · Max. N oaspeți · Check-in HH:MM · Check-out HH:MM" row. */
export function PropertyFacts({ property }: PropertyFactsProps) {
  const facts: { icon: typeof BedDouble; label: string }[] = [
    {
      icon: BedDouble,
      label: `${property.bedrooms} ${property.bedrooms === 1 ? "dormitor" : "dormitoare"}`,
    },
    { icon: Bath, label: `${property.bathrooms} ${property.bathrooms === 1 ? "baie" : "băi"}` },
    { icon: Users, label: `Max. ${property.maxGuests} oaspeți` },
    {
      icon: Clock,
      label: `Check-in ${property.checkInTime.slice(0, 5)} · Check-out ${property.checkOutTime.slice(0, 5)}`,
    },
  ]

  return (
    <div className="flex flex-wrap items-center gap-x-2 gap-y-1.5 text-sm text-muted-foreground">
      {facts.map((fact, index) => (
        <span key={fact.label} className="flex items-center gap-2">
          {index > 0 && <span className="text-border">·</span>}
          <span className="flex items-center gap-1.5">
            <fact.icon className="size-4 shrink-0" />
            {fact.label}
          </span>
        </span>
      ))}
    </div>
  )
}
