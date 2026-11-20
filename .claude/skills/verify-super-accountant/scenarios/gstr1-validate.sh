# Scenario: GST validation endpoint (POST /api/gst/validate, synthetic Tally JSON)
run_scenario() {
  bootstrapped || vsa_bootstrap
  as_accountant
  local fixture="$VSA_DIR/scenarios/fixtures/gst-validate.json"

  VSA_JWT=""
  req "validate-unauthenticated" POST /api/gst/validate
  expect_status 401 || expect_status 403

  as_accountant
  req "validate-ok" POST /api/gst/validate "" "$fixture"
  expect_status 200
  expect_json '.totalVouchersAnalyzed >= 1'
  expect_json '.results != null'
}
