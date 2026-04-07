# Super Accountant — Design System (MASTER)

> **Source of Truth** for all UI decisions. Page-specific overrides live in `design-system/pages/`.
> Stack: React 19 + Vite + TypeScript + Tailwind CSS v4

---

## 1. Product Context

- **Type:** B2B SaaS — Accounting & Business Management
- **Users:** Owner, Accountant, Data Entry Operator, Cashier
- **Primary surface:** Dashboard / Data-dense tables / Forms
- **Style direction:** Minimalism — clean, functional, trustworthy, data-first
- **Anti-patterns:** Decorative gradients, emojis as icons, glassmorphism backgrounds, playful colors

---

## 2. Color System

### Primary Palette

| Token | Value | Usage |
|-------|-------|-------|
| `--color-primary` | `#2563EB` | CTAs, active nav, links, focused borders |
| `--color-primary-hover` | `#1D4ED8` | Hover state on primary buttons/links |
| `--color-primary-light` | `#EFF6FF` | Hover tint backgrounds, selected rows |
| `--color-primary-subtle` | `#BFDBFE` | Badges, chips, focus rings |

### Semantic Colors

| Token | Value | Usage |
|-------|-------|-------|
| `--color-success` | `#16A34A` | Positive amounts, paid status, success toasts |
| `--color-success-bg` | `#F0FDF4` | Success badge background |
| `--color-warning` | `#D97706` | Pending status, overdue warning |
| `--color-warning-bg` | `#FFFBEB` | Warning badge background |
| `--color-danger` | `#DC2626` | Negative amounts, errors, overdue, delete |
| `--color-danger-bg` | `#FEF2F2` | Error badge background |
| `--color-info` | `#0891B2` | Informational states, draft status |
| `--color-info-bg` | `#ECFEFF` | Info badge background |

### Neutral Palette (Slate scale)

| Token | Value | Usage |
|-------|-------|-------|
| `--color-bg` | `#F8FAFC` | Page background |
| `--color-surface` | `#FFFFFF` | Cards, panels, modals |
| `--color-surface-raised` | `#F1F5F9` | Table hover rows, input backgrounds |
| `--color-border` | `#E2E8F0` | Default borders, dividers |
| `--color-border-strong` | `#CBD5E1` | Focused input borders, strong dividers |
| `--color-text-primary` | `#0F172A` | Headings, labels, important content |
| `--color-text-secondary` | `#475569` | Body text, descriptions |
| `--color-text-muted` | `#94A3B8` | Placeholders, disabled text, captions |
| `--color-text-inverse` | `#FFFFFF` | Text on dark/primary backgrounds |

### Sidebar (dark surface)

| Token | Value | Usage |
|-------|-------|-------|
| `--color-sidebar-bg` | `#0F172A` | Sidebar background |
| `--color-sidebar-item` | `#CBD5E1` | Inactive nav item text/icon |
| `--color-sidebar-active-bg` | `#1E3A5F` | Active nav item background |
| `--color-sidebar-active-text` | `#FFFFFF` | Active nav item text |
| `--color-sidebar-border` | `#1E293B` | Sidebar internal dividers |

---

## 3. Typography

### Font Family

**Inter** — single family, weight hierarchy (Minimal Swiss pattern)

```css
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap');
```

All text uses `font-family: 'Inter', system-ui, sans-serif`.

### Type Scale

| Role | Size | Weight | Line Height | Usage |
|------|------|--------|-------------|-------|
| Display | 2rem (32px) | 700 | 1.25 | Page titles (rare) |
| H1 | 1.5rem (24px) | 600 | 1.33 | Section headings |
| H2 | 1.25rem (20px) | 600 | 1.4 | Card headings |
| H3 | 1.125rem (18px) | 500 | 1.4 | Sub-headings |
| Body | 0.9375rem (15px) | 400 | 1.6 | Default text |
| Small | 0.875rem (14px) | 400 | 1.5 | Table cells, form labels |
| XSmall | 0.75rem (12px) | 500 | 1.4 | Badges, captions, timestamps |
| Mono | 0.875rem (14px) | 400 | 1.5 | Account numbers, amounts, codes |

### Rules
- Body text minimum: **15px** on desktop, **16px** on mobile
- Line length: **65–75 characters** per line (use `max-w-prose` or `max-w-2xl`)
- Numbers (amounts, IDs): use `font-variant-numeric: tabular-nums` for alignment in tables
- Monospace for: voucher numbers, account codes, transaction IDs

---

## 4. Spacing

Base unit: **4px (0.25rem)**

