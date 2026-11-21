"use client"

import { BadgeCheck, CalendarClock, ListChecks, MessageCircle, TrendingUp, Wallet } from "lucide-react"
import { Reveal, RevealGroup, RevealItem } from "@/components/marketing/reveal"

const SERVICES = [
  {
    icon: ListChecks,
    number: "01",
    title: "Administrarea anunțurilor Airbnb și Booking.com",
    description:
      "Pregătim conținutul și administrăm anunțurile pe canalele stabilite împreună, fără a promite afiliere oficială cu platformele.",
    color: "bg-blue-500/10 text-blue-600 dark:text-blue-400",
  },
  {
    icon: MessageCircle,
    number: "02",
    title: "Comunicare cu oaspeții",
    description:
      "Gestionăm întrebările și informațiile necesare sejurului, în limitele serviciilor agreate pentru proprietate.",
    color: "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400",
  },
  {
    icon: TrendingUp,
    number: "03",
    title: "Prețuri adaptate sezonului",
    description:
      "Analizăm contextul proprietății și ajustăm tarifele în funcție de perioadă, fără garanții de venit sau ocupare.",
    color: "bg-violet-500/10 text-violet-600 dark:text-violet-400",
  },
  {
    icon: CalendarClock,
    number: "04",
    title: "Coordonare check-in și check-out",
    description:
      "Organizăm sosirea și plecarea oaspeților conform regulilor și accesului disponibil la fiecare proprietate.",
    color: "bg-amber-500/10 text-amber-600 dark:text-amber-400",
  },
  {
    icon: BadgeCheck,
    number: "05",
    title: "Curățenie și verificarea proprietății",
    description:
      "Coordonăm curățenia și verificăm starea proprietății între sejururi pe baza procesului stabilit la onboarding.",
    color: "bg-rose-500/10 text-rose-600 dark:text-rose-400",
  },
  {
    icon: Wallet,
    number: "06",
    title: "Rapoarte financiare transparente",
    description:
      "Centralizăm veniturile și costurile în rapoarte care te ajută să urmărești rezultatele proprietății.",
    color: "bg-violet-500/10 text-violet-600 dark:text-violet-400",
  },
]

export function ServicesSection() {
  return (
    <section className="mx-auto max-w-6xl px-6 py-24 sm:px-10 sm:py-32">
      <Reveal className="max-w-2xl">
        <span className="text-sm font-medium text-primary">Ce facem</span>
        <h2 className="mt-3 text-balance font-heading text-3xl font-semibold tracking-tight sm:text-4xl">
          Administrare completă, de la primul oaspete
        </h2>
        <p className="mt-4 text-balance text-muted-foreground">
          Preluăm toată operațiunea proprietății tale — tu te bucuri de venit, noi ne ocupăm de restul.
        </p>
      </Reveal>

      <RevealGroup stagger={0.08} className="mt-14 grid gap-px overflow-hidden rounded-2xl border border-border/60 bg-border/60 sm:grid-cols-2 lg:grid-cols-3">
        {SERVICES.map((service) => (
          <RevealItem key={service.number} className="group relative bg-card p-8 transition-colors hover:bg-muted/40">
            <span className="text-xs font-medium text-muted-foreground/60">{service.number}</span>
            <div className={`mt-4 flex size-11 items-center justify-center rounded-xl transition-transform duration-500 group-hover:scale-110 ${service.color}`}>
              <service.icon className="size-5" />
            </div>
            <h3 className="mt-5 text-lg font-medium">{service.title}</h3>
            <p className="mt-2 text-sm text-muted-foreground">{service.description}</p>
          </RevealItem>
        ))}
      </RevealGroup>
    </section>
  )
}
