# Scenario: GST B2CL threshold dated rows (PR5)
# A Rs 1,50,000 inter-state B2C invoice is B2CS in July 2024 (threshold 2.5 lakh)
# and B2CL from 1 Aug 2024 (threshold 1 lakh). GSTR-1 prepare and filed-return
# recon must read the same dated statutory rows and agree.
run_scenario() {
  bootstrapped || vsa_bootstrap
  local fixtures="$VSA_DIR/scenarios/fixtures"

  # ---------- prepare side: books from Apr 2024, GSTR-1 first period Apr 2024 ----------
  as_owner
  local prepare_org; prepare_org=$(state_get org_id)
  vsa_psql "update organizations set books_start_date='2024-04-01' where id='$prepare_org'"
  as_accountant
  req "prepare-responsibility-start" PUT /api/v1/compliance/responsibility-start \
    '{"type":"GSTR1","firstPeriodStart":"2024-04-01"}'
  expect_status "200|201" || vsa_fail "prepare responsibility-start failed: $RESP"

  local jul; jul=$(fresh_workflow GSTR1 2024-07 "$fixtures/daybook-b2cl-threshold-jul.json")
  local aug; aug=$(fresh_workflow GSTR1 2024-08 "$fixtures/daybook-b2cl-threshold-aug.json")

  as_accountant
  req "prepare-tables-jul" GET "/api/v1/workflows/$jul/gstr1/tables"
  expect_status 200 || vsa_fail "July tables failed: $RESP"
  expect_json '[.tables[].tableCode] | index("B2CS") != null' \
    || vsa_fail "July 1.5L inter-state invoice should be B2CS at the 2.5L threshold: $RESP"
  expect_json '[.tables[].tableCode] | index("B2CL") == null' \
    || vsa_fail "July 1.5L inter-state invoice must not be B2CL: $RESP"
  expect_json '([.tables[] | select(.tableCode == "B2CS")][0].totals.taxableValue // 0) == 150000' \
    || vsa_fail "July B2CS taxable should be 150000: $RESP"

  req "prepare-tables-aug" GET "/api/v1/workflows/$aug/gstr1/tables"
  expect_status 200 || vsa_fail "August tables failed: $RESP"
  expect_json '[.tables[].tableCode] | index("B2CL") != null' \
    || vsa_fail "August 1.5L inter-state invoice should be B2CL at the 1L threshold: $RESP"
  expect_json '[.tables[].tableCode] | index("B2CS") == null' \
    || vsa_fail "August 1.5L inter-state invoice must not be B2CS: $RESP"
  expect_json '([.tables[] | select(.tableCode == "B2CL")][0].totals.taxableValue // 0) == 150000' \
    || vsa_fail "August B2CL taxable should be 150000: $RESP"

  # ---------- recon side: baseline Apr-Aug 2024, import and reconcile same invoices ----------
  as_owner
  req "recon-org-create" POST /api/organizations \
    '{"name":"Verify B2CL Recon","gstin":"29AAVCS5633D1ZL","pan":"AAVCS5633D","registeredAddress":"Test Street 1","financialYearStart":4}'
  expect_status 201 || vsa_fail "recon org create failed: $RESP"
  local recon_org; recon_org=$(echo "$RESP" | jq -r .id)

  VSA_JWT="$(state_get owner_token)"
  vsa_org_accountant "$recon_org" b2clrecon
  vsa_psql "update organizations set books_start_date='2024-04-01' where id='$recon_org'"

  req "recon-masters-upload" POST /api/v1/uploads "" "$fixtures/masters.json"
  expect_status "200|201" || vsa_fail "recon masters upload failed: $RESP"

  local items='[' first=1 r role label h
  for r in OUTPUT:Output INPUT:Input CASH_BALANCE:Cash LIABILITY_BALANCE:Liability CREDIT_BALANCE:"Credit Balance" GST_TDS:"GST TDS"; do
    role=${r%%:*}; label=${r#*:}
    for h in CGST SGST IGST; do
      [ $first = 1 ] && first=0 || items="$items,"
      items="$items{\"ledgerName\":\"$label $h\",\"role\":\"$role\",\"head\":\"$h\"}"
    done
  done
  items="$items]"
  req "recon-tax-ledger-mapping" PUT /api/v1/gst/tax-ledger-mapping "{\"items\":$items}"
  expect_status "200|201" || vsa_fail "recon tax ledger mapping failed: $RESP"
  save_tds_mapping_from_view

  req "recon-responsibility-start" PUT /api/v1/compliance/responsibility-start \
    '{"type":"GSTR1","firstPeriodStart":"2024-09-01"}'
  expect_status "200|201" || vsa_fail "recon responsibility-start failed: $RESP"

  # A throwaway GSTR3B workflow carries one uploaded day book covering the
  # Apr-Aug baseline range; recon reuses the completed upload job.
  req "recon-books-workflow" POST /api/v1/workflows '{"type":"GSTR3B","periodKey":"2024-09"}'
  expect_status 200 || vsa_fail "recon books workflow create failed: $RESP"
  local books_wf; books_wf=$(echo "$RESP" | jq -r .id)
  local out
  out=$(curl -s -X POST -H "Authorization: Bearer $VSA_JWT" \
        -F "file=@$fixtures/daybook-b2cl-threshold.json" \
        -F "coverageFrom=2024-04-01" -F "coverageTo=2024-09-30" \
        -o "$VSA_EVIDENCE_RUN/last-body" -w '%{http_code}' \
        "http://localhost:${VSA_API_PORT}/api/v1/workflows/$books_wf/upload") \
    || vsa_fail "recon daybook upload curl failed"
  STATUS="$out"; RESP=$(cat "$VSA_EVIDENCE_RUN/last-body")
  VSA_METHOD=POST; VSA_PATH="/api/v1/workflows/$books_wf/upload"; VSA_STEP="recon-daybook-upload"
  expect_status 200 || vsa_fail "recon daybook upload failed: $RESP"

  req "recon-baseline-list" GET /api/v1/filed-returns/baseline
  expect_status 200 || vsa_fail "baseline list failed: $RESP"
  local jul_row aug_row
  jul_row=$(echo "$RESP" | jq -r '.[] | select(.type == "GSTR1" and .periodKey == "2024-07") | .workflowId')
  aug_row=$(echo "$RESP" | jq -r '.[] | select(.type == "GSTR1" and .periodKey == "2024-08") | .workflowId')
  [ -n "$jul_row" ] && [ "$jul_row" != "null" ] || vsa_fail "no July 2024 GSTR1 baseline row: $RESP"
  [ -n "$aug_row" ] && [ "$aug_row" != "null" ] || vsa_fail "no August 2024 GSTR1 baseline row: $RESP"

  VSA_FORM_PARAM="GSTR1"
  req "recon-import-jul" POST /api/v1/filed-returns/gstr1/json "" "$fixtures/gstr1-b2cl-threshold-jul.json"
  VSA_FORM_PARAM=""
  expect_status 201 || vsa_fail "July filed-return import failed: $RESP"

  VSA_FORM_PARAM="GSTR1"
  req "recon-import-aug" POST /api/v1/filed-returns/gstr1/json "" "$fixtures/gstr1-b2cl-threshold-aug.json"
  VSA_FORM_PARAM=""
  expect_status 201 || vsa_fail "August filed-return import failed: $RESP"

  req "recon-jul" POST "/api/v1/filed-returns/baseline/$jul_row/reconcile"
  expect_status 200 || vsa_fail "July reconcile failed: $RESP"
  expect_json '.status == "RECONCILED"' || vsa_fail "July reconcile status: $RESP"
  expect_json '.material == 0' \
    || vsa_fail "July recon must agree with prepare (B2CS at the 2.5L threshold): $RESP"

  req "recon-aug" POST "/api/v1/filed-returns/baseline/$aug_row/reconcile"
  expect_status 200 || vsa_fail "August reconcile failed: $RESP"
  expect_json '.status == "RECONCILED"' || vsa_fail "August reconcile status: $RESP"
  expect_json '.material == 0' \
    || vsa_fail "August recon must agree with prepare (B2CL at the 1L threshold): $RESP"
}
