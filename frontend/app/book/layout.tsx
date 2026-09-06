"use client"

import Link from "next/link"
import { Building2 } from "lucide-react"
import { ThemeToggle } from "@/components/layout/theme-toggle"
import { SiteBackgroundVideo } from "@/components/marketing/site-background-video"
import { useCinematicBackgroundEnabled } from "@/hooks/use-cinematic-background-enabled"
import { cn } from "@/lib/utils"

export default function BookLayout({
  children,
}: {
  children: React.ReactNode
}) {
  const cinematicEnabled = useCinematicBackgroundEnabled()

  return (
    <div className={cn("flex min-h-screen flex-col", !cinematicEnabled && "bg-background")}>
      {cinematicEnabled && <SiteBackgroundVideo />}
      <header className="sticky top-0 z-30 flex h-16 items-center justify-between border-b border-border/60 bg-background/80 px-6 backdrop-blur">
        <Link href="/" className="flex items-center gap-2 font-heading font-semibold tracking-tight">
          <span className="flex size-7 items-center justify-center rounded-lg bg-primary text-primary-foreground">
            <Building2 className="size-3.5" />
          </span>
          BH Group
        </Link>
        <ThemeToggle />
      </header>
      <main className="flex-1 px-6 py-8">{children}</main>
      <footer className="flex items-center justify-center gap-4 px-6 py-6 text-center text-xs text-muted-foreground">
        <span>© {new Date().getFullYear()} BH Group. Toate drepturile rezervate.</span>
        <Link href="/login" className="text-muted-foreground/70 hover:text-foreground">
          Acces echipă
        </Link>
      </footer>
    </div>
  )
}
