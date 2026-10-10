import { describe, expect, it } from "vitest"
import { commissionPercentSchema, currentMonthRange, formatMoney } from "./commission"

describe("commissionPercentSchema", () => {
  it("treats an empty value as not configured, not as 0%", () => {
    expect(commissionPercentSchema.parse("")).toBeUndefined()
    expect(commissionPercentSchema.parse(undefined)).toBeUndefined()
    expect(commissionPercentSchema.parse(null)).toBeUndefined()
  })

  it("accepts the bounds and up to two decimals", () => {
    expect(commissionPercentSchema.parse("0")).toBe(0)
    expect(commissionPercentSchema.parse("100")).toBe(100)
    expect(commissionPercentSchema.parse("17.5")).toBe(17.5)
    expect(commissionPercentSchema.parse("12.25")).toBe(12.25)
  })

  it("rejects values below 0, above 100 or with more than two decimals", () => {
    expect(commissionPercentSchema.safeParse("-0.01").success).toBe(false)
    expect(commissionPercentSchema.safeParse("100.01").success).toBe(false)
    expect(commissionPercentSchema.safeParse("12.345").success).toBe(false)
    expect(commissionPercentSchema.safeParse("abc").success).toBe(false)
  })
})

describe("currentMonthRange", () => {
  it("spans the whole local calendar month", () => {
    expect(currentMonthRange(new Date(2026, 1, 14))).toEqual({ from: "2026-02-01", to: "2026-02-28" })
    expect(currentMonthRange(new Date(2028, 1, 1))).toEqual({ from: "2028-02-01", to: "2028-02-29" })
    expect(currentMonthRange(new Date(2026, 11, 31))).toEqual({ from: "2026-12-01", to: "2026-12-31" })
  })
})

describe("formatMoney", () => {
  it("always shows two decimals and the currency", () => {
    expect(formatMoney(80, "RON")).toMatch(/^80,00\sRON$/)
    expect(formatMoney(1234.5, "EUR")).toMatch(/^1\.234,50\sEUR$/)
  })
})
