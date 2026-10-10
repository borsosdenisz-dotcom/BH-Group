"use client"

import { use } from "react"
import { OwnerRevenueLines } from "@/components/finance/owner-revenue-lines"
import Link from "next/link"
import { ArrowLeft, Bath, BedDouble, MapPin, Users } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { DocumentList } from "@/components/properties/document-list"
import { useOwnerProperty } from "@/hooks/use-owner"
import { PROPERTY_STATUS_BADGE_VARIANT, PROPERTY_STATUS_LABELS } from "@/lib/property-labels"

export default function OwnerPropertyDetailPage({
  params,
}: {
  params: Promise<{ id: string }>
}) {
  const { id } = use(params)
  const { data: property, isLoading } = useOwnerProperty(id)

  if (isLoading || !property) {
    return (
      <div className="mx-auto flex max-w-3xl flex-col gap-6">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-64 w-full" />
      </div>
    )
  }

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-6">
      <Link
        href="/dashboard/owner/properties"
        className="flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="size-3.5" />
        Înapoi la proprietățile mele
      </Link>

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

      <div className="grid gap-4 sm:grid-cols-3">
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
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Încasări (toate rezervările)</CardTitle>
        </CardHeader>
        <CardContent>
          <OwnerRevenueLines lines={property.revenueByCurrency} showPayout={false} />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Documente</CardTitle>
        </CardHeader>
        <CardContent>
          <DocumentList propertyId={id} documents={property.documents} canManage={false} viewerType="owner" />
        </CardContent>
      </Card>
    </div>
  )
}
