"use client"

import { useState } from "react"
import { useRouter } from "next/navigation"
import { motion } from "motion/react"
import { Calendar, MapPin, Search, Users } from "lucide-react"
import { Input } from "@/components/ui/input"
import { Button } from "@/components/ui/button"

export function HomeSearchBar() {
  const router = useRouter()
  const [destination, setDestination] = useState("")
  const [checkIn, setCheckIn] = useState("")
  const [checkOut, setCheckOut] = useState("")
  const [guests, setGuests] = useState("")

  function handleSearch() {
    const params = new URLSearchParams()
    if (destination) params.set("search", destination)
    if (checkIn) params.set("checkIn", checkIn)
    if (checkOut) params.set("checkOut", checkOut)
    if (guests) params.set("guests", guests)
    router.push(`/book${params.toString() ? `?${params.toString()}` : ""}`)
  }

  return (
    <section className="border-b border-border/60 bg-card py-10">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true }}
        transition={{ duration: 0.7, ease: [0.16, 1, 0.3, 1] }}
        className="mx-auto w-full max-w-4xl px-6 sm:px-10"
      >
        <div className="flex flex-col gap-3 rounded-2xl border border-border/60 bg-background p-4 shadow-sm sm:flex-row sm:items-end sm:gap-2">
          <div className="flex min-w-0 flex-1 flex-col gap-1.5">
            <label className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
              <MapPin className="size-3.5" />
              Destinație
            </label>
            <Input
              placeholder="Oraș sau nume proprietate"
              value={destination}
              onChange={(e) => setDestination(e.target.value)}
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <label className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
              <Calendar className="size-3.5" />
              Check-in
            </label>
            <Input type="date" value={checkIn} onChange={(e) => setCheckIn(e.target.value)} className="w-full sm:w-36" />
          </div>
          <div className="flex flex-col gap-1.5">
            <label className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
              <Calendar className="size-3.5" />
              Check-out
            </label>
            <Input type="date" value={checkOut} onChange={(e) => setCheckOut(e.target.value)} className="w-full sm:w-36" />
          </div>
          <div className="flex flex-col gap-1.5">
            <label className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
              <Users className="size-3.5" />
              Oaspeți
            </label>
            <Input
              type="number"
              min={1}
              placeholder="2"
              value={guests}
              onChange={(e) => setGuests(e.target.value)}
              className="w-full sm:w-20"
            />
          </div>
          <Button size="lg" className="gap-2" onClick={handleSearch}>
            <Search className="size-4" />
            Caută
          </Button>
        </div>
      </motion.div>
    </section>
  )
}
