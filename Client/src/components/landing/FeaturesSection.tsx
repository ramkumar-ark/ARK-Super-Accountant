import { BarChart2, Zap, FileText, RefreshCw, Send, Users, ScanLine } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'

interface Feature {
  icon: LucideIcon
  title: string
  description: string
}

const features: Feature[] = [
  {
    icon: BarChart2,
    title: 'Accounting Analytics',
    description: 'Real-time insights on your financial data — P&L, cash flow, and trends at a glance.',
  },
  {
    icon: Zap,
    title: 'Process Automation',
    description: 'From journal entries to ledger posting, routine tasks run without human intervention.',
  },
  {
    icon: FileText,
    title: 'GST & IT Reports',
    description: 'Auto-generate GST returns, ITR summaries, and financial statements — ready to file.',
  },
  {
    icon: RefreshCw,
    title: 'Reconciliation',
    description: 'Bank, vendor, and tax reconciliations done automatically, flagging only the exceptions.',
  },
  {
    icon: Send,
    title: 'Tax Return Filing',
    description: 'Direct filing integration — reviewed, approved, submitted without leaving the platform.',
  },
  {
    icon: Users,
    title: 'Multi-Role Collaboration',
    description: 'Owners, accountants, operators, and auditors — one platform, role-appropriate access.',
  },
  {
    icon: ScanLine,
    title: 'Invoice Intelligence',
    description: 'Upload any invoice — Super Accountant reads it, classifies it, and generates the accounting entries automatically.',
  },
]

function FeatureCard({ icon: Icon, title, description }: Feature) {
  return (
    <div className="bg-white border border-border rounded-lg p-6 hover:border-primary transition-colors duration-150 cursor-default">
      <div className="w-12 h-12 rounded-full bg-primary-light flex items-center justify-center mb-4">
        <Icon size={24} className="text-primary" aria-hidden="true" />
      </div>
      <h3 className="text-lg font-semibold text-text-primary mb-2">{title}</h3>
      <p className="text-sm text-text-secondary leading-relaxed">{description}</p>
    </div>
  )
}

export function FeaturesSection() {
  return (
    <section id="features" aria-labelledby="features-heading" className="bg-white py-24 px-6">
      <div className="max-w-6xl mx-auto">
        <p className="text-xs font-semibold uppercase tracking-widest text-text-muted text-center mb-4">
          WHAT WE DO
        </p>
        <h2 id="features-heading" className="text-[32px] font-bold text-text-primary text-center mb-12">
          Everything your accounting team needs, automated.
        </h2>

        {/* Row 1: 4 cards */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-8 mb-8">
          {features.slice(0, 4).map((f) => (
            <FeatureCard key={f.title} {...f} />
          ))}
        </div>

        {/* Row 2: 3 cards, centred */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-8 lg:max-w-4xl lg:mx-auto">
          {features.slice(4).map((f) => (
            <FeatureCard key={f.title} {...f} />
          ))}
        </div>
      </div>
    </section>
  )
}
