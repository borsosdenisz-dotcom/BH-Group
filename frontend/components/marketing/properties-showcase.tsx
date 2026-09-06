"use client"

import Image from "next/image"
import Link from "next/link"
import { motion } from "motion/react"
import { ArrowRight, BedDouble, Building2, MapPin, Users } from "lucide-react"
import { Reveal, RevealGroup, RevealItem } from "@/components/marketing/reveal"
import { buttonVariants } from "@/components/ui/button"
import { usePublicProperties } from "@/hooks/use-public-booking"
import { cn } from "@/lib/utils"

export function PropertiesShowcase() {
  const { data, isLoading } = usePublicProperties({ page: 0, size: 6 })
  const properties = data?.content ?? []

  return (
    <section className="mx-auto max-w-6xl px-6 py-24 sm:px-10 sm:py-32">
      <div className="flex flex-wrap items-end justify-between gap-6">
        <Reveal className="max-w-xl">
          <span className="text-sm font-medium text-primary">Proprietăți</span>
          <h2 className="mt-3 text-balance font-heading text-3xl font-semibold tracking-tight sm:text-4xl">
            Locuri în care oaspeții revin
          </h2>
        </Reveal>
        <Reveal delay={0.1}>
          <Link href="/book" className={cn(buttonVariants({ variant: "outline" }), "gap-2")}>
            Vezi toate proprietățile
            <ArrowRight className="size-4" />
          </Link>
        </Reveal>
      </div>

      {isLoading ? (
        <div className="mt-12 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 3 }).map((_, i) => (
            <div key={i} className="aspect-[4/5] animate-pulse rounded-2xl bg-muted" />
          ))}
        </div>
      ) : properties.length === 0 ? (
        <div className="mt-12 flex flex-col items-center gap-3 rounded-2xl border border-dashed border-border/60 py-16 text-center text-muted-foreground">
          <Building2 className="size-8" />
          <p>Proprietățile vor apărea aici de îndată ce sunt publicate.</p>
        </div>
      ) : (
        <RevealGroup stagger={0.08} className="mt-12 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {properties.map((property) => (
            <RevealItem key={property.id}>
              <Link
                href={`/book/${property.id}`}
                className="tilt-card group block overflow-hidden rounded-2xl border border-border/60 bg-card shadow-sm"
              >
                <div className="relative aspect-[4/3] overflow-hidden">
                  {property.coverPhotoUrl ? (
                    <Image
                      src={property.coverPhotoUrl}
                      alt={property.name}
                      fill
                      sizes="(min-width: 1024px) 33vw, (min-width: 640px) 50vw, 100vw"
                      className="object-cover transition-transform duration-700 ease-[cubic-bezier(0.16,1,0.3,1)] group-hover:scale-110"
                    />
                  ) : (
                    <div className="flex size-full items-center justify-center bg-muted text-muted-foreground">
                      <Building2 className="size-8" />
                    </div>
                  )}
                  <div className="absolute inset-0 bg-gradient-to-t from-black/60 via-transparent to-transparent opacity-0 transition-opacity duration-500 group-hover:opacity-100" />
                  <motion.div
                    initial={{ y: 8, opacity: 0 }}
                    whileInView={{ y: 0, opacity: 1 }}
                    viewport={{ once: true }}
                    className="absolute bottom-3 left-3 rounded-full bg-white/90 px-3 py-1 text-xs font-medium text-neutral-900 backdrop-blur-sm"
                  >
                    {property.basePricePerNight
                      ? `${property.basePricePerNight} ${property.currency} / noapte`
                      : "Preț la cerere"}
                  </motion.div>
                </div>
                <div className="flex flex-col gap-2 p-5">
                  <h3 className="font-medium leading-tight">{property.name}</h3>
                  <p className="flex items-center gap-1 text-sm text-muted-foreground">
                    <MapPin className="size-3.5" />
                    {property.city}
                  </p>
                  <div className="mt-1 flex items-center gap-4 text-xs text-muted-foreground">
                    <span className="flex items-center gap-1">
                      <BedDouble className="size-3.5" />
                      {property.bedrooms} dormitoare
                    </span>
                    <span className="flex items-center gap-1">
                      <Users className="size-3.5" />
                      {property.maxGuests} oaspeți
                    </span>
                  </div>
                </div>
              </Link>
            </RevealItem>
          ))}
        </RevealGroup>
      )}
    </section>
  )
}
