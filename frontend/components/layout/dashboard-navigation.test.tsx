import { beforeEach, describe, expect, it, vi } from "vitest"
import { renderWithProviders, screen, userEvent } from "@/test/utils"
import { useCurrentUser } from "@/hooks/use-current-user"
import { DashboardMobileNav } from "./dashboard-mobile-nav"
import { DashboardSidebar } from "./dashboard-sidebar"

vi.mock("@/hooks/use-current-user", () => ({
  useCurrentUser: vi.fn(),
}))

describe("dashboard navigation", () => {
  beforeEach(() => {
    vi.mocked(useCurrentUser).mockReturnValue({
      data: { role: "SUPER_ADMIN" },
    } as never)
  })

  it("links the desktop brand to the admin menu and the back arrow to the client portal", () => {
    renderWithProviders(<DashboardSidebar />)

    expect(screen.getByRole("link", { name: "Meniul principal BH Stays" })).toHaveAttribute(
      "href",
      "/dashboard"
    )
    expect(screen.getByRole("link", { name: "Înapoi la portalul clienților" })).toHaveAttribute(
      "href",
      "/"
    )
  })

  it("offers the same destinations in the mobile menu", async () => {
    const user = userEvent.setup()
    renderWithProviders(<DashboardMobileNav />)

    await user.click(screen.getByRole("button", { name: "Deschide meniul" }))

    expect(screen.getByRole("link", { name: "Meniul principal BH Stays" })).toHaveAttribute(
      "href",
      "/dashboard"
    )
    expect(screen.getByRole("link", { name: "Înapoi la portalul clienților" })).toHaveAttribute(
      "href",
      "/"
    )
  })
})
