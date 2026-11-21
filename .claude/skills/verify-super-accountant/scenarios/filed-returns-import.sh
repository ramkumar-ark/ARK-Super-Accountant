# Scenario: filed-return portal JSON import + workflow portal-upload record
run_scenario() {
  bootstrapped || vsa_bootstrap
  local fixture="$VSA_DIR/scenarios/fixtures/gstr1-portal.json"

  as_accountant
  VSA_FORM_PARAM="GSTR1"
  req "filed-returns-import" POST /api/v1/filed-returns/gstr1/json "" "$fixture"
  VSA_FORM_PARAM=""
  expect_status 201
  local snap; snap=$(echo "$RESP" | jq -r .id)
  expect_json '.id != null'

  local id; id=$(state_get "wf_TDS_PAYMENT_2026-04")
  if [ -z "$id" ]; then id=$(fresh_workflow TDS_PAYMENT "2026-04"); fi
  req "snapshots-list" GET "/api/v1/filed-returns/snapshots?workflowId=$id"
  expect_status 200
  expect_json 'type == "array"'
  tds_nil_challan "$id"
  as_accountant
  local stage; stage=$(curl -s -H "Authorization: Bearer $VSA_JWT" \
    "http://localhost:${VSA_API_PORT}/api/v1/workflows/$id" | jq -r '.workflow.stage // .stage')
  if [ "$stage" != "FINALIZED" ]; then
    id=$(fresh_workflow TDS_PAYMENT "2026-04")
    tds_nil_challan "$id"
    req "finalize-workflow" POST "/api/v1/workflows/$id/finalize" '{}'
    expect_status 200 || vsa_fail "finalize failed: $RESP"
  fi
  req "portal-upload-record" POST "/api/v1/workflows/$id/gstr1/portal-upload" \
    "{\"fileHash\":\"$(sha256sum "$fixture" | cut -d' ' -f1)\",\"note\":\"verify scenario portal upload\"}"
  expect_status 200
  expect_json '.recorded == true'
}
