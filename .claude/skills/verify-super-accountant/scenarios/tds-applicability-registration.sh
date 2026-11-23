# Scenario: TDS applicability at registration and via the applicability API.
# Covers: company PAN (4th char C -> entity K) always TDS-applicable and keeps a
# supplied TAN; individual PAN (P -> entity Q) below the turnover limit is not
# applicable and never stores a TAN even when one is sent; missing turnover
# answer, malformed TAN, missing entity type rejections; profession (50 lakh)
# vs business (1 crore) derived answers; GET/PUT /tds-applicability including
# the company 400, operator 403 and missing-field 400 guards.

# tdsap_new_org <json-extra-fields>: create an organization as the bootstrap
# owner, remember its id in $ORGA_ID.
tdsap_new_org() {
  local extra="$1"
  req "create-$2" POST /api/organizations "{\"name\":\"$2\",$extra}"
  ORGA_ID=""
  if [ "$STATUS" = "201" ]; then
    ORGA_ID=$(echo "$RESP" | jq -r .id)
  fi
}

run_scenario() {
  bootstrapped || vsa_bootstrap
  as_owner

  # (a) Company PAN with a TAN: deductor K, TAN normalised, applicable now.
  tdsap_new_org '"pan":"AAACA1234A","tan":"blra12345b"' "TDS Company Org"
  expect_status 201 || vsa_fail "company org create failed: $RESP"
  expect_json '.deductorType == "K"'
  expect_json '.tan == "BLRA12345B"'
  expect_json '.tdsApplicable == true'
  state_set tdsa_company_id "$ORGA_ID"

  # (a) Individual PAN below the limit, TAN sent anyway: not applicable and no
  # TAN stored.
  tdsap_new_org '"pan":"AAAPA1234A","tdsTurnoverExceeded":false,"tan":"BLRA12345B"' "TDS Individual Org"
  expect_status 201 || vsa_fail "individual org create failed: $RESP"
  expect_json '.deductorType == "Q"'
  expect_json '.tdsApplicable == false'
  VSA_STEP="individual-tan-not-stored"
  if echo "$RESP" | jq -e '.tan == null or .tan == ""' >/dev/null; then ok=true; else ok=false; fi
  vsa_log_step "$VSA_SCENARIO" "$VSA_STEP" POST /api/organizations "$STATUS" "tan absent" "$ok"
  [ "$ok" = true ] || vsa_fail "TAN was stored for a non-deductor: $RESP"
  state_set tdsa_indiv_id "$ORGA_ID"

  # (a) Missing turnover answer for an individual PAN: 400.
  tdsap_new_org '"pan":"AAAPA1234B"' "TDS No Answer Org"
  expect_status 400
  expect_contains "Tell us whether last year's turnover exceeded"

  # (a) Malformed TAN: 400.
  tdsap_new_org '"pan":"AAACA1234B","tan":"bad"' "TDS Bad Tan Org"
  expect_status 400
  expect_contains "TAN"

  # (a) Profession (50 lakh limit): 40 lakh professional fees stays below.
  tdsap_new_org '"pan":"AAAPA1234C","priorYearTurnover":4000000,"turnoverNature":"PROFESSION"' "TDS Prof Low Org"
  expect_status 201 || vsa_fail "profession-low create failed: $RESP"
  expect_json '.tdsApplicable == false'

  # (a) Business (1 crore limit): 1.1 crore crosses the BUSINESS_TURNOVER_LIMIT
  # (1,00,00,000) so TDS applies without an explicit tdsTurnoverExceeded.
  tdsap_new_org '"pan":"AAAPA1234D","priorYearTurnover":11000000,"turnoverNature":"BUSINESS"' "TDS Biz Mid Org"
  expect_status 201 || vsa_fail "business-mid create failed: $RESP"
  expect_json '.tdsApplicable == true'

  # (a) No PAN and no entity type: 400 asking to choose the entity type.
  tdsap_new_org '"financialYearStart":4' "TDS No Pan Org"
  expect_status 400
  expect_contains "Choose the entity type"

  # (b) GET applicability for the company: entity K captured at registration,
  # all years applicable.
  local cid; cid=$(state_get tdsa_company_id)
  req "get-company-applicability" GET "/api/v1/organizations/$cid/tds-applicability"
  expect_status 200
  expect_json '.entityCode == "K" and .entityCodeSource == "DEDUCTOR_TYPE"'
  expect_json '(.years | length) > 0'
  expect_json 'all(.years[]; .applicable == true)'

  # (b) Company PUT rejected: entity K always deducts TDS.
  req "put-company-rejected" PUT "/api/v1/organizations/$cid/tds-applicability/2026" '{"applicable":false}'
  expect_status 400
  expect_contains "always deducts"

  # (b) PUT without the applicable field: 400.
  local iid; iid=$(state_get tdsa_indiv_id)
  req "put-missing-field" PUT "/api/v1/organizations/$iid/tds-applicability/2026" '{"priorYearTurnover":100}'
  expect_status 400

  # (b) Operator cannot change applicability: 403.
  as_operator
  req "put-operator-forbidden" PUT "/api/v1/organizations/$iid/tds-applicability/2026" '{"applicable":false}'
  expect_status 403

  # (b) Owner PUT on the individual org: record a DECLARED not-applicable year,
  # then flip it true and back via the API.
  as_owner
  req "select-indiv-org" POST "/api/organizations/$iid/select"
  expect_status 200 || vsa_fail "select individual org failed: $RESP"
  state_set tdsa_indiv_owner_token "$(echo "$RESP" | jq -r .token)"
  VSA_JWT="$(state_get tdsa_indiv_owner_token)"
  req "put-indiv-declared-true" PUT "/api/v1/organizations/$iid/tds-applicability/2026" '{"applicable":true,"priorYearTurnover":1000000,"turnoverNature":"BUSINESS","note":"declared at setup"}'
  expect_status 200 || vsa_fail "indiv PUT true failed: $RESP"
  req "get-indiv-declared" GET "/api/v1/organizations/$iid/tds-applicability"
  expect_status 200
  expect_json '[.years[] | select(.financialYear == 2026)][0].applicable == true'
  req "put-indiv-declared-false" PUT "/api/v1/organizations/$iid/tds-applicability/2026" '{"applicable":false,"priorYearTurnover":1000000,"turnoverNature":"BUSINESS","note":"still under the limit"}'
  expect_status 200
  req "get-indiv-final" GET "/api/v1/organizations/$iid/tds-applicability"
  expect_status 200
  expect_json '[.years[] | select(.financialYear == 2026)][0].applicable == false'
}
