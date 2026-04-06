interface Step {
  number: string
  title: string
  description: string
}

const steps: Step[] = [
  {
    number: '01',
    title: 'Import Your Data',
    description:
      'Connect your Tally data, upload transaction files, or upload invoices directly. Super Accountant parses, reads, and classifies everything automatically.',
  },
  {
    number: '02',
    title: 'Automated Processing',
    description:
      'Journal entries, ledger postings, and reconciliations run automatically. Exceptions are flagged for review.',
  },
  {
    number: '03',
    title: 'Reports Generated',
    description:
      'GST returns, ITR summaries, P&L, cash flow — all generated and ready for your review.',
  },
  {
    number: '04',
    title: 'Review & File',
    description:
      'Accountant or owner reviews, approves, and files — directly from the platform.',
  },
]

export function HowItWorksSection() {
  return (
    <section id="how-it-works" aria-labelledby="how-it-works-heading" className="bg-white py-24 px-6">
      <div className="max-w-6xl mx-auto">
        <p className="text-xs font-semibold uppercase tracking-widest text-text-muted text-center mb-4">
          HOW IT WORKS
        </p>
        <h2 id="how-it-works-heading" className="text-[32px] font-bold text-text-primary text-center mb-12">
          From raw data to filed returns — automatically.
        </h2>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-12 items-start">
          {/* Steps */}
          <div className="flex flex-col">
            {steps.map((step, i) => (
              <div key={step.number} className="flex gap-6">
                {/* Step number + connector line */}
                <div className="flex flex-col items-center">
                  <div className="w-12 h-12 rounded-full bg-primary-light flex items-center justify-center text-primary font-bold text-lg shrink-0">
                    {step.number}
                  </div>
                  {i < steps.length - 1 && (
                    <div
                      className="w-px flex-1 bg-border min-h-[32px] mt-1"
                      aria-hidden="true"
                    />
                  )}
                </div>

                {/* Content */}
                <div className="pb-8">
                  <h3 className="text-lg font-semibold text-text-primary mb-1 mt-2.5">
                    {step.title}
                  </h3>
                  <p className="text-sm text-text-secondary leading-relaxed">
                    {step.description}
                  </p>
                </div>
              </div>
            ))}
          </div>

          {/* Screenshot placeholder */}
          <div
            className="bg-surface-raised rounded-xl min-h-[400px] hidden lg:block"
            aria-hidden="true"
          />
        </div>
      </div>
    </section>
  )
}
