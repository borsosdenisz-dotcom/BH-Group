import { CalendarRange, CheckCircle2, Search, Send } from "lucide-react"
import { SectionHeader } from "@/components/ui/page-header"

const STEPS = [
  { icon: Search, title: "Caută", description: "Alege destinația, perioada și numărul de oaspeți." },
  { icon: CalendarRange, title: "Selectează", description: "Verifică pagina proprietății și marchează intervalul dorit." },
  { icon: Send, title: "Trimite cererea", description: "Completează datele de contact și transmite solicitarea." },
  { icon: CheckCircle2, title: "Primește răspunsul", description: "Echipa verifică solicitarea și comunică separat confirmarea." },
]

export function GuestProcessSection() {
  return (
    <section id="cum-rezervi" className="mx-auto max-w-7xl px-5 py-[var(--space-section)] sm:px-8 lg:px-10">
      <SectionHeader eyebrow="Cum rezervi" title="Patru pași simpli, fără promisiuni ascunse." />
      <ol className="mt-12 grid gap-px overflow-hidden border border-border bg-border sm:grid-cols-2 lg:grid-cols-4">
        {STEPS.map((step, index) => (
          <li key={step.title} className="bg-card p-6 sm:p-7">
            <div className="flex items-center justify-between"><step.icon className="size-5 text-primary" /><span className="text-xs font-semibold text-muted-foreground">0{index + 1}</span></div>
            <h3 className="mt-9 font-heading text-xl font-semibold">{step.title}</h3>
            <p className="mt-2 text-sm leading-6 text-muted-foreground">{step.description}</p>
          </li>
        ))}
      </ol>
    </section>
  )
}
