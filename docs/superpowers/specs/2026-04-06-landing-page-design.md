# Landing Page — Design Spec

**Date:** 2026-04-06
**Product:** Super Accountant
**Page type:** Public marketing landing page
**Stack:** React 19 + Vite + TypeScript + Tailwind CSS v4
**Design system:** `design-system/MASTER.md` + `design-system/pages/landing.md`

---

## Purpose & Goals

Convert visitors (small business owners and CA/accounting firms) into sign-ups.
Primary CTA: **Get Started Free** (self-serve signup, no credit card required).

### Target Audiences

- **Small business owners & managers** — want compliance handled, need financial visibility, don't want to be in the books
- **CA/accounting firms** — managing multiple clients, need automation to scale, want exception-based workflows

---

## Page Structure

```
1. Navbar        — sticky, white, primary CTA
2. Hero          — headline, sub-text, dual CTA, trust badges
3. Problem       — dark section, 3 pain-point cards
4. Features      — 7-card grid, what the product does
5. Audience      — two-column benefit cards (Owners vs CA Firms)
6. How It Works  — 4-step automation workflow
7. CTA Strip     — blue, final conversion push
8. Footer        — 4-column, dark bg
```

---

## Section 1: Navbar

- **Height:** 72px, sticky, `background: #FFFFFF`, `border-bottom: 1px solid #E2E8F0`
- **Left:** `BookOpen` Lucide icon (`#2563EB`, 20px) + "Super Accountant" Inter 700 `#0F172A`
- **Center:** nav links — "Features" · "For CA Firms" · "How It Works" — Inter 500 15px `#475569`, hover `#0F172A`, `transition-colors duration-150`
- **Right:** "Log In" ghost link (`#2563EB`) + "Get Started Free" primary button (44px height, 8px radius)
- **Mobile (≤768px):** hamburger icon, slide-down drawer with all links + CTA

---

## Section 2: Hero

- **Background:** `#FFFFFF`, 120px top / 96px bottom padding
- **Layout:** centered, `max-w-3xl`
- **Eyebrow pill:** "GST · Income Tax · Reconciliation · Filings" — `#2563EB` text, `#EFF6FF` bg, 12px/500, `border-radius: 9999px`, 4px 12px padding
- **Headline:** 60px / 700 / line-height 1.15 / letter-spacing -0.02em
  > "Accounting on Autopilot. Compliance Without the Chaos."
  > Keyword "Autopilot" rendered in `#2563EB`
- **Sub-headline:** 20px / 400 / `#475569` / max-w-2xl centered / line-height 1.5
  > "Super Accountant automates your books, reports, and tax filings — so your team spends time on decisions, not data entry."
