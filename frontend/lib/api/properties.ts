import { apiClient } from "@/lib/api/client"
import type {
  CancellationPolicy,
  Facility,
  IntegrationMode,
  PageResponse,
  PropertyDocumentResponse,
  PropertyDocumentType,
  PropertyResponse,
  PropertyStatus,
  PropertySummaryResponse,
  PropertyType,
  SeasonalRateResponse,
} from "@/lib/api/types"

export interface AddressPayload {
  addressLine: string
  city: string
  county?: string
  postalCode?: string
  country: string
  latitude?: number | null
  longitude?: number | null
}

export interface PropertyPayload {
  name: string
  description?: string
  propertyType: PropertyType
  status?: PropertyStatus
  address: AddressPayload
  showExactAddressPublicly: boolean
  bedrooms: number
  bathrooms: number
  maxGuests: number
  sizeSqm?: number | null
  basePricePerNight?: number | null
  weekendPricePerNight?: number | null
  cleaningFee?: number | null
  extraGuestFee?: number | null
  baseGuestsIncluded?: number | null
  weeklyDiscountPercent?: number | null
  monthlyDiscountPercent?: number | null
  minStayNights?: number | null
  maxStayNights?: number | null
  cancellationPolicy?: CancellationPolicy
  ownerId?: string | null
  commissionPercent?: number | null
  cleaningChecklist?: string[]
  checkInTime?: string
  checkOutTime?: string
  facilities: Facility[]
  smartLockEnabled: boolean
  smartLockProvider?: string
  smartLockDeviceId?: string
  lateCheckoutEnabled: boolean
  lateCheckoutTime?: string | null
  lateCheckoutFee?: number | null
}

export interface SeasonalRatePayload {
  label: string
  startDate: string
  endDate: string
  pricePerNight: number
}

export interface PropertyListParams {
  search?: string
  status?: PropertyStatus
  type?: PropertyType
  page?: number
  size?: number
  sort?: string
}

function buildQuery(params: Record<string, string | number | undefined>) {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== "") {
      query.set(key, String(value))
    }
  })
  const qs = query.toString()
  return qs ? `?${qs}` : ""
}

export const propertiesApi = {
  list: (params: PropertyListParams = {}) =>
    apiClient.get<PageResponse<PropertySummaryResponse>>(
      `/properties${buildQuery({
        search: params.search,
        status: params.status,
        type: params.type,
        page: params.page,
        size: params.size ?? 12,
        sort: params.sort ?? "createdAt,desc",
      })}`
    ),

  get: (id: string) => apiClient.get<PropertyResponse>(`/properties/${id}`),

  create: (payload: PropertyPayload) =>
    apiClient.post<PropertyResponse>("/properties", payload),

  update: (id: string, payload: PropertyPayload & { status: PropertyStatus }) =>
    apiClient.put<PropertyResponse>(`/properties/${id}`, payload),

  delete: (id: string) => apiClient.delete<void>(`/properties/${id}`),

  updateIntegrationMode: (id: string, mode: IntegrationMode) =>
    apiClient.patch<PropertyResponse>(`/properties/${id}/integration-mode`, { mode }),

  /** null clears the commission back to "not configured". */
  updateManagementCommission: (id: string, commissionPercent: number | null) =>
    apiClient.patch<PropertyResponse>(`/properties/${id}/management-commission`, { commissionPercent }),

  uploadPhoto: (id: string, file: File, caption?: string) => {
    const formData = new FormData()
    formData.set("file", file)
    if (caption) formData.set("caption", caption)
    return apiClient.upload(`/properties/${id}/photos`, formData)
  },

  deletePhoto: (id: string, photoId: string) =>
    apiClient.delete<void>(`/properties/${id}/photos/${photoId}`),

  setCoverPhoto: (id: string, photoId: string) =>
    apiClient.patch<void>(`/properties/${id}/photos/${photoId}/cover`),

  uploadDocument: (id: string, file: File, documentType: PropertyDocumentType, expiresAt?: string) => {
    const formData = new FormData()
    formData.set("file", file)
    formData.set("documentType", documentType)
    if (expiresAt) formData.set("expiresAt", expiresAt)
    return apiClient.upload<PropertyDocumentResponse>(`/properties/${id}/documents`, formData)
  },

  deleteDocument: (id: string, documentId: string) =>
    apiClient.delete<void>(`/properties/${id}/documents/${documentId}`),

  listSeasonalRates: (id: string) =>
    apiClient.get<SeasonalRateResponse[]>(`/properties/${id}/seasonal-rates`),

  addSeasonalRate: (id: string, payload: SeasonalRatePayload) =>
    apiClient.post<SeasonalRateResponse>(`/properties/${id}/seasonal-rates`, payload),

  updateSeasonalRate: (id: string, rateId: string, payload: SeasonalRatePayload) =>
    apiClient.put<SeasonalRateResponse>(`/properties/${id}/seasonal-rates/${rateId}`, payload),

  deleteSeasonalRate: (id: string, rateId: string) =>
    apiClient.delete<void>(`/properties/${id}/seasonal-rates/${rateId}`),
}
