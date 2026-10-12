import type { Metadata } from "next"
import { publicApi } from "@/lib/api/public"
import { PROPERTY_TYPE_LABELS } from "@/lib/property-labels"
import { PropertyDetailContent } from "./property-detail-content"

const SITE_URL = process.env.NEXT_PUBLIC_SITE_URL ?? "https://bhstays.ro"

async function fetchProperty(id: string) {
  try {
    return await publicApi.getProperty(id)
  } catch {
    return null
  }
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ id: string }>
}): Promise<Metadata> {
  const { id } = await params
  const property = await fetchProperty(id)

  if (!property) {
    return { title: "Proprietate" }
  }

  const title = `${property.name} — ${property.city}`
  const description =
    property.description?.slice(0, 160) ??
    `${PROPERTY_TYPE_LABELS[property.propertyType]} în ${property.city}, ${property.bedrooms} dormitoare, până la ${property.maxGuests} oaspeți.`
  const imageUrl = property.photos[0]?.url

  return {
    title,
    description,
    // A demo listing is a showcase, not an offer - keep it out of search results.
    ...(property.demo ? { robots: { index: false, follow: false } } : {}),
    alternates: { canonical: `${SITE_URL}/book/${id}` },
    openGraph: {
      title,
      description,
      url: `${SITE_URL}/book/${id}`,
      type: "website",
      images: imageUrl ? [{ url: imageUrl }] : undefined,
    },
    twitter: {
      card: "summary_large_image",
      title,
      description,
      images: imageUrl ? [imageUrl] : undefined,
    },
  }
}

export default async function PublicPropertyDetailPage({
  params,
}: {
  params: Promise<{ id: string }>
}) {
  const { id } = await params
  const property = await fetchProperty(id)

  const jsonLd = property && !property.demo
    ? {
        "@context": "https://schema.org",
        "@type": "LodgingBusiness",
        name: property.name,
        description: property.description ?? undefined,
        image: property.photos.map((p) => p.url),
        address: {
          "@type": "PostalAddress",
          addressLocality: property.city,
          addressRegion: property.county ?? undefined,
          addressCountry: property.country,
        },
        amenityFeature: property.facilities.map((facility) => ({
          "@type": "LocationFeatureSpecification",
          name: facility,
        })),
        ...(property.basePricePerNight != null
          ? {
              priceRange: `${property.basePricePerNight} ${property.currency}`,
            }
          : {}),
      }
    : null

  return (
    <>
      {jsonLd && (
        <script
          type="application/ld+json"
          dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
        />
      )}
      <PropertyDetailContent id={id} />
    </>
  )
}
