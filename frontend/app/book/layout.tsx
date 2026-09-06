import Link from "next/link"
import { BrandMark } from "@/components/marketing/brand-mark"
import { SiteFooter } from "@/components/marketing/site-footer"
import { ThemeToggle } from "@/components/layout/theme-toggle"

export default function BookLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-screen flex-col bg-background">
      <header className="sticky top-0 z-40 border-b border-border/80 bg-background/95 shadow-[var(--shadow-xs)] backdrop-blur-md">
        <div className="mx-auto flex h-18 max-w-7xl items-center justify-between px-5 sm:px-8 lg:px-10">
          <Link href="/" aria-label="BH Group — pagina principală"><BrandMark /></Link>
          <div className="flex items-center gap-2">
            <Link href="/pentru-proprietari" className="hidden min-h-11 items-center px-3 text-sm font-semibold text-muted-foreground hover:text-foreground sm:inline-flex">Pentru proprietari</Link>
            <ThemeToggle />
          </div>
        </div>
      </header>
      <main className="flex-1 px-5 py-10 sm:px-8 sm:py-14 lg:px-10">{children}</main>
      <SiteFooter />
    </div>
  )
}
