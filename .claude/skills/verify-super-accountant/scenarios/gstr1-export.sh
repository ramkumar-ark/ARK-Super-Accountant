# Scenario: GSTR-1 export (GET /{id}/gstr1/export)
run_scenario() {
  bootstrapped || vsa_bootstrap
  local id; id=$(fresh_workflow GSTR1 "$(next_period)")

  as_accountant
  req "fill-in-before-export" PUT "/api/v1/workflows/$id/gstr1/fill-ins" \
    '{"scope":"LEDGER","key":"Synthetic Sales Ledger","attribute":"HSN","value":"01011010"}'
  expect_status 200

  req "export-json" GET "/api/v1/workflows/$id/gstr1/export"
  expect_status 200
  expect_json 'type == "object"'
  expect_contains 'doc_issue'

  as_operator
  req "export-operator-forbidden" GET "/api/v1/workflows/$id/gstr1/export"
  # export requires ACCOUNTANT
  expect_status 403
}
