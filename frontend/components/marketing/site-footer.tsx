import Link from "next/link"
import { ArrowUpRight, Mail, Phone } from "lucide-react"
import { BrandMark } from "@/components/marketing/brand-mark"
import { siteConfig } from "@/lib/site-config"

export function SiteFooter() {
  return (
    <footer className="bg-navy text-white">
      <div className="mx-auto grid max-w-7xl gap-10 px-5 py-14 sm:px-8 md:grid-cols-[1.2fr_1fr_1fr] lg:px-10">
        <div><BrandMark inverse /><p className="mt-5 max-w-sm text-sm leading-6 text-white/62">Administrare de proprietăți și rezervări directe, într-o experiență clară pentru oaspeți și proprietari.</p></div>
        <nav aria-label="Linkuri utile"><p className="text-xs font-semibold uppercase tracking-[0.16em] text-brass">Explorează</p><ul className="mt-4 space-y-2 text-sm text-white/72"><li><Link href="/book" className="inline-flex min-h-11 items-center hover:text-white">Proprietăți</Link></li><li><Link href="/pentru-proprietari" className="inline-flex min-h-11 items-center hover:text-white">Pentru proprietari</Link></li><li><Link href="/login" className="inline-flex min-h-11 items-center hover:text-white">Acces echipă</Link></li></ul></nav>
        <div><p className="text-xs font-semibold uppercase tracking-[0.16em] text-brass">Contact</p><ul className="mt-4 space-y-2 text-sm text-white/72">{siteConfig.companyEmail && <li><a href={`mailto:${siteConfig.companyEmail}`} className="inline-flex min-h-11 items-center gap-2 hover:text-white"><Mail className="size-4" />{siteConfig.companyEmail}</a></li>}{siteConfig.companyPhone && <li><a href={`tel:${siteConfig.companyPhone}`} className="inline-flex min-h-11 items-center gap-2 hover:text-white"><Phone className="size-4" />{siteConfig.companyPhone}</a></li>}{!siteConfig.companyEmail && !siteConfig.companyPhone && <li><Link href="/pentru-proprietari#formular" className="inline-flex min-h-11 items-center gap-2 hover:text-white">Trimite un mesaj <ArrowUpRight className="size-4" /></Link></li>}</ul></div>
      </div>
      <div className="border-t border-white/12"><div className="mx-auto flex max-w-7xl flex-col gap-3 px-5 py-6 text-xs text-white/48 sm:flex-row sm:items-center sm:justify-between sm:px-8 lg:px-10"><span>© {new Date().getFullYear()} BH Group. Toate drepturile rezervate.</span><div className="flex flex-wrap gap-5"><Link href="/termeni-si-conditii" className="hover:text-white">Termeni și condiții</Link><Link href="/confidentialitate" className="hover:text-white">Confidențialitate</Link></div></div></div>
    </footer>
  )
}
