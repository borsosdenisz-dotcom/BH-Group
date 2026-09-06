import type { LucideIcon } from "lucide-react"
import { cn } from "@/lib/utils"
import { Card } from "@/components/ui/card"

interface StatCardProps {
  label: string
  value: React.ReactNode
  icon: LucideIcon
  helper?: React.ReactNode
  tone?: "default" | "primary" | "success" | "warning"
  className?: string
}

const TONES = {
  default: "bg-card text-foreground",
  primary: "border-primary/25 bg-primary/6 text-foreground",
  success: "border-success/25 bg-success/6 text-foreground",
  warning: "border-amber-700/25 bg-amber-700/6 text-foreground",
}

const ICON_TONES = {
  default: "bg-muted text-muted-foreground",
  primary: "bg-primary/12 text-primary",
  success: "bg-success/12 text-success",
  warning: "bg-amber-700/12 text-amber-800 dark:text-amber-300",
}

export function StatCard({ label, value, icon: Icon, helper, tone = "default", className }: StatCardProps) {
  return (
    <Card className={cn("gap-0 p-5", TONES[tone], className)}>
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <p className="text-xs font-semibold uppercase tracking-[0.12em] text-muted-foreground">{label}</p>
          <div className="tabular-nums mt-3 text-2xl font-semibold tracking-tight">{value}</div>
          {helper && <div className="mt-2 text-xs text-muted-foreground">{helper}</div>}
        </div>
        <span className={cn("flex size-10 shrink-0 items-center justify-center rounded-md", ICON_TONES[tone])}>
          <Icon className="size-5" aria-hidden="true" />
        </span>
      </div>
    </Card>
  )
}
