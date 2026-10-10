import { beforeEach, describe, expect, it, vi } from "vitest"
import { fireEvent, renderWithProviders, screen, userEvent, within } from "@/test/utils"
import { PropertyFinancialSection } from "./property-financial-section"
import { usePropertyCommissionReport } from "@/hooks/use-financial-reports"
import { useUpdateManagementCommission } from "@/hooks/use-properties"
import { currentMonthRange } from "@/lib/commission"
import type { PropertyCommissionCurrency, PropertyCommissionReportResponse } from "@/lib/api/types"

vi.mock("@/hooks/use-financial-reports", () => ({
  usePropertyCommissionReport: vi.fn(),
}))

vi.mock("@/hooks/use-properties", () => ({
  useUpdateManagementCommission: vi.fn(),
}))

const PROPERTY_ID = "property-1"

function currencyLine(overrides: Partial<PropertyCommissionCurrency>): PropertyCommissionCurrency {
  return {
    currency: "RON",
    capturedTotal: 1500,
    refundedTotal: 250,
    netRevenue: 1250,
    commissionableBase: 1000,
    commissionPercents: [20],
    bhStaysRevenue: 200,
    ownerAmount: 1050,
    unallocatedNetRevenue: 0,
    unallocatedReservationCount: 0,
    reservationCount: 2,
    ...overrides,
  }
}

function report(currencies: PropertyCommissionCurrency[], commissionPercent: number | null = 20): PropertyCommissionReportResponse {
  return {
    propertyId: PROPERTY_ID,
    propertyName: "Apartament Cluj",
    from: null,
    to: null,
    commissionPercent,
    commissionConfigured: commissionPercent != null,
    currencies,
  }
}

const mutate = vi.fn()
const refetch = vi.fn()

function mockReport(value: Partial<ReturnType<typeof usePropertyCommissionReport>>) {
  vi.mocked(usePropertyCommissionReport).mockReturnValue({
    data: undefined,
    isLoading: false,
    isError: false,
    refetch,
    ...value,
  } as never)
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(useUpdateManagementCommission).mockReturnValue({ mutate, isPending: false } as never)
})

