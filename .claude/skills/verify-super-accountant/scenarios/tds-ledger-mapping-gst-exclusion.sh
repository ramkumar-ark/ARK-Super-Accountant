# Scenario: GST ledgers never appear in the TDS ledger mapping section, even
# when the masters snapshot does not flag them as GST. The fixture mixes
# Duties & Taxes ledgers with no GST taxtype/category (Output CGST/SGST with
# taxtype "Others", Input CGST/SGST with no taxtype, Input IGST, IGST Payable
# and Output Cess under a user "GST" sub-group) with a flagged GST ledger, a
# genuine TDS payable and ambiguous non-GST duty ledgers. The mapping view,
# its blockers and PUT must list/accept only the TDS payable and the
# ambiguous duty ledgers (suggested NOT_TDS), never a GST ledger.

tdsgst_as_map() { VSA_JWT=$(state_get acct_token_tdsgst); }

run_scenario() {
  bootstrapped || vsa_bootstrap
  as_owner
  local org
  org=$(state_get tdsgst_org_id)
  if [ -z "$org" ]; then
    req "tdsgst-create-org" POST /api/organizations \
      '{"name":"TDS GST Exclusion Org","pan":"AAVCG5633D","tan":"GSTX12345D"}'
    expect_status 201 || vsa_fail "company org create failed: $RESP"
    expect_json '.tdsApplicable == true'
    org=$(echo "$RESP" | jq -r .id)
    state_set tdsgst_org_id "$org"
  fi
  vsa_org_accountant "$org" "tdsgst"
  tdsgst_as_map

  req "tdsgst-upload-masters" POST /api/v1/uploads "" \
    "$VSA_DIR/scenarios/fixtures/tds-ledger-mapping-gst-masters.json"
  expect_status "200|201" || vsa_fail "masters upload failed: $RESP"

  req "tdsgst-view" GET /api/v1/tds/ledger-mapping
  expect_status 200 || vsa_fail "mapping view failed: $RESP"
  expect_json '[.ledgers[] | .ledgerName] == ["PF Payable","Professional Tax","TDS Payable - 194C A/c"]'
  expect_json '([.ledgers[] | {(.ledgerName): [.suggestedRole, .suggestedSection]}] | add) == {"PF Payable": ["NOT_TDS", null], "Professional Tax": ["NOT_TDS", null], "TDS Payable - 194C A/c": ["TDS_PAYABLE", "194C"]}'
  VSA_STEP="tdsgst-view-no-gst"; ok=false
  echo "$RESP" | jq -e '[.ledgers[] | .ledgerName | test("GST|Cess"; "i")] | any | not' >/dev/null && ok=true || ok=false
  vsa_log_step "$VSA_SCENARIO" "$VSA_STEP" GET "$VSA_PATH" "$STATUS" "no GST ledger in the TDS mapping rows" "$ok"
  [ "$ok" = true ] || vsa_fail "GST ledger listed in TDS mapping: $(echo "$RESP" | jq -c '[.ledgers[].ledgerName]')"
  VSA_STEP="tdsgst-blockers-no-gst"; ok=false
  [ "$(echo "$RESP" | jq -c .blockers)" = '["3 TDS ledgers not mapped: PF Payable, Professional Tax, TDS Payable - 194C A/c"]' ] && ok=true || ok=false
  vsa_log_step "$VSA_SCENARIO" "$VSA_STEP" GET "$VSA_PATH" "$STATUS" "blockers list no GST ledger" "$ok"
  [ "$ok" = true ] || vsa_fail "unexpected blockers: $(echo "$RESP" | jq -c .blockers)"

  req "tdsgst-put-gst-ledger" PUT /api/v1/tds/ledger-mapping \
    '{"items":[{"ledgerName":"Output CGST","role":"NOT_TDS"}]}'
  expect_status 400 || vsa_fail "a GST ledger must not be mappable as a TDS ledger: $RESP"
  expect_contains "Unknown TDS ledger"
}
