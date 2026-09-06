"use client"

import { useMemo, useState } from "react"
import { ChevronLeft, ChevronRight } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { usePublicCalendar } from "@/hooks/use-public-booking"
import { formatLocalDate, parseLocalDate } from "@/lib/date"
import { cn } from "@/lib/utils"
import type { PublicCalendarEntryResponse } from "@/lib/api/types"

const WEEKDAY_LABELS = ["Lun", "Mar", "Mie", "Joi", "Vin", "Sâm", "Dum"]
const MONTH_LABELS = [
  "Ianuarie", "Februarie", "Martie", "Aprilie", "Mai", "Iunie",
  "Iulie", "August", "Septembrie", "Octombrie", "Noiembrie", "Decembrie",
]

function toDateOnly(date: Date) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate())
}

function addDays(date: Date, amount: number) {
  const next = new Date(date)
  next.setDate(next.getDate() + amount)
  return next
}

function diffDays(from: Date, to: Date) {
  return Math.round((toDateOnly(to).getTime() - toDateOnly(from).getTime()) / 86400000)
}

function gridDaysForMonth(month: Date) {
  const year = month.getFullYear()
  const monthIndex = month.getMonth()
  const firstOfMonth = new Date(year, monthIndex, 1)
  const startOffset = (firstOfMonth.getDay() + 6) % 7
  const gridStart = new Date(year, monthIndex, 1 - startOffset)
  return Array.from({ length: 42 }, (_, i) => addDays(gridStart, i))
}

function isBookedNight(day: Date, ranges: PublicCalendarEntryResponse[]) {
  const d = toDateOnly(day)
  return ranges.some((range) => {
    const checkIn = toDateOnly(parseLocalDate(range.checkInDate))
    const checkOut = toDateOnly(parseLocalDate(range.checkOutDate))
    return d >= checkIn && d < checkOut
  })
}

interface AvailabilityCalendarProps {
  propertyId: string
  minStayNights?: number | null
  maxStayNights?: number | null
  checkIn: string | null
  checkOut: string | null
  onSelect: (range: { checkIn: string | null; checkOut: string | null }) => void
}

