import { TdsSectionSelect } from '@/components/TdsSectionSelect'
import { useState, useEffect, useRef } from 'react'
import { api } from '@/lib/api'
import { Database, Upload, SearchX, CheckCircle2, Loader2, AlertCircle } from 'lucide-react'
import { LedgerMappingPanel } from '@/components/LedgerMappingPanel'
import { AppShell } from '@/components/AppShell'
import { useAuthStore } from '@/store/authStore'
import { ValidationSettingsPanel } from '@/components/ValidationSettingsPanel'

interface PreconfiguredMaster {
  id: string
  ledgerName: string
  category: string
  expectedParentGroup: string | null
  expectedGstApplicable: boolean | null
  expectedTdsApplicable: boolean | null
  active: boolean
  createdAt: string
  updatedAt: string
  tdsSection: string | null
  gstApplicabilityType: string | null
  hsnSacCode: string | null
  gstin: string | null
}

interface ValidationFinding {
  id: string
  uploadJobId: string
  ruleCode: string
  mismatchType: string | null
  ledgerName: string
  category: string
  actualValue: string | null
  severity: 'HIGH' | 'MEDIUM' | 'LOW' | 'INFO' | 'WARNING' | 'ERROR'
  message: string
  suggestedFix: string | null
  resolveStatus: 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED' | 'APPROVED' | 'DISCARDED'
  resolveNote: string | null
  resolvedBy: string | null
  proposedValue: string | null
  submittedBy: string | null
  reviewedBy: string | null
  reviewNote: string | null
}

// Rule codes whose corrected value is typed in by the user; everything else is derived server-side.
// Sentinel the backend reads as "mark this ledger GST not applicable" instead of a GSTIN.
const GST_NOT_APPLICABLE = 'NOT_APPLICABLE'
const TYPED_INPUT_RULES = ['GSTIN_PRESENCE', 'HSN_SAC_CODE', 'TDS_SECTION_MAPPING', 'GST_APPLICABILITY']
const APPLICABILITY_MISMATCH_TYPES = ['GST_APPLICABILITY_MISMATCH', 'TDS_APPLICABILITY_MISMATCH']
const DERIVABLE_MISMATCH_TYPES = [
  'MISSING_IN_CONFIGURATION',
  'NAME_MISMATCH',
  'PARENT_GROUP_MISMATCH',
  'GST_APPLICABILITY_MISMATCH',
  'TDS_APPLICABILITY_MISMATCH',
]
const GST_APPLICABILITY_TYPES = ['TAXABLE', 'EXEMPT', 'ZERO_RATED', 'NON_GST', 'RCM', 'NOT_APPLICABLE']
// Detection reasons a mismatch finding can have (MismatchType on the server).
const FINDINGS_PAGE_SIZE = 100

const MISMATCH_REASONS: Record<string, string> = {
  MISSING_IN_CONFIGURATION: 'Missing in configuration',
  MISSING_IN_UPLOAD: 'Missing in upload',
  NAME_MISMATCH: 'Name mismatch',
  PARENT_GROUP_MISMATCH: 'Parent group mismatch',
  GST_APPLICABILITY_MISMATCH: 'GST applicability mismatch',
  TDS_APPLICABILITY_MISMATCH: 'TDS applicability mismatch',
}

interface BulkFailure {
  findingId: string
  ledgerName: string | null
  error: string
}

const VALUE_LABELS: Record<string, string> = {
  GSTIN_PRESENCE: 'GSTIN',
  HSN_SAC_CODE: 'HSN/SAC code',
  TDS_SECTION_MAPPING: 'TDS section',
  GST_APPLICABILITY: 'GST applicability',
}

function apiErrorMessage(err: unknown, fallback: string): string {
  const data = (err as { response?: { data?: { error?: string } } })?.response?.data
  return data?.error || fallback
}

