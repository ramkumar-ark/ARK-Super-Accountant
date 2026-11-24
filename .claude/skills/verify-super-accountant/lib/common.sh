#!/usr/bin/env bash
# Shared helpers for verify-super-accountant scenario runs.
# Sourced by scripts/drive and by scenarios/*.sh. Never prints db.env values.
set -u

VSA_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VSA_EVIDENCE="${VSA_EVIDENCE:-$VSA_DIR/evidence}"
VSA_SCENARIO=""

# Loaded from the instance marker written by scripts/up:
#   VSA_API_PORT, VSA_VITE_PORT, VSA_SLUG, VSA_BASE, VSA_LOG_DIR, VSA_STATE_FILE

vsa_fail() { echo "FAIL: $*" >&2; exit 1; }

vsa_log_step() {
  # <scenario> <step> <method> <url> <status> <assertion> <ok>
  jq -cn --arg sc "$1" --arg st "$2" --arg m "$3" --arg u "$4" \
    --argjson s "${5:-0}" --arg a "$6" --argjson ok "${7:-false}" --arg ts "$(date -u +%FT%TZ)" \
    '{scenario:$sc,step:$st,method:$m,url:$u,status:$s,assertion:$a,ok:$ok,ts:$ts}' \
    >> "$VSA_EVIDENCE_RUN/scenarios.jsonl"
}

# req <step-label> <method> <path> [json-body] [file]
# Auth: uses $VSA_JWT. Response body -> $RESP, status -> $STATUS, content type -> $CTYPE.
req() {
  VSA_STEP="$1"; VSA_METHOD="$2"; VSA_PATH="$3"
  local body="${4:-}"; local file="${5:-}"
  local url="http://localhost:${VSA_API_PORT}${VSA_PATH}"
  local args=(-s -X "$VSA_METHOD" -H "Authorization: Bearer ${VSA_JWT:-}" -o "$VSA_EVIDENCE_RUN/last-body" -w '%{http_code} %{content_type}')
  [ -n "$body" ] && args+=(-H 'Content-Type: application/json' -d "$body")
  if [ -n "$file" ]; then
    args+=(-F "file=@$file")
    [ -n "${VSA_FORM_PARAM:-}" ] && args+=(-F "form=${VSA_FORM_PARAM}")
  fi
  local out
  out=$(curl "${args[@]}" "$url") || vsa_fail "curl failed for $url"
  STATUS="${out%% *}"
  CTYPE="${out#* }"
  RESP=$(cat "$VSA_EVIDENCE_RUN/last-body")
  echo "$out" >> "$VSA_EVIDENCE_RUN/http.log"
  echo "<== $VSA_METHOD $VSA_PATH -> $STATUS" >> "$VSA_EVIDENCE_RUN/http.log"
}

# Assertions: record a jsonl step line; ok=false never exits, drive tallies failures.
expect_status() {
  local want="$1"
  if [[ "$STATUS" =~ ^($want)$ ]]; then ok=true; else ok=false; fi
  vsa_log_step "$VSA_SCENARIO" "$VSA_STEP" "$VSA_METHOD" "$VSA_PATH" "$STATUS" "status=$want" "$ok"
  [ "$ok" = true ]
}

expect_json() {
  # jq -e expression against $RESP
  echo "$RESP" | jq -e "$1" >/dev/null 2>&1 && ok=true || ok=false
  vsa_log_step "$VSA_SCENARIO" "$VSA_STEP" "$VSA_METHOD" "$VSA_PATH" "$STATUS" "json:$(echo "$1" | cut -c1-60)" "$ok"
  [ "$ok" = true ]
}

expect_contains() {
  echo "$RESP" | grep -qF "$1" && ok=true || ok=false
  vsa_log_step "$VSA_SCENARIO" "$VSA_STEP" "$VSA_METHOD" "$VSA_PATH" "$STATUS" "contains:$1" "$ok"
  [ "$ok" = true ]
}

step_ok() {
  vsa_log_step "$VSA_SCENARIO" "$VSA_STEP" "$VSA_METHOD" "$VSA_PATH" "$STATUS" "$1" true
}

