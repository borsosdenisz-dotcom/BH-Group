import { beforeEach, describe, expect, it, vi } from "vitest"
import { renderWithProviders, screen, userEvent, within } from "@/test/utils"
import { CommissionSummarySection } from "./commission-summary-section"
import { useCommissionSummary } from "@/hooks/use-financial-reports"
import { currentMonthRange } from "@/lib/commission"
import type { CommissionSummaryCurrencyTotals, CommissionSummaryResponse } from "@/lib/api/types"

vi.mock("@/hooks/use-financial-reports", () => ({
  useCommissionSummary: vi.fn(),
}))

const refetch = vi.fn()

function totals(overrides: Partial<CommissionSummaryCurrencyTotals>): CommissionSummaryCurrencyTotals {
  return {
    currency: "RON",
    capturedTotal: 2000,
    refundedTotal: 0,
    propertiesNetRevenue: 2000,
    bhStaysRevenue: 430,
    ownersAmount: 970,
    includedPropertyCount: 4,
    unallocatedNetRevenue: 0,
    unallocatedReservationCount: 0,
    ...overrides,
  }
}

function summary(overrides: Partial<CommissionSummaryResponse> = {}): CommissionSummaryResponse {
  return {
    from: null,
    to: null,
    totals: [
      totals({ currency: "EUR", propertiesNetRevenue: 100, bhStaysRevenue: 20, ownersAmount: 80, includedPropertyCount: 1, unallocatedNetRevenue: 50, unallocatedReservationCount: 1 }),
      totals({}),
    ],
    unconfiguredProperties: [{ propertyId: "p-unset", propertyName: "Apartament fără comision" }],
    ...overrides,
  }
}

function mockSummary(value: Partial<ReturnType<typeof useCommissionSummary>>) {
  vi.mocked(useCommissionSummary).mockReturnValue({
    data: undefined,
    isLoading: false,
    isError: false,
    refetch,
    ...value,
  } as never)
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe("CommissionSummarySection", () => {
  it("shows the three figures separately for each currency", () => {
    mockSummary({ data: summary() })

    renderWithProviders(<CommissionSummarySection />)

    const ron = screen.getByRole("group", { name: "Totaluri RON" })
    expect(within(ron).getByText("Venit net proprietăți").closest("[data-slot=card]")).toHaveTextContent(/2\.000,00\sRON/)
    expect(within(ron).getByText("Venit BH Stays din comisioane").closest("[data-slot=card]")).toHaveTextContent(/430,00\sRON/)
    expect(within(ron).getByText("Sumă datorată proprietarilor").closest("[data-slot=card]")).toHaveTextContent(/970,00\sRON/)
    expect(ron).toHaveTextContent("4 proprietăți incluse în RON.")
    expect(ron).not.toHaveTextContent("fără snapshot")

    const eur = screen.getByRole("group", { name: "Totaluri EUR" })
    expect(eur).toHaveTextContent(/1 rezervare fără snapshot verificabil \(50,00\sEUR\): incluse în venitul net/)
    expect(within(eur).getByText("Venit BH Stays din comisioane").closest("[data-slot=card]")).toHaveTextContent(/20,00\sEUR/)
    expect(eur).toHaveTextContent("1 proprietate inclusă în EUR.")
    expect(within(eur).queryByText(/RON/)).not.toBeInTheDocument()

    // the properties' money is never presented as the company's revenue
    expect(screen.queryByText(/venit(ul)? (total|firmei)/i)).not.toBeInTheDocument()
  })

  it("defaults to the current month", () => {
    mockSummary({ data: summary() })

    renderWithProviders(<CommissionSummarySection />)

    const { from, to } = currentMonthRange()
    expect(useCommissionSummary).toHaveBeenCalledWith({ from, to }, true)
    expect(screen.getByLabelText("De la")).toHaveValue(from)
    expect(screen.getByLabelText("Până la")).toHaveValue(to)
  })

  it("warns about unconfigured properties and links to their commission setting", () => {
    mockSummary({ data: summary() })

    renderWithProviders(<CommissionSummarySection />)

    const warning = screen.getByRole("status")
    expect(warning).toHaveTextContent("1 proprietate are comisionul neconfigurat")
    expect(within(warning).getByRole("link", { name: "Apartament fără comision" })).toHaveAttribute(
      "href",
      "/dashboard/properties/p-unset#comision-administrare"
    )
  })

  it("summarises a long list of unconfigured properties", () => {
    mockSummary({
      data: summary({
        unconfiguredProperties: Array.from({ length: 7 }, (_, i) => ({ propertyId: `p-${i}`, propertyName: `Proprietate ${i}` })),
      }),
    })

    renderWithProviders(<CommissionSummarySection />)

    const warning = screen.getByRole("status")
    expect(warning).toHaveTextContent("7 proprietăți au comisionul neconfigurat")
    expect(within(warning).getAllByRole("link")).toHaveLength(6)
    expect(within(warning).getByRole("link", { name: "și încă 2" })).toHaveAttribute("href", "/dashboard/properties")
  })

  it("has loading, empty and error states", async () => {
    mockSummary({ isLoading: true })
    const loading = renderWithProviders(<CommissionSummarySection />)
    expect(screen.getByLabelText("Se încarcă încasările")).toBeInTheDocument()
    loading.unmount()

    mockSummary({ data: summary({ totals: [], unconfiguredProperties: [] }) })
    const empty = renderWithProviders(<CommissionSummarySection />)
    expect(screen.getByText("Nicio încasare în perioada selectată.")).toBeInTheDocument()
    expect(screen.queryByRole("status")).not.toBeInTheDocument()
    empty.unmount()

    mockSummary({ isError: true })
    renderWithProviders(<CommissionSummarySection />)
    expect(screen.getByRole("alert")).toHaveTextContent("Încasările nu au putut fi încărcate.")
    await userEvent.click(screen.getByRole("button", { name: "Reîncearcă" }))
    expect(refetch).toHaveBeenCalled()
  })

  it("is a labelled region with a heading", () => {
    mockSummary({ data: summary() })
    renderWithProviders(<CommissionSummarySection />)
    expect(screen.getByRole("region", { name: "Încasări și comisioane" })).toBeInTheDocument()
    expect(screen.getByRole("heading", { level: 2, name: "Încasări și comisioane" })).toBeInTheDocument()
  })
})
