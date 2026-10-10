import { apiClient } from "@/lib/api/client"
import type {
  CommissionSummaryResponse,
  FinancialReportSummaryResponse,
  PropertyCommissionReportResponse,
} from "@/lib/api/types"

export interface ReportPeriod {
  from?: string
  to?: string
}

export interface FinancialReportParams {
  propertyId?: string
  from?: string
  to?: string
}

function buildQuery(params: Record<string, string | undefined>) {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value) query.set(key, value)
  })
  const qs = query.toString()
  return qs ? `?${qs}` : ""
}

export const financialReportsApi = {
  summary: (params: FinancialReportParams = {}) =>
    apiClient.get<FinancialReportSummaryResponse>(
      `/reports/financial/summary${buildQuery({ propertyId: params.propertyId, from: params.from, to: params.to })}`
    ),

  propertyCommission: (propertyId: string, period: ReportPeriod = {}) =>
    apiClient.get<PropertyCommissionReportResponse>(
      `/reports/financial/properties/${propertyId}/commission${buildQuery({ from: period.from, to: period.to })}`
    ),

  commissionSummary: (period: ReportPeriod = {}) =>
    apiClient.get<CommissionSummaryResponse>(
      `/reports/financial/commission-summary${buildQuery({ from: period.from, to: period.to })}`
    ),
}
