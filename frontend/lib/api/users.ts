import { apiClient } from "@/lib/api/client"
import type { PageResponse, Role, UserResponse, UserStatus } from "@/lib/api/types"

export interface UserCreatePayload {
  firstName: string
  lastName: string
  email: string
  phone?: string
  role: Role
}

export interface UserUpdatePayload {
  firstName: string
  lastName: string
  phone?: string
  role: Role
}

export interface UserListParams {
  search?: string
  role?: Role
  status?: UserStatus
  page?: number
  size?: number
}

export const usersApi = {
  list: (params: UserListParams = {}) => {
    const query = new URLSearchParams()
    if (params.search) query.set("search", params.search)
    if (params.role) query.set("role", params.role)
    if (params.status) query.set("status", params.status)
    query.set("page", String(params.page ?? 0))
    query.set("size", String(params.size ?? 20))
    query.set("sort", "createdAt,desc")
    return apiClient.get<PageResponse<UserResponse>>(`/users?${query.toString()}`)
  },

  create: (payload: UserCreatePayload) => apiClient.post<UserResponse>("/users", payload),

  update: (id: string, payload: UserUpdatePayload) =>
    apiClient.put<UserResponse>(`/users/${id}`, payload),

  updateStatus: (id: string, status: UserStatus, confirmEmail?: string) =>
    apiClient.patch<UserResponse>(`/users/${id}/status`, { status, confirmEmail }),

  resendInvite: (id: string) => apiClient.post<UserResponse>(`/users/${id}/resend-invite`),

  resetMfa: (id: string) => apiClient.post<void>(`/users/${id}/reset-mfa`),

  delete: (id: string) => apiClient.delete<void>(`/users/${id}`),
}
