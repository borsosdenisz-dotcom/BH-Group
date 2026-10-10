"use client"

import { useId } from "react"
import { Input } from "@/components/ui/input"
import type { DateRange } from "@/lib/commission"

interface ReportPeriodFilterProps {
  value: DateRange
  onChange: (value: DateRange) => void
}

/** The same "De la / Până la" date inputs the finance page uses, with labels tied to their inputs. */
export function ReportPeriodFilter({ value, onChange }: ReportPeriodFilterProps) {
  const id = useId()
  const invalid = Boolean(value.from && value.to && value.from > value.to)

  return (
    <div className="flex flex-col gap-1">
      <div className="flex flex-wrap items-end gap-3">
        <div className="flex flex-col gap-1">
          <label htmlFor={`${id}-from`} className="text-xs text-muted-foreground">
            De la
          </label>
          <Input
            id={`${id}-from`}
            type="date"
            value={value.from}
            onChange={(e) => onChange({ ...value, from: e.target.value })}
            aria-invalid={invalid || undefined}
            className="w-40"
          />
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor={`${id}-to`} className="text-xs text-muted-foreground">
            Până la
          </label>
          <Input
            id={`${id}-to`}
            type="date"
            value={value.to}
            onChange={(e) => onChange({ ...value, to: e.target.value })}
            aria-invalid={invalid || undefined}
            className="w-40"
          />
        </div>
      </div>
      {invalid && (
        <p role="alert" className="text-xs text-destructive">
          Data de început trebuie să fie înaintea datei de sfârșit.
        </p>
      )}
    </div>
  )
}

/** A period the backend accepts; an inverted range is not sent at all. */
export function isValidPeriod(range: DateRange) {
  return !(range.from && range.to && range.from > range.to)
}
