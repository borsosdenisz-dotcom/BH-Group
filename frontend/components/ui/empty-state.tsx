import type { LucideIcon } from "lucide-react"
import type { ReactNode } from "react"
import { cn } from "@/lib/utils"

interface EmptyStateProps {
  icon: LucideIcon
  title: string
  description?: string
  action?: ReactNode
  className?: string
}

export function EmptyState({ icon: Icon, title, description, action, className }: EmptyStateProps) {
  return (
    <div
      className={cn(
        "flex min-h-56 w-full flex-col items-center justify-center border border-dashed border-border bg-card/55 px-5 py-12 text-center",
        className
      )}
    >
      <span className="mb-5 flex size-12 items-center justify-center rounded-full border border-primary/20 bg-primary/8 text-primary">
        <Icon className="size-5" aria-hidden="true" />
      </span>
      <h2 className="font-heading text-xl font-semibold">{title}</h2>
      {description && <p className="mt-2 max-w-md text-sm leading-6 text-muted-foreground">{description}</p>}
      {action && <div className="mt-6">{action}</div>}
    </div>
  )
}
