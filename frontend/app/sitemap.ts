import type { MetadataRoute } from "next"
import { publicApi } from "@/lib/api/public"

const SITE_URL = process.env.NEXT_PUBLIC_SITE_URL ?? "https://bhstays.ro"

const STATIC_ROUTES = ["", "/book", "/pentru-proprietari", "/termeni-si-conditii", "/confidentialitate"]

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const staticEntries: MetadataRoute.Sitemap = STATIC_ROUTES.map((path) => ({
    url: `${SITE_URL}${path}`,
    lastModified: new Date(),
  }))

  try {
    const properties = await publicApi.searchProperties({ size: 200 })
    const propertyEntries: MetadataRoute.Sitemap = properties.content
      .filter((property) => !property.demo)
      .map((property) => ({
        url: `${SITE_URL}/book/${property.id}`,
        lastModified: new Date(),
      }))
    return [...staticEntries, ...propertyEntries]
  } catch {
    return staticEntries
  }
}
