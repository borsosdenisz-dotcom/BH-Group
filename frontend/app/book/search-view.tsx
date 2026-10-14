"use client"

import { Suspense, useEffect, useMemo, useRef, useState } from "react"
import dynamic from "next/dynamic"
import { useSearchParams } from "next/navigation"
import { Map as MapIcon, SlidersHorizontal, Search } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import { DataPagination } from "@/components/ui/data-pagination"
import { DateRangePicker } from "@/components/booking/date-range-picker"
import { GuestPicker } from "@/components/booking/guest-picker"
import { PublicPropertyCard } from "@/components/booking/public-property-card"
import { usePublicProperties } from "@/hooks/use-public-booking"
import { ALL_FACILITIES, FACILITY_LABELS } from "@/lib/property-labels"
import { cn } from "@/lib/utils"
import type { Facility } from "@/lib/api/types"

const LeafletMap = dynamic(() => import("@/components/map/leaflet-map"), {
  ssr: false,
  loading: () => <Skeleton className="size-full rounded-2xl" />,
})

function formatPinPrice(value: number | null, currency: string) {
  if (value == null) return "La cerere"
  return `${new Intl.NumberFormat("ro-RO", { maximumFractionDigits: 0 }).format(value)} ${currency}`
}

function SearchForm() {
  const initialParams = useSearchParams()
  const [search, setSearch] = useState(initialParams.get("search") ?? "")
  // `guests` is still the single total the API filters on, and still starts
  // undefined so an untouched search stays unfiltered exactly as before.
  // adults/children only back the picker's display and come from the URL when
  // the homepage bar put them there.
  const [guests, setGuests] = useState<number | undefined>(
    initialParams.get("guests") ? Number(initialParams.get("guests")) : undefined
  )
  const [adults, setAdults] = useState(() => {
    const fromUrl = Number(initialParams.get("adults"))
    if (fromUrl > 0) return fromUrl
    const total = Number(initialParams.get("guests"))
    return total > 0 ? total - Math.max(0, Number(initialParams.get("children")) || 0) : 2
  })
  const [children, setChildren] = useState(() =>
    Math.max(0, Number(initialParams.get("children")) || 0)
  )
  const [checkIn, setCheckIn] = useState(initialParams.get("checkIn") ?? "")
  const [checkOut, setCheckOut] = useState(initialParams.get("checkOut") ?? "")
  const [page, setPage] = useState(0)
  const [showFilters, setShowFilters] = useState(false)
  // Card or pin under the pointer, and the pin last clicked; the hovered one wins.
  const [hoveredId, setHoveredId] = useState<string | null>(null)
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const activeId = hoveredId ?? selectedId
  // Below lg the map is hidden until asked for, then shown under the list.
  const [showMap, setShowMap] = useState(false)
  const mapSectionRef = useRef<HTMLDivElement>(null)

  // The map opens under the list, usually off-screen on a phone - bring it into view.
  useEffect(() => {
    if (showMap) mapSectionRef.current?.scrollIntoView({ behavior: "smooth", block: "nearest" })
  }, [showMap])
  const [minPrice, setMinPrice] = useState<number | undefined>(undefined)
  const [maxPrice, setMaxPrice] = useState<number | undefined>(undefined)
  const [bedrooms, setBedrooms] = useState<number | undefined>(undefined)
  const [facilities, setFacilities] = useState<Facility[]>([])

  const { data, isLoading } = usePublicProperties({
    search: search || undefined,
    guests,
    minPrice,
    maxPrice,
    bedrooms,
    facilities: facilities.length ? facilities : undefined,
    checkIn: checkIn || undefined,
    checkOut: checkOut || undefined,
    page,
  })

  function toggleFacility(facility: Facility) {
    setFacilities((prev) =>
      prev.includes(facility) ? prev.filter((f) => f !== facility) : [...prev, facility]
    )
    setPage(0)
  }

  const activeFilterCount = facilities.length + (minPrice ? 1 : 0) + (maxPrice ? 1 : 0) + (bedrooms ? 1 : 0)

  const mapMarkers = useMemo(
    () =>
      (data?.content ?? [])
        .filter((property) => property.latitude != null && property.longitude != null)
        .map((property) => ({
          id: property.id,
          lat: property.latitude as number,
          lng: property.longitude as number,
          label: property.name,
          href: `/book/${property.id}`,
          price: formatPinPrice(property.basePricePerNight, property.currency),
        })),
    [data]
  )

  const searchParams = new URLSearchParams()
  if (checkIn) searchParams.set("checkIn", checkIn)
  if (checkOut) searchParams.set("checkOut", checkOut)
  if (guests) searchParams.set("guests", String(guests))

  function selectFromMap(id: string) {
    setSelectedId(id)
    document
      .querySelector(`[data-property-card="${CSS.escape(id)}"]`)
      ?.scrollIntoView({ behavior: "smooth", block: "nearest" })
  }

  const hasMap = mapMarkers.length > 0

  return (
    // One container for heading, search bar and results, so all three share the same edges.
    <div className="mx-auto flex w-full max-w-[1400px] flex-col px-4 sm:px-6 lg:px-10">
      <div className="text-center">
        <h1 className="font-heading text-3xl font-semibold tracking-tight sm:text-4xl">
          Găsește-ți următoarea vacanță
        </h1>
        <p className="mt-2 text-muted-foreground">
          Caută dintre proprietățile noastre disponibile pentru închiriere.
        </p>
      </div>

      <div className="flex flex-col gap-3 py-6">
        {/* Segments in one bar on desktop, stacked full-width on phones. Dates
            and guests are popovers, so opening either overlays the page instead
            of pushing the results down. */}
        <div className="flex w-full flex-col gap-3 rounded-2xl border border-border/60 bg-card p-3 shadow-sm sm:flex-row sm:flex-wrap sm:items-end sm:gap-2 lg:flex-nowrap">
          <div className="flex min-w-0 flex-col gap-1.5 sm:min-w-48 sm:flex-1">
            <span className="px-1 text-xs font-medium text-muted-foreground">Destinație</span>
            <div className="relative">
              <Search className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                placeholder="Oraș sau nume proprietate"
                className="h-11 rounded-xl pl-9"
                value={search}
                onChange={(e) => {
                  setSearch(e.target.value)
                  setPage(0)
                }}
              />
            </div>
          </div>

          <DateRangePicker
            label="Perioadă"
            checkIn={checkIn}
            checkOut={checkOut}
            onChange={(from, to) => {
              setCheckIn(from)
              setCheckOut(to)
              setPage(0)
            }}
            className="sm:w-52"
          />

          <GuestPicker
            label="Oaspeți"
            adults={adults}
            childrenCount={children}
            onChange={(nextAdults, nextChildren) => {
              setAdults(nextAdults)
              setChildren(nextChildren)
              setGuests(nextAdults + nextChildren)
              setPage(0)
            }}
            className="sm:w-44"
          />

          <Button
            type="button"
            className="h-11 w-full gap-2 rounded-xl px-5 sm:w-auto"
            onClick={() => setPage(0)}
          >
            <Search className="size-4" />
            Caută
          </Button>
          <Button
            type="button"
            variant="outline"
            className="h-11 w-full gap-2 rounded-xl sm:w-auto"
            onClick={() => setShowFilters((prev) => !prev)}
          >
            <SlidersHorizontal className="size-4" />
            Filtre{activeFilterCount > 0 ? ` (${activeFilterCount})` : ""}
          </Button>
        </div>

        {showFilters && (
          <div className="flex w-full flex-col gap-5 rounded-2xl border border-border/60 bg-card p-4 shadow-sm">
            <div className="flex flex-wrap items-end gap-3">
              <div className="flex flex-col gap-1">
                <label className="text-xs font-medium text-muted-foreground">Preț min. / noapte</label>
                <Input
                  type="number"
                  min={0}
                  className="w-32"
                  value={minPrice ?? ""}
                  onChange={(e) => {
                    setMinPrice(e.target.value ? Number(e.target.value) : undefined)
                    setPage(0)
                  }}
                />
              </div>
              <div className="flex flex-col gap-1">
                <label className="text-xs font-medium text-muted-foreground">Preț max. / noapte</label>
                <Input
                  type="number"
                  min={0}
                  className="w-32"
                  value={maxPrice ?? ""}
                  onChange={(e) => {
                    setMaxPrice(e.target.value ? Number(e.target.value) : undefined)
                    setPage(0)
                  }}
                />
              </div>
              <div className="flex flex-col gap-1">
                <label className="text-xs font-medium text-muted-foreground">Dormitoare min.</label>
                <Select
                  value={bedrooms ? String(bedrooms) : "any"}
                  onValueChange={(value) => {
                    setBedrooms(value === "any" ? undefined : Number(value))
                    setPage(0)
                  }}
                >
                  <SelectTrigger className="w-32">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="any">Oricâte</SelectItem>
                    {[1, 2, 3, 4, 5].map((n) => (
                      <SelectItem key={n} value={String(n)}>
                        {n}+
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>
            <div>
              <p className="mb-2 text-xs font-medium text-muted-foreground">Facilități</p>
              <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-4">
                {ALL_FACILITIES.map((facility) => (
                  <label key={facility} className="flex items-center gap-2 text-sm">
                    <Checkbox
                      checked={facilities.includes(facility)}
                      onCheckedChange={() => toggleFacility(facility)}
                    />
                    {FACILITY_LABELS[facility]}
                  </label>
                ))}
              </div>
            </div>
          </div>
        )}
      </div>

      {isLoading ? (
        <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 xl:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} className="aspect-[4/3] w-full rounded-2xl" />
          ))}
        </div>
      ) : !data || data.content.length === 0 ? (
        <div className="flex flex-col items-center justify-center rounded-2xl border border-dashed border-border/60 px-6 py-10 text-center sm:py-14">
          <p className="text-sm text-muted-foreground">
            Nu au fost găsite proprietăți disponibile pentru criteriile selectate.
          </p>
        </div>
      ) : (
        <div className={cn("grid gap-6", hasMap && "lg:grid-cols-[minmax(0,1fr)_minmax(380px,460px)]")}>
          <div className="flex min-w-0 flex-col gap-6">
            {hasMap && (
              <Button
                type="button"
                variant="outline"
                className="w-full gap-2 rounded-xl sm:w-auto sm:self-start lg:hidden"
                aria-expanded={showMap}
                onClick={() => setShowMap((prev) => !prev)}
              >
                <MapIcon className="size-4" />
                {showMap ? "Ascunde harta" : "Arată harta"}
              </Button>
            )}
            <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 xl:grid-cols-3">
              {data.content.map((property) => (
                <div
                  key={property.id}
                  data-property-card={property.id}
                  data-active={activeId === property.id || undefined}
                  className={cn(
                    "rounded-2xl transition-shadow duration-200",
                    activeId === property.id && "ring-2 ring-primary ring-offset-2 ring-offset-background"
                  )}
                  onMouseEnter={() => setHoveredId(property.id)}
                  onMouseLeave={() => setHoveredId((current) => (current === property.id ? null : current))}
                >
                  <PublicPropertyCard property={property} searchParams={searchParams.toString()} />
                </div>
              ))}
            </div>
            <DataPagination page={data.page} totalPages={data.totalPages} onPageChange={setPage} />
          </div>

          {hasMap && (
            // Desktop: sticky beside the list, never taller than the viewport.
            // Below lg: hidden until "Arată harta", then shown under the list.
            <div
              ref={mapSectionRef}
              data-testid="results-map"
              className={cn(
                "h-[60vh] min-h-80 overflow-hidden rounded-2xl lg:sticky lg:top-20 lg:block lg:h-[calc(100vh-6rem)] lg:min-h-0",
                showMap ? "block" : "hidden"
              )}
            >
              <LeafletMap
                markers={mapMarkers}
                activeId={activeId}
                onMarkerHover={setHoveredId}
                onMarkerClick={selectFromMap}
                className="rounded-2xl"
              />
            </div>
          )}
        </div>
      )}
    </div>
  )
}

export function BookingSearchView() {
  return (
    <Suspense fallback={null}>
      <SearchForm />
    </Suspense>
  )
}
