#!/bin/bash
# Nacos 的 PG 建表。库 patra_nacos 由 02-create-databases.sql 创建。
#
# 全新部署：postgres 镜像在数据目录为空时自动执行本脚本。
# 已有数据的 PG（如 mini）：先建库，再手动执行（见 patra-infra/docker/README.md「Nacos」）：
#   docker exec patra-postgres psql -U postgres -c 'CREATE DATABASE patra_nacos;'
#   docker exec patra-postgres bash /docker-entrypoint-initdb.d/03-nacos-schema.sh
#
# 官方 schema 每张表先 DROP 再建，在有数据的库上重跑会清空数据，所以先判断库的状态：
#   一张 Nacos 表都没有 → 建表；全部都在 → 跳过；只有一部分 → 报错，不动库。
# 建表整体放在一个事务里：去掉官方脚本里空的 BEGIN;/COMMIT; 行，否则其中的 COMMIT 会提前提交外层事务。
# 中途失败会整体回滚，库回到空状态，修好原因后直接重跑。
#
# 提交为可执行文件。没有可执行位时镜像会用 source 执行它，所以全部逻辑放在函数里，
# 报错用 return 让调用方停下，不写裸 exit（exit 会直接终止镜像的整个初始化）。
# NACOS_SCHEMA_FILE 可覆盖 schema 路径，只用于测试中途失败。

nacos_schema_main() {
  local dir file db=patra_nacos user="${POSTGRES_USER:-postgres}" tables total in_list present
  dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  file="${NACOS_SCHEMA_FILE:-$dir/nacos/pg-schema-3.2.4.sql}"
  tables="$(sed -n -E 's/^CREATE TABLE "([a-z_]+)".*/\1/p' "$file")"
  total="$(printf '%s\n' "$tables" | grep -c .)"
  # shellcheck disable=SC2086 # 有意不加引号：按表名分词，逐个套上引号
  in_list="$(printf "'%s'," $tables)"
  in_list="${in_list%,}"

  if ! present="$(psql -v ON_ERROR_STOP=1 -U "$user" -d "$db" -Atc \
    "SELECT count(*) FROM pg_tables WHERE schemaname = 'public' AND tablename IN ($in_list)")"; then
    echo "[nacos-schema] 错误：连不上库 $db。全新部署由 02-create-databases.sql 创建；" \
      "已有数据的 PG 先执行 CREATE DATABASE $db;" >&2
    return 1
  fi

  if [ "$present" -eq 0 ]; then
    echo "[nacos-schema] $db 是空库，开始建表（$total 张）"
    if ! { echo 'SET client_min_messages = warning;'; grep -v -E '^(BEGIN|COMMIT);[[:space:]]*$' "$file"; } \
      | psql -q -v ON_ERROR_STOP=1 --single-transaction -U "$user" -d "$db"; then
      echo "[nacos-schema] 错误：建表失败，事务已回滚，$db 仍是空库。修好原因后重跑本脚本" >&2
      return 1
    fi
    echo "[nacos-schema] 建表完成"
  elif [ "$present" -eq "$total" ]; then
    echo "[nacos-schema] $db 已有全部 $total 张 Nacos 表，跳过"
  else
    echo "[nacos-schema] 错误：$db 只有 $present/$total 张 Nacos 表，状态不完整，未做任何改动" >&2
    echo "[nacos-schema] 恢复：确认库里没有要保留的数据后，执行 DROP DATABASE $db; CREATE DATABASE $db;" \
      "再执行本脚本" >&2
    return 1
  fi
}

# source 执行时 return 生效；直接执行时 return 失败，才走到 exit
# shellcheck disable=SC2317
nacos_schema_main || { return 1 2>/dev/null || exit 1; }
