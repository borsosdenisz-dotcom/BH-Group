"use client"

import { Suspense, useMemo, useState } from "react"
import dynamic from "next/dynamic"
import { useSearchParams } from "next/navigation"
import { SlidersHorizontal, Search } from "lucide-react"
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
import { PublicPropertyCard } from "@/components/booking/public-property-card"
import { usePublicProperties } from "@/hooks/use-public-booking"
import { ALL_FACILITIES, FACILITY_LABELS } from "@/lib/property-labels"
import type { Facility } from "@/lib/api/types"

const LeafletMap = dynamic(() => import("@/components/map/leaflet-map"), {
  ssr: false,
  loading: () => <Skeleton className="h-96 w-full" />,
})

function SearchForm() {
  const initialParams = useSearchParams()
  const [search, setSearch] = useState(initialParams.get("search") ?? "")
  const [guests, setGuests] = useState<number | undefined>(
    initialParams.get("guests") ? Number(initialParams.get("guests")) : undefined
  )
  const [checkIn, setCheckIn] = useState(initialParams.get("checkIn") ?? "")
  const [checkOut, setCheckOut] = useState(initialParams.get("checkOut") ?? "")
  const [page, setPage] = useState(0)
  const [showFilters, setShowFilters] = useState(false)
  const [activeId, setActiveId] = useState<string | null>(null)
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
        })),
    [data]
  )

  const searchParams = new URLSearchParams()
  if (checkIn) searchParams.set("checkIn", checkIn)
  if (checkOut) searchParams.set("checkOut", checkOut)
  if (guests) searchParams.set("guests", String(guests))

  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-8">
      <div className="text-center">
        <h1 className="font-heading text-3xl font-semibold tracking-tight sm:text-4xl">
          Găsește-ți următoarea vacanță
        </h1>
        <p className="mt-2 text-muted-foreground">
          Caută dintre proprietățile noastre disponibile pentru închiriere.
        </p>
      </div>

      <div className="mx-auto flex w-full max-w-3xl flex-wrap items-end gap-3 rounded-2xl border border-border/60 bg-card p-4 shadow-sm">
        <div className="flex min-w-48 flex-1 flex-col gap-1">
          <label className="text-xs font-medium text-muted-foreground">Destinație</label>
          <div className="relative">
            <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              placeholder="Oraș sau nume proprietate"
              className="pl-9"
              value={search}
              onChange={(e) => {
                setSearch(e.target.value)
                setPage(0)
              }}
            />
          </div>
        </div>
        <div className="flex flex-col gap-1">
          <label className="text-xs font-medium text-muted-foreground">Check-in</label>
          <Input
            type="date"
            value={checkIn}
            onChange={(e) => {
              setCheckIn(e.target.value)
              setPage(0)
            }}
          />
        </div>
        <div className="flex flex-col gap-1">
          <label className="text-xs font-medium text-muted-foreground">Check-out</label>
          <Input
            type="date"
            value={checkOut}
            onChange={(e) => {
              setCheckOut(e.target.value)
              setPage(0)
            }}
          />
        </div>
        <div className="flex w-24 flex-col gap-1">
          <label className="text-xs font-medium text-muted-foreground">Oaspeți</label>
          <Input
            type="number"
            min={1}
            value={guests ?? ""}
            onChange={(e) => {
              setGuests(e.target.value ? Number(e.target.value) : undefined)
              setPage(0)
            }}
          />
        </div>
        <Button type="button" onClick={() => setPage(0)}>
          Caută
        </Button>
        <Button
          type="button"
          variant="outline"
          className="gap-2"
          onClick={() => setShowFilters((prev) => !prev)}
        >
          <SlidersHorizontal className="size-4" />
          Filtre{activeFilterCount > 0 ? ` (${activeFilterCount})` : ""}
        </Button>
      </div>

      {showFilters && (
        <div className="mx-auto flex w-full max-w-3xl flex-col gap-5 rounded-2xl border border-border/60 bg-card p-4 shadow-sm">
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

      {isLoading ? (
        <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {Array.from({ length: 8 }).map((_, i) => (
            <Skeleton key={i} className="aspect-[4/3] w-full rounded-2xl" />
          ))}
        </div>
      ) : !data || data.content.length === 0 ? (
        <div className="flex flex-col items-center justify-center rounded-2xl border border-dashed border-border/60 py-16 text-center">
          <p className="text-sm text-muted-foreground">
            Nu au fost găsite proprietăți disponibile pentru criteriile selectate.
          </p>
        </div>
      ) : (
        <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_400px]">
          <div className="flex flex-col gap-6">
            <div className="grid gap-6 sm:grid-cols-2 xl:grid-cols-3">
              {data.content.map((property) => (
                <div
                  key={property.id}
                  onMouseEnter={() => setActiveId(property.id)}
                  onMouseLeave={() => setActiveId((current) => (current === property.id ? null : current))}
                >
                  <PublicPropertyCard property={property} searchParams={searchParams.toString()} />
                </div>
              ))}
            </div>
            <DataPagination page={data.page} totalPages={data.totalPages} onPageChange={setPage} />
          </div>

          {mapMarkers.length > 0 && (
            <div className="h-96 lg:sticky lg:top-24 lg:h-[calc(100vh-8rem)]">
              <LeafletMap markers={mapMarkers} activeId={activeId} />
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
