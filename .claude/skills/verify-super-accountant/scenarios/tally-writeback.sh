# Scenario: Tally write-back change items (PAN only, JSONEx import file)
# Covers: PAN_FORMAT acknowledgement (including "No PAN / not available"), change-item listing,
# export file shape, export -> EXPORTED, wrong-value upload -> FAILED + retry, target-value upload
# -> APPLIED, and role separation on export/retry.
run_scenario() {
  bootstrapped || vsa_bootstrap
  local F="$VSA_DIR/scenarios/fixtures"

  as_accountant

  req "upload-base" POST /api/v1/uploads "" "$F/tally-writeback-base.json"
  expect_status 201
  local job; job=$(jq -r '.id // empty' <<<"$RESP")
  [ -n "$job" ] || vsa_fail "base masters upload returned no job id"

  local vid nid
  vid=$(jq -r '[.findings[] | select(.ledgerName=="Verify Tally Vendor" and .ruleCode=="PAN_FORMAT")][0].id // empty' <<<"$RESP")
  nid=$(jq -r '[.findings[] | select(.ledgerName=="Verify Tally NoPan" and .ruleCode=="PAN_FORMAT")][0].id // empty' <<<"$RESP")
  [ -n "$vid" ] && [ -n "$nid" ] || vsa_fail "expected two PAN_FORMAT findings, got vendor='$vid' nopan='$nid'"

  req "ack-valid-pan" POST "/api/v1/uploads/$job/findings/$vid/action" \
    '{"action":"ACKNOWLEDGE","value":"AAAPL1234C"}'
  expect_status 200

  req "ack-no-pan" POST "/api/v1/uploads/$job/findings/$nid/action" \
    '{"action":"ACKNOWLEDGE","noPan":true}'
  expect_status 200

  req "list-pending" GET /api/v1/tally-changes
  expect_status 200
  expect_json '(.items | length) == 2'
  expect_json '[.items[] | select(.ledgerName=="Verify Tally Vendor" and .status=="PENDING" and .newValue=="AAAPL1234C" and .field=="PAN")] | length == 1'
  expect_json '[.items[] | select(.ledgerName=="Verify Tally NoPan" and .status=="PENDING" and .newValue=="" and .field=="PAN")] | length == 1'
  expect_json '.mastersUpload.jobId != null and .mastersUpload.stale == false'

  as_operator
  req "operator-export-forbidden" POST /api/v1/tally-changes/export '{}'
  expect_status 403

  as_accountant
  req "export-pending" POST /api/v1/tally-changes/export '{}'
  expect_status 200
  echo "$RESP" > "$VSA_EVIDENCE_RUN/tally-export.json"
  expect_json '.static_variables[0].name == "svMstImportFormat" and .static_variables[0].value == "jsonex"'
  expect_json '(.tallymessage | length) == 2'
  expect_json '[.tallymessage[] | select(.name=="Verify Tally Vendor" and .metadata.type=="Ledger" and .metadata.action=="Alter" and .incometaxnumber=="AAAPL1234C")] | length == 1'
  expect_json '[.tallymessage[] | select(.name=="Verify Tally NoPan" and .incometaxnumber=="")] | length == 1'

  req "list-exported" GET /api/v1/tally-changes
  expect_status 200
  expect_json '[.items[] | select(.status=="EXPORTED")] | length == 2'

  req "upload-wrong-pan" POST /api/v1/uploads "" "$F/tally-writeback-wrong.json"
  expect_status 201

  local tries failed
  for tries in $(seq 1 20); do
    req "poll-failed" GET /api/v1/tally-changes
    expect_status 200
    failed=$(jq -r '[.items[] | select(.status=="FAILED" and .failureReason=="TALLY_VALUE_CHANGED")] | length' <<<"$RESP")
    [ "$failed" = "2" ] && break
    sleep 1
  done
  [ "$failed" = "2" ] || vsa_fail "expected both exported items to become FAILED/TALLY_VALUE_CHANGED, saw $failed"

  local vid_item nid_item
  vid_item=$(jq -r '[.items[] | select(.ledgerName=="Verify Tally Vendor" and .status=="FAILED")][0].id // empty' <<<"$RESP")
  nid_item=$(jq -r '[.items[] | select(.ledgerName=="Verify Tally NoPan" and .status=="FAILED")][0].id // empty' <<<"$RESP")
  [ -n "$vid_item" ] && [ -n "$nid_item" ] || vsa_fail "failed items missing ids"

  req "retry-vendor" POST "/api/v1/tally-changes/$vid_item/retry" '{}'
  expect_status 200
  expect_json '.status == "PENDING" and .failureReason == null'
  req "retry-nopan" POST "/api/v1/tally-changes/$nid_item/retry" '{}'
  expect_status 200
  expect_json '.status == "PENDING" and .failureReason == null'

  req "upload-applied" POST /api/v1/uploads "" "$F/tally-writeback-applied.json"
  expect_status 201

  local tries applied
  for tries in $(seq 1 20); do
    req "poll-applied" GET /api/v1/tally-changes
    expect_status 200
    applied=$(jq -r '[.items[] | select(.status=="APPLIED")] | length' <<<"$RESP")
    [ "$applied" = "2" ] && break
    sleep 1
  done
  [ "$applied" = "2" ] || vsa_fail "expected both change items APPLIED, saw $applied"

  expect_json '[.items[] | select(.ledgerName=="Verify Tally Vendor" and .status=="APPLIED" and .observedValue==null)] | length == 1'
  expect_json '[.items[] | select(.ledgerName=="Verify Tally NoPan" and .status=="APPLIED" and .observedValue==null)] | length == 1'

  req "export-after-applied" POST /api/v1/tally-changes/export '{}'
  expect_status 409
  expect_json '.code == "NOTHING_ELIGIBLE"'
}
