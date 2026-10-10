"use client"

import { useQuery } from "@tanstack/react-query"
import {
  financialReportsApi,
  type FinancialReportParams,
  type ReportPeriod,
} from "@/lib/api/financial-reports"

export function useFinancialReport(params: FinancialReportParams) {
  return useQuery({
    queryKey: ["financial-report", params],
    queryFn: () => financialReportsApi.summary(params),
  })
}

export function usePropertyCommissionReport(propertyId: string, period: ReportPeriod, enabled = true) {
  return useQuery({
    queryKey: ["property-commission-report", propertyId, period],
    queryFn: () => financialReportsApi.propertyCommission(propertyId, period),
    enabled,
  })
}

export function useCommissionSummary(period: ReportPeriod, enabled = true) {
  return useQuery({
    queryKey: ["commission-summary", period],
    queryFn: () => financialReportsApi.commissionSummary(period),
    enabled,
  })
}
