"use client"

import Link from "next/link"
import { OwnerRevenueLines } from "@/components/finance/owner-revenue-lines"
import { AlertTriangle, ArrowRight, Building2, CalendarClock, Wallet } from "lucide-react"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Skeleton } from "@/components/ui/skeleton"
import { useCurrentUser } from "@/hooks/use-current-user"
import { useOwnerDashboardSummary } from "@/hooks/use-owner"
import { RESERVATION_STATUS_LABELS } from "@/lib/reservation-labels"
import { MAINTENANCE_PRIORITY_BADGE_VARIANT, MAINTENANCE_PRIORITY_LABELS } from "@/lib/cleaning-labels"

export function OwnerDashboardOverview() {
  const { data: user, isLoading: isUserLoading } = useCurrentUser()
  const { data: summary, isLoading } = useOwnerDashboardSummary()

  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">
          {isUserLoading ? <Skeleton className="h-8 w-64" /> : `Bine ai venit, ${user?.firstName}!`}
        </h1>
        <p className="mt-1 text-sm text-muted-foreground">
          Situația proprietăților tale administrate de BH Stays.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-1.5 text-base">
            <Wallet className="size-4" aria-hidden /> Încasări și sumă de plată
          </CardTitle>
          <CardDescription>Bani încasați după refunduri, separat pe monedă.</CardDescription>
        </CardHeader>
        <CardContent>
          {isLoading || !summary ? (
            <Skeleton className="h-32 w-full" aria-label="Se încarcă încasările" />
          ) : (
            <OwnerRevenueLines lines={summary.revenueByCurrency} />
          )}
        </CardContent>
      </Card>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
        <Card>
          <CardHeader>
            <CardDescription className="flex items-center gap-1.5">
              <Building2 className="size-3.5" /> Proprietăți
            </CardDescription>
            <CardTitle className="text-2xl">
              {isLoading || !summary ? <Skeleton className="h-8 w-16" /> : summary.totalProperties}
            </CardTitle>
          </CardHeader>
        </Card>
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <Link
          href="/dashboard/owner/properties"
          className="group flex items-center justify-between rounded-xl border border-border/60 bg-card p-6 transition-colors hover:border-primary/40"
        >
          <div className="flex items-center gap-4">
            <span className="flex size-11 items-center justify-center rounded-lg bg-primary/10 text-primary">
              <Building2 className="size-5" />
            </span>
            <div>
              <p className="font-medium">Proprietățile mele</p>
              <p className="text-sm text-muted-foreground">Vezi detalii și venituri</p>
            </div>
          </div>
          <ArrowRight className="size-4 text-muted-foreground transition-transform group-hover:translate-x-1" />
        </Link>

        <Link
          href="/dashboard/owner/reservations"
          className="group flex items-center justify-between rounded-xl border border-border/60 bg-card p-6 transition-colors hover:border-primary/40"
        >
          <div className="flex items-center gap-4">
            <span className="flex size-11 items-center justify-center rounded-lg bg-primary/10 text-primary">
              <CalendarClock className="size-5" />
            </span>
            <div>
              <p className="font-medium">Rezervările mele</p>
              <p className="text-sm text-muted-foreground">Vezi și filtrează</p>
            </div>
          </div>
          <ArrowRight className="size-4 text-muted-foreground transition-transform group-hover:translate-x-1" />
        </Link>
      </div>

      {!isLoading && summary && summary.openMaintenanceTickets.length > 0 && (
        <Card className="border-destructive/30 bg-destructive/5">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              <AlertTriangle className="size-4 text-destructive" />
              Probleme de mentenanță în curs
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="flex flex-col divide-y divide-border/60">
              {summary.openMaintenanceTickets.map((ticket) => (
                <div key={ticket.id} className="flex items-center justify-between py-2.5 text-sm">
                  <span className="font-medium">
                    {ticket.title}
                    <span className="ml-2 font-normal text-muted-foreground">{ticket.propertyName}</span>
                  </span>
                  <Badge variant={MAINTENANCE_PRIORITY_BADGE_VARIANT[ticket.priority]}>
                    {MAINTENANCE_PRIORITY_LABELS[ticket.priority]}
                  </Badge>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Rezervări viitoare</CardTitle>
        </CardHeader>
        <CardContent>
          {isLoading || !summary ? (
            <Skeleton className="h-40" />
          ) : summary.upcomingReservations.length === 0 ? (
            <p className="text-sm text-muted-foreground">Nu există rezervări viitoare.</p>
          ) : (
            <div className="flex flex-col divide-y divide-border/60">
              {summary.upcomingReservations.map((reservation) => (
                <div key={reservation.id} className="flex items-center justify-between py-2.5 text-sm">
                  <span className="font-medium">
                    {reservation.guestFirstName} {reservation.guestLastName}
                    <span className="ml-2 font-normal text-muted-foreground">
                      {reservation.propertyName}
                    </span>
                  </span>
                  <span className="flex items-center gap-2 text-muted-foreground">
                    {reservation.checkInDate}
                    <Badge variant="outline" className="text-[10px]">
                      {RESERVATION_STATUS_LABELS[reservation.status]}
                    </Badge>
                  </span>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
