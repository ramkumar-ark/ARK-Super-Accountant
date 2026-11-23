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

  # Discard flow:
  # Finalized workflow cannot be discarded (409)
  req "discard-finalized-conflict" POST "/api/v1/workflows/$id2/discard"
  expect_status 409

  # In an organization without existing TDS_PAYMENT workflows, starting a workflow locks
  # responsibility-start and discarding it unlocks responsibility-start.
  as_owner
  local disc_org="Workflow Discard Org $(state_get runid)"
  req "create-discard-org" POST /api/organizations "{\"name\":\"$disc_org\",\"pan\":\"AAACA8888X\",\"financialYearStart\":4}"
  expect_status 201 || vsa_fail "create discard org failed: $RESP"
  local d_org_id; d_org_id=$(echo "$RESP" | jq -r .id)
  vsa_org_accountant "$d_org_id" "discard"
  VSA_JWT="$(state_get acct_token_discard)"

  # Initial responsibility start for TDS_PAYMENT is unlocked
  req "resp-start-check-initial" GET /api/v1/compliance/responsibility-start
  expect_status 200
  local tds_locked
  tds_locked=$(echo "$RESP" | jq -r '.[] | select(.type=="TDS_PAYMENT") | .locked')
  [ "$tds_locked" = "false" ] || vsa_fail "expected TDS_PAYMENT to be unlocked initially, got: $tds_locked"

  # Invite operator for the discard org to verify 403
  as_owner
  req "invite-discard-operator" POST "/api/organizations/$d_org_id/invites?role=operator"
  expect_status 200 || vsa_fail "invite discard operator failed: $RESP"
  local d_op_inv; d_op_inv=$(echo "$RESP" | jq -r .token)
  local d_op_uname="v$(echo "$d_org_id" | tr -d '-' | cut -c1-12)p"
  req "signup-discard-operator" POST "/api/auth/signup?invite=$d_op_inv" \
    "{\"username\":\"$d_op_uname\",\"email\":\"$d_op_uname@verify.local\",\"role\":\"operator\",\"password\":\"Verify!discard\"}"
  expect_status 200 || vsa_fail "signup discard operator failed: $RESP"
  req "signin-discard-operator" POST /api/auth/signin "{\"username\":\"$d_op_uname\",\"password\":\"Verify!discard\"}"
  expect_status 200 || vsa_fail "signin discard operator failed: $RESP"
  local d_op_tok; d_op_tok=$(echo "$RESP" | jq -r .token)
  VSA_JWT="$d_op_tok"
  req "select-discard-operator" POST "/api/organizations/$d_org_id/select"
  expect_status 200 || vsa_fail "select discard operator failed: $RESP"
  local d_op_org_tok; d_op_org_tok=$(echo "$RESP" | jq -r .token)

  # Start a discardable STARTED workflow (TDS_PAYMENT) as accountant
  VSA_JWT="$(state_get acct_token_discard)"
  local disc_period="2026-05"
  req "create-discardable" POST /api/v1/workflows '{"type":"TDS_PAYMENT","periodKey":"'$disc_period'"}'
  expect_status 200
  local disc_id; disc_id=$(echo "$RESP" | jq -r .id)

  # Started workflow locks responsibility start for TDS_PAYMENT
  req "resp-start-check-locked" GET /api/v1/compliance/responsibility-start
  expect_status 200
  tds_locked=$(echo "$RESP" | jq -r '.[] | select(.type=="TDS_PAYMENT") | .locked')
  [ "$tds_locked" = "true" ] || vsa_fail "expected TDS_PAYMENT to be locked before discard, got: $tds_locked"

  # Operator cannot discard (403)
  VSA_JWT="$d_op_org_tok"
  req "operator-discard-forbidden" POST "/api/v1/workflows/$disc_id/discard"
  expect_status 403

  # Accountant discards the clean workflow (200)
  VSA_JWT="$(state_get acct_token_discard)"
  req "accountant-discard" POST "/api/v1/workflows/$disc_id/discard"
  expect_status 200
  expect_json '.active == false'

  # Discarded workflow no longer appears in workflow list
  req "workflows-list-omits-discarded" GET /api/v1/workflows
  expect_status 200
  local found
  found=$(echo "$RESP" | jq -r --arg did "$disc_id" '[.[] | select(.id == $did)] | length')
  [ "$found" = "0" ] || vsa_fail "discarded workflow still present in list: $disc_id"

  # Discarding already discarded workflow returns 409
  req "discard-already-discarded" POST "/api/v1/workflows/$disc_id/discard"
  expect_status 409

  # Responsibility start is now unlocked again
  req "resp-start-check-unlocked" GET /api/v1/compliance/responsibility-start
  expect_status 200
  tds_locked=$(echo "$RESP" | jq -r '.[] | select(.type=="TDS_PAYMENT") | .locked')
  [ "$tds_locked" = "false" ] || vsa_fail "expected TDS_PAYMENT to be unlocked after discard, got: $tds_locked"

  # Now verify that a workflow with day-book data bound cannot be discarded (409)
  local disc_period_data="2026-06"
  req "create-data-wf" POST /api/v1/workflows '{"type":"TDS_PAYMENT","periodKey":"'$disc_period_data'"}'
  expect_status 200
  local data_wf_id; data_wf_id=$(echo "$RESP" | jq -r .id)

  # Upload masters and set tax ledger mapping for discard org so daybook upload succeeds
  req "upload-discard-masters" POST /api/v1/uploads "" "$VSA_DIR/scenarios/fixtures/masters.json"
  expect_status "200|201" || vsa_fail "discard org masters upload failed: $RESP"
  local disc_items='['
  local disc_first=1 dr dh drole dlabel
  for dr in OUTPUT:Output INPUT:Input CASH_BALANCE:Cash LIABILITY_BALANCE:Liability CREDIT_BALANCE:"Credit Balance" GST_TDS:"GST TDS"; do
    drole=${dr%%:*}; dlabel=${dr#*:}
    for dh in CGST SGST IGST; do
      [ $disc_first = 1 ] && disc_first=0 || disc_items="$disc_items,"
      disc_items="$disc_items{\"ledgerName\":\"$dlabel $dh\",\"role\":\"$drole\",\"head\":\"$dh\"}"
    done
  done
  disc_items="$disc_items]"
  req "discard-tax-mapping" PUT /api/v1/gst/tax-ledger-mapping "{\"items\":$disc_items}"
  expect_status "200|201" || vsa_fail "discard tax ledger mapping failed: $RESP"

  local dbfile="$VSA_EVIDENCE_RUN/daybook-discard-data.json"
  jq --arg pre "202606" \
    '[.tallymessage | to_entries[] | .value.date = ($pre + (["01","05","07","09"])[.key % 4]) | .value] | {tallymessage: .}' \
    "$VSA_DIR/scenarios/fixtures/daybook.json" > "$dbfile"
  local url="http://localhost:${VSA_API_PORT}/api/v1/workflows/$data_wf_id/upload"
  local out
  out=$(curl -s -X POST -H "Authorization: Bearer $VSA_JWT" \
        -F "file=@$dbfile" -F "coverageFrom=2026-04-01" -F "coverageTo=2026-06-30" \
        -o "$VSA_EVIDENCE_RUN/last-body" -w '%{http_code}' "$url")
  STATUS="$out"; RESP=$(cat "$VSA_EVIDENCE_RUN/last-body"); VSA_METHOD=POST; VSA_PATH="/api/v1/workflows/$data_wf_id/upload"; VSA_STEP="upload-data-wf"
  expect_status 200 || vsa_fail "daybook upload for discard data check failed: $RESP"

  # Discarding a workflow with day-book data bound is refused with 409
  req "discard-with-data-conflict" POST "/api/v1/workflows/$data_wf_id/discard"
  expect_status 409

  # Restore default accountant token for any subsequent scenarios
  as_accountant
}
