// Minimal stand-in for the public booking API, used by the Playwright suite.
// The property page fetches on the server, so a browser-side route mock is not
// enough: both Next.js and the browser talk to this server instead.
import { createServer } from "node:http"

const PORT = Number(process.env.MOCK_API_PORT ?? 8099)
const ORIGIN = `http://127.0.0.1:${PORT}`

function photo(id, n) {
  return { id: `${id}-photo-${n}`, url: `${ORIGIN}/photos/${n}.svg`, caption: null, sortOrder: n, cover: n === 0 }
}

// Same ids as the production listings the crash was reported on; everything
// else is made up.
const PROPERTIES = [
  { id: "afc5d4c6-f52c-46dd-b520-f9edbbeb91cd", name: "Apartament Central", lat: 46.7712, lng: 23.6236, price: 250 },
  { id: "d7c92c25-a9d9-4742-9edf-04f6849e03eb", name: "Studio Parc", lat: 46.7657, lng: 23.5801, price: null },
  { id: "a0bde8a7-348d-4805-9f06-24a3f0cda8c9", name: "Casa de pe deal", lat: 46.7489, lng: 23.6012, price: 420 },
]

function summary(p) {
  return {
    id: p.id,
    name: p.name,
    city: "Cluj-Napoca",
    county: "Cluj",
    latitude: p.lat,
    longitude: p.lng,
    propertyType: "APARTMENT",
    bedrooms: 2,
    bathrooms: 1,
    maxGuests: 4,
    basePricePerNight: p.price,
    currency: "RON",
    coverPhotoUrl: `${ORIGIN}/photos/0.svg`,
    facilities: ["WIFI", "KITCHEN"],
    demo: false,
  }
}

function detail(p) {
  return {
    id: p.id,
    name: p.name,
    description: "Un apartament luminos, aproape de centru.",
    propertyType: "APARTMENT",
    addressLine: null,
    city: "Cluj-Napoca",
    county: "Cluj",
    country: "România",
    latitude: p.lat,
    longitude: p.lng,
    exactLocation: false,
    bedrooms: 2,
    bathrooms: 1,
    maxGuests: 4,
    minStayNights: null,
    maxStayNights: null,
    sizeSqm: 55,
    basePricePerNight: p.price,
    currency: "RON",
    checkInTime: "14:00:00",
    checkOutTime: "11:00:00",
    facilities: ["WIFI", "KITCHEN"],
    photos: [0, 1, 2, 3, 4].map((n) => photo(p.id, n)),
    demo: false,
  }
}

function photoSvg(n) {
  const hue = (n * 67) % 360
  return `<svg xmlns="http://www.w3.org/2000/svg" width="800" height="600"><rect width="800" height="600" fill="hsl(${hue} 45% 55%)"/></svg>`
}

// The API client sends credentials, so CORS must name the origin, not "*".
function cors(req) {
  return {
    "access-control-allow-origin": req.headers.origin ?? "*",
    "access-control-allow-credentials": "true",
    vary: "Origin",
  }
}

function send(req, res, status, body, type = "application/json") {
  res.writeHead(status, { "content-type": type, ...cors(req) })
  res.end(type === "application/json" ? JSON.stringify(envelope(req, status, body)) : body)
}

// Same success/error envelope as the real API.
function envelope(req, status, data) {
  const timestamp = new Date().toISOString()
  if (status < 400) return { success: true, data, message: null, timestamp }
  return { success: false, errorCode: "NOT_FOUND", message: "Not found", timestamp, path: req.url }
}

const server = createServer((req, res) => {
  const url = new URL(req.url ?? "/", ORIGIN)
  const path = url.pathname

  if (req.method === "OPTIONS") {
    res.writeHead(204, {
      ...cors(req),
      "access-control-allow-headers": req.headers["access-control-request-headers"] ?? "content-type",
      "access-control-allow-methods": "GET,POST,OPTIONS",
    })
    return res.end()
  }

  const photoMatch = /^\/photos\/(\d+)\.svg$/.exec(path)
  if (photoMatch) return send(req, res, 200, photoSvg(Number(photoMatch[1])), "image/svg+xml")

  if (path === "/api/v1/public/properties") {
    const content = PROPERTIES.map(summary)
    return send(req, res, 200, {
      content, page: 0, size: 12, totalElements: content.length, totalPages: 1, first: true, last: true,
    })
  }

  const calendarMatch = /^\/api\/v1\/public\/properties\/([^/]+)\/calendar$/.exec(path)
  if (calendarMatch) return send(req, res, 200, [])

  const propertyMatch = /^\/api\/v1\/public\/properties\/([^/]+)$/.exec(path)
  if (propertyMatch) {
    const p = PROPERTIES.find((candidate) => candidate.id === propertyMatch[1])
    return p ? send(req, res, 200, detail(p)) : send(req, res, 404, null)
  }

  if (path === "/api/v1/public/reservations/availability") return send(req, res, 200, { available: true })
  if (path === "/api/v1/public/payments/config") {
    return send(req, res, 200, { cardPaymentsEnabled: false, publishableKey: null })
  }

  send(req, res, 404, null)
})

server.listen(PORT, "127.0.0.1", () => {
  console.log(`mock api listening on ${ORIGIN}`)
})
