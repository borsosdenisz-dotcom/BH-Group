import { expect, test, type Page } from "@playwright/test"

// Mirrors e2e/mock-api.mjs.
const PROPERTIES = [
  { id: "afc5d4c6-f52c-46dd-b520-f9edbbeb91cd", name: "Apartament Central" },
  { id: "d7c92c25-a9d9-4742-9edf-04f6849e03eb", name: "Studio Parc" },
  { id: "a0bde8a7-348d-4805-9f06-24a3f0cda8c9", name: "Casa de pe deal" },
]

const ERROR_BOUNDARY_TEXT = "A apărut o eroare neașteptată"

// 1x1 transparent PNG, so map tiles and pin icons load without the network.
const EMPTY_PNG = Buffer.from(
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=",
  "base64"
)

/** Collects every uncaught error and console.error the page produces. */
function trackErrors(page: Page) {
  const errors: string[] = []
  page.on("pageerror", (error) => errors.push(`pageerror: ${error.stack ?? error.message}`))
  page.on("console", (message) => {
    if (message.type() === "error") errors.push(`console.error: ${message.text()}`)
  })
  return errors
}

async function prepare(page: Page, theme: "dark" | "light") {
  await page.route(/tile\.openstreetmap\.org|unpkg\.com/, (route) =>
    route.fulfill({ status: 200, contentType: "image/png", body: EMPTY_PNG })
  )
  await page.addInitScript((value) => {
    window.localStorage.setItem("theme", value)
    window.localStorage.setItem("bh-cookie-consent", "necessary")
  }, theme)
}

/**
 * Opens /book and waits until the results map exists - hidden on a phone, but
 * with its pins - so a card interaction reaches the map like it does for a
 * real visitor, instead of racing Leaflet's lazy load.
 */
async function openListing(page: Page, propertyId: string) {
  await page.goto("/book")
  const card = page.locator(`a[href^="/book/${propertyId}"]`).first()
  await expect(card).toBeVisible()
  await page.getByTestId("results-map").locator(".leaflet-container").waitFor({ state: "attached" })
  return card
}

/** The property page in full: gallery, lightbox, map and calendar. */
async function expectPropertyPage(page: Page, name: string) {
  await expect(page.getByRole("heading", { level: 1, name })).toBeVisible()
  await expect(page.getByText(ERROR_BOUNDARY_TEXT)).toHaveCount(0)

  await page.getByRole("button", { name: `Deschide galeria foto — ${name}, fotografia 1 din 5` }).click()
  const lightbox = page.getByRole("dialog")
  await expect(lightbox).toBeVisible()
  await lightbox.getByRole("button", { name: "Închide galeria" }).click()
  await expect(lightbox).toHaveCount(0)

  const location = page.getByRole("heading", { name: "Unde vei sta" })
  await location.scrollIntoViewIfNeeded()
  await expect(page.locator(".leaflet-container")).toBeVisible()

  const next = page.getByRole("button", { name: "Luna următoare" })
  await next.scrollIntoViewIfNeeded()
  await next.click()
  await expect(page.getByRole("button", { name: "Luna anterioară" })).toBeEnabled()
}

for (const theme of ["dark", "light"] as const) {
  test.describe(`property details (${theme})`, () => {
    test.beforeEach(async ({ page }) => {
      await prepare(page, theme)
    })

    /**
     * Regression: on phones the listings map is mounted hidden, so Leaflet
     * never gets a view. Tapping a card fires mouseenter, which highlighted
     * its pin and asked the hidden map for its bounds - Leaflet threw "Set map
     * center and zoom first." and /book fell into the error boundary instead
     * of opening the property.
     */
    for (const property of PROPERTIES) {
      test(`opens ${property.name} from the listing`, async ({ page, isMobile }) => {
        const errors = trackErrors(page)
        const card = await openListing(page, property.id)

        if (isMobile) await card.tap()
        else await card.click()

        await expect(page.getByText(ERROR_BOUNDARY_TEXT)).toHaveCount(0)
        await expect(page).toHaveURL(new RegExp(`/book/${property.id}`), { timeout: 10_000 })
        await expectPropertyPage(page, property.name)
        await expect(page.locator("html")).toHaveClass(new RegExp(`\\b${theme}\\b`))
        expect(errors).toEqual([])
      })

      test(`opens ${property.name} directly`, async ({ page }) => {
        const errors = trackErrors(page)
        await page.goto(`/book/${property.id}`)
        await expectPropertyPage(page, property.name)
        expect(errors).toEqual([])
      })
    }

    test("the listing map still works after it is shown on a phone", async ({ page, isMobile }) => {
      test.skip(!isMobile, "the map toggle only exists below lg")
      const errors = trackErrors(page)
      const card = await openListing(page, PROPERTIES[0].id)
      await card.hover()
      await page.getByRole("button", { name: "Arată harta" }).tap()
      await expect(page.getByTestId("results-map").locator(".leaflet-container")).toBeVisible()
      await expect(page.getByTestId("results-map").getByText("250 RON")).toBeVisible()
      expect(errors).toEqual([])
    })
  })
}
