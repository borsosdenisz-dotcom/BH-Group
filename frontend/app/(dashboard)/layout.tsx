import { DashboardSidebar } from "@/components/layout/dashboard-sidebar"
import { DashboardTopbar } from "@/components/layout/dashboard-topbar"
import { RouteRoleGuard } from "@/components/layout/route-role-guard"

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <div className="flex min-h-screen min-w-0 bg-muted/30">
      <RouteRoleGuard />
      <DashboardSidebar />
      <div className="flex min-w-0 flex-1 flex-col">
        <DashboardTopbar />
        <main className="min-w-0 flex-1 px-4 py-6 sm:px-6 md:py-8 xl:px-10">{children}</main>
      </div>
    </div>
  )
}
