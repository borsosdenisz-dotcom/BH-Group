"use client"

import { useState } from "react"
import { toast } from "sonner"
import { Download, Plus, Receipt, Trash2 } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Checkbox } from "@/components/ui/checkbox"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
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
import { DataPagination } from "@/components/ui/data-pagination"
import { downloadFile } from "@/lib/download-file"
import { useCreateExpense, useDeleteExpense, useExpenses, useUploadExpenseReceipt } from "@/hooks/use-expenses"
import { useFinancialReport } from "@/hooks/use-financial-reports"
import { useProperties } from "@/hooks/use-properties"
import { ALL_EXPENSE_CATEGORIES, EXPENSE_CATEGORY_LABELS } from "@/lib/expense-labels"
import { formatLocalDate } from "@/lib/date"
import { OwnerStatementsSection } from "./owner-statements-section"
import type { ExpenseCategory } from "@/lib/api/types"

function formatCurrency(value: number, currency: string) {
  return new Intl.NumberFormat("ro-RO", { maximumFractionDigits: 2 }).format(value) + " " + currency
}

export function FinanceView() {
  const [propertyId, setPropertyId] = useState("")
  const [from, setFrom] = useState("")
  const [to, setTo] = useState("")

  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-8">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Finanțe</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Cheltuieli pe proprietăți și raport de profitabilitate.
        </p>
      </div>

      <div className="flex flex-wrap items-end gap-3">
        <PropertyFilterSelect value={propertyId} onChange={setPropertyId} />
        <div className="flex flex-col gap-1">
          <label className="text-xs text-muted-foreground">De la</label>
          <Input type="date" value={from} onChange={(e) => setFrom(e.target.value)} className="w-40" />
        </div>
        <div className="flex flex-col gap-1">
          <label className="text-xs text-muted-foreground">Până la</label>
          <Input type="date" value={to} onChange={(e) => setTo(e.target.value)} className="w-40" />
        </div>
      </div>

      <FinancialReportSection propertyId={propertyId} from={from} to={to} />
      <ExpensesSection propertyId={propertyId} from={from} to={to} />
      <OwnerStatementsSection />
    </div>
  )
}

