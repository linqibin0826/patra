#!/usr/bin/env bash
# ============================================================================
# 回填运营分析面板的 ADS 表（运营分析面板 spec §3.2、§8）：每张表按天调它的计算视图
#   INSERT INTO <表> SELECT * FROM <表>_calc(from = '<天>', to = '<天>')
# 刷新视图每小时只重算最近两天，更早的历史靠这个脚本补一次。
#
# 用法（在 Mac mini 上）：
#   bash patra-infra/scripts/backfill-ads.sh --from 2026-08-06 --to 2026-11-02
#   bash patra-infra/scripts/backfill-ads.sh --dry-run --from … --to … --events-floor 2026-08-10
#
# - 十张表按固定顺序：先 device_user_links（留存的认回要用），每人首日要在留存之前；每张表按天从早到晚，串行执行，
#   任一条失败立即退出并打印表名与日期。重跑安全：面板读各表的 *_latest 视图，每天只取最近一次计算的结果。
# - 读 events 的表（明细只留 90 天）从 events 现存最早的分区开始，更早的天跳过；
#   活跃与留存读 400 天的日活表，不跳过。--events-floor 指定这个下限（不连库，测试用）。
# - 以 patra 账号经 http://127.0.0.1:8123 执行，密码从 ~/.patra/secrets/clickhouse.env 读，不打印。
# ============================================================================
set -euo pipefail

usage() {
  echo "用法：backfill-ads.sh [--dry-run] --from YYYY-MM-DD --to YYYY-MM-DD [--events-floor YYYY-MM-DD]" >&2
  exit 1
}

FROM=""
TO=""
EVENTS_FLOOR=""
DRY_RUN=0
while [ $# -gt 0 ]; do
  case "${1}" in
    --from) FROM="${2:-}"; shift 2 ;;
    --to) TO="${2:-}"; shift 2 ;;
    --events-floor) EVENTS_FLOOR="${2:-}"; shift 2 ;;
    --dry-run) DRY_RUN=1; shift ;;
    *) usage ;;
  esac
done
[ -n "${FROM}" ] && [ -n "${TO}" ] || usage

# 表的顺序就是执行顺序；读 events 的表标 1
TABLES="device_user_links:1 ads_daily_active:0 ads_person_first_day:0 ads_cohort_retention:0 ads_daily_sessions:1 ads_daily_search:1
ads_daily_zero_result_terms:1 ads_daily_counter_items:1 ads_daily_quality:1 ads_daily_duplicates:1"

next_day() { # BSD date（macOS）优先，GNU date 兜底
  date -j -v+1d -f %Y-%m-%d "${1}" +%Y-%m-%d 2>/dev/null || date -d "${1} + 1 day" +%Y-%m-%d
}

query() { # query <SQL>：以 patra 账号执行，失败时把 ClickHouse 的报错打出来
  curl -sS --noproxy '*' --fail-with-body "http://127.0.0.1:8123/?database=patra_analytics" \
    -H "X-ClickHouse-User: patra" -H "X-ClickHouse-Key: ${CLICKHOUSE_PATRA_PASSWORD}" \
    --data-binary "${1}"
}

if [ "${DRY_RUN}" -eq 0 ]; then
  set -a
  # shellcheck disable=SC1091
  . "${HOME}/.patra/secrets/clickhouse.env"
  set +a
  if [ -z "${EVENTS_FLOOR}" ]; then
    FLOOR_ID="$(query "SELECT min(partition_id) FROM system.parts WHERE database = 'patra_analytics' AND table = 'events' AND active")"
    if ! [[ "${FLOOR_ID}" =~ ^[0-9]{8}$ ]]; then
      echo "events 没有活跃分区（或查询失败），不知道明细从哪天开始：${FLOOR_ID}" >&2
      exit 1
    fi
    EVENTS_FLOOR="${FLOOR_ID:0:4}-${FLOOR_ID:4:2}-${FLOOR_ID:6:2}"
  fi
fi
[ -n "${EVENTS_FLOOR}" ] || usage

for entry in ${TABLES}; do
  table="${entry%%:*}"
  reads_events="${entry##*:}"
  day="${FROM}"
  if [ "${reads_events}" = "1" ] && [[ "${day}" < "${EVENTS_FLOOR}" ]]; then
    day="${EVENTS_FLOOR}"
  fi
  while ! [[ "${day}" > "${TO}" ]]; do
    sql="INSERT INTO ${table} SELECT * FROM ${table}_calc(from = '${day}', to = '${day}')"
    if [ "${DRY_RUN}" -eq 1 ]; then
      echo "${sql}"
    else
      started="$(date +%s)"
      # --fail-with-body 把 ClickHouse 的报错正文写在 stdout，接下来失败时转到 stderr
      if ! out="$(query "${sql}")"; then
        echo "${out}" >&2
        echo "失败：${table} ${day}" >&2
        exit 1
      fi
      echo "${table} ${day} $(( $(date +%s) - started )) 秒"
    fi
    day="$(next_day "${day}")"
  done
done