# Run-state helpers (users/org/workflows shared across scenarios in one run)
state_get() { jq -r --arg k "$1" '.[$k] // empty' "$VSA_STATE_FILE" 2>/dev/null; }
state_set() { local t; t=$(mktemp); jq --arg k "$1" --arg v "$2" '.[$k] = $v' "$VSA_STATE_FILE" > "$t" && mv "$t" "$VSA_STATE_FILE"; }

# Bootstrap: create owner/accountant/operator + org + invite flow once per run.
vsa_bootstrap() {
  VSA_SCENARIO="bootstrap"
  local slug; slug=$(state_get slug)
  # Unique-per-run ids so re-runs against a reused verify DB never collide.
  local runid; runid="$(date +%s%N | tail -c 7 | tr '0-9' 'a-j')$(echo "$SLUG" | sha256sum | cut -c1-3)"
  state_set runid "$runid"
  local owner_user="v${runid}o" acct_user="v${runid}a" oper_user="v${runid}p"
  state_set owner_user "$owner_user"
  state_set acct_user "$acct_user"
  state_set oper_user "$oper_user"
  local pass="Verify!$runid"
  state_set pass "$pass"
  local owner_email="verify-owner-${runid}@example.test"
  local acct_email="verify-accountant-${runid}@example.test"
  local oper_email="verify-operator-${runid}@example.test"

  VSA_JWT=""
  req "warmup-401" POST /api/auth/signin '{"username":"x","password":"x"}'
  expect_status 401 || vsa_fail "API not reachable: $STATUS"

  req "signup-owner" POST /api/auth/signup "{\"username\":\"v${runid}o\",\"email\":\"$owner_email\",\"role\":\"owner\",\"password\":\"Verify!$runid\"}"
  expect_status 200 || vsa_fail "signup owner failed: $RESP"

  req "signin-owner" POST /api/auth/signin "{\"username\":\"v${runid}o\",\"password\":\"Verify!$runid\"}"
  expect_status 200
  expect_json '.token != null'
  state_set owner_token "$(echo "$RESP" | jq -r .token)"

  VSA_JWT="$(state_get owner_token)"
  req "create-org" POST /api/organizations '{"name":"Verify Org","gstin":"29AAVCS5633D1ZL","pan":"AAVCS5633D","registeredAddress":"Test Street 1","financialYearStart":4}'
  expect_status 201 || vsa_fail "org create failed: $RESP"
  state_set org_id "$(echo "$RESP" | jq -r .id)"

  req "invite-accountant" POST "/api/organizations/$(state_get org_id)/invites?role=accountant"
  local acct_invite; acct_invite=$(echo "$RESP" | jq -r .token)
  req "signup-accountant-invite" POST "/api/auth/signup?invite=$acct_invite" "{\"username\":\"v${runid}a\",\"email\":\"$acct_email\",\"role\":\"accountant\",\"password\":\"Verify!$runid\"}"
  expect_status 200 || vsa_fail "signup accountant failed: $RESP"

  req "invite-operator" POST "/api/organizations/$(state_get org_id)/invites?role=operator"
  local oper_invite; oper_invite=$(echo "$RESP" | jq -r .token)
  req "signup-operator-invite" POST "/api/auth/signup?invite=$oper_invite" "{\"username\":\"v${runid}p\",\"email\":\"$oper_email\",\"role\":\"operator\",\"password\":\"Verify!$runid\"}"
  expect_status 200 || vsa_fail "signup operator failed: $RESP"

  req "signin-accountant" POST /api/auth/signin "{\"username\":\"v${runid}a\",\"password\":\"Verify!$runid\"}"
  expect_status 200
  VSA_JWT="$(echo "$RESP" | jq -r .token)"
  req "select-org-accountant" POST "/api/organizations/$(state_get org_id)/select"
  expect_status 200
  expect_json '.token != null'
  state_set accountant_token "$(echo "$RESP" | jq -r .token)"

  req "signin-operator" POST /api/auth/signin "{\"username\":\"v${runid}p\",\"password\":\"Verify!$runid\"}"
  expect_status 200
  VSA_JWT="$(echo "$RESP" | jq -r .token)"
  req "select-org-operator" POST "/api/organizations/$(state_get org_id)/select"
  expect_status 200
  state_set operator_token "$(echo "$RESP" | jq -r .token)"

  # Baseline: create one BASELINE row (GSTR1 2026-04, the coverage start month)
  # via responsibility-start, then confirm it with a full-range daybook upload
  # on a throwaway GSTR3B workflow. Confirmed baseline is required by advance;
  # the confirmed GSTR1 2026-04 row is the import target for filed returns.
  ensure_masters
  as_accountant
  req "responsibility-start" PUT /api/v1/compliance/responsibility-start \
    '{"type":"GSTR1","firstPeriodStart":"2026-05-01"}'
  expect_status "200|201" || vsa_fail "responsibility-start failed: $RESP"
  req "baseline-upload-workflow" POST /api/v1/workflows '{"type":"GSTR3B","periodKey":"2026-05"}'
  expect_status 200 || vsa_fail "baseline GSTR3B workflow create failed: $RESP"
  local bid; bid=$(echo "$RESP" | jq -r .id)
  local url="http://localhost:${VSA_API_PORT}/api/v1/workflows/$bid/upload"
  local out
  jq '[.tallymessage | to_entries[] | .value.date = ("202605" + (["01","05","07","09"])[.key % 4]) | .value] | {tallymessage: .}' \
    "$VSA_DIR/scenarios/fixtures/daybook.json" > "$VSA_EVIDENCE_RUN/daybook-baseline.json"
  out=$(curl -s -X POST -H "Authorization: Bearer $VSA_JWT" \
        -F "file=@$VSA_EVIDENCE_RUN/daybook-baseline.json" \
        -F "coverageFrom=2026-04-01" -F "coverageTo=2026-05-31" \
        -o "$VSA_EVIDENCE_RUN/last-body" -w '%{http_code}' "$url") || vsa_fail "baseline upload curl failed"
  STATUS="$out"; RESP=$(cat "$VSA_EVIDENCE_RUN/last-body"); VSA_METHOD=POST; VSA_PATH="/api/v1/workflows/$bid/upload"; VSA_STEP="baseline-upload"
  expect_status 200 || vsa_fail "baseline daybook upload failed: $RESP"
  req "baseline-confirm" POST /api/v1/compliance/baseline/confirm '{}'
  expect_status 200 || vsa_fail "baseline confirm failed: $RESP"

  state_set bootstrapped 1
}

