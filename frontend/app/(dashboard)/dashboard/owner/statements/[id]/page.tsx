"use client"

import { use } from "react"
import Link from "next/link"
import { ArrowLeft, Printer } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { OwnerStatementBreakdown } from "@/components/finance/owner-statement-breakdown"
import { useMyOwnerStatement } from "@/hooks/use-owner-statements"
import type { OwnerStatementStatus } from "@/lib/api/types"

const STATUS_LABELS: Record<OwnerStatementStatus, string> = {
  ISSUED: "Emis",
  PAID: "Plătit",
}

export default function OwnerStatementDetailPage({
  params,
}: {
  params: Promise<{ id: string }>
}) {
  const { id } = use(params)
  const { data: statement, isLoading } = useMyOwnerStatement(id)

  if (isLoading || !statement) {
    return (
      <div className="mx-auto flex max-w-3xl flex-col gap-6">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-64 w-full" />
      </div>
    )
  }

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-6">
      <div className="flex items-center justify-between gap-3 print:hidden">
        <Link
          href="/dashboard/owner/statements"
          className="flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
        >
          <ArrowLeft className="size-3.5" />
          Înapoi la deconturile mele
        </Link>
        <Button type="button" variant="outline" size="sm" className="gap-2" onClick={() => window.print()}>
          <Printer className="size-4" />
          Printează / salvează PDF
        </Button>
      </div>

      <div>
        <div className="flex items-center gap-3">
          <h1 className="text-2xl font-semibold tracking-tight">
            Decont {statement.periodStart} → {statement.periodEnd}
          </h1>
          <Badge variant={statement.status === "PAID" ? "secondary" : "outline"}>
            {STATUS_LABELS[statement.status]}
          </Badge>
        </div>
        <p className="mt-1 text-sm text-muted-foreground">Proprietar: {statement.ownerName}</p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Decont {statement.currency}</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-3">
          <OwnerStatementBreakdown statement={statement} />
          {statement.paymentReference && (
            <p className="text-xs text-muted-foreground">
              Referință plată: {statement.paymentReference}
              {statement.paidAt ? ` · ${statement.paidAt.slice(0, 10)}` : ""}
            </p>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
