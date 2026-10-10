"use client"

import { useId, useState, type FormEvent } from "react"
import { AlertTriangle } from "lucide-react"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"
import { ReportPeriodFilter, isValidPeriod } from "@/components/finance/report-period-filter"
import { usePropertyCommissionReport } from "@/hooks/use-financial-reports"
import { useUpdateManagementCommission } from "@/hooks/use-properties"
import {
  commissionPercentSchema,
  currentMonthRange,
  formatMoney,
  formatPercents,
  type DateRange,
} from "@/lib/commission"
import type { PropertyCommissionCurrency } from "@/lib/api/types"

interface PropertyFinancialSectionProps {
  propertyId: string
  commissionPercent: number | null
  canManage: boolean
}

/**
 * Money collected for this property in the selected period, and how it
 * splits between the owner and BH Stays. One block per currency - amounts
 * in different currencies are never added together.
 */
export function PropertyFinancialSection({ propertyId, commissionPercent, canManage }: PropertyFinancialSectionProps) {
  const [period, setPeriod] = useState<DateRange>(() => currentMonthRange())
  const validPeriod = isValidPeriod(period)
  const { data: report, isLoading, isError, refetch } = usePropertyCommissionReport(
    propertyId,
    { from: period.from || undefined, to: period.to || undefined },
    validPeriod
  )

  return (
    <div className="flex flex-col gap-4">
      <ReportPeriodFilter value={period} onChange={setPeriod} />

      {commissionPercent == null && (
        <Alert className="border-amber-300/60 bg-amber-50 dark:bg-amber-950/20" role="status">
          <AlertTriangle />
          <AlertTitle>Comision neconfigurat</AlertTitle>
          <AlertDescription>
            Rezervările create cât timp procentul nu este setat nu vor avea comision BH Stays. Setează-l înainte
            de a primi rezervări noi.
          </AlertDescription>
        </Alert>
      )}

      {!validPeriod ? null : isLoading ? (
        <Skeleton className="h-40 w-full" aria-label="Se încarcă raportul financiar" />
      ) : isError || !report ? (
        <Alert variant="destructive">
          <AlertTitle>Raportul financiar nu a putut fi încărcat.</AlertTitle>
          <AlertDescription>
            <Button variant="outline" size="sm" className="mt-2" onClick={() => refetch()}>
              Reîncearcă
            </Button>
          </AlertDescription>
        </Alert>
      ) : report.currencies.length === 0 ? (
        <p className="text-sm text-muted-foreground">Nicio încasare în perioada selectată.</p>
      ) : (
        <div className="flex flex-col gap-4">
          {report.currencies.map((line) => (
            <CurrencyBreakdown key={line.currency} line={line} />
          ))}
        </div>
      )}

      {canManage && <CommissionEditor propertyId={propertyId} commissionPercent={commissionPercent} />}
    </div>
  )
}

function CurrencyBreakdown({ line }: { line: PropertyCommissionCurrency }) {
  const { currency } = line
  return (
    <section aria-label={`Încasări în ${currency}`} className="rounded-lg border border-border/60 p-4">
      <h3 className="mb-3 text-sm font-semibold">{currency}</h3>
      <dl className="grid gap-3 text-sm sm:grid-cols-3">
        <Figure label="Venit brut încasat" value={formatMoney(line.capturedTotal, currency)} />
        <Figure label="Refunduri" value={formatMoney(line.refundedTotal, currency)} />
        <Figure label="Venit net" value={formatMoney(line.netRevenue, currency)} emphasis />
        <Figure label="Procent BH Stays (rezervări)" value={formatPercents(line.commissionPercents)} />
        <Figure label="Venit BH Stays" value={formatMoney(line.bhStaysRevenue, currency)} />
        <Figure label="Sumă proprietar" value={formatMoney(line.ownerAmount, currency)} emphasis />
      </dl>
      <p className="mt-3 text-xs text-muted-foreground">
        Comisionul se aplică doar cazării ({formatMoney(line.commissionableBase, currency)} după refunduri), la
        procentul valabil când a fost creată fiecare rezervare; curățenia, oaspeții suplimentari, late checkout,
        taxele și addon-urile rămân integral proprietarului. Încasările intră în perioadă după data plății, iar
        refundurile după data refundului.
      </p>
      {line.unallocatedReservationCount > 0 && (
        <p className="mt-1 text-xs text-amber-700 dark:text-amber-400">
          {line.unallocatedReservationCount}{" "}
          {line.unallocatedReservationCount === 1 ? "rezervare" : "rezervări"} fără snapshot verificabil (
          {formatMoney(line.unallocatedNetRevenue, currency)}): incluse în venitul net, fără comision BH Stays.
        </p>
      )}
    </section>
  )
}

function Figure({ label, value, emphasis }: { label: string; value: string; emphasis?: boolean }) {
  return (
    <div>
      <dt className="text-muted-foreground">{label}</dt>
      <dd className={emphasis ? "text-base font-semibold" : "font-medium"}>{value}</dd>
    </div>
  )
}

function CommissionEditor({ propertyId, commissionPercent }: { propertyId: string; commissionPercent: number | null }) {
  const id = useId()
  const [value, setValue] = useState(commissionPercent != null ? String(commissionPercent) : "")
  const [error, setError] = useState<string | null>(null)
  const updateCommission = useUpdateManagementCommission(propertyId)

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const parsed = commissionPercentSchema.safeParse(value.trim())
    if (!parsed.success) {
      setError(parsed.error.issues[0]?.message ?? "Procent invalid")
      return
    }
    setError(null)
    updateCommission.mutate(parsed.data ?? null)
  }

  return (
    <form
      id="comision-administrare"
      noValidate
      onSubmit={handleSubmit}
      className="flex flex-col gap-1 border-t border-border/60 pt-4">
      <label htmlFor={`${id}-commission`} className="text-sm font-medium">
        Comision administrare BH Stays (%)
      </label>
      <div className="flex flex-wrap items-center gap-2">
        <Input
          id={`${id}-commission`}
          type="number"
          inputMode="decimal"
          min={0}
          max={100}
          step="0.01"
          value={value}
          onChange={(e) => setValue(e.target.value)}
          aria-invalid={error ? true : undefined}
          aria-describedby={`${id}-commission-help${error ? ` ${id}-commission-error` : ""}`}
          className="w-32"
        />
        <Button type="submit" size="sm" disabled={updateCommission.isPending}>
          {updateCommission.isPending ? "Se salvează..." : "Salvează"}
        </Button>
      </div>
      <p id={`${id}-commission-help`} className="text-xs text-muted-foreground">
        Între 0 și 100, maximum două zecimale. Lasă gol pentru „neconfigurat”. Se aplică rezervărilor create de
        acum înainte; rezervările existente își păstrează procentul.
      </p>
      {error && (
        <p id={`${id}-commission-error`} role="alert" className="text-xs text-destructive">
          {error}
        </p>
      )}
    </form>
  )
}
