"use client"

import { formatMoney, formatPercents } from "@/lib/commission"
import type { OwnerRevenueLine } from "@/lib/api/types"

interface OwnerRevenueLinesProps {
  lines: OwnerRevenueLine[]
  /** Hide the expenses/payout rows (e.g. on a single property, where expenses are settled on the statement). */
  showPayout?: boolean
}

/**
 * An owner's collected money, one block per currency, on the statement
 * formula - so the owner portal shows exactly what a statement would.
 */
export function OwnerRevenueLines({ lines, showPayout = true }: OwnerRevenueLinesProps) {
  if (lines.length === 0) {
    return <p className="text-sm text-muted-foreground">Nicio încasare înregistrată încă.</p>
  }
  return (
    <div className="flex flex-col gap-4">
      {lines.map((line) => (
        <CurrencyBlock key={line.currency} line={line} showPayout={showPayout} />
      ))}
    </div>
  )
}

function Row({ label, value, strong }: { label: string; value: string; strong?: boolean }) {
  return (
    <div className={`flex items-center justify-between ${strong ? "font-medium" : ""}`}>
      <dt className={strong ? "" : "text-muted-foreground"}>{label}</dt>
      <dd>{value}</dd>
    </div>
  )
}

function CurrencyBlock({ line, showPayout }: { line: OwnerRevenueLine; showPayout: boolean }) {
  const { currency } = line
  const money = (value: number) => formatMoney(value, currency)
  const commissionLabel =
    line.commissionPercents.length > 0
      ? `Comision BH Stays (${formatPercents(line.commissionPercents)})`
      : "Comision BH Stays"

  return (
    <section aria-label={`Încasări ${currency}`} className="rounded-lg border border-border/60 p-4">
      <h3 className="mb-2 text-sm font-semibold">{currency}</h3>
      <dl className="grid gap-2 text-sm">
        <Row label="Venit net încasat" value={money(line.netRevenue)} strong />
        <Row label={commissionLabel} value={money(-line.bhStaysCommission)} />
        <Row label="Sumă proprietar" value={money(line.ownerAmount)} strong />
        {showPayout && <Row label="Cheltuieli facturate" value={money(-line.expensesTotal)} />}
        {showPayout && <Row label="Net de plată" value={money(line.netPayout)} strong />}
      </dl>
      {line.unallocatedReservationCount > 0 && (
        <p role="status" className="mt-1 text-xs text-muted-foreground">
          {line.unallocatedReservationCount}{" "}
          {line.unallocatedReservationCount === 1 ? "rezervare" : "rezervări"} fără snapshot verificabil (
          {money(line.unallocatedNetRevenue)}): fără comision BH Stays.
        </p>
      )}
    </section>
  )
}
