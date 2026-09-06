"use client"

import { useState } from "react"
import Link from "next/link"
import { toast } from "sonner"
import { CalendarClock, Download, Plus, Search } from "lucide-react"
import { Button, buttonVariants } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { downloadFile } from "@/lib/download-file"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Badge } from "@/components/ui/badge"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { Skeleton } from "@/components/ui/skeleton"
import { DataPagination } from "@/components/ui/data-pagination"
import { EmptyState } from "@/components/ui/empty-state"
import { PageHeader } from "@/components/ui/page-header"
import { useReservations } from "@/hooks/use-reservations"
import { useCurrentUser } from "@/hooks/use-current-user"
import {
  ALL_RESERVATION_STATUSES,
  RESERVATION_STATUS_BADGE_VARIANT,
  RESERVATION_STATUS_LABELS,
} from "@/lib/reservation-labels"
import { cn } from "@/lib/utils"
import type { ReservationStatus } from "@/lib/api/types"

export function ReservationsView() {
  const [search, setSearch] = useState("")
  const [status, setStatus] = useState<ReservationStatus | "ALL">("ALL")
  const [page, setPage] = useState(0)

  const { data: user } = useCurrentUser()
  const isFullAdmin = user?.role === "SUPER_ADMIN" || user?.role === "ADMINISTRATOR"
  const canManage = isFullAdmin || user?.role === "SUPPORT_AGENT"

  const { data, isLoading } = useReservations({
    search: search || undefined,
    status: status === "ALL" ? undefined : status,
    page,
  })

  async function handleExport() {
    const params = new URLSearchParams()
    if (search) params.set("search", search)
    if (status !== "ALL") params.set("status", status)
    try {
      await downloadFile(`/reservations/export?${params.toString()}`, "rezervari.csv")
    } catch {
      toast.error("Exportul a eșuat")
    }
  }

  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-6">
      <PageHeader eyebrow="Operațiuni" title="Rezervări" description="Administrează rezervările pentru proprietățile vizibile rolului tău." actions={
        <div className="flex flex-wrap gap-2">
          {isFullAdmin && (
            <Button type="button" variant="outline" className="gap-2" onClick={handleExport}>
              <Download className="size-4" />
              Export CSV
            </Button>
          )}
          {canManage && (
            <Link href="/dashboard/reservations/new" className={cn(buttonVariants())}>
              <Plus className="size-4" />
              Adaugă rezervare
            </Link>
          )}
        </div>
      } />

      <div className="flex flex-wrap gap-3">
        <div className="relative min-w-0 flex-[1_1_16rem]">
          <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            placeholder="Caută după numele oaspetelui..."
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
            setStatus(value as ReservationStatus | "ALL")
            setPage(0)
          }}
        >
          <SelectTrigger className="w-full sm:w-48">
            <SelectValue placeholder="Status" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">Toate statusurile</SelectItem>
            {ALL_RESERVATION_STATUSES.map((s) => (
              <SelectItem key={s} value={s}>
                {RESERVATION_STATUS_LABELS[s]}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {isLoading ? (
        <Skeleton className="h-96 w-full" />
      ) : !data || data.content.length === 0 ? (
        <EmptyState icon={CalendarClock} title="Nu au fost găsite rezervări" description="Încearcă alte criterii de căutare sau verifică din nou mai târziu." />
      ) : (
        <>
          <div className="overflow-hidden border border-border bg-card">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Oaspete</TableHead>
                  <TableHead>Proprietate</TableHead>
                  <TableHead>Perioadă</TableHead>
                  <TableHead>Status</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.content.map((reservation) => (
                  <TableRow key={reservation.id} className="cursor-pointer">
                    <TableCell>
                      <Link
                        href={`/dashboard/reservations/${reservation.id}`}
                        className="font-medium hover:text-primary"
                      >
                        {reservation.guestFirstName} {reservation.guestLastName}
                      </Link>
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {reservation.propertyName}
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {reservation.checkInDate} → {reservation.checkOutDate}
                    </TableCell>
                    <TableCell>
                      <Badge variant={RESERVATION_STATUS_BADGE_VARIANT[reservation.status]}>
                        {RESERVATION_STATUS_LABELS[reservation.status]}
                      </Badge>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
          <DataPagination page={data.page} totalPages={data.totalPages} onPageChange={setPage} />
        </>
      )}
    </div>
  )
}
