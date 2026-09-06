import Image from "next/image"
import Link from "next/link"
import { ArrowDown, CheckCircle2 } from "lucide-react"
import { buttonVariants } from "@/components/ui/button"
import { cn } from "@/lib/utils"

const AREAS = ["Operațiuni coordonate", "Comunicare cu oaspeții", "Vizibilitate financiară"]

export function PentruProprietariHero() {
  return (
    <section className="relative flex min-h-[82svh] items-end overflow-hidden bg-navy text-white">
      <Image src="/images/owner-hero.jpg" alt="Interior pregătit pentru administrarea unui sejur" fill sizes="100vw" priority className="object-cover" />
      <div className="absolute inset-0 bg-[linear-gradient(90deg,rgba(12,24,42,.94)_0%,rgba(12,24,42,.72)_52%,rgba(12,24,42,.18)_100%)]" />
      <div className="relative z-10 mx-auto w-full max-w-7xl px-5 pb-14 pt-32 sm:px-8 lg:px-10 lg:pb-20">
        <p className="text-xs font-semibold uppercase tracking-[0.2em] text-brass">Pentru proprietari</p>
        <h1 className="mt-5 max-w-4xl text-balance font-heading text-[clamp(2.8rem,6vw,5.4rem)] font-semibold leading-[1] tracking-[-0.04em]">Administrare atentă, cu informațiile la vedere.</h1>
        <p className="mt-6 max-w-2xl text-pretty text-base leading-7 text-white/78 sm:text-lg">BH Group coordonează activitatea proprietății și îți oferă acces la informațiile operaționale și financiare disponibile în portal.</p>
        <div className="mt-8 flex flex-col gap-3 sm:flex-row"><Link href="#formular" className={cn(buttonVariants({ size: "lg" }), "gap-2")}>Solicită o discuție <ArrowDown className="size-4" /></Link><Link href="#portal" className={cn(buttonVariants({ size: "lg", variant: "outline" }), "border-white/35 bg-white/8 text-white hover:border-white/55 hover:bg-white/14 hover:text-white")}>Descoperă portalul</Link></div>
        <ul className="mt-8 flex flex-wrap gap-x-7 gap-y-2 text-sm text-white/70">{AREAS.map((area) => <li key={area} className="flex items-center gap-2"><CheckCircle2 className="size-4 text-brass" />{area}</li>)}</ul>
      </div>
    </section>
  )
}
