import { Shield, CheckCircle, Zap } from 'lucide-react'

const trustBadges = [
  { icon: Shield, label: 'Secure & Compliant' },
  { icon: CheckCircle, label: 'No credit card required' },
  { icon: Zap, label: 'GST & IT ready' },
]

export function HeroSection() {
  return (
    <section className="bg-white pt-[120px] pb-24 px-6">
      <div className="max-w-3xl mx-auto text-center">
        {/* Eyebrow pill */}
        <div className="inline-flex items-center px-3 py-1 rounded-full bg-primary-light text-primary text-xs font-medium mb-6">
          GST · Income Tax · Reconciliation · Filings
        </div>

        {/* Headline */}
        <h1 className="text-[60px] font-bold leading-[1.15] tracking-[-0.02em] text-text-primary mb-6">
          Accounting on{' '}
          <span className="text-primary">Autopilot</span>.{' '}
          Compliance Without the Chaos.
        </h1>

        {/* Sub-headline */}
        <p className="text-xl text-text-secondary max-w-2xl mx-auto leading-relaxed mb-10">
          Super Accountant automates your books, reports, and tax filings — so your team spends time on decisions, not data entry.
        </p>

        {/* CTA row */}
        <div className="flex flex-col sm:flex-row items-center justify-center gap-4 mb-8">
          <a
            href="/signup"
            className="bg-primary hover:bg-primary-hover text-white font-medium px-6 py-3 rounded-lg transition-colors duration-150 min-h-[44px] flex items-center cursor-pointer"
          >
            Get Started Free
          </a>
          <a
            href="#how-it-works"
            className="text-primary hover:text-primary-hover font-medium px-6 py-3 rounded-lg transition-colors duration-150 min-h-[44px] flex items-center border border-border hover:border-primary cursor-pointer"
          >
            See How It Works
          </a>
        </div>

        {/* Trust badges */}
        <div className="flex flex-wrap items-center justify-center gap-6 text-sm text-text-muted">
          {trustBadges.map(({ icon: Icon, label }) => (
            <span key={label} className="flex items-center gap-1.5">
              <Icon size={14} aria-hidden="true" />
              {label}
            </span>
          ))}
        </div>
      </div>
    </section>
  )
}
