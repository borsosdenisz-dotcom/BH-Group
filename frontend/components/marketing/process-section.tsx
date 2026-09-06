"use client"

import { motion, useReducedMotion } from "motion/react"
import { CalendarCheck, ClipboardList, Handshake, FileCheck2 } from "lucide-react"
import { Reveal } from "@/components/marketing/reveal"

const STEPS = [
  {
    icon: Handshake,
    title: "Discuție & evaluare",
    description: "Discutăm despre proprietate, obiective și serviciile potrivite situației tale.",
  },
  {
    icon: ClipboardList,
    title: "Pregătire & listare",
    description: "Fotografiem profesional, scriem anunțul și publicăm pe canalele relevante și prin rezervare directă.",
  },
  {
    icon: CalendarCheck,
    title: "Preluăm operațiunea",
    description: "Curățenie, check-in, comunicare cu oaspeții — coordonate prin sistem, din prima rezervare.",
  },
  {
    icon: FileCheck2,
    title: "Urmărești activitatea",
    description: "Consulți în portal informațiile disponibile despre rezervări, extrase și cheltuieli.",
  },
]

export function ProcessSection() {
  const reduceMotion = useReducedMotion()
  return (
    <section className="border-y border-border bg-sand/35 py-[var(--space-section)]">
      <div className="mx-auto max-w-7xl px-5 sm:px-8 lg:px-10">
        <Reveal className="max-w-2xl">
          <span className="text-sm font-medium text-primary">Cum funcționează</span>
          <h2 className="mt-3 text-balance font-heading text-3xl font-semibold tracking-tight sm:text-4xl">
            De la discuția inițială la activitatea curentă
          </h2>
        </Reveal>

        <div className="relative mt-16">
          <div className="absolute left-0 right-0 top-6 hidden h-px bg-border sm:block" />
          <motion.div
            initial={{ scaleX: reduceMotion ? 1 : 0 }}
            whileInView={{ scaleX: 1 }}
            viewport={{ once: true, margin: "-100px" }}
            transition={{ duration: reduceMotion ? 0 : 1.4, ease: [0.16, 1, 0.3, 1] }}
            style={{ transformOrigin: "left" }}
            className="absolute left-0 right-0 top-6 hidden h-px bg-primary sm:block"
          />

          <div className="grid gap-10 sm:grid-cols-4">
            {STEPS.map((step, index) => (
              <Reveal key={step.title} delay={index * 0.1} className="relative flex flex-col items-start">
                <span className="relative z-10 flex size-12 items-center justify-center rounded-md border border-primary/20 bg-background text-primary shadow-[var(--shadow-xs)]">
                  <step.icon className="size-5" />
                </span>
                <h3 className="mt-5 font-medium">{step.title}</h3>
                <p className="mt-2 text-sm text-muted-foreground">{step.description}</p>
              </Reveal>
            ))}
          </div>
        </div>
      </div>
    </section>
  )
}