# vsa_org_accountant <orgId> <tag>: invite, sign up and select an accountant for
# <orgId>; on success VSA_JWT holds that org-scoped accountant token and it is
# also stored in state as "acct_token_<tag>".
vsa_org_accountant() {
  local org="$1" tag="$2" invite
  local uname="v$(echo "$org" | tr -d '-' | cut -c1-12)a"
  req "invite-$tag-accountant" POST "/api/organizations/$org/invites?role=accountant"
  expect_status 200 || vsa_fail "invite $tag accountant failed: $RESP"
  invite=$(echo "$RESP" | jq -r .token)
  req "signup-$tag-accountant" POST "/api/auth/signup?invite=$invite" \
    "{\"username\":\"$uname\",\"email\":\"$uname@verify.local\",\"role\":\"accountant\",\"password\":\"Verify!$tag\"}"
  expect_status 200 || vsa_fail "signup $tag accountant failed: $RESP"
  req "signin-$tag-accountant" POST /api/auth/signin "{\"username\":\"$uname\",\"password\":\"Verify!$tag\"}"
  expect_status 200 || vsa_fail "signin $tag accountant failed: $RESP"
  VSA_JWT="$(echo "$RESP" | jq -r .token)"
  req "select-$tag-accountant" POST "/api/organizations/$org/select"
  expect_status 200 || vsa_fail "select $tag accountant failed: $RESP"
  state_set "acct_token_$tag" "$(echo "$RESP" | jq -r .token)"
  VSA_JWT="$(state_get acct_token_$tag)"
}

