# Scenario: GST tax-ledger mapping gates day-book uploads over HTTP for a
# GSTIN company org. The org is a company-PAN TDS deductor, so the initial
# day-book upload is refused (422) with BOTH the TDS and GST ledger-mapping
# sentences (combined refusal); the mapping view starts blocked with every
# GST ledger unmapped (gstApplicable true), and the full area x head save
# plus the TDS mapping save (company deductor, same as ensure_masters)
# complete both gates so the same upload succeeds.

gstlm_as_map() { VSA_JWT=$(state_get acct_token_gstlm); }

run_scenario() {
  bootstrapped || vsa_bootstrap
  as_owner
  local org
  org=$(state_get gstlm_org_id)
  if [ -z "$org" ]; then
    req "gstlm-create-org" POST /api/organizations \
      '{"name":"GST Ledger Mapping Org","pan":"AAVCS5633D","gstin":"27AAPFU0939F1ZV"}'
    expect_status 201 || vsa_fail "company org create failed: $RESP"
    org=$(echo "$RESP" | jq -r .id)
    state_set gstlm_org_id "$org"
  fi
  vsa_org_accountant "$org" "gstlm"
  gstlm_as_map

  req "gstlm-upload-masters" POST /api/v1/uploads "" \
    "$VSA_DIR/scenarios/fixtures/masters.json"
  expect_status "200|201" || vsa_fail "masters upload failed: $RESP"

  req "gstlm-view-initial" GET /api/v1/gst/tax-ledger-mapping
  expect_status 200 || vsa_fail "mapping view failed: $RESP"
  expect_json '.complete == false'
  expect_json '.gstApplicable == true'
  VSA_STEP="gstlm-view-blockers"; ok=false
  echo "$RESP" | jq -e '.blockers[0] | contains("tax ledgers not mapped")' >/dev/null && ok=true || ok=false
  vsa_log_step "$VSA_SCENARIO" "$VSA_STEP" GET "$VSA_PATH" "$STATUS" "unmapped GST ledgers block" "$ok"
  [ "$ok" = true ] || vsa_fail "unexpected blockers: $RESP"

  local dd="$VSA_EVIDENCE_RUN"
  jq '[.tallymessage | to_entries[] | .value.date = ("202605" + (["01","05","07","09"])[.key % 4]) | .value] | {tallymessage: .}' \
    "$VSA_DIR/scenarios/fixtures/daybook.json" > "$dd/gstlm-daybook.json"
  req "gstlm-responsibility-start" PUT /api/v1/compliance/responsibility-start \
    '{"type":"GSTR1","firstPeriodStart":"2026-05-01"}'
  expect_status "200|201" || vsa_fail "responsibility-start failed: $RESP"
  req "gstlm-start-gstr3b" POST /api/v1/workflows '{"type":"GSTR3B","periodKey":"2026-05"}'
  expect_status 200 || vsa_fail "GSTR3B start failed: $RESP"
  local gid; gid=$(echo "$RESP" | jq -r .id)

  local rurl="http://localhost:${VSA_API_PORT}/api/v1/workflows/$gid/upload" rout
  rout=$(curl -s -X POST -H "Authorization: Bearer $VSA_JWT" \
        -F "file=@$dd/gstlm-daybook.json" \
        -F "coverageFrom=2026-04-01" -F "coverageTo=2026-05-31" \
        -o "$dd/last-body" -w '%{http_code}' "$rurl")
  STATUS="$rout"; RESP=$(cat "$dd/last-body"); VSA_METHOD=POST; VSA_PATH="/api/v1/workflows/$gid/upload"; VSA_STEP="gstlm-daybook-upload-refused"
  expect_status 422 || vsa_fail "day-book upload while unmapped should be a 422: $RESP"
  expect_contains "TDS ledger mapping"
  expect_contains "GST tax-ledger mapping"

  local items='[' first=1 r h role label
  for r in OUTPUT:Output INPUT:Input CASH_BALANCE:Cash LIABILITY_BALANCE:Liability CREDIT_BALANCE:"Credit Balance" GST_TDS:"GST TDS"; do
    role=${r%%:*}; label=${r#*:}
    for h in CGST SGST IGST; do
      [ $first = 1 ] && first=0 || items="$items,"
      items="$items{\"ledgerName\":\"$label $h\",\"role\":\"$role\",\"head\":\"$h\"}"
    done
  done
  items="$items]"
  req "gstlm-put-mapping" PUT /api/v1/gst/tax-ledger-mapping "{\"items\":$items}"
  expect_status "200|201" || vsa_fail "tax ledger mapping failed: $RESP"

  save_tds_mapping_from_view

  req "gstlm-view-complete" GET /api/v1/gst/tax-ledger-mapping
  expect_status 200 || vsa_fail "mapping view failed: $RESP"
  expect_json '.complete == true'
  expect_json '.gstApplicable == true'
  VSA_STEP="gstlm-blockers-cleared"; ok=false
  [ "$(echo "$RESP" | jq -c .blockers)" = '[]' ] && ok=true || ok=false
  vsa_log_step "$VSA_SCENARIO" "$VSA_STEP" GET "$VSA_PATH" "$STATUS" "blockers cleared by mapping save" "$ok"
  [ "$ok" = true ] || vsa_fail "blockers not cleared: $RESP"

  rout=$(curl -s -X POST -H "Authorization: Bearer $VSA_JWT" \
        -F "file=@$dd/gstlm-daybook.json" \
        -F "coverageFrom=2026-04-01" -F "coverageTo=2026-05-31" \
        -o "$dd/last-body" -w '%{http_code}' "$rurl")
  STATUS="$rout"; RESP=$(cat "$dd/last-body"); VSA_METHOD=POST; VSA_PATH="/api/v1/workflows/$gid/upload"; VSA_STEP="gstlm-daybook-upload-after-mapping"
  expect_status 200 || vsa_fail "day-book upload after mapping failed: $RESP"

  as_accountant
}
