import { Navbar } from '@/components/landing/Navbar'
import { HeroSection } from '@/components/landing/HeroSection'
import { ProblemSection } from '@/components/landing/ProblemSection'
import { FeaturesSection } from '@/components/landing/FeaturesSection'
import { AudienceSection } from '@/components/landing/AudienceSection'
import { HowItWorksSection } from '@/components/landing/HowItWorksSection'
import { CtaStripSection } from '@/components/landing/CtaStripSection'
import { LandingFooter } from '@/components/landing/LandingFooter'

export function LandingPage() {
  return (
    <>
      <Navbar />
      <main>
        <HeroSection />
        <ProblemSection />
        <FeaturesSection />
        <AudienceSection />
        <HowItWorksSection />
        <CtaStripSection />
      </main>
      <LandingFooter />
    </>
  )
}
