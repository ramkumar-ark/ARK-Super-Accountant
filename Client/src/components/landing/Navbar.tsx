import { useState } from 'react'
import { BookOpen, Menu, X } from 'lucide-react'

const navLinks = [
  { label: 'Features', href: '#features' },
  { label: 'For CA Firms', href: '#who-its-for' },
  { label: 'How It Works', href: '#how-it-works' },
]

export function Navbar() {
  const [mobileOpen, setMobileOpen] = useState(false)

  return (
    <nav
      className="sticky top-0 z-10 bg-white border-b border-border"
      aria-label="Main navigation"
    >
      <div className="max-w-6xl mx-auto px-6 flex items-center justify-between h-[72px]">
        {/* Logo */}
        <a
          href="/"
          className="flex items-center gap-2 font-bold text-text-primary text-lg leading-none"
        >
          <BookOpen size={20} className="text-primary" aria-hidden="true" />
          Super Accountant
        </a>

        {/* Desktop nav links */}
        <div className="hidden md:flex items-center gap-8">
          {navLinks.map((link) => (
            <a
              key={link.label}
              href={link.href}
              className="text-text-secondary text-sm font-medium hover:text-text-primary transition-colors duration-150"
            >
              {link.label}
            </a>
          ))}
        </div>

        {/* Desktop CTAs */}
        <div className="hidden md:flex items-center gap-4">
          <a
            href="/login"
            className="text-primary text-sm font-medium hover:text-primary-hover transition-colors duration-150"
          >
            Log In
          </a>
          <a
            href="/signup"
            className="bg-primary hover:bg-primary-hover text-white text-sm font-medium px-4 py-2.5 rounded-lg transition-colors duration-150 min-h-[44px] flex items-center cursor-pointer"
          >
            Get Started Free
          </a>
        </div>

        {/* Mobile hamburger */}
        <button
          className="md:hidden p-2 text-text-secondary hover:text-text-primary transition-colors duration-150 cursor-pointer"
          onClick={() => setMobileOpen((prev) => !prev)}
          aria-label={mobileOpen ? 'Close menu' : 'Open menu'}
          aria-expanded={mobileOpen}
          aria-controls="mobile-menu"
        >
          {mobileOpen ? <X size={22} aria-hidden="true" /> : <Menu size={22} aria-hidden="true" />}
        </button>
      </div>

      {/* Mobile drawer */}
      <div
        id="mobile-menu"
        hidden={!mobileOpen}
        className="md:hidden border-t border-border bg-white px-6 py-4 flex flex-col gap-4"
      >
        {navLinks.map((link) => (
          <a
            key={link.label}
            href={link.href}
            className="text-text-secondary text-sm font-medium hover:text-text-primary transition-colors duration-150"
            onClick={() => setMobileOpen(false)}
          >
            {link.label}
          </a>
        ))}
        <hr className="border-border" />
        <a href="/login" className="text-primary text-sm font-medium" onClick={() => setMobileOpen(false)}>
          Log In
        </a>
        <a
          href="/signup"
          className="bg-primary text-white text-sm font-medium px-4 py-2.5 rounded-lg text-center min-h-[44px] flex items-center justify-center cursor-pointer"
          onClick={() => setMobileOpen(false)}
        >
          Get Started Free
        </a>
      </div>
    </nav>
  )
}
