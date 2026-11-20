# Scenario: GSTR-1 prepared tables (GET /{id}/gstr1/tables)
run_scenario() {
  bootstrapped || vsa_bootstrap
  local id; id=$(fresh_workflow GSTR1 "$(next_period)")

  as_accountant
  req "gstr1-tables" GET "/api/v1/workflows/$id/gstr1/tables"
  expect_status 200
  expect_json 'type == "object"'

  as_operator
  req "gstr1-tables-operator" GET "/api/v1/workflows/$id/gstr1/tables"
  expect_status 200
}