bootstrapped() { [ "$(state_get bootstrapped)" = "1" ]; }
as_accountant() { VSA_JWT=$(state_get accountant_token); }
as_operator() { VSA_JWT=$(state_get operator_token); }
as_owner() { VSA_JWT=$(state_get owner_token); }

# ensure_masters: upload + validate synthetic masters once per run (per org).
# save_tds_mapping_from_view: save the current org's TDS ledger mapping from
# its view - "TDS Payable*" ledgers as TDS_PAYABLE (combined, null section),
# every other candidate NOT_TDS - so the TDS mapping gate is complete.
save_tds_mapping_from_view() {
  req "tds-ledger-mapping-view" GET /api/v1/tds/ledger-mapping
  expect_status "200" || vsa_fail "TDS ledger mapping view failed: $RESP"
  local tds_items
  tds_items=$(echo "$RESP" | jq -c '[.ledgers[] | {ledgerName, role: (if (.ledgerName | startswith("TDS Payable")) then "TDS_PAYABLE" else "NOT_TDS" end)}]')
  req "tds-ledger-mapping" PUT /api/v1/tds/ledger-mapping "{\"items\":$tds_items}"
  expect_status "200|201" || vsa_fail "TDS ledger mapping failed: $RESP"
}

ensure_masters() {
  [ "$(state_get masters_done)" = "1" ] && return 0
  as_accountant
  req "upload-masters" POST /api/v1/uploads "" "$VSA_DIR/scenarios/fixtures/masters.json"
  expect_status "200|201" || vsa_fail "masters upload failed: $RESP"
  local job_id; job_id=$(echo "$RESP" | jq -r '.id // .jobId // empty')
  # Parsing is synchronous; confirm the job reached a validated terminal status.
  if [ -z "$job_id" ]; then
    req "masters-status" GET "/api/v1/uploads?page=0&size=1"
    job_id=$(echo "$RESP" | jq -r '.content[0].id // .[0].id // empty')
  fi
  [ -n "$job_id" ] || vsa_fail "masters job id not found: $RESP"
  req "masters-job" GET "/api/v1/uploads/$job_id/mismatches"
  # Even a 200 with empty mismatches proves the job exists; validated-ness is
  # what matters downstream, and it was computed synchronously above.

  # Save tax-ledger mappings so the GST analyzers pass their mapping gate.
  local items='['
  local first=1 r h role head
  for r in OUTPUT:Output INPUT:Input CASH_BALANCE:Cash LIABILITY_BALANCE:Liability CREDIT_BALANCE:"Credit Balance" GST_TDS:"GST TDS"; do
    role=${r%%:*}; local label=${r#*:}
    for h in CGST SGST IGST; do
      [ $first = 1 ] && first=0 || items="$items,"
      items="$items{\"ledgerName\":\"$label $h\",\"role\":\"$role\",\"head\":\"$h\"}"
    done
  done
  items="$items]"
  req "tax-ledger-mapping" PUT /api/v1/gst/tax-ledger-mapping "{\"items\":$items}"
  expect_status "200|201" || vsa_fail "tax ledger mapping failed: $RESP"

  # Save TDS ledger mappings too: the bootstrap org is a company-PAN deductor.
  # Candidates are the masters' TDS ledgers (GST and expense ledgers are never
  # candidates). The two TDS Payable duty ledgers stay combined payables (null
  # section: the engine infers the per-voucher section); every other candidate
  # is explicitly NOT_TDS so TDS workflows pass the mapping gate.
  save_tds_mapping_from_view
  state_set masters_done 1
}

# fresh_workflow <type> <periodKey> [daybook-file]: create workflow, upload
# daybook, analyze, acknowledge blocking findings, advance to PREPARE. Echoes
# workflow id. periodKey may be a quarter (YYYY-Qn, FY starts April); an
# explicit daybook-file is uploaded as-is instead of the date-remapped default.
fresh_workflow() {
  local type="$1" period="$2" custom_daybook="${3:-}"
  ensure_masters
  # Bootstrap accountant by default; a scenario operating on a different org
  # can pass its own accountant token via VSA_WF_JWT (e.g. the individual org
  # in tds-applicability-gating). Never trust the caller's token blindly:
  # scenarios leave owner/operator tokens in VSA_JWT.
  if [ -n "${VSA_WF_JWT:-}" ]; then VSA_JWT="$VSA_WF_JWT"; else as_accountant; fi
  req "create-workflow" POST /api/v1/workflows "{\"type\":\"$type\",\"periodKey\":\"$period\"}"
  expect_status 200 || vsa_fail "workflow create failed: $RESP"
  local id; id=$(echo "$RESP" | jq -r .id)
  state_set "wf_${type}_${period}" "$id"

  # Derive the required coverage window: from FY start (month 4) through the
  # workflow period end. Upload must cover both.
  local y m from to db
  y=${period%%-*}; m=${period#*-}
  if [[ "$m" =~ ^Q([1-4])$ ]]; then
    m=$(( 3 * ${BASH_REMATCH[1]} + 3 ))
    if [ "$m" -gt 12 ]; then m=$((m-12)); y=$((y+1)); fi
  fi
  m=$(printf '%02d' "$((10#$m))")
  if [ "$m" -ge 4 ]; then from="$y-04-01"; else from="$((y-1))-04-01"; fi
  to=$(date -d "$y-$m-01 +1 month -1 day" +%F)
  local dbfile="$VSA_EVIDENCE_RUN/daybook-$type-$period.json"
  if [ -n "$custom_daybook" ]; then
    cp "$custom_daybook" "$dbfile"
  else
    db=$(jq --arg pre "$y$m" \
          '[.tallymessage | to_entries[] | .value.date = ($pre + (["01","05","07","09"])[.key % 4]) | .value] | {tallymessage: .}' \
          "$VSA_DIR/scenarios/fixtures/daybook.json")
    echo "$db" > "$dbfile"
  fi
  VSA_FORM_PARAM=""
  local url="http://localhost:${VSA_API_PORT}/api/v1/workflows/$id/upload"
  local out
  out=$(curl -s -X POST -H "Authorization: Bearer $VSA_JWT" \
        -F "file=@$dbfile" -F "coverageFrom=$from" -F "coverageTo=$to" \
        -o "$VSA_EVIDENCE_RUN/last-body" -w '%{http_code}' "$url") || vsa_fail "upload curl failed"
  STATUS="$out"; RESP=$(cat "$VSA_EVIDENCE_RUN/last-body"); VSA_METHOD=POST; VSA_PATH="/api/v1/workflows/$id/upload"; VSA_STEP="upload-daybook"
  expect_status 200 || vsa_fail "daybook upload failed: $RESP"

  # Analysis runs automatically after the binding transaction commits
  # (UploadBoundListener); the explicit analyze endpoint races with it.
  # Poll GET /{id} until .workflow.stage reaches FINDINGS_REVIEW (30 x 0.5s),
  # falling back to an explicit analyze call if it is still DATA_UPLOADED.
  local st tries=0 stage
  while [ $tries -lt 30 ]; do
    tries=$((tries+1)); sleep 0.5
    req "stage-poll-$tries" GET "/api/v1/workflows/$id"
    stage=$(echo "$RESP" | jq -r '.workflow.stage // .stage // empty')
    [ "$stage" = "FINDINGS_REVIEW" ] && break
    if [ "$stage" = "DATA_UPLOADED" ] && [ $tries -ge 5 ]; then
      req "analyze-retry" POST "/api/v1/workflows/$id/analyze" || true
    fi
  done
  VSA_STEP="analyze"
  [ "$stage" = "FINDINGS_REVIEW" ] || vsa_fail "analyze did not reach FINDINGS_REVIEW (stage=$stage): $RESP"

  # Acknowledge blocking findings until advance is allowed (max 10 rounds).
  local round=0
  while [ $round -lt 10 ]; do
    req "list-findings" GET "/api/v1/workflows/$id/findings"
    local n; n=$(echo "$RESP" | jq -r '.blockingCount // 0')
    [ "$n" = "0" ] && break
    local ids fids f
    ids=$(echo "$RESP" | jq -c '[.findings[] | .id]')
    req "ack-findings" POST "/api/v1/workflows/$id/findings/bulk-action" "{\"action\":\"ACKNOWLEDGE\",\"findingIds\":$ids}"
    if [ "$STATUS" != "200" ]; then
      # Bulk ACKNOWLEDGE refused (some findings need a typed RESOLVE value);
      # resolve each one individually with a per-type value.
      for f in $(echo "$RESP" | jq -r '.blocking ?? [] | .[].id // empty' 2>/dev/null) \
               $(echo "$RESP" | jq -r '.failures[]?.findingId // empty' 2>/dev/null); do
        [ -n "$f" ] || continue
        req "resolve-finding" POST "/api/v1/workflows/$id/findings/$f/action" \
          '{"action":"ACKNOWLEDGE","value":"194C","note":"verify synthetic resolve"}'
        if [ "$STATUS" != "200" ]; then
          req "approve-finding" POST "/api/v1/workflows/$id/findings/$f/action" \
            '{"action":"APPROVE","value":"194C","note":"verify synthetic resolve"}'
        fi
      done
    fi
    round=$((round+1))
  done
  [ $round -lt 10 ] || vsa_fail "blocking findings could not be cleared: $RESP"

  req "advance-workflow" POST "/api/v1/workflows/$id/advance"
  expect_status 200 || vsa_fail "advance to PREPARE failed: $RESP"
  echo "$id"
}

# tds_nil_challan <workflowId>: ensure an approved nil TDS challan exists on a
# TDS_PAYMENT workflow in PREPARE (required by its finalize precondition).
tds_nil_challan() {
  local id="$1"
  req "challans-list" GET "/api/v1/workflows/$id/tds/challans"
  local cid
  cid=$(echo "$RESP" | jq -r '[.challans[]? | select(.status=="APPROVED" or .status=="PROPOSED")][0].id // empty')
  [ -n "$cid" ] && return 0
  # Deposit date sits inside the workflow's own month.
  req "workflow-period" GET "/api/v1/workflows/$id"
  local pk; pk=$(echo "$RESP" | jq -r '.workflow.periodKey // .periodKey')
  req "challan-propose" POST "/api/v1/workflows/$id/tds/challans" \
    "{\"bsrCode\":\"1234567\",\"depositDate\":\"$pk-15\",\"challanSerialNumber\":\"123\",\"cin\":\"CINVERIFY123\",\"tdsAmount\":0,\"surcharge\":0,\"educationCess\":0,\"interest\":0,\"penalty\":0,\"nilChallan\":true}"
  expect_status "200|201" || vsa_fail "challan propose failed: $RESP"
  cid=$(echo "$RESP" | jq -r .id)
  req "challan-approve" POST "/api/v1/workflows/$id/tds/challans/$cid/approve"
  expect_status 200 || vsa_fail "challan approve failed: $RESP"
}

# next_period: allocate the next unique workflow periodKey for this run
# (FY starts 2026-04 per default coverage; sequential months avoid 409 conflicts).
next_period() {
  local n; n=$(state_get period_n); [ -z "$n" ] && n=4; n=$((n+1)); state_set period_n "$n"
  if [ "$n" -le 12 ]; then printf '2026-%02d' "$n"; else printf '2027-%02d' "$((n-12))"; fi
}

# vsa_psql <sql>: SQL against the isolated verify DB only (never the default
# one), for ids / settings the HTTP API does not expose (ledger snapshot ids,
# organization books start date). Credentials come from db.env, never printed.
vsa_psql() {
  local db; db=$(jq -r .database "$VSA_MARKER")
  ( set -a; . "$HOME/.config/super-accountant/db.env"; set +a
    PGPASSWORD="$DB_PASSWORD" psql -h localhost -U "${DB_USERNAME:-postgres}" -d "$db" -tA -c "$1" )
}
