import { useState, useEffect, useRef } from 'react'
import { api } from '@/lib/api'
import { Database, Upload, SearchX, CheckCircle2, Loader2, AlertCircle } from 'lucide-react'
import { LedgerMappingPanel } from '@/components/LedgerMappingPanel'
import { AppShell } from '@/components/AppShell'

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
  ledgerName: string
  category: string
  severity: 'HIGH' | 'MEDIUM' | 'LOW' | 'INFO' | 'WARNING' | 'ERROR'
  message: string
  suggestedFix: string | null
  resolveStatus: 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED' | 'ACCEPTED' | 'OVERRIDDEN'
  resolveNote: string | null
  resolvedBy: string | null
}

export function MastersPage() {
  const [activeTab, setActiveTab] = useState<'ledgers' | 'findings'>('ledgers')
  const [masters, setMasters] = useState<PreconfiguredMaster[]>([])
  const [findings, setFindings] = useState<ValidationFinding[]>([])
  const [openFindingsCount, setOpenFindingsCount] = useState(0)
  const [loading, setLoading] = useState(true)
  const [findingsLoading, setFindingsLoading] = useState(false)
  const [error, setError] = useState('')
  const [findingsError, setFindingsError] = useState('')
  const [categoryFilter, setCategoryFilter] = useState('')
  const [severityFilter, setSeverityFilter] = useState('')
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
      const params = new URLSearchParams({ size: '100', showResolved: String(showResolved) })
      if (severityFilter) params.set('severity', severityFilter)
      const res = await api.get(`/v1/uploads/latest/mismatches?${params}`)
      const items: ValidationFinding[] = res.data.content ?? []
      setFindings(items)
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
  }, [activeTab, showResolved, severityFilter])

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
        {activeTab === 'findings' && (
          <FindingsTab
            findings={findings}
            loading={findingsLoading}
            error={findingsError}
            showResolved={showResolved}
            severityFilter={severityFilter}
            onShowResolvedChange={setShowResolved}
            onSeverityFilterChange={setSeverityFilter}
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
  onShowResolvedChange,
  onSeverityFilterChange,
  onFindingResolved,
}: {
  findings: ValidationFinding[]
  loading: boolean
  error: string
  showResolved: boolean
  severityFilter: string
  onShowResolvedChange: (v: boolean) => void
  onSeverityFilterChange: (v: string) => void
  onFindingResolved: () => void
}) {
  const [overridingId, setOverridingId] = useState<string | null>(null)
  const [overrideNote, setOverrideNote] = useState('')
  const [acceptingId, setAcceptingId] = useState<string | null>(null)
  const [findingErrors, setFindingErrors] = useState<Record<string, string>>({})
  const [ruleFilter, setRuleFilter] = useState('')

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
    f.resolveStatus === 'RESOLVED' ||
    f.resolveStatus === 'ACCEPTED' ||
    f.resolveStatus === 'OVERRIDDEN'

  async function handleAccept(finding: ValidationFinding) {
    setAcceptingId(finding.id)
    setFindingErrors((prev) => ({ ...prev, [finding.id]: '' }))
    try {
      await api.patch(
        `/v1/uploads/${finding.uploadJobId}/mismatches/${finding.id}/resolve`,
        { status: 'ACCEPTED' },
      )
      onFindingResolved()
    } catch {
      setFindingErrors((prev) => ({
        ...prev,
        [finding.id]: 'Could not accept this finding. Try again.',
      }))
    } finally {
      setAcceptingId(null)
    }
  }

  async function handleOverride(finding: ValidationFinding) {
    setFindingErrors((prev) => ({ ...prev, [finding.id]: '' }))
    try {
      await api.patch(
        `/v1/uploads/${finding.uploadJobId}/mismatches/${finding.id}/resolve`,
        { status: 'OVERRIDDEN', note: overrideNote },
      )
      setOverridingId(null)
      setOverrideNote('')
      onFindingResolved()
    } catch {
      setFindingErrors((prev) => ({
        ...prev,
        [finding.id]: 'Could not resolve this finding. Try again.',
      }))
    }
  }

  const filteredFindings = ruleFilter
    ? findings.filter((f) => f.ruleCode === ruleFilter)
    : findings
  const openFindings = filteredFindings.filter((f) => !isResolved(f))

  const RULE_LABELS: Record<string, string> = {
    MISMATCH_DETECTION: 'Mismatch Detection',
    GSTIN_PRESENCE: 'GSTIN Presence',
    GST_APPLICABILITY: 'GST Applicability',
    HSN_SAC_CODE: 'HSN/SAC Code',
    TDS_SECTION_MAPPING: 'TDS Section Mapping',
  }

  const availableRules = [...new Set(findings.map((f) => f.ruleCode))].sort()

  const filterBar = (
    <div className="bg-[var(--color-surface)] border border-[var(--color-border)] rounded-[var(--radius-md)] p-4 shadow-[var(--shadow-sm)] mb-4 flex flex-wrap gap-3 items-center">
      <select
        value={severityFilter}
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
        onChange={(e) => setRuleFilter(e.target.value)}
        className="h-10 px-3 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface-raised)] text-sm text-[var(--color-text-primary)]"
      >
        <option value="">All Types</option>
        {availableRules.map((code) => (
          <option key={code} value={code}>
            {RULE_LABELS[code] ?? code}
          </option>
        ))}
      </select>
      <label className="flex items-center gap-2 text-sm text-[var(--color-text-secondary)] cursor-pointer">
        <input
          type="checkbox"
          checked={showResolved}
          onChange={(e) => onShowResolvedChange(e.target.checked)}
          className="rounded"
        />
        Show resolved
      </label>
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

  return (
    <div id="findings-panel" role="tabpanel">
      {filterBar}
      <div className="flex flex-col gap-3">
        {(showResolved ? filteredFindings : openFindings).map((finding) => (
          <div
            key={finding.id}
            className={`bg-[var(--color-surface)] rounded-[var(--radius-md)] border border-[var(--color-border)] border-l-4 ${SEVERITY_BORDER[finding.severity] ?? ''} p-4`}
          >
            <div className="flex items-start gap-4">
              <div className="flex-1">
                <div className="flex items-center gap-2 mb-1">
                  <span
                    className={`text-xs px-2 py-0.5 rounded-full uppercase font-normal ${SEVERITY_BADGE[finding.severity] ?? ''}`}
                  >
                    {finding.severity}
                  </span>
                  <span className="text-xs text-[var(--color-text-muted)]">{finding.ruleCode}</span>
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
                      {finding.resolveStatus === 'OVERRIDDEN' ? 'Overridden' : 'Resolved'}
                      {finding.resolveNote && ` — ${finding.resolveNote}`}
                    </span>
                  </div>
                )}
                {findingErrors[finding.id] && (
                  <p className="text-xs text-[var(--color-danger)] mt-1">
                    {findingErrors[finding.id]}
                  </p>
                )}
              </div>
              {!isResolved(finding) && (
                <div className="flex gap-2 flex-shrink-0">
                  <button
                    onClick={() => handleAccept(finding)}
                    disabled={acceptingId === finding.id}
                    aria-label={`Accept Fix for ${finding.ledgerName}`}
                    className="h-9 px-3 rounded-[var(--radius-md)] border border-[var(--color-border)] text-sm text-[var(--color-text-primary)] hover:bg-[var(--color-surface-raised)] disabled:opacity-50"
                  >
                    {acceptingId === finding.id ? 'Accepting…' : 'Accept Fix'}
                  </button>
                  <button
                    onClick={() =>
                      setOverridingId(overridingId === finding.id ? null : finding.id)
                    }
                    aria-expanded={overridingId === finding.id}
                    aria-label={`Override Value for ${finding.ledgerName}`}
                    className="h-9 px-3 rounded-[var(--radius-md)] text-sm text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-raised)]"
                  >
                    Override Value
                  </button>
                </div>
              )}
            </div>
            {overridingId === finding.id && (
              <div className="mt-3 bg-[var(--color-surface-raised)] rounded-[var(--radius-md)] p-4">
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
                    className="h-9 px-3 bg-[var(--color-primary)] text-white rounded-[var(--radius-md)] text-sm"
                  >
                    Confirm Override
                  </button>
                  <button
                    onClick={() => {
                      setOverridingId(null)
                      setOverrideNote('')
                    }}
                    className="h-9 px-3 text-sm text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-raised)] rounded-[var(--radius-md)]"
                  >
                    Cancel Changes
                  </button>
                </div>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}
