"use client"

import Link from "next/link"
import { usePathname } from "next/navigation"
import { cn } from "@/lib/utils"
import { useCurrentUser } from "@/hooks/use-current-user"
import { DASHBOARD_NAV_ITEMS } from "@/lib/dashboard-nav"
import { BrandMark } from "@/components/marketing/brand-mark"

export function DashboardSidebar() {
  const pathname = usePathname()
  const { data: user } = useCurrentUser()

  const visibleItems = DASHBOARD_NAV_ITEMS.filter(
    (item) => !item.roles || (user && item.roles.includes(user.role))
  )

  return (
    <aside className="sticky top-0 hidden h-screen w-68 shrink-0 border-r border-sidebar-border bg-sidebar text-sidebar-foreground md:flex md:flex-col">
      <div className="flex h-20 flex-col justify-center border-b border-sidebar-border px-6">
        <BrandMark inverse />
        <span className="mt-1 pl-[2.9rem] text-[10px] font-semibold uppercase tracking-[0.16em] text-sidebar-foreground/45">Administrare</span>
      </div>
      <nav aria-label="Navigație dashboard" className="flex flex-1 flex-col gap-1 overflow-y-auto px-3 py-5">
        {visibleItems.map((item) => {
          const isActive =
            item.href === "/dashboard"
              ? pathname === item.href
              : pathname.startsWith(item.href)
          const Icon = item.icon
          return (
            <Link
              key={item.href}
              href={item.href}
              className={cn(
                "relative flex min-h-11 items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors before:absolute before:inset-y-2 before:left-0 before:w-0.5 before:bg-sidebar-primary before:opacity-0",
                isActive
                  ? "bg-sidebar-accent text-sidebar-accent-foreground before:opacity-100"
                  : "text-sidebar-foreground/62 hover:bg-sidebar-accent/65 hover:text-sidebar-accent-foreground"
              )}
            >
              <Icon className={cn("size-4", isActive && "text-sidebar-primary")} />
              {item.label}
            </Link>
          )
        })}
      </nav>
    </aside>
  )
}
