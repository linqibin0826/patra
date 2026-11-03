#!/usr/bin/env bash
# ============================================================================
# backfill-ads.sh 单测 —— 只跑 --dry-run（只打印要执行的 SQL，不连 ClickHouse）。
#   场景1 顺序与日期：十张表按固定顺序、每张表按天从早到晚，一天一条 INSERT … SELECT
#   场景2 明细下限：读 events 的表跳过 --events-floor 之前的天，读日活表的两张不跳过
#   场景3 参数缺失：缺 --from 报用法退出
# 运行：bash patra-infra/scripts/backfill-ads.test.sh
# ============================================================================
set -uo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PASS=0; FAIL=0

check() { # check <描述> <期望> <实际>
  if [ "${2}" = "${3}" ]; then
    PASS=$((PASS + 1)); printf '  ✓ %s\n' "${1}"
  else
    FAIL=$((FAIL + 1)); printf '  ✗ %s\n--- 期望\n%s\n--- 实际\n%s\n' "${1}" "${2}" "${3}"
  fi
}

sql() { # sql <表> <日期>
  printf "INSERT INTO %s SELECT * FROM %s_calc(from = '%s', to = '%s')" "${1}" "${1}" "${2}" "${2}"
}

echo "== 场景 1 / 2：顺序、日期与明细下限 =="
EXPECTED="$(
  sql device_user_links 2026-10-02; echo
  sql ads_daily_active 2026-10-01; echo
  sql ads_daily_active 2026-10-02; echo
  sql ads_person_first_day 2026-10-01; echo
  sql ads_person_first_day 2026-10-02; echo
  sql ads_cohort_retention 2026-10-01; echo
  sql ads_cohort_retention 2026-10-02; echo
  sql ads_daily_sessions 2026-10-02; echo
  sql ads_daily_search 2026-10-02; echo
  sql ads_daily_zero_result_terms 2026-10-02; echo
  sql ads_daily_counter_items 2026-10-02; echo
  sql ads_daily_quality 2026-10-02; echo
  sql ads_daily_duplicates 2026-10-02
)"
ACTUAL="$(bash "${SCRIPT_DIR}/backfill-ads.sh" --dry-run --from 2026-10-01 --to 2026-10-02 --events-floor 2026-10-02)"
check "十张表按顺序、按天，明细表从下限开始" "${EXPECTED}" "${ACTUAL}"

echo "== 场景 2b：跨月的日期递增 =="
ACTUAL="$(bash "${SCRIPT_DIR}/backfill-ads.sh" --dry-run --from 2026-09-30 --to 2026-10-01 --events-floor 2026-09-30 | grep '^INSERT INTO ads_daily_active ')"
EXPECTED="$(sql ads_daily_active 2026-09-30; echo; sql ads_daily_active 2026-10-01)"
check "09-30 之后是 10-01" "${EXPECTED}" "${ACTUAL}"

echo "== 场景 3：参数缺失 =="
if bash "${SCRIPT_DIR}/backfill-ads.sh" --dry-run --to 2026-10-02 --events-floor 2026-10-02 >/dev/null 2>&1; then
  MISSING_FROM=0
else
  MISSING_FROM=1
fi
check "缺 --from 退出码非 0" "1" "${MISSING_FROM}"

echo "== 场景 4：events 没有活跃分区 =="
TMP="$(mktemp -d)"
mkdir -p "${TMP}/bin" "${TMP}/home/.patra/secrets"
echo "CLICKHOUSE_PATRA_PASSWORD=stub" > "${TMP}/home/.patra/secrets/clickhouse.env"
printf '#!/usr/bin/env bash\necho "curl $*" >> "%s/calls.log"\n' "${TMP}" > "${TMP}/bin/curl"
chmod +x "${TMP}/bin/curl"
OUT="$(HOME="${TMP}/home" PATH="${TMP}/bin:${PATH}" bash "${SCRIPT_DIR}/backfill-ads.sh" --from 2026-10-01 --to 2026-10-01 2>&1)"
STATUS=$?
check "查不到分区时退出码非 0" "1" "$([ "${STATUS}" -ne 0 ] && echo 1 || echo 0)"
check "查不到分区时不执行任何 INSERT" "1" "$(grep -c '' "${TMP}/calls.log" | tr -d ' ')"
check "报错里说明原因" "1" "$(echo "${OUT}" | grep -c 'events 没有活跃分区')"
rm -rf "${TMP}"

echo "通过 ${PASS} / 失败 ${FAIL}"
[ "${FAIL}" -eq 0 ]
