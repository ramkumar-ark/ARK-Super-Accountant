# Scenario: masters TDS section labels span both Acts. A masters mapping applies
# across FY 2025-26 and FY 2026-27, so its finding text must show the old section
# and the Income-tax Act 2025 s.393 reference together, old first.
run_scenario() {
  bootstrapped || vsa_bootstrap
  ensure_masters
  as_accountant
  req "masters-tds-findings" GET "/api/v1/uploads/latest/mismatches?ruleCode=TDS_SECTION_MAPPING&size=100"
  expect_status 200

  local fix; fix=$(echo "$RESP" | jq -r '.content[0].suggestedFix // empty')
  VSA_STEP="suggested-fix-both-names"
  if [ -n "$fix" ] \
     && [[ "$fix" == *"194C / 393(1) [Table: Sl. No. 6(i).D(b)]"* ]] \
     && [[ "$fix" == *"194J_A / 393(1) [Table: Sl. No. 6(iii).D(a)]"* ]]; then
    ok=true
  else
    ok=false
  fi
  vsa_log_step "$VSA_SCENARIO" "$VSA_STEP" GET "$VSA_PATH" "$STATUS" \
    "suggestedFix shows old and s.393 names" "$ok"
  [ "$ok" = true ] || vsa_fail "masters TDS suggestedFix still shows old-only names: $fix"
}
