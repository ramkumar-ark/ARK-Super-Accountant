# Scenario: workflow lifecycle (create -> 403/409 guards -> PREPARE -> finalize -> events)
run_scenario() {
  bootstrapped || vsa_bootstrap
  local period; period=$(next_period)

  as_operator
  req "operator-create" POST /api/v1/workflows '{"type":"GSTR1","periodKey":"'$period'"}'
  expect_status 200
  local id; id=$(echo "$RESP" | jq -r .id)
  expect_json '(.workflow.stage // .stage) != null'

  req "operator-advance-forbidden" POST "/api/v1/workflows/$id/advance"
  # advance requires ACCOUNTANT; operator gets 403
  expect_status 403

  as_accountant
  # A bare STARTED workflow has no daybook data; advance is a 409.
  req "advance-without-data" POST "/api/v1/workflows/$id/advance"
  expect_status 409

  local id2; id2=$(fresh_workflow TDS_PAYMENT "2027-01")
  tds_nil_challan "$id2"
  req "accountant-finalize" POST "/api/v1/workflows/$id2/finalize"
  expect_status 200
  expect_json '(.workflow.stage // .stage) == "FINALIZED"'

  req "events" GET "/api/v1/workflows/$id2/events"
  expect_status 200
  expect_contains 'FINALIZED'

  as_accountant
  req "unfinalize" POST "/api/v1/workflows/$id2/unfinalize" '{"reason":"verify scenario"}'
  expect_status 200
  expect_json '(.workflow.stage // .stage) == "PREPARE"'

  # Re-finalize and leave the workflow FINALIZED so a later scenario can start
  # an amendment revision for the same period.
  req "re-finalize" POST "/api/v1/workflows/$id2/finalize" '{}'
  expect_status 200
  expect_json '(.workflow.stage // .stage) == "FINALIZED"'
}
