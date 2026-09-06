"use client"

import Image from "next/image"
import Link from "next/link"
import { Building2 } from "lucide-react"
import { ThemeToggle } from "@/components/layout/theme-toggle"
import { SiteBackgroundVideo } from "@/components/marketing/site-background-video"
import { useCinematicBackgroundEnabled } from "@/hooks/use-cinematic-background-enabled"
import { cn } from "@/lib/utils"

export default function AuthLayout({
  children,
}: {
  children: React.ReactNode
}) {
  const cinematicEnabled = useCinematicBackgroundEnabled()

  return (
    <div
      className={cn(
        "relative flex min-h-screen flex-col overflow-hidden text-white",
        !cinematicEnabled && "bg-navy"
      )}
    >
      {cinematicEnabled ? (
        <>
          <SiteBackgroundVideo />
          {/* Extra darkening on top of the page-wide video for the white form/text. */}
          <div className="absolute inset-0 bg-gradient-to-b from-black/70 via-black/60 to-black/80" />
        </>
      ) : (
        <>
          {/* Mobile/tablet/reduced-motion/save-data - identical to the
              pre-video-background auth layout. */}
          <Image
            src="https://images.unsplash.com/photo-1512917774080-9991f1c4c750?w=2000&q=80&auto=format&fit=crop"
            alt="Interior modern administrat de BH Group"
            fill
            sizes="100vw"
            priority
            className="kb-image-loop absolute inset-0 object-cover"
          />
          <div className="absolute inset-0 bg-gradient-to-b from-black/70 via-black/60 to-black/80" />
        </>
      )}

      <header className="relative z-10 flex items-center justify-between px-6 py-6 sm:px-10">
        <Link href="/" className="flex items-center gap-2 font-heading text-lg font-semibold tracking-tight text-white">
          <span className="flex size-8 items-center justify-center rounded-lg bg-primary text-primary-foreground">
            <Building2 className="size-4" />
          </span>
          BH Group
        </Link>
        <ThemeToggle />
      </header>

      <main className="relative z-10 flex flex-1 flex-col items-center justify-center px-6 py-10">
        <div className="w-full max-w-sm">{children}</div>
        <p className="mt-6 max-w-sm text-center text-sm text-white/60">
          Cauți o proprietate de rezervat?{" "}
          <Link href="/book" className="underline underline-offset-2 hover:text-white">
            Caută aici
          </Link>
          , fără cont necesar.
        </p>
      </main>

      <footer className="relative z-10 px-6 py-6 text-center text-xs text-white/50">
        © {new Date().getFullYear()} BH Group. Toate drepturile rezervate.
      </footer>
    </div>
  )
}
