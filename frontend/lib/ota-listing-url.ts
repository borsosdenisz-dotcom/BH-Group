export type OtaPlatform = "AIRBNB" | "BOOKING"

export const OTA_LISTING_URL_MAX_LENGTH = 500

const AIRBNB_DOMAINS = new Set([
  "airbnb.com",
  "airbnb.com.ro",
  "airbnb.co.uk",
  "airbnb.ca",
  "airbnb.com.au",
  "airbnb.ie",
  "airbnb.de",
  "airbnb.es",
  "airbnb.fr",
  "airbnb.it",
  "airbnb.nl",
  "airbnb.pt",
  "airbnb.at",
  "airbnb.ch",
  "airbnb.be",
  "airbnb.dk",
  "airbnb.se",
  "airbnb.no",
  "airbnb.pl",
  "airbnb.cz",
  "airbnb.hu",
  "airbnb.gr",
  "airbnb.com.tr",
  "airbnb.co.nz",
  "airbnb.com.br",
  "airbnb.mx",
  "airbnb.com.ar",
  "airbnb.cl",
  "airbnb.jp",
  "airbnb.co.kr",
  "airbnb.co.in",
  "airbnb.com.sg",
  "airbnb.com.hk",
  "airbnb.com.tw",
  "airbnb.co.za",
  "airbnb.ae",
])

const BOOKING_DOMAINS = new Set(["booking.com"])

export function isValidOtaListingUrl(value: string | null | undefined, platform: OtaPlatform) {
  if (value == null || value.trim() === "") return true

  const normalized = value.trim()
  if (normalized.length > OTA_LISTING_URL_MAX_LENGTH) return false

  try {
    const url = new URL(normalized)
    if (
      url.protocol !== "https:" ||
      url.username !== "" ||
      url.password !== "" ||
      (url.port !== "" && url.port !== "443")
    ) {
      return false
    }

    const host = url.hostname.toLowerCase()
    const allowedDomains = platform === "AIRBNB" ? AIRBNB_DOMAINS : BOOKING_DOMAINS
    return [...allowedDomains].some((domain) => host === domain || host.endsWith(`.${domain}`))
  } catch {
    return false
  }
}
