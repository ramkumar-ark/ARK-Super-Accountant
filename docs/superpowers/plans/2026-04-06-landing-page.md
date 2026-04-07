# Landing Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the public marketing landing page for Super Accountant at route `/`, converting visitors (small business owners and CA firms) to sign-ups.

**Architecture:** Eight section components (`Navbar`, `HeroSection`, `ProblemSection`, `FeaturesSection`, `AudienceSection`, `HowItWorksSection`, `CtaStripSection`, `LandingFooter`) composed into a `LandingPage` page component, wired to TanStack Router at `/`. Each component is standalone with no shared state, tested independently with Vitest + React Testing Library.

**Tech Stack:** React 19, TypeScript, Tailwind CSS v4, TanStack Router v1, Lucide React, Vitest, @testing-library/react, @testing-library/jest-dom, jsdom

---

## Design References

- `design-system/MASTER.md` — global tokens, typography, color, components
- `design-system/pages/landing.md` — landing-specific overrides (section spacing, hero type scale, dark sections)
- `docs/superpowers/specs/2026-04-06-landing-page-design.md` — full section-by-section spec

**Key Tailwind classes from the design system (Tailwind v4 `@theme` vars):**

| Class | Value | Use |
|-------|-------|-----|
| `bg-primary` | `#2563EB` | Primary CTA backgrounds |
| `text-primary` | `#2563EB` | Links, accents |
| `bg-primary-light` | `#EFF6FF` | Icon containers, highlights |
| `text-text-primary` | `#0F172A` | Headings |
| `text-text-secondary` | `#475569` | Body copy |
| `text-text-muted` | `#94A3B8` | Captions, badges |
| `bg-sidebar-bg` | `#0F172A` | Dark sections |
| `border-border` | `#E2E8F0` | Default borders |
| `bg-bg` | `#F8FAFC` | Alternating section bg |
| `bg-surface-raised` | `#F1F5F9` | Placeholder areas |
| `text-success` | `#16A34A` | Checkmark icons |
| `text-sidebar-item` | `#CBD5E1` | Footer link default |
| `border-sidebar-border` | `#1E293B` | Footer divider |
| `hover:bg-primary-hover` | `#1D4ED8` | Button hover |

---

## File Map

```
NEW
Client/src/test/setup.ts                          — @testing-library/jest-dom import
Client/src/components/landing/Navbar.tsx          — sticky navbar + mobile drawer
Client/src/components/landing/Navbar.test.tsx     — navbar tests
Client/src/components/landing/HeroSection.tsx     — hero headline, CTA, trust badges
Client/src/components/landing/HeroSection.test.tsx
Client/src/components/landing/ProblemSection.tsx  — dark bg, 3 pain-point cards
Client/src/components/landing/ProblemSection.test.tsx
Client/src/components/landing/FeaturesSection.tsx — 7-card feature grid
Client/src/components/landing/FeaturesSection.test.tsx
Client/src/components/landing/AudienceSection.tsx — 2-col audience benefit cards
Client/src/components/landing/AudienceSection.test.tsx
Client/src/components/landing/HowItWorksSection.tsx — 4-step workflow
Client/src/components/landing/HowItWorksSection.test.tsx
Client/src/components/landing/CtaStripSection.tsx — blue CTA strip
Client/src/components/landing/CtaStripSection.test.tsx
Client/src/components/landing/LandingFooter.tsx   — 4-column dark footer
Client/src/components/landing/LandingFooter.test.tsx
Client/src/pages/LandingPage.tsx                  — composes all sections
Client/src/pages/LandingPage.test.tsx             — smoke test

MODIFIED
Client/package.json         — add lucide-react dep; add vitest, @testing-library/* dev deps; add test scripts
Client/vite.config.ts       — add vitest test block (environment, setupFiles, globals)
Client/tsconfig.app.json    — add "vitest/globals" to types array
Client/index.html           — update <title> to "Super Accountant"
Client/src/main.tsx         — replace with TanStack Router setup + stub /login and /signup routes
Client/src/App.tsx          — replace with redirect stub (unused but kept to avoid import errors during transition)
```

---

## Task 1: Install Dependencies and Configure Test Environment

**Files:**
- Modify: `Client/package.json`
- Modify: `Client/vite.config.ts`
- Modify: `Client/tsconfig.app.json`
- Create: `Client/src/test/setup.ts`

- [ ] **Step 1: Install runtime dependency — lucide-react**

```bash
cd Client && npm install lucide-react
```

Expected: `lucide-react` added to `dependencies` in `package.json`.

