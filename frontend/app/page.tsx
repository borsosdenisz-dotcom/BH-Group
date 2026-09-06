import { SiteHeader } from "@/components/marketing/site-header"
import { HeroSection } from "@/components/marketing/hero-section"
import { WelcomeSection } from "@/components/marketing/welcome-section"
import { PropertiesShowcase } from "@/components/marketing/properties-showcase"
import { InfoSection } from "@/components/marketing/info-section"
import { GuestProcessSection } from "@/components/marketing/guest-process-section"
import { CtaSection } from "@/components/marketing/cta-section"
import { SiteFooter } from "@/components/marketing/site-footer"
import { ChatWidget } from "@/components/marketing/chat-widget"

export default function Home() {
  return (
    <div className="flex flex-1 flex-col overflow-x-hidden">
      <SiteHeader />
      <HeroSection />
      <WelcomeSection />
      <div id="proprietati">
        <PropertiesShowcase />
      </div>
      <div id="informatii">
        <InfoSection />
      </div>
      <GuestProcessSection />
      <CtaSection />
      <SiteFooter />
      <ChatWidget />
    </div>
  )
}
