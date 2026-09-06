import Link from "next/link"
import { buttonVariants } from "@/components/ui/button"
import { cn } from "@/lib/utils"

export function MobileOwnerCta() {
  return <div className="fixed inset-x-4 bottom-4 z-30 lg:hidden" style={{ bottom: "max(1rem, env(safe-area-inset-bottom))" }}><Link href="#formular" className={cn(buttonVariants({ size: "lg" }), "w-full shadow-[var(--shadow-md)]")}>Solicită o discuție</Link></div>
}