export function AvailabilityCalendar({
  propertyId,
  minStayNights,
  maxStayNights,
  checkIn,
  checkOut,
  onSelect,
}: AvailabilityCalendarProps) {
  const today = toDateOnly(new Date())
  const [anchorMonth, setAnchorMonth] = useState(() => new Date(today.getFullYear(), today.getMonth(), 1))

  const nextMonth = useMemo(
    () => new Date(anchorMonth.getFullYear(), anchorMonth.getMonth() + 1, 1),
    [anchorMonth]
  )
  const firstGridDays = useMemo(() => gridDaysForMonth(anchorMonth), [anchorMonth])
  const secondGridDays = useMemo(() => gridDaysForMonth(nextMonth), [nextMonth])

  // Fetch a padded range covering both visible month grids (including the
  // leading/trailing days from adjacent months shown for calendar layout),
  // so those days reflect real availability instead of only the current month.
  const rangeFrom = formatLocalDate(firstGridDays[0])
  const rangeTo = formatLocalDate(secondGridDays[secondGridDays.length - 1])
  const { data: bookedRanges, isLoading } = usePublicCalendar(propertyId, rangeFrom, rangeTo)
  const ranges = bookedRanges ?? []

  const checkInDate = checkIn ? parseLocalDate(checkIn) : null
  const checkOutDate = checkOut ? parseLocalDate(checkOut) : null

  const canGoBack = anchorMonth > new Date(today.getFullYear(), today.getMonth(), 1)

  function isValidCheckoutCandidate(day: Date, from: Date) {
    if (day <= from) return false
    const nights = diffDays(from, day)
    if (minStayNights && nights < minStayNights) return false
    if (maxStayNights && nights > maxStayNights) return false
    for (let d = from; d < day; d = addDays(d, 1)) {
      if (isBookedNight(d, ranges)) return false
    }
    return true
  }

  function handleDayClick(day: Date) {
    const isPast = day < today
    if (isPast) return

    if (!checkInDate || checkOutDate) {
      // Starting a fresh selection.
      if (isBookedNight(day, ranges)) return
      onSelect({ checkIn: formatLocalDate(day), checkOut: null })
      return
    }

    // A check-in is already selected; this click chooses the check-out.
    if (isValidCheckoutCandidate(day, checkInDate)) {
      onSelect({ checkIn: formatLocalDate(checkInDate), checkOut: formatLocalDate(day) })
      return
    }

    // Not a valid check-out for the current check-in (before it, or the
    // range would cross a booked night, or violate min/max stay) - treat
    // it as restarting the selection from this day instead, unless the
    // day itself is unavailable.
    if (isBookedNight(day, ranges)) return
    onSelect({ checkIn: formatLocalDate(day), checkOut: null })
  }

  function renderMonth(month: Date, days: Date[], hiddenOnMobile: boolean) {
    const monthIndex = month.getMonth()
    return (
      <div className={cn("flex flex-1 flex-col gap-3", hiddenOnMobile && "hidden sm:flex")}>
        <p className="text-sm font-semibold">
          {MONTH_LABELS[monthIndex]} {month.getFullYear()}
        </p>
        <div className="overflow-hidden rounded-xl border border-border/60">
          <div className="grid grid-cols-7 border-b border-border/60 bg-muted/40 text-xs font-medium text-muted-foreground">
            {WEEKDAY_LABELS.map((label) => (
              <div key={label} className="px-1 py-2 text-center">
                {label}
              </div>
            ))}
          </div>
          <div className="grid grid-cols-7">
            {days.map((day) => {
              const isCurrentMonth = day.getMonth() === monthIndex
              const isPast = day < today
              const booked = isBookedNight(day, ranges)
              const isCheckIn = checkInDate && formatLocalDate(day) === formatLocalDate(checkInDate)
              const isCheckOut = checkOutDate && formatLocalDate(day) === formatLocalDate(checkOutDate)
              const isInRange =
                checkInDate && checkOutDate && day > checkInDate && day < checkOutDate

              const selectingCheckout = !!checkInDate && !checkOutDate
              const isValidCheckoutTarget =
                selectingCheckout && day > checkInDate! && isValidCheckoutCandidate(day, checkInDate!)

              const disabled = isPast || (booked && !isCheckOut)

              return (
                <button
                  key={day.toISOString()}
                  type="button"
                  disabled={disabled}
                  onClick={() => handleDayClick(day)}
                  aria-pressed={!!isCheckIn || !!isCheckOut}
                  aria-label={day.toLocaleDateString("ro-RO", { day: "numeric", month: "long", year: "numeric" })}
                  className={cn(
                    "flex h-11 items-center justify-center border-b border-r border-border/60 text-sm last:border-r-0 sm:h-12",
                    "disabled:cursor-not-allowed",
                    !isCurrentMonth && "text-muted-foreground/40",
                    isPast && "text-muted-foreground/30",
                    booked && !isPast && isCurrentMonth && "bg-muted text-muted-foreground/70 line-through",
                    isCurrentMonth && !isPast && !booked && "hover:bg-accent",
                    selectingCheckout && isCurrentMonth && !isPast && !booked && !isValidCheckoutTarget && "text-muted-foreground/50",
                    isInRange && "bg-primary/15 font-medium",
                    (isCheckIn || isCheckOut) && "bg-primary font-semibold text-primary-foreground hover:bg-primary"
                  )}
                >
                  {day.getDate()}
                </button>
              )
            })}
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <p className="text-sm font-medium">
          {!checkInDate
            ? "Alege data de check-in"
            : !checkOutDate
              ? "Alege data de check-out"
              : `${diffDays(checkInDate, checkOutDate)} nopți selectate`}
        </p>
        <div className="flex gap-1.5">
          <Button
            type="button"
            size="icon"
            variant="outline"
            className="size-8"
            disabled={!canGoBack}
            onClick={() => setAnchorMonth(new Date(anchorMonth.getFullYear(), anchorMonth.getMonth() - 1, 1))}
            aria-label="Luna anterioară"
          >
            <ChevronLeft className="size-4" />
          </Button>
          <Button
            type="button"
            size="icon"
            variant="outline"
            className="size-8"
            onClick={() => setAnchorMonth(new Date(anchorMonth.getFullYear(), anchorMonth.getMonth() + 1, 1))}
            aria-label="Luna următoare"
          >
            <ChevronRight className="size-4" />
          </Button>
        </div>
      </div>

      {isLoading ? (
        <div className="flex gap-4">
          <Skeleton className="h-80 w-full" />
          <Skeleton className="hidden h-80 w-full sm:block" />
        </div>
      ) : (
        <div className="flex flex-col gap-6 sm:flex-row">
          {renderMonth(anchorMonth, firstGridDays, false)}
          {renderMonth(nextMonth, secondGridDays, true)}
        </div>
      )}

      <div className="flex flex-wrap items-center gap-x-5 gap-y-2 border-t border-border/60 pt-4 text-xs text-muted-foreground">
        <span className="flex items-center gap-1.5">
          <span className="inline-block size-2.5 rounded-sm border border-border/60" />
          Disponibil
        </span>
        <span className="flex items-center gap-1.5">
          <span className="inline-block size-2.5 rounded-sm bg-muted" />
          Indisponibil
        </span>
        <span className="flex items-center gap-1.5">
          <span className="inline-block size-2.5 rounded-sm bg-primary" />
          Selecția ta
        </span>
        {(minStayNights || maxStayNights) && (
          <span>
            {minStayNights ? `Sejur minim ${minStayNights} nopți` : ""}
            {minStayNights && maxStayNights ? " · " : ""}
            {maxStayNights ? `Sejur maxim ${maxStayNights} nopți` : ""}
          </span>
        )}
      </div>
    </div>
  )
}
