"use client"

import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from "@/components/ui/accordion"
import { Reveal } from "@/components/marketing/reveal"

const FAQS = [
  {
    question: "Ce comision percepeți din venitul proprietății?",
    answer:
      "Comisionul depinde de locație, tipul proprietății și serviciile incluse. Îl stabilim împreună, transparent, la prima discuție — fără costuri ascunse.",
  },
  {
    question: "Trebuie să semnez un contract pe termen lung?",
    answer:
      "Durata, condițiile și eventualul preaviz se discută transparent înainte de începerea colaborării și sunt stabilite în documentele agreate de ambele părți.",
  },
  {
    question: "Pot să folosesc și eu proprietatea din când în când?",
    answer:
      "Spune-ne din timp perioadele în care vrei să folosești proprietatea, iar disponibilitatea și rezervările existente vor fi verificate împreună cu echipa.",
  },
  {
    question: "Pe ce platforme listați proprietatea?",
    answer:
      "Publicăm proprietățile pe canalele de distribuție relevante și prin rezervare directă pe platforma noastră; disponibilitatea exactă pe fiecare canal se stabilește proprietate cu proprietate.",
  },
  {
    question: "Cât durează până apare prima rezervare?",
    answer:
      "Depinde de sezon, locație și cât de competitiv e anunțul — nu putem promite un termen fix, dar te ținem la curent pe tot parcursul.",
  },
]

export function FaqSection() {
  return (
    <section className="mx-auto max-w-4xl px-5 py-[var(--space-section)] sm:px-8 lg:px-10">
      <Reveal className="text-center">
        <span className="text-sm font-medium text-primary">Întrebări frecvente ale proprietarilor</span>
        <h2 className="mt-3 text-balance font-heading text-3xl font-semibold tracking-tight sm:text-4xl">
          Vrei să-ți listezi proprietatea? Iată ce trebuie să știi
        </h2>
      </Reveal>

      <Reveal delay={0.1} className="mt-12">
        <Accordion className="w-full">
          {FAQS.map((faq, index) => (
            <AccordionItem key={faq.question} value={`item-${index}`}>
              <AccordionTrigger className="text-left text-base font-medium">
                {faq.question}
              </AccordionTrigger>
              <AccordionContent className="text-muted-foreground">
                {faq.answer}
              </AccordionContent>
            </AccordionItem>
          ))}
        </Accordion>
      </Reveal>
    </section>
  )
}
