import { z } from "zod"
import { formatLocalDate } from "@/lib/date"

const RANGE_MESSAGE = "Comisionul trebuie să fie între 0 și 100"

/**
 * BH Stays management commission as typed in a form: empty means "not
 * configured" (undefined), never 0% - those are different things. Mirrors
 * the backend rule: 0.00-100.00 with at most two decimals.
 */
export const commissionPercentSchema = z.preprocess(
  (value) => (value === "" || value === null || value === undefined ? undefined : value),
  z.coerce
    .number({ message: "Introdu un procent valid" })
    .min(0, RANGE_MESSAGE)
    .max(100, RANGE_MESSAGE)
    .refine((value) => /^\d+(\.\d{1,2})?$/.test(String(value)), "Maximum două zecimale")
    .optional()
)

/** Money with exactly two decimals and its own currency - amounts in different currencies are never combined. */
export function formatMoney(value: number, currency: string) {
  return (
    new Intl.NumberFormat("ro-RO", { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value) +
    " " +
    currency
  )
}

export function formatPercent(value: number) {
  return new Intl.NumberFormat("ro-RO", { minimumFractionDigits: 0, maximumFractionDigits: 2 }).format(value) + "%"
}

/** The reservations' snapshotted percents in a period ("20% / 25%"), or a dash when none applied. */
export function formatPercents(values: number[]) {
  return values.length === 0 ? "—" : values.map(formatPercent).join(" / ")
}

export interface DateRange {
  from: string
  to: string
}

/** First and last calendar day of the month containing `today`, as local "YYYY-MM-DD" dates. */
export function currentMonthRange(today: Date = new Date()): DateRange {
  const first = new Date(today.getFullYear(), today.getMonth(), 1)
  const last = new Date(today.getFullYear(), today.getMonth() + 1, 0)
  return { from: formatLocalDate(first), to: formatLocalDate(last) }
}
