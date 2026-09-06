"use client"

import { MessageCircle, Sparkles, TrendingUp, Wallet } from "lucide-react"
import { Reveal, RevealGroup, RevealItem } from "@/components/marketing/reveal"
import { SectionHeader } from "@/components/ui/page-header"

const SERVICES = [
  {
    icon: TrendingUp,
    number: "01",
    title: "Prețuri adaptate sezonului",
    description:
      "Stabilim tarife diferite pe weekend și sezoane, ca proprietatea ta să rămână competitivă pe parcursul anului.",
  },
  {
    icon: Sparkles,
    number: "02",
    title: "Curățenie gestionată pentru fiecare sejur",
    description:
      "Fiecare sejur are o sarcină de curățenie alocată și urmărită în sistem, cu protocoale de igienă profesionale.",
  },
  {
    icon: MessageCircle,
    number: "03",
    title: "Comunicare directă cu oaspeții",
    description:
      "Răspundem întrebărilor, gestionăm check-in/check-out și rezolvăm orice situație — în română și engleză.",
  },
  {
    icon: Wallet,
    number: "04",
    title: "Rapoarte financiare transparente",
    description:
      "Vezi în orice moment veniturile, cheltuielile și ocuparea proprietății tale, fără să ceri nimic nimănui.",
  },
]

export function ServicesSection() {
  return (
    <section className="mx-auto max-w-7xl px-5 py-[var(--space-section)] sm:px-8 lg:px-10">
      <Reveal><SectionHeader eyebrow="Ce coordonăm" title="Serviciile importante, explicate fără promisiuni vagi." description="Activitățile sunt organizate în jurul listării, pregătirii sejururilor, comunicării și raportării." /></Reveal>

      <RevealGroup stagger={0.08} className="mt-12 grid gap-px overflow-hidden border border-border bg-border sm:grid-cols-2">
        {SERVICES.map((service) => (
          <RevealItem key={service.number} className="group relative bg-card p-7 transition-colors hover:bg-accent/35 sm:p-8">
            <span className="text-xs font-medium text-muted-foreground/60">{service.number}</span>
            <div className="mt-4 flex size-11 items-center justify-center rounded-md bg-primary/10 text-primary">
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
