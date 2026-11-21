import { describe, expect, it } from "vitest"
import { renderWithProviders, screen, userEvent } from "@/test/utils"
import { SiteHeader } from "./site-header"

describe("SiteHeader", () => {
  it("exposes the owners page in desktop and mobile navigation", async () => {
    const user = userEvent.setup()
    renderWithProviders(<SiteHeader />)

    expect(screen.getByRole("navigation").querySelector('a[href="/pentru-proprietari"]')).not.toBeNull()

    await user.click(screen.getByRole("button", { name: "Deschide meniul" }))

    const mobileNavigation = screen.getByRole("navigation", { name: "Navigație mobilă" })
    expect(mobileNavigation.querySelector('a[href="/pentru-proprietari"]')).toHaveTextContent("Pentru proprietari")
    expect(screen.getAllByText("Vezi proprietăți").length).toBeGreaterThan(0)
    expect(screen.queryByText("Rezervă acum")).not.toBeInTheDocument()
  })

  it("retains an accessible light/dark theme control", async () => {
    renderWithProviders(<SiteHeader />)

    expect(await screen.findByRole("button", { name: /Comută tema/ })).toBeEnabled()
  })
})
