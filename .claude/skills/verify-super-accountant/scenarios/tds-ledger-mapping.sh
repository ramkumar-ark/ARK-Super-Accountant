# Scenario: TDS ledger mapping over HTTP for a fresh company (deductor K)
# organization. Covers: the mapping view's candidate rows with suggested
# role/section/area (duty ledgers via the Duties & Taxes group chain with
# sections from their names, an unseeded-section duty ledger, and a
# loans-&-advances receivable); unmapped payable candidates block TDS
# analysis (the workflow stays DATA_UPLOADED; an explicit analyze returns
# 200 without moving) behind TDS_MAPPING / TDS_LEDGER_UNMAPPED findings
# that also block advance (409); saving TDS_PAYABLE rows with their
# sections shrinks the blocker list, a NOT_TDS save removes a candidate
# from blockers, and completing the gate re-runs analysis automatically so
# acknowledged findings advance to PREPARE; an unmapped receivable only
# warns (warn-not-block while the payable side is clean) and mapping it
# clears the warning with the row marked saved. PUT is Accountant-only
# (operator 403) and rejects unknown ledgers / sections on non-payable
# roles with 400.

# tdslm_as_map: act as this org's accountant.
tdslm_as_map() { VSA_JWT=$(state_get acct_token_tdslm); }

# tdslm_journal <yyyymmdd> <voucher-number> <out>: one journal voucher -
# contractor expense debited, the party and a TDS payable duty ledger
# credited.
tdslm_journal() {
  jq -n --arg d "$1" --arg v "$2" '{tallymessage: [{
    date: $d, vouchertypename: "Journal", vouchernumber: $v,
    guid: ("SYN-TDSLM-" + $v), partyledgername: "Ledger Mapping Contractor Party",
    allledgerentries: [
      {ledgername: "Ledger Mapping Contractor Expense", amount: 1000},
      {ledgername: "Ledger Mapping Contractor Party", amount: -900},
      {ledgername: "TDS Payable - 194C A/c", amount: -100}
    ]}]}' > "$3"
}

# tdslm_ack_blocking <id>: acknowledge this org's blocking findings (bulk
# ACKNOWLEDGE, falling back to typed individual actions) until none remain.
tdslm_ack_blocking() {
  local id="$1" round=0 n ids f
  while [ $round -lt 10 ]; do
    req "tdslm-list-findings" GET "/api/v1/workflows/$id/findings"
    n=$(echo "$RESP" | jq -r '.blockingCount // 0')
    [ "$n" = "0" ] && break
    ids=$(echo "$RESP" | jq -c '[.findings[] | .id]')
    req "tdslm-ack-findings" POST "/api/v1/workflows/$id/findings/bulk-action" \
      "{\"action\":\"ACKNOWLEDGE\",\"findingIds\":$ids}"
    if [ "$STATUS" != "200" ]; then
      for f in $(echo "$RESP" | jq -r '.blocking ?? [] | .[].id // empty' 2>/dev/null) \
               $(echo "$RESP" | jq -r '.failures[]?.findingId // empty' 2>/dev/null); do
        [ -n "$f" ] || continue
        req "tdslm-resolve-finding" POST "/api/v1/workflows/$id/findings/$f/action" \
          '{"action":"ACKNOWLEDGE","value":"194C","note":"verify synthetic resolve"}'
        if [ "$STATUS" != "200" ]; then
          req "tdslm-approve-finding" POST "/api/v1/workflows/$id/findings/$f/action" \
            '{"action":"APPROVE","value":"194C","note":"verify synthetic resolve"}'
        fi
      done
    fi
    round=$((round+1))
  done
  [ $round -lt 10 ] || vsa_fail "blocking findings could not be cleared: $RESP"
}

