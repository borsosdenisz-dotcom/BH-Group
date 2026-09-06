"use client"

import { MessageCircle, Sparkles, TrendingUp, Wallet } from "lucide-react"
import { Reveal, RevealGroup, RevealItem } from "@/components/marketing/reveal"

const SERVICES = [
  {
    icon: TrendingUp,
    number: "01",
    title: "Prețuri adaptate sezonului",
    description:
      "Stabilim tarife diferite pe weekend și sezoane, ca proprietatea ta să rămână competitivă pe parcursul anului.",
    color: "bg-blue-500/10 text-blue-600 dark:text-blue-400",
  },
  {
    icon: Sparkles,
    number: "02",
    title: "Curățenie gestionată pentru fiecare sejur",
    description:
      "Fiecare sejur are o sarcină de curățenie alocată și urmărită în sistem, cu protocoale de igienă profesionale.",
    color: "bg-amber-500/10 text-amber-600 dark:text-amber-400",
  },
  {
    icon: MessageCircle,
    number: "03",
    title: "Comunicare directă cu oaspeții",
    description:
      "Răspundem întrebărilor, gestionăm check-in/check-out și rezolvăm orice situație — în română și engleză.",
    color: "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400",
  },
  {
    icon: Wallet,
    number: "04",
    title: "Rapoarte financiare transparente",
    description:
      "Vezi în orice moment veniturile, cheltuielile și ocuparea proprietății tale, fără să ceri nimic nimănui.",
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

      <RevealGroup stagger={0.1} className="mt-14 grid gap-px overflow-hidden rounded-2xl border border-border/60 bg-border/60 sm:grid-cols-2">
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
