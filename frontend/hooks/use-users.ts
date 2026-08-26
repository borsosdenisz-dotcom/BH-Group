"use client"

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { toast } from "sonner"
import {
  usersApi,
  type UserCreatePayload,
  type UserListParams,
  type UserUpdatePayload,
} from "@/lib/api/users"
import { ApiError, type UserStatus } from "@/lib/api/types"

function errorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiError) return error.message
  return fallback
}

export function useUsers(params: UserListParams = {}) {
  return useQuery({
    queryKey: ["users", params],
    queryFn: () => usersApi.list(params),
  })
}

export function useCreateUser() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (payload: UserCreatePayload) => usersApi.create(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["users"] })
      toast.success("Invitație trimisă. Colegul tău își activează contul din emailul primit.")
    },
    onError: (error) => {
      toast.error(errorMessage(error, "Nu am putut crea contul. Încearcă din nou."))
    },
  })
}

export function useResendInvite() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id: string) => usersApi.resendInvite(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["users"] })
      toast.success("Invitație retrimisă.")
    },
    onError: (error) => {
      toast.error(errorMessage(error, "Nu am putut retrimite invitația."))
    },
  })
}

export function useUpdateUser() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: UserUpdatePayload }) =>
      usersApi.update(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["users"] })
      toast.success("Cont actualizat.")
    },
    onError: (error) => {
      toast.error(errorMessage(error, "Nu am putut actualiza contul."))
    },
  })
}

export function useUpdateUserStatus() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ id, status, confirmEmail }: { id: string; status: UserStatus; confirmEmail?: string }) =>
      usersApi.updateStatus(id, status, confirmEmail),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["users"] })
      toast.success("Status actualizat.")
    },
    onError: (error) => {
      toast.error(errorMessage(error, "Nu am putut actualiza statusul."))
    },
  })
}

export function useDeleteUser() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id: string) => usersApi.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["users"] })
      toast.success("Cont șters definitiv.")
    },
    onError: (error) => {
      toast.error(errorMessage(error, "Nu am putut șterge contul."))
    },
  })
}

export function useResetUserMfa() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id: string) => usersApi.resetMfa(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["users"] })
      toast.success("2FA resetat. Utilizatorul îl va configura din nou la următoarea autentificare.")
    },
    onError: (error) => {
      toast.error(errorMessage(error, "Nu am putut reseta 2FA."))
    },
  })
}
