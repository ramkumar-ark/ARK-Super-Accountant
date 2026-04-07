import { CheckCircle } from 'lucide-react'

const ownerBenefits = [
  'Always-current financial position',
  'Auto-generated P&L and cash flow reports',
  'Tax obligations tracked and filed on time',
  'Approve and review — without doing the work',
  'One dashboard for all your entities',
]

const caFirmBenefits = [
  'Centralized client data — no more file juggling',
  'Automated GST, TDS, and ITR preparation',
  'Role-based access for your team and clients',
  'Exception-based workflow — review only what needs attention',
  'Audit trail on every transaction',
]

interface BenefitCardProps {
  title: string
  subtitle: string
  benefits: string[]
}

function BenefitCard({ title, subtitle, benefits }: BenefitCardProps) {
  return (
    <div className="bg-white border border-border rounded-lg p-8">
      <h3 className="text-xl font-semibold text-text-primary mb-2">{title}</h3>
      <p className="text-text-secondary text-sm mb-6">{subtitle}</p>
      <ul className="flex flex-col gap-3">
        {benefits.map((benefit) => (
          <li key={benefit} className="flex items-start gap-3 text-sm text-text-secondary">
            <CheckCircle
              size={18}
              className="text-success mt-0.5 shrink-0"
              aria-hidden="true"
            />
            {benefit}
          </li>
        ))}
      </ul>
    </div>
  )
}

export function AudienceSection() {
  return (
    <section id="who-its-for" aria-labelledby="audience-heading" className="bg-bg py-24 px-6">
      <div className="max-w-4xl mx-auto">
        <p className="text-xs font-semibold uppercase tracking-widest text-text-muted text-center mb-4">
          WHO IT'S FOR
        </p>
        <h2 id="audience-heading" className="text-[32px] font-bold text-text-primary text-center mb-12">
          Built for the people who run the numbers.
        </h2>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
          <BenefitCard
            title="For Business Owners & Managers"
            subtitle="Stay in control without being in the books."
            benefits={ownerBenefits}
          />
          <BenefitCard
            title="For CA Firms"
            subtitle="Manage more clients with less effort."
            benefits={caFirmBenefits}
          />
        </div>
      </div>
    </section>
  )
}
