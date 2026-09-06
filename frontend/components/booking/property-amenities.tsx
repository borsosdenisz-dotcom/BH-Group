import { FACILITY_ICONS, FACILITY_LABELS } from "@/lib/property-labels"
import type { Facility } from "@/lib/api/types"

interface PropertyAmenitiesProps {
  facilities: Facility[]
}

export function PropertyAmenities({ facilities }: PropertyAmenitiesProps) {
  return (
    <section className="flex flex-col gap-4">
      <h2 className="font-heading text-lg font-semibold tracking-tight">Facilități</h2>
      {facilities.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nu sunt facilități publicate pentru această proprietate.</p>
      ) : <div className="grid grid-cols-1 gap-px overflow-hidden border border-border bg-border sm:grid-cols-2 lg:grid-cols-3">
        {facilities.map((facility) => {
          const Icon = FACILITY_ICONS[facility]
          return (
            <div key={facility} className="flex min-h-16 items-center gap-3 bg-card px-4 text-sm">
              <Icon className="size-5 shrink-0 text-primary" />
              {FACILITY_LABELS[facility]}
            </div>
          )
        })}
      </div>}
    </section>
  )
}
