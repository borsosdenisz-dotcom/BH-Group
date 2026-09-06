import Link from "next/link"
import { ArrowRight, Search } from "lucide-react"
import { buttonVariants } from "@/components/ui/button"
import { cn } from "@/lib/utils"

export function CtaSection() {
  return (
    <section className="mx-auto w-full max-w-7xl px-5 pb-[var(--space-section)] sm:px-8 lg:px-10">
      <div className="grid overflow-hidden border border-primary/20 bg-sand/70 lg:grid-cols-[1fr_auto] lg:items-center">
        <div className="p-7 sm:p-10 lg:p-12">
          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-primary">Următorul pas</p>
          <h2 className="mt-3 max-w-2xl text-balance font-heading text-3xl font-semibold tracking-tight sm:text-4xl">Caută un sejur sau discută despre administrarea proprietății tale.</h2>
          <p className="mt-4 max-w-2xl text-sm leading-6 text-muted-foreground">Două experiențe distincte, aceeași abordare: informații clare și contact direct cu echipa BH Group.</p>
        </div>
        <div className="flex flex-col gap-3 border-t border-primary/15 p-7 lg:w-72 lg:border-l lg:border-t-0 lg:p-10">
          <Link href="/book" className={cn(buttonVariants({ size: "lg" }), "w-full")}><Search className="size-4" />Caută cazare</Link>
          <Link href="/pentru-proprietari" className={cn(buttonVariants({ size: "lg", variant: "outline" }), "w-full")}>Pentru proprietari <ArrowRight className="size-4" /></Link>
        </div>
      </div>
    </section>
  )
}
