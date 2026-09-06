"use client"

import Image from "next/image"
import { ArrowRight } from "lucide-react"
import { Reveal } from "@/components/marketing/reveal"
import { LeadDialog } from "@/components/marketing/lead-dialog"
import { buttonVariants } from "@/components/ui/button"
import { cn } from "@/lib/utils"

export function CtaSection() {
  return (
    <section className="relative mx-6 mb-24 overflow-hidden rounded-3xl sm:mx-10 sm:mb-32">
      <div className="absolute inset-0">
        <Image
          src="https://images.unsplash.com/photo-1493809842364-78817add7ffb?w=2000&q=80&auto=format&fit=crop"
          alt="Interior modern, luminos"
          fill
          sizes="100vw"
          className="kb-image-loop object-cover"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-black/85 via-black/50 to-black/20" />
      </div>

      <div className="relative z-10 flex flex-col items-start gap-6 px-8 py-20 text-white sm:px-16 sm:py-28">
        <Reveal>
          <h2 className="max-w-xl text-balance font-heading text-3xl font-semibold tracking-tight sm:text-4xl">
            Ești gata să vezi cât poate câștiga proprietatea ta?
          </h2>
        </Reveal>
        <Reveal delay={0.1}>
          <p className="max-w-lg text-balance text-white/75">
            Fără angajament, fără costuri de evaluare. Lasă-ne datele tale și te contactăm noi.
          </p>
        </Reveal>
        <Reveal delay={0.2}>
          <LeadDialog
            trigger={
              <button className={cn(buttonVariants({ size: "lg" }), "gap-2")}>
                Listează proprietatea ta
                <ArrowRight className="size-4" />
              </button>
            }
          />
        </Reveal>
      </div>
    </section>
  )
}
