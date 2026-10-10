"use client"

import { use, useState } from "react"
import Link from "next/link"
import {
  BedDouble,
  Bath,
  Clock,
  MapPin,
  Pencil,
  Trash2,
  Users,
} from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button, buttonVariants } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { cn } from "@/lib/utils"
import { Skeleton } from "@/components/ui/skeleton"
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog"
import { PhotoGallery } from "@/components/properties/photo-gallery"
import { DocumentList } from "@/components/properties/document-list"
import { IcalSyncCard } from "@/components/properties/ical-sync-card"
import { PricingAdminSection } from "@/components/properties/pricing-admin-section"
import { PropertyFinancialSection } from "@/components/properties/property-financial-section"
import { SeasonalRatesManager } from "@/components/properties/seasonal-rates-manager"
import { useCurrentUser } from "@/hooks/use-current-user"
import { useDeleteProperty, useProperty } from "@/hooks/use-properties"
import {
  CANCELLATION_POLICY_LABELS,
  FACILITY_LABELS,
  PROPERTY_STATUS_BADGE_VARIANT,
  PROPERTY_STATUS_LABELS,
} from "@/lib/property-labels"

export default function PropertyDetailPage({
  params,
}: {
  params: Promise<{ id: string }>
}) {
  const { id } = use(params)
  const { data: property, isLoading } = useProperty(id)
  const { data: user } = useCurrentUser()
  const deleteProperty = useDeleteProperty()
  const [deleteOpen, setDeleteOpen] = useState(false)

  const canManage = user?.role === "SUPER_ADMIN" || user?.role === "ADMINISTRATOR"

  if (isLoading || !property) {
    return (
      <div className="mx-auto flex max-w-5xl flex-col gap-6">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-64 w-full" />
      </div>
    )
  }

  return (
    <div className="mx-auto flex max-w-5xl flex-col gap-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-semibold tracking-tight">{property.name}</h1>
            <Badge variant={PROPERTY_STATUS_BADGE_VARIANT[property.status]}>
              {PROPERTY_STATUS_LABELS[property.status]}
            </Badge>
          </div>
          <p className="mt-1 flex items-center gap-1 text-sm text-muted-foreground">
            <MapPin className="size-3.5" />
            {property.address.addressLine}, {property.address.city}
          </p>
        </div>

        {canManage && (
          <div className="flex gap-2">
            <Link
              href={`/dashboard/properties/${id}/edit`}
              className={cn(buttonVariants({ variant: "outline" }))}
            >
              <Pencil className="size-4" />
              Editează
            </Link>
            <AlertDialog open={deleteOpen} onOpenChange={setDeleteOpen}>
              <AlertDialogTrigger render={<Button variant="destructive" />}>
                <Trash2 className="size-4" />
                Șterge
              </AlertDialogTrigger>
              <AlertDialogContent>
                <AlertDialogHeader>
                  <AlertDialogTitle>Ștergi această proprietate?</AlertDialogTitle>
                  <AlertDialogDescription>
                    Acțiunea este ireversibilă. Toate pozele și documentele asociate vor fi șterse.
                  </AlertDialogDescription>
                </AlertDialogHeader>
                <AlertDialogFooter>
                  <AlertDialogCancel>Anulează</AlertDialogCancel>
                  <AlertDialogAction onClick={() => deleteProperty.mutate(id)}>
                    Șterge definitiv
                  </AlertDialogAction>
                </AlertDialogFooter>
              </AlertDialogContent>
            </AlertDialog>
          </div>
        )}
      </div>

      <div className="grid gap-4 sm:grid-cols-5">
        <Card>
          <CardContent className="flex items-center gap-2 text-sm">
            <BedDouble className="size-4 text-muted-foreground" />
            {property.bedrooms} dormitoare
          </CardContent>
        </Card>
        <Card>
          <CardContent className="flex items-center gap-2 text-sm">
            <Bath className="size-4 text-muted-foreground" />
            {property.bathrooms} băi
          </CardContent>
        </Card>
        <Card>
          <CardContent className="flex items-center gap-2 text-sm">
            <Users className="size-4 text-muted-foreground" />
            max {property.maxGuests} oaspeți
          </CardContent>
        </Card>
        <Card>
          <CardContent className="flex items-center gap-2 text-sm">
            <Clock className="size-4 text-muted-foreground" />
            {property.checkInTime.slice(0, 5)} - {property.checkOutTime.slice(0, 5)}
          </CardContent>
        </Card>
        <Card>
          <CardContent className="text-sm">
            {property.basePricePerNight != null ? (
              <span className="font-medium">{property.basePricePerNight} RON / noapte</span>
            ) : (
              <span className="text-muted-foreground">Preț nesetat</span>
            )}
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Poze</CardTitle>
        </CardHeader>
        <CardContent>
          <PhotoGallery propertyId={id} photos={property.photos} canManage={!!canManage} />
        </CardContent>
      </Card>

      {property.description && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Descriere</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="whitespace-pre-line text-sm text-muted-foreground">
              {property.description}
            </p>
          </CardContent>
        </Card>
      )}

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Facilități</CardTitle>
        </CardHeader>
        <CardContent>
          {property.facilities.length === 0 ? (
            <p className="text-sm text-muted-foreground">Nicio facilitate specificată.</p>
          ) : (
            <div className="flex flex-wrap gap-2">
              {property.facilities.map((facility) => (
                <Badge key={facility} variant="secondary">
                  {FACILITY_LABELS[facility]}
                </Badge>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Prețuri & reguli de rezervare</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-3 text-sm sm:grid-cols-2">
          <div>
            <span className="text-muted-foreground">Preț standard: </span>
            {property.basePricePerNight != null ? `${property.basePricePerNight} RON / noapte` : "nesetat"}
          </div>
          <div>
            <span className="text-muted-foreground">Preț weekend: </span>
            {property.weekendPricePerNight != null ? `${property.weekendPricePerNight} RON / noapte` : "—"}
          </div>
          <div>
            <span className="text-muted-foreground">Taxă curățenie: </span>
            {property.cleaningFee != null ? `${property.cleaningFee} RON` : "—"}
          </div>
          <div>
            <span className="text-muted-foreground">Taxă oaspete suplimentar: </span>
            {property.extraGuestFee != null
              ? `${property.extraGuestFee} RON/noapte (peste ${property.baseGuestsIncluded ?? "-"} oaspeți)`
              : "—"}
          </div>
          <div>
            <span className="text-muted-foreground">Discount săptămânal/lunar: </span>
            {property.weeklyDiscountPercent != null || property.monthlyDiscountPercent != null
              ? [
                  property.weeklyDiscountPercent != null ? `${property.weeklyDiscountPercent}% (7+ nopți)` : null,
                  property.monthlyDiscountPercent != null ? `${property.monthlyDiscountPercent}% (28+ nopți)` : null,
                ]
                  .filter(Boolean)
                  .join(" · ")
              : "—"}
          </div>
          <div>
            <span className="text-muted-foreground">Sejur minim/maxim: </span>
            {property.minStayNights ?? "-"} / {property.maxStayNights ?? "-"} nopți
          </div>
          <div>
            <span className="text-muted-foreground">Politică de anulare: </span>
            {CANCELLATION_POLICY_LABELS[property.cancellationPolicy]}
          </div>
          <div>
            <span className="text-muted-foreground">Proprietar: </span>
            {property.ownerName ?? "neasociat"}
            {property.commissionPercent != null && ` · comision ${property.commissionPercent}%`}
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Financiar</CardTitle>
        </CardHeader>
        <CardContent>
          <PropertyFinancialSection
            propertyId={id}
            commissionPercent={property.commissionPercent}
            canManage={!!canManage}
          />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Sezoane de preț</CardTitle>
        </CardHeader>
        <CardContent>
          <SeasonalRatesManager propertyId={id} canManage={!!canManage} />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Documente</CardTitle>
        </CardHeader>
        <CardContent>
          <DocumentList propertyId={id} documents={property.documents} canManage={!!canManage} />
        </CardContent>
      </Card>

      {canManage && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Sincronizare calendar (Airbnb / Booking.com)</CardTitle>
          </CardHeader>
          <CardContent>
            <IcalSyncCard
              propertyId={id}
              exportUrl={property.icalExportUrl}
              integrationMode={property.integrationMode}
            />
          </CardContent>
        </Card>
      )}

      {canManage && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Preț dinamic</CardTitle>
          </CardHeader>
          <CardContent>
            <PricingAdminSection propertyId={id} city={property.address.city} />
          </CardContent>
        </Card>
      )}
    </div>
  )
}
