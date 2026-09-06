"use client"

import { useEffect, useState } from "react"
import Link from "next/link"
import { usePathname } from "next/navigation"
import { Menu } from "lucide-react"
import { BrandMark } from "@/components/marketing/brand-mark"
import { Button, buttonVariants } from "@/components/ui/button"
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetTrigger } from "@/components/ui/sheet"
import { ThemeToggle } from "@/components/layout/theme-toggle"
import { cn } from "@/lib/utils"

const NAV_LINKS = [
  { href: "/book", label: "Proprietăți" },
  { href: "/#cum-rezervi", label: "Cum rezervi" },
  { href: "/pentru-proprietari", label: "Pentru proprietari" },
]

export function SiteHeader() {
  const [scrolled, setScrolled] = useState(false)
  const [open, setOpen] = useState(false)
  const pathname = usePathname()

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 28)
    onScroll()
    window.addEventListener("scroll", onScroll, { passive: true })
    return () => window.removeEventListener("scroll", onScroll)
  }, [])

  return (
    <header
      className={cn(
        "fixed inset-x-0 top-0 z-50 border-b transition-[background-color,border-color] duration-300",
        scrolled
          ? "border-border/80 bg-background/95 shadow-[var(--shadow-xs)] backdrop-blur-md"
          : "border-white/15 bg-navy/20"
      )}
    >
      <div className="mx-auto flex h-18 max-w-7xl items-center justify-between px-5 sm:px-8 lg:px-10">
        <Link href="/" aria-label="BH Group — pagina principală">
          <BrandMark inverse={!scrolled} />
        </Link>

        <nav aria-label="Navigație principală" className="hidden items-center gap-8 md:flex">
          {NAV_LINKS.map((link) => {
            const active = link.href === pathname
            return (
              <Link
                key={link.href}
                href={link.href}
                aria-current={active ? "page" : undefined}
                className={cn(
                  "relative py-2 text-sm font-semibold transition-colors after:absolute after:inset-x-0 after:-bottom-0.5 after:h-px after:origin-left after:scale-x-0 after:bg-current after:transition-transform hover:after:scale-x-100",
                  scrolled ? "text-muted-foreground hover:text-foreground" : "text-white/85 hover:text-white",
                  active && "after:scale-x-100"
                )}
              >
                {link.label}
              </Link>
            )
          })}
        </nav>

        <div className="flex items-center gap-1.5">
          <ThemeToggle />
          <Link href="/book" className={cn(buttonVariants({ size: "sm" }), "hidden sm:inline-flex")}>
            Caută cazare
          </Link>
          <Sheet open={open} onOpenChange={setOpen}>
            <SheetTrigger render={<Button variant="ghost" size="icon" className={cn("md:hidden", !scrolled && "text-white hover:bg-white/10 hover:text-white")} aria-label="Deschide meniul" />}>
              <Menu className="size-5" />
            </SheetTrigger>
            <SheetContent side="right" className="w-[min(88vw,22rem)] p-0">
              <SheetHeader className="border-b border-border px-5 py-5">
                <SheetTitle><BrandMark /></SheetTitle>
              </SheetHeader>
              <nav aria-label="Navigație mobilă" className="flex flex-col p-3">
                {NAV_LINKS.map((link) => (
                  <Link
                    key={link.href}
                    href={link.href}
                    onClick={() => setOpen(false)}
                    className="flex min-h-12 items-center border-b border-border/65 px-3 text-base font-semibold last:border-0 hover:bg-accent/55"
                  >
                    {link.label}
                  </Link>
                ))}
                <Link href="/book" onClick={() => setOpen(false)} className={cn(buttonVariants(), "mt-5 w-full")}>
                  Caută cazare
                </Link>
              </nav>
            </SheetContent>
          </Sheet>
        </div>
      </div>
    </header>
  )
}
