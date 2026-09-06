import { Badge } from "@/components/ui/badge"
import { cn } from "@/lib/utils"

const TONES = {
  neutral: "border-border bg-muted text-muted-foreground",
  info: "border-primary/20 bg-primary/10 text-primary",
  success: "border-success/20 bg-success/10 text-success",
  warning: "border-amber-700/20 bg-amber-700/10 text-amber-800 dark:text-amber-300",
  destructive: "border-destructive/20 bg-destructive/10 text-destructive",
}

export function StatusBadge({
  label,
  tone = "neutral",
  className,
}: {
  label: string
  tone?: keyof typeof TONES
  className?: string
}) {
  return (
    <Badge variant="outline" className={cn("gap-1.5", TONES[tone], className)}>
      <span className="size-1.5 rounded-full bg-current" aria-hidden="true" />
      {label}
    </Badge>
  )
}
