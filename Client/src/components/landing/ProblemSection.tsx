import { Clock, AlertTriangle, Share2 } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'

interface ProblemCard {
  icon: LucideIcon
  title: string
  body: string
}

const problems: ProblemCard[] = [
  {
    icon: Clock,
    title: 'Hours lost to data entry',
    body: 'Your team spends days keying transactions that should take minutes.',
  },
  {
    icon: AlertTriangle,
    title: 'Missed compliance deadlines',
    body: 'GST, TDS, ITR — one missed date means penalties and stress.',
  },
  {
    icon: Share2,
    title: 'No single source of truth',
    body: 'Owners, accountants, and auditors work from different versions of the same data.',
  },
]

export function ProblemSection() {
  return (
    <section className="bg-sidebar-bg py-24 px-6">
      <div className="max-w-6xl mx-auto">
        <p className="text-xs font-semibold uppercase tracking-widest text-text-muted text-center mb-4">
          THE PROBLEM
        </p>
        <h2 className="text-[32px] font-bold text-white text-center mb-12 max-w-2xl mx-auto leading-tight">
          Manual accounting is costing you more than you think.
        </h2>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
          {problems.map((p) => (
            <div
              key={p.title}
              className="border border-white/10 rounded-lg p-6"
            >
              <p.icon size={24} className="text-white mb-4" aria-hidden="true" />
              <h3 className="text-[#F1F5F9] text-lg font-semibold mb-2">{p.title}</h3>
              <p className="text-text-muted text-sm leading-relaxed">{p.body}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
