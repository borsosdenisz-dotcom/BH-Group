import { describe, expect, it } from "vitest"
import { renderWithProviders, screen } from "@/test/utils"
import { PentruProprietariHero } from "./pentru-proprietari-hero"
import { ProcessSection } from "./process-section"
import { ServicesSection } from "./services-section"

describe("owners page content", () => {
  it("covers OTA administration and onboarding with the required lead CTA", () => {
    renderWithProviders(
      <>
        <PentruProprietariHero />
        <ServicesSection />
        <ProcessSection />
      </>
    )

    expect(screen.getByRole("button", { name: "Solicită o evaluare gratuită" })).toBeInTheDocument()
    expect(screen.getByText("Administrarea anunțurilor Airbnb și Booking.com")).toBeInTheDocument()
    expect(screen.getByText("Comunicare cu oaspeții")).toBeInTheDocument()
    expect(screen.getByText("Prețuri adaptate sezonului")).toBeInTheDocument()
    expect(screen.getByText("Coordonare check-in și check-out")).toBeInTheDocument()
    expect(screen.getByText("Curățenie și verificarea proprietății")).toBeInTheDocument()
    expect(screen.getByText("Rapoarte financiare transparente")).toBeInTheDocument()
    expect(screen.getByText("Un onboarding clar, pas cu pas")).toBeInTheDocument()
  })
})