function PropertyFilterSelect({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  const { data: properties } = useProperties({ size: 100 })
  const selected = value || "ALL"
  const label =
    selected === "ALL"
      ? "Toate proprietățile"
      : (properties?.content.find((p) => p.id === selected)?.name ?? "Toate proprietățile")

  return (
    <div className="flex flex-col gap-1">
      <label className="text-xs text-muted-foreground">Proprietate</label>
      <Select value={selected} onValueChange={(v) => onChange(v === "ALL" ? "" : (v ?? ""))}>
        <SelectTrigger className="w-56">
          <SelectValue>{() => label}</SelectValue>
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="ALL">Toate proprietățile</SelectItem>
          {properties?.content.map((property) => (
            <SelectItem key={property.id} value={property.id}>
              {property.name}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  )
}

function FinancialReportSection({ propertyId, from, to }: { propertyId: string; from: string; to: string }) {
  const { data, isLoading } = useFinancialReport({ propertyId: propertyId || undefined, from, to })

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
      <CardContent>
        {isLoading || !data ? (
          <Skeleton className="h-32 w-full" />
        ) : data.rows.length === 0 ? (
          <p className="text-sm text-muted-foreground">Nicio proprietate găsită.</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Proprietate</TableHead>
                <TableHead>Proprietar</TableHead>
                <TableHead className="text-right">Venit brut</TableHead>
                <TableHead className="text-right">Comision</TableHead>
                <TableHead className="text-right">Cheltuieli</TableHead>
                <TableHead className="text-right">Profit net</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.rows.map((row) => (
                <TableRow key={row.propertyId}>
                  <TableCell className="font-medium">{row.propertyName}</TableCell>
                  <TableCell className="text-muted-foreground">{row.ownerName ?? "BH Group"}</TableCell>
                  <TableCell className="text-right">{formatCurrency(row.grossRevenue, row.currency)}</TableCell>
                  <TableCell className="text-right">{formatCurrency(row.commissionAmount, row.currency)}</TableCell>
                  <TableCell className="text-right">{formatCurrency(row.expensesTotal, row.currency)}</TableCell>
                  <TableCell
                    className={`text-right font-medium ${row.netProfit < 0 ? "text-destructive" : ""}`}
                  >
                    {formatCurrency(row.netProfit, row.currency)}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
            <TableFooter>
              {data.totals.map((totals) => (
                <TableRow key={totals.currency}>
                  <TableCell colSpan={2}>Total {totals.currency}</TableCell>
                  <TableCell className="text-right">
                    {formatCurrency(totals.totalGrossRevenue, totals.currency)}
                  </TableCell>
                  <TableCell className="text-right">
                    {formatCurrency(totals.totalCommission, totals.currency)}
                  </TableCell>
                  <TableCell className="text-right">
                    {formatCurrency(totals.totalExpenses, totals.currency)}
                  </TableCell>
                  <TableCell
                    className={`text-right ${totals.totalNetProfit < 0 ? "text-destructive" : ""}`}
                  >
                    {formatCurrency(totals.totalNetProfit, totals.currency)}
                  </TableCell>
                </TableRow>
              ))}
            </TableFooter>
          </Table>
        )}
      </CardContent>
    </Card>
  )
}

function ExpensesSection({ propertyId, from, to }: { propertyId: string; from: string; to: string }) {
  const [category, setCategory] = useState<ExpenseCategory | "ALL">("ALL")
  const [page, setPage] = useState(0)
  const [showForm, setShowForm] = useState(false)

  const { data, isLoading } = useExpenses({
    propertyId: propertyId || undefined,
    category: category === "ALL" ? undefined : category,
    from,
    to,
    page,
  })
  const deleteExpense = useDeleteExpense()
  const uploadReceipt = useUploadExpenseReceipt()

  async function handleExport() {
    const params = new URLSearchParams()
    if (propertyId) params.set("propertyId", propertyId)
    if (category !== "ALL") params.set("category", category)
    if (from) params.set("from", from)
    if (to) params.set("to", to)
    try {
      await downloadFile(`/expenses/export?${params.toString()}`, "cheltuieli.csv")
    } catch {
      toast.error("Exportul a eșuat")
    }
  }

  return (
    <Card>
      <CardHeader className="flex flex-row flex-wrap items-center justify-between gap-3">
        <CardTitle className="text-base">Cheltuieli</CardTitle>
        <div className="flex gap-2">
          <Button type="button" variant="outline" size="sm" className="gap-2" onClick={handleExport}>
            <Download className="size-4" />
            Export CSV
          </Button>
          <Button type="button" size="sm" className="gap-2" onClick={() => setShowForm((v) => !v)}>
            <Plus className="size-4" />
            Adaugă cheltuială
          </Button>
        </div>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {showForm && <CreateExpenseForm onCreated={() => setShowForm(false)} />}

        <Select
          value={category}
          onValueChange={(v) => {
            setCategory((v ?? "ALL") as ExpenseCategory | "ALL")
            setPage(0)
          }}
        >
          <SelectTrigger className="w-56">
            <SelectValue>{() => (category === "ALL" ? "Toate categoriile" : EXPENSE_CATEGORY_LABELS[category])}</SelectValue>
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">Toate categoriile</SelectItem>
            {ALL_EXPENSE_CATEGORIES.map((c) => (
              <SelectItem key={c} value={c}>
                {EXPENSE_CATEGORY_LABELS[c]}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        {isLoading ? (
          <div className="flex flex-col gap-2">
            {Array.from({ length: 4 }).map((_, i) => (
              <Skeleton key={i} className="h-14 w-full" />
            ))}
          </div>
        ) : !data || data.content.length === 0 ? (
          <div className="flex flex-col items-center justify-center rounded-lg border border-dashed py-12 text-center">
            <p className="text-sm text-muted-foreground">Nicio cheltuială înregistrată.</p>
          </div>
        ) : (
          <>
            <div className="flex flex-col divide-y divide-border/60 rounded-lg border">
              {data.content.map((expense) => (
                <div key={expense.id} className="flex flex-wrap items-center justify-between gap-3 p-4 text-sm">
                  <div>
                    <p className="font-medium">
                      {formatCurrency(expense.amount, expense.currency)}
                      <span className="ml-2 font-normal text-muted-foreground">
                        {EXPENSE_CATEGORY_LABELS[expense.category]}
                      </span>
                    </p>
                    <p className="text-xs text-muted-foreground">
                      {expense.propertyName} · {expense.expenseDate}
                      {expense.vendor ? ` · ${expense.vendor}` : ""}
                    </p>
                  </div>
                  {expense.chargeToOwner && <Badge variant="secondary">Facturat proprietarului</Badge>}
                  <div className="flex items-center gap-1">
                    <label className="cursor-pointer">
                      <input
                        type="file"
                        accept="application/pdf,image/*"
                        className="hidden"
                        onChange={(e) => {
                          const file = e.target.files?.[0]
                          if (file) uploadReceipt.mutate({ id: expense.id, file })
                          e.target.value = ""
                        }}
                      />
                      <span className="inline-flex size-8 items-center justify-center rounded-md text-muted-foreground hover:bg-accent hover:text-accent-foreground">
                        <Receipt className="size-4" />
                      </span>
                    </label>
                    {expense.receiptUrl && (
                      <button
                        type="button"
                        onClick={() =>
                          downloadFile(`/expenses/${expense.id}/receipt/download`, "bon")
                            .catch(() => toast.error("Descărcarea bonului a eșuat"))
                        }
                        className="text-xs text-primary underline"
                      >
                        Vezi bon
                      </button>
                    )}
                    <Button
                      type="button"
                      size="icon-sm"
                      variant="ghost"
                      aria-label="Șterge"
                      onClick={() => deleteExpense.mutate(expense.id)}
                    >
                      <Trash2 className="size-4" />
                    </Button>
                  </div>
                </div>
              ))}
            </div>
            <DataPagination page={data.page} totalPages={data.totalPages} onPageChange={setPage} />
          </>
        )}
      </CardContent>
    </Card>
  )
}

function CreateExpenseForm({ onCreated }: { onCreated: () => void }) {
  const { data: properties } = useProperties({ size: 100 })
  const createExpense = useCreateExpense()

  const [propertyId, setPropertyId] = useState("")
  const [category, setCategory] = useState<ExpenseCategory>("OTHER")
  const [amount, setAmount] = useState("")
  const [vendor, setVendor] = useState("")
  const [expenseDate, setExpenseDate] = useState(() => formatLocalDate(new Date()))
  const [notes, setNotes] = useState("")
  const [chargeToOwner, setChargeToOwner] = useState(false)

  function handleSubmit() {
    const parsedAmount = Number(amount)
    if (!propertyId || !parsedAmount || parsedAmount <= 0 || !expenseDate) return
    createExpense.mutate(
      {
        propertyId,
        category,
        amount: parsedAmount,
        vendor: vendor || undefined,
        expenseDate,
        notes: notes || undefined,
        chargeToOwner,
      },
      {
        onSuccess: () => {
          setAmount("")
          setVendor("")
          setNotes("")
          setChargeToOwner(false)
          onCreated()
        },
      }
    )
  }

  return (
    <div className="flex flex-col gap-3 rounded-lg border p-4">
      <div className="grid gap-3 sm:grid-cols-3">
        <Select value={propertyId} onValueChange={(v) => setPropertyId(v ?? "")}>
          <SelectTrigger>
            <SelectValue placeholder="Proprietate" />
          </SelectTrigger>
          <SelectContent>
            {properties?.content.map((property) => (
              <SelectItem key={property.id} value={property.id}>
                {property.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select value={category} onValueChange={(v) => setCategory(v as ExpenseCategory)}>
          <SelectTrigger>
            <SelectValue>{() => EXPENSE_CATEGORY_LABELS[category]}</SelectValue>
          </SelectTrigger>
          <SelectContent>
            {ALL_EXPENSE_CATEGORIES.map((c) => (
              <SelectItem key={c} value={c}>
                {EXPENSE_CATEGORY_LABELS[c]}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Input
          type="number"
          min={0.01}
          step="0.01"
          placeholder="Sumă (RON)"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
        />
        <Input placeholder="Furnizor (opțional)" value={vendor} onChange={(e) => setVendor(e.target.value)} />
        <Input type="date" value={expenseDate} onChange={(e) => setExpenseDate(e.target.value)} />
        <Input placeholder="Notă (opțional)" value={notes} onChange={(e) => setNotes(e.target.value)} />
      </div>
      <label className="flex w-fit items-center gap-2 text-sm">
        <Checkbox checked={chargeToOwner} onCheckedChange={(checked) => setChargeToOwner(checked === true)} />
        Facturează proprietarului (se scade din venitul net al proprietarului)
      </label>
      <Button
        className="self-start"
        disabled={!propertyId || !amount || createExpense.isPending}
        onClick={handleSubmit}
      >
        Salvează cheltuiala
      </Button>
    </div>
  )
}
