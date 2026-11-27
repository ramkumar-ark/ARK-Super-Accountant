export const SECTION_CARD_CLASS =
  'bg-[var(--color-surface)] border border-[var(--color-border)] rounded-[var(--radius-md)] p-4 sm:p-6 shadow-[var(--shadow-sm)]'

export const NESTED_CARD_CLASS =
  'bg-[var(--color-surface)] border border-[var(--color-border)] rounded-[var(--radius-md)]'

export const ALERT_ERROR_CLASS =
  'flex items-center gap-2 px-4 py-3 rounded-[var(--radius-md)] bg-[var(--color-danger-bg)] text-[var(--color-danger)] text-sm'

export const ALERT_ERROR_LIST_CLASS =
  'flex items-start gap-2 px-4 py-3 rounded-[var(--radius-md)] bg-[var(--color-danger-bg)] text-[var(--color-danger)] text-sm'

export const ALERT_WARNING_CLASS =
  'flex items-center gap-2 px-4 py-3 rounded-[var(--radius-md)] bg-[var(--color-warning-bg)] text-[var(--color-warning)] text-sm'

export const ALERT_WARNING_LIST_CLASS =
  'flex items-start gap-2 px-4 py-3 rounded-[var(--radius-md)] bg-[var(--color-warning-bg)] text-[var(--color-warning)] text-sm'

export const ALERT_SUCCESS_CLASS =
  'flex items-center gap-2 px-4 py-3 rounded-[var(--radius-md)] bg-[var(--color-success-bg)] text-[var(--color-success)] text-sm'

export const LOADING_CLASS =
  'flex items-center gap-2 px-4 py-3 rounded-[var(--radius-md)] bg-[var(--color-surface-raised)] text-[var(--color-text-secondary)] text-sm'

export const INPUT_CLASS =
  'h-10 px-3 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface-raised)] text-sm'

export const TABLE_INPUT_CLASS =
  'h-9 w-full px-2.5 rounded-[var(--radius-sm)] border border-[var(--color-border)] bg-[var(--color-surface-raised)] text-sm sm:w-auto sm:max-w-56'

export const PRIMARY_BUTTON_CLASS =
  'px-3 py-1.5 text-sm rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white hover:bg-[var(--color-primary-hover)] disabled:opacity-50'

export const GHOST_BUTTON_CLASS =
  'text-xs px-2 py-1 rounded-[var(--radius-sm)] border border-[var(--color-border)] bg-[var(--color-surface)] hover:bg-[var(--color-surface-raised)] text-[var(--color-text-secondary)]'

export const PILL_SUCCESS_CLASS =
  'px-2 py-0.5 rounded-full text-xs font-medium bg-[var(--color-success-bg)] text-[var(--color-success)]'

export const PILL_WARNING_CLASS =
  'px-2 py-0.5 rounded-full text-xs font-medium bg-[var(--color-warning-bg)] text-[var(--color-warning)]'

export const TABLE_HEAD_CLASS =
  'px-2 py-2 text-left text-xs font-medium uppercase tracking-wide text-[var(--color-text-secondary)]'

// Stacked-table pattern: below sm each row collapses into a label/value card
// so wide tables fit phones; from sm up the table renders normally. Every cell
// keeps one DOM for both layouts — a phone-only label span plus a value wrapper
// that right-aligns on phone and becomes a plain left-aligned flex box on
// desktop — so tests and screen readers still see a single table.
export const STACKED_TABLE_CLASS = 'w-full text-sm block sm:table'
export const STACKED_THEAD_CLASS = 'hidden sm:table-header-group'
export const STACKED_BODY_CLASS = 'block sm:table-row-group'
export const STACKED_ROW_CLASS = 'block sm:table-row border-b border-[var(--color-border)]'
export const STACKED_CELL_CLASS =
  'flex sm:table-cell items-center justify-between gap-3 px-2 py-2.5 align-middle min-w-0'
export const STACKED_LABEL_CLASS =
  'sm:hidden text-xs font-medium uppercase tracking-wide text-[var(--color-text-secondary)] shrink-0'
export const STACKED_VALUE_CLASS =
  'min-w-0 flex flex-1 items-center justify-end break-words sm:flex-none sm:justify-start'
