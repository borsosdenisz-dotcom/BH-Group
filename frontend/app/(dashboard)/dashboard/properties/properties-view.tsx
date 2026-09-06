"use client"

import { useState } from "react"
import Link from "next/link"
import { toast } from "sonner"
import { Building2, Download, Plus, Search } from "lucide-react"
import { Button, buttonVariants } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { cn } from "@/lib/utils"
import { downloadFile } from "@/lib/download-file"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import { DataPagination } from "@/components/ui/data-pagination"
import { EmptyState } from "@/components/ui/empty-state"
import { PageHeader } from "@/components/ui/page-header"
import { PropertyCard } from "@/components/properties/property-card"
import { useProperties } from "@/hooks/use-properties"
import { useCurrentUser } from "@/hooks/use-current-user"
import {
  ALL_PROPERTY_STATUSES,
  ALL_PROPERTY_TYPES,
  PROPERTY_STATUS_LABELS,
  PROPERTY_TYPE_LABELS,
} from "@/lib/property-labels"
import type { PropertyStatus, PropertyType } from "@/lib/api/types"

export function PropertiesView() {
  const [search, setSearch] = useState("")
  const [status, setStatus] = useState<PropertyStatus | "ALL">("ALL")
  const [type, setType] = useState<PropertyType | "ALL">("ALL")
  const [page, setPage] = useState(0)

  const { data: user } = useCurrentUser()
  const canManage = user?.role === "SUPER_ADMIN" || user?.role === "ADMINISTRATOR"

  const { data, isLoading } = useProperties({
    search: search || undefined,
    status: status === "ALL" ? undefined : status,
    type: type === "ALL" ? undefined : type,
    page,
  })

  async function handleExport() {
    const params = new URLSearchParams()
    if (search) params.set("search", search)
    if (status !== "ALL") params.set("status", status)
    if (type !== "ALL") params.set("type", type)
    try {
      await downloadFile(`/properties/export?${params.toString()}`, "proprietati.csv")
    } catch {
      toast.error("Exportul a eșuat")
    }
  }

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-6">
      <PageHeader eyebrow="Portofoliu" title="Proprietăți" description="Administrează portofoliul de proprietăți." actions={
        <div className="flex flex-wrap gap-2">
          <Button type="button" variant="outline" className="gap-2" onClick={handleExport}>
            <Download className="size-4" />
            Export CSV
          </Button>
          {canManage && (
            <Link href="/dashboard/properties/new" className={cn(buttonVariants())}>
              <Plus className="size-4" />
              Adaugă proprietate
            </Link>
          )}
        </div>
      } />

      <div className="flex flex-wrap gap-3">
        <div className="relative min-w-0 flex-[1_1_16rem]">
          <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            placeholder="Caută după nume sau oraș..."
            className="pl-9"
            value={search}
            onChange={(e) => {
              setSearch(e.target.value)
              setPage(0)
            }}
          />
        </div>
        <Select
          value={status}
          onValueChange={(value) => {
            setStatus(value as PropertyStatus | "ALL")
            setPage(0)
          }}
        >
          <SelectTrigger className="w-full sm:w-44">
            <SelectValue placeholder="Status" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">Toate statusurile</SelectItem>
            {ALL_PROPERTY_STATUSES.map((s) => (
              <SelectItem key={s} value={s}>
                {PROPERTY_STATUS_LABELS[s]}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select
          value={type}
          onValueChange={(value) => {
            setType(value as PropertyType | "ALL")
            setPage(0)
          }}
        >
          <SelectTrigger className="w-full sm:w-44">
            <SelectValue placeholder="Tip" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">Toate tipurile</SelectItem>
            {ALL_PROPERTY_TYPES.map((t) => (
              <SelectItem key={t} value={t}>
                {PROPERTY_TYPE_LABELS[t]}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {isLoading ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {Array.from({ length: 8 }).map((_, i) => (
            <Skeleton key={i} className="aspect-[4/3] w-full" />
          ))}
        </div>
      ) : !data || data.content.length === 0 ? (
        <EmptyState icon={Building2} title="Nu au fost găsite proprietăți" description="Ajustează filtrele sau adaugă prima proprietate, dacă rolul tău permite acest lucru." />
      ) : (
        <>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
            {data.content.map((property) => (
              <PropertyCard key={property.id} property={property} />
            ))}
          </div>
          <DataPagination page={data.page} totalPages={data.totalPages} onPageChange={setPage} />
        </>
      )}
    </div>
  )
}