run_scenario() {
  bootstrapped || vsa_bootstrap
  as_owner
  local org
  org=$(state_get tdslm_org_id)
  if [ -z "$org" ]; then
    req "tdslm-create-org" POST /api/organizations \
      '{"name":"TDS Ledger Mapping Org","pan":"AAVCA1234B","tan":"MAPT12345D"}'
    expect_status 201 || vsa_fail "company org create failed: $RESP"
    expect_json '.deductorType == "K"'
    expect_json '.tan == "MAPT12345D"'
    expect_json '.tdsApplicable == true'
    org=$(echo "$RESP" | jq -r .id)
    state_set tdslm_org_id "$org"
  fi
  local uname="v$(echo "$org" | tr -d '-' | cut -c1-12)a"
  vsa_org_accountant "$org" "tdslm"
  tdslm_as_map

  # Masters upload turns the fixture into this org's candidate ledger
  # snapshots; the view then lists exactly the four TDS candidates with
  # their suggestions, blocked, with the receivable only warned.
  req "tdslm-upload-masters" POST /api/v1/uploads "" \
    "$VSA_DIR/scenarios/fixtures/tds-ledger-mapping-masters.json"
  expect_status "200|201" || vsa_fail "masters upload failed: $RESP"
  local job; job=$(echo "$RESP" | jq -r '.id // .jobId // empty')
  [ -n "$job" ] || vsa_fail "masters job id not found: $RESP"

  req "tdslm-view-initial" GET /api/v1/tds/ledger-mapping
  expect_status 200 || vsa_fail "mapping view failed: $RESP"
  expect_json '.complete == false'
  VSA_STEP="tdslm-view-job"; ok=false
  [ "$(echo "$RESP" | jq -r .mastersJobId)" = "$job" ] && ok=true || ok=false
  vsa_log_step "$VSA_SCENARIO" "$VSA_STEP" GET "$VSA_PATH" "$STATUS" "view bound to the masters job" "$ok"
  [ "$ok" = true ] || vsa_fail "view mastersJobId is not the uploaded job: $RESP"
  VSA_STEP="tdslm-view-blockers"; ok=false
  [ "$(echo "$RESP" | jq -c .blockers)" = '["3 TDS ledgers not mapped: TDS Payable - 192B A/c, TDS Payable - 194C A/c, TDS Payable - 194Q A/c"]' ] && ok=true || ok=false
  vsa_log_step "$VSA_SCENARIO" "$VSA_STEP" GET "$VSA_PATH" "$STATUS" "blockers list" "$ok"
  [ "$ok" = true ] || vsa_fail "unexpected blockers: $RESP"
  VSA_STEP="tdslm-view-receivable-warning"; ok=false
  [ "$(echo "$RESP" | jq -c .receivableWarnings)" = '["1 TDS receivable ledgers not mapped - receivable checks skip them until mapped"]' ] && ok=true || ok=false
  vsa_log_step "$VSA_SCENARIO" "$VSA_STEP" GET "$VSA_PATH" "$STATUS" "receivable warning present" "$ok"
  [ "$ok" = true ] || vsa_fail "unexpected receivable warnings: $RESP"
  expect_json '[.ledgers[] | .ledgerName] == ["TDS (FY:24-25) A/c","TDS Payable - 192B A/c","TDS Payable - 194C A/c","TDS Payable - 194Q A/c"]'
  expect_json '([.ledgers[] | {(.ledgerName): [.suggestedRole, .suggestedSection, .area]}] | add) == {"TDS (FY:24-25) A/c": ["TDS_RECEIVABLE", null, "RECEIVABLE"], "TDS Payable - 192B A/c": ["TDS_PAYABLE", null, "PAYABLE"], "TDS Payable - 194C A/c": ["TDS_PAYABLE", "194C", "PAYABLE"], "TDS Payable - 194Q A/c": ["TDS_PAYABLE", "194Q", "PAYABLE"]}'
  expect_json 'all(.ledgers[]; .saved == false)'

  # PUT guards: unknown ledger and a section on a non-payable role are 400s;
  # the operator role is refused with 403 (role check runs before validation).
  req "tdslm-put-unknown" PUT /api/v1/tds/ledger-mapping \
    '{"items":[{"ledgerName":"No Such Ledger","role":"TDS_PAYABLE"}]}'
  expect_status 400 || vsa_fail "unknown ledger should be a 400: $RESP"
  expect_contains "Unknown TDS ledger"
  req "tdslm-put-section-on-receivable" PUT /api/v1/tds/ledger-mapping \
    '{"items":[{"ledgerName":"TDS (FY:24-25) A/c","role":"TDS_RECEIVABLE","section":"194C"}]}'
  expect_status 400 || vsa_fail "section on a non-payable role should be a 400: $RESP"
  expect_contains "Section must be empty"
  as_operator
  req "tdslm-put-operator" PUT /api/v1/tds/ledger-mapping \
    '{"items":[{"ledgerName":"TDS Payable - 194C A/c","role":"TDS_PAYABLE","section":"194C"}]}'
  expect_status 403 || vsa_fail "operator PUT should be a 403: $RESP"
  tdslm_as_map

  # Baseline dance (bootstrap choreography): a GSTR3B workflow whose day-book
  # upload lays the confirmed GSTR1 baseline the later advance needs.
  local dd="$VSA_EVIDENCE_RUN"
  tdslm_journal 20260510 "TDSLM-BASE-1" "$dd/tdslm-baseline-daybook.json"
  req "tdslm-responsibility-start" PUT /api/v1/compliance/responsibility-start \
    '{"type":"GSTR1","firstPeriodStart":"2026-05-01"}'
  expect_status "200|201" || vsa_fail "responsibility-start failed: $RESP"
  req "tdslm-start-gstr3b" POST /api/v1/workflows '{"type":"GSTR3B","periodKey":"2026-05"}'
  expect_status 200 || vsa_fail "GSTR3B start failed: $RESP"
  local gid; gid=$(echo "$RESP" | jq -r .id)
  local url="http://localhost:${VSA_API_PORT}/api/v1/workflows/$gid/upload" out
  out=$(curl -s -X POST -H "Authorization: Bearer $VSA_JWT" \
        -F "file=@$dd/tdslm-baseline-daybook.json" \
        -F "coverageFrom=2026-04-01" -F "coverageTo=2026-05-31" \
        -o "$dd/last-body" -w '%{http_code}' "$url")
  STATUS="$out"; RESP=$(cat "$dd/last-body"); VSA_METHOD=POST; VSA_PATH="/api/v1/workflows/$gid/upload"; VSA_STEP="tdslm-baseline-daybook-upload"
  expect_status 200 || vsa_fail "baseline daybook upload failed: $RESP"
  req "tdslm-baseline-confirm" POST /api/v1/compliance/baseline/confirm '{}'
  expect_status 200 || vsa_fail "baseline confirm failed: $RESP"

  # TDS payment workflow: created and uploaded fine, but the mapping gate
  # blocks its analysis while payables are unmapped.
  req "tdslm-start-payment" POST /api/v1/workflows '{"type":"TDS_PAYMENT","periodKey":"2026-06"}'
  expect_status 200 || vsa_fail "TDS payment start failed: $RESP"
  local id; id=$(echo "$RESP" | jq -r .id)
  tdslm_journal 20260610 "TDSLM-1" "$dd/tdslm-tds-daybook.json"
  url="http://localhost:${VSA_API_PORT}/api/v1/workflows/$id/upload"
  out=$(curl -s -X POST -H "Authorization: Bearer $VSA_JWT" \
        -F "file=@$dd/tdslm-tds-daybook.json" \
        -F "coverageFrom=2026-04-01" -F "coverageTo=2026-06-30" \
        -o "$dd/last-body" -w '%{http_code}' "$url")
  STATUS="$out"; RESP=$(cat "$dd/last-body"); VSA_METHOD=POST; VSA_PATH="/api/v1/workflows/$id/upload"; VSA_STEP="tdslm-tds-daybook-upload"
  expect_status 200 || vsa_fail "TDS daybook upload failed: $RESP"

  # Blocked: the stage stays DATA_UPLOADED even after the automatic
  # post-upload analysis attempt has had time to fail...
  local i stage=""
  for i in 1 2 3 4 5 6 7 8; do
    sleep 0.5
    req "tdslm-stage-stays-$i" GET "/api/v1/workflows/$id"
    stage=$(echo "$RESP" | jq -r '.workflow.stage // .stage // empty')
    [ "$stage" = "DATA_UPLOADED" ] || vsa_fail "stage left DATA_UPLOADED while unmapped (got '$stage')"
  done
  VSA_STEP="tdslm-stage-stays-uploaded"; ok=false
  [ "$stage" = "DATA_UPLOADED" ] && ok=true || ok=false
  vsa_log_step "$VSA_SCENARIO" "$VSA_STEP" GET "/api/v1/workflows/$id" "$STATUS" "stage stayed DATA_UPLOADED while unmapped" "$ok"
  [ "$ok" = true ] || vsa_fail "stage left DATA_UPLOADED while unmapped"
  # ...and an explicit analyze is accepted (200) but cannot move the stage.
  req "tdslm-analyze-blocked" POST "/api/v1/workflows/$id/analyze"
  expect_status 200 || vsa_fail "blocked analyze should still be a 200: $RESP"
  expect_json '.stage == "DATA_UPLOADED"'

  # The blockers are TDS_MAPPING findings, one per unmapped payable ledger;
  # the unmapped receivable is only a view warning, never a finding.
  req "tdslm-findings-blocked" GET "/api/v1/workflows/$id/findings"
  expect_status 200 || vsa_fail "findings list failed: $RESP"
  expect_json '.blockingCount == 3'
  expect_json 'all(.findings[]; .source == "TDS_MAPPING" and .findingType == "TDS_LEDGER_UNMAPPED" and .blocking)'
  VSA_STEP="tdslm-finding-message"; ok=false
  echo "$RESP" | jq -e --arg m "TDS ledger 'TDS Payable - 194C A/c' is not mapped. Map it under Settings → Compliance → TDS ledgers." \
    'any(.findings[]; .message == $m)' >/dev/null && ok=true || ok=false
  vsa_log_step "$VSA_SCENARIO" "$VSA_STEP" GET "$VSA_PATH" "$STATUS" "194C unmapped message" "$ok"
  [ "$ok" = true ] || vsa_fail "expected the 194C unmapped finding: $RESP"
  expect_json 'all(.findings[]; .ledgerName != "TDS (FY:24-25) A/c")'
  req "tdslm-advance-blocked" POST "/api/v1/workflows/$id/advance"
  expect_status 409 || vsa_fail "advance while unmapped should be a 409: $RESP"
  expect_contains "only a workflow in FINDINGS_REVIEW"

  # Save the two mapped payable duty ledgers (sections from their names):
  # the blocker list shrinks to the one remaining candidate.
  req "tdslm-put-payables" PUT /api/v1/tds/ledger-mapping \
    '{"items":[
       {"ledgerName":"TDS Payable - 194C A/c","role":"TDS_PAYABLE","section":"194C"},
       {"ledgerName":"TDS Payable - 194Q A/c","role":"TDS_PAYABLE","section":"194Q"}]}'
  expect_status 200 || vsa_fail "payables PUT failed: $RESP"
  req "tdslm-view-after-payables" GET /api/v1/tds/ledger-mapping
  expect_status 200 || vsa_fail "mapping view failed: $RESP"
  VSA_STEP="tdslm-blockers-shrink"; ok=false
  [ "$(echo "$RESP" | jq -c .blockers)" = '["1 TDS ledgers not mapped: TDS Payable - 192B A/c"]' ] && ok=true || ok=false
  vsa_log_step "$VSA_SCENARIO" "$VSA_STEP" GET "$VSA_PATH" "$STATUS" "blockers shrunk to 192B" "$ok"
  [ "$ok" = true ] || vsa_fail "blockers did not shrink to 192B: $RESP"
  expect_json '.complete == false'
  expect_json '([.ledgers[] | select(.saved) | .ledgerName] | sort) == ["TDS Payable - 194C A/c","TDS Payable - 194Q A/c"]'
  expect_json 'all(.ledgers[] | select(.saved); .savedAt != null)'
  VSA_STEP="tdslm-saved-by"; ok=false
  [ "$(echo "$RESP" | jq -r --arg u "$uname" '.ledgers[] | select(.ledgerName == "TDS Payable - 194C A/c") | .savedBy')" = "$uname" ] && ok=true || ok=false
  vsa_log_step "$VSA_SCENARIO" "$VSA_STEP" GET "$VSA_PATH" "$STATUS" "194C row savedBy the accountant" "$ok"
  [ "$ok" = true ] || vsa_fail "savedBy is not the accountant uname: $RESP"

  # NOT_TDS saves the remaining duty ledger: it stops being a candidate, the
  # gate completes and the automatic re-analysis moves the workflow on.
  req "tdslm-put-not-tds" PUT /api/v1/tds/ledger-mapping \
    '{"items":[{"ledgerName":"TDS Payable - 192B A/c","role":"NOT_TDS"}]}'
  expect_status 200 || vsa_fail "NOT_TDS PUT failed: $RESP"
  req "tdslm-view-complete" GET /api/v1/tds/ledger-mapping
  expect_status 200 || vsa_fail "mapping view failed: $RESP"
  expect_json '.complete == true'
  VSA_STEP="tdslm-blockers-cleared"; ok=false
  [ "$(echo "$RESP" | jq -c .blockers)" = '[]' ] && ok=true || ok=false
  vsa_log_step "$VSA_SCENARIO" "$VSA_STEP" GET "$VSA_PATH" "$STATUS" "blockers cleared by NOT_TDS" "$ok"
  [ "$ok" = true ] || vsa_fail "blockers not cleared: $RESP"
  expect_json '([.ledgers[] | select(.ledgerName == "TDS Payable - 192B A/c")][0] | .saved and .role == "NOT_TDS" and .section == null)'
  VSA_STEP="tdslm-receivable-still-warned"; ok=false
  [ "$(echo "$RESP" | jq -c .receivableWarnings)" = '["1 TDS receivable ledgers not mapped - receivable checks skip them until mapped"]' ] && ok=true || ok=false
  vsa_log_step "$VSA_SCENARIO" "$VSA_STEP" GET "$VSA_PATH" "$STATUS" "receivable warns while payables are clean" "$ok"
  [ "$ok" = true ] || vsa_fail "receivable warning should persist while unmapped: $RESP"

  # Gate complete: re-analysis runs automatically; poll to FINDINGS_REVIEW
  # (30 x 0.5s, explicit analyze retry as a fallback).
  local tries=0
  while [ $tries -lt 30 ]; do
    tries=$((tries+1)); sleep 0.5
    req "tdslm-remap-stage-poll-$tries" GET "/api/v1/workflows/$id"
    stage=$(echo "$RESP" | jq -r '.workflow.stage // .stage // empty')
    [ "$stage" = "FINDINGS_REVIEW" ] && break
    if [ "$stage" = "DATA_UPLOADED" ] && [ $tries -ge 5 ]; then
      req "tdslm-analyze-retry" POST "/api/v1/workflows/$id/analyze" || true
    fi
  done
  VSA_STEP="tdslm-remap-stage"
  ok=false; [ "$stage" = "FINDINGS_REVIEW" ] && ok=true || ok=false
  vsa_log_step "$VSA_SCENARIO" "$VSA_STEP" GET "$VSA_PATH" "$STATUS" "re-analysis reached FINDINGS_REVIEW" "$ok"
  [ "$ok" = true ] || vsa_fail "re-analysis did not reach FINDINGS_REVIEW (stage=$stage): $RESP"

  # The unmapped-ledger findings are gone once every payable is saved.
  req "tdslm-findings-after-remap" GET "/api/v1/workflows/$id/findings"
  expect_status 200 || vsa_fail "findings list failed: $RESP"
  expect_json '([.findings[] | select(.source == "TDS_MAPPING")] | length) == 0'
  tdslm_ack_blocking "$id"
  req "tdslm-advance-workflow" POST "/api/v1/workflows/$id/advance"
  expect_status 200 || vsa_fail "advance to PREPARE failed: $RESP"
  expect_json '.stage == "PREPARE"'

  # Finally map the receivable: its warning clears and the row is saved.
  req "tdslm-put-receivable" PUT /api/v1/tds/ledger-mapping \
    '{"items":[{"ledgerName":"TDS (FY:24-25) A/c","role":"TDS_RECEIVABLE"}]}'
  expect_status 200 || vsa_fail "receivable PUT failed: $RESP"
  req "tdslm-view-final" GET /api/v1/tds/ledger-mapping
  expect_status 200 || vsa_fail "mapping view failed: $RESP"
  expect_json '.complete == true'
  VSA_STEP="tdslm-receivable-warning-cleared"; ok=false
  [ "$(echo "$RESP" | jq -c .receivableWarnings)" = '[]' ] && ok=true || ok=false
  vsa_log_step "$VSA_SCENARIO" "$VSA_STEP" GET "$VSA_PATH" "$STATUS" "receivable warning cleared" "$ok"
  [ "$ok" = true ] || vsa_fail "receivable warnings not cleared: $RESP"
  expect_json '([.ledgers[] | select(.ledgerName == "TDS (FY:24-25) A/c")][0] | .saved and .role == "TDS_RECEIVABLE" and .area == "RECEIVABLE" and .savedAt != null)'
}
