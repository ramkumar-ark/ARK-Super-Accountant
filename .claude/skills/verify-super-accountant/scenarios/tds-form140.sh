# Scenario: TDS quarter export under the Income-tax Act 2025 (Form 140) vs the
# old Act (26Q v7.8). Drives the real choreography over HTTP: analyzer-created
# deductee entries -> challans -> map -> gate -> finalize -> GET /tds/export.
# Quarters used (one periodKey each, never reused): 2025-Q1 old Act, 2026-Q1 new
# Act (194C individual/non-individual + 194J_B), 2026-Q2 new Act with 194M,
# 2026-Q3 new Act with a payee that has no PAN (gate must block).

TDS_TAN="CHEA12345B"

# tds_daybook <fy> <month01-12> <outfile> <spec...>: journal vouchers dated the
# 10th of the month, spec = expenseLedger:partyLedger:expense:tds
tds_daybook() {
  local fy="$1" mm="$2" out="$3"; shift 3
  local y="$fy"; [ "$mm" -lt 4 ] && y=$((fy+1))
  local arr='[]' n=0 spec exp party amt tds
  for spec in "$@"; do
    IFS=: read -r exp party amt tds <<< "$spec"
    n=$((n+1))
    arr=$(echo "$arr" | jq --arg d "$y$(printf '%02d' "$mm")10" --arg n "$n" --arg e "$exp" --arg p "$party" \
      --argjson a "$amt" --argjson t "$tds" \
      '. + [{date:$d,vouchertypename:"Journal",vouchernumber:("TDS-"+$n),guid:("SYN-TDS-"+$d+"-"+$n),partyledgername:$p,
        allledgerentries:[{ledgername:$e,amount:$a},{ledgername:$p,amount:(-($a-$t))},{ledgername:"TDS Payable 194C",amount:(-$t)}]}]')
  done
  echo "{\"tallymessage\":$arr}" > "$out"
}

