import Image from "next/image"
import Link from "next/link"
import { ArrowRight, CalendarCheck, KeyRound, MessageCircle } from "lucide-react"
import { buttonVariants } from "@/components/ui/button"
import { SectionHeader } from "@/components/ui/page-header"
import { cn } from "@/lib/utils"

const DETAILS = [
  { icon: CalendarCheck, title: "Perioade clare", description: "Verifici disponibilitatea și selectezi datele direct în calendar." },
  { icon: KeyRound, title: "Detalii într-un singur loc", description: "Facilitățile, regulile și politica proprietății sunt vizibile înainte de cerere." },
  { icon: MessageCircle, title: "Legătură cu echipa", description: "Cererea ajunge direct la echipa care gestionează proprietatea." },
]

export function WelcomeSection() {
  return (
    <section className="mx-auto grid max-w-7xl gap-12 px-5 py-[var(--space-section)] sm:px-8 lg:grid-cols-[.9fr_1.1fr] lg:items-center lg:px-10">
      <div className="relative min-h-96 overflow-hidden bg-muted lg:min-h-[36rem]">
        <Image
          src="/images/auth-hero.jpg"
          alt="Living luminos pregătit pentru un sejur"
          fill
          sizes="(min-width: 1024px) 44vw, 100vw"
          className="object-cover"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-navy/45 via-transparent to-transparent" />
        <p className="absolute bottom-5 left-5 max-w-xs border-l-2 border-brass pl-4 text-sm leading-6 text-white">
          Informațiile afișate provin din proprietățile publicate în platformă.
        </p>
      </div>
      <div>
        <SectionHeader
          eyebrow="Un sejur fără ambiguități"
          title="De la căutare la cerere, fiecare pas rămâne clar."
          description="BH Group reunește informațiile proprietății, disponibilitatea și cererea de rezervare într-un flux direct, fără cont pentru oaspeți."
        />
        <div className="mt-9 divide-y divide-border border-y border-border">
          {DETAILS.map((item) => (
            <div key={item.title} className="grid grid-cols-[auto_1fr] gap-4 py-5">
              <span className="flex size-10 items-center justify-center rounded-md bg-primary/10 text-primary"><item.icon className="size-5" /></span>
              <div><h3 className="font-semibold">{item.title}</h3><p className="mt-1 text-sm leading-6 text-muted-foreground">{item.description}</p></div>
            </div>
          ))}
        </div>
        <Link href="/book" className={cn(buttonVariants({ size: "lg" }), "mt-8")}>
          Vezi proprietățile <ArrowRight className="size-4" />
        </Link>
      </div>
    </section>
  )
}
