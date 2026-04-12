# Landing Page — Design Overrides

> Inherits from `design-system/MASTER.md`. Rules here take precedence for the public marketing landing page only.

---

## Context

Public-facing marketing page targeting small business owners and CA/accounting firms.
Goal: communicate value, build trust, drive Sign Up.
Tone: professional, credible, confident — not playful or decorative.

---

## Typography Overrides (Landing-Specific Scale)

Marketing pages need larger, bolder headlines than the app shell.

| Role | Size | Weight | Usage |
|------|------|--------|-------|
| Hero Headline | 3rem–3.75rem (48–60px) | 700 | Main hero heading |
| Section Headline | 2rem (32px) | 700 | Section titles |
| Sub-headline | 1.25rem (20px) | 400 | Hero sub-text, section descriptions |
| Feature Title | 1.125rem (18px) | 600 | Feature card headings |
| Body | 1rem (16px) | 400 | Section body copy (↑ from 15px for readability) |
| Caption | 0.875rem (14px) | 400 | Fine print, footnotes |

- Hero headline: line-height 1.15, letter-spacing -0.02em (tight for large display text)
- Section headline: line-height 1.25
- Max line length: 60ch for hero sub-text, 65ch for body

---

## Color Overrides (Landing-Specific)

| Usage | Value | Notes |
|-------|-------|-------|
| Page background | `#FFFFFF` | Pure white (not slate-50) for marketing feel |
| Alternating section bg | `#F8FAFC` | Subtle variation between sections |
| Hero accent | `#2563EB` | Trust Blue — used for headline keyword highlight |
| Feature icon bg | `#EFF6FF` | `--color-primary-light` — icon container fill |
| Problem section bg | `#0F172A` | Dark section for contrast (problem statement) |
| Problem section text | `#F1F5F9` | Light text on dark background |

---

## Spacing Overrides (Sections)

| Zone | Spacing |
|------|---------|
| Section vertical padding | 96px (6rem) |
| Hero vertical padding | 120px top, 96px bottom |
| Navbar height | 72px (↑ from 64px for marketing) |
| Container max-width | 1152px (max-w-5xl / max-w-6xl) |
| Container padding | 0 24px (mobile), 0 48px (tablet+) |
| Feature card gap | 32px |

---

## Section Structure

```
1. Navbar (sticky, white, border-bottom)
2. Hero
3. Problem (dark bg)
4. Features (white bg)
5. Benefits by Audience (slate-50 bg)
6. Automation Showcase (white bg)
7. CTA Strip (primary blue bg)
8. Footer (dark bg)
```

---

## Component Rules

### Navbar
- Sticky, `background: white`, `border-bottom: 1px solid #E2E8F0`, height 72px
- Logo: Inter 700, `#0F172A`, + Lucide icon in `#2563EB`
- Nav links: Inter 500 15px, `#475569`, hover `#0F172A`, transition-colors 150ms
- CTA button: primary style (44px height, 8px radius)
- Mobile: hamburger at ≤768px, slide-down menu

### Hero
- Layout: centered text, max-w-3xl, no full-bleed images
- Headline: can bold/color one keyword in `#2563EB`
- Sub-headline: `#475569`, 20px, max-w-2xl centered
- CTA buttons: Primary "Get Started Free" + Ghost "See How It Works" (scroll-to-features)
- Trust badge row below CTA: small Lucide icons + short phrases (e.g. "No credit card required", "GST-ready", "Secure & compliant")
- Background: pure white, no gradients, no illustrations

### Problem Section (Dark)
- Background: `#0F172A`
- 3 pain-point cards in a row: white border `rgba(255,255,255,0.1)`, rounded-lg
- Card content: Lucide icon (white, 24px) + bold stat or short statement + 1-line description in `#94A3B8`
- No box-shadow (dark bg makes it invisible)

### Features Section
- 6 features in a 3×2 grid (desktop), 2×3 (tablet), 1×6 (mobile)
- Each feature card: white bg, `border: 1px solid #E2E8F0`, rounded-lg, 24px padding
- Icon: Lucide 24px inside a 48px `#EFF6FF` circle
- No hover elevation — use `border-color` transition to `#2563EB` on hover

### Benefits by Audience
- Two-column layout: "For Business Owners" | "For CA Firms"
- Each column: card with heading, 4–5 bullet points (Lucide `CheckCircle` icon in green), no border between cols on desktop
- Slate-50 section background

### Automation Showcase
- Numbered step list (1–4) showing the automation workflow
- Left: step number + title + description; Right: placeholder for screenshot/mockup
- Connected by a vertical line between steps

### CTA Strip
- Background: `#2563EB`
- Large white headline, white sub-text, white outlined "Get Started Free" button
- No decorative elements

### Footer
- Background: `#0F172A`
- 4-column layout: Logo+tagline | Product links | Company links | Legal
- Text: `#CBD5E1`, links hover to white
- Bottom bar: copyright, privacy, terms

---

## Animation Rules (Landing-Specific)

- Entrance animations: fade-up on scroll (`opacity: 0 → 1`, `translateY: 20px → 0`)
- Duration: 400ms ease-out
- Stagger child elements by 100ms
- Hero elements: no scroll trigger — animate in on page load (300ms)
- Respect `prefers-reduced-motion`: all animations disabled

---

## Anti-Patterns (Landing-Specific)

- No hero background images or gradients behind text
- No stock photography
- No carousels or auto-playing content
- No more than 2 CTA buttons visible at once
- No testimonials (not yet — product needs traction first)
- No pricing section (direct to sign-up instead)
- No emoji in copy or icons
