import { CreditCard } from "lucide-react"
import { siteConfig } from "@/lib/site-config"
import { cn } from "@/lib/utils"

/**
 * Public booking is card-only: when online card payment is not available
 * there is no way to book online at all, so say so plainly and point to the
 * existing contact details instead of offering any other way to pay.
 */
export function OnlineBookingUnavailable({ className }: { className?: string }) {
  return (
    <div
      role="status"
      className={cn("flex flex-col gap-2 rounded-lg bg-muted/60 px-4 py-3 text-sm text-muted-foreground", className)}
    >
      <p className="flex items-center gap-2 font-medium text-foreground">
        <CreditCard className="size-4 shrink-0" />
        Rezervarea online nu este momentan disponibilă.
      </p>
      <p>
        {siteConfig.companyEmail || siteConfig.companyPhone ? (
          <>
            Te rugăm să ne contactezi
            {siteConfig.companyPhone && (
              <>
                {" "}
                la{" "}
                <a href={`tel:${siteConfig.companyPhone}`} className="text-foreground underline">
                  {siteConfig.companyPhone}
                </a>
              </>
            )}
            {siteConfig.companyEmail && (
              <>
                {siteConfig.companyPhone ? " sau" : ""} la{" "}
                <a href={`mailto:${siteConfig.companyEmail}`} className="text-foreground underline">
                  {siteConfig.companyEmail}
                </a>
              </>
            )}
            .
          </>
        ) : (
          <>
            Te rugăm să ne contactezi prin{" "}
            <a href="/pentru-proprietari#formular" className="text-foreground underline">
              formularul de contact
            </a>
            .
          </>
        )}
      </p>
    </div>
  )
}
