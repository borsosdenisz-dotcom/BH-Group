import { FACILITY_ICONS, FACILITY_LABELS } from "@/lib/property-labels"
import type { Facility } from "@/lib/api/types"

interface PropertyAmenitiesProps {
  facilities: Facility[]
}

export function PropertyAmenities({ facilities }: PropertyAmenitiesProps) {
  if (facilities.length === 0) return null

  return (
    <section className="flex flex-col gap-4">
      <h2 className="font-heading text-lg font-semibold tracking-tight">Facilități</h2>
      <div className="grid grid-cols-1 gap-x-6 gap-y-4 sm:grid-cols-2 lg:grid-cols-3">
        {facilities.map((facility) => {
          const Icon = FACILITY_ICONS[facility]
          return (
            <div key={facility} className="flex items-center gap-3 text-sm">
              <Icon className="size-5 shrink-0 text-muted-foreground" />
              {FACILITY_LABELS[facility]}
            </div>
          )
        })}
      </div>
    </section>
  )
}
