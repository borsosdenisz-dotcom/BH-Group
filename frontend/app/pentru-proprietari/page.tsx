import type { Metadata } from "next"
import { SiteHeader } from "@/components/marketing/site-header"
import { ServicesSection } from "@/components/marketing/services-section"
import { ProcessSection } from "@/components/marketing/process-section"
import { RevenueEstimateSection } from "@/components/marketing/revenue-estimate-section"
import { FaqSection } from "@/components/marketing/faq-section"
import { LeadFormSection } from "@/components/marketing/lead-form-section"
import { SiteFooter } from "@/components/marketing/site-footer"
import { ChatWidget } from "@/components/marketing/chat-widget"
import { SiteBackgroundVideo } from "@/components/marketing/site-background-video"
import { PentruProprietariHero } from "@/components/marketing/pentru-proprietari-hero"

export const metadata: Metadata = {
  title: "Administrare proprietăți pentru închirieri pe termen scurt",
  description:
    "Administrare anunțuri Airbnb și Booking.com, comunicare cu oaspeții, coordonare operațională și raportare financiară pentru proprietari.",
  alternates: { canonical: "/pentru-proprietari" },
}

export default function PentruProprietariPage() {
  return (
    <div className="flex flex-1 flex-col overflow-x-hidden">
      <SiteBackgroundVideo />
      <SiteHeader />

      <PentruProprietariHero />

      <div id="servicii">
        <ServicesSection />
      </div>
      <ProcessSection />
      <RevenueEstimateSection />
      <div id="faq">
        <FaqSection />
      </div>
      <div id="formular">
        <LeadFormSection />
      </div>
      <SiteFooter />
      <ChatWidget />
    </div>
  )
}