| Token | Value | Usage |
|-------|-------|-------|
| `space-1` | 4px | Icon padding, tight gaps |
| `space-2` | 8px | Button internal padding (V), badge padding |
| `space-3` | 12px | Input padding (V), small card sections |
| `space-4` | 16px | Default padding, card inner spacing |
| `space-5` | 20px | Form field gap |
| `space-6` | 24px | Section gap, page padding |
| `space-8` | 32px | Between major sections |
| `space-10` | 40px | Large section gaps |
| `space-12` | 48px | Page top padding |

---

## 5. Border Radius

| Token | Value | Usage |
|-------|-------|-------|
| `radius-sm` | 4px | Badges, small tags, table chips |
| `radius-md` | 8px | Cards, inputs, buttons, dropdowns |
| `radius-lg` | 12px | Modals, panels, toasts |
| `radius-xl` | 16px | Large cards, sidesheets |
| `radius-full` | 9999px | Avatar circles, toggle pills |

---

## 6. Shadows

Minimal shadows for depth hierarchy (not decorative):

| Token | Value | Usage |
|-------|-------|-------|
| `shadow-sm` | `0 1px 2px 0 rgba(0,0,0,0.05)` | Input focus glow, subtle cards |
| `shadow-md` | `0 4px 6px -1px rgba(0,0,0,0.07), 0 2px 4px -2px rgba(0,0,0,0.05)` | Default cards, dropdowns |
| `shadow-lg` | `0 10px 15px -3px rgba(0,0,0,0.08), 0 4px 6px -4px rgba(0,0,0,0.05)` | Modals, overlays, floating panels |
| `shadow-none` | `none` | Flat table rows, inline elements |

---

## 7. Layout

### App Shell

```
┌─────────────────────────────────────────────┐
│  Sidebar (240px fixed) │  Main Content Area  │
│  bg: #0F172A           │  bg: #F8FAFC        │
│                        │  ┌─────────────────┐│
│  [Logo]                │  │ Topbar (64px)   ││
│  [Nav Items]           │  └─────────────────┘│
│                        │  ┌─────────────────┐│
│                        │  │ Page Content    ││
│  [User / Role]         │  │ p: 24px         ││
└─────────────────────────────────────────────┘
```

| Zone | Spec |
|------|------|
| Sidebar width | 240px (collapsed: 64px icon-only) |
| Topbar height | 64px |
| Page content padding | 24px all sides |
| Max content width | 1280px (centered) |
| Sidebar z-index | 20 |
| Modal z-index | 40 |
| Toast z-index | 50 |
| Dropdown z-index | 10 |

### Responsive Breakpoints

| Breakpoint | Width | Layout change |
|------------|-------|---------------|
| Mobile (`sm`) | < 640px | Sidebar collapses to bottom nav or hamburger |
| Tablet (`md`) | 640–1024px | Sidebar becomes icon-only (64px) |
| Desktop (`lg`) | ≥ 1024px | Full sidebar (240px) |

---

## 8. Components

### Buttons

| Variant | Background | Text | Border | Usage |
|---------|-----------|------|--------|-------|
| Primary | `#2563EB` | white | none | Main CTA (Save, Submit, Confirm) |
| Secondary | white | `#0F172A` | `#E2E8F0` | Secondary actions (Cancel, Back) |
| Danger | `#DC2626` | white | none | Destructive actions (Delete) |
| Ghost | transparent | `#2563EB` | none | Subtle actions (Edit inline) |
| Link | transparent | `#2563EB` | none | Navigation-style actions |

**Rules:**
- Min height: **44px** (touch target)
- Min width: **88px**
- Border radius: 8px
- Font weight: 500
- Padding: 10px 16px
- Disabled: 50% opacity, `cursor-not-allowed`
- Loading: show spinner, disable interaction
- Transition: `transition-colors duration-150`

### Inputs & Form Fields

- Height: **44px**
- Border: `1px solid #E2E8F0`
- Border radius: 8px
- Focus: `outline: none; ring: 2px solid #2563EB`
- Padding: `0 12px`
- Background: white (not slate-50)
- Error state: border `#DC2626`, error message below in `#DC2626` size 12px
- Label: above input, `font-size: 14px`, `font-weight: 500`, `color: #0F172A`
- Required marker: red asterisk after label

### Cards

- Background: white
- Border: `1px solid #E2E8F0`
- Border radius: 8px
- Shadow: `shadow-md`
- Padding: 24px
- Heading: H2 (20px / 600)
- Inner sections separated by `border-top: 1px solid #E2E8F0`

### Tables

- Background: white
- Header: `background: #F8FAFC`, `font-weight: 600`, `font-size: 12px`, `text-transform: uppercase`, `letter-spacing: 0.05em`, `color: #475569`
- Row height: 48px
- Row hover: `background: #F8FAFC`
- Selected row: `background: #EFF6FF`
- Border: `1px solid #E2E8F0` on table, no internal vertical borders
- Row divider: `border-bottom: 1px solid #F1F5F9`
- Amount columns: right-aligned, `font-variant-numeric: tabular-nums`, monospace
- Pagination: bottom right, show "X–Y of Z"
- Empty state: centered icon + message

