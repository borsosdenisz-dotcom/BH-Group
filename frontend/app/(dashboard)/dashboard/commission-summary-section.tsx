"use client"

import { useState } from "react"
import Link from "next/link"
import { AlertTriangle, Building2, HandCoins, Landmark } from "lucide-react"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { ReportPeriodFilter, isValidPeriod } from "@/components/finance/report-period-filter"
import { useCommissionSummary } from "@/hooks/use-financial-reports"
import { currentMonthRange, formatMoney, type DateRange } from "@/lib/commission"
import type { CommissionSummaryCurrencyTotals, UnconfiguredProperty } from "@/lib/api/types"

/** How many unconfigured properties are linked by name before the list is summarised. */
const UNCONFIGURED_LINK_LIMIT = 5

/**
 * The money collected for the owners' properties is not BH Stays's revenue:
 * BH Stays only keeps its commission. The three figures are shown apart,
 * one row per currency, and never summed across currencies.
 */
export function CommissionSummarySection() {
  const [period, setPeriod] = useState<DateRange>(() => currentMonthRange())
  const validPeriod = isValidPeriod(period)
  const { data: summary, isLoading, isError, refetch } = useCommissionSummary(
    { from: period.from || undefined, to: period.to || undefined },
    validPeriod
  )

  return (
    <section aria-labelledby="commission-summary-title" className="flex flex-col gap-4">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h2 id="commission-summary-title" className="text-lg font-semibold tracking-tight">
            Încasări și comisioane
          </h2>
          <p className="text-sm text-muted-foreground">
            Doar bani încasați, după data plății; refundurile, după data refundului.
          </p>
        </div>
        <ReportPeriodFilter value={period} onChange={setPeriod} />
      </div>

      {!validPeriod ? null : isLoading ? (
        <div className="grid gap-4 sm:grid-cols-3" aria-label="Se încarcă încasările">
          <Skeleton className="h-24" />
          <Skeleton className="h-24" />
          <Skeleton className="h-24" />
        </div>
      ) : isError || !summary ? (
        <Alert variant="destructive">
          <AlertTitle>Încasările nu au putut fi încărcate.</AlertTitle>
          <AlertDescription>
            <Button variant="outline" size="sm" className="mt-2" onClick={() => refetch()}>
              Reîncearcă
            </Button>
          </AlertDescription>
        </Alert>
      ) : (
        <>
          {summary.totals.length === 0 ? (
            <p className="text-sm text-muted-foreground">Nicio încasare în perioada selectată.</p>
          ) : (
            summary.totals.map((totals) => <CurrencyTotals key={totals.currency} totals={totals} />)
          )}
          <UnconfiguredWarning properties={summary.unconfiguredProperties} />
        </>
      )}
    </section>
  )
}

function CurrencyTotals({ totals }: { totals: CommissionSummaryCurrencyTotals }) {
  const { currency } = totals
  return (
    <div role="group" aria-label={`Totaluri ${currency}`} className="flex flex-col gap-2">
      <div className="grid gap-4 sm:grid-cols-3">
        <Card>
          <CardHeader>
            <CardDescription className="flex items-center gap-1.5">
              <Building2 className="size-3.5" aria-hidden /> Venit net proprietăți
            </CardDescription>
            <CardTitle className="text-2xl">{formatMoney(totals.propertiesNetRevenue, currency)}</CardTitle>
          </CardHeader>
        </Card>
        <Card className="border-primary/20 bg-primary/5">
          <CardHeader>
            <CardDescription className="flex items-center gap-1.5">
              <Landmark className="size-3.5" aria-hidden /> Venit BH Stays din comisioane
            </CardDescription>
            <CardTitle className="text-2xl">{formatMoney(totals.bhStaysRevenue, currency)}</CardTitle>
          </CardHeader>
        </Card>
        <Card>
          <CardHeader>
            <CardDescription className="flex items-center gap-1.5">
              <HandCoins className="size-3.5" aria-hidden /> Sumă datorată proprietarilor
            </CardDescription>
            <CardTitle className="text-2xl">{formatMoney(totals.ownersAmount, currency)}</CardTitle>
          </CardHeader>
        </Card>
      </div>
      <p className="text-xs text-muted-foreground">
        {totals.includedPropertyCount}{" "}
        {totals.includedPropertyCount === 1 ? "proprietate inclusă" : "proprietăți incluse"} în {currency}.
        {totals.unallocatedReservationCount > 0 &&
          ` ${totals.unallocatedReservationCount} ` +
            `${totals.unallocatedReservationCount === 1 ? "rezervare" : "rezervări"} fără snapshot verificabil ` +
            `(${formatMoney(totals.unallocatedNetRevenue, currency)}): incluse în venitul net, fără comision BH Stays.`}
      </p>
    </div>
  )
}

function UnconfiguredWarning({ properties }: { properties: UnconfiguredProperty[] }) {
  if (properties.length === 0) {
    return null
  }
  const shown = properties.slice(0, UNCONFIGURED_LINK_LIMIT)
  const remaining = properties.length - shown.length

  return (
    <Alert className="border-amber-300/60 bg-amber-50 dark:bg-amber-950/20" role="status">
      <AlertTriangle />
      <AlertTitle>
        {properties.length === 1
          ? "1 proprietate are comisionul neconfigurat"
          : `${properties.length} proprietăți au comisionul neconfigurat`}
      </AlertTitle>
      <AlertDescription>
        <p>Rezervările noi ale acestor proprietăți nu vor avea comision BH Stays până nu setezi procentul.</p>
        <ul className="mt-1 flex flex-wrap gap-x-3 gap-y-1">
          {shown.map((property) => (
            <li key={property.propertyId}>
              <Link href={`/dashboard/properties/${property.propertyId}#comision-administrare`}>
                {property.propertyName}
              </Link>
            </li>
          ))}
          {remaining > 0 && (
            <li>
              <Link href="/dashboard/properties">și încă {remaining}</Link>
            </li>
          )}
        </ul>
      </AlertDescription>
    </Alert>
  )
}
