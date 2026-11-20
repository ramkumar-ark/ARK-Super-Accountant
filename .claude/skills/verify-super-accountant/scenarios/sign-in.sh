# Scenario: sign-in and role separation (HTTP)
run_scenario() {
  bootstrapped || vsa_bootstrap
  local acct_user; acct_user=$(state_get acct_user)
  local oper_user; oper_user=$(state_get oper_user)
  local pass; pass=$(state_get pass)

  as_operator
  req "signin-operator" POST /api/auth/signin "{\"username\":\"$oper_user\",\"password\":\"$pass\"}"
  expect_status 200
  expect_json '.token != null and .role != null'
  expect_contains 'OPERATOR'

  as_accountant
  req "signin-accountant" POST /api/auth/signin "{\"username\":\"$acct_user\",\"password\":\"$pass\"}"
  expect_status 200
  expect_json '.token != null and .role != null'
  expect_contains 'ACCOUNTANT'

  req "signin-bad-password" POST /api/auth/signin "{\"username\":\"$acct_user\",\"password\":\"wrong-password-1\"}"
  expect_status 401

  VSA_JWT=""
  req "unauthenticated-me" GET /api/organizations/me/list
  expect_status '401|403'
}