### Badges / Status Chips

| Status | Text | Background | Usage |
|--------|------|-----------|-------|
| Paid / Active | `#16A34A` | `#F0FDF4` | Success states |
| Pending | `#D97706` | `#FFFBEB` | Awaiting action |
| Overdue / Error | `#DC2626` | `#FEF2F2` | Attention needed |
| Draft | `#0891B2` | `#ECFEFF` | Work in progress |
| Inactive | `#64748B` | `#F1F5F9` | Disabled/archived |

- Font: 12px / 500
- Padding: 2px 8px
- Border radius: 4px
- No border (use background only)

### Sidebar Navigation

- Background: `#0F172A`
- Logo area: 64px height, 24px padding
- Nav item: 40px height, 12px V padding, 16px H padding, 6px border-radius
- Active item: `background: #1E3A5F`, `color: white`
- Inactive item: `color: #CBD5E1`
- Hover item: `background: rgba(255,255,255,0.06)`, `color: white`
- Section label: `color: #475569`, `font-size: 11px`, `font-weight: 600`, uppercase
- Icon: 18px, left of label, `color` inherits
- Transition: `duration-150`

### Modals

- Backdrop: `rgba(0, 0, 0, 0.5)`
- Modal: white, border-radius 12px, shadow-lg
- Width: `max-w-lg` (default), `max-w-2xl` (large), `max-w-sm` (confirmation)
- Header: H2 + close button (X icon), `border-bottom: 1px solid #E2E8F0`
- Footer: `border-top: 1px solid #E2E8F0`, right-aligned buttons
- Padding: 24px

### Toast Notifications

- Position: top-right, 16px from edges
- Width: 360px max
- Border radius: 8px
- Shadow: shadow-lg
- Duration: 4s auto-dismiss
- Types: success (green left border), error (red), warning (amber), info (blue)

---

## 9. Icons

Use **Lucide React** exclusively (`lucide-react` package).

- Standard size: 18px (`size={18}`)
- Small (table/badge): 14px
- Large (empty states): 48px
- Color: inherits from parent (no hardcoded fill)
- Never use emojis as icons

---

## 10. Animation & Transitions

- Micro-interactions: `150ms ease-in-out` (hover, focus)
- Page transitions: `200ms ease-out`
- Modal open: `200ms` fade + scale from 0.95 → 1
- Sidebar collapse: `250ms ease-in-out`
- Use `transform` and `opacity` only — never animate `width`, `height`, or `margin`
- Respect `prefers-reduced-motion`: wrap all animations

---

## 11. Accessibility

- All interactive elements: visible focus ring (`ring-2 ring-blue-600 ring-offset-2`)
- Color is never the only indicator (always pair with label/icon)
- Form inputs: always have `<label>` with `for` attribute
- Icon-only buttons: always have `aria-label`
- Images: descriptive `alt` text
- Tables: use `<th scope="col">` for headers
- WCAG AA minimum (4.5:1 for normal text, 3:1 for large text)
- Tab order matches visual order
- `role="alert"` for toast notifications

---

## 12. Z-Index Scale

| Layer | Value | Elements |
|-------|-------|---------|
| Base | 0 | Normal content |
| Sticky | 10 | Sticky table headers, sticky nav |
| Sidebar | 20 | Sidebar overlay on mobile |
| Modal backdrop | 30 | Dimmed overlay |
| Modal | 40 | Modal/dialog panels |
| Toast | 50 | Notification toasts |

---

## 13. Role-Specific UI Notes

| Role | Dashboard Focus | Key Screens |
|------|----------------|-------------|
| Owner | Financial overview, P&L, cash flow | Dashboard, Reports, All modules |
| Accountant | Journal entries, reconciliation | Ledger, Reports, Tax |
| Data Entry Operator | Fast data input, validation | Vouchers, Masters, Import |
| Cashier | Quick transactions, receipts | Sales, Receipts, Cash register |

- Data Entry screens: larger inputs, keyboard-first, minimal clicks
- Reports/Owner screens: data-dense tables, export actions prominent
- Destructive actions always require confirmation modal

---

## 14. Anti-Patterns (Do NOT use)

- No decorative background gradients on full pages
- No emojis as UI icons (use Lucide icons)
- No glassmorphism `backdrop-filter: blur` on main UI (only modals/overlays if needed)
- No color-only status indicators without label
- No `cursor-default` on clickable elements
- No inline `style={{}}` for themed values (use Tailwind classes)
- No fixed font sizes below 12px
- No more than 3 font weights in any one view
- No box-shadow on every element (only cards, modals, dropdowns)
