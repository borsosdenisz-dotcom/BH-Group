import { Info } from "lucide-react"
import { cn } from "@/lib/utils"

export const DEMO_BANNER_TITLE = "Anunț demonstrativ – nu se poate rezerva"

/**
 * Shown at the top of a demo (showcase) property. The backend refuses every
 * quote, booking and payment for it; this only says so before anyone tries.
 */
export function DemoPropertyBanner({ className }: { className?: string }) {
  return (
    <div
      role="status"
      className={cn(
        "flex items-start gap-3 rounded-xl border border-amber-300/70 bg-amber-50 px-4 py-3 text-sm text-amber-900",
        "dark:border-amber-400/30 dark:bg-amber-400/10 dark:text-amber-100",
        className
      )}
    >
      <Info className="mt-0.5 size-4 shrink-0" />
      <div className="flex flex-col gap-0.5">
        <p className="font-medium">{DEMO_BANNER_TITLE}</p>
        <p className="text-amber-800/90 dark:text-amber-100/80">
          Această proprietate este prezentată doar ca exemplu. Prețul și calendarul sunt orientative și nu se
          pot face rezervări sau plăți pentru ea.
        </p>
      </div>
    </div>
  )
}

/** Small "Demo" label for property cards in listings and search results. */
export function DemoBadge({ className }: { className?: string }) {
  return (
    <span
      className={cn(
        "rounded-full bg-amber-400 px-2.5 py-1 text-[11px] font-semibold uppercase tracking-wide text-amber-950 shadow-sm",
        className
      )}
    >
      Demo
    </span>
  )
}