# tds_setup_org_and_sections: deductor identity + TDS sections on expense ledgers (once per run).
tds_setup() {
  [ "$(state_get tds_setup_done)" = "1" ] && return 0
  ensure_masters
  as_owner
  req "deductor-details" PATCH "/api/organizations/$(state_get org_id)/deductor" \
    "{\"tan\":\"$TDS_TAN\",\"deductorType\":\"Q\",\"responsiblePersonName\":\"Verify Person\",\"responsiblePersonDesignation\":\"Director\"}"
  expect_status 200 || vsa_fail "deductor details failed: $RESP"
  as_accountant
  req "masters-latest" GET "/api/v1/uploads?page=0&size=1"
  local job; job=$(echo "$RESP" | jq -r '.content[0].id')
  local pair name sec sid
  for pair in "TDS Contract Expense=194C" "TDS Contract Expense 194M=194M" "TDS Professional Expense=194J_B"; do
    name=${pair%%=*}; sec=${pair#*=}
    sid=$(vsa_psql "select id from upload_ledger_snapshots where upload_job_id='$job' and ledger_name='$name'" | head -1)
    [ -n "$sid" ] || vsa_fail "no snapshot for $name"
    req "set-section-$sec" PATCH "/api/v1/uploads/$job/ledgers/$sid" \
      "{\"tdsApplicable\":true,\"tdsSection\":\"$sec\"}"
    expect_status 200 || vsa_fail "section $sec failed: $RESP"
  done
  state_set tds_setup_done 1
}

# tds_quarter <fy> <qn> <daybook-file>: analyze the quarter workflow to PREPARE; echoes its id.
tds_quarter() { fresh_workflow TDS_26Q "$1-Q$2" "$3"; }

# tds_pay_months <fy> <qn> <entries-total>: finalize the three payment months of the
# quarter; the first month carries the real challan (entries-total), the rest nil.
# Echoes the approved challan id.
tds_pay_months() {
  local fy="$1" qn="$2" total="$3" first=1 i m y pid cid=""
  for i in 0 1 2; do
    m=$(( 4 + 3*(qn-1) + i )); y=$fy
    [ "$m" -gt 12 ] && { m=$((m-12)); y=$((fy+1)); }
    pid=$(fresh_workflow TDS_PAYMENT "$y-$(printf '%02d' "$m")")
    as_accountant
    if [ $first = 1 ]; then
      first=0
      req "challan-propose" POST "/api/v1/workflows/$pid/tds/challans" \
        "{\"bsrCode\":\"1234567\",\"depositDate\":\"$y-$(printf '%02d' "$m")-25\",\"challanSerialNumber\":\"555\",\"cin\":\"CINTDS140\",\"tdsAmount\":$total,\"surcharge\":0,\"educationCess\":0,\"interest\":0,\"penalty\":0,\"nilChallan\":false}"
      expect_status "200|201" || vsa_fail "challan propose failed: $RESP"
      cid=$(echo "$RESP" | jq -r .id)
      req "challan-approve" POST "/api/v1/workflows/$pid/tds/challans/$cid/approve"
      expect_status 200 || vsa_fail "challan approve failed: $RESP"
    else
      tds_nil_challan "$pid"
    fi
    req "payment-finalize" POST "/api/v1/workflows/$pid/finalize"
    expect_status 200 || vsa_fail "payment finalize failed: $RESP"
  done
  echo "$cid"
}

# tds_prepare_and_export <qid> <challan-id>: map all entries, require the gate, finalize and export.
tds_map_gate_finalize() {
  local qid="$1" cid="$2"
  as_accountant
  req "entries" GET "/api/v1/workflows/$qid/tds/entries"
  expect_status 200
  local ids; ids=$(echo "$RESP" | jq -c '[.[].id]')
  req "map-entries" POST "/api/v1/workflows/$qid/tds/entries/map" "{\"challanId\":\"$cid\",\"entryIds\":$ids}"
  expect_status 200
  req "gate" GET "/api/v1/workflows/$qid/tds/gate"
  expect_status 200
  expect_json 'all(.[]; .passed)'
  req "finalize-quarter" POST "/api/v1/workflows/$qid/finalize"
  expect_status 200 || vsa_fail "quarter finalize failed: $RESP"
}

# tds_export <qid>: GET export keeping headers; body in $RESP, headers in $VSA_EVIDENCE_RUN/export.hdr
tds_export() {
  local qid="$1"
  VSA_STEP="export"; VSA_METHOD=GET; VSA_PATH="/api/v1/workflows/$qid/tds/export"
  STATUS=$(curl -s -D "$VSA_EVIDENCE_RUN/export.hdr" -o "$VSA_EVIDENCE_RUN/last-body" -w '%{http_code}' \
    -H "Authorization: Bearer $VSA_JWT" "http://localhost:${VSA_API_PORT}$VSA_PATH")
  RESP=$(cat "$VSA_EVIDENCE_RUN/last-body")
  cp "$VSA_EVIDENCE_RUN/last-body" "$VSA_EVIDENCE_RUN/export-$qid.txt"
  echo "<== GET $VSA_PATH -> $STATUS" >> "$VSA_EVIDENCE_RUN/http.log"
}

expect_header() {
  grep -iF "$1" "$VSA_EVIDENCE_RUN/export.hdr" >/dev/null && ok=true || ok=false
  vsa_log_step "$VSA_SCENARIO" "$VSA_STEP" GET "$VSA_PATH" "$STATUS" "header:$1" "$ok"
  [ "$ok" = true ]
}

run_scenario() {
  bootstrapped || vsa_bootstrap
  tds_setup
  local dd="$VSA_EVIDENCE_RUN" qid cid

  # (1)+(4a) New Act quarter FY2026 Q1: individual -> 1023, non-individual -> 1024, 194J_B -> 1027.
  tds_daybook 2026 4 "$dd/tds-2026-q1.json" \
    "TDS Contract Expense:TDS Payee Individual:500000:5000" \
    "TDS Contract Expense:TDS Payee Company:500000:10000" \
    "TDS Professional Expense:TDS Payee Company:200000:20000"
  # Payment months first: each payment upload becomes the latest upload for its
  # period, so the quarter's own upload must be the last one bound.
  cid=$(tds_pay_months 2026 1 35000)
  qid=$(tds_quarter 2026 1 "$dd/tds-2026-q1.json")
  as_accountant
  req "entries-q1" GET "/api/v1/workflows/$qid/tds/entries"
  expect_json 'length == 3'
  tds_map_gate_finalize "$qid" "$cid"
  tds_export "$qid"
  expect_status 200
  expect_contains '^BH^1^3^140^'   # 3 challans: one real, two nil
  expect_contains '^202627^'
  expect_contains '^1023^'
  expect_contains '^1024^'
  expect_contains '^1027^'
  expect_header "140_${TDS_TAN}_FY2026_Q1.txt"
  VSA_STEP="no-old-act-codes"
  if echo "$RESP" | grep -qE '\^(94C|4JB)\^'; then ok=false; else ok=true; fi
  vsa_log_step "$VSA_SCENARIO" "$VSA_STEP" GET "$VSA_PATH" "$STATUS" "body has no 26Q section codes" "$ok"
  as_operator
  req "export-operator-forbidden" GET "/api/v1/workflows/$qid/tds/export"
  expect_status 403

  # (3) 194M in a new Act quarter (FY2026 Q2): no Form 140 code -> 400 mentioning Form 141.
  tds_daybook 2026 7 "$dd/tds-2026-q2.json" \
    "TDS Contract Expense 194M:TDS Payee Resident Individual 194M:6000000:120000"
  cid=$(tds_pay_months 2026 2 120000)
  qid=$(tds_quarter 2026 2 "$dd/tds-2026-q2.json")
  as_accountant
  req "entries-q2" GET "/api/v1/workflows/$qid/tds/entries"
  expect_json 'length == 1 and .[0].section == "194M"'
  tds_map_gate_finalize "$qid" "$cid"
  tds_export "$qid"
  expect_status 400
  expect_contains 'Form 141'

  # (4b) 194C payee without a PAN (FY2026 Q3): the entry is taken at the higher rate so the
  # PANS_VALID gate lets it through, and the export must then fail visibly (400) because the
  # individual/HUF vs other payee type cannot be derived - never a silent 1023/1024 default.
  tds_daybook 2026 10 "$dd/tds-2026-q3.json" \
    "TDS Contract Expense:TDS Payee No PAN:500000:5000"
  cid=$(tds_pay_months 2026 3 5000)
  qid=$(tds_quarter 2026 3 "$dd/tds-2026-q3.json")
  as_accountant
  req "entries-q3" GET "/api/v1/workflows/$qid/tds/entries"
  expect_json 'length == 1 and .[0].panValid == false and .[0].deducteePan == null'
  tds_map_gate_finalize "$qid" "$cid"
  tds_export "$qid"
  expect_status 400
  expect_contains 'Cannot determine 194C payee type'

  # (2) Old Act quarter FY2025 Q1: still Form 26Q v7.8 with 194-series codes (94C / 4JB).
  # Books start is not settable over HTTP and defaults to today's FY (coverage start),
  # so the pre-2026 quarter needs it moved back on the isolated DB; restored on exit.
  local org; org=$(state_get org_id)
  trap "vsa_psql \"update organizations set books_start_date=NULL where id='$org'\" >/dev/null" EXIT
  vsa_psql "update organizations set books_start_date='2025-04-01' where id='$org'" >/dev/null
  tds_daybook 2025 4 "$dd/tds-2025-q1.json" \
    "TDS Contract Expense:TDS Payee Individual:500000:5000" \
    "TDS Professional Expense:TDS Payee Company:200000:20000"
  cid=$(tds_pay_months 2025 1 25000)
  qid=$(tds_quarter 2025 1 "$dd/tds-2025-q1.json")
  tds_map_gate_finalize "$qid" "$cid"
  tds_export "$qid"
  expect_status 200
  expect_contains '^26Q^'
  expect_contains '^94C^'
  expect_contains '^4JB^'
  expect_header "26Q_${TDS_TAN}_FY2025_Q1.txt"
  VSA_STEP="no-form140"
  if echo "$RESP" | grep -qE '\^140\^|\^(1023|1024|1027)\^'; then ok=false; else ok=true; fi
  vsa_log_step "$VSA_SCENARIO" "$VSA_STEP" GET "$VSA_PATH" "$STATUS" "old-Act body has no Form 140 markers" "$ok"
  vsa_psql "update organizations set books_start_date=NULL where id='$org'" >/dev/null
  trap - EXIT
}
