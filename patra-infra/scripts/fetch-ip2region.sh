#!/usr/bin/env bash
# 下载 identity 查 IP 属地用的 ip2region 数据文件（PAP-99）到 ~/.patra/data/ip2region/。
# 版本与 SHA-256 取同目录的 ip2region.properties；校验通过才替换旧文件。幂等：已是该版本则跳过。
# mini 与本机各跑一次；更新数据 = 改 properties 后 git pull 再跑，然后重启 identity。
# 用法：bash patra-infra/scripts/fetch-ip2region.sh [目标目录]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROPERTIES="${SCRIPT_DIR}/ip2region.properties"
TARGET_DIR="${1:-${HOME}/.patra/data/ip2region}"

property() {
  grep -E "^${1}=" "${PROPERTIES}" | head -1 | cut -d= -f2-
}

TAG="$(property tag)"
mkdir -p "${TARGET_DIR}"

for VERSION in v4 v6; do
  FILE="ip2region_${VERSION}.xdb"
  EXPECTED="$(property "${VERSION}.sha256")"
  TARGET="${TARGET_DIR}/${FILE}"
  if [ -f "${TARGET}" ] && [ "$(shasum -a 256 "${TARGET}" | cut -d' ' -f1)" = "${EXPECTED}" ]; then
    echo "${FILE} 已是 ${TAG}，跳过"
    continue
  fi
  PART="${TARGET}.part"
  URL="https://raw.githubusercontent.com/lionsoul2014/ip2region/${TAG}/data/${FILE}"
  echo "下载 ${URL}"
  curl -fsSL --retry 3 --retry-delay 5 --max-time 900 -o "${PART}" "${URL}"
  ACTUAL="$(shasum -a 256 "${PART}" | cut -d' ' -f1)"
  if [ "${ACTUAL}" != "${EXPECTED}" ]; then
    rm -f "${PART}"
    echo "${FILE} 的 SHA-256 不符：期望 ${EXPECTED}，实际 ${ACTUAL}" >&2
    exit 1
  fi
  mv -f "${PART}" "${TARGET}"
  echo "${FILE} 已更新到 ${TAG}"
done
