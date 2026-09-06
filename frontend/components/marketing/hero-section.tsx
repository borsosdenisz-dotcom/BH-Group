"use client"

import { useRef } from "react"
import Image from "next/image"
import Link from "next/link"
import { motion, useReducedMotion, useScroll, useTransform } from "motion/react"
import { ArrowRight, Search } from "lucide-react"
import { buttonVariants } from "@/components/ui/button"
import { useCinematicBackgroundEnabled } from "@/hooks/use-cinematic-background-enabled"
import { cn } from "@/lib/utils"

const EASE_CINEMATIC = [0.16, 1, 0.3, 1] as const

export function HeroSection() {
  const sectionRef = useRef<HTMLDivElement>(null)
  const reduceMotion = useReducedMotion()
  const cinematicEnabled = useCinematicBackgroundEnabled()
  const { scrollYProgress } = useScroll({
    target: sectionRef,
    offset: ["start start", "end start"],
  })

  // Scroll-linked parallax is a motion effect too (WCAG 2.3.3) - frozen in
  // place rather than animated when reduced motion is preferred.
  const bgY = useTransform(scrollYProgress, [0, 1], reduceMotion ? ["0%", "0%"] : ["0%", "22%"])
  const bgScale = useTransform(scrollYProgress, [0, 1], reduceMotion ? [1.04, 1.04] : [1.04, 1.16])
  const contentOpacity = useTransform(scrollYProgress, [0, 0.7], [1, 0])

  return (
    <section
      ref={sectionRef}
      className={cn(
        "relative flex min-h-[92vh] items-end overflow-hidden text-white",
        !cinematicEnabled && "bg-navy"
      )}
    >
      {cinematicEnabled ? (
        /* Desktop only - no local background layer here, the page-wide
           <SiteBackgroundVideo /> (mounted in app/page.tsx) shows through
           this transparent section. Scroll parallax still applies to the
           extra darkening gradients below, which this hero needs on top of
           the site-wide dimming for its large white headline. */
        <motion.div
          style={{ y: bgY, scale: bgScale }}
          className="absolute inset-0"
        >
          <div className="absolute inset-0 bg-gradient-to-t from-black via-black/55 to-black/10" />
          <div className="absolute inset-0 bg-gradient-to-r from-black/70 via-black/10 to-black/40" />
        </motion.div>
      ) : (
        /* Mobile/tablet/reduced-motion/save-data - identical to the
           pre-video-background hero: its own Ken Burns image + gradients,
           no site-wide layer involved. */
        <>
          <motion.div
            style={{ y: bgY, scale: bgScale }}
            className="absolute inset-0"
          >
            <Image
              src="https://images.unsplash.com/photo-1522708323590-d24dbb6b0267?w=2000&q=80&auto=format&fit=crop"
              alt="Apartament modern administrat de BH Group"
              fill
              sizes="100vw"
              priority
              className="kb-image-loop object-cover"
            />
          </motion.div>
          <div className="absolute inset-0 bg-gradient-to-t from-black via-black/55 to-black/10" />
          <div className="absolute inset-0 bg-gradient-to-r from-black/70 via-black/10 to-black/40" />
        </>
      )}

      <motion.div
        style={{ opacity: contentOpacity }}
        className="relative z-10 mx-auto flex w-full max-w-6xl flex-col gap-8 px-6 pb-20 pt-40 sm:px-10"
      >
        <motion.span
          initial={{ opacity: 0, y: reduceMotion ? 0 : 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: reduceMotion ? 0.2 : 0.8, ease: EASE_CINEMATIC }}
          className="w-fit rounded-full border border-white/20 bg-white/10 px-4 py-1.5 text-xs font-medium tracking-wide text-white/80 backdrop-blur-sm"
        >
          Cazare premium în România
        </motion.span>

        <motion.h1
          initial={{ opacity: 0, y: reduceMotion ? 0 : 24 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: reduceMotion ? 0.2 : 0.9, delay: reduceMotion ? 0 : 0.1, ease: EASE_CINEMATIC }}
          className="max-w-3xl text-balance font-heading text-5xl font-semibold leading-[1.05] tracking-tight sm:text-6xl lg:text-7xl"
        >
          Locuiește ca un localnic, oriunde te oprești.
        </motion.h1>

        <motion.p
          initial={{ opacity: 0, y: reduceMotion ? 0 : 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: reduceMotion ? 0.2 : 0.9, delay: reduceMotion ? 0 : 0.2, ease: EASE_CINEMATIC }}
          className="max-w-xl text-balance text-lg text-white/75"
        >
          O colecție curatoriată de apartamente moderne, selectate după standarde clare
          de calitate — pregătite pentru sejururi de business sau de vacanță.
        </motion.p>

        <motion.div
          initial={{ opacity: 0, y: reduceMotion ? 0 : 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: reduceMotion ? 0.2 : 0.9, delay: reduceMotion ? 0 : 0.3, ease: EASE_CINEMATIC }}
          className="flex flex-wrap items-center gap-3"
        >
          <Link href="/book" className={cn(buttonVariants({ size: "lg" }), "gap-2")}>
            <Search className="size-4" />
            Vezi apartamentele
          </Link>
          <Link
            href="/pentru-proprietari"
            className={cn(
              buttonVariants({ size: "lg", variant: "outline" }),
              "gap-2 border-white/30 bg-white/5 text-white hover:bg-white/15 hover:text-white"
            )}
          >
            Ai o proprietate de listat?
            <ArrowRight className="size-4" />
          </Link>
        </motion.div>
      </motion.div>
    </section>
  )
}
