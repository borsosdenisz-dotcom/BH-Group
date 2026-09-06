"use client"

import Link from "next/link"
import { usePathname } from "next/navigation"
import { Building2 } from "lucide-react"
import { cn } from "@/lib/utils"
import { useCurrentUser } from "@/hooks/use-current-user"
import { DASHBOARD_NAV_ITEMS } from "@/lib/dashboard-nav"

export function DashboardSidebar() {
  const pathname = usePathname()
  const { data: user } = useCurrentUser()

  const visibleItems = DASHBOARD_NAV_ITEMS.filter(
    (item) => !item.roles || (user && item.roles.includes(user.role))
  )

  return (
    <aside className="hidden w-60 shrink-0 border-r border-border/60 bg-background md:flex md:flex-col">
      <div className="flex h-16 items-center gap-2 px-6 font-semibold tracking-tight">
        <span className="flex size-7 items-center justify-center rounded-lg bg-primary text-primary-foreground">
          <Building2 className="size-3.5" />
        </span>
        BH Group PMS
      </div>
      <nav className="flex flex-1 flex-col gap-1 px-3 py-2">
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
                "flex items-center gap-2.5 rounded-lg px-3 py-2 text-sm font-medium transition-colors",
                isActive
                  ? "bg-muted text-foreground"
                  : "text-muted-foreground hover:bg-muted/60 hover:text-foreground"
              )}
            >
              <Icon className="size-4" />
              {item.label}
            </Link>
          )
        })}
      </nav>
    </aside>
  )
}
