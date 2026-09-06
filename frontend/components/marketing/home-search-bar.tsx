"use client"

import { useState } from "react"
import { useRouter } from "next/navigation"
import { CalendarDays, MapPin, Search, Users } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"

export function HomeSearchBar() {
  const router = useRouter()
  const [search, setSearch] = useState("")
  const [checkIn, setCheckIn] = useState("")
  const [checkOut, setCheckOut] = useState("")
  const [guests, setGuests] = useState("2")

  function handleSearch() {
    const params = new URLSearchParams()
    if (search.trim()) params.set("search", search.trim())
    if (checkIn) params.set("checkIn", checkIn)
    if (checkOut) params.set("checkOut", checkOut)
    if (guests) params.set("guests", guests)
    router.push(`/book${params.size ? `?${params.toString()}` : ""}`)
  }

  return (
    <form
      className="mt-9 grid w-full gap-3 border border-white/18 bg-background p-4 text-foreground shadow-[var(--shadow-md)] sm:grid-cols-2 lg:grid-cols-[minmax(14rem,1.4fr)_1fr_1fr_.65fr_auto] lg:items-end"
      onSubmit={(event) => { event.preventDefault(); handleSearch() }}
      aria-label="Caută o proprietate"
    >
      <div className="min-w-0">
        <label htmlFor="home-destination" className="mb-1.5 flex items-center gap-1.5 text-xs font-semibold text-muted-foreground">
          <MapPin className="size-3.5" aria-hidden="true" /> Destinație
        </label>
        <Input id="home-destination" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Oraș sau proprietate" />
      </div>
      <div className="min-w-0">
        <label htmlFor="home-check-in" className="mb-1.5 flex items-center gap-1.5 text-xs font-semibold text-muted-foreground">
          <CalendarDays className="size-3.5" aria-hidden="true" /> Sosire
        </label>
        <Input id="home-check-in" type="date" value={checkIn} onChange={(event) => setCheckIn(event.target.value)} />
      </div>
      <div className="min-w-0">
        <label htmlFor="home-check-out" className="mb-1.5 flex items-center gap-1.5 text-xs font-semibold text-muted-foreground">
          <CalendarDays className="size-3.5" aria-hidden="true" /> Plecare
        </label>
        <Input id="home-check-out" type="date" value={checkOut} onChange={(event) => setCheckOut(event.target.value)} />
      </div>
      <div className="min-w-0">
        <label htmlFor="home-guests" className="mb-1.5 flex items-center gap-1.5 text-xs font-semibold text-muted-foreground">
          <Users className="size-3.5" aria-hidden="true" /> Oaspeți
        </label>
        <Input id="home-guests" type="number" min={1} value={guests} onChange={(event) => setGuests(event.target.value)} />
      </div>
      <Button type="submit" size="lg" className="w-full lg:w-auto">
        <Search className="size-4" /> Caută
      </Button>
    </form>
  )
}
