# Scenario: TDS discovery prompts and PAN-only advisories for an individual/HUF
# organization that declared TDS not applicable at registration, then crossed
# the turnover line in its books.
# Covers: a BOOKS_EXCEED_LIMIT prompt raised on baseline confirm when the
# measured preceding-FY books exceed 50 lakh, with the "since 1 April" message;
# dismiss without a reason rejected; confirming the prompt makes the year
# applicable from books; re-saving the unchanged answer leaves the confirmed
# baseline untouched; 194M / 194-IB advisories with Form 26QD/26QC before
# 1 April 2026 and Form 141 on/after; advisories empty for an applicable year
# and for a company.

# tdsap_prompt_daybook <out>: books for FY 2025 (sales over the 50 lakh line)
# plus 194C contractor and 194I_B rent debits in FY 2025 and FY 2026.
tdsap_prompt_daybook() {
  local arr='[]'
  local v vdate vparty vexp vamp vn=0
  for v in \
    "20250510:Contractor:Expense:5500000" \
    "20250511:Contractor:Rent:70000" \
    "20250610::Sales:2000000" \
    "20250710::Sales:2000000" \
    "20250810::Sales:2000000" \
    "20260510:Contractor:Expense:3000000" \
    "20260511:Contractor:Expense:2500000" \
    "20260512:Contractor:Rent:60000"; do
    IFS=: read -r vdate vparty vexp vamp <<< "$v"
    vn=$((vn+1))
    local exp="Scenario Contractor Expense" party="Scenario Contractor Party" rent=""
    [ "$vexp" = "Rent" ] && exp="Scenario Rent Expense"
    if [ "$vexp" = "Sales" ]; then
      arr=$(echo "$arr" | jq --arg d "$vdate" --arg n "$vn" --argjson a "$vamp" \
        '. + [{date:$d,vouchertypename:"Journal",vouchernumber:("TDSA-"+$n),guid:("SYN-TDSA-V-"+$n),partyledgername:"Scenario Sundry Debtor",
          allledgerentries:[{ledgername:"Scenario Sundry Debtor",amount:$a},{ledgername:"Scenario Sales Ledger",amount:(-($a))}]}]')
    else
      arr=$(echo "$arr" | jq --arg d "$vdate" --arg n "$vn" --argjson a "$vamp" --arg e "$exp" --arg p "$party" \
        '. + [{date:$d,vouchertypename:"Journal",vouchernumber:("TDSA-"+$n),guid:("SYN-TDSA-V-"+$n),partyledgername:$p,
          allledgerentries:[{ledgername:$e,amount:$a},{ledgername:$p,amount:(-($a))}]}]')
    fi
  done
  echo "{\"tallymessage\":$arr}" > "$1"
}

