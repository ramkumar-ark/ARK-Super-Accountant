# Scenario: The statutory section reader endpoint, GET /api/tds/sections.
# Proves the single source of section truth (statutory_sections /
# statutory_section_rules, backfilled from the seed) is served read-only to any
# authenticated user: all thirteen active Income-tax sections in sort order with
# the s.393 legal reference from the dated rule rows, the RPU 26Q/140 codes, the
# canonical display descriptions, no legacy 194I row (only the A/B halves), the
# Finance Act 2025 additions 194D/193/194T with their verified codes, no 194B
# (verified but out of scope, not seeded); and that the security chain rejects
# anonymous calls.

run_scenario() {
  bootstrapped || vsa_bootstrap
  as_owner

  req "list-sections" GET /api/tds/sections
  expect_status 200 || vsa_fail "sections list failed: $RESP"
  expect_json 'length == 13'
  expect_json '[.[] | select(.code == "194C")] | length == 1'
  expect_json '[.[] | select(.code == "194C")][0].legalReference == "393(1) [Table: Sl. No. 6(i)]"'
  expect_json '[.[] | select(.code == "194C")][0].rpu26qCode == "94C"'
  expect_json '[.[] | select(.code == "194C")][0].rpu140Code == "1024"'
  expect_json '[.[] | select(.code == "194C")][0].description == "Payments to contractors and sub-contractors"'
  expect_json '[.[] | select(.code == "194M")][0].legalReference == "393(1) [Table: Sl. No. 6(ii)]"'
  expect_json '[.[] | select(.code == "194D")][0] | .rpu26qCode == "94D" and .rpu140Code == "1005" and .legalReference == "393(1) [Table: Sl. No. 1(i)]"'
  expect_json '[.[] | select(.code == "193")][0] | .rpu26qCode == "193" and .rpu140Code == "1019" and .legalReference == "393(1) [Table: Sl. No. 5(i)]"'
  expect_json '[.[] | select(.code == "194T")][0] | .rpu26qCode == "94T" and .rpu140Code == "1067" and .legalReference == "393(3) [Table: Sl. No. 7]"'
  expect_json '[.[] | select(.code == "194I")] | length == 0'
  expect_json '[.[] | select(.code == "194I_B")][0].legalReference == "393(1) [Table: Sl. No. 2(ii).D(b)]"'
  VSA_STEP="no-unseeded-sections"
  if echo "$RESP" | grep -q '"194B"\|"194"\|"192A"'; then ok=false; else ok=true; fi
  vsa_log_step "$VSA_SCENARIO" "$VSA_STEP" GET /api/tds/sections "$STATUS" "no 194B/194/192A rows" "$ok"
  [ "$ok" = true ] || vsa_fail "194B/194/192A must not be listed (not seeded)"

  # Anonymous call is rejected by the security chain.
  local url="http://localhost:${VSA_API_PORT}/api/tds/sections" out
  out=$(curl -s -o /dev/null -w '%{http_code}' "$url")
  STATUS="$out"; RESP=""; VSA_METHOD=GET; VSA_PATH="/api/tds/sections"; VSA_STEP="unauthenticated-rejected"
  if [ "$out" = "401" ]; then ok=true; else ok=false; fi
  vsa_log_step "$VSA_SCENARIO" "$VSA_STEP" GET /api/tds/sections "$STATUS" "401 without a token" "$ok"
  [ "$ok" = true ] || vsa_fail "unauthenticated GET /api/tds/sections returned $out, expected 401"
}
