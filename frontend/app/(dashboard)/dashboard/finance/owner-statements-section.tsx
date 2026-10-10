"use client"

import { useState } from "react"
import { toast } from "sonner"
import { Download, FileText } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
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
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { DataPagination } from "@/components/ui/data-pagination"
import { downloadFile } from "@/lib/download-file"
import { useUsers } from "@/hooks/use-users"
import {
  useGenerateOwnerStatement,
  useMarkStatementPaid,
  useOwnerStatement,
  useOwnerStatements,
} from "@/hooks/use-owner-statements"
import { OwnerStatementBreakdown } from "@/components/finance/owner-statement-breakdown"
import type { OwnerStatementStatus } from "@/lib/api/types"

function formatCurrency(value: number, currency: string) {
  return new Intl.NumberFormat("ro-RO", { maximumFractionDigits: 2 }).format(value) + " " + currency
}

const STATUS_LABELS: Record<OwnerStatementStatus, string> = {
  ISSUED: "Emis",
  PAID: "Plătit",
}

export function OwnerStatementsSection() {
  const [ownerId, setOwnerId] = useState("")
  const [status, setStatus] = useState<OwnerStatementStatus | "ALL">("ALL")
  const [page, setPage] = useState(0)
  const [showGenerateForm, setShowGenerateForm] = useState(false)
  const [detailId, setDetailId] = useState<string | null>(null)

  const { data, isLoading } = useOwnerStatements({
    ownerId: ownerId || undefined,
    status: status === "ALL" ? undefined : status,
    page,
  })
  const markPaid = useMarkStatementPaid()

  async function handleExport() {
    const params = new URLSearchParams()
    if (ownerId) params.set("ownerId", ownerId)
    if (status !== "ALL") params.set("status", status)
    try {
      await downloadFile(`/owner-statements/export?${params.toString()}`, "deconturi-proprietari.csv")
    } catch {
      toast.error("Exportul a eșuat")
    }
  }

  return (
    <Card>
      <CardHeader className="flex flex-row flex-wrap items-center justify-between gap-3">
        <CardTitle className="text-base">Deconturi proprietari</CardTitle>
        <div className="flex gap-2">
          <Button type="button" variant="outline" size="sm" className="gap-2" onClick={handleExport}>
            <Download className="size-4" />
            Export CSV
          </Button>
          <Button type="button" size="sm" className="gap-2" onClick={() => setShowGenerateForm((v) => !v)}>
            <FileText className="size-4" />
            Generează decont
          </Button>
        </div>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {showGenerateForm && (
          <GenerateStatementForm onGenerated={() => setShowGenerateForm(false)} />
        )}

        <div className="flex flex-wrap items-end gap-3">
          <OwnerFilterSelect value={ownerId} onChange={(v) => { setOwnerId(v); setPage(0) }} />
          <div className="flex flex-col gap-1">
            <label className="text-xs text-muted-foreground">Status</label>
            <Select
              value={status}
              onValueChange={(v) => { setStatus((v ?? "ALL") as OwnerStatementStatus | "ALL"); setPage(0) }}
            >
              <SelectTrigger className="w-40">
                <SelectValue>{() => (status === "ALL" ? "Toate" : STATUS_LABELS[status])}</SelectValue>
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">Toate</SelectItem>
                <SelectItem value="ISSUED">Emis</SelectItem>
                <SelectItem value="PAID">Plătit</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>

        {isLoading ? (
          <div className="flex flex-col gap-2">
            {Array.from({ length: 4 }).map((_, i) => (
              <Skeleton key={i} className="h-14 w-full" />
            ))}
          </div>
        ) : !data || data.content.length === 0 ? (
          <div className="flex flex-col items-center justify-center rounded-lg border border-dashed py-12 text-center">
            <p className="text-sm text-muted-foreground">Niciun decont generat încă.</p>
          </div>
        ) : (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Proprietar</TableHead>
                  <TableHead>Perioadă</TableHead>
                  <TableHead className="text-right">Net de plată</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead />
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.content.map((statement) => (
                  <TableRow key={statement.id}>
                    <TableCell className="font-medium">{statement.ownerName}</TableCell>
                    <TableCell className="text-muted-foreground">
                      {statement.periodStart} → {statement.periodEnd}
                    </TableCell>
                    <TableCell className="text-right font-medium">
                      {formatCurrency(statement.netPayout, statement.currency)}
                    </TableCell>
                    <TableCell>
                      <Badge variant={statement.status === "PAID" ? "secondary" : "outline"}>
                        {STATUS_LABELS[statement.status]}
                      </Badge>
                    </TableCell>
                    <TableCell className="flex flex-wrap justify-end gap-2">
                      <Button type="button" variant="outline" size="sm" onClick={() => setDetailId(statement.id)}>
                        Detalii
                      </Button>
                      {statement.status === "ISSUED" && (
                        <Button
                          type="button"
                          size="sm"
                          disabled={markPaid.isPending}
                          onClick={() => markPaid.mutate({ id: statement.id })}
                        >
                          Marchează plătit
                        </Button>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <DataPagination page={data.page} totalPages={data.totalPages} onPageChange={setPage} />
          </>
        )}
      </CardContent>

      <StatementDetailDialog id={detailId} onClose={() => setDetailId(null)} />
    </Card>
  )
}

function OwnerFilterSelect({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  const { data: owners } = useUsers({ role: "OWNER", size: 100 })
  const selected = value || "ALL"
  const label =
    selected === "ALL"
      ? "Toți proprietarii"
      : (owners?.content.find((o) => o.id === selected)
          ? `${owners.content.find((o) => o.id === selected)?.firstName} ${owners.content.find((o) => o.id === selected)?.lastName}`
          : "Toți proprietarii")

  return (
    <div className="flex flex-col gap-1">
      <label className="text-xs text-muted-foreground">Proprietar</label>
      <Select value={selected} onValueChange={(v) => onChange(v === "ALL" ? "" : (v ?? ""))}>
        <SelectTrigger className="w-56">
          <SelectValue>{() => label}</SelectValue>
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="ALL">Toți proprietarii</SelectItem>
          {owners?.content.map((owner) => (
            <SelectItem key={owner.id} value={owner.id}>
              {owner.firstName} {owner.lastName}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  )
}

function GenerateStatementForm({ onGenerated }: { onGenerated: () => void }) {
  const { data: owners } = useUsers({ role: "OWNER", size: 100 })
  const generate = useGenerateOwnerStatement()

  const [ownerId, setOwnerId] = useState("")
  const [periodStart, setPeriodStart] = useState("")
  const [periodEnd, setPeriodEnd] = useState("")

  function handleSubmit() {
    if (!ownerId || !periodStart || !periodEnd) return
    generate.mutate(
      { ownerId, periodStart, periodEnd },
      { onSuccess: onGenerated }
    )
  }

  return (
    <div className="flex flex-col gap-3 rounded-lg border p-4">
      <div className="grid gap-3 sm:grid-cols-3">
        <Select value={ownerId} onValueChange={(v) => setOwnerId(v ?? "")}>
          <SelectTrigger>
            <SelectValue placeholder="Proprietar" />
          </SelectTrigger>
          <SelectContent>
            {owners?.content.map((owner) => (
              <SelectItem key={owner.id} value={owner.id}>
                {owner.firstName} {owner.lastName}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <div className="flex flex-col gap-1">
          <label className="text-xs text-muted-foreground">Început perioadă</label>
          <Input type="date" value={periodStart} onChange={(e) => setPeriodStart(e.target.value)} />
        </div>
        <div className="flex flex-col gap-1">
          <label className="text-xs text-muted-foreground">Sfârșit perioadă</label>
          <Input type="date" value={periodEnd} onChange={(e) => setPeriodEnd(e.target.value)} />
        </div>
      </div>
      <p className="text-xs text-muted-foreground">
        Se generează câte un decont separat pentru fiecare monedă în care proprietarul a avut activitate
        în perioada selectată. Reîncercarea aceleiași perioade pentru același proprietar este respinsă.
      </p>
      <Button
        className="self-start"
        disabled={!ownerId || !periodStart || !periodEnd || generate.isPending}
        onClick={handleSubmit}
      >
        Generează
      </Button>
    </div>
  )
}

function StatementDetailDialog({ id, onClose }: { id: string | null; onClose: () => void }) {
  const { data: statement, isLoading } = useOwnerStatement(id ?? undefined)

  return (
    <Dialog open={!!id} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>Detalii decont</DialogTitle>
        </DialogHeader>
        {isLoading || !statement ? (
          <Skeleton className="h-48 w-full" />
        ) : (
          <div className="flex flex-col gap-4">
            <div className="grid grid-cols-2 gap-2 text-sm sm:grid-cols-4">
              <div>
                <p className="text-xs text-muted-foreground">Proprietar</p>
                <p className="font-medium">{statement.ownerName}</p>
              </div>
              <div>
                <p className="text-xs text-muted-foreground">Perioadă</p>
                <p className="font-medium">{statement.periodStart} → {statement.periodEnd}</p>
              </div>
              <div>
                <p className="text-xs text-muted-foreground">Status</p>
                <p className="font-medium">{STATUS_LABELS[statement.status]}</p>
              </div>
              <div>
                <p className="text-xs text-muted-foreground">Net de plată</p>
                <p className="font-medium">{formatCurrency(statement.netPayout, statement.currency)}</p>
              </div>
            </div>

            <OwnerStatementBreakdown statement={statement} />

            {statement.paymentReference && (
              <p className="text-xs text-muted-foreground">Referință plată: {statement.paymentReference}</p>
            )}
          </div>
        )}
        <DialogFooter showCloseButton />
      </DialogContent>
    </Dialog>
  )
}
