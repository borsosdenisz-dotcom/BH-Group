import Image from "next/image"
import Link from "next/link"
import { ArrowRight, Check, Search } from "lucide-react"
import { HomeSearchBar } from "@/components/marketing/home-search-bar"
import { buttonVariants } from "@/components/ui/button"
import { cn } from "@/lib/utils"

const TRUST_POINTS = ["Fără cont", "Preț detaliat", "Confirmare din partea echipei"]

export function HeroSection() {
  return (
    <section className="relative flex min-h-[min(900px,100svh)] items-end overflow-hidden bg-navy text-white">
      <Image
        src="/images/hospitality-hero.jpg"
        alt="Interior luminos pregătit pentru oaspeți"
        fill
        sizes="100vw"
        priority
        className="object-cover object-center"
      />
      <div className="absolute inset-0 bg-[linear-gradient(90deg,rgba(12,24,42,.94)_0%,rgba(12,24,42,.72)_48%,rgba(12,24,42,.2)_100%)]" />
      <div className="absolute inset-x-0 bottom-0 h-48 bg-gradient-to-t from-navy/80 to-transparent" />

      <div className="relative z-10 mx-auto w-full max-w-7xl px-5 pb-8 pt-32 sm:px-8 sm:pb-12 lg:px-10 lg:pb-16">
        <div className="max-w-3xl">
          <p className="mb-5 text-xs font-semibold uppercase tracking-[0.2em] text-white/75">
            Rezervări directe · România
          </p>
          <h1 className="text-balance font-heading text-[clamp(2.8rem,7vw,5.8rem)] font-semibold leading-[0.98] tracking-[-0.04em]">
            Sejururi atent pregătite, rezervate direct.
          </h1>
          <p className="mt-6 max-w-2xl text-pretty text-base leading-7 text-white/82 sm:text-lg">
            Descoperă proprietățile publicate de BH Group, verifică perioada și trimite o cerere de rezervare direct echipei care administrează sejurul.
          </p>
          <div className="mt-7 flex flex-col gap-3 sm:flex-row">
            <Link href="/book" className={cn(buttonVariants({ size: "lg" }), "gap-2")}>
              <Search className="size-4" />
              Caută proprietăți
            </Link>
            <Link
              href="/pentru-proprietari"
              className={cn(buttonVariants({ size: "lg", variant: "outline" }), "border-white/35 bg-white/8 text-white hover:border-white/55 hover:bg-white/14 hover:text-white")}
            >
              Administrează-ți proprietatea
              <ArrowRight className="size-4" />
            </Link>
          </div>
          <ul className="mt-7 flex flex-wrap gap-x-6 gap-y-2 text-sm text-white/72" aria-label="Detalii despre rezervare">
            {TRUST_POINTS.map((item) => (
              <li key={item} className="flex items-center gap-2">
                <Check className="size-4 text-brass" aria-hidden="true" />
                {item}
              </li>
            ))}
          </ul>
        </div>

        <HomeSearchBar />
      </div>
    </section>
  )
}
