# Scenario: TDS applicability gating over HTTP for an individual/HUF (entity Q)
# organization whose FY 2026 was declared not applicable at registration.
# Covers: TDS_PAYMENT / TDS_26Q start refused with a clear 400 while GSTR3B is
# untouched; masters upload for a non-deductor raises no TDS_SECTION_MAPPING
# findings but still flags a malformed PAN; TDS sections set on masters stay
# visible in preconfigured masters (hidden, never cleared); the
# debtor-tans/missing endpoint works for a non-deductor; a finalized TDS
# payment workflow blocks marking the year not applicable with 409; draft TDS
# workflows of a year marked not applicable disappear from the workflow list.
# State hygiene: wf_ state keys are saved/restored so later scenarios (e.g.
# filed-returns-import) still see the bootstrap org's workflows.

TDSAP_TAN="BLRA12345B"

# tdsap_as_indiv: act as the individual org's accountant.
tdsap_as_indiv() { VSA_JWT=$(state_get acct_token_indiv); }
# tdsap_as_indiv_owner: act as the individual org's owner.
tdsap_as_indiv_owner() { VSA_JWT=$(state_get tdsa_indiv_owner_token); }

# tdsap_debtor_daybook <out>: one journal voucher - TDS Receivable debited,
# a Sundry Debtor credited - the shape debtor-tans/missing reports.
tdsap_debtor_daybook() {
  jq -n '{tallymessage: [{
    date: "20260510", vouchertypename: "Journal", vouchernumber: "TDSREC-1",
    guid: "SYN-TDSA-TDSREC-1", partyledgername: "Scenario Sundry Debtor",
    allledgerentries: [
      {ledgername: "TDS Receivable", amount: 5000},
      {ledgername: "Scenario Sundry Debtor", amount: -5000}
    ]}]}' > "$1"
}

