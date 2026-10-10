"use client"

import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { formatMoney, formatPercent } from "@/lib/commission"
import type { OwnerStatementResponse } from "@/lib/api/types"

/**
 * The money side of a statement - shared by the admin dialog and the
 * owner's statement page so both always show the same figures. A
 * statement covers a single currency.
 */
export function OwnerStatementBreakdown({ statement }: { statement: OwnerStatementResponse }) {
  return statement.calculationMethod === "CAPTURED_ACCOMMODATION"
    ? <CapturedAccommodationBreakdown statement={statement} />
    : <LegacyBreakdown statement={statement} />
}

function SummaryRow({ label, value, strong }: { label: string; value: string; strong?: boolean }) {
  return (
    <div className={`flex items-center justify-between ${strong ? "text-base font-medium" : ""}`}>
      <dt className={strong ? "" : "text-muted-foreground"}>{label}</dt>
      <dd>{value}</dd>
    </div>
  )
}

function CapturedAccommodationBreakdown({ statement }: { statement: OwnerStatementResponse }) {
  const { currency } = statement
  const money = (value: number | null) => (value != null ? formatMoney(value, currency) : "—")

  return (
    <div className="flex flex-col gap-4">
      <dl className="grid gap-2 text-sm" aria-label={`Rezumat decont ${currency}`}>
        <SummaryRow label="Încasat" value={money(statement.capturedTotal)} />
        <SummaryRow
          label="Refunduri"
          value={statement.refundedTotal != null ? formatMoney(-statement.refundedTotal, currency) : "—"}
        />
        <SummaryRow label="Venit net încasat" value={money(statement.grossRevenue)} strong />
        <SummaryRow label="Bază comisionabilă (cazare)" value={money(statement.commissionableBase)} />
        <SummaryRow label="Comision BH Stays" value={formatMoney(-statement.commissionAmount, currency)} />
        <SummaryRow label="Sumă proprietar" value={money(statement.ownerAmount)} strong />
        <SummaryRow label="Cheltuieli facturate" value={formatMoney(-statement.expensesTotal, currency)} />
        <SummaryRow label="Net de plată" value={money(statement.netPayout)} strong />
      </dl>
      <p className="text-xs text-muted-foreground">
        Comisionul se aplică doar cazării încasate, după refunduri. Taxa de curățenie, taxa pentru oaspeți
        suplimentari, late checkout, taxele și addon-urile nu se comisionează.
      </p>
      {statement.unallocatedReservationCount != null && statement.unallocatedReservationCount > 0 && (
        <p role="status" className="text-xs text-amber-700 dark:text-amber-400">
          {statement.unallocatedReservationCount}{" "}
          {statement.unallocatedReservationCount === 1 ? "rezervare" : "rezervări"} fără snapshot verificabil (
          {money(statement.unallocatedNetRevenue)}): incluse în venitul net, fără comision BH Stays.
        </p>
      )}

      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Proprietate</TableHead>
            <TableHead className="text-right">Venit net</TableHead>
            <TableHead className="text-right">Comision</TableHead>
            <TableHead className="text-right">Comision BH Stays</TableHead>
            <TableHead className="text-right">Sumă proprietar</TableHead>
            <TableHead className="text-right">Cheltuieli</TableHead>
            <TableHead className="text-right">Net</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {statement.lines.map((line, i) => (
            <TableRow key={line.propertyId ?? i}>
              <TableCell>{line.propertyName}</TableCell>
              <TableCell className="text-right">{formatMoney(line.grossRevenue, currency)}</TableCell>
              <TableCell className="text-right">
                {line.commissionPercent != null ? formatPercent(line.commissionPercent) : "—"}
              </TableCell>
              <TableCell className="text-right">{formatMoney(line.commissionAmount, currency)}</TableCell>
              <TableCell className="text-right">{money(line.ownerAmount)}</TableCell>
              <TableCell className="text-right">{formatMoney(line.expensesTotal, currency)}</TableCell>
              <TableCell className="text-right font-medium">{formatMoney(line.netAmount, currency)}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  )
}

/** Issued before the shared formula: shown exactly as issued, clearly marked. */
function LegacyBreakdown({ statement }: { statement: OwnerStatementResponse }) {
  const { currency } = statement
  return (
    <div className="flex flex-col gap-4">
      <p role="note" className="rounded-md bg-muted/40 p-3 text-xs text-muted-foreground">
        Decont emis cu metoda veche de calcul: comisionul a fost aplicat pe tot venitul net, nu doar pe cazare.
        Sumele sunt afișate exact cum au fost emise.
      </p>
      <dl className="grid gap-2 text-sm" aria-label={`Rezumat decont ${currency}`}>
        <SummaryRow label="Venit net încasat" value={formatMoney(statement.grossRevenue, currency)} strong />
        <SummaryRow label="Comision BH Stays" value={formatMoney(-statement.commissionAmount, currency)} />
        <SummaryRow label="Cheltuieli facturate" value={formatMoney(-statement.expensesTotal, currency)} />
        <SummaryRow label="Net de plată" value={formatMoney(statement.netPayout, currency)} strong />
      </dl>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Proprietate</TableHead>
            <TableHead className="text-right">Venit net</TableHead>
            <TableHead className="text-right">Comision</TableHead>
            <TableHead className="text-right">Cheltuieli</TableHead>
            <TableHead className="text-right">Net</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {statement.lines.map((line, i) => (
            <TableRow key={line.propertyId ?? i}>
              <TableCell>{line.propertyName}</TableCell>
              <TableCell className="text-right">{formatMoney(line.grossRevenue, currency)}</TableCell>
              <TableCell className="text-right">{formatMoney(line.commissionAmount, currency)}</TableCell>
              <TableCell className="text-right">{formatMoney(line.expensesTotal, currency)}</TableCell>
              <TableCell className="text-right font-medium">{formatMoney(line.netAmount, currency)}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  )
}