- [ ] **Step 2: Install test dev dependencies**

```bash
npm install -D vitest @testing-library/react @testing-library/jest-dom jsdom
```

Expected: all four packages appear in `devDependencies`.

- [ ] **Step 3: Add test scripts to package.json**

Open `Client/package.json`. Replace the `"scripts"` block:

```json
"scripts": {
  "dev": "vite",
  "build": "tsc -b && vite build",
  "lint": "eslint .",
  "preview": "vite preview",
  "test": "vitest",
  "test:run": "vitest run"
},
```

- [ ] **Step 4: Add vitest config to vite.config.ts**

Replace the entire content of `Client/vite.config.ts`:

```ts
/// <reference types="vitest" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { resolve } from 'path'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    globals: true,
  },
})
```

- [ ] **Step 5: Add vitest types to tsconfig.app.json**

In `Client/tsconfig.app.json`, update the `"types"` array:

```json
"types": ["vite/client", "vitest/globals"]
```

- [ ] **Step 6: Create test setup file**

Create `Client/src/test/setup.ts`:

```ts
import '@testing-library/jest-dom'
```

- [ ] **Step 7: Update page title in index.html**

In `Client/index.html`, change:
```html
<title>client</title>
```
to:
```html
<title>Super Accountant — Accounting on Autopilot</title>
```

- [ ] **Step 8: Verify test runner works**

```bash
cd Client && npm run test:run
```

Expected: "No test files found" or 0 tests run — no errors. If errors appear, check that `@testing-library/jest-dom` is installed and `setup.ts` path is correct.

- [ ] **Step 9: Commit**

```bash
git add Client/package.json Client/package-lock.json Client/vite.config.ts Client/tsconfig.app.json Client/src/test/setup.ts Client/index.html
git commit -m "chore: install lucide-react and vitest test infrastructure"
```

---

## Task 2: Wire TanStack Router

**Files:**
- Modify: `Client/src/main.tsx`
- Modify: `Client/src/App.tsx` (clear boilerplate)
- Modify: `Client/src/App.css` (clear boilerplate)

- [ ] **Step 1: Create a stub LandingPage so the import resolves immediately**

Create `Client/src/pages/LandingPage.tsx` with a placeholder (Task 11 replaces this entirely):

```tsx
export function LandingPage() {
  return <div>Landing page coming soon</div>
}
```

- [ ] **Step 2: Replace main.tsx with TanStack Router setup**

Replace the entire content of `Client/src/main.tsx`:

```tsx
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import {
  RouterProvider,
  createRouter,
  createRoute,
  createRootRoute,
} from '@tanstack/react-router'
import './index.css'
import { LandingPage } from '@/pages/LandingPage'

const rootRoute = createRootRoute()

const indexRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/',
  component: LandingPage,
})

const loginRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/login',
  component: () => <div className="p-8 text-text-secondary">Login — coming soon</div>,
})

const signupRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/signup',
  component: () => <div className="p-8 text-text-secondary">Sign Up — coming soon</div>,
})

const routeTree = rootRoute.addChildren([indexRoute, loginRoute, signupRoute])

const router = createRouter({ routeTree })

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <RouterProvider router={router} />
  </StrictMode>,
)
```

- [ ] **Step 3: Clear App.tsx boilerplate**

Replace the entire content of `Client/src/App.tsx`:

```tsx
// Placeholder — navigation is handled via TanStack Router in main.tsx.
// This file is kept to avoid breaking existing imports during transition.
export default function App() {
  return null
}
```

- [ ] **Step 4: Clear App.css**

Replace the entire content of `Client/src/App.css`:

```css
/* App.css cleared — styles live in index.css and component Tailwind classes */
```

- [ ] **Step 5: Start dev server and verify no errors**

```bash
cd Client && npm run dev
```

Open `http://localhost:5173`. Expected: blank page or minimal output, no console errors. (LandingPage doesn't exist yet — that's fine.)

- [ ] **Step 6: Commit**

```bash
git add Client/src/pages/LandingPage.tsx Client/src/main.tsx Client/src/App.tsx Client/src/App.css
git commit -m "feat: set up TanStack Router with landing, login, and signup stub routes"
```

---

## Task 3: Navbar Component

**Files:**
- Create: `Client/src/components/landing/Navbar.tsx`
- Create: `Client/src/components/landing/Navbar.test.tsx`

- [ ] **Step 1: Write the failing tests**

Create `Client/src/components/landing/Navbar.test.tsx`:

```tsx
import { describe, it, expect } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { Navbar } from './Navbar'

describe('Navbar', () => {
  it('renders the brand name', () => {
    render(<Navbar />)
    expect(screen.getByText('Super Accountant')).toBeInTheDocument()
  })

  it('renders desktop nav links', () => {
    render(<Navbar />)
    expect(screen.getByRole('link', { name: 'Features' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'For CA Firms' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'How It Works' })).toBeInTheDocument()
  })

  it('renders Features link pointing to #features', () => {
    render(<Navbar />)
    expect(screen.getByRole('link', { name: 'Features' })).toHaveAttribute('href', '#features')
  })

  it('renders For CA Firms link pointing to #who-its-for', () => {
    render(<Navbar />)
    expect(screen.getByRole('link', { name: 'For CA Firms' })).toHaveAttribute('href', '#who-its-for')
  })

  it('renders How It Works link pointing to #how-it-works', () => {
    render(<Navbar />)
    expect(screen.getByRole('link', { name: 'How It Works' })).toHaveAttribute('href', '#how-it-works')
  })

  it('renders Log In link pointing to /login', () => {
    render(<Navbar />)
    expect(screen.getByRole('link', { name: 'Log In' })).toHaveAttribute('href', '/login')
  })

  it('renders Get Started Free CTA pointing to /signup', () => {
    render(<Navbar />)
    // First match is the desktop CTA
    const ctaLinks = screen.getAllByRole('link', { name: 'Get Started Free' })
    expect(ctaLinks[0]).toHaveAttribute('href', '/signup')
  })

  it('hamburger button has aria-label "Open menu" initially', () => {
    render(<Navbar />)
    expect(screen.getByRole('button', { name: 'Open menu' })).toBeInTheDocument()
  })

  it('mobile drawer opens on hamburger click', () => {
    render(<Navbar />)
    const hamburger = screen.getByRole('button', { name: 'Open menu' })
    expect(screen.getAllByRole('link', { name: 'Features' })).toHaveLength(1)
    fireEvent.click(hamburger)
    // Mobile drawer adds a second set of links
    expect(screen.getAllByRole('link', { name: 'Features' })).toHaveLength(2)
  })

  it('hamburger aria-expanded is false by default, true when open', () => {
    render(<Navbar />)
    const hamburger = screen.getByRole('button', { name: 'Open menu' })
    expect(hamburger).toHaveAttribute('aria-expanded', 'false')
    fireEvent.click(hamburger)
    expect(screen.getByRole('button', { name: 'Close menu' })).toHaveAttribute('aria-expanded', 'true')
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd Client && npm run test:run -- src/components/landing/Navbar.test.tsx
```

Expected: FAIL — `Cannot find module './Navbar'`

- [ ] **Step 3: Implement Navbar.tsx**

Create `Client/src/components/landing/Navbar.tsx`:

```tsx
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
        >
          {mobileOpen ? <X size={22} aria-hidden="true" /> : <Menu size={22} aria-hidden="true" />}
        </button>
      </div>

      {/* Mobile drawer */}
      {mobileOpen && (
        <div className="md:hidden border-t border-border bg-white px-6 py-4 flex flex-col gap-4">
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
          <a href="/login" className="text-primary text-sm font-medium">
            Log In
          </a>
          <a
            href="/signup"
            className="bg-primary text-white text-sm font-medium px-4 py-2.5 rounded-lg text-center min-h-[44px] flex items-center justify-center cursor-pointer"
          >
            Get Started Free
          </a>
        </div>
      )}
    </nav>
  )
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
npm run test:run -- src/components/landing/Navbar.test.tsx
```

Expected: all 10 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add Client/src/components/landing/Navbar.tsx Client/src/components/landing/Navbar.test.tsx
git commit -m "feat: add Navbar component with mobile drawer and anchor nav links"
```

---

## Task 4: HeroSection Component

**Files:**
- Create: `Client/src/components/landing/HeroSection.tsx`
- Create: `Client/src/components/landing/HeroSection.test.tsx`

- [ ] **Step 1: Write the failing tests**

Create `Client/src/components/landing/HeroSection.test.tsx`:

```tsx
import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { HeroSection } from './HeroSection'