run_scenario() {
  bootstrapped || vsa_bootstrap
  as_owner

  # Individual org, TDS declared not applicable at registration.
  req "create-prompt-org" POST /api/organizations '{"name":"TDS Prompt Org","pan":"AAAPA1234E","tdsTurnoverExceeded":false}'
  expect_status 201 || vsa_fail "prompt org create failed: $RESP"
  local oid; oid=$(echo "$RESP" | jq -r .id)
  state_set tdsa_prompt_id "$oid"
  vsa_org_accountant "$oid" "prompt"

  # Books start must reach back into FY 2025 for the preceding-year measurement.
  vsa_psql "update organizations set books_start_date='2025-04-01' where id='$oid'" > /dev/null
  trap "vsa_psql \"update organizations set books_start_date=NULL where id='$oid'\" >/dev/null" EXIT

  req "upload-prompt-masters" POST /api/v1/uploads "" "$VSA_DIR/scenarios/fixtures/tds-applicability-masters.json"
  expect_status "200|201" || vsa_fail "prompt masters upload failed: $RESP"
  local job; job=$(echo "$RESP" | jq -r .id)
  local sid
  for pair in "Scenario Contractor Expense=194C" "Scenario Rent Expense=194I_B"; do
    local name=${pair%%=*} sec=${pair#*=}
    sid=$(vsa_psql "select id from upload_ledger_snapshots where upload_job_id='$job' and ledger_name='$name'" | head -1)
    [ -n "$sid" ] || vsa_fail "no snapshot for $name"
    req "set-section-$sec" PATCH "/api/v1/uploads/$job/ledgers/$sid" "{\"tdsApplicable\":true,\"tdsSection\":\"$sec\"}"
    expect_status 200 || vsa_fail "section $sec failed: $RESP"
  done

  # Baseline flow: responsibility start, daybook upload, confirm -> the books
  # (FY 2025 sales 60 lakh) cross 50 lakh, so the prompt must appear.
  req "prompt-responsibility-start" PUT /api/v1/compliance/responsibility-start '{"type":"GSTR1","firstPeriodStart":"2026-05-01"}'
  expect_status "200|201" || vsa_fail "responsibility-start failed: $RESP"
  local dd="$VSA_EVIDENCE_RUN"
  tdsap_prompt_daybook "$dd/tdsa-prompt-daybook.json"
  req "prompt-gstr3b-create" POST /api/v1/workflows '{"type":"GSTR3B","periodKey":"2026-05"}'
  expect_status 200 || vsa_fail "prompt GSTR3B create failed: $RESP"
  local bid; bid=$(echo "$RESP" | jq -r .id)
  local url="http://localhost:${VSA_API_PORT}/api/v1/workflows/$bid/upload" out
  out=$(curl -s -X POST -H "Authorization: Bearer $VSA_JWT" \
        -F "file=@$dd/tdsa-prompt-daybook.json" \
        -F "coverageFrom=2025-04-01" -F "coverageTo=2026-05-31" \
        -o "$dd/last-body" -w '%{http_code}' "$url")
  STATUS="$out"; RESP=$(cat "$dd/last-body"); VSA_METHOD=POST; VSA_PATH="/api/v1/workflows/$bid/upload"; VSA_STEP="prompt-daybook-upload"
  expect_status 200 || vsa_fail "prompt daybook upload failed: $RESP"
  req "prompt-baseline-confirm" POST /api/v1/compliance/baseline/confirm '{}'
  expect_status 200 || vsa_fail "baseline confirm failed: $RESP"

  # (e) The books-exceed prompt: kind, year, and the "since 1 April 2026" message.
  req "list-prompts" GET "/api/v1/organizations/$oid/tds-applicability/prompts"
  expect_status 200
  expect_json 'length >= 1'
  expect_json '[.[] | select(.kind == "BOOKS_EXCEED_LIMIT" and .financialYear == 2026)] | length == 1'
  req "list-prompts-again" GET "/api/v1/organizations/$oid/tds-applicability/prompts"
  local pid; pid=$(echo "$RESP" | jq -r '[.[] | select(.kind == "BOOKS_EXCEED_LIMIT")][0].id')
  expect_contains "1 April 2026"

  # (f) PAN-only advisories while the year is not applicable: FY 2025 payments
  # predate 1 April 2026 -> Form 26QD / 26QC.
  req "advisories-2025" GET "/api/v1/organizations/$oid/tds-applicability/2025/pan-only-advisories"
  expect_status 200
  expect_contains '"194M"'
  expect_contains '"194-IB"'
  expect_contains 'Form 26QD'
  expect_contains 'Form 26QC'

  # (f) FY 2026 debits fall on/after 1 April 2026 -> Form 141.
  req "advisories-2026" GET "/api/v1/organizations/$oid/tds-applicability/2026/pan-only-advisories"
  expect_status 200
  expect_contains '"194M"'
  expect_contains '"194-IB"'
  expect_contains 'Form 141'
  VSA_STEP="no-26qd-in-2026"
  if echo "$RESP" | grep -q 'Form 26QD'; then ok=false; else ok=true; fi
  vsa_log_step "$VSA_SCENARIO" "$VSA_STEP" GET "/api/v1/organizations/$oid/tds-applicability/2026/pan-only-advisories" "$STATUS" "FY2026 uses Form 141 only" "$ok"
  [ "$ok" = true ] || vsa_fail "FY2026 advisory should use Form 141, not Form 26QD"

  # (e) Dismiss without a reason: 400.
  req "dismiss-blank" POST "/api/v1/organizations/$oid/tds-applicability/prompts/$pid/dismiss" '{"reason":" "}'
  expect_status 400

  # (e) Confirming the prompt: FY 2026 becomes applicable, sourced from books.
  req "confirm-prompt" POST "/api/v1/organizations/$oid/tds-applicability/prompts/$pid/confirm" '{"applicable":true,"priorYearTurnover":6000000,"turnoverNature":"BUSINESS"}'
  expect_status 200 || vsa_fail "prompt confirm failed: $RESP"
  req "get-after-confirm" GET "/api/v1/organizations/$oid/tds-applicability"
  expect_status 200
  expect_json '[.years[] | select(.financialYear == 2026)][0].applicable == true'
  expect_contains 'CONFIRMED_FROM_BOOKS'

  # (e) Re-saving the unchanged answer must not touch the confirmed baseline:
  # the baseline view is byte-identical (volatile timestamps aside) after a
  # second PUT with the same answer.
  req "baseline-before-resave" GET /api/v1/compliance/baseline
  expect_status 200
  local before after
  before=$(echo "$RESP" | jq -cS 'map(. | walk(if type == "object" then del(.updatedAt,.createdAt,.confirmedAt) else . end))')
  req "resave-unchanged" PUT "/api/v1/organizations/$oid/tds-applicability/2026" '{"applicable":true,"priorYearTurnover":6000000,"turnoverNature":"BUSINESS","note":"re-save with no change"}'
  expect_status 200 || vsa_fail "re-save failed: $RESP"
  req "baseline-after-resave" GET /api/v1/compliance/baseline
  expect_status 200
  after=$(echo "$RESP" | jq -cS 'map(. | walk(if type == "object" then del(.updatedAt,.createdAt,.confirmedAt) else . end))')
  VSA_STEP="baseline-untouched"
  if [ "$before" = "$after" ] && [ -n "$before" ] && [ "$before" != "null" ]; then ok=true; else ok=false; fi
  vsa_log_step "$VSA_SCENARIO" "$VSA_STEP" GET /api/v1/compliance/baseline "$STATUS" "baseline unchanged by re-save" "$ok"
  [ "$ok" = true ] || vsa_fail "re-saving an unchanged answer disturbed the confirmed baseline"

  # (f) Advisories disappear for an applicable year and never exist for a company.
  req "advisories-2026-after-confirm" GET "/api/v1/organizations/$oid/tds-applicability/2026/pan-only-advisories"
  expect_status 200
  expect_json 'length == 0'
  local cid; cid=$(state_get tdsa_company_id)
  [ -n "$cid" ] || vsa_fail "tds-applicability-registration must run first (no company org)"
  as_owner
  req "advisories-company" GET "/api/v1/organizations/$cid/tds-applicability/2026/pan-only-advisories"
  expect_status 200
  expect_json 'length == 0'

  vsa_psql "update organizations set books_start_date=NULL where id='$oid'" > /dev/null
  trap - EXIT
}