describe("PropertyFinancialSection", () => {
  it("shows every figure for the period, one block per currency", () => {
    mockReport({
      data: report([
        currencyLine({ currency: "EUR", capturedTotal: 500, refundedTotal: 200, netRevenue: 300, commissionableBase: 300, bhStaysRevenue: 60, ownerAmount: 240 }),
        currencyLine({}),
      ]),
    })

    renderWithProviders(<PropertyFinancialSection propertyId={PROPERTY_ID} commissionPercent={20} canManage />)

    const ron = screen.getByRole("region", { name: "Încasări în RON" })
    expect(within(ron).getByText("Venit brut încasat").nextSibling).toHaveTextContent(/1\.500,00\sRON/)
    expect(within(ron).getByText("Refunduri").nextSibling).toHaveTextContent(/250,00\sRON/)
    expect(within(ron).getByText("Venit net").nextSibling).toHaveTextContent(/1\.250,00\sRON/)
    expect(within(ron).getByText("Procent BH Stays (rezervări)").nextSibling).toHaveTextContent("20%")
    expect(within(ron).getByText("Venit BH Stays").nextSibling).toHaveTextContent(/200,00\sRON/)
    expect(within(ron).getByText("Sumă proprietar").nextSibling).toHaveTextContent(/1\.050,00\sRON/)

    const eur = screen.getByRole("region", { name: "Încasări în EUR" })
    expect(within(eur).getByText("Venit net").nextSibling).toHaveTextContent(/300,00\sEUR/)
    expect(within(eur).getByText("Sumă proprietar").nextSibling).toHaveTextContent(/240,00\sEUR/)
    // nothing in one currency's block is expressed in the other
    expect(within(eur).queryByText(/RON/)).not.toBeInTheDocument()
  })

  it("requests the current month by default", () => {
    mockReport({ data: report([]) })

    renderWithProviders(<PropertyFinancialSection propertyId={PROPERTY_ID} commissionPercent={20} canManage={false} />)

    const { from, to } = currentMonthRange()
    expect(usePropertyCommissionReport).toHaveBeenCalledWith(PROPERTY_ID, { from, to }, true)
    expect(screen.getByLabelText("De la")).toHaveValue(from)
    expect(screen.getByLabelText("Până la")).toHaveValue(to)
  })

  it("warns that new reservations get no commission while the property is unconfigured", () => {
    // past reservations keep their own snapshot percent, even though the setting is now empty
    mockReport({ data: report([currencyLine({ commissionPercents: [20, 25] })], null) })

    renderWithProviders(<PropertyFinancialSection propertyId={PROPERTY_ID} commissionPercent={null} canManage />)

    expect(screen.getByRole("status")).toHaveTextContent("Comision neconfigurat")
    expect(screen.getByRole("status")).toHaveTextContent("Rezervările create cât timp procentul nu este setat")
    const ron = screen.getByRole("region", { name: "Încasări în RON" })
    expect(within(ron).getByText("Procent BH Stays (rezervări)").nextSibling).toHaveTextContent("20% / 25%")
    expect(within(ron).getByText("Venit BH Stays").nextSibling).toHaveTextContent(/200,00\sRON/)
  })

  it("shows a later refund as a negative adjustment of its own period", () => {
    mockReport({
      data: report([currencyLine({
        capturedTotal: 0, refundedTotal: 100, netRevenue: -100, commissionableBase: -80,
        bhStaysRevenue: -16, ownerAmount: -84,
      })]),
    })

    renderWithProviders(<PropertyFinancialSection propertyId={PROPERTY_ID} commissionPercent={20} canManage={false} />)

    const ron = screen.getByRole("region", { name: "Încasări în RON" })
    expect(within(ron).getByText("Venit net").nextSibling).toHaveTextContent(/-100,00\sRON/)
    expect(within(ron).getByText("Venit BH Stays").nextSibling).toHaveTextContent(/-16,00\sRON/)
    expect(within(ron).getByText("Sumă proprietar").nextSibling).toHaveTextContent(/-84,00\sRON/)
  })

  it("mentions collected money of reservations without a verifiable snapshot", () => {
    mockReport({ data: report([currencyLine({ unallocatedNetRevenue: 200, unallocatedReservationCount: 2 })]) })

    renderWithProviders(<PropertyFinancialSection propertyId={PROPERTY_ID} commissionPercent={20} canManage={false} />)

    expect(screen.getByText(/2 rezervări fără snapshot verificabil \(\s*200,00\sRON\s*\)/)).toBeInTheDocument()
  })

  it("has loading, empty and error states", async () => {
    mockReport({ isLoading: true })
    const { unmount } = renderWithProviders(
      <PropertyFinancialSection propertyId={PROPERTY_ID} commissionPercent={20} canManage={false} />
    )
    expect(screen.getByLabelText("Se încarcă raportul financiar")).toBeInTheDocument()
    unmount()

    mockReport({ data: report([]) })
    const empty = renderWithProviders(
      <PropertyFinancialSection propertyId={PROPERTY_ID} commissionPercent={20} canManage={false} />
    )
    expect(screen.getByText("Nicio încasare în perioada selectată.")).toBeInTheDocument()
    empty.unmount()

    mockReport({ isError: true })
    renderWithProviders(<PropertyFinancialSection propertyId={PROPERTY_ID} commissionPercent={20} canManage={false} />)
    expect(screen.getByRole("alert")).toHaveTextContent("Raportul financiar nu a putut fi încărcat.")
    await userEvent.click(screen.getByRole("button", { name: "Reîncearcă" }))
    expect(refetch).toHaveBeenCalled()
  })

  it("does not query an inverted period", () => {
    mockReport({ data: report([]) })
    renderWithProviders(<PropertyFinancialSection propertyId={PROPERTY_ID} commissionPercent={20} canManage={false} />)

    fireEvent.change(screen.getByLabelText("De la"), { target: { value: "2099-12-31" } })

    expect(screen.getByRole("alert")).toHaveTextContent("Data de început trebuie să fie înaintea datei de sfârșit.")
    expect(vi.mocked(usePropertyCommissionReport).mock.lastCall?.[2]).toBe(false)
  })

  describe("commission editor", () => {
    beforeEach(() => mockReport({ data: report([]) }))

    it("is only shown to administrators", () => {
      renderWithProviders(<PropertyFinancialSection propertyId={PROPERTY_ID} commissionPercent={20} canManage={false} />)
      expect(screen.queryByLabelText("Comision administrare BH Stays (%)")).not.toBeInTheDocument()
    })

    it("saves a valid percentage", async () => {
      renderWithProviders(<PropertyFinancialSection propertyId={PROPERTY_ID} commissionPercent={20} canManage />)

      const input = screen.getByLabelText("Comision administrare BH Stays (%)")
      expect(input).toHaveValue(20)
      await userEvent.clear(input)
      await userEvent.type(input, "17.5")
      await userEvent.click(screen.getByRole("button", { name: "Salvează" }))

      expect(mutate).toHaveBeenCalledWith(17.5)
    })

    it("clears the commission when left empty", async () => {
      renderWithProviders(<PropertyFinancialSection propertyId={PROPERTY_ID} commissionPercent={20} canManage />)

      await userEvent.clear(screen.getByLabelText("Comision administrare BH Stays (%)"))
      await userEvent.click(screen.getByRole("button", { name: "Salvează" }))

      expect(mutate).toHaveBeenCalledWith(null)
    })

    it.each([
      ["150", "Comisionul trebuie să fie între 0 și 100"],
      ["-1", "Comisionul trebuie să fie între 0 și 100"],
      ["12.345", "Maximum două zecimale"],
    ])("rejects %s without calling the API", async (value, message) => {
      renderWithProviders(<PropertyFinancialSection propertyId={PROPERTY_ID} commissionPercent={null} canManage />)

      const input = screen.getByLabelText("Comision administrare BH Stays (%)")
      await userEvent.type(input, value)
      await userEvent.click(screen.getByRole("button", { name: "Salvează" }))

      expect(screen.getByRole("alert")).toHaveTextContent(message)
      expect(input).toHaveAttribute("aria-invalid", "true")
      expect(input.getAttribute("aria-describedby")).toContain(screen.getByRole("alert").id)
      expect(mutate).not.toHaveBeenCalled()
    })
  })
})
