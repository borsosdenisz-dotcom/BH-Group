"use client"

import { motion, useReducedMotion } from "motion/react"
import { CalendarCheck, ClipboardList, Handshake, Rocket } from "lucide-react"
import { Reveal } from "@/components/marketing/reveal"

const STEPS = [
  {
    icon: Handshake,
    title: "Discuție & evaluare",
    description: "Analizăm proprietatea ta și îți prezentăm un venit estimativ, fără niciun angajament.",
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
    icon: Rocket,
    title: "Tu încasezi venitul",
    description: "Primești deconturi periodice, cu raport detaliat al veniturilor și cheltuielilor.",
  },
]

export function ProcessSection() {
  const reduceMotion = useReducedMotion()
  return (
    <section className="border-y border-border/60 bg-muted/30 py-24 sm:py-32">
      <div className="mx-auto max-w-6xl px-6 sm:px-10">
        <Reveal className="max-w-2xl">
          <span className="text-sm font-medium text-primary">Cum funcționează</span>
          <h2 className="mt-3 text-balance font-heading text-3xl font-semibold tracking-tight sm:text-4xl">
            De la primul apel, la primul venit
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
              <Reveal key={step.title} delay={index * 0.12} className="relative flex flex-col items-start">
                <span className="relative z-10 flex size-12 items-center justify-center rounded-full border border-border bg-background text-primary shadow-sm">
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
