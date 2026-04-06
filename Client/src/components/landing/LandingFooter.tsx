import { BookOpen } from 'lucide-react'

const productLinks = [
  { label: 'Features', href: '#features' },
  { label: 'How It Works', href: '#how-it-works' },
  { label: 'For CA Firms', href: '#who-its-for' },
  { label: 'Security', href: '#' },
]

const companyLinks = [
  { label: 'About', href: '#' },
  { label: 'Contact', href: '#' },
  { label: 'Blog', href: '#' },
]

const legalLinks = [
  { label: 'Privacy Policy', href: '#' },
  { label: 'Terms of Service', href: '#' },
  { label: 'Cookie Policy', href: '#' },
]

interface FooterColumnProps {
  heading: string
  links: { label: string; href: string }[]
}

function FooterColumn({ heading, links }: FooterColumnProps) {
  return (
    <div>
      <p className="text-[#475569] text-xs font-semibold uppercase tracking-widest mb-4">
        {heading}
      </p>
      <ul className="flex flex-col gap-3">
        {links.map((link) => (
          <li key={link.label}>
            <a
              href={link.href}
              className="text-sidebar-item text-sm hover:text-white transition-colors duration-150"
            >
              {link.label}
            </a>
          </li>
        ))}
      </ul>
    </div>
  )
}

export function LandingFooter() {
  return (
    <footer className="bg-sidebar-bg pt-16 pb-8 px-6">
      <div className="max-w-6xl mx-auto">
        {/* Columns */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-8 mb-12">
          {/* Brand */}
          <div>
            <a
              href="/"
              className="flex items-center gap-2 text-white font-bold text-base mb-3"
            >
              <BookOpen size={18} className="text-primary" aria-hidden="true" />
              Super Accountant
            </a>
            <p className="text-text-muted text-sm mb-2">Accounting on Autopilot.</p>
            <p className="text-[#64748B] text-xs leading-relaxed">
              Automate your books, reports, and filings — all in one platform.
            </p>
          </div>

          <FooterColumn heading="Product" links={productLinks} />
          <FooterColumn heading="Company" links={companyLinks} />
          <FooterColumn heading="Legal" links={legalLinks} />
        </div>

        {/* Bottom bar */}
        <div className="border-t border-sidebar-border pt-6 flex flex-col sm:flex-row items-center justify-between gap-2">
          <p className="text-[#475569] text-[13px]">
            © 2026 Super Accountant. All rights reserved.
          </p>
          <div className="flex items-center gap-4">
            <a href="#" className="text-[#475569] text-[13px] hover:text-white transition-colors duration-150">
              Privacy
            </a>
            <a href="#" className="text-[#475569] text-[13px] hover:text-white transition-colors duration-150">
              Terms
            </a>
          </div>
        </div>
      </div>
    </footer>
  )
}
