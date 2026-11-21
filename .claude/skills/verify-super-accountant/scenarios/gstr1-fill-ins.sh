# Scenario: GSTR-1 fill-ins (PUT /{id}/gstr1/fill-ins) with re-read
run_scenario() {
  bootstrapped || vsa_bootstrap
  local id; id=$(fresh_workflow GSTR1 "$(next_period)")

  as_operator
  req "fill-ins-operator-forbidden" PUT "/api/v1/workflows/$id/gstr1/fill-ins" \
    '{"scope":"LEDGER","key":"Synthetic Sales Ledger","attribute":"HSN","value":"01011010"}'
  # fill-ins require ACCOUNTANT
  expect_status 403

  as_accountant
  req "fill-in-upsert" PUT "/api/v1/workflows/$id/gstr1/fill-ins" \
    '{"scope":"LEDGER","key":"Synthetic Sales Ledger","attribute":"HSN","value":"01011010"}'
  expect_status 200

  req "tables-reflect-fill-in" GET "/api/v1/workflows/$id/gstr1/tables"
  expect_status 200
  expect_contains '01011010'

  req "fill-in-clear" PUT "/api/v1/workflows/$id/gstr1/fill-ins" \
    '{"scope":"LEDGER","key":"Synthetic Sales Ledger","attribute":"HSN","value":""}'
  expect_status 204
}
