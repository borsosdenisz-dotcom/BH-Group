import Image from "next/image"
import Link from "next/link"
import { CheckCircle2 } from "lucide-react"
import { BrandMark } from "@/components/marketing/brand-mark"
import { ThemeToggle } from "@/components/layout/theme-toggle"

const AUTH_POINTS = ["Acces bazat pe rol", "Autentificare în doi pași", "Sesiuni protejate"]

export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="relative flex min-h-screen flex-col overflow-hidden bg-background">
      <header className="relative z-20 flex h-20 items-center justify-between px-5 sm:px-8 lg:px-10">
        <Link href="/" aria-label="BH Group — pagina principală" className="rounded-md bg-navy px-3 py-2"><BrandMark inverse /></Link>
        <ThemeToggle />
      </header>

      <main className="relative z-10 grid flex-1 lg:grid-cols-[1.05fr_.95fr]">
        <div className="relative hidden overflow-hidden lg:block">
          <Image src="/images/auth-hero.jpg" alt="Interior luminos administrat pentru oaspeți" fill sizes="55vw" priority className="object-cover" />
          <div className="absolute inset-0 bg-navy/66" />
          <div className="absolute inset-x-0 bottom-0 p-12 text-white xl:p-16">
            <p className="text-xs font-semibold uppercase tracking-[0.18em] text-brass">Spațiul echipei</p>
            <h1 className="mt-4 max-w-xl font-heading text-4xl font-semibold leading-tight tracking-tight xl:text-5xl">Administrarea proprietăților, într-un mediu controlat.</h1>
            <ul className="mt-8 flex flex-wrap gap-x-6 gap-y-3 text-sm text-white/72">{AUTH_POINTS.map((point) => <li key={point} className="flex items-center gap-2"><CheckCircle2 className="size-4 text-brass" />{point}</li>)}</ul>
          </div>
        </div>
        <div className="flex flex-col items-center justify-center px-5 py-10 sm:px-8 lg:px-12">
          <div className="w-full max-w-md">{children}</div>
          <p className="mt-6 max-w-md text-center text-sm text-muted-foreground">Cauți o proprietate de rezervat? <Link href="/book" className="font-semibold text-foreground underline underline-offset-4 hover:text-primary">Caută aici</Link>, fără cont necesar.</p>
        </div>
      </main>

      <footer className="relative z-10 border-t border-border px-5 py-5 text-center text-xs text-muted-foreground">© {new Date().getFullYear()} BH Group. Toate drepturile rezervate.</footer>
    </div>
  )
}
