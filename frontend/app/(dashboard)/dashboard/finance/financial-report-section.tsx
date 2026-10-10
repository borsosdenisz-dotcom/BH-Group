"use client"

import { toast } from "sonner"
import { Download } from "lucide-react"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Table,
  TableBody,
  TableCell,
  TableFooter,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { downloadFile } from "@/lib/download-file"
import { useFinancialReport } from "@/hooks/use-financial-reports"
import { formatMoney, formatPercents } from "@/lib/commission"
import type { FinancialReportCurrencyTotals } from "@/lib/api/types"

interface FinancialReportSectionProps {
  propertyId: string
  from: string
  to: string
}

/**
 * Collected money per property and currency - the same figures as the
 * property page and the dashboard (captures by capture date, refunds by
 * refund date, commission on accommodation only at each reservation's
 * snapshotted percent) - plus expenses. Totals are per currency; RON and
 * EUR are never added together.
 */
export function FinancialReportSection({ propertyId, from, to }: FinancialReportSectionProps) {
  const { data, isLoading, isError, refetch } = useFinancialReport({ propertyId: propertyId || undefined, from, to })

  async function handleExport() {
    const params = new URLSearchParams()
    if (propertyId) params.set("propertyId", propertyId)
    if (from) params.set("from", from)
    if (to) params.set("to", to)
    try {
      await downloadFile(`/reports/financial/export?${params.toString()}`, "raport-financiar.csv")
    } catch {
      toast.error("Exportul a eșuat")
    }
  }

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between">
        <CardTitle className="text-base">Raport de profitabilitate</CardTitle>
        <Button type="button" variant="outline" size="sm" className="gap-2" onClick={handleExport}>
          <Download className="size-4" />
          Export CSV
        </Button>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        {isLoading ? (
          <Skeleton className="h-32 w-full" aria-label="Se încarcă raportul" />
        ) : isError || !data ? (
          <Alert variant="destructive">
            <AlertTitle>Raportul nu a putut fi încărcat.</AlertTitle>
            <AlertDescription>
              <Button variant="outline" size="sm" className="mt-2" onClick={() => refetch()}>
                Reîncearcă
              </Button>
            </AlertDescription>
          </Alert>
        ) : data.rows.length === 0 ? (
          <p className="text-sm text-muted-foreground">Nicio proprietate găsită.</p>
        ) : (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Proprietate</TableHead>
                  <TableHead>Proprietar</TableHead>
                  <TableHead className="text-right">Încasat</TableHead>
                  <TableHead className="text-right">Refunduri</TableHead>
                  <TableHead className="text-right">Venit net</TableHead>
                  <TableHead className="text-right">Comision</TableHead>
                  <TableHead className="text-right">Venit BH Stays</TableHead>
                  <TableHead className="text-right">Sumă proprietar</TableHead>
                  <TableHead className="text-right">Cheltuieli</TableHead>
                  <TableHead className="text-right">Profit net</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.rows.map((row) => (
                  <TableRow key={`${row.propertyId}-${row.currency}`}>
                    <TableCell className="font-medium">{row.propertyName}</TableCell>
                    <TableCell className="text-muted-foreground">{row.ownerName ?? "BH Stays"}</TableCell>
                    <TableCell className="text-right">{formatMoney(row.capturedTotal, row.currency)}</TableCell>
                    <TableCell className="text-right">{formatMoney(row.refundedTotal, row.currency)}</TableCell>
                    <TableCell className="text-right">{formatMoney(row.netRevenue, row.currency)}</TableCell>
                    <TableCell className="text-right">{formatPercents(row.commissionPercents)}</TableCell>
                    <TableCell className="text-right">{formatMoney(row.bhStaysRevenue, row.currency)}</TableCell>
                    <TableCell className="text-right">{formatMoney(row.ownerAmount, row.currency)}</TableCell>
                    <TableCell className="text-right">{formatMoney(row.expensesTotal, row.currency)}</TableCell>
                    <TableCell className={`text-right font-medium ${row.netProfit < 0 ? "text-destructive" : ""}`}>
                      {formatMoney(row.netProfit, row.currency)}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
              <TableFooter>
                {data.totals.map((totals) => (
                  <TableRow key={totals.currency}>
                    <TableCell colSpan={2}>Total {totals.currency}</TableCell>
                    <TableCell className="text-right">{formatMoney(totals.revenue.capturedTotal, totals.currency)}</TableCell>
                    <TableCell className="text-right">{formatMoney(totals.revenue.refundedTotal, totals.currency)}</TableCell>
                    <TableCell className="text-right">
                      {formatMoney(totals.revenue.propertiesNetRevenue, totals.currency)}
                    </TableCell>
                    <TableCell />
                    <TableCell className="text-right">{formatMoney(totals.revenue.bhStaysRevenue, totals.currency)}</TableCell>
                    <TableCell className="text-right">{formatMoney(totals.revenue.ownersAmount, totals.currency)}</TableCell>
                    <TableCell className="text-right">{formatMoney(totals.totalExpenses, totals.currency)}</TableCell>
                    <TableCell className={`text-right ${totals.totalNetProfit < 0 ? "text-destructive" : ""}`}>
                      {formatMoney(totals.totalNetProfit, totals.currency)}
                    </TableCell>
                  </TableRow>
                ))}
              </TableFooter>
            </Table>
            <ReconciliationNotes totals={data.totals} />
          </>
        )}
      </CardContent>
    </Card>
  )
}

/** Money that was not commissioned because its reservation has no verifiable snapshot, per currency. */
function ReconciliationNotes({ totals }: { totals: FinancialReportCurrencyTotals[] }) {
  const notes = totals.flatMap(({ currency, revenue }) => {
    const lines: string[] = []
    if (revenue.unallocatedReservationCount > 0) {
      lines.push(
        `${revenue.unallocatedReservationCount} ` +
          `${revenue.unallocatedReservationCount === 1 ? "rezervare" : "rezervări"} fără snapshot verificabil ` +
          `(${formatMoney(revenue.unallocatedNetRevenue, currency)}): incluse în venitul net, fără comision BH Stays.`
      )
    }
    return lines
  })
  if (notes.length === 0) {
    return null
  }
  return (
    <ul role="status" className="flex flex-col gap-1 text-xs text-amber-700 dark:text-amber-400">
      {notes.map((note) => (
        <li key={note}>{note}</li>
      ))}
    </ul>
  )
}
