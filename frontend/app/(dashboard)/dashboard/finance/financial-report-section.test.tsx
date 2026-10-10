import { beforeEach, describe, expect, it, vi } from "vitest"
import { renderWithProviders, screen, userEvent, within } from "@/test/utils"
import { FinancialReportSection } from "./financial-report-section"
import { useFinancialReport } from "@/hooks/use-financial-reports"
import type {
  CommissionSummaryCurrencyTotals,
  FinancialReportRowResponse,
  FinancialReportSummaryResponse,
} from "@/lib/api/types"

vi.mock("@/hooks/use-financial-reports", () => ({
  useFinancialReport: vi.fn(),
}))

const refetch = vi.fn()

function row(overrides: Partial<FinancialReportRowResponse>): FinancialReportRowResponse {
  return {
    propertyId: "p1",
    propertyName: "Apartament Cluj",
    ownerName: "Maria Ionescu",
    currency: "RON",
    capturedTotal: 1000,
    refundedTotal: 250,
    netRevenue: 750,
    commissionableBase: 600,
    commissionPercents: [20],
    propertyCommissionPercent: 20,
    bhStaysRevenue: 120,
    ownerAmount: 630,
    unallocatedNetRevenue: 0,
    unallocatedReservationCount: 0,
    expensesTotal: 150,
    netProfit: 600,
    // deprecated fields carry deliberately different values: the table must not read them
    grossRevenue: 99999,
    commissionAmount: 88888,
    ...overrides,
  }
}

function revenue(overrides: Partial<CommissionSummaryCurrencyTotals>): CommissionSummaryCurrencyTotals {
  return {
    currency: "RON",
    capturedTotal: 1000,
    refundedTotal: 250,
    propertiesNetRevenue: 750,
    bhStaysRevenue: 120,
    ownersAmount: 630,
    includedPropertyCount: 1,
    unallocatedNetRevenue: 0,
    unallocatedReservationCount: 0,
    ...overrides,
  }
}

function mockReport(value: Partial<ReturnType<typeof useFinancialReport>>) {
  vi.mocked(useFinancialReport).mockReturnValue({
    data: undefined,
    isLoading: false,
    isError: false,
    refetch,
    ...value,
  } as never)
}

const report: FinancialReportSummaryResponse = {
  rows: [
    row({}),
    row({
      currency: "EUR", capturedTotal: 300, refundedTotal: 0, netRevenue: 300, commissionableBase: 200,
      bhStaysRevenue: 40, ownerAmount: 260, expensesTotal: 0, netProfit: 300,
    }),
    row({
      propertyId: "p2", propertyName: "Apartament neconfigurat", ownerName: null, capturedTotal: 400,
      refundedTotal: 0, netRevenue: 400, commissionableBase: 0, commissionPercents: [],
      propertyCommissionPercent: null, bhStaysRevenue: 0, ownerAmount: 400, expensesTotal: 0, netProfit: 400,
      unallocatedNetRevenue: 400, unallocatedReservationCount: 1,
    }),
  ],
  totals: [
    {
      currency: "EUR",
      revenue: revenue({ currency: "EUR", capturedTotal: 300, refundedTotal: 0, propertiesNetRevenue: 300, bhStaysRevenue: 40, ownersAmount: 260 }),
      totalExpenses: 0,
      totalNetProfit: 300,
      totalGrossRevenue: 99999,
      totalCommission: 88888,
    },
    {
      currency: "RON",
      revenue: revenue({
        capturedTotal: 1400, propertiesNetRevenue: 1150, ownersAmount: 1030,
        unallocatedNetRevenue: 400, unallocatedReservationCount: 1,
      }),
      totalExpenses: 150,
      totalNetProfit: 1000,
      totalGrossRevenue: 99999,
      totalCommission: 88888,
    },
  ],
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe("FinancialReportSection (/finance)", () => {
  it("shows collected money, BH Stays revenue and owner amount per property and currency", () => {
    mockReport({ data: report })
    renderWithProviders(<FinancialReportSection propertyId="" from="" to="" />)

    const [ronRow, eurRow, unsetRow] = screen.getAllByRole("row").slice(1, 4)
    expect(ronRow).toHaveTextContent(/1\.000,00\sRON/)
    expect(ronRow).toHaveTextContent(/250,00\sRON/)
    expect(ronRow).toHaveTextContent(/750,00\sRON/)
    expect(ronRow).toHaveTextContent("20%")
    expect(ronRow).toHaveTextContent(/120,00\sRON/)
    expect(ronRow).toHaveTextContent(/630,00\sRON/)
    expect(eurRow).toHaveTextContent(/40,00\sEUR/)
    expect(within(eurRow).queryByText(/RON/)).not.toBeInTheDocument()
    expect(within(unsetRow).getAllByRole("cell")[5]).toHaveTextContent("—")
    expect(unsetRow).toHaveTextContent("BH Stays")

    // the deprecated gross/commission fields are never displayed as collected money
    expect(screen.queryByText(/99\.999/)).not.toBeInTheDocument()
    expect(screen.queryByText(/88\.888/)).not.toBeInTheDocument()
    expect(screen.queryByText("Venit brut")).not.toBeInTheDocument()
  })

  it("totals each currency on its own, from the dashboard figures", () => {
    mockReport({ data: report })
    renderWithProviders(<FinancialReportSection propertyId="" from="" to="" />)

    const totalRon = screen.getByText("Total RON").closest("tr") as HTMLElement
    const totalEur = screen.getByText("Total EUR").closest("tr") as HTMLElement
    expect(totalRon).toHaveTextContent(/1\.150,00\sRON/)
    expect(totalRon).toHaveTextContent(/1\.000,00\sRON/)
    expect(totalEur).toHaveTextContent(/300,00\sEUR/)
    expect(within(totalEur).queryByText(/RON/)).not.toBeInTheDocument()
  })

  it("explains money that was not commissioned", () => {
    mockReport({ data: report })
    renderWithProviders(<FinancialReportSection propertyId="" from="" to="" />)

    const notes = screen.getByRole("status")
    expect(notes).toHaveTextContent(/1 rezervare fără snapshot verificabil \(400,00\sRON\)/)
    expect(notes).not.toHaveTextContent(/EUR/)
  })

  it("passes the period and property filters through", () => {
    mockReport({ data: report })
    renderWithProviders(<FinancialReportSection propertyId="p1" from="2026-01-01" to="2026-01-31" />)
    expect(useFinancialReport).toHaveBeenCalledWith({ propertyId: "p1", from: "2026-01-01", to: "2026-01-31" })
  })

  it("has loading, empty and error states", async () => {
    mockReport({ isLoading: true })
    const loading = renderWithProviders(<FinancialReportSection propertyId="" from="" to="" />)
    expect(screen.getByLabelText("Se încarcă raportul")).toBeInTheDocument()
    loading.unmount()

    mockReport({ data: { rows: [], totals: [] } })
    const empty = renderWithProviders(<FinancialReportSection propertyId="" from="" to="" />)
    expect(screen.getByText("Nicio proprietate găsită.")).toBeInTheDocument()
    empty.unmount()

    mockReport({ isError: true })
    renderWithProviders(<FinancialReportSection propertyId="" from="" to="" />)
    expect(screen.getByRole("alert")).toHaveTextContent("Raportul nu a putut fi încărcat.")
    await userEvent.click(screen.getByRole("button", { name: "Reîncearcă" }))
    expect(refetch).toHaveBeenCalled()
  })
})