- **CTA row:** "Get Started Free" (primary) + "See How It Works" (ghost, scrolls to #how-it-works)
- **Trust badges** (14px `#94A3B8`, flex row, gap-6):
  - `Shield` "Secure & Compliant"
  - `CheckCircle` "No credit card required"
  - `Zap` "GST & IT ready"
- **Animation:** fade-up on page load, 300ms ease-out, no scroll trigger

---

## Section 3: Problem

- **Background:** `#0F172A`, 96px vertical padding
- **Section label:** "THE PROBLEM" — 12px/600 uppercase `#94A3B8`, centered
- **Heading:** "Manual accounting is costing you more than you think." — 32px/700 white, centered
- **3 pain-point cards** (3-col desktop, 1-col mobile), gap 32px:

| Icon | Title | Body |
|------|-------|------|
| `Clock` | Hours lost to data entry | Your team spends days keying transactions that should take minutes. |
| `AlertTriangle` | Missed compliance deadlines | GST, TDS, ITR — one missed date means penalties and stress. |
| `Share2` | No single source of truth | Owners, accountants, and auditors work from different versions of the same data. |

- **Card style:** `border: 1px solid rgba(255,255,255,0.1)`, `border-radius: 8px`, 24px padding
- **Icon:** white, 24px
- **Title:** `#F1F5F9` 18px/600
- **Body:** `#94A3B8` 15px/400

---

## Section 4: Features

- **Background:** `#FFFFFF`, 96px vertical padding
- **Section label:** "WHAT WE DO" — 12px/600 uppercase `#94A3B8`, centered
- **Heading:** "Everything your accounting team needs, automated." — 32px/700 `#0F172A`, centered
- **7 feature cards** — CSS grid `grid-cols-4` first row (4 cards), `grid-cols-3` second row (3 cards) on desktop using `col-span` or subgrid; `grid-cols-2` (tablet ≥768px); `grid-cols-1` (mobile)

| Icon | Title | Description |
|------|-------|-------------|
| `BarChart2` | Accounting Analytics | Real-time insights on your financial data — P&L, cash flow, and trends at a glance. |
| `Zap` | Process Automation | From journal entries to ledger posting, routine tasks run without human intervention. |
| `FileText` | GST & IT Reports | Auto-generate GST returns, ITR summaries, and financial statements — ready to file. |
| `RefreshCw` | Reconciliation | Bank, vendor, and tax reconciliations done automatically, flagging only the exceptions. |
| `Send` | Tax Return Filing | Direct filing integration — reviewed, approved, submitted without leaving the platform. |
| `Users` | Multi-Role Collaboration | Owners, accountants, operators, and auditors — one platform, role-appropriate access. |
| `ScanLine` | Invoice Intelligence | Upload any invoice — Super Accountant reads it, classifies it, and generates the accounting entries automatically. |

- **Card style:** white bg, `border: 1px solid #E2E8F0`, `border-radius: 8px`, 24px padding; hover → `border-color: #2563EB`, `transition-colors duration-150`
- **Icon container:** 48×48px circle, `#EFF6FF` bg, icon `#2563EB` 24px
- **Title:** 18px/600 `#0F172A`
- **Description:** 15px/400 `#475569`

---

## Section 5: Benefits by Audience

- **Background:** `#F8FAFC`, 96px vertical padding
- **Section label:** "WHO IT'S FOR" — 12px/600 uppercase `#94A3B8`, centered
- **Heading:** "Built for the people who run the numbers." — 32px/700 `#0F172A`, centered
- **Layout:** 2-column cards, `max-w-4xl` centered, gap 32px (stacks on mobile)

**Left — For Business Owners & Managers**
- Sub: "Stay in control without being in the books."
- Bullets (`CheckCircle` `#16A34A` 18px):
  - Always-current financial position
  - Auto-generated P&L and cash flow reports
  - Tax obligations tracked and filed on time
  - Approve and review — without doing the work
  - One dashboard for all your entities

**Right — For CA Firms**
- Sub: "Manage more clients with less effort."
- Bullets:
  - Centralized client data — no more file juggling
  - Automated GST, TDS, and ITR preparation
  - Role-based access for your team and clients
  - Exception-based workflow — review only what needs attention
  - Audit trail on every transaction

- **Card style:** white bg, `border: 1px solid #E2E8F0`, `border-radius: 8px`, 32px padding

---

## Section 6: How It Works

- **Background:** `#FFFFFF`, 96px vertical padding
- **Section label:** "HOW IT WORKS" — 12px/600 uppercase `#94A3B8`, centered
- **Heading:** "From raw data to filed returns — automatically." — 32px/700 `#0F172A`, centered
- **Layout:** 2-column desktop (steps left, screenshot placeholder right); stacks on mobile
- **4 steps**, connected by vertical line `#E2E8F0`:

| Step | Title | Description |
|------|-------|-------------|
| 01 | Import Your Data | Connect your Tally data, upload transaction files, or upload invoices directly. Super Accountant parses, reads, and classifies everything automatically. |
| 02 | Automated Processing | Journal entries, ledger postings, and reconciliations run automatically. Exceptions are flagged for review. |
| 03 | Reports Generated | GST returns, ITR summaries, P&L, cash flow — all generated and ready for your review. |
| 04 | Review & File | Accountant or owner reviews, approves, and files — directly from the platform. |

- **Step number:** 48×48px circle, `#EFF6FF` bg, `#2563EB` text, Inter 700 20px
- **Step title:** 18px/600 `#0F172A`
- **Step description:** 15px/400 `#475569`
- **Right column:** light grey placeholder `#F1F5F9`, `border-radius: 12px` — reserved for product screenshot

---

## Section 7: CTA Strip

- **Background:** `#2563EB`, 96px vertical padding
- **Layout:** centered, `max-w-2xl`
- **Heading:** "Ready to put your accounting on autopilot?" — 32px/700 white
- **Sub-text:** "Join businesses and CA firms who've automated their compliance and reclaimed their time." — 18px/400 `rgba(255,255,255,0.8)`
- **Button:** white bg, `#2563EB` text, Inter 600, 44px height, 8px radius; hover → `#EFF6FF` bg
- **Trust line** (`rgba(255,255,255,0.6)`, 14px, flex row, gap-6):
  - `Shield` "Secure"
  - `CheckCircle` "No credit card required"
  - `Lock` "Your data stays yours"

---

## Section 8: Footer

- **Background:** `#0F172A`, 64px top / 32px bottom padding
- **4-column layout** (2-col tablet, 1-col mobile):
  - **Brand:** Logo + "Accounting on Autopilot." tagline (`#94A3B8` 14px) + "Automate your books, reports, and filings — all in one platform." (`#64748B` 13px)
  - **Product:** Features, How It Works, For CA Firms, Security
  - **Company:** About, Contact, Blog
  - **Legal:** Privacy Policy, Terms of Service, Cookie Policy
- **Column heading:** `#475569`, 12px/600 uppercase, 16px bottom margin
- **Links:** `#CBD5E1` 14px/400, hover → white, `transition-colors duration-150`
- **Divider:** `border-top: 1px solid #1E293B`, 32px below columns
- **Bottom bar:** "© 2026 Super Accountant. All rights reserved." left · Privacy · Terms right — `#475569` 13px

---

## Routing

- Landing page at route `/` (public, no auth required)
- "Log In" links to `/login`
- "Get Started Free" links to `/signup`
- "See How It Works" smooth-scrolls to `#how-it-works`

### Section Anchor IDs

| Nav link | Anchor | Section |
|----------|--------|---------|
| Features | `#features` | Section 4 |
| For CA Firms | `#who-its-for` | Section 5 |
| How It Works | `#how-it-works` | Section 6 |

---

## Accessibility

- All sections have landmark roles (`<nav>`, `<main>`, `<footer>`)
- All Lucide icon-only buttons have `aria-label`
- Trust badge icons are `aria-hidden="true"` (decorative)
- Focus ring: `ring-2 ring-blue-600 ring-offset-2` on all interactive elements
- Contrast: all text meets WCAG AA (4.5:1 normal, 3:1 large)
- `prefers-reduced-motion`: all entrance animations disabled

---

## Out of Scope

- Testimonials / social proof (no traction yet)
- Pricing section (direct to signup instead)
- Blog / resource content
- Dark mode toggle
- Animations beyond fade-up entrance
