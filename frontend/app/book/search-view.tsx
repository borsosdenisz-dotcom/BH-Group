"use client"

import { Suspense, useMemo, useState } from "react"
import dynamic from "next/dynamic"
import { useSearchParams } from "next/navigation"
import { Building2, RotateCcw, Search, SlidersHorizontal } from "lucide-react"
import { PublicPropertyCard } from "@/components/booking/public-property-card"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { DataPagination } from "@/components/ui/data-pagination"
import { EmptyState } from "@/components/ui/empty-state"
import { Input } from "@/components/ui/input"
import { PageHeader } from "@/components/ui/page-header"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Sheet, SheetClose, SheetContent, SheetDescription, SheetFooter, SheetHeader, SheetTitle, SheetTrigger } from "@/components/ui/sheet"
import { Skeleton } from "@/components/ui/skeleton"
import { usePublicProperties } from "@/hooks/use-public-booking"
import type { Facility } from "@/lib/api/types"
import { ALL_FACILITIES, FACILITY_LABELS } from "@/lib/property-labels"

const LeafletMap = dynamic(() => import("@/components/map/leaflet-map"), { ssr: false, loading: () => <Skeleton className="h-96 w-full" /> })

interface FiltersPanelProps {
  idPrefix: string
  minPrice?: number
  maxPrice?: number
  bedrooms?: number
  facilities: Facility[]
  onMinPriceChange: (value?: number) => void
  onMaxPriceChange: (value?: number) => void
  onBedroomsChange: (value?: number) => void
  onFacilityToggle: (facility: Facility) => void
  onReset: () => void
}

function FiltersPanel({ idPrefix, minPrice, maxPrice, bedrooms, facilities, onMinPriceChange, onMaxPriceChange, onBedroomsChange, onFacilityToggle, onReset }: FiltersPanelProps) {
  return (
    <div className="flex flex-col gap-6">
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <div><label htmlFor={`${idPrefix}-min-price`} className="mb-1.5 block text-xs font-semibold text-muted-foreground">Preț minim / noapte</label><Input id={`${idPrefix}-min-price`} type="number" min={0} value={minPrice ?? ""} onChange={(event) => onMinPriceChange(event.target.value ? Number(event.target.value) : undefined)} /></div>
        <div><label htmlFor={`${idPrefix}-max-price`} className="mb-1.5 block text-xs font-semibold text-muted-foreground">Preț maxim / noapte</label><Input id={`${idPrefix}-max-price`} type="number" min={0} value={maxPrice ?? ""} onChange={(event) => onMaxPriceChange(event.target.value ? Number(event.target.value) : undefined)} /></div>
        <div><label htmlFor={`${idPrefix}-bedrooms`} className="mb-1.5 block text-xs font-semibold text-muted-foreground">Dormitoare</label><Select value={bedrooms ? String(bedrooms) : "any"} onValueChange={(value) => onBedroomsChange(value === "any" ? undefined : Number(value))}><SelectTrigger id={`${idPrefix}-bedrooms`} className="w-full"><SelectValue /></SelectTrigger><SelectContent><SelectItem value="any">Oricâte</SelectItem>{[1, 2, 3, 4, 5].map((number) => <SelectItem key={number} value={String(number)}>{number} sau mai multe</SelectItem>)}</SelectContent></Select></div>
      </div>
      <fieldset><legend className="mb-3 text-xs font-semibold text-muted-foreground">Facilități</legend><div className="grid grid-cols-2 gap-x-4 gap-y-1 sm:grid-cols-3 lg:grid-cols-4">{ALL_FACILITIES.map((facility) => <label key={facility} className="flex min-h-11 cursor-pointer items-center gap-2 text-sm"><Checkbox checked={facilities.includes(facility)} onCheckedChange={() => onFacilityToggle(facility)} />{FACILITY_LABELS[facility]}</label>)}</div></fieldset>
      <Button type="button" variant="ghost" className="w-fit" onClick={onReset}><RotateCcw className="size-4" />Resetează filtrele</Button>
    </div>
  )
}

