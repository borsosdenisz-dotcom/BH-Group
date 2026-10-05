import type { ComponentProps, ReactElement, ReactNode } from "react"
import { beforeEach, describe, expect, it, vi } from "vitest"
import { render, screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import RootLayout from "./layout"
import { ThemeProvider } from "@/lib/providers/theme-provider"
import { ThemeToggle } from "@/components/layout/theme-toggle"

vi.mock("next/font/google", () => ({
  Fraunces: () => ({ variable: "font-display" }),
  Geist: () => ({ variable: "font-sans" }),
  Geist_Mono: () => ({ variable: "font-mono" }),
}))

type ProviderProps = ComponentProps<typeof ThemeProvider>

function getRootThemeProviderProps(): Omit<ProviderProps, "children"> {
  const layout = RootLayout({ children: null })
  const body = layout.props.children as ReactElement<{
    children: ReactElement<ProviderProps>
  }>
  const provider = body.props.children

  expect(provider.type).toBe(ThemeProvider)

  const props = { ...provider.props }
  delete props.children
  return props
}

function renderWithRootTheme(children: ReactNode) {
  const providerProps = getRootThemeProviderProps()
  return render(<ThemeProvider {...providerProps}>{children}</ThemeProvider>)
}

describe("root theme", () => {
  beforeEach(() => {
    localStorage.clear()
    document.documentElement.className = ""
    document.documentElement.style.colorScheme = ""
  })

  it("defaults a new visitor to dark without saving a forced preference", async () => {
    renderWithRootTheme(<ThemeToggle />)

    await waitFor(() => expect(document.documentElement).toHaveClass("dark"))
    expect(localStorage.getItem("theme")).toBeNull()
    expect(screen.getByRole("button", { name: "Comută tema (Light)" })).toBeEnabled()
  })

  it("respects a saved light preference", async () => {
    localStorage.setItem("theme", "light")

    renderWithRootTheme(<ThemeToggle />)

    await waitFor(() => expect(document.documentElement).not.toHaveClass("dark"))
    expect(document.documentElement).toHaveStyle({ colorScheme: "light" })
    expect(localStorage.getItem("theme")).toBe("light")
    expect(screen.getByRole("button", { name: "Comută tema (Dark)" })).toBeEnabled()
  })

  it("toggles the theme and persists each choice", async () => {
    const user = userEvent.setup()
    renderWithRootTheme(<ThemeToggle />)

    const switchToLight = await screen.findByRole("button", {
      name: "Comută tema (Light)",
    })
    await user.click(switchToLight)

    await waitFor(() => expect(document.documentElement).not.toHaveClass("dark"))
    expect(localStorage.getItem("theme")).toBe("light")

    const switchToDark = screen.getByRole("button", { name: "Comută tema (Dark)" })
    await user.click(switchToDark)

    await waitFor(() => expect(document.documentElement).toHaveClass("dark"))
    expect(localStorage.getItem("theme")).toBe("dark")
  })
})
