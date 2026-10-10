"use client"

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { useRouter } from "next/navigation"
import { toast } from "sonner"
import {
  propertiesApi,
  type PropertyListParams,
  type PropertyPayload,
  type SeasonalRatePayload,
} from "@/lib/api/properties"
import { ApiError, type IntegrationMode, type PropertyDocumentType, type PropertyStatus } from "@/lib/api/types"

function errorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiError) return error.message
  return fallback
}

export function useProperties(params: PropertyListParams) {
  return useQuery({
    queryKey: ["properties", params],
    queryFn: () => propertiesApi.list(params),
  })
}

export function useProperty(id: string) {
  return useQuery({
    queryKey: ["property", id],
    queryFn: () => propertiesApi.get(id),
    enabled: !!id,
  })
}

export function useCreateProperty() {
  const router = useRouter()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (payload: PropertyPayload) => propertiesApi.create(payload),
    onSuccess: (property) => {
      queryClient.invalidateQueries({ queryKey: ["properties"] })
      toast.success("Proprietate creată cu succes")
      router.push(`/dashboard/properties/${property.id}`)
    },
    onError: (error) => {
      toast.error(errorMessage(error, "Crearea proprietății a eșuat"))
    },
  })
}

export function useUpdateProperty(id: string) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (payload: PropertyPayload & { status: PropertyStatus }) =>
      propertiesApi.update(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["properties"] })
      queryClient.invalidateQueries({ queryKey: ["property", id] })
      toast.success("Proprietate actualizată cu succes")
    },
    onError: (error) => {
      toast.error(errorMessage(error, "Actualizarea proprietății a eșuat"))
    },
  })
}

export function useUpdateIntegrationMode(id: string) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (mode: IntegrationMode) => propertiesApi.updateIntegrationMode(id, mode),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["property", id] })
      toast.success("Mod de sincronizare actualizat")
    },
    onError: (error) => {
      toast.error(errorMessage(error, "Actualizarea modului de sincronizare a eșuat"))
    },
  })
}

export function useUpdateManagementCommission(id: string) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (commissionPercent: number | null) => propertiesApi.updateManagementCommission(id, commissionPercent),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["property", id] })
      queryClient.invalidateQueries({ queryKey: ["property-commission-report", id] })
      queryClient.invalidateQueries({ queryKey: ["commission-summary"] })
      toast.success("Comision de administrare actualizat")
    },
    onError: (error) => {
      toast.error(errorMessage(error, "Actualizarea comisionului a eșuat"))
    },
  })
}

export function useDeleteProperty() {
  const router = useRouter()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id: string) => propertiesApi.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["properties"] })
      toast.success("Proprietate ștearsă cu succes")
      router.push("/dashboard/properties")
    },
    onError: (error) => {
      toast.error(errorMessage(error, "Ștergerea proprietății a eșuat"))
    },
  })
}

export function useUploadPropertyPhoto(propertyId: string) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ file, caption }: { file: File; caption?: string }) =>
      propertiesApi.uploadPhoto(propertyId, file, caption),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["property", propertyId] })
      toast.success("Poză adăugată")
    },
    onError: (error) => {
      toast.error(errorMessage(error, "Încărcarea pozei a eșuat"))
    },
  })
}

export function useDeletePropertyPhoto(propertyId: string) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (photoId: string) => propertiesApi.deletePhoto(propertyId, photoId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["property", propertyId] })
      toast.success("Poză ștearsă")
    },
    onError: (error) => {
      toast.error(errorMessage(error, "Ștergerea pozei a eșuat"))
    },
  })
}

export function useSetCoverPhoto(propertyId: string) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (photoId: string) => propertiesApi.setCoverPhoto(propertyId, photoId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["property", propertyId] })
      toast.success("Poza de copertă a fost actualizată")
    },
    onError: (error) => {
      toast.error(errorMessage(error, "Actualizarea copertei a eșuat"))
    },
  })
}

export function useUploadPropertyDocument(propertyId: string) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({
      file,
      documentType,
      expiresAt,
    }: {
      file: File
      documentType: PropertyDocumentType
      expiresAt?: string
    }) => propertiesApi.uploadDocument(propertyId, file, documentType, expiresAt),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["property", propertyId] })
      toast.success("Document adăugat")
    },
    onError: (error) => {
      toast.error(errorMessage(error, "Încărcarea documentului a eșuat"))
    },
  })
}

export function useDeletePropertyDocument(propertyId: string) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (documentId: string) => propertiesApi.deleteDocument(propertyId, documentId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["property", propertyId] })
      toast.success("Document șters")
    },
    onError: (error) => {
      toast.error(errorMessage(error, "Ștergerea documentului a eșuat"))
    },
  })
}

export function useSeasonalRates(propertyId: string) {
  return useQuery({
    queryKey: ["seasonal-rates", propertyId],
    queryFn: () => propertiesApi.listSeasonalRates(propertyId),
    enabled: !!propertyId,
  })
}

export function useAddSeasonalRate(propertyId: string) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (payload: SeasonalRatePayload) => propertiesApi.addSeasonalRate(propertyId, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["seasonal-rates", propertyId] })
      toast.success("Sezon adăugat")
    },
    onError: (error) => {
      toast.error(errorMessage(error, "Adăugarea sezonului a eșuat"))
    },
  })
}

export function useUpdateSeasonalRate(propertyId: string) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ rateId, payload }: { rateId: string; payload: SeasonalRatePayload }) =>
      propertiesApi.updateSeasonalRate(propertyId, rateId, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["seasonal-rates", propertyId] })
      toast.success("Sezon actualizat")
    },
    onError: (error) => {
      toast.error(errorMessage(error, "Actualizarea sezonului a eșuat"))
    },
  })
}

export function useDeleteSeasonalRate(propertyId: string) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (rateId: string) => propertiesApi.deleteSeasonalRate(propertyId, rateId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["seasonal-rates", propertyId] })
      toast.success("Sezon șters")
    },
    onError: (error) => {
      toast.error(errorMessage(error, "Ștergerea sezonului a eșuat"))
    },
  })
}
