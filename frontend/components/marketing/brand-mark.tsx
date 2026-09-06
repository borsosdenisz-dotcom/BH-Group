import { Building2 } from "lucide-react"
import { cn } from "@/lib/utils"

export function BrandMark({ inverse = false, compact = false }: { inverse?: boolean; compact?: boolean }) {
  return (
    <span className={cn("inline-flex items-center gap-2.5", inverse ? "text-white" : "text-foreground")}>
      <span
        className={cn(
          "flex size-9 items-center justify-center rounded-md border",
          inverse
            ? "border-white/20 bg-white/10 text-white"
            : "border-primary/20 bg-primary text-primary-foreground"
        )}
      >
        <Building2 className="size-4" aria-hidden="true" />
      </span>
      {!compact && <span className="font-heading text-lg font-semibold tracking-tight">BH Group</span>}
    </span>
  )
}