export function MastersPage() {
  const [activeTab, setActiveTab] = useState<'ledgers' | 'findings' | 'settings'>('ledgers')
  const [masters, setMasters] = useState<PreconfiguredMaster[]>([])
  const [findings, setFindings] = useState<ValidationFinding[]>([])
  const [openFindingsCount, setOpenFindingsCount] = useState(0)
  const [loading, setLoading] = useState(true)
  const [findingsLoading, setFindingsLoading] = useState(false)
  const [error, setError] = useState('')
  const [findingsError, setFindingsError] = useState('')
  const [categoryFilter, setCategoryFilter] = useState('')
  const [severityFilter, setSeverityFilter] = useState('')
  const [mismatchFilter, setMismatchFilter] = useState('')
  const [findingsTotal, setFindingsTotal] = useState(0)
  const [ruleFilter, setRuleFilter] = useState('')
  const [findingsPage, setFindingsPage] = useState(0)
  const [searchQuery, setSearchQuery] = useState('')
  const [showResolved, setShowResolved] = useState(false)
  const [editingMaster, setEditingMaster] = useState<PreconfiguredMaster | null>(null)
  const [page, setPage] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [uploading, setUploading] = useState(false)
  const [uploadError, setUploadError] = useState('')
  const [uploadSuccess, setUploadSuccess] = useState('')
  const fileInputRef = useRef<HTMLInputElement>(null)
  const PAGE_SIZE = 50

  const loadMasters = async () => {
    setLoading(true)
    setError('')
    try {
      const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) })
      if (categoryFilter) params.set('category', categoryFilter)
      const res = await api.get(`/v1/preconfigured-masters?${params}`)
      setMasters(res.data.content ?? res.data)
      setTotalElements(res.data.totalElements ?? res.data.length)
    } catch {
      setError('Could not load ledgers. Refresh the page. If the problem continues, contact support.')
    } finally {
      setLoading(false)
    }
  }

  const loadFindings = async () => {
    setFindingsLoading(true)
    setFindingsError('')
    try {
      // "Show resolved" lists the closed findings only; unchecked lists the open and pending ones.
      const params = new URLSearchParams({
        size: String(FINDINGS_PAGE_SIZE),
        page: String(findingsPage),
        showResolved: String(showResolved),
        resolvedOnly: String(showResolved),
      })
      if (severityFilter) params.set('severity', severityFilter)
      if (mismatchFilter) params.set('mismatchType', mismatchFilter)
      if (ruleFilter) params.set('ruleCode', ruleFilter)
      const res = await api.get(`/v1/uploads/latest/mismatches?${params}`)
      const items: ValidationFinding[] = res.data.content ?? []
      if (items.length === 0 && findingsPage > 0) {
        // The last page emptied (everything on it was resolved): step back to the new last page.
        setFindingsPage(findingsPage - 1)
        return
      }
      setFindings(items)
      setFindingsTotal(res.data.totalElements ?? items.length)
      loadOpenFindingsCount()
    } catch {
      setFindingsError('Could not load findings. Refresh to try again.')
    } finally {
      setFindingsLoading(false)
    }
  }

  const handleUpload = async (file: File) => {
    setUploading(true)
    setUploadError('')
    setUploadSuccess('')
    const formData = new FormData()
    formData.append('file', file)
    try {
      const res = await api.post('/v1/uploads', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      const job = res.data
      const ledgerCount = job.totalLedgersParsed ?? 0
      const mismatchCount = job.totalMismatches ?? 0
      if (ledgerCount === 0) {
        setUploadError('No ledgers found in the uploaded file. Ensure you are exporting the Masters report from TallyPrime.')
      } else if (mismatchCount > 0) {
        setUploadSuccess(
          `Uploaded ${ledgerCount} ledgers — ${mismatchCount} finding${mismatchCount === 1 ? '' : 's'} detected.`,
        )
        setActiveTab('findings')
        loadFindings()
      } else {
        setUploadSuccess(`Uploaded ${ledgerCount} ledgers — no issues found.`)
      }
      setPage(0)
      loadMasters()
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { error?: string } } }
      setUploadError(
        axiosErr.response?.data?.error ?? 'Upload failed. Check the file and try again.',
      )
    } finally {
      setUploading(false)
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

  const loadOpenFindingsCount = async () => {
    try {
      const res = await api.get('/v1/uploads/latest/mismatches?size=1&showResolved=false')
      const page = res.data
      setOpenFindingsCount(page.totalElements ?? 0)
    } catch {
      // badge count is non-critical
    }
  }

  useEffect(() => {
    loadMasters()
    loadOpenFindingsCount()
  }, [page, categoryFilter])

  useEffect(() => {
    if (activeTab === 'findings') loadFindings()
  }, [activeTab, showResolved, severityFilter, mismatchFilter, ruleFilter, findingsPage])

  const filteredMasters = masters.filter((m) =>
    searchQuery ? m.ledgerName.toLowerCase().includes(searchQuery.toLowerCase()) : true,
  )

  return (
    <AppShell>
      <main className="flex-1 overflow-y-auto p-6">
        <div className="flex flex-col gap-3 mb-4">
          <div className="flex justify-end">
            <input
              ref={fileInputRef}
              type="file"
              accept=".json"
              className="hidden"
              onChange={(e) => {
                const file = e.target.files?.[0]
                if (file) handleUpload(file)
              }}
            />
            <button
              onClick={() => fileInputRef.current?.click()}
              disabled={uploading}
              className="h-11 px-4 flex items-center gap-2 bg-[var(--color-primary)] text-white rounded-[var(--radius-md)] hover:bg-[var(--color-primary-hover)] text-sm disabled:opacity-60"
            >
              {uploading ? (
                <>
                  <Loader2 size={18} className="animate-spin" /> Uploading…
                </>
              ) : (
                <>
                  <Upload size={18} /> Upload Masters
                </>
              )}
            </button>
          </div>
          {uploadError && (
            <div className="flex items-center gap-2 px-4 py-3 rounded-[var(--radius-md)] bg-[var(--color-danger-bg)] text-[var(--color-danger)] text-sm">
              <AlertCircle size={16} className="flex-shrink-0" />
              {uploadError}
            </div>
          )}
          {uploadSuccess && (
            <div className="flex items-center gap-2 px-4 py-3 rounded-[var(--radius-md)] bg-[var(--color-success-bg)] text-[var(--color-success)] text-sm">
              <CheckCircle2 size={16} className="flex-shrink-0" />
              {uploadSuccess}
            </div>
          )}
        </div>
        {/* Tab bar */}
        <div role="tablist" className="flex border-b border-[var(--color-border)] mb-6">
          <button
            role="tab"
            aria-selected={activeTab === 'ledgers'}
            aria-controls="ledgers-panel"
            onClick={() => setActiveTab('ledgers')}
            className={`h-11 px-4 text-sm font-normal border-b-2 transition-colors ${
              activeTab === 'ledgers'
                ? 'border-[var(--color-primary)] text-[var(--color-primary)]'
                : 'border-transparent text-[var(--color-text-secondary)]'
            }`}
          >
            Ledgers
          </button>
          <button
            role="tab"
            aria-selected={activeTab === 'findings'}
            aria-controls="findings-panel"
            disabled={!loading && totalElements === 0 && openFindingsCount === 0}
            title={!loading && totalElements === 0 && openFindingsCount === 0 ? 'Upload masters to view findings' : undefined}
            onClick={() => setActiveTab('findings')}
            className={`h-11 px-4 text-sm font-normal border-b-2 transition-colors flex items-center gap-2 disabled:opacity-40 disabled:cursor-not-allowed ${
              activeTab === 'findings'
                ? 'border-[var(--color-primary)] text-[var(--color-primary)]'
                : 'border-transparent text-[var(--color-text-secondary)]'
            }`}
          >
            Findings
            {openFindingsCount > 0 && (
              <span className="px-2 py-0.5 rounded-full text-xs bg-[var(--color-danger-bg)] text-[var(--color-danger)]">
                {openFindingsCount}
              </span>
            )}
          </button>
          <button
            role="tab"
            aria-selected={activeTab === 'settings'}
            aria-controls="settings-panel"
            onClick={() => setActiveTab('settings')}
            className={`h-11 px-4 text-sm font-normal border-b-2 transition-colors ${
              activeTab === 'settings'
                ? 'border-[var(--color-primary)] text-[var(--color-primary)]'
                : 'border-transparent text-[var(--color-text-secondary)]'
            }`}
          >
            Validation settings
          </button>
        </div>

        {/* LEDGERS TAB */}
        {activeTab === 'ledgers' && (
          <div id="ledgers-panel" role="tabpanel">
            <div className="bg-[var(--color-surface)] border border-[var(--color-border)] rounded-[var(--radius-md)] p-4 shadow-[var(--shadow-sm)] mb-4">
              <div className="flex flex-wrap gap-3 items-center">
                <select
                  value={categoryFilter}
                  onChange={(e) => setCategoryFilter(e.target.value)}
                  className="h-10 px-3 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface-raised)] text-sm text-[var(--color-text-primary)] focus:outline-none focus:border-[var(--color-primary)]"
                >
                  <option value="">All Categories</option>
                  {['PURCHASE', 'EXPENSE', 'INCOME', 'GST', 'TDS', 'CREDITOR', 'OTHER'].map((c) => (
                    <option key={c} value={c}>
                      {c.charAt(0) + c.slice(1).toLowerCase()}
                    </option>
                  ))}
                </select>
                <input
                  type="text"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  placeholder="Search ledger name…"
                  className="h-10 pl-3 pr-3 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface-raised)] text-sm text-[var(--color-text-primary)] focus:outline-none focus:border-[var(--color-primary)] w-64"
                />
              </div>
            </div>
            {loading ? (
              <div className="flex justify-center py-20">
                <Loader2 className="animate-spin text-[var(--color-text-muted)]" size={32} />
              </div>
            ) : error ? (
              <div className="text-[var(--color-danger)] text-sm py-10 text-center">{error}</div>
            ) : filteredMasters.length === 0 ? (
              searchQuery ? (
                <div className="flex flex-col items-center py-20 gap-3">
                  <SearchX size={48} className="text-[var(--color-text-muted)]" />
                  <p className="text-sm font-semibold text-[var(--color-text-primary)]">
                    No ledgers match your filters
                  </p>
                  <p className="text-sm text-[var(--color-text-muted)]">
                    Try adjusting the category filter or clear the search.
                  </p>
                  <button
                    onClick={() => {
                      setCategoryFilter('')
                      setSearchQuery('')
                    }}
                    className="text-sm text-[var(--color-primary)]"
                  >
                    Clear filters
                  </button>
                </div>
              ) : (
                <div className="flex flex-col items-center py-20 gap-3">
                  <Database size={48} className="text-[var(--color-text-muted)]" />
                  <p className="text-sm font-semibold text-[var(--color-text-primary)]">
                    No ledgers found
                  </p>
                  <p className="text-sm text-[var(--color-text-muted)]">
                    Upload a masters XML file to get started, or adjust your filters.
                  </p>
                </div>
              )
            ) : (
              <div className="bg-[var(--color-surface)] rounded-[var(--radius-lg)] border border-[var(--color-border)] shadow-[var(--shadow-sm)] overflow-x-auto">
                <table className="w-full">
                  <thead className="sticky top-0 z-[var(--z-sticky)] bg-[var(--color-bg)]">
                    <tr>
                      {[
                        'Ledger Name',
                        'Category',
                        'Parent Group',
                        'TDS Section',
                        'GST Applicability',
                        'HSN / SAC',
                        'GSTIN',
                        'Actions',
                      ].map((col) => (
                        <th
                          key={col}
                          scope="col"
                          className="px-4 py-3 text-left text-[0.75rem] font-semibold uppercase tracking-[0.05em] text-[var(--color-text-secondary)]"
                        >
                          {col}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {filteredMasters.map((master) => (
                      <tr
                        key={master.id}
                        className="h-12 border-t border-[var(--color-surface-raised)] hover:bg-[var(--color-surface-raised)]"
                      >
                        <td className="px-4 py-2 text-sm text-[var(--color-text-primary)] min-w-[200px]">
                          {master.ledgerName}
                        </td>
                        <td className="px-4 py-2 w-[120px]">
                          <CategoryBadge category={master.category} />
                        </td>
                        <td className="px-4 py-2 text-sm text-[var(--color-text-secondary)] w-[160px]">
                          {master.expectedParentGroup ?? '—'}
                        </td>
                        <td className="px-4 py-2 w-[160px]">
                          {master.tdsSection === 'NOT_SUBJECT' ? (
                            <span className="text-sm font-mono text-[var(--color-text-muted)]">
                              Not Subject to TDS
                            </span>
                          ) : master.tdsSection ? (
                            <span className="text-sm font-mono text-[var(--color-text-primary)]">
                              {master.tdsSection}
                            </span>
                          ) : (
                            <span className="text-sm text-[var(--color-text-muted)]">—</span>
                          )}
                        </td>
                        <td className="px-4 py-2 w-[140px]">
                          {master.gstApplicabilityType ? (
                            <GstBadge type={master.gstApplicabilityType} />
                          ) : (
                            <span className="text-xs px-2 py-0.5 rounded-full bg-[var(--color-danger-bg)] text-[var(--color-danger)]">
                              Not Set
                            </span>
                          )}
                        </td>
                        <td className="px-4 py-2 w-[100px]">
                          {master.hsnSacCode ? (
                            <span className="text-sm font-mono text-[var(--color-text-primary)]">
                              {master.hsnSacCode}
                            </span>
                          ) : (
                            <span className="text-sm font-mono text-[var(--color-danger)]">—</span>
                          )}
                        </td>
                        <td className="px-4 py-2 w-[160px]">
                          {master.gstin ? (
                            <span className="text-sm font-mono text-[var(--color-text-primary)]">
                              {master.gstin}
                            </span>
                          ) : master.category === 'PURCHASE' ? (
                            <span className="text-sm font-mono text-[var(--color-danger)]">—</span>
                          ) : (
                            <span className="text-sm text-[var(--color-text-muted)]">—</span>
                          )}
                        </td>
                        <td className="px-4 py-2 w-[80px]">
                          <button
                            onClick={() => setEditingMaster(master)}
                            aria-label={`Edit mapping for ${master.ledgerName}`}
                            className="text-sm text-[var(--color-primary)] hover:underline"
                          >
                            Edit
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
                <div className="flex items-center justify-between px-4 py-3 border-t border-[var(--color-border)]">
                  <span className="text-sm text-[var(--color-text-muted)]">
                    {totalElements === 0 ? '0' : `${page * PAGE_SIZE + 1}–${Math.min((page + 1) * PAGE_SIZE, totalElements)}`} of{' '}
                    {totalElements} ledgers
                  </span>
                  <div className="flex gap-2">
                    <button
                      disabled={page === 0}
                      onClick={() => setPage((p) => p - 1)}
                      className="h-9 w-9 rounded-[var(--radius-md)] border border-[var(--color-border)] text-sm disabled:opacity-40"
                    >
                      ←
                    </button>
                    <button
                      disabled={(page + 1) * PAGE_SIZE >= totalElements}
                      onClick={() => setPage((p) => p + 1)}
                      className="h-9 w-9 rounded-[var(--radius-md)] border border-[var(--color-border)] text-sm disabled:opacity-40"
                    >
                      →
                    </button>
                  </div>
                </div>
              </div>
            )}
          </div>
        )}

        {/* FINDINGS TAB */}
        {activeTab === 'settings' && (
          <div id="settings-panel" role="tabpanel">
            <ValidationSettingsPanel />
          </div>
        )}

        {activeTab === 'findings' && (
          <FindingsTab
            findings={findings}
            loading={findingsLoading}
            error={findingsError}
            showResolved={showResolved}
            severityFilter={severityFilter}
            mismatchFilter={mismatchFilter}
            total={findingsTotal}
            ruleFilter={ruleFilter}
            page={findingsPage}
            onPageChange={setFindingsPage}
            onRuleFilterChange={(v) => {
              setFindingsPage(0)
              setRuleFilter(v)
            }}
            onMismatchFilterChange={(v) => {
              setFindingsPage(0)
              setMismatchFilter(v)
            }}
            onShowResolvedChange={(v) => {
              setFindingsPage(0)
              setShowResolved(v)
            }}
            onSeverityFilterChange={(v) => {
              setFindingsPage(0)
              setSeverityFilter(v)
            }}
            onFindingResolved={loadFindings}
          />
        )}
      </main>

      {editingMaster && (
        <LedgerMappingPanel
          master={editingMaster}
          onClose={() => setEditingMaster(null)}
          onSaved={() => {
            setEditingMaster(null)
            loadMasters()
            loadOpenFindingsCount()
            if (activeTab === 'findings') loadFindings()
          }}
        />
      )}
    </AppShell>
  )
}

function CategoryBadge({ category }: { category: string }) {
  const COLORS: Record<string, string> = {
    PURCHASE: 'text-[var(--color-warning)] bg-[var(--color-warning-bg)]',
    EXPENSE: 'text-[var(--color-danger)] bg-[var(--color-danger-bg)]',
    INCOME: 'text-[var(--color-success)] bg-[var(--color-success-bg)]',
    GST: 'text-[var(--color-info)] bg-[var(--color-info-bg)]',
    TDS: 'text-[var(--color-primary)] bg-[var(--color-primary-light)]',
    CREDITOR: 'text-[#7C3AED] bg-[#EDE9FE]',
    OTHER: 'text-[#64748B] bg-[var(--color-surface-raised)]',
  }
  return (
    <span
      className={`text-xs px-2 py-0.5 rounded-full font-normal ${COLORS[category] ?? COLORS.OTHER}`}
    >
      {category}
    </span>
  )
}

function GstBadge({ type }: { type: string }) {
  const COLORS: Record<string, string> = {
    TAXABLE: 'text-[var(--color-success)] bg-[var(--color-success-bg)]',
    EXEMPT: 'text-[#64748B] bg-[var(--color-surface-raised)]',
    ZERO_RATED: 'text-[var(--color-info)] bg-[var(--color-info-bg)]',
    NON_GST: 'text-[#64748B] bg-[var(--color-surface-raised)]',
    RCM: 'text-[var(--color-warning)] bg-[var(--color-warning-bg)]',
    NOT_APPLICABLE: 'text-[var(--color-text-muted)] bg-[var(--color-surface-raised)]',
  }
  const labels: Record<string, string> = {
    TAXABLE: 'Taxable',
    EXEMPT: 'Exempt',
    ZERO_RATED: 'Zero-rated',
    NON_GST: 'Non-GST',
    RCM: 'RCM',
    NOT_APPLICABLE: 'Not Applicable',
  }
  return (
    <span className={`text-xs px-2 py-0.5 rounded-full font-normal ${COLORS[type] ?? ''}`}>
      {labels[type] ?? type}
    </span>
  )
}

function FindingsTab({
  findings,
  loading,
  error,
  showResolved,
  severityFilter,
  mismatchFilter,
  total,
  ruleFilter,
  page,
  onPageChange,
  onRuleFilterChange,
  onMismatchFilterChange,
  onShowResolvedChange,
  onSeverityFilterChange,
  onFindingResolved,
}: {
  findings: ValidationFinding[]
  loading: boolean
  error: string
  showResolved: boolean
  severityFilter: string
  mismatchFilter: string
  total: number
  ruleFilter: string
  page: number
  onPageChange: (p: number) => void
  onRuleFilterChange: (v: string) => void
  onMismatchFilterChange: (v: string) => void
  onShowResolvedChange: (v: boolean) => void
  onSeverityFilterChange: (v: string) => void
  onFindingResolved: () => void
}) {
  const [overridingId, setOverridingId] = useState<string | null>(null)
  const [overrideNote, setOverrideNote] = useState('')
  const [overrideValue, setOverrideValue] = useState('')
  const [rejectingId, setRejectingId] = useState<string | null>(null)
  const [rejectNote, setRejectNote] = useState('')
  const [busyId, setBusyId] = useState<string | null>(null)
  const role = useAuthStore((st) => st.user?.role)
  // Server is the authority; these only decide which buttons to show.
  const isAccountant = role === 'ROLE_ACCOUNTANT'
  const canSubmit = isAccountant || role === 'ROLE_OPERATOR'
  const [findingErrors, setFindingErrors] = useState<Record<string, string>>({})
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [bulkBusy, setBulkBusy] = useState(false)
  const [bulkError, setBulkError] = useState('')
  const [bulkWorking, setBulkWorking] = useState('')
  const [bulkResult, setBulkResult] = useState('')
  const [bulkFailures, setBulkFailures] = useState<BulkFailure[]>([])
  // "Select all N matching": the action then targets every finding matching the filters, not just this page.
  const [allMatching, setAllMatching] = useState(false)
  // True server counts of what a bulk override / approve would touch under the current filters.
  const [matchedOverride, setMatchedOverride] = useState(0)
  const [matchedApprove, setMatchedApprove] = useState(0)
  // Bulk TDS / GSTIN: one value is applied to the ticked findings of that rule, repeated per value.
  const [matchedTds, setMatchedTds] = useState(0)
  const [matchedGstin, setMatchedGstin] = useState(0)
  const [bulkTdsSection, setBulkTdsSection] = useState('')

  const SEVERITY_BORDER: Record<string, string> = {
    HIGH: 'border-l-[var(--color-danger)]',
    MEDIUM: 'border-l-[var(--color-warning)]',
    LOW: 'border-l-[var(--color-info)]',
    ERROR: 'border-l-[var(--color-danger)]',
    WARNING: 'border-l-[var(--color-warning)]',
    INFO: 'border-l-[var(--color-info)]',
  }
  const SEVERITY_BADGE: Record<string, string> = {
    HIGH: 'text-[var(--color-danger)] bg-[var(--color-danger-bg)]',
    MEDIUM: 'text-[var(--color-warning)] bg-[var(--color-warning-bg)]',
    LOW: 'text-[var(--color-info)] bg-[var(--color-info-bg)]',
    ERROR: 'text-[var(--color-danger)] bg-[var(--color-danger-bg)]',
    WARNING: 'text-[var(--color-warning)] bg-[var(--color-warning-bg)]',
    INFO: 'text-[var(--color-info)] bg-[var(--color-info-bg)]',
  }

  const isResolved = (f: ValidationFinding) =>
    f.resolveStatus === 'RESOLVED' || f.resolveStatus === 'APPROVED' || f.resolveStatus === 'DISCARDED'

  const actionUrl = (f: ValidationFinding) => `/v1/uploads/${f.uploadJobId}/findings/${f.id}/action`

  function openOverride(finding: ValidationFinding) {
    if (overridingId === finding.id) {
      setOverridingId(null)
      return
    }
    // A rejected proposal comes back pre-filled so it can be corrected and resubmitted.
    setOverrideValue(finding.proposedValue ?? '')
    setOverrideNote(finding.resolveNote ?? '')
    setRejectingId(null)
    setOverridingId(finding.id)
  }

  async function runAction(
    finding: ValidationFinding,
    body: Record<string, string>,
    fallbackError: string,
  ): Promise<boolean> {
    setBusyId(finding.id)
    setFindingErrors((prev) => ({ ...prev, [finding.id]: '' }))
    try {
      await api.post(actionUrl(finding), body)
      onFindingResolved()
      return true
    } catch (err) {
      setFindingErrors((prev) => ({ ...prev, [finding.id]: apiErrorMessage(err, fallbackError) }))
      return false
    } finally {
      setBusyId(null)
    }
  }

  async function handleOverride(finding: ValidationFinding) {
    const body: Record<string, string> = { action: 'ACKNOWLEDGE', note: overrideNote }
    if (TYPED_INPUT_RULES.includes(finding.ruleCode)) body.value = overrideValue.trim()
    const ok = await runAction(finding, body, 'Could not resolve this finding. Try again.')
    if (ok) {
      setOverridingId(null)
      setOverrideNote('')
      setOverrideValue('')
    }
  }

  async function handleApprove(finding: ValidationFinding) {
    await runAction(finding, { action: 'APPROVE' }, 'Could not approve this finding. Try again.')
  }

  async function handleReject(finding: ValidationFinding) {
    const ok = await runAction(
      finding,
      { action: 'REJECT', note: rejectNote.trim() },
      'Could not reject this finding. Try again.',
    )
    if (ok) {
      setRejectingId(null)
      setRejectNote('')
    }
  }

  // Bulk actions only cover findings whose corrected value the server derives itself.
  const canBulkOverride = (f: ValidationFinding) =>
    canSubmit &&
    f.resolveStatus === 'OPEN' &&
    f.ruleCode === 'MISMATCH_DETECTION' &&
    f.mismatchType != null &&
    DERIVABLE_MISMATCH_TYPES.includes(f.mismatchType) &&
    (!APPLICABILITY_MISMATCH_TYPES.includes(f.mismatchType) ||
      f.actualValue === 'true' ||
      f.actualValue === 'false')
  const canBulkTyped = (f: ValidationFinding, rule: string) =>
    canSubmit && f.resolveStatus === 'OPEN' && f.ruleCode === rule
  const canBulkApprove = (f: ValidationFinding) => isAccountant && f.resolveStatus === 'ACKNOWLEDGED'

  // The filters currently applied to the list, as the bulk endpoint's filter mode takes them.
  const filterBody = (): Record<string, unknown> => {
    const body: Record<string, unknown> = { allMatching: true }
    if (mismatchFilter) body.mismatchType = mismatchFilter
    if (ruleFilter) body.ruleCode = ruleFilter
    if (severityFilter) body.severity = severityFilter
    return body
  }

  async function runBulk(
    action: 'ACKNOWLEDGE' | 'APPROVE',
    ids: string[] | null,
    value?: string,
    matched?: number,
  ) {
    if (!ids) {
      const count = matched ?? (action === 'APPROVE' ? matchedApprove : matchedOverride)
      const verb = action === 'APPROVE' ? 'Approve' : isAccountant ? 'Resolve' : 'Submit for review'
      // Reaches beyond the rows on this page: say exactly how many findings it covers.
      if (!window.confirm(`${verb} all ${count} matching findings, not just the ${visibleFindings.length} on this page?`)) return
    }
    const count = ids ? ids.length : matched ?? (action === 'APPROVE' ? matchedApprove : matchedOverride)
    setBulkBusy(true)
    setBulkError('')
    setBulkResult('')
    setBulkFailures([])
    setBulkWorking(
      `${action === 'APPROVE' ? 'Approving' : isAccountant ? 'Resolving' : 'Submitting'} ${count} finding${count === 1 ? '' : 's'}… this can take a little while for large selections.`,
    )
    const body: Record<string, unknown> = ids ? { findingIds: ids } : filterBody()
    body.action = action
    if (value) {
      body.value = value
      // The value implies the rule; a stale rule filter must not clash with it.
      delete body.ruleCode
    }
    try {
      const jobId = findings[0]?.uploadJobId
      const res = await api.post(`/v1/uploads/${jobId}/findings/bulk-action`, body)
      const done = Number(res.data?.processed ?? count)
      const noun = `${done} finding${done === 1 ? '' : 's'}`
      setBulkResult(
        action === 'APPROVE' ? `Approved ${noun}.` : isAccountant ? `Resolved ${noun}.` : `Submitted ${noun} for review.`,
      )
      setSelectedIds(new Set())
      setAllMatching(false)
      onFindingResolved()
    } catch (err) {
      setBulkError(apiErrorMessage(err, 'Could not apply the bulk action. Nothing was changed.'))
      const failures = (err as { response?: { data?: { failures?: BulkFailure[] } } })?.response?.data?.failures
      setBulkFailures(failures ?? [])
    } finally {
      setBulkBusy(false)
      setBulkWorking('')
    }
  }

  function toggleSelected(id: string) {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  // What the master will take if this finding is approved.
  const proposedDisplay = (f: ValidationFinding) =>
    TYPED_INPUT_RULES.includes(f.ruleCode) ? f.proposedValue : f.actualValue

  // Every filter, the rule type included, is applied by the server so paging and counts are exact.
  const filteredFindings = findings
  const openFindings = filteredFindings.filter((f) => !isResolved(f))
  // "Show resolved" lists closed findings only; otherwise the open and pending ones.
  const visibleFindings = showResolved ? filteredFindings.filter(isResolved) : openFindings
  const overridable = visibleFindings.filter(canBulkOverride)
  const tdsEligible = visibleFindings.filter((f) => canBulkTyped(f, 'TDS_SECTION_MAPPING'))
  const gstinEligible = visibleFindings.filter((f) => canBulkTyped(f, 'GSTIN_PRESENCE'))
  const selectedTds = tdsEligible.filter((f) => selectedIds.has(f.id))
  const selectedGstin = gstinEligible.filter((f) => selectedIds.has(f.id))
  const approvable = visibleFindings.filter(canBulkApprove)
  const selectedOverridable = overridable.filter((f) => selectedIds.has(f.id))
  const selectedApprovable = approvable.filter((f) => selectedIds.has(f.id))
  const totalPages = Math.max(1, Math.ceil(total / FINDINGS_PAGE_SIZE))
  const jobId = findings[0]?.uploadJobId

  // Server counts for the current filters; refreshed whenever the list is (reload, page, filter, after an action).
  useEffect(() => {
    if (!jobId || showResolved) return
    let live = true
    const count = async (action: 'ACKNOWLEDGE' | 'APPROVE') => {
      const res = await api.post(`/v1/uploads/${jobId}/findings/bulk-action`, {
        ...filterBody(),
        action,
        dryRun: true,
      })
      return Number(res.data?.matched ?? 0)
    }
    const safe = (p: Promise<number>) => p.catch(() => 0)
    safe(canSubmit ? count('ACKNOWLEDGE') : Promise.resolve(0)).then((n) => live && setMatchedOverride(n))
    safe(isAccountant ? count('APPROVE') : Promise.resolve(0)).then((n) => live && setMatchedApprove(n))
    // A typed value implies its rule, so these only make sense when the filters leave room for that rule.
    const typedCount = async (rule: string, value: string) => {
      if (mismatchFilter || (ruleFilter && ruleFilter !== rule)) return 0
      const body: Record<string, unknown> = { ...filterBody(), action: 'ACKNOWLEDGE', dryRun: true, value }
      delete body.ruleCode
      const res = await api.post(`/v1/uploads/${jobId}/findings/bulk-action`, body)
      return Number(res.data?.matched ?? 0)
    }
    safe(canSubmit ? typedCount('TDS_SECTION_MAPPING', '194C') : Promise.resolve(0)).then((n) => live && setMatchedTds(n))
    safe(canSubmit ? typedCount('GSTIN_PRESENCE', GST_NOT_APPLICABLE) : Promise.resolve(0)).then(
      (n) => live && setMatchedGstin(n),
    )
    return () => {
      live = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [findings, showResolved, canSubmit, isAccountant])

  const RULE_LABELS: Record<string, string> = {
    MISMATCH_DETECTION: 'Mismatch Detection',
    GSTIN_PRESENCE: 'GSTIN Presence',
    GST_APPLICABILITY: 'GST Applicability',
    HSN_SAC_CODE: 'HSN/SAC Code',
    TDS_SECTION_MAPPING: 'TDS Section Mapping',
  }


  const filterBar = (
    <div className="bg-[var(--color-surface)] border border-[var(--color-border)] rounded-[var(--radius-md)] p-4 shadow-[var(--shadow-sm)] mb-4 flex flex-wrap gap-3 items-center">
      <select
        value={severityFilter}
        disabled={bulkBusy}
        onChange={(e) => onSeverityFilterChange(e.target.value)}
        className="h-10 px-3 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface-raised)] text-sm text-[var(--color-text-primary)]"
      >
        <option value="">All Severities</option>
        <option value="HIGH">HIGH</option>
        <option value="MEDIUM">MEDIUM</option>
        <option value="LOW">LOW</option>
      </select>
      <select
        value={ruleFilter}
        aria-label="Filter by rule type"
        disabled={bulkBusy}
        onChange={(e) => {
          setSelectedIds(new Set())
          setAllMatching(false)
          onRuleFilterChange(e.target.value)
        }}
        className="h-10 px-3 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface-raised)] text-sm text-[var(--color-text-primary)]"
      >
        <option value="">All Types</option>
        {Object.keys(RULE_LABELS).map((code) => (
          <option key={code} value={code}>
            {RULE_LABELS[code] ?? code}
          </option>
        ))}
      </select>
      <select
        value={mismatchFilter}
        onChange={(e) => {
          setSelectedIds(new Set())
          onMismatchFilterChange(e.target.value)
        }}
        aria-label="Filter by reason"
        disabled={bulkBusy}
        className="h-10 px-3 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface-raised)] text-sm text-[var(--color-text-primary)]"
      >
        <option value="">All reasons</option>
        {Object.entries(MISMATCH_REASONS).map(([code, label]) => (
          <option key={code} value={code}>
            {label}
          </option>
        ))}
      </select>
      <label className="flex items-center gap-2 text-sm text-[var(--color-text-secondary)] cursor-pointer">
        <input
          type="checkbox"
          checked={showResolved}
          disabled={bulkBusy}
          onChange={(e) => onShowResolvedChange(e.target.checked)}
          className="rounded"
        />
        Show resolved
      </label>
      {bulkWorking && (
        <div className="basis-full flex items-center gap-2 text-sm text-[var(--color-text-secondary)]" role="status">
          <Loader2 className="animate-spin" size={16} />
          {bulkWorking}
        </div>
      )}
      {!bulkWorking && bulkResult && (
        <div className="basis-full text-sm text-[var(--color-success)]" role="status">
          {bulkResult}
        </div>
      )}
    </div>
  )

  if (loading)
    return (
      <div id="findings-panel" role="tabpanel">
        {filterBar}
        <div className="flex justify-center py-20">
          <Loader2 className="animate-spin text-[var(--color-text-muted)]" size={32} />
        </div>
      </div>
    )
  if (error)
    return (
      <div id="findings-panel" role="tabpanel">
        {filterBar}
        <div className="text-[var(--color-danger)] text-sm py-10 text-center">{error}</div>
      </div>
    )

  if (filteredFindings.length === 0)
    return (
      <div id="findings-panel" role="tabpanel">
        {filterBar}
        <div className="flex flex-col items-center py-20 gap-3">
          <CheckCircle2 size={48} className="text-[var(--color-success)]" />
          <p className="text-sm font-semibold text-[var(--color-text-primary)]">
            {severityFilter || ruleFilter ? 'No matching findings' : 'No findings'}
          </p>
          <p className="text-sm text-[var(--color-text-muted)]">
            {severityFilter || ruleFilter
              ? 'Try adjusting your filters.'
              : 'Upload a masters file to run validation checks.'}
          </p>
        </div>
      </div>
    )
  if (visibleFindings.length === 0 && showResolved)
    return (
      <div id="findings-panel" role="tabpanel">
        {filterBar}
        <div className="flex flex-col items-center py-20 gap-3">
          <p className="text-sm font-semibold text-[var(--color-text-primary)]">No resolved findings</p>
        </div>
      </div>
    )
  if (openFindings.length === 0 && !showResolved)
    return (
      <div id="findings-panel" role="tabpanel">
        {filterBar}
        <div className="flex flex-col items-center py-20 gap-3">
          <CheckCircle2 size={48} className="text-[var(--color-success)]" />
          <p className="text-sm font-semibold text-[var(--color-text-primary)]">
            All findings resolved
          </p>
          <p className="text-sm text-[var(--color-text-muted)]">
            Every ledger has passed the TDS and GST mapping checks.
          </p>
        </div>
      </div>
    )

  const pageSelected = selectedOverridable.length + selectedApprovable.length + selectedTds.length + selectedGstin.length
  const pageEligible = overridable.length + approvable.length + tdsEligible.length + gstinEligible.length
  const allOnPageSelected = pageEligible > 0 && pageSelected === pageEligible
  const matchedTotal = matchedOverride + matchedApprove + matchedTds + matchedGstin
  const btn =
    'h-9 px-3 rounded-[var(--radius-md)] border border-[var(--color-border)] disabled:opacity-40'
  const overrideVerb = isAccountant ? 'Resolve' : 'Submit for review'
  // With "all matching" chosen, a button covers the server's count; otherwise just the ticked rows.
  const overrideCount = allMatching ? matchedOverride : selectedOverridable.length
  const approveCount = allMatching ? matchedApprove : selectedApprovable.length
  const tdsCount = allMatching ? matchedTds : selectedTds.length
  const gstinCount = allMatching ? matchedGstin : selectedGstin.length

  // Open findings that need a typed value (GSTIN, HSN/SAC, TDS section, ...): bulk leaves them out by design.
  const typedOnly = canSubmit && !showResolved
    ? visibleFindings.filter(
        (f) =>
          f.resolveStatus === 'OPEN' &&
          !canBulkOverride(f) &&
          f.ruleCode !== 'TDS_SECTION_MAPPING' &&
          f.ruleCode !== 'GSTIN_PRESENCE',
      )
    : []
  const typedNote =
    typedOnly.length > 0 ? (
      <p className="text-xs text-[var(--color-text-muted)] mb-3" role="note">
        {typedOnly.length} finding{typedOnly.length === 1 ? '' : 's'} on this page need
        {typedOnly.length === 1 ? 's' : ''} a typed value (such as an HSN/SAC code or GST applicability), so
        bulk actions are not available for {typedOnly.length === 1 ? 'it' : 'them'}. Resolve
        {typedOnly.length === 1 ? ' it' : ' them'} one at a time.
      </p>
    ) : null

  const bulkBar =
    !showResolved && (pageEligible > 0 || matchedTotal > 0) ? (
      <div
        className="bg-[var(--color-surface)] border border-[var(--color-border)] rounded-[var(--radius-md)] p-3 mb-3 flex flex-wrap gap-3 items-center text-sm"
        aria-label="Bulk actions"
      >
        <label className="flex items-center gap-2 text-[var(--color-text-secondary)] cursor-pointer">
          <input
            type="checkbox"
            aria-label="Select all"
            checked={allOnPageSelected}
            disabled={bulkBusy}
            onChange={(e) => {
              setAllMatching(false)
              setSelectedIds(
                e.target.checked
                  ? new Set([...overridable, ...approvable, ...tdsEligible, ...gstinEligible].map((f) => f.id))
                  : new Set(),
              )
            }}
          />
          Select all
        </label>
        {overrideCount > 0 && (
          <button
            disabled={bulkBusy}
            onClick={() =>
              allMatching ? runBulk('ACKNOWLEDGE', null) : runBulk('ACKNOWLEDGE', selectedOverridable.map((f) => f.id))
            }
            className={btn}
          >
            {overrideVerb} selected ({overrideCount})
          </button>
        )}
        {tdsCount > 0 && (
          <span className="flex items-center gap-2">
            <TdsSectionSelect
              ariaLabel="TDS section for selected"
              value={bulkTdsSection}
              disabled={bulkBusy}
              placeholder="TDS section…"
              onChange={setBulkTdsSection}
              className="h-9 px-2 max-w-72 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface-raised)]"
            />
            <button
              disabled={bulkBusy || !bulkTdsSection}
              onClick={() =>
                allMatching
                  ? runBulk('ACKNOWLEDGE', null, bulkTdsSection, matchedTds)
                  : runBulk('ACKNOWLEDGE', selectedTds.map((f) => f.id), bulkTdsSection)
              }
              className={btn}
            >
              {overrideVerb} TDS section ({tdsCount})
            </button>
          </span>
        )}
        {gstinCount > 0 && (
          <button
            disabled={bulkBusy}
            onClick={() =>
              allMatching
                ? runBulk('ACKNOWLEDGE', null, GST_NOT_APPLICABLE, matchedGstin)
                : runBulk('ACKNOWLEDGE', selectedGstin.map((f) => f.id), GST_NOT_APPLICABLE)
            }
            className={btn}
          >
            GST not applicable ({gstinCount})
          </button>
        )}
        {approveCount > 0 && (
          <button
            disabled={bulkBusy}
            onClick={() =>
              allMatching ? runBulk('APPROVE', null) : runBulk('APPROVE', selectedApprovable.map((f) => f.id))
            }
            className={btn}
          >
            Approve selected ({approveCount})
          </button>
        )}
        {allOnPageSelected && !allMatching && matchedTotal > pageSelected && (
          <div className="basis-full text-[var(--color-text-secondary)]">
            {pageSelected} on this page selected.{' '}
            <button
              className="underline text-[var(--color-primary)]"
              disabled={bulkBusy}
              onClick={() => setAllMatching(true)}
            >
              Select all {matchedTotal} matching
            </button>
          </div>
        )}
        {allMatching && (
          <div className="basis-full text-[var(--color-text-secondary)]">
            All {matchedTotal} matching findings selected.{' '}
            <button
              className="underline text-[var(--color-primary)]"
              disabled={bulkBusy}
              onClick={() => {
                setAllMatching(false)
                setSelectedIds(new Set())
              }}
            >
              Clear selection
            </button>
          </div>
        )}
        {bulkError && (
          <div className="basis-full text-xs text-[var(--color-danger)]" role="alert">
            <p>{bulkError}</p>
            {bulkFailures.length > 0 && (
              <ul className="list-disc pl-5 mt-1">
                {bulkFailures.map((f) => (
                  <li key={f.findingId}>
                    {f.ledgerName ?? f.findingId}: {f.error}
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}
      </div>
    ) : null

  const pageWindow = (() => {
    const pages = new Set([0, totalPages - 1, page - 1, page, page + 1].filter((p) => p >= 0 && p < totalPages))
    return [...pages].sort((a, b) => a - b)
  })()
  const goToPage = (p: number) => {
    setSelectedIds(new Set())
    setAllMatching(false)
    onPageChange(p)
  }
  const pager =
    total > FINDINGS_PAGE_SIZE ? (
      <nav aria-label="Findings pages" className="flex flex-wrap items-center gap-2 mt-4 text-sm">
        <span className="text-[var(--color-text-muted)] mr-2">
          {page * FINDINGS_PAGE_SIZE + 1}–{Math.min((page + 1) * FINDINGS_PAGE_SIZE, total)} of {total}
        </span>
        <button className={btn} disabled={bulkBusy || page === 0} onClick={() => goToPage(page - 1)}>
          Previous
        </button>
        {pageWindow.map((p, i) => (
          <span key={p} className="flex items-center gap-2">
            {i > 0 && p - pageWindow[i - 1] > 1 && <span aria-hidden>…</span>}
            <button
              className={btn}
              aria-label={`Page ${p + 1}`}
              aria-current={p === page ? 'page' : undefined}
              disabled={bulkBusy || p === page}
              onClick={() => goToPage(p)}
            >
              {p + 1}
            </button>
          </span>
        ))}
        <button className={btn} disabled={bulkBusy || page >= totalPages - 1} onClick={() => goToPage(page + 1)}>
          Next
        </button>
      </nav>
    ) : null

  return (
    <div id="findings-panel" role="tabpanel">
      {filterBar}
      {bulkBar}
      {typedNote}
      <div className="flex flex-col gap-3">
        {visibleFindings.map((finding) => (
          <div
            key={finding.id}
            className={`bg-[var(--color-surface)] rounded-[var(--radius-md)] border border-[var(--color-border)] border-l-4 ${SEVERITY_BORDER[finding.severity] ?? ''} p-4`}
          >
            <div className="flex items-start gap-4">
              {(canBulkOverride(finding) ||
                canBulkApprove(finding) ||
                canBulkTyped(finding, 'TDS_SECTION_MAPPING') ||
                canBulkTyped(finding, 'GSTIN_PRESENCE')) && (
                <input
                  type="checkbox"
                  aria-label={`Select ${finding.ledgerName}`}
                  checked={selectedIds.has(finding.id)}
                  disabled={bulkBusy}
                  onChange={() => toggleSelected(finding.id)}
                  className="mt-1"
                />
              )}
              <div className="flex-1">
                <div className="flex items-center gap-2 mb-1">
                  <span
                    className={`text-xs px-2 py-0.5 rounded-full uppercase font-normal ${SEVERITY_BADGE[finding.severity] ?? ''}`}
                  >
                    {finding.severity}
                  </span>
                  <span className="text-xs text-[var(--color-text-muted)]">
                    {finding.mismatchType ? MISMATCH_REASONS[finding.mismatchType] ?? finding.mismatchType : finding.ruleCode}
                  </span>
                </div>
                <p className="text-sm font-semibold text-[var(--color-text-primary)]">
                  {finding.ledgerName}
                </p>
                <p className="text-sm text-[var(--color-text-secondary)]">{finding.message}</p>
                {finding.suggestedFix && (
                  <p className="text-xs text-[var(--color-text-muted)] font-mono mt-1">
                    Suggested: {finding.suggestedFix}
                  </p>
                )}
                {isResolved(finding) && (
                  <div className="flex items-center gap-1 mt-1">
                    <CheckCircle2 size={14} className="text-[var(--color-success)]" />
                    <span className="text-xs text-[var(--color-text-muted)]">
                      Resolved
                      {finding.resolveNote && ` — ${finding.resolveNote}`}
                    </span>
                  </div>
                )}
                {finding.resolveStatus === 'ACKNOWLEDGED' && (
                  <div className="mt-2 text-xs text-[var(--color-text-secondary)]">
                    <span className="inline-block px-2 py-0.5 rounded-full bg-[var(--color-warning-bg)] text-[var(--color-warning)]">
                      Pending accountant review
                    </span>
                    <p className="mt-1">
                      Proposed value: <span className="font-mono">{proposedDisplay(finding) ?? '—'}</span>
                      {finding.submittedBy && ` · submitted by ${finding.submittedBy}`}
                    </p>
                    {finding.resolveNote && <p>Note: {finding.resolveNote}</p>}
                  </div>
                )}
                {finding.resolveStatus === 'OPEN' && finding.reviewNote && (
                  <p className="mt-2 text-xs text-[var(--color-danger)]">
                    Rejected{finding.reviewedBy ? ` by ${finding.reviewedBy}` : ''}: {finding.reviewNote}
                  </p>
                )}
                {findingErrors[finding.id] && (
                  <p className="text-xs text-[var(--color-danger)] mt-1">
                    {findingErrors[finding.id]}
                  </p>
                )}
              </div>
              {finding.resolveStatus === 'OPEN' && canSubmit && (
                <div className="flex gap-2 flex-shrink-0">
                  <button
                    onClick={() => openOverride(finding)}
                    aria-expanded={overridingId === finding.id}
                    aria-label={`Override Value for ${finding.ledgerName}`}
                    className="h-9 px-3 rounded-[var(--radius-md)] text-sm text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-raised)]"
                  >
                    Override Value
                  </button>
                </div>
              )}
              {finding.resolveStatus === 'ACKNOWLEDGED' && isAccountant && (
                <div className="flex gap-2 flex-shrink-0">
                  <button
                    onClick={() => handleApprove(finding)}
                    disabled={busyId === finding.id || bulkBusy}
                    aria-label={`Approve ${finding.ledgerName}`}
                    className="h-9 px-3 bg-[var(--color-primary)] text-white rounded-[var(--radius-md)] text-sm disabled:opacity-50"
                  >
                    Approve
                  </button>
                  <button
                    onClick={() => {
                      setRejectingId(rejectingId === finding.id ? null : finding.id)
                      setRejectNote('')
                    }}
                    aria-expanded={rejectingId === finding.id}
                    aria-label={`Reject ${finding.ledgerName}`}
                    className="h-9 px-3 rounded-[var(--radius-md)] border border-[var(--color-border)] text-sm text-[var(--color-text-primary)] hover:bg-[var(--color-surface-raised)]"
                  >
                    Reject
                  </button>
                </div>
              )}
            </div>
            {overridingId === finding.id && finding.resolveStatus === 'OPEN' && (
              <div className="mt-3 bg-[var(--color-surface-raised)] rounded-[var(--radius-md)] p-4">
                {TYPED_INPUT_RULES.includes(finding.ruleCode) ? (
                  <div className="mb-2">
                    <label className="block text-xs text-[var(--color-text-secondary)] mb-1">
                      {VALUE_LABELS[finding.ruleCode]} (required)
                      {finding.ruleCode === 'TDS_SECTION_MAPPING' ? (
                        <TdsSectionSelect
                          value={overrideValue}
                          onChange={setOverrideValue}
                          className="mt-1 w-full h-10 px-3 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] text-sm text-[var(--color-text-primary)]"
                        />
                      ) : finding.ruleCode === 'GST_APPLICABILITY' ? (
                        <select
                          value={overrideValue}
                          onChange={(e) => setOverrideValue(e.target.value)}
                          className="mt-1 w-full h-10 px-3 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] text-sm text-[var(--color-text-primary)]"
                        >
                          <option value="">Select…</option>
                          {GST_APPLICABILITY_TYPES.map((t) => (
                            <option key={t} value={t}>
                              {t}
                            </option>
                          ))}
                        </select>
                      ) : (
                        <input
                          type="text"
                          value={overrideValue}
                          disabled={overrideValue === GST_NOT_APPLICABLE}
                          onChange={(e) => setOverrideValue(e.target.value)}
                          className="mt-1 w-full h-10 px-3 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] text-sm text-[var(--color-text-primary)] focus:outline-none focus:border-[var(--color-primary)] disabled:opacity-50"
                        />
                      )}
                    </label>
                    {finding.ruleCode === 'GSTIN_PRESENCE' && (
                      <label className="mt-2 flex items-center gap-2 text-xs text-[var(--color-text-secondary)]">
                        <input
                          type="checkbox"
                          checked={overrideValue === GST_NOT_APPLICABLE}
                          onChange={(e) => setOverrideValue(e.target.checked ? GST_NOT_APPLICABLE : '')}
                        />
                        GST not applicable for this ledger
                      </label>
                    )}
                  </div>
                ) : finding.mismatchType === 'MISSING_IN_CONFIGURATION' ? (
                  <p className="mb-2 text-xs text-[var(--color-text-secondary)]">
                    <span className="font-mono">{finding.ledgerName}</span> will be added to the approved masters.
                  </p>
                ) : (
                  <p className="mb-2 text-xs text-[var(--color-text-secondary)]">
                    The master will be updated to:{' '}
                    <span className="font-mono">{finding.actualValue ?? '—'}</span>
                  </p>
                )}
                <textarea
                  rows={2}
                  value={overrideNote}
                  onChange={(e) => setOverrideNote(e.target.value)}
                  placeholder="Add a note explaining the override (optional)…"
                  className="w-full px-3 py-2 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] text-sm text-[var(--color-text-primary)] resize-none focus:outline-none focus:border-[var(--color-primary)]"
                />
                <div className="flex gap-2 mt-2">
                  <button
                    onClick={() => handleOverride(finding)}
                    disabled={
                      busyId === finding.id ||
                      bulkBusy ||
                      (TYPED_INPUT_RULES.includes(finding.ruleCode) && overrideValue.trim() === '')
                    }
                    className="h-9 px-3 bg-[var(--color-primary)] text-white rounded-[var(--radius-md)] text-sm disabled:opacity-50"
                  >
                    {isAccountant ? 'Confirm Override' : 'Submit for Review'}
                  </button>
                  <button
                    onClick={() => {
                      setOverridingId(null)
                      setOverrideNote('')
                      setOverrideValue('')
                    }}
                    className="h-9 px-3 text-sm text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-raised)] rounded-[var(--radius-md)]"
                  >
                    Cancel Changes
                  </button>
                </div>
              </div>
            )}
            {rejectingId === finding.id && finding.resolveStatus === 'ACKNOWLEDGED' && isAccountant && (
              <div className="mt-3 bg-[var(--color-surface-raised)] rounded-[var(--radius-md)] p-4">
                <textarea
                  rows={2}
                  value={rejectNote}
                  onChange={(e) => setRejectNote(e.target.value)}
                  placeholder="Explain why this is rejected (required)…"
                  className="w-full px-3 py-2 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] text-sm text-[var(--color-text-primary)] resize-none focus:outline-none focus:border-[var(--color-primary)]"
                />
                <div className="flex gap-2 mt-2">
                  <button
                    onClick={() => handleReject(finding)}
                    disabled={busyId === finding.id || bulkBusy || rejectNote.trim() === ''}
                    className="h-9 px-3 bg-[var(--color-danger)] text-white rounded-[var(--radius-md)] text-sm disabled:opacity-50"
                  >
                    Confirm Reject
                  </button>
                  <button
                    onClick={() => {
                      setRejectingId(null)
                      setRejectNote('')
                    }}
                    className="h-9 px-3 text-sm text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-raised)] rounded-[var(--radius-md)]"
                  >
                    Cancel
                  </button>
                </div>
              </div>
            )}
          </div>
        ))}
      </div>
      {pager}
    </div>
  )
}
