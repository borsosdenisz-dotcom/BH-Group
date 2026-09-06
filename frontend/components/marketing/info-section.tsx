import { CalendarClock, ListChecks, MessageSquareText, ShieldCheck } from "lucide-react"
import { SectionHeader } from "@/components/ui/page-header"

const INFO_ITEMS = [
  { icon: ListChecks, title: "Informații înainte de cerere", description: "Facilitățile, capacitatea și regulile publicate sunt vizibile pe pagina proprietății." },
  { icon: CalendarClock, title: "Perioadă și preț", description: "Selectezi intervalul, iar sumarul disponibil îți arată componentele prețului înainte să continui." },
  { icon: MessageSquareText, title: "Cerere, nu plată instantă", description: "Trimiterea formularului creează o cerere de rezervare; echipa o verifică și o confirmă separat." },
  { icon: ShieldCheck, title: "Politici afișate clar", description: "Condițiile proprietății și politica de anulare sunt prezentate înainte de trimiterea cererii." },
]

export function InfoSection() {
  return (
    <section className="bg-navy py-[var(--space-section)] text-white">
      <div className="mx-auto max-w-7xl px-5 sm:px-8 lg:px-10">
        <SectionHeader eyebrow="Înainte de a rezerva" title="Informația importantă, la locul potrivit." description="Fluxul direct păstrează transparența asupra proprietății și a pașilor care urmează." className="[&_p]:text-white/68 [&_p:first-child]:text-brass" />
        <div className="mt-12 grid border-y border-white/14 sm:grid-cols-2 lg:grid-cols-4">
          {INFO_ITEMS.map((item, index) => (
            <article key={item.title} className="border-b border-white/14 px-0 py-7 sm:px-6 sm:[&:nth-child(odd)]:border-r lg:border-b-0 lg:border-r lg:first:pl-0 lg:last:border-r-0 lg:last:pr-0">
              <item.icon className="size-5 text-brass" aria-hidden="true" />
              <p className="mt-8 text-xs font-semibold text-white/45">0{index + 1}</p>
              <h3 className="mt-2 font-heading text-xl font-semibold">{item.title}</h3>
              <p className="mt-3 text-sm leading-6 text-white/68">{item.description}</p>
            </article>
          ))}
        </div>
      </div>
    </section>
  )
}
