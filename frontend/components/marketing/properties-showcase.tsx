"use client"

import Link from "next/link"
import { ArrowRight, Building2 } from "lucide-react"
import { buttonVariants } from "@/components/ui/button"
import { EmptyState } from "@/components/ui/empty-state"
import { SectionHeader } from "@/components/ui/page-header"
import { Skeleton } from "@/components/ui/skeleton"
import { PublicPropertyCard } from "@/components/booking/public-property-card"
import { usePublicProperties } from "@/hooks/use-public-booking"
import { cn } from "@/lib/utils"

export function PropertiesShowcase() {
  const { data, isLoading, isError } = usePublicProperties({ page: 0, size: 6 })
  const properties = data?.content ?? []

  return (
    <section className="border-y border-border/70 bg-card/55 py-[var(--space-section)]">
      <div className="mx-auto max-w-7xl px-5 sm:px-8 lg:px-10">
        <SectionHeader
          eyebrow="Proprietăți publicate"
          title="Alege locul potrivit ritmului tău."
          description="Explorează numai proprietățile disponibile public în platformă. Capacitatea, facilitățile și prețul pornesc din datele reale ale fiecărei listări."
          actions={<Link href="/book" className={cn(buttonVariants({ variant: "outline" }), "gap-2")}>Vezi toate <ArrowRight className="size-4" /></Link>}
        />

        {isLoading ? (
          <div className="mt-12 grid gap-6 sm:grid-cols-2 lg:grid-cols-3" aria-label="Se încarcă proprietățile">
            {Array.from({ length: 3 }).map((_, index) => <Skeleton key={index} className="aspect-[4/5] w-full" />)}
          </div>
        ) : isError ? (
          <EmptyState className="mt-12" icon={Building2} title="Proprietățile nu au putut fi încărcate" description="Încearcă din nou sau revino în pagina de căutare." action={<Link href="/book" className={buttonVariants()}>Deschide căutarea</Link>} />
        ) : properties.length === 0 ? (
          <EmptyState className="mt-12" icon={Building2} title="Nu sunt proprietăți publicate momentan" description="Revino în curând pentru a vedea listările disponibile pentru rezervare directă." />
        ) : (
          <div className="mt-12 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {properties.map((property) => <PublicPropertyCard key={property.id} property={property} />)}
          </div>
        )}
      </div>
    </section>
  )
}
