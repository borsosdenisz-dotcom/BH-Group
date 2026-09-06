"use client"

import Link from "next/link"
import {
  ArrowRight,
  Building2,
  CalendarClock,
  ShieldAlert,
  ShieldCheck,
  UserPlus,
  Wallet,
} from "lucide-react"
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
import { useDashboardSummary } from "@/hooks/use-dashboard"
import { ROLE_LABELS } from "@/lib/roles"
import { RESERVATION_STATUS_LABELS } from "@/lib/reservation-labels"

function formatCurrency(value: number, currency: string) {
  return new Intl.NumberFormat("ro-RO", { maximumFractionDigits: 0 }).format(value) + " " + currency
}

export function DashboardOverview() {
  const { data: user, isLoading } = useCurrentUser()
  const { data: summary, isLoading: isSummaryLoading } = useDashboardSummary()

  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">
            {isLoading ? <Skeleton className="h-8 w-64" /> : `Bine ai venit, ${user?.firstName}!`}
          </h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Statistici și activitate recentă a platformei.
          </p>
        </div>
        {!isLoading && user && (
          <div className="flex items-center gap-2">
            <Badge variant="secondary" className="text-sm">
              {ROLE_LABELS[user.role]}
            </Badge>
            {user.mfaEnabled ? (
              <span className="flex items-center gap-1 text-xs text-emerald-600">
                <ShieldCheck className="size-3.5" /> 2FA activ
              </span>
            ) : (
              <span className="flex items-center gap-1 text-xs text-amber-600">
                <ShieldAlert className="size-3.5" /> 2FA inactiv
              </span>
            )}
          </div>
        )}
      </div>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Card className="border-primary/20 bg-primary/5">
          <CardHeader>
            <CardDescription className="flex items-center gap-1.5">
              <Wallet className="size-3.5" /> Venit total
            </CardDescription>
            <CardTitle className="text-2xl">
              {isSummaryLoading || !summary ? (
                <Skeleton className="h-8 w-32" />
              ) : (
                formatCurrency(summary.totalRevenue, summary.currency)
              )}
            </CardTitle>
          </CardHeader>
        </Card>
        <Card>
          <CardHeader>
            <CardDescription className="flex items-center gap-1.5">
              <CalendarClock className="size-3.5" /> Rezervări
            </CardDescription>
            <CardTitle className="text-2xl">
              {isSummaryLoading || !summary ? <Skeleton className="h-8 w-16" /> : summary.totalReservations}
            </CardTitle>
          </CardHeader>
        </Card>
        <Card>
          <CardHeader>
            <CardDescription className="flex items-center gap-1.5">
              <Building2 className="size-3.5" /> Proprietăți
            </CardDescription>
            <CardTitle className="text-2xl">
              {isSummaryLoading || !summary ? <Skeleton className="h-8 w-16" /> : summary.totalProperties}
            </CardTitle>
          </CardHeader>
        </Card>
        <Card className={summary && summary.uncontactedLeads > 0 ? "border-amber-300/60 bg-amber-50 dark:bg-amber-950/20" : ""}>
          <CardHeader>
            <CardDescription className="flex items-center gap-1.5">
              <UserPlus className="size-3.5" /> Lead-uri noi
            </CardDescription>
            <CardTitle className="text-2xl">
              {isSummaryLoading || !summary ? <Skeleton className="h-8 w-16" /> : summary.uncontactedLeads}
            </CardTitle>
          </CardHeader>
        </Card>
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        <Link
          href="/dashboard/properties"
          className="group flex items-center justify-between rounded-xl border border-border/60 bg-card p-6 transition-colors hover:border-primary/40"
        >
          <div className="flex items-center gap-4">
            <span className="flex size-11 items-center justify-center rounded-lg bg-primary/10 text-primary">
              <Building2 className="size-5" />
            </span>
            <div>
              <p className="font-medium">Proprietăți</p>
              <p className="text-sm text-muted-foreground">Administrează listările</p>
            </div>
          </div>
          <ArrowRight className="size-4 text-muted-foreground transition-transform group-hover:translate-x-1" />
        </Link>

        <Link
          href="/dashboard/reservations"
          className="group flex items-center justify-between rounded-xl border border-border/60 bg-card p-6 transition-colors hover:border-primary/40"
        >
          <div className="flex items-center gap-4">
            <span className="flex size-11 items-center justify-center rounded-lg bg-primary/10 text-primary">
              <CalendarClock className="size-5" />
            </span>
            <div>
              <p className="font-medium">Rezervări</p>
              <p className="text-sm text-muted-foreground">Vezi și gestionează</p>
            </div>
          </div>
          <ArrowRight className="size-4 text-muted-foreground transition-transform group-hover:translate-x-1" />
        </Link>

        <Link
          href="/dashboard/leads"
          className="group flex items-center justify-between rounded-xl border border-border/60 bg-card p-6 transition-colors hover:border-primary/40"
        >
          <div className="flex items-center gap-4">
            <span className="flex size-11 items-center justify-center rounded-lg bg-primary/10 text-primary">
              <UserPlus className="size-5" />
            </span>
            <div>
              <p className="font-medium">Lead-uri</p>
              <p className="text-sm text-muted-foreground">Proprietari interesați</p>
            </div>
          </div>
          <ArrowRight className="size-4 text-muted-foreground transition-transform group-hover:translate-x-1" />
        </Link>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Rezervări viitoare</CardTitle>
          </CardHeader>
          <CardContent>
            {isSummaryLoading || !summary ? (
              <Skeleton className="h-40" />
            ) : summary.upcomingReservations.length === 0 ? (
              <p className="text-sm text-muted-foreground">Nu există rezervări viitoare.</p>
            ) : (
              <div className="flex flex-col divide-y divide-border/60">
                {summary.upcomingReservations.map((reservation) => (
                  <Link
                    key={reservation.id}
                    href={`/dashboard/reservations/${reservation.id}`}
                    className="flex items-center justify-between py-2.5 text-sm hover:text-primary"
                  >
                    <span className="font-medium">
                      {reservation.guestFirstName} {reservation.guestLastName}
                    </span>
                    <span className="text-muted-foreground">
                      {reservation.checkInDate} · {RESERVATION_STATUS_LABELS[reservation.status]}
                    </span>
                  </Link>
                ))}
              </div>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">Lead-uri recente</CardTitle>
          </CardHeader>
          <CardContent>
            {isSummaryLoading || !summary ? (
              <Skeleton className="h-40" />
            ) : summary.recentLeads.length === 0 ? (
              <p className="text-sm text-muted-foreground">Niciun lead încă.</p>
            ) : (
              <div className="flex flex-col divide-y divide-border/60">
                {summary.recentLeads.map((lead) => (
                  <Link
                    key={lead.id}
                    href="/dashboard/leads"
                    className="flex items-center justify-between py-2.5 text-sm hover:text-primary"
                  >
                    <span className="font-medium">{lead.fullName}</span>
                    <Badge variant={lead.contacted ? "secondary" : "default"} className="text-[10px]">
                      {lead.contacted ? "Contactat" : "Nou"}
                    </Badge>
                  </Link>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
