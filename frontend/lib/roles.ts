import type { Role, UserStatus } from "@/lib/api/types"

export const ROLE_LABELS: Record<Role, string> = {
  SUPER_ADMIN: "Super Admin",
  ADMINISTRATOR: "Administrator",
  OWNER: "Proprietar",
  CLEANER: "Cleaner",
  MAINTENANCE: "Mentenanță",
  ACCOUNTANT: "Contabil",
  SUPPORT_AGENT: "Agent suport",
}

export const ALL_ROLES: Role[] = [
  "SUPER_ADMIN",
  "ADMINISTRATOR",
  "OWNER",
  "CLEANER",
  "MAINTENANCE",
  "ACCOUNTANT",
  "SUPPORT_AGENT",
]

export const USER_STATUS_LABELS: Record<UserStatus, string> = {
  PENDING: "În așteptare",
  ACTIVE: "Activ",
  SUSPENDED: "Suspendat",
  DISABLED: "Dezactivat",
}

export const ALL_USER_STATUSES: UserStatus[] = [
  "PENDING",
  "ACTIVE",
  "SUSPENDED",
  "DISABLED",
]

export const USER_STATUS_BADGE_VARIANT: Record<
  UserStatus,
  "default" | "secondary" | "outline" | "destructive"
> = {
  PENDING: "outline",
  ACTIVE: "default",
  SUSPENDED: "destructive",
  DISABLED: "secondary",
}

const RESTRICTED_ROLES: Role[] = ["SUPER_ADMIN", "ADMINISTRATOR"]

export function canAssignRole(actingRole: Role | undefined, targetRole: Role): boolean {
  if (!actingRole) return false
  if (RESTRICTED_ROLES.includes(targetRole)) {
    return actingRole === "SUPER_ADMIN"
  }
  return true
}
