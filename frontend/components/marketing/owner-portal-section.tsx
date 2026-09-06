import { CalendarDays, Home, MessageSquareText, ReceiptText } from "lucide-react"
import { SectionHeader } from "@/components/ui/page-header"

const PORTAL_ITEMS = [
  { icon: Home, title: "Proprietăți", description: "Vezi proprietățile asociate contului tău și informațiile lor publicate." },
  { icon: CalendarDays, title: "Rezervări", description: "Urmărești rezervările proprietăților tale și perioadele aferente." },
  { icon: ReceiptText, title: "Cheltuieli și extrase", description: "Consulți cheltuielile și extrasele disponibile în cont." },
  { icon: MessageSquareText, title: "Conversații", description: "Păstrezi discuțiile cu echipa în firele dedicate proprietarului." },
]

export function OwnerPortalSection() {
  return (
    <section id="portal" className="bg-navy py-[var(--space-section)] text-white">
      <div className="mx-auto grid max-w-7xl gap-12 px-5 sm:px-8 lg:grid-cols-[.8fr_1.2fr] lg:px-10">
        <SectionHeader eyebrow="Portalul proprietarului" title="Situația proprietății, organizată într-un singur loc." description="Accesul și informațiile afișate respectă rolul proprietarului existent în platformă." className="self-start [&_p]:text-white/68 [&_p:first-child]:text-brass" />
        <div className="grid gap-px overflow-hidden border border-white/14 bg-white/14 sm:grid-cols-2">{PORTAL_ITEMS.map((item) => <article key={item.title} className="bg-navy p-6 sm:p-7"><item.icon className="size-5 text-brass" /><h3 className="mt-8 font-heading text-xl font-semibold">{item.title}</h3><p className="mt-2 text-sm leading-6 text-white/66">{item.description}</p></article>)}</div>
      </div>
    </section>
  )
}