describe('HeroSection', () => {
  it('renders an h1 heading', () => {
    render(<HeroSection />)
    expect(screen.getByRole('heading', { level: 1 })).toBeInTheDocument()
  })

  it('renders "Autopilot" in the headline', () => {
    render(<HeroSection />)
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Autopilot')
  })

  it('renders the sub-headline', () => {
    render(<HeroSection />)
    expect(screen.getByText(/automates your books, reports, and tax filings/i)).toBeInTheDocument()
  })

  it('renders Get Started Free CTA linking to /signup', () => {
    render(<HeroSection />)
    const cta = screen.getByRole('link', { name: 'Get Started Free' })
    expect(cta).toHaveAttribute('href', '/signup')
  })

  it('renders See How It Works link pointing to #how-it-works', () => {
    render(<HeroSection />)
    const link = screen.getByRole('link', { name: 'See How It Works' })
    expect(link).toHaveAttribute('href', '#how-it-works')
  })

  it('renders Secure & Compliant trust badge', () => {
    render(<HeroSection />)
    expect(screen.getByText(/Secure & Compliant/i)).toBeInTheDocument()
  })

  it('renders No credit card required trust badge', () => {
    render(<HeroSection />)
    expect(screen.getByText(/No credit card required/i)).toBeInTheDocument()
  })

  it('renders GST & IT ready trust badge', () => {
    render(<HeroSection />)
    expect(screen.getByText(/GST & IT ready/i)).toBeInTheDocument()
  })

  it('renders the eyebrow pill', () => {
    render(<HeroSection />)
    expect(screen.getByText(/GST · Income Tax · Reconciliation · Filings/i)).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

```bash
npm run test:run -- src/components/landing/HeroSection.test.tsx
```

Expected: FAIL — `Cannot find module './HeroSection'`

- [ ] **Step 3: Implement HeroSection.tsx**

Create `Client/src/components/landing/HeroSection.tsx`:

```tsx
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
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
npm run test:run -- src/components/landing/HeroSection.test.tsx
```

Expected: all 9 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add Client/src/components/landing/HeroSection.tsx Client/src/components/landing/HeroSection.test.tsx
git commit -m "feat: add HeroSection with headline, dual CTA, and trust badges"
```

---

## Task 5: ProblemSection Component

**Files:**
- Create: `Client/src/components/landing/ProblemSection.tsx`
- Create: `Client/src/components/landing/ProblemSection.test.tsx`

- [ ] **Step 1: Write the failing tests**

Create `Client/src/components/landing/ProblemSection.test.tsx`:

```tsx
import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ProblemSection } from './ProblemSection'

describe('ProblemSection', () => {
  it('renders THE PROBLEM label', () => {
    render(<ProblemSection />)
    expect(screen.getByText('THE PROBLEM')).toBeInTheDocument()
  })

  it('renders the section heading', () => {
    render(<ProblemSection />)
    expect(screen.getByRole('heading', { level: 2 })).toHaveTextContent(
      'Manual accounting is costing you more than you think.'
    )
  })

  it('renders Hours lost to data entry card', () => {
    render(<ProblemSection />)
    expect(screen.getByText('Hours lost to data entry')).toBeInTheDocument()
  })

  it('renders Missed compliance deadlines card', () => {
    render(<ProblemSection />)
    expect(screen.getByText('Missed compliance deadlines')).toBeInTheDocument()
  })

  it('renders No single source of truth card', () => {
    render(<ProblemSection />)
    expect(screen.getByText('No single source of truth')).toBeInTheDocument()
  })

  it('renders all 3 card body texts', () => {
    render(<ProblemSection />)
    expect(screen.getByText(/days keying transactions/i)).toBeInTheDocument()
    expect(screen.getByText(/one missed date means penalties/i)).toBeInTheDocument()
    expect(screen.getByText(/different versions of the same data/i)).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

```bash
npm run test:run -- src/components/landing/ProblemSection.test.tsx
```

Expected: FAIL — `Cannot find module './ProblemSection'`

- [ ] **Step 3: Implement ProblemSection.tsx**

Create `Client/src/components/landing/ProblemSection.tsx`:

```tsx
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
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
npm run test:run -- src/components/landing/ProblemSection.test.tsx
```

Expected: all 6 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add Client/src/components/landing/ProblemSection.tsx Client/src/components/landing/ProblemSection.test.tsx
git commit -m "feat: add ProblemSection with 3 pain-point cards on dark background"
```

---

## Task 6: FeaturesSection Component

**Files:**
- Create: `Client/src/components/landing/FeaturesSection.tsx`
- Create: `Client/src/components/landing/FeaturesSection.test.tsx`

- [ ] **Step 1: Write the failing tests**

Create `Client/src/components/landing/FeaturesSection.test.tsx`:

```tsx
import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { FeaturesSection } from './FeaturesSection'

describe('FeaturesSection', () => {
  it('renders WHAT WE DO label', () => {
    render(<FeaturesSection />)
    expect(screen.getByText('WHAT WE DO')).toBeInTheDocument()
  })

  it('renders section heading', () => {
    render(<FeaturesSection />)
    expect(screen.getByRole('heading', { level: 2 })).toHaveTextContent(
      'Everything your accounting team needs, automated.'
    )
  })

  it('renders Accounting Analytics card', () => {
    render(<FeaturesSection />)
    expect(screen.getByText('Accounting Analytics')).toBeInTheDocument()
  })

  it('renders Process Automation card', () => {
    render(<FeaturesSection />)
    expect(screen.getByText('Process Automation')).toBeInTheDocument()
  })

  it('renders GST & IT Reports card', () => {
    render(<FeaturesSection />)
    expect(screen.getByText('GST & IT Reports')).toBeInTheDocument()
  })

  it('renders Reconciliation card', () => {
    render(<FeaturesSection />)
    expect(screen.getByText('Reconciliation')).toBeInTheDocument()
  })

  it('renders Tax Return Filing card', () => {
    render(<FeaturesSection />)
    expect(screen.getByText('Tax Return Filing')).toBeInTheDocument()
  })

  it('renders Multi-Role Collaboration card', () => {
    render(<FeaturesSection />)
    expect(screen.getByText('Multi-Role Collaboration')).toBeInTheDocument()
  })

  it('renders Invoice Intelligence card', () => {
    render(<FeaturesSection />)
    expect(screen.getByText('Invoice Intelligence')).toBeInTheDocument()
  })

  it('renders the section with id="features"', () => {
    render(<FeaturesSection />)
    expect(document.getElementById('features')).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

```bash
npm run test:run -- src/components/landing/FeaturesSection.test.tsx
```

Expected: FAIL — `Cannot find module './FeaturesSection'`

- [ ] **Step 3: Implement FeaturesSection.tsx**

Create `Client/src/components/landing/FeaturesSection.tsx`:

```tsx
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
    <section id="features" className="bg-white py-24 px-6">
      <div className="max-w-6xl mx-auto">
        <p className="text-xs font-semibold uppercase tracking-widest text-text-muted text-center mb-4">
          WHAT WE DO
        </p>
        <h2 className="text-[32px] font-bold text-text-primary text-center mb-12">
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
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
npm run test:run -- src/components/landing/FeaturesSection.test.tsx
```

Expected: all 10 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add Client/src/components/landing/FeaturesSection.tsx Client/src/components/landing/FeaturesSection.test.tsx
git commit -m "feat: add FeaturesSection with 7-card grid (4+3 layout)"
```

---

## Task 7: AudienceSection Component

**Files:**
- Create: `Client/src/components/landing/AudienceSection.tsx`
- Create: `Client/src/components/landing/AudienceSection.test.tsx`

- [ ] **Step 1: Write the failing tests**

Create `Client/src/components/landing/AudienceSection.test.tsx`:

```tsx
import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { AudienceSection } from './AudienceSection'

describe('AudienceSection', () => {
  it('renders WHO IT\'S FOR label', () => {
    render(<AudienceSection />)
    expect(screen.getByText("WHO IT'S FOR")).toBeInTheDocument()
  })

  it('renders section heading', () => {
    render(<AudienceSection />)
    expect(screen.getByRole('heading', { level: 2 })).toHaveTextContent(
      'Built for the people who run the numbers.'
    )
  })

  it('renders For Business Owners & Managers card heading', () => {
    render(<AudienceSection />)
    expect(screen.getByText('For Business Owners & Managers')).toBeInTheDocument()
  })

  it('renders For CA Firms card heading', () => {
    render(<AudienceSection />)
    expect(screen.getByText('For CA Firms')).toBeInTheDocument()
  })

  it('renders owner sub-headline', () => {
    render(<AudienceSection />)
    expect(screen.getByText('Stay in control without being in the books.')).toBeInTheDocument()
  })

  it('renders CA firm sub-headline', () => {
    render(<AudienceSection />)
    expect(screen.getByText('Manage more clients with less effort.')).toBeInTheDocument()
  })

  it('renders owner benefit bullets', () => {
    render(<AudienceSection />)
    expect(screen.getByText('Always-current financial position')).toBeInTheDocument()
    expect(screen.getByText('Auto-generated P&L and cash flow reports')).toBeInTheDocument()
    expect(screen.getByText('Tax obligations tracked and filed on time')).toBeInTheDocument()
    expect(screen.getByText('Approve and review — without doing the work')).toBeInTheDocument()
    expect(screen.getByText('One dashboard for all your entities')).toBeInTheDocument()
  })

  it('renders CA firm benefit bullets', () => {
    render(<AudienceSection />)
    expect(screen.getByText('Centralized client data — no more file juggling')).toBeInTheDocument()
    expect(screen.getByText('Automated GST, TDS, and ITR preparation')).toBeInTheDocument()
    expect(screen.getByText('Role-based access for your team and clients')).toBeInTheDocument()
    expect(screen.getByText('Exception-based workflow — review only what needs attention')).toBeInTheDocument()
    expect(screen.getByText('Audit trail on every transaction')).toBeInTheDocument()
  })

  it('renders section with id="who-its-for"', () => {
    render(<AudienceSection />)
    expect(document.getElementById('who-its-for')).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

```bash
npm run test:run -- src/components/landing/AudienceSection.test.tsx
```

Expected: FAIL — `Cannot find module './AudienceSection'`

- [ ] **Step 3: Implement AudienceSection.tsx**

Create `Client/src/components/landing/AudienceSection.tsx`:

```tsx
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
    <section id="who-its-for" className="bg-bg py-24 px-6">
      <div className="max-w-4xl mx-auto">
        <p className="text-xs font-semibold uppercase tracking-widest text-text-muted text-center mb-4">
          WHO IT'S FOR
        </p>
        <h2 className="text-[32px] font-bold text-text-primary text-center mb-12">
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
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
npm run test:run -- src/components/landing/AudienceSection.test.tsx
```

Expected: all 10 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add Client/src/components/landing/AudienceSection.tsx Client/src/components/landing/AudienceSection.test.tsx
git commit -m "feat: add AudienceSection with owner and CA firm benefit cards"
```

---

## Task 8: HowItWorksSection Component

**Files:**
- Create: `Client/src/components/landing/HowItWorksSection.tsx`
- Create: `Client/src/components/landing/HowItWorksSection.test.tsx`

- [ ] **Step 1: Write the failing tests**

Create `Client/src/components/landing/HowItWorksSection.test.tsx`:

```tsx
import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { HowItWorksSection } from './HowItWorksSection'

describe('HowItWorksSection', () => {
  it('renders HOW IT WORKS label', () => {
    render(<HowItWorksSection />)
    expect(screen.getByText('HOW IT WORKS')).toBeInTheDocument()
  })

  it('renders section heading', () => {
    render(<HowItWorksSection />)
    expect(screen.getByRole('heading', { level: 2 })).toHaveTextContent(
      'From raw data to filed returns — automatically.'
    )
  })

  it('renders step 01 Import Your Data', () => {
    render(<HowItWorksSection />)
    expect(screen.getByText('Import Your Data')).toBeInTheDocument()
  })

  it('renders step 02 Automated Processing', () => {
    render(<HowItWorksSection />)
    expect(screen.getByText('Automated Processing')).toBeInTheDocument()
  })

  it('renders step 03 Reports Generated', () => {
    render(<HowItWorksSection />)
    expect(screen.getByText('Reports Generated')).toBeInTheDocument()
  })

  it('renders step 04 Review & File', () => {
    render(<HowItWorksSection />)
    expect(screen.getByText('Review & File')).toBeInTheDocument()
  })

  it('mentions invoice upload in step 01 description', () => {
    render(<HowItWorksSection />)
    expect(screen.getByText(/upload invoices directly/i)).toBeInTheDocument()
  })

  it('renders section with id="how-it-works"', () => {
    render(<HowItWorksSection />)
    expect(document.getElementById('how-it-works')).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

```bash
npm run test:run -- src/components/landing/HowItWorksSection.test.tsx
```

Expected: FAIL — `Cannot find module './HowItWorksSection'`

- [ ] **Step 3: Implement HowItWorksSection.tsx**

Create `Client/src/components/landing/HowItWorksSection.tsx`:

```tsx
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
    <section id="how-it-works" className="bg-white py-24 px-6">
      <div className="max-w-6xl mx-auto">
        <p className="text-xs font-semibold uppercase tracking-widest text-text-muted text-center mb-4">
          HOW IT WORKS
        </p>
        <h2 className="text-[32px] font-bold text-text-primary text-center mb-12">
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
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
npm run test:run -- src/components/landing/HowItWorksSection.test.tsx
```

Expected: all 8 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add Client/src/components/landing/HowItWorksSection.tsx Client/src/components/landing/HowItWorksSection.test.tsx
git commit -m "feat: add HowItWorksSection with 4-step automation workflow"
```

---

## Task 9: CtaStripSection Component

**Files:**
- Create: `Client/src/components/landing/CtaStripSection.tsx`
- Create: `Client/src/components/landing/CtaStripSection.test.tsx`

- [ ] **Step 1: Write the failing tests**

Create `Client/src/components/landing/CtaStripSection.test.tsx`:

```tsx
import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { CtaStripSection } from './CtaStripSection'

describe('CtaStripSection', () => {
  it('renders the CTA heading', () => {
    render(<CtaStripSection />)
    expect(screen.getByRole('heading', { level: 2 })).toHaveTextContent(
      'Ready to put your accounting on autopilot?'
    )
  })

  it('renders the sub-text', () => {
    render(<CtaStripSection />)
    expect(screen.getByText(/automated their compliance and reclaimed their time/i)).toBeInTheDocument()
  })

  it('renders Get Started Free link pointing to /signup', () => {
    render(<CtaStripSection />)
    expect(screen.getByRole('link', { name: 'Get Started Free' })).toHaveAttribute('href', '/signup')
  })

  it('renders Secure trust badge', () => {
    render(<CtaStripSection />)
    expect(screen.getByText('Secure')).toBeInTheDocument()
  })

  it('renders No credit card required trust badge', () => {
    render(<CtaStripSection />)
    expect(screen.getByText('No credit card required')).toBeInTheDocument()
  })

  it('renders Your data stays yours trust badge', () => {
    render(<CtaStripSection />)
    expect(screen.getByText('Your data stays yours')).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

```bash
npm run test:run -- src/components/landing/CtaStripSection.test.tsx
```

Expected: FAIL — `Cannot find module './CtaStripSection'`

- [ ] **Step 3: Implement CtaStripSection.tsx**

Create `Client/src/components/landing/CtaStripSection.tsx`:

```tsx
import { Shield, CheckCircle, Lock } from 'lucide-react'

const trustItems = [
  { icon: Shield, label: 'Secure' },
  { icon: CheckCircle, label: 'No credit card required' },
  { icon: Lock, label: 'Your data stays yours' },
]

export function CtaStripSection() {
  return (
    <section className="bg-primary py-24 px-6">
      <div className="max-w-2xl mx-auto text-center">
        <h2 className="text-[32px] font-bold text-white mb-4">
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
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
npm run test:run -- src/components/landing/CtaStripSection.test.tsx
```

Expected: all 6 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add Client/src/components/landing/CtaStripSection.tsx Client/src/components/landing/CtaStripSection.test.tsx
git commit -m "feat: add CtaStripSection with blue background and trust badges"
```

---

## Task 10: LandingFooter Component

**Files:**
- Create: `Client/src/components/landing/LandingFooter.tsx`
- Create: `Client/src/components/landing/LandingFooter.test.tsx`

- [ ] **Step 1: Write the failing tests**

Create `Client/src/components/landing/LandingFooter.test.tsx`:

```tsx
import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { LandingFooter } from './LandingFooter'

describe('LandingFooter', () => {
  it('renders Super Accountant brand name', () => {
    render(<LandingFooter />)
    expect(screen.getByText('Super Accountant')).toBeInTheDocument()
  })

  it('renders tagline', () => {
    render(<LandingFooter />)
    expect(screen.getByText('Accounting on Autopilot.')).toBeInTheDocument()
  })

  it('renders copyright notice', () => {
    render(<LandingFooter />)
    expect(screen.getByText(/© 2026 Super Accountant/i)).toBeInTheDocument()
  })

  it('renders Product column heading', () => {
    render(<LandingFooter />)
    expect(screen.getByText('Product')).toBeInTheDocument()
  })

  it('renders Company column heading', () => {
    render(<LandingFooter />)
    expect(screen.getByText('Company')).toBeInTheDocument()
  })

  it('renders Legal column heading', () => {
    render(<LandingFooter />)
    expect(screen.getByText('Legal')).toBeInTheDocument()
  })

  it('renders product links', () => {
    render(<LandingFooter />)
    expect(screen.getByRole('link', { name: 'Features' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'How It Works' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'For CA Firms' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Security' })).toBeInTheDocument()
  })

  it('renders company links', () => {
    render(<LandingFooter />)
    expect(screen.getByRole('link', { name: 'About' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Contact' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Blog' })).toBeInTheDocument()
  })

  it('renders legal links', () => {
    render(<LandingFooter />)
    expect(screen.getByRole('link', { name: 'Privacy Policy' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Terms of Service' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Cookie Policy' })).toBeInTheDocument()
  })

  it('renders footer as a footer landmark', () => {
    render(<LandingFooter />)
    expect(screen.getByRole('contentinfo')).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

```bash
npm run test:run -- src/components/landing/LandingFooter.test.tsx
```

Expected: FAIL — `Cannot find module './LandingFooter'`

- [ ] **Step 3: Implement LandingFooter.tsx**

Create `Client/src/components/landing/LandingFooter.tsx`:

```tsx
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
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
npm run test:run -- src/components/landing/LandingFooter.test.tsx
```

Expected: all 11 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add Client/src/components/landing/LandingFooter.tsx Client/src/components/landing/LandingFooter.test.tsx
git commit -m "feat: add LandingFooter with 4-column layout on dark background"
```

---

## Task 11: LandingPage Composition and Route Wiring

**Files:**
- Create: `Client/src/pages/LandingPage.tsx`
- Create: `Client/src/pages/LandingPage.test.tsx`
- Modify: `Client/src/main.tsx` (simplify to static import)

- [ ] **Step 1: Write the failing smoke test**

Create `Client/src/pages/LandingPage.test.tsx`:

```tsx
import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { LandingPage } from './LandingPage'

describe('LandingPage', () => {
  it('renders the navbar', () => {
    render(<LandingPage />)
    expect(screen.getByRole('navigation', { name: 'Main navigation' })).toBeInTheDocument()
  })

  it('renders the main content area', () => {
    render(<LandingPage />)
    expect(screen.getByRole('main')).toBeInTheDocument()
  })

  it('renders the footer', () => {
    render(<LandingPage />)
    expect(screen.getByRole('contentinfo')).toBeInTheDocument()
  })

  it('renders the hero heading', () => {
    render(<LandingPage />)
    expect(screen.getByRole('heading', { level: 1 })).toBeInTheDocument()
  })

  it('renders the features section', () => {
    render(<LandingPage />)
    expect(document.getElementById('features')).toBeInTheDocument()
  })

  it('renders the how it works section', () => {
    render(<LandingPage />)
    expect(document.getElementById('how-it-works')).toBeInTheDocument()
  })

  it('renders the audience section', () => {
    render(<LandingPage />)
    expect(document.getElementById('who-its-for')).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

```bash
npm run test:run -- src/pages/LandingPage.test.tsx
```

Expected: FAIL — `Cannot find module './LandingPage'`

- [ ] **Step 3: Implement LandingPage.tsx**

Create `Client/src/pages/LandingPage.tsx`:

```tsx
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
```

- [ ] **Step 4: Run LandingPage tests to verify they pass**

```bash
npm run test:run -- src/pages/LandingPage.test.tsx
```

Expected: all 7 tests PASS.

- [ ] **Step 5: Simplify main.tsx to use static import**

Replace the entire content of `Client/src/main.tsx` with the cleaner static import version:

```tsx
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import {
  RouterProvider,
  createRouter,
  createRoute,
  createRootRoute,
} from '@tanstack/react-router'
import './index.css'
import { LandingPage } from '@/pages/LandingPage'

const rootRoute = createRootRoute()

const indexRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/',
  component: LandingPage,
})

const loginRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/login',
  component: () => <div className="p-8 text-text-secondary">Login — coming soon</div>,
})

const signupRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/signup',
  component: () => <div className="p-8 text-text-secondary">Sign Up — coming soon</div>,
})

const routeTree = rootRoute.addChildren([indexRoute, loginRoute, signupRoute])

const router = createRouter({ routeTree })

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <RouterProvider router={router} />
  </StrictMode>,
)
```

- [ ] **Step 6: Run the full test suite**

```bash
npm run test:run
```

Expected: all tests PASS (no failures). Count should be 67+ tests across all components.

- [ ] **Step 7: Verify in the browser**

```bash
npm run dev
```

Open `http://localhost:5173`. Verify:
- Navbar is sticky at the top
- Hero section loads with large headline and CTA buttons
- Problem section has dark background
- Features grid shows all 7 cards
- Audience section shows two benefit cards
- How It Works section shows 4 steps
- CTA Strip has blue background
- Footer has 4 columns with dark background
- Mobile hamburger menu works at narrow viewport

- [ ] **Step 8: Commit**

```bash
git add Client/src/pages/LandingPage.tsx Client/src/pages/LandingPage.test.tsx Client/src/main.tsx
git commit -m "feat: compose LandingPage and wire to TanStack Router at /"
```
