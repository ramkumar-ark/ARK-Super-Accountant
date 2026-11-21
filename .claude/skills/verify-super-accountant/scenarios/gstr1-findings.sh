# Scenario: GSTR-1 validation + findings surfaces
run_scenario() {
  bootstrapped || vsa_bootstrap
  local id; id=$(fresh_workflow GSTR1 "$(next_period)")

  as_accountant
  req "gstr1-validation" GET "/api/v1/workflows/$id/gstr1/validation"
  expect_status 200
  expect_json '.errors != null and .warnings != null'

  req "findings-list" GET "/api/v1/workflows/$id/findings"
  expect_status 200
  expect_json '.findings | type == "array"'

  req "findings-action-bogus" POST "/api/v1/workflows/$id/findings/00000000-0000-0000-0000-000000000000/action" \
    '{"action":"ACKNOWLEDGE"}'
  # A nonexistent finding id must not succeed silently.
  [ "$STATUS" -ge 400 ] && ok=true || ok=false
  vsa_log_step "$VSA_SCENARIO" "findings-action-bogus" "$VSA_METHOD" "$VSA_PATH" "$STATUS" "status>=400" "$ok"
  [ "$ok" = true ]
}