function SearchForm() {
  const initialParams = useSearchParams()
  const [search, setSearch] = useState(initialParams.get("search") ?? "")
  const [guests, setGuests] = useState<number | undefined>(initialParams.get("guests") ? Number(initialParams.get("guests")) : undefined)
  const [checkIn, setCheckIn] = useState(initialParams.get("checkIn") ?? "")
  const [checkOut, setCheckOut] = useState(initialParams.get("checkOut") ?? "")
  const [page, setPage] = useState(0)
  const [showFilters, setShowFilters] = useState(false)
  const [mobileFiltersOpen, setMobileFiltersOpen] = useState(false)
  const [activeId, setActiveId] = useState<string | null>(null)
  const [minPrice, setMinPrice] = useState<number | undefined>()
  const [maxPrice, setMaxPrice] = useState<number | undefined>()
  const [bedrooms, setBedrooms] = useState<number | undefined>()
  const [facilities, setFacilities] = useState<Facility[]>([])

  const { data, isLoading, isError, refetch } = usePublicProperties({ search: search || undefined, guests, minPrice, maxPrice, bedrooms, facilities: facilities.length ? facilities : undefined, checkIn: checkIn || undefined, checkOut: checkOut || undefined, page })
  const activeFilterCount = facilities.length + (minPrice !== undefined ? 1 : 0) + (maxPrice !== undefined ? 1 : 0) + (bedrooms !== undefined ? 1 : 0)
  const mapMarkers = useMemo(() => (data?.content ?? []).filter((property) => property.latitude != null && property.longitude != null).map((property) => ({ id: property.id, lat: property.latitude as number, lng: property.longitude as number, label: property.name, href: `/book/${property.id}` })), [data])
  const forwardedSearchParams = useMemo(() => { const params = new URLSearchParams(); if (checkIn) params.set("checkIn", checkIn); if (checkOut) params.set("checkOut", checkOut); if (guests) params.set("guests", String(guests)); return params.toString() }, [checkIn, checkOut, guests])

  function updatePage() { setPage(0) }
  function toggleFacility(facility: Facility) { setFacilities((current) => current.includes(facility) ? current.filter((item) => item !== facility) : [...current, facility]); updatePage() }
  function resetFilters() { setMinPrice(undefined); setMaxPrice(undefined); setBedrooms(undefined); setFacilities([]); updatePage() }
  const filterProps = { minPrice, maxPrice, bedrooms, facilities, onMinPriceChange: (value?: number) => { setMinPrice(value); updatePage() }, onMaxPriceChange: (value?: number) => { setMaxPrice(value); updatePage() }, onBedroomsChange: (value?: number) => { setBedrooms(value); updatePage() }, onFacilityToggle: toggleFacility, onReset: resetFilters }

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-8">
      <PageHeader eyebrow="Rezervări directe" title="Găsește proprietatea potrivită" description="Caută în listările publicate, compară detaliile și trimite o cerere direct echipei BH Group." />

      <form className="grid min-w-0 gap-3 border border-border/80 bg-card p-4 shadow-[var(--shadow-sm)] sm:grid-cols-2 lg:grid-cols-[minmax(13rem,1.5fr)_1fr_1fr_.7fr_auto] lg:items-end" onSubmit={(event) => { event.preventDefault(); updatePage() }}>
        <div className="min-w-0 sm:col-span-2 lg:col-span-1"><label htmlFor="search-destination" className="mb-1.5 block text-xs font-semibold text-muted-foreground">Destinație</label><div className="relative"><Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" /><Input id="search-destination" value={search} onChange={(event) => { setSearch(event.target.value); updatePage() }} placeholder="Oraș sau proprietate" className="pl-9" /></div></div>
        <div className="min-w-0"><label htmlFor="search-check-in" className="mb-1.5 block text-xs font-semibold text-muted-foreground">Sosire</label><Input id="search-check-in" type="date" value={checkIn} onChange={(event) => { setCheckIn(event.target.value); updatePage() }} /></div>
        <div className="min-w-0"><label htmlFor="search-check-out" className="mb-1.5 block text-xs font-semibold text-muted-foreground">Plecare</label><Input id="search-check-out" type="date" value={checkOut} onChange={(event) => { setCheckOut(event.target.value); updatePage() }} /></div>
        <div className="min-w-0"><label htmlFor="search-guests" className="mb-1.5 block text-xs font-semibold text-muted-foreground">Oaspeți</label><Input id="search-guests" type="number" min={1} value={guests ?? ""} onChange={(event) => { setGuests(event.target.value ? Number(event.target.value) : undefined); updatePage() }} /></div>
        <Button type="submit" className="w-full"><Search className="size-4" />Caută</Button>
      </form>

      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border pb-5">
        <p className="text-sm text-muted-foreground">{isLoading ? "Se caută proprietăți…" : `${data?.totalElements ?? 0} ${(data?.totalElements ?? 0) === 1 ? "proprietate" : "proprietăți"}`}</p>
        <div className="hidden md:block"><Button type="button" variant="outline" onClick={() => setShowFilters((current) => !current)} aria-expanded={showFilters}><SlidersHorizontal className="size-4" />Filtre{activeFilterCount ? ` (${activeFilterCount})` : ""}</Button></div>
        <div className="md:hidden"><Sheet open={mobileFiltersOpen} onOpenChange={setMobileFiltersOpen}><SheetTrigger render={<Button type="button" variant="outline" />}><SlidersHorizontal className="size-4" />Filtre{activeFilterCount ? ` (${activeFilterCount})` : ""}</SheetTrigger><SheetContent side="bottom" className="max-h-[88svh] overflow-y-auto"><SheetHeader><SheetTitle>Filtrează proprietățile</SheetTitle><SheetDescription>Alege criteriile importante pentru sejurul tău.</SheetDescription></SheetHeader><div className="px-4 pb-4"><FiltersPanel idPrefix="mobile" {...filterProps} /></div><SheetFooter className="sticky bottom-0 border-t bg-background"><SheetClose render={<Button className="w-full" />}>Vezi rezultatele</SheetClose></SheetFooter></SheetContent></Sheet></div>
      </div>

      {showFilters && <div className="hidden border border-border/80 bg-card p-5 shadow-[var(--shadow-xs)] md:block"><FiltersPanel idPrefix="desktop" {...filterProps} /></div>}

      {isLoading ? (
        <div className="grid gap-6 sm:grid-cols-2 xl:grid-cols-3" aria-label="Se încarcă proprietățile">{Array.from({ length: 6 }).map((_, index) => <div key={index} className="space-y-3"><Skeleton className="aspect-[4/3] w-full" /><Skeleton className="h-6 w-2/3" /><Skeleton className="h-4 w-1/2" /></div>)}</div>
      ) : isError ? (
        <EmptyState icon={Building2} title="Căutarea nu este disponibilă momentan" description="Nu am putut încărca proprietățile. Încearcă din nou." action={<Button onClick={() => refetch()}>Reîncearcă</Button>} />
      ) : !data || data.content.length === 0 ? (
        <EmptyState icon={Building2} title="Nu am găsit proprietăți" description={activeFilterCount ? "Schimbă sau resetează filtrele pentru a vedea alte rezultate." : "Nu sunt proprietăți publicate pentru criteriile selectate."} action={activeFilterCount ? <Button variant="outline" onClick={resetFilters}>Resetează filtrele</Button> : undefined} />
      ) : (
        <div className="grid min-w-0 gap-7 lg:grid-cols-[minmax(0,1fr)_minmax(20rem,26rem)]">
          <div className="min-w-0"><div className="grid gap-6 sm:grid-cols-2 xl:grid-cols-3">{data.content.map((property) => <div key={property.id} onMouseEnter={() => setActiveId(property.id)} onMouseLeave={() => setActiveId((current) => current === property.id ? null : current)}><PublicPropertyCard property={property} searchParams={forwardedSearchParams} /></div>)}</div><div className="mt-8"><DataPagination page={data.page} totalPages={data.totalPages} onPageChange={setPage} /></div></div>
          {mapMarkers.length > 0 && <div className="h-96 overflow-hidden border border-border lg:sticky lg:top-24 lg:h-[calc(100vh-8rem)]"><LeafletMap markers={mapMarkers} activeId={activeId} /></div>}
        </div>
      )}
    </div>
  )
}

export function BookingSearchView() {
  return <Suspense fallback={<div className="mx-auto max-w-7xl"><Skeleton className="h-10 w-72" /><Skeleton className="mt-8 h-32 w-full" /></div>}><SearchForm /></Suspense>
}