run_scenario() {
  bootstrapped || vsa_bootstrap
  local iid; iid=$(state_get tdsa_indiv_id)
  [ -n "$iid" ] || vsa_fail "tds-applicability-registration must run first (no individual org)"
  local saved_wf_2604; saved_wf_2604=$(state_get wf_TDS_PAYMENT_2026-04)
  as_owner
  vsa_org_accountant "$iid" "indiv"

  # (c) TDS_PAYMENT start refused in the not-applicable FY.
  req "start-payment-refused" POST /api/v1/workflows '{"type":"TDS_PAYMENT","periodKey":"2026-05"}'
  expect_status 400
  expect_contains "not applicable"

  # (c) TDS_26Q start refused too.
  req "start-26q-refused" POST /api/v1/workflows '{"type":"TDS_26Q","periodKey":"2026-Q1"}'
  expect_status 400
  expect_contains "not applicable"

  # (d) Masters upload for the non-deductor: no TDS_SECTION_MAPPING findings,
  # malformed PAN still flagged.
  req "upload-indiv-masters" POST /api/v1/uploads "" "$VSA_DIR/scenarios/fixtures/tds-applicability-masters.json"
  expect_status "200|201" || vsa_fail "individual masters upload failed: $RESP"
  local job; job=$(echo "$RESP" | jq -r .id)
  req "indiv-masters-mismatches" GET "/api/v1/uploads/$job/mismatches"
  expect_status 200
  VSA_STEP="no-tds-section-findings"
  if echo "$RESP" | jq -e 'all(.content[]; .ruleCode != "TDS_SECTION_MAPPING")' >/dev/null; then ok=true; else ok=false; fi
  vsa_log_step "$VSA_SCENARIO" "$VSA_STEP" GET "/api/v1/uploads/$job/mismatches" "$STATUS" "no TDS_SECTION_MAPPING" "$ok"
  [ "$ok" = true ] || vsa_fail "TDS_SECTION_MAPPING findings leaked for a non-deductor: $RESP"
  expect_json 'any(.content[]; .ruleCode == "PAN_FORMAT")'

  # (d) Hidden, never cleared: a TDS section set on this org's masters stays on
  # the ledger snapshot even though the org is not a TDS deductor. The snapshot
  # is what the TDS analyzer reads; preconfigured masters rows are seeded at
  # first upload and the snapshot PATCH does not re-sync them.
  local sid; sid=$(vsa_psql "select id from upload_ledger_snapshots where upload_job_id='$job' and ledger_name='Scenario Contractor Expense'" | head -1)
  [ -n "$sid" ] || vsa_fail "no snapshot for Scenario Contractor Expense"
  req "set-indiv-section" PATCH "/api/v1/uploads/$job/ledgers/$sid" '{"tdsApplicable":true,"tdsSection":"194C"}'
  expect_status 200 || vsa_fail "setting a TDS section failed: $RESP"
  VSA_STEP="snapshot-keeps-section"
  local kept; kept=$(vsa_psql "select tds_section from upload_ledger_snapshots where id='$sid'" | head -1)
  [ "$kept" = "194C" ] && ok=true || ok=false
  vsa_log_step "$VSA_SCENARIO" "$VSA_STEP" PATCH "/api/v1/uploads/$job/ledgers/$sid" 200 "snapshot tds_section kept for a non-deductor" "$ok"
  [ "$ok" = true ] || vsa_fail "snapshot tds_section not kept: got '$kept'"

  # (d) Debtor TAN gaps are reported for a non-deductor too, from the latest
  # completed day-book upload. The GSTR3B create doubles as the not-gated
  # control; its day-book upload also lays the confirmed GSTR1 baseline the
  # later finalized-workflow steps need (bootstrap choreography).
  local dd="$VSA_EVIDENCE_RUN"
  tdsap_debtor_daybook "$dd/tdsa-debtor-daybook.json"
  req "indiv-responsibility-start" PUT /api/v1/compliance/responsibility-start '{"type":"GSTR1","firstPeriodStart":"2026-05-01"}'
  expect_status "200|201" || vsa_fail "indiv responsibility-start failed: $RESP"
  req "start-gstr3b-ok" POST /api/v1/workflows '{"type":"GSTR3B","periodKey":"2026-05"}'
  expect_status 200 || vsa_fail "GSTR3B start should not be gated: $RESP"
  local gid; gid=$(echo "$RESP" | jq -r .id)
  local url="http://localhost:${VSA_API_PORT}/api/v1/workflows/$gid/upload" out
  out=$(curl -s -X POST -H "Authorization: Bearer $VSA_JWT" \
        -F "file=@$dd/tdsa-debtor-daybook.json" \
        -F "coverageFrom=2026-04-01" -F "coverageTo=2026-05-31" \
        -o "$dd/last-body" -w '%{http_code}' "$url")
  STATUS="$out"; RESP=$(cat "$dd/last-body"); VSA_METHOD=POST; VSA_PATH="/api/v1/workflows/$gid/upload"; VSA_STEP="indiv-daybook-upload"
  expect_status 200 || vsa_fail "indiv daybook upload failed: $RESP"
  req "indiv-baseline-confirm" POST /api/v1/compliance/baseline/confirm '{}'
  expect_status 200 || vsa_fail "indiv baseline confirm failed: $RESP"
  req "debtor-tans-missing" GET /api/v1/tds/debtor-tans/missing
  expect_status 200
  expect_contains "Scenario Sundry Debtor"

  # (b) Finalized TDS workflow blocks marking the year not applicable (409).
  tdsap_as_indiv_owner
  req "deductor-details-indiv" PATCH "/api/organizations/$iid/deductor" \
    "{\"tan\":\"$TDSAP_TAN\",\"deductorType\":\"Q\",\"responsiblePersonName\":\"Verify Person\",\"responsiblePersonDesignation\":\"Director\"}"
  expect_status 200 || vsa_fail "indiv deductor details failed: $RESP"
  req "put-indiv-true-for-409" PUT "/api/v1/organizations/$iid/tds-applicability/2026" '{"applicable":true,"priorYearTurnover":6000000,"turnoverNature":"BUSINESS"}'
  expect_status 200 || vsa_fail "indiv PUT true failed: $RESP"
  tdsap_as_indiv
  local dd="$VSA_EVIDENCE_RUN"
  # April: the first month of the FY has no not-yet-finalized predecessor.
  jq -n '{tallymessage: [{
    date: "20260410", vouchertypename: "Journal", vouchernumber: "TDSAP-PAY-1",
    guid: "SYN-TDSA-PAY-1", partyledgername: "Scenario Contractor Party",
    allledgerentries: [
      {ledgername: "Scenario Contractor Expense", amount: 1000},
      {ledgername: "Scenario Contractor Party", amount: -1000}
    ]}]}' > "$dd/tdsa-payment-daybook.json"
  VSA_WF_JWT="$VSA_JWT"
  local pid; pid=$(fresh_workflow TDS_PAYMENT "2026-04" "$dd/tdsa-payment-daybook.json")
  unset VSA_WF_JWT
  tds_nil_challan "$pid"
  req "finalize-payment" POST "/api/v1/workflows/$pid/finalize"
  expect_status 200 || vsa_fail "TDS_PAYMENT finalize failed: $RESP"
  req "put-indiv-false-409" PUT "/api/v1/organizations/$iid/tds-applicability/2026" '{"applicable":false,"priorYearTurnover":6000000,"turnoverNature":"BUSINESS"}'
  expect_status 409
  expect_contains "unfinalize"

  # (c) Draft TDS workflows of a year marked not applicable vanish from the
  # list (FY 2025, so the finalized FY 2026 workflow does not trigger the 409).
  # FY 2025 sits before the default coverage start (2026-04-01); widen the
  # books start on the isolated DB for these steps and restore it after.
  trap "vsa_psql \"update organizations set books_start_date=NULL where id='$iid'\" >/dev/null" EXIT
  vsa_psql "update organizations set books_start_date='2025-04-01' where id='$iid'" >/dev/null
  tdsap_as_indiv_owner
  req "put-2025-true" PUT "/api/v1/organizations/$iid/tds-applicability/2025" '{"applicable":true,"priorYearTurnover":6000000,"turnoverNature":"BUSINESS"}'
  expect_status 200 || vsa_fail "indiv 2025 PUT true failed: $RESP"
  tdsap_as_indiv
  req "start-2025-draft" POST /api/v1/workflows '{"type":"TDS_PAYMENT","periodKey":"2025-05"}'
  expect_status 200 || vsa_fail "2025 TDS_PAYMENT start failed: $RESP"
  local draft; draft=$(echo "$RESP" | jq -r .id)
  tdsap_as_indiv_owner
  req "put-2025-false" PUT "/api/v1/organizations/$iid/tds-applicability/2025" '{"applicable":false,"priorYearTurnover":6000000,"turnoverNature":"BUSINESS"}'
  expect_status 200 || vsa_fail "2025 PUT false failed: $RESP"
  tdsap_as_indiv
  req "list-workflows-after-hide" GET /api/v1/workflows
  expect_status 200
  VSA_STEP="draft-hidden"
  if echo "$RESP" | jq -e --arg d "$draft" 'all(.[]; .id != $d)' >/dev/null; then ok=true; else ok=false; fi
  vsa_log_step "$VSA_SCENARIO" "$VSA_STEP" GET /api/v1/workflows "$STATUS" "draft $draft hidden" "$ok"
  [ "$ok" = true ] || vsa_fail "draft TDS workflow still listed for a non-applicable FY: $RESP"
  vsa_psql "update organizations set books_start_date=NULL where id='$iid'" >/dev/null
  trap - EXIT
  state_set wf_TDS_PAYMENT_2026-04 "$saved_wf_2604"
}
