import { Shield, CheckCircle, Lock } from 'lucide-react'

const trustItems = [
  { icon: Shield, label: 'Secure' },
  { icon: CheckCircle, label: 'No credit card required' },
  { icon: Lock, label: 'Your data stays yours' },
]

export function CtaStripSection() {
  return (
    <section aria-labelledby="cta-heading" className="bg-primary py-24 px-6">
      <div className="max-w-2xl mx-auto text-center">
        <h2 id="cta-heading" className="text-[32px] font-bold text-white mb-4">
          Ready to put your accounting on autopilot?
        </h2>
        <p className="text-xl text-white/80 mb-8 leading-relaxed">
          Join businesses and CA firms who've automated their compliance and reclaimed their time.
        </p>
        <a
          href="/signup"
          className="inline-flex items-center bg-white text-primary hover:bg-primary-light font-semibold px-8 py-3 rounded-lg transition-colors duration-150 min-h-[44px] mb-6 cursor-pointer"
        >
          Get Started Free
        </a>
        <div className="flex flex-wrap items-center justify-center gap-6 text-sm text-white/60">
          {trustItems.map(({ icon: Icon, label }) => (
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
