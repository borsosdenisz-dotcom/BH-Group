import { fireEvent, screen, waitFor } from "@testing-library/react"
import { describe, expect, it } from "vitest"
import { renderWithProviders, userEvent } from "@/test/utils"
import type { PropertyPhotoResponse } from "@/lib/api/types"
import { PropertyGallery } from "./property-gallery"

const photos: PropertyPhotoResponse[] = [
  {
    id: "photo-1",
    url: "/photo-1.jpg",
    caption: "Living",
    sortOrder: 0,
    cover: true,
  },
  {
    id: "photo-2",
    url: "/photo-2.jpg",
    caption: "Dormitor",
    sortOrder: 1,
    cover: false,
  },
  {
    id: "photo-3",
    url: "/photo-3.jpg",
    caption: null,
    sortOrder: 2,
    cover: false,
  },
]

describe("PropertyGallery", () => {
  it("opens the fullscreen gallery at the selected photo", async () => {
    const user = userEvent.setup()
    renderWithProviders(<PropertyGallery photos={photos} propertyName="Apartament Central" />)

    await user.click(
      screen.getByRole("button", {
        name: "Deschide galeria foto — Apartament Central, fotografia 2 din 3",
      })
    )

    const dialog = screen.getByRole("dialog", {
      name: "Apartament Central — fotografia 2 din 3",
    })
    expect(dialog).toBeInTheDocument()
    expect(dialog).toHaveClass("h-dvh", "w-screen", "max-w-none")
    expect(screen.getByRole("img", { name: "Dormitor" })).toHaveClass("object-contain")
    expect(screen.getByRole("status")).toHaveTextContent("2 / 3")
    await waitFor(() => expect(screen.getByRole("button", { name: "Închide galeria" })).toHaveFocus())
  })

  it("navigates in order with controls and keyboard arrows", async () => {
    const user = userEvent.setup()
    renderWithProviders(<PropertyGallery photos={photos} propertyName="Apartament Central" />)

    await user.click(screen.getByRole("button", { name: /fotografia 1 din 3/ }))
    await user.click(screen.getByRole("button", { name: "Fotografia următoare" }))
    expect(screen.getByRole("status")).toHaveTextContent("2 / 3")
    expect(screen.getByRole("img", { name: "Dormitor" })).toBeInTheDocument()

    fireEvent.keyDown(window, { key: "ArrowRight" })
    expect(screen.getByRole("status")).toHaveTextContent("3 / 3")
    expect(
      screen.getByRole("img", { name: "Apartament Central — fotografia 3" })
    ).toBeInTheDocument()

    fireEvent.keyDown(window, { key: "ArrowLeft" })
    expect(screen.getByRole("status")).toHaveTextContent("2 / 3")

    await user.click(screen.getByRole("button", { name: "Fotografia anterioară" }))
    expect(screen.getByRole("status")).toHaveTextContent("1 / 3")
  })

  it("closes from the close button and Escape", async () => {
    const user = userEvent.setup()
    renderWithProviders(<PropertyGallery photos={photos} propertyName="Apartament Central" />)

    const openButton = screen.getByRole("button", { name: /fotografia 1 din 3/ })
    await user.click(openButton)
    await user.click(screen.getByRole("button", { name: "Închide galeria" }))
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument())
    expect(openButton).toHaveFocus()

    await user.click(openButton)
    await user.keyboard("{Escape}")
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument())
  })
})
