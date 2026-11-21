import { describe, expect, it } from "vitest"
import { isValidOtaListingUrl } from "./ota-listing-url"

describe("isValidOtaListingUrl", () => {
  it.each([
    ["https://www.airbnb.com/rooms/123", "AIRBNB"],
    ["https://airbnb.com.ro/rooms/123", "AIRBNB"],
    ["https://www.booking.com/hotel/ro/test.html", "BOOKING"],
  ] as const)("accepts official HTTPS URL %s", (url, platform) => {
    expect(isValidOtaListingUrl(url, platform)).toBe(true)
  })

  it.each([
    ["http://www.airbnb.com/rooms/123", "AIRBNB"],
    ["javascript:alert(1)", "AIRBNB"],
    ["data:text/html,hello", "BOOKING"],
    ["/rooms/123", "AIRBNB"],
    ["https://airbnb.com.evil.example/rooms/123", "AIRBNB"],
    ["https://booking.com.evil.example/hotel/test", "BOOKING"],
    ["https://booking.example/hotel/test", "BOOKING"],
    ["https://user@booking.com/hotel/test", "BOOKING"],
    ["https://booking.com:8443/hotel/test", "BOOKING"],
  ] as const)("rejects unsafe or non-official URL %s", (url, platform) => {
    expect(isValidOtaListingUrl(url, platform)).toBe(false)
  })

  it("keeps optional empty fields valid", () => {
    expect(isValidOtaListingUrl(null, "AIRBNB")).toBe(true)
    expect(isValidOtaListingUrl("", "BOOKING")).toBe(true)
  })
})
