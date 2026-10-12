# Nacos 加固与升级 实施计划（PAP-71）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 mini 上的 Nacos 具备存放配置和密钥的条件：
- 升到 3.2.4，存储改到 PG；
- 轮换已公开的密钥；
- 按用途分出账号和权限；
- 6 个服务改用应用账号。

最后按设计的顺序切换上线。

**Architecture:**
- **服务端**：compose 指定 3.2.4 镜像和 PG 存储，鉴权密钥改由仓库外的 `nacos-server.env` 注入。建表由 PG init 目录里的 `03-nacos-schema.sh` 完成，先判断库的状态，再在单个事务里建表。
- **账号**：`nacos-init.sh` 幂等地同步管理员、namespace、账号、角色和权限，`nacos-verify.sh` 把验收固化成 11 项检查，两者共用 `nacos-lib.sh`。
- **应用**：容器经 `env_file` 读 `nacos.env`，本机进程经 dev profile 导入同一个文件。

**Tech Stack:** Docker Compose、Nacos 3.2.4（官方 PG 数据源插件）、PostgreSQL 17、Bash 3.2（mini 的系统 bash）、curl、jq、Spring Boot 4 config data（`spring.config.import`）。

**Spec:** `docs/patra/specs/2026-10-11-nacos-hardening-design.md`

## Global Constraints

- **密钥不进仓库。** 仓库是公开的。token 签名密钥、identity、三个账号的密码只放在 `~/.patra/secrets/`（目录 0700，文件 0600）。
  - 不打印到终端，不写进文档、提交信息、Linear。
  - 检查密钥时只输出计数、退出码、长度或文件属性。
  - 测试用的密钥只放在 `$SP` 下，同样不打印。
- **账号与权限**（spec 第 7 节）：

  | 账号 | 角色 | 权限 resource / action |
  |---|---|---|
  | `nacos` | `ROLE_ADMIN` | 全部 |
  | `patra-app` | `PATRA_APP` | `public:*:naming/*` / `rw`；`dev:*:config/*` / `r` |
  | `patra-push` | `PATRA_PUSH` | `dev:DEFAULT_GROUP:config/*` / `rw`；`dev:*:config/*` / `r` |

  namespace ID 和名称都是 `dev`；密钥分组 `PATRA_SECRETS`。
- **密钥文件**（spec 第 8 节）：

  | 文件 | 变量 | 放在哪 |
  |---|---|---|
  | `nacos-server.env` | `NACOS_AUTH_TOKEN`、`NACOS_AUTH_IDENTITY_KEY`、`NACOS_AUTH_IDENTITY_VALUE` | 只放 mini |
  | `nacos-admin.env` | `NACOS_ADMIN_PASSWORD` | 只放 mini，另存密码管理器 |
  | `nacos.env` | `NACOS_USERNAME=patra-app`、`NACOS_PASSWORD` | 两台机器 |
  | `nacos-push.env` | `NACOS_PUSH_USERNAME=patra-push`、`NACOS_PUSH_PASSWORD` | 两台机器 |

- **脚本约定**（spec 11.1）：
  - 令牌只放 `Authorization: Bearer` 请求头。
  - 密码、配置正文只走 POST / PUT 的表单请求体。
  - GET / DELETE 的参数进 URL，只能放不敏感的值。
  - 登录看 HTTP 200 且 `accessToken` 非空；其余接口看响应体 `code` 为 0。
  - 拒绝就是 HTTP 403。
  - 不打印密码、令牌和响应体。
  - 兼容 bash 3.2：不用关联数组、`mapfile`，不开 `set -u`（空数组展开会报错）。
- **mini 上的操作。** 改变 mini 状态的每一步，执行前先向用户确认；只读检查不用确认。推送 main 要等用户在当轮明确开口。
- **提交。**
  - 提交信息格式是 `type(scope): 中文开头的 subject (PAP-71)`。subject 不以类名、Issue key 或英文开头（commitlint 拒绝大写开头）。
  - body 每行不超过 100 个字符，最后一行是 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。
  - 只暂存本任务列出的路径，提交前看一眼 `git diff --cached --stat`。
  - 命令文本里不要出现推送命令的字面量，包括 heredoc 正文：会被 ship-gate 钩子整条拒绝。
  - 本计划不改 `*.gradle.kts`，不用提醒「加载 Gradle 更改」。
- **命令环境。**
  - 工作目录是主检出 `/Users/linqibin/Projects/Products/patra`，分支 `feat/v0.9-config-center-api`，不建 worktree。命令都在这里执行，不要 `cd`。
  - 主检出由 IDEA 打开，切分支、改文件 IDEA 都看得到。本地编译报 `bad class file`、`NoSuchFileException` 时，先怀疑 IDEA 在并发写 `build/classes`，不要先改代码。
  - `$SP` 指 `/private/tmp/claude-501/-Users-linqibin-Projects-Products-patra/8ea8a2e3-a097-4197-8e12-990535fe9923/scratchpad/pap71`。Bash 工具不保留 shell 状态，用到它的每条命令开头都写 `SP=<上面的路径>;`。换了会话，就换成新会话的 scratchpad。
  - macOS 没有 `timeout`；本机 `grep` 是 ugrep；mini 是 bash 3.2、BSD 工具、`/usr/bin/jq`。
  - 本机临时容器的端口：Nacos 映射到 28848 / 28080，PG 不映射。不碰本机或 mini 上任何 `patra-*` 容器。
  - 拉 `nacos/nacos-server:v3.0.2` 走 OrbStack 的系统代理。拉不动就重试，不要改 OrbStack 设置。

## Review Focus

| # | 输入或故障 | 期望 | 由哪一步钉住 |
|---|---|---|---|
| 1 | 管理员初始化接口成功时，响应体里有明文密码 | 两个脚本的输出里一个密钥值都没有 | Task 4 第 3 步 |
| 2 | 改密码走 `PUT` 的表单请求体，服务端如果不解析，重置就会失败 | 改了文件再跑 `nacos-init.sh`，新密码能登录、旧密码不能 | Task 4 第 6 步 |
| 3 | 推送账号权限被放宽时，`nacos-verify.sh` 第 10 项真的会往 `PATRA_SECRETS` 写探针 | 只写探针名、不碰 `patra-secrets.yaml`，结束后不留探针 | Task 4 第 5 步 |
| 4 | `03-nacos-schema.sh` 丢了可执行位，被镜像用 source 执行 | 跳过时不中断调用方；报错时让调用方停下 | Task 1 第 1 步的 `t03.sh` |
| 5 | 缺凭据文件，也没设同名环境变量 | 发出任何请求之前退出，说清缺哪个变量、哪个文件 | Task 4 第 7 步 |

## 写计划时已核实的点（2026-10-12，对照 3.0.2 / 3.2.4 源码）

| 点 | 结论 |
|---|---|
| spec 第 12 节第 3 条：删除权限的参数 | `DELETE /v3/auth/permission` 的参数就是 `role`、`resource`、`action` |
| spec 第 12 节第 2 条：已存在的用户再建一次 | 不再依赖它的返回：先 `GET /v3/auth/user/list` 查用户，存在就 `PUT /v3/auth/user`（参数 `username`、`newPassword`），不存在才创建 |
| 管理员初始化接口 | 两个版本一样：已有管理员时 `code` 409，否则建管理员，**响应体回显明文密码** |
| 状态接口 `GET /nacos/v3/admin/core/state` | 不需要鉴权；字段在 `.data.version`、`.data.datasource_platform`、`.data.auth_enabled` |
| namespace | 存在性用 `GET /v3/admin/core/namespace/check?namespaceId=dev`，`.data` 是个数；列表 `GET /v3/admin/core/namespace/list`，`.data[].namespace` |
| 角色、用户、权限的列表接口 | 都是 GET，`pageNo`、`pageSize` 必填，带 `search=accurate` 做精确匹配；结果在 `.data.pageItems` |
| 实例列表 `GET /v3/admin/ns/instance/list` | `.data` 是实例数组 |
| 6 个服务名 | `patra-registry`、`patra-object-storage`、`patra-catalog`、`patra-ingest`、`patra-identity`、`patra-gateway` |
| 容器的 profile | `.env.common` 设 `SPRING_PROFILES_ACTIVE=container`，dev profile 的导入不影响容器 |
| compose 插值 | `DB_PASSWORD_0=${POSTGRES_PASSWORD:-123456}`：`patra-infra/docker/.env` 没有这个变量，`.env.dev` 不会被 compose 自动读，所以取默认值，和 postgres 服务一致 |
| CD 路由 | `postgres/*`、`*.example`、`scripts/*`、apps 以外的 compose、`.env`、`.env.dev` 不触发构建；apps compose、`.env.common` 触发全量 |

## 与 spec 的出入

| 出入 | 理由 |
|---|---|
| `nacos-server.env`、`nacos-admin.env` 直接在 mini 上生成，不在本机生成再 scp | 这两个文件只放 mini（spec 第 8 节），本机生成会多留一份 |
| 新增 `patra-infra/scripts/nacos-lib.sh`，两个脚本共用 | 环境变量与文件的读取、请求封装、登录、就绪等待两个脚本都要，抄两份会走样 |
| 验收第 11 项按服务逐个查实例列表，不解析服务列表 | 实例列表的返回结构确定（数组），服务列表的分页结构在不同参数下不同 |
| 切换第 2 步记下的旧令牌存进 `$SP/old-admin-token`（0600），不存终端变量 | Bash 工具不保留 shell 状态 |
| 切换前加一步本机冒烟：本机用 dev profile 起 registry，以 `patra-app` 注册到旧服务端 | 推送 main 之前，确认账号和导入在真实环境里成立 |
| 另改 `patra-gateway-boot/README.md` 的配置表、`patra-object-storage/README.md` 的本地启动说明 | 两处写着默认账号 `nacos` / `nacos`，改完后就是错的 |

## 文件结构

| 文件 | 动作 | 任务 |
|---|---|---|
| `docs/patra/release-specs/v0.9-config-center.md`、`docs/patra/specs/2026-10-11-nacos-hardening-design.md`、本计划 | 提交 | 0 |
| `patra-infra/docker/postgres/init-scripts/02-create-databases.sql` | 改：加 `patra_nacos` | 1 |
| `patra-infra/docker/postgres/init-scripts/nacos/pg-schema-3.2.4.sql` | 新建：从镜像原样复制，加文件头 | 1 |
| `patra-infra/docker/postgres/init-scripts/03-nacos-schema.sh` | 新建，755 | 1 |
| `patra-infra/docker/docker-compose.core.yaml` | 改：nacos 服务 | 2 |
| `patra-infra/docker/secrets/nacos-server.env.example`、`nacos-admin.env.example`、`nacos.env.example`、`nacos-push.env.example` | 新建 | 2 |
| `patra-infra/docker/.env`、`.env.example` | 改：删 Nacos 鉴权一节 | 2 |
| `patra-infra/docker/.env.dev` | 改：第 10 行注释 | 2 |
| `patra-infra/scripts/nacos-lib.sh` | 新建 | 3 |
| `patra-infra/scripts/nacos-verify.sh` | 新建，755 | 3 |
| `patra-infra/scripts/nacos-init.sh` | 新建，755 | 4 |
| `patra-infra/docker/docker-compose.apps.yaml` | 改：6 个服务的 `env_file`、头部注释 | 6 |
| `patra-infra/docker/.env.common` | 改：删账号两行，改注释 | 6 |
| 6 个服务的 `application-dev.yml` | 改：导入 `nacos.env` | 6 |
| `patra-infra/CLAUDE.md`、`patra-infra/docker/README.md`、根 `CLAUDE.md` | 改 | 7 |
| `patra-api/patra-gateway-boot/README.md`、`patra-api/patra-object-storage/README.md` | 改 | 7 |
| spec 第 5、12 节 | 改：写回实测 | 5、上线 8 |

不进仓库、放在 `$SP` 的测试脚手架：`harness.sh`、`t03.sh`、`admin.sh`、`t-import.sh`、测试密钥目录 `home/.patra/secrets/`。

## 执行顺序

1. **第一部分：Task 0～8。** 在 `feat/v0.9-config-center-api` 上完成，可以连续执行。
2. **最终评审。** 第一部分完成后，对整个分支做一次最终评审，修掉评审意见。
3. **用户本地验收。** 把改动和测试结果给用户看，用户确认后才进入第二部分。
4. **第二部分：上线 1～8。** 这部分操作 mini，不连续执行：每一步改动 mini 之前都先向用户确认；推送 main 要等用户开口。

---

# 第一部分：分支上的改动

### Task 0: 分支对齐 main，提交设计文档

**Files:**
- Commit: `docs/patra/release-specs/v0.9-config-center.md`、`docs/patra/specs/2026-10-11-nacos-hardening-design.md`、`docs/patra/plans/2026-10-12-nacos-hardening.md`

**Interfaces:**
- Consumes: 无。
- Produces: 基于最新 `origin/main` 的分支，后续提交都在它之上。

- [ ] **Step 1: 对齐 origin/main**

分支在 2026-10-12 从当时最新的 main（`1aad64da5`）切出，还没有自己的提交。开工前再对齐一次：

```bash
git fetch origin main && git merge --ff-only origin/main
```

Expected：`git rev-list --left-right --count origin/main...HEAD` 输出 `0	0`。`git status --short` 只有两个未跟踪的文档和本计划。

- [ ] **Step 2: 提交三份文档**

```bash
git add docs/patra/release-specs/v0.9-config-center.md docs/patra/specs/2026-10-11-nacos-hardening-design.md \
  docs/patra/plans/2026-10-12-nacos-hardening.md
git diff --cached --stat
git commit -F - <<'EOF'
docs(config-center): 新增 v0.9 release spec 与 Nacos 加固的工程设计和实施计划 (PAP-71)

release spec 定下 v0.9 的范围和 8 个 Issue；工程设计和计划覆盖 PAP-71：
Nacos 升到 3.2.4、存储改到 PG、轮换已公开的鉴权密钥、按用途分出账号和权限。

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
```

Expected：提交成功，`git status --short` 为空。

---

### Task 1: PG 建库，幂等且原子的建表脚本

**Files:**
- Modify: `patra-infra/docker/postgres/init-scripts/02-create-databases.sql`
- Create: `patra-infra/docker/postgres/init-scripts/nacos/pg-schema-3.2.4.sql`
- Create: `patra-infra/docker/postgres/init-scripts/03-nacos-schema.sh`（git 记 100755）
- Test（不进仓库）: `$SP/harness.sh`、`$SP/t03.sh`

**Interfaces:**
- Consumes: 无。
- Produces:
  - 库 `patra_nacos`，其中 13 张 Nacos 表。
  - `03-nacos-schema.sh`：环境变量 `NACOS_SCHEMA_FILE` 覆盖 schema 路径，默认是脚本同目录的 `nacos/pg-schema-3.2.4.sql`；成功返回 0，报错返回 1。输出前缀 `[nacos-schema]`。
  - `$SP/harness.sh` 的子命令：
    - `pg`：起临时 PG，容器 `pap71-pg`，网络别名 `postgres`；
    - `nacos`：按 core compose 原样起 Nacos，容器 `pap71-nacos`；
    - `nacos-old`：3.0.2 加内嵌 Derby，模拟 mini 现状；
    - `down`：删掉临时容器和网络。
  - 测试密钥目录 `$SP/home/.patra/secrets/`。

- [ ] **Step 1: 写测试脚手架和 03 的测试**

写 `$SP/harness.sh`：

```bash
#!/bin/bash
# PAP-71 本机临时环境：临时 PG + 临时 Nacos，参数取自仓库里的 core compose（docker compose config）。
# 用法：
#   bash harness.sh pg          起临时 PG，挂仓库的 init-scripts（数据目录为空，init 脚本会自动执行）
#   bash harness.sh nacos       按 core compose 原样起 Nacos（镜像、环境变量、env_file 都取自 compose）
#   bash harness.sh nacos-old   3.0.2 + 内嵌 Derby，模拟 mini 现状
#   bash harness.sh down        删掉临时容器和网络
# 测试密钥在 $SP/home/.patra/secrets/，compose 的 ${HOME} 指向 $SP/home。
set -eo pipefail
W="$(git rev-parse --show-toplevel)"
SP="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
S="$SP/home/.patra/secrets"
NET=pap71-net
REAL_HOME="$HOME"

gen_secrets() {
  [ -f "$S/nacos-server.env" ] && return 0
  (
    umask 077
    mkdir -p "$S"
    printf 'NACOS_AUTH_TOKEN=%s\nNACOS_AUTH_IDENTITY_KEY=%s\nNACOS_AUTH_IDENTITY_VALUE=%s\n' \
      "$(openssl rand -base64 32)" "$(openssl rand -hex 16)" "$(openssl rand -hex 32)" > "$S/nacos-server.env"
    printf 'NACOS_ADMIN_PASSWORD=%s\n' "$(openssl rand -hex 24)" > "$S/nacos-admin.env"
    printf 'NACOS_USERNAME=patra-app\nNACOS_PASSWORD=%s\n' "$(openssl rand -hex 24)" > "$S/nacos.env"
    printf 'NACOS_PUSH_USERNAME=patra-push\nNACOS_PUSH_PASSWORD=%s\n' "$(openssl rand -hex 24)" > "$S/nacos-push.env"
  )
}

compose_json() {
  DOCKER_CONFIG="$REAL_HOME/.docker" HOME="$SP/home" \
    docker compose -f "$W/patra-infra/docker/docker-compose.core.yaml" config --format json
}

ensure_net() { docker network inspect "$NET" >/dev/null 2>&1 || docker network create "$NET" >/dev/null; }

up_pg() {
  local json pw
  json="$(compose_json)"
  pw="$(jq -r '.services.postgres.environment.POSTGRES_PASSWORD' <<<"$json")"
  ensure_net
  docker rm -f -v pap71-pg >/dev/null 2>&1 || true
  docker run -d --name pap71-pg --network "$NET" --network-alias postgres -e POSTGRES_PASSWORD="$pw" \
    -v "$W/patra-infra/docker/postgres/init-scripts:/docker-entrypoint-initdb.d:ro" \
    "$(jq -r '.services.postgres.image' <<<"$json")" >/dev/null
  # initdb 期间的临时服务也会打一次 ready，要等第二次
  local i
  for i in $(seq 1 90); do
    [ "$(docker logs pap71-pg 2>&1 | grep -c 'database system is ready to accept connections')" -ge 2 ] && break
    if [ -z "$(docker ps -q -f name=^pap71-pg$ -f status=running)" ]; then
      docker logs pap71-pg 2>&1 | tail -20
      echo "pap71-pg 退出了（init 脚本失败？）" >&2
      exit 1
    fi
    sleep 1
  done
  echo "pap71-pg 就绪"
  docker logs pap71-pg 2>&1 | grep -E '\[nacos-schema\]|ERROR' || true
}

up_nacos() {
  local mode="$1" json image filter envf="$SP/nacos-run.env"
  gen_secrets
  json="$(compose_json)"
  image="$(jq -r '.services.nacos.image' <<<"$json")"
  filter='.services.nacos.environment | to_entries[]'
  if [ "$mode" = old ]; then
    image=nacos/nacos-server:v3.0.2
    filter="$filter"' | select(.key | test("^(SPRING_DATASOURCE_PLATFORM|DB_)") | not)'
  fi
  (umask 077; jq -r "$filter"' | "\(.key)=\(.value)"' <<<"$json" > "$envf")
  ensure_net
  docker rm -f pap71-nacos >/dev/null 2>&1 || true
  docker run -d --name pap71-nacos --network "$NET" \
    --hostname "$(jq -r '.services.nacos.hostname' <<<"$json")" \
    --env-file "$envf" -p 28848:8848 -p 28080:8080 "$image" >/dev/null
  local i
  for i in $(seq 1 90); do
    curl -fsS --noproxy '*' -m 3 -o /dev/null http://127.0.0.1:28080/v3/console/health/readiness 2>/dev/null \
      && { echo "pap71-nacos 就绪（$image）"; return 0; }
    sleep 2
  done
  # 镜像启动脚本开着 set -x，会打出鉴权变量，过滤掉再看
  docker logs pap71-nacos 2>&1 | grep -v NACOS_AUTH | tail -30
  echo "pap71-nacos 180 秒内没有就绪" >&2
  exit 1
}

case "${1:-}" in
  pg) up_pg ;;
  nacos) up_nacos new ;;
  nacos-old) up_nacos old ;;
  down)
    docker rm -f -v pap71-nacos pap71-pg >/dev/null 2>&1 || true
    docker network rm "$NET" >/dev/null 2>&1 || true
    ;;
  *) echo "用法: $0 {pg|nacos|nacos-old|down}" >&2; exit 2 ;;
esac
```

写 `$SP/t03.sh`：

```bash
#!/bin/bash
# 03-nacos-schema.sh 的四种库状态。前提：刚执行过 harness.sh pg（数据目录为空，init 脚本已自动执行），
# 而且 pap71-nacos 没在跑（第 4 组要删库重建，有连接时 DROP DATABASE 会失败）。
W="$(git rev-parse --show-toplevel)"
PASS=0; FAIL=0
q() { docker exec -i pap71-pg psql -U postgres -d patra_nacos -Atc "$1"; }
run03() { docker exec "$@" pap71-pg bash /docker-entrypoint-initdb.d/03-nacos-schema.sh; }
src03() { docker exec pap71-pg bash -c 'set -Eeo pipefail; . /docker-entrypoint-initdb.d/03-nacos-schema.sh; echo AFTER'; }
check() {
  if [ "$2" = "$3" ]; then PASS=$((PASS + 1)); echo "  ✓ $1"
  else FAIL=$((FAIL + 1)); echo "  ✗ $1：期望 $3，实得 $2"; fi
}
N="SELECT count(*) FROM pg_tables WHERE schemaname = 'public'"

echo "== 1 全新部署：init 目录自动执行 02 和 03"
check "patra_nacos 有 13 张表" "$(q "$N")" 13
check "03 在 git 里记为可执行" \
  "$(git -C "$W" ls-files -s patra-infra/docker/postgres/init-scripts/03-nacos-schema.sh | cut -c1-6)" 100755
check "init 日志有建表完成" "$(docker logs pap71-pg 2>&1 | grep -c '\[nacos-schema\] 建表完成')" 1

echo "== 2 已建好：重跑跳过，数据不动"
q "INSERT INTO users (username, password, enabled) VALUES ('pap71-marker', 'x', true)" >/dev/null
out="$(run03 2>&1)"; rc=$?
check "退出码 0" "$rc" 0
check "提示跳过" "$(grep -c '跳过' <<<"$out")" 1
check "标记行还在" "$(q "SELECT count(*) FROM users WHERE username = 'pap71-marker'")" 1
out="$(src03 2>&1)"
check "source 方式下跳过，不中断调用方" "$(grep -c AFTER <<<"$out")" 1

echo "== 3 部分状态：报错，不动库"
q "DROP TABLE users" >/dev/null
out="$(run03 2>&1)"; rc=$?
check "退出码非零" "$([ "$rc" -ne 0 ] && echo yes)" yes
check "打印恢复方法" "$(grep -c 'DROP DATABASE patra_nacos' <<<"$out")" 1
check "其余 12 张表不动" "$(q "$N")" 12
out="$(src03 2>&1)"; rc=$?
check "source 方式下返回非零" "$([ "$rc" -ne 0 ] && echo yes)" yes
check "source 方式下调用方停在这里" "$(grep -c AFTER <<<"$out")" 0

echo "== 4 中途失败：整体回滚，修好后重跑成功"
docker exec pap71-pg psql -q -U postgres -c 'DROP DATABASE patra_nacos' -c 'CREATE DATABASE patra_nacos'
docker exec pap71-pg bash -c "awk '/^CREATE TABLE \"users\"/{print \"SELECT * FROM pap71_no_such_table;\"}{print}' \
  /docker-entrypoint-initdb.d/nacos/pg-schema-3.2.4.sql > /tmp/broken.sql"
out="$(run03 -e NACOS_SCHEMA_FILE=/tmp/broken.sql 2>&1)"; rc=$?
check "退出码非零" "$([ "$rc" -ne 0 ] && echo yes)" yes
check "回滚后一张表都没有" "$(q "$N")" 0
out="$(run03 2>&1)"; rc=$?
check "重跑退出码 0" "$rc" 0
check "重跑后 13 张表" "$(q "$N")" 13

echo; echo "通过 $PASS / 失败 $FAIL"
[ "$FAIL" -eq 0 ]
```

- [ ] **Step 2: 跑测试，确认失败**

```bash
SP=<路径>; mkdir -p "$SP"; bash "$SP/harness.sh" down; bash "$SP/harness.sh" pg && bash "$SP/t03.sh"; echo "exit=$?"
```

Expected：`pap71-pg 就绪`；第 1 组「13 张表」失败，原因是库 `patra_nacos` 不存在；末行 `exit=1`。

- [ ] **Step 3: 建库，复制 schema**

`02-create-databases.sql` 末尾加一行：

```sql
CREATE DATABASE patra_nacos;
```

从镜像复制 schema，加文件头：

```bash
mkdir -p patra-infra/docker/postgres/init-scripts/nacos
F=patra-infra/docker/postgres/init-scripts/nacos/pg-schema-3.2.4.sql
{
  cat <<'EOF'
-- 来源：nacos/nacos-server:v3.2.4 镜像内的 /home/nacos/conf/pg-schema.sql，以下原样复制，未做修改。
-- 许可：Apache License 2.0（见下方原文件头）。
-- 由 ../03-nacos-schema.sh 执行。不要直接对已有数据的库执行本文件：每张表都会先 DROP 再建。
-- 升级 Nacos 时换成新版本的文件，见 patra-infra/docker/README.md「Nacos」。
EOF
  docker run --rm --entrypoint cat nacos/nacos-server:v3.2.4 /home/nacos/conf/pg-schema.sql
} > "$F"
diff <(tail -n +5 "$F") <(docker run --rm --entrypoint cat nacos/nacos-server:v3.2.4 /home/nacos/conf/pg-schema.sql) \
  && echo "与镜像一致"
grep -c '^CREATE TABLE "' "$F"
```

Expected：`与镜像一致`、`13`。

- [ ] **Step 4: 写 03 脚本**

`patra-infra/docker/postgres/init-scripts/03-nacos-schema.sh`：

```bash
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

nacos_schema_main || { return 1 2>/dev/null || exit 1; }
```

```bash
chmod 755 patra-infra/docker/postgres/init-scripts/03-nacos-schema.sh
git add --chmod=+x patra-infra/docker/postgres/init-scripts/03-nacos-schema.sh
shellcheck patra-infra/docker/postgres/init-scripts/03-nacos-schema.sh && echo "shellcheck 通过"
```

Expected：`shellcheck 通过`。如果 shellcheck 报 `printf "'%s'," $tables` 未加引号（SC2086），那是有意分词：在这一行上方加 `# shellcheck disable=SC2086`，并注明是按表名分词。

- [ ] **Step 5: 跑测试，确认通过**

PG 的 init 脚本只在数据目录为空时执行，所以重建容器再测：

```bash
SP=<路径>; bash "$SP/harness.sh" pg && bash "$SP/t03.sh"; echo "exit=$?"
```

Expected：
- `harness.sh` 输出里有 `[nacos-schema] patra_nacos 是空库，开始建表（13 张）` 和 `[nacos-schema] 建表完成`。
- `通过 16 / 失败 0`，`exit=0`。

- [ ] **Step 6: 提交**

```bash
git add patra-infra/docker/postgres/init-scripts/02-create-databases.sql \
  patra-infra/docker/postgres/init-scripts/nacos/pg-schema-3.2.4.sql \
  patra-infra/docker/postgres/init-scripts/03-nacos-schema.sh
git diff --cached --stat
git commit -F - <<'EOF'
feat(infra): 新增 patra_nacos 库和幂等、原子的 Nacos 建表脚本 (PAP-71)

03-nacos-schema.sh 先数库里已有几张 Nacos 表：空库建表，齐全跳过，只有一部分时报错、不动库。
建表去掉官方脚本里空的 BEGIN/COMMIT 后整体放进一个事务，中途失败会回滚到空库。
schema 从 3.2.4 镜像原样复制。

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
```

---

### Task 2: 服务端 compose 升级，鉴权密钥移出仓库

**Files:**
- Modify: `patra-infra/docker/docker-compose.core.yaml:47-73`（nacos 服务）
- Create: `patra-infra/docker/secrets/nacos-server.env.example`、`nacos-admin.env.example`、`nacos.env.example`、`nacos-push.env.example`
- Modify: `patra-infra/docker/.env:20-28`、`patra-infra/docker/.env.example:17-23`、`patra-infra/docker/.env.dev:10`

**Interfaces:**
- Consumes: Task 1 的 `harness.sh pg`，以及库 `patra_nacos`。
- Produces: `harness.sh nacos` 起出 3.2.4、PG 存储、鉴权开启的 Nacos，供 Task 3、4 使用。

- [ ] **Step 1: 跑一次，确认还是旧服务端**

```bash
SP=<路径>; bash "$SP/harness.sh" nacos && \
  curl -sS --noproxy '*' http://127.0.0.1:28848/nacos/v3/admin/core/state \
  | jq -r '.data | "\(.version) \(.datasource_platform) \(.auth_enabled)"'
```

Expected：`pap71-nacos 就绪（nacos/nacos-server:v3.0.2）`，接着一行 `3.0.2  true`（存储为空，也就是 Derby）。

- [ ] **Step 2: 改 compose 的 nacos 服务**

把 `docker-compose.core.yaml` 里从 `  nacos:` 到 `environment` 列表结束（第 47～60 行）替换为下面这段。`ports`、`volumes`、`healthcheck` 不变：

```yaml
  nacos:
    image: nacos/nacos-server:v3.2.4
    container_name: patra-nacos
    # 固定 hostname：容器重建后 hostname 一变，jraft 身份对不上，会选不出 leader
    hostname: patra-nacos
    restart: unless-stopped
    depends_on:
      postgres:
        condition: service_healthy
    env_file:
      # token 签名密钥与 identity，放在仓库外（见 README「密钥」）。缺失时 compose 拒绝执行。
      # 三项鉴权变量不要再写进下面的 environment：environment 优先于 env_file，插值出的空值会盖掉文件里的值
      - path: ${HOME}/.patra/secrets/nacos-server.env
        required: true
    environment:
      - MODE=standalone
      - PREFER_HOST_MODE=hostname
      - NACOS_AUTH_ENABLE=true
      # 存储：业务 PG 里的 patra_nacos 库（官方 PG 插件，镜像自带）。建表见 postgres/init-scripts/03-nacos-schema.sh
      - SPRING_DATASOURCE_PLATFORM=postgresql
      - DB_URL_0=jdbc:postgresql://postgres:5432/patra_nacos?currentSchema=public&ApplicationName=nacos&connectTimeout=5&socketTimeout=30&tcpKeepAlive=true&sslmode=disable
      - DB_USER_0=postgres
      - DB_PASSWORD_0=${POSTGRES_PASSWORD:-123456}
      # 不显式指定时回落到 MySQL 驱动，启动失败
      - DB_POOL_CONFIG_DRIVERCLASSNAME=org.postgresql.Driver
      - DB_POOL_CONFIG_MAXIMUMPOOLSIZE=5
      - DB_POOL_CONFIG_MINIMUMIDLE=1
      - DB_POOL_CONNECTION_TIMEOUT=5000
      - JVM_XMS=256m
      - JVM_XMX=256m
      - TZ=Asia/Shanghai
```

postgres 服务已有 `pg_isready` 健康检查，`depends_on` 的 `service_healthy` 可以直接用。

- [ ] **Step 3: 删掉仓库里的鉴权密钥**

`.env`：删掉第 20～28 行，也就是整个「Nacos 鉴权（compose 必须）」一节，连同它前面的空行。

`.env.example`：删掉第 17～23 行的「Nacos 鉴权」一节，连同它前面的空行。

`.env.dev` 第 10 行改为：

```
# Nacos Configuration（鉴权密钥在仓库外的 ~/.patra/secrets/nacos-server.env，本文件仅放可公开值）
```

- [ ] **Step 4: 写四个模板**

`patra-infra/docker/secrets/nacos-server.env.example`：

```bash
# Nacos 服务端的 token 签名密钥和 identity。真文件在仓库外：~/.patra/secrets/nacos-server.env，目录 0700、文件 0600。
# 只放在 mini：只有 core 栈的 nacos 读它（compose env_file，必需）。MacBook 不需要。
# identity 请求头匹配上就跳过全部鉴权，等同于万能钥匙：key 和 value 都随机生成，和签名密钥同等对待。
#
# 生成（在 mini 上执行，只写文件，不打印到终端）：
#   umask 077 && mkdir -p ~/.patra/secrets
#   printf 'NACOS_AUTH_TOKEN=%s\nNACOS_AUTH_IDENTITY_KEY=%s\nNACOS_AUTH_IDENTITY_VALUE=%s\n' \
#     "$(openssl rand -base64 32)" "$(openssl rand -hex 16)" "$(openssl rand -hex 32)" \
#     > ~/.patra/secrets/nacos-server.env
#
# NACOS_AUTH_TOKEN 解码后至少 32 字节。留空时 nacos 启动失败。
# 换签名密钥会让已签出的令牌全部失效，客户端会自动重新登录。
# 改完要重建容器才生效：docker compose -f docker-compose.core.yaml up -d --force-recreate nacos
NACOS_AUTH_TOKEN=
NACOS_AUTH_IDENTITY_KEY=
NACOS_AUTH_IDENTITY_VALUE=
```

`patra-infra/docker/secrets/nacos-admin.env.example`：

```bash
# Nacos 管理员 nacos 的密码。真文件在仓库外：~/.patra/secrets/nacos-admin.env，目录 0700、文件 0600。
# 只放在 mini，另在密码管理器里存一份（控制台登录、应急）。
# 谁读：scripts/nacos-init.sh（服务端还没有管理员时用它初始化，之后用它登录）、scripts/nacos-verify.sh。
#
# 生成（在 mini 上执行，只写文件，不打印到终端）：
#   umask 077 && mkdir -p ~/.patra/secrets
#   printf 'NACOS_ADMIN_PASSWORD=%s\n' "$(openssl rand -hex 24)" > ~/.patra/secrets/nacos-admin.env
#
# 只在服务端还没有管理员时决定管理员密码。之后要改，先在控制台改密码，再同步改这个文件。
NACOS_ADMIN_PASSWORD=
```

`patra-infra/docker/secrets/nacos.env.example`：

```bash
# 应用连 Nacos 的账号 patra-app：服务注册读写、配置只读。真文件在仓库外：~/.patra/secrets/nacos.env，目录 0700、文件 0600。
# MacBook 和 mini 放同一份。
# 谁读：6 个后端服务的容器（compose env_file，必需）；这 6 个服务在本机以 dev profile 起的进程
#       （application-dev.yml 的 spring.config.import）；scripts/nacos-init.sh 按它建账号，nacos-verify.sh 用它验权限。
#
# 生成（在 MacBook 上执行，只写文件，不打印到终端）：
#   umask 077 && mkdir -p ~/.patra/secrets
#   printf 'NACOS_USERNAME=patra-app\nNACOS_PASSWORD=%s\n' "$(openssl rand -hex 24)" > ~/.patra/secrets/nacos.env
# 复制到 mini：scp -p ~/.patra/secrets/nacos.env linqibin@linqibins-mac-mini:.patra/secrets/
#
# 换密码：两台机器都改文件 → mini 上跑 nacos-init.sh（账号密码会被重置成文件里的值）
#         → 重建 6 个应用容器（见 README「改了密钥文件之后」）、重启本机进程。
# 值只用十六进制：这个文件同时按 compose 的 env_file 和 Java properties 解析。
NACOS_USERNAME=patra-app
NACOS_PASSWORD=
```

`patra-infra/docker/secrets/nacos-push.env.example`：

```bash
# 推送配置的账号 patra-push：只能写 dev 的 DEFAULT_GROUP，能读 dev 的全部配置。
# 真文件在仓库外：~/.patra/secrets/nacos-push.env，目录 0700、文件 0600。MacBook 和 mini 放同一份。
# 谁读：scripts/nacos-init.sh 按它建账号，nacos-verify.sh 用它验权限；PAP-75 的推送脚本。
#
# 生成（在 MacBook 上执行，只写文件，不打印到终端）：
#   umask 077 && mkdir -p ~/.patra/secrets
#   printf 'NACOS_PUSH_USERNAME=patra-push\nNACOS_PUSH_PASSWORD=%s\n' "$(openssl rand -hex 24)" \
#     > ~/.patra/secrets/nacos-push.env
# 复制到 mini：scp -p ~/.patra/secrets/nacos-push.env linqibin@linqibins-mac-mini:.patra/secrets/
#
# 换密码：两台机器都改文件 → mini 上跑 nacos-init.sh。
NACOS_PUSH_USERNAME=patra-push
NACOS_PUSH_PASSWORD=
```

- [ ] **Step 5: 跑测试，确认通过**

```bash
SP=<路径>
bash "$SP/harness.sh" pg && bash "$SP/harness.sh" nacos && \
  curl -sS --noproxy '*' http://127.0.0.1:28848/nacos/v3/admin/core/state \
  | jq -r '.data | "\(.version) \(.datasource_platform) \(.auth_enabled)"'
DOCKER_CONFIG="$HOME/.docker" HOME="$SP/home" docker compose -f patra-infra/docker/docker-compose.core.yaml \
  config --format json | jq -r '.services.nacos.environment | [.NACOS_AUTH_TOKEN, .NACOS_AUTH_IDENTITY_KEY,
  .NACOS_AUTH_IDENTITY_VALUE] | map(length > 0) | @tsv'
mkdir -p "$SP/empty-home"
DOCKER_CONFIG="$HOME/.docker" HOME="$SP/empty-home" \
  docker compose -f patra-infra/docker/docker-compose.core.yaml config --quiet 2>&1 | grep -o 'nacos-server.env not found'
git check-ignore -q patra-infra/docker/secrets/nacos.env && echo "真文件会被忽略"
git check-ignore -q patra-infra/docker/secrets/nacos.env.example || echo "模板不被忽略"
git grep -nE 'NACOS_AUTH_(TOKEN|IDENTITY_KEY|IDENTITY_VALUE)=[A-Za-z0-9+/=]{5,}'; echo "grep exit=$?"
```

Expected：
- `pap71-pg 就绪`、`pap71-nacos 就绪（nacos/nacos-server:v3.2.4）`，状态一行 `3.2.4 postgresql true`。
- `true	true	true`：三项鉴权变量来自 `env_file`，没有被空值盖掉。
- `nacos-server.env not found`：缺文件时 compose 拒绝执行。
- `真文件会被忽略`、`模板不被忽略`。
- `grep exit=1`：仓库里没有任何非空的鉴权值。

- [ ] **Step 6: 提交**

```bash
git add patra-infra/docker/docker-compose.core.yaml patra-infra/docker/.env patra-infra/docker/.env.example \
  patra-infra/docker/.env.dev patra-infra/docker/secrets/nacos-server.env.example \
  patra-infra/docker/secrets/nacos-admin.env.example patra-infra/docker/secrets/nacos.env.example \
  patra-infra/docker/secrets/nacos-push.env.example
git diff --cached --stat
git commit -F - <<'EOF'
feat(infra): Nacos 升到 3.2.4 并改用 PG 存储，鉴权密钥移出仓库 (PAP-71)

token 签名密钥和 identity 改由仓库外的 nacos-server.env 注入，缺文件时 compose 拒绝执行；
仓库里原来那组已公开的值随之删除，上线时轮换作废。新增四个密钥文件模板。

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
```

---

### Task 3: 共用函数与验收脚本（先写验收，对未初始化的服务端应当失败）

**Files:**
- Create: `patra-infra/scripts/nacos-lib.sh`
- Create: `patra-infra/scripts/nacos-verify.sh`（755）
- Test（不进仓库）: `$SP/test.env`、`$SP/admin.sh`

**Interfaces:**
- Consumes: `harness.sh nacos` 起的服务端，以及 `$SP/home/.patra/secrets/` 下的测试密钥。
- Produces（`nacos-lib.sh`，供 Task 4 和 PAP-75 使用）：
  - 变量 `NACOS_ADDR`（默认 `http://127.0.0.1:8848`）、`NACOS_CONSOLE_ADDR`（默认 `http://127.0.0.1:8080`）、`NACOS_SECRETS_DIR`（默认 `$HOME/.patra/secrets`）。
  - `nacos_load VAR FILE`：环境变量优先，否则读文件；失败返回 1。
  - `nacos_api METHOD PATH TOKEN [key=value ...]`：`PATH` 不含 `/nacos`；结果放进 `NACOS_HTTP`、`NACOS_BODY`；总是返回 0。
  - `nacos_code`：输出 `NACOS_BODY` 的 `code`，不是 JSON 时输出空。
  - `nacos_ok`：判断 HTTP 200 且 `code` 为 0。
  - `nacos_login USER PASSWORD`：成功时设 `NACOS_TOKEN`，返回 0。
  - `nacos_wait_ready [秒]`：等 readiness，默认 180 秒。
- Produces（`nacos-verify.sh`）：逐行打印 `PASS n` 或 `FAIL n`，最后一行 `通过 X / 失败 Y`，有 FAIL 时返回 1。参数错误或缺凭据时返回 2。

- [ ] **Step 1: 写测试辅助文件**

`$SP/test.env`（用前先赋值 `SP`）：

```bash
export NACOS_ADDR=http://127.0.0.1:28848
export NACOS_CONSOLE_ADDR=http://127.0.0.1:28080
export NACOS_SECRETS_DIR="$SP/home/.patra/secrets"
```

`$SP/admin.sh`：

```bash
#!/bin/bash
# 测试用：以管理员身份调一次 Nacos 接口。用法：bash admin.sh METHOD PATH [key=value ...]
# 打印「HTTP 状态码 code」；设了 PRINT_JQ 时再按它打印响应体（只用于不含密钥的查询）。
W="$(git rev-parse --show-toplevel)"
. "$W/patra-infra/scripts/nacos-lib.sh"
nacos_load NACOS_ADMIN_PASSWORD nacos-admin.env || exit 1
nacos_login nacos "$NACOS_ADMIN_PASSWORD" || { echo "管理员登录失败（HTTP $NACOS_HTTP）"; exit 1; }
m="$1"; p="$2"; shift 2
nacos_api "$m" "$p" "$NACOS_TOKEN" "$@"
echo "HTTP $NACOS_HTTP code $(nacos_code)"
[ -n "$PRINT_JQ" ] && jq -r "$PRINT_JQ" <<<"$NACOS_BODY"
exit 0
```

- [ ] **Step 2: 写 `nacos-lib.sh`**

```bash
#!/bin/bash
# nacos-init.sh、nacos-verify.sh 共用的函数。只供 source，不单独执行。
#
# 输入（环境变量，都有默认值）：
#   NACOS_ADDR          Nacos HTTP 地址，默认 http://127.0.0.1:8848（不用 localhost：容器和本机都可能先解析到 ::1）
#   NACOS_CONSOLE_ADDR  控制台地址（readiness 在这里），默认 http://127.0.0.1:8080
#   NACOS_SECRETS_DIR   密钥文件目录，默认 ~/.patra/secrets
#
# 约定（docs/patra/specs/2026-10-11-nacos-hardening-design.md 11.1）：
#   - 令牌只放 Authorization 请求头；密码、配置正文只走 POST/PUT 的表单请求体；
#     GET/DELETE 的参数进 URL 查询串，只能放不敏感的值（镜像开着 access log，会记录查询串）。
#   - 登录接口没有 code，看 HTTP 200 且 accessToken 非空；其余接口看响应体 code 为 0。
#     很多错误的 HTTP 状态码仍是 200，只看状态码会漏判。
#   - 不打印密码、令牌和响应体。
#   - 兼容 mini 的 bash 3.2。

NACOS_ADDR="${NACOS_ADDR:-http://127.0.0.1:8848}"
NACOS_CONSOLE_ADDR="${NACOS_CONSOLE_ADDR:-http://127.0.0.1:8080}"
NACOS_SECRETS_DIR="${NACOS_SECRETS_DIR:-$HOME/.patra/secrets}"

# nacos_load VAR FILE：环境变量 VAR 已有值就用它；否则从 $NACOS_SECRETS_DIR/FILE 读 VAR=... 这一行。
# 读不到返回 1，并说明缺什么。
nacos_load() {
  local var="$1" file="$NACOS_SECRETS_DIR/$2" val
  [ -n "${!var}" ] && return 0
  if [ ! -f "$file" ]; then
    echo "缺少 $var：环境变量没有设置，$file 也不存在" >&2
    return 1
  fi
  val="$(sed -n "s/^$var=//p" "$file" | tail -n 1)"
  if [ -z "$val" ]; then
    echo "缺少 $var：$file 里没有这一行，或者值为空" >&2
    return 1
  fi
  printf -v "$var" '%s' "$val"
}

# nacos_api METHOD PATH TOKEN [key=value ...]
# PATH 不含 /nacos 前缀，例如 /v3/auth/user。TOKEN 为空时不带 Authorization。
# 结果放在 NACOS_HTTP（状态码，连不上时是 000）和 NACOS_BODY（响应体）。总是返回 0，由调用方判断。
nacos_api() {
  local method="$1" path="$2" token="$3" kv out
  shift 3
  local args=(-sS --noproxy '*' -m 15 -X "$method" -w '\n%{http_code}')
  case "$method" in GET | DELETE) args+=(-G) ;; esac
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  for kv in "$@"; do args+=(--data-urlencode "$kv"); done
  if out="$(curl "${args[@]}" "$NACOS_ADDR/nacos$path" 2>/dev/null)"; then
    NACOS_HTTP="${out##*$'\n'}"
    NACOS_BODY="${out%$'\n'*}"
  else
    NACOS_HTTP=000
    NACOS_BODY=""
  fi
  return 0
}

# nacos_code：输出 NACOS_BODY 里的 code；不是 JSON 时输出空。
nacos_code() { jq -r '.code // empty' <<<"$NACOS_BODY" 2>/dev/null; }

# nacos_ok：上一次请求 HTTP 200 且 code 为 0。
nacos_ok() { [ "$NACOS_HTTP" = 200 ] && [ "$(nacos_code)" = 0 ]; }

# nacos_login USER PASSWORD：成功时把令牌放进 NACOS_TOKEN 并返回 0。
nacos_login() {
  NACOS_TOKEN=""
  nacos_api POST /v3/auth/user/login "" "username=$1" "password=$2"
  [ "$NACOS_HTTP" = 200 ] || return 1
  NACOS_TOKEN="$(jq -r '.accessToken // empty' <<<"$NACOS_BODY" 2>/dev/null)"
  [ -n "$NACOS_TOKEN" ]
}

# nacos_wait_ready [秒数]：等控制台 readiness 返回 200，默认最多 180 秒。
nacos_wait_ready() {
  local limit="${1:-180}" waited=0
  until curl -fsS --noproxy '*' -m 5 -o /dev/null "$NACOS_CONSOLE_ADDR/v3/console/health/readiness" 2>/dev/null; do
    waited=$((waited + 3))
    [ "$waited" -ge "$limit" ] && return 1
    sleep 3
  done
}
```

- [ ] **Step 3: 写 `nacos-verify.sh`**

```bash
#!/bin/bash
# Nacos 加固的验收：逐项打印 PASS / FAIL，任意一项 FAIL 返回 1；参数错误或缺凭据返回 2。
# 用法（mini 上，仓库根目录）：
#   bash patra-infra/scripts/nacos-verify.sh                     # 第 1～10 项
#   bash patra-infra/scripts/nacos-verify.sh --expect-services   # 另查第 11 项：6 个服务都已注册
# 输入同 nacos-init.sh：NACOS_ADDR、NACOS_CONSOLE_ADDR、NACOS_SECRETS_DIR；凭据可用同名环境变量覆盖。
# 只读写探针：配置 nacos-verify-probe*.yaml、服务 nacos-verify-probe，结束时清掉。不碰任何真实配置和密钥。
set -o pipefail
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/nacos-lib.sh"

EXPECT_SERVICES=false
case "${1:-}" in
  --expect-services) EXPECT_SERVICES=true ;;
  "") ;;
  *) echo "用法: $0 [--expect-services]" >&2; exit 2 ;;
esac

nacos_load NACOS_ADMIN_PASSWORD nacos-admin.env || exit 2
nacos_load NACOS_USERNAME nacos.env || exit 2
nacos_load NACOS_PASSWORD nacos.env || exit 2
nacos_load NACOS_PUSH_USERNAME nacos-push.env || exit 2
nacos_load NACOS_PUSH_PASSWORD nacos-push.env || exit 2

PROBE=nacos-verify-probe.yaml
PROBE_PUSH=nacos-verify-probe-push.yaml
PROBE_SVC=nacos-verify-probe
PROBE_IP=192.0.2.1 # TEST-NET-1，保证探针实例不会真的被调用
SERVICES="patra-registry patra-object-storage patra-catalog patra-ingest patra-identity patra-gateway"
PASS=0
FAIL=0
AT=""
APT=""
PT=""

pass() { PASS=$((PASS + 1)); printf '  PASS %2s  %s\n' "$1" "$2"; }
fail() { FAIL=$((FAIL + 1)); printf '  FAIL %2s  %s（%s）\n' "$1" "$2" "$3"; }
detail() { echo "HTTP $NACOS_HTTP code $(nacos_code)"; }

# cfg METHOD TOKEN NAMESPACE GROUP DATAID：发布（POST，正文固定为 probe: 1）、读（GET）、删（DELETE）一份配置
cfg() {
  if [ "$1" = POST ]; then
    nacos_api POST /v3/admin/cs/config "$2" "namespaceId=$3" "groupName=$4" "dataId=$5" "content=probe: 1" type=yaml
  else
    nacos_api "$1" /v3/admin/cs/config "$2" "namespaceId=$3" "groupName=$4" "dataId=$5"
  fi
}

# inst METHOD TOKEN NAMESPACE：注册（POST）或注销（DELETE）探针实例
inst() {
  nacos_api "$1" /v3/admin/ns/instance "$2" "namespaceId=$3" groupName=DEFAULT_GROUP \
    "serviceName=$PROBE_SVC" "ip=$PROBE_IP" port=9 ephemeral=false
}

cleanup() {
  [ -n "$AT" ] || return 0
  local ns
  cfg DELETE "$AT" dev DEFAULT_GROUP "$PROBE"
  cfg DELETE "$AT" dev DEFAULT_GROUP "$PROBE_PUSH"
  cfg DELETE "$AT" public DEFAULT_GROUP "$PROBE_PUSH"
  cfg DELETE "$AT" dev PATRA_SECRETS "$PROBE"
  for ns in public dev; do
    inst DELETE "$AT" "$ns"
    nacos_api DELETE /v3/admin/ns/service "$AT" "namespaceId=$ns" groupName=DEFAULT_GROUP "serviceName=$PROBE_SVC"
  done
}
trap cleanup EXIT

echo "Nacos 验收：$NACOS_ADDR"

D="版本 3.2.4、PG 存储、鉴权开启"
nacos_api GET /v3/admin/core/state ""
state="$(jq -r '.data | "\(.version) \(.datasource_platform) \(.auth_enabled)"' <<<"$NACOS_BODY" 2>/dev/null)"
if [ "$state" = "3.2.4 postgresql true" ]; then pass 1 "$D"; else fail 1 "$D" "实际：${state:-$(detail)}"; fi

D="管理员用默认密码登录不了"
if nacos_login nacos nacos; then fail 2 "$D" "默认密码登录成功"; else pass 2 "$D"; fi

D="管理员用文件里的密码能登录"
if nacos_login nacos "$NACOS_ADMIN_PASSWORD"; then AT="$NACOS_TOKEN"; pass 3 "$D"; else fail 3 "$D" "HTTP $NACOS_HTTP"; fi
nacos_login "$NACOS_USERNAME" "$NACOS_PASSWORD" && APT="$NACOS_TOKEN"
nacos_login "$NACOS_PUSH_USERNAME" "$NACOS_PUSH_PASSWORD" && PT="$NACOS_TOKEN"

D="namespace 列表包含 dev"
if [ -z "$AT" ]; then
  fail 4 "$D" "没有管理员令牌"
else
  nacos_api GET /v3/admin/core/namespace/list "$AT"
  if nacos_ok && jq -e '.data[] | select(.namespace == "dev")' <<<"$NACOS_BODY" >/dev/null; then
    pass 4 "$D"
  else
    fail 4 "$D" "$(detail)"
  fi
fi

D="patra-app 能读 dev/DEFAULT_GROUP 的配置"
if [ -z "$APT" ]; then
  fail 5 "$D" "patra-app 登录失败"
elif [ -z "$AT" ]; then
  fail 5 "$D" "没有管理员令牌，发布不了探针"
else
  cfg POST "$AT" dev DEFAULT_GROUP "$PROBE"
  cfg GET "$APT" dev DEFAULT_GROUP "$PROBE"
  if nacos_ok && [ "$(jq -r '.data.content // empty' <<<"$NACOS_BODY")" = "probe: 1" ]; then
    pass 5 "$D"
  else
    fail 5 "$D" "$(detail)"
  fi
fi

D="patra-app 发布 dev、public 配置都被拒"
if [ -z "$APT" ]; then
  fail 6 "$D" "patra-app 登录失败"
else
  cfg POST "$APT" dev DEFAULT_GROUP "$PROBE_PUSH"; r1="$NACOS_HTTP"
  cfg POST "$APT" public DEFAULT_GROUP "$PROBE_PUSH"; r2="$NACOS_HTTP"
  if [ "$r1" = 403 ] && [ "$r2" = 403 ]; then pass 6 "$D"; else fail 6 "$D" "dev HTTP $r1，public HTTP $r2"; fi
fi

D="patra-app 能在 public 注册、注销实例"
if [ -z "$APT" ]; then
  fail 7 "$D" "patra-app 登录失败"
else
  inst POST "$APT" public; ok1=no; nacos_ok && ok1=yes; d1="$(detail)"
  inst DELETE "$APT" public; ok2=no; nacos_ok && ok2=yes; d2="$(detail)"
  if [ "$ok1" = yes ] && [ "$ok2" = yes ]; then pass 7 "$D"; else fail 7 "$D" "注册 $d1，注销 $d2"; fi
fi

D="patra-app 在 dev 注册实例被拒"
if [ -z "$APT" ]; then
  fail 8 "$D" "patra-app 登录失败"
else
  inst POST "$APT" dev
  if [ "$NACOS_HTTP" = 403 ]; then pass 8 "$D"; else fail 8 "$D" "$(detail)"; fi
fi

D="patra-push 能发布、删除 dev/DEFAULT_GROUP 的配置"
if [ -z "$PT" ]; then
  fail 9 "$D" "patra-push 登录失败"
else
  cfg POST "$PT" dev DEFAULT_GROUP "$PROBE_PUSH"; ok1=no; nacos_ok && ok1=yes; d1="$(detail)"
  cfg DELETE "$PT" dev DEFAULT_GROUP "$PROBE_PUSH"; ok2=no; nacos_ok && ok2=yes; d2="$(detail)"
  if [ "$ok1" = yes ] && [ "$ok2" = yes ]; then pass 9 "$D"; else fail 9 "$D" "发布 $d1，删除 $d2"; fi
fi

D="patra-push 写入、删除 PATRA_SECRETS 都被拒"
if [ -z "$PT" ]; then
  fail 10 "$D" "patra-push 登录失败"
else
  cfg POST "$PT" dev PATRA_SECRETS "$PROBE"; r1="$NACOS_HTTP"
  cfg DELETE "$PT" dev PATRA_SECRETS "$PROBE"; r2="$NACOS_HTTP"
  if [ "$r1" = 403 ] && [ "$r2" = 403 ]; then pass 10 "$D"; else fail 10 "$D" "写入 HTTP $r1，删除 HTTP $r2"; fi
fi

if [ "$EXPECT_SERVICES" = true ]; then
  D="public 下 6 个 patra 服务都有实例"
  if [ -z "$APT" ]; then
    fail 11 "$D" "patra-app 登录失败"
  else
    missing=""
    for s in $SERVICES; do
      nacos_api GET /v3/admin/ns/instance/list "$APT" namespaceId=public groupName=DEFAULT_GROUP "serviceName=$s"
      if ! { nacos_ok && jq -e '.data | length > 0' <<<"$NACOS_BODY" >/dev/null; }; then missing="$missing $s"; fi
    done
    if [ -z "$missing" ]; then pass 11 "$D"; else fail 11 "$D" "没有实例：$missing"; fi
  fi
fi

echo "通过 $PASS / 失败 $FAIL"
if [ "$FAIL" -eq 0 ]; then exit 0; else exit 1; fi
```

```bash
chmod 755 patra-infra/scripts/nacos-verify.sh
shellcheck -x patra-infra/scripts/nacos-lib.sh patra-infra/scripts/nacos-verify.sh && echo "shellcheck 通过"
```

Expected：`shellcheck 通过`。`nacos-lib.sh` 只被 source，shellcheck 若报它缺 shebang 用途或 SC2034（变量看似未用），在对应行加 `# shellcheck disable=...` 并注明原因，不改逻辑。

- [ ] **Step 4: 对未初始化的服务端跑，确认失败**

Task 2 起的 `pap71-nacos` 刚建好，还没有管理员：

```bash
SP=<路径>; . "$SP/test.env"; bash patra-infra/scripts/nacos-verify.sh; echo "exit=$?"
```

Expected：
- `PASS 1`、`PASS 2`；第 2 项通过，是因为还没有管理员。
- 第 3～10 项都是 FAIL，原因分别是登录失败或没有令牌。
- `通过 2 / 失败 8`、`exit=1`。

- [ ] **Step 5: 缺凭据时的退出码**

```bash
SP=<路径>; . "$SP/test.env"; NACOS_SECRETS_DIR="$SP/empty-home" bash patra-infra/scripts/nacos-verify.sh; echo "exit=$?"
```

Expected：一行 `缺少 NACOS_ADMIN_PASSWORD：环境变量没有设置，.../nacos-admin.env 也不存在`，`exit=2`。

- [ ] **Step 6: 提交**

```bash
git add patra-infra/scripts/nacos-lib.sh patra-infra/scripts/nacos-verify.sh
git add --chmod=+x patra-infra/scripts/nacos-verify.sh
git diff --cached --stat
git commit -F - <<'EOF'
feat(infra): 新增 Nacos 验收脚本和两个脚本共用的函数 (PAP-71)

nacos-verify.sh 把加固要求固化成 11 项检查：版本与存储、默认密码、namespace，
以及应用账号、推送账号各自能做和不能做的事。只读写探针，结束时清掉。

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
```

---

### Task 4: 初始化脚本

**Files:**
- Create: `patra-infra/scripts/nacos-init.sh`（755）

**Interfaces:**
- Consumes: `nacos-lib.sh` 的全部函数（Task 3）；`harness.sh nacos` 起的服务端。
- Produces:
  - `nacos-init.sh`：成功返回 0，失败返回 1。
  - 输出每行以 `[nacos-init]` 开头。
  - 每个角色打印一行 `角色 <ROLE> 的权限：新增 N，删除 M`。

- [ ] **Step 1: 写 `nacos-init.sh`**

```bash
#!/bin/bash
# Nacos 初始化：管理员、dev namespace、两个账号、角色和权限。幂等，可以反复执行。
# 用法（mini 上，仓库根目录，nacos 容器 healthy 之后）：
#   bash patra-infra/scripts/nacos-init.sh
# 输入：
#   NACOS_ADDR、NACOS_CONSOLE_ADDR、NACOS_SECRETS_DIR（见 nacos-lib.sh）
#   凭据：nacos-admin.env、nacos.env、nacos-push.env；环境变量里已有同名变量时优先，对应文件可以不存在
# 账号的权限以下面的 APP_PERMS、PUSH_PERMS 为准：缺的补上，多的删掉。不要在控制台手改权限，下次执行会被改回。
# 改账号密码：改文件后重跑本脚本，已有账号的密码会被重置成文件里的值。
# 管理员密码只在服务端还没有管理员时生效；之后要改，先在控制台改，再同步改 nacos-admin.env。
set -eo pipefail
. "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/nacos-lib.sh"

APP_ROLE=PATRA_APP
PUSH_ROLE=PATRA_PUSH
# 每行一条权限：resource|action。资源串按 {namespace}:{group}:{config|naming}/{name} 做正则匹配
APP_PERMS='public:*:naming/*|rw
dev:*:config/*|r'
PUSH_PERMS='dev:DEFAULT_GROUP:config/*|rw
dev:*:config/*|r'

log() { echo "[nacos-init] $*"; }
die() { echo "[nacos-init] 失败：$*" >&2; exit 1; }
detail() { echo "HTTP $NACOS_HTTP code $(nacos_code)"; }

nacos_load NACOS_ADMIN_PASSWORD nacos-admin.env || exit 1
nacos_load NACOS_USERNAME nacos.env || exit 1
nacos_load NACOS_PASSWORD nacos.env || exit 1
nacos_load NACOS_PUSH_USERNAME nacos-push.env || exit 1
nacos_load NACOS_PUSH_PASSWORD nacos-push.env || exit 1

# ensure_user USERNAME PASSWORD：不存在就创建；存在就把密码重置成文件里的值
ensure_user() {
  nacos_api GET /v3/auth/user/list "$AT" pageNo=1 pageSize=100 "username=$1" search=accurate
  nacos_ok || die "查询用户 $1：$(detail)"
  if jq -e --arg u "$1" '.data.pageItems[]? | select(.username == $u)' <<<"$NACOS_BODY" >/dev/null; then
    nacos_api PUT /v3/auth/user "$AT" "username=$1" "newPassword=$2"
    nacos_ok || die "重置 $1 的密码：$(detail)"
    log "用户 $1 已存在，密码已按文件重置"
  else
    nacos_api POST /v3/auth/user "$AT" "username=$1" "password=$2"
    nacos_ok || die "创建用户 $1：$(detail)"
    log "用户 $1 已创建"
  fi
}

# ensure_role ROLE USERNAME：没绑定就绑定
ensure_role() {
  nacos_api GET /v3/auth/role/list "$AT" pageNo=1 pageSize=100 "username=$2" "role=$1" search=accurate
  nacos_ok || die "查询角色 $1：$(detail)"
  if jq -e --arg r "$1" --arg u "$2" '.data.pageItems[]? | select(.role == $r and .username == $u)' \
    <<<"$NACOS_BODY" >/dev/null; then
    log "角色 $1 已绑定 $2"
  else
    nacos_api POST /v3/auth/role "$AT" "role=$1" "username=$2"
    nacos_ok || die "绑定角色 $1 到 $2：$(detail)"
    log "角色 $1 已绑定到 $2"
  fi
}

# sync_perms ROLE DESIRED：让角色的权限与 DESIRED（每行 resource|action）完全一致
sync_perms() {
  local role="$1" desired="$2" current line added=0 removed=0
  nacos_api GET /v3/auth/permission/list "$AT" pageNo=1 pageSize=100 "role=$role" search=accurate
  nacos_ok || die "查询 $role 的权限：$(detail)"
  current="$(jq -r '.data.pageItems[]? | "\(.resource)|\(.action)"' <<<"$NACOS_BODY")"
  while IFS= read -r line; do
    [ -z "$line" ] && continue
    if ! grep -Fxq -- "$line" <<<"$current"; then
      nacos_api POST /v3/auth/permission "$AT" "role=$role" "resource=${line%|*}" "action=${line##*|}"
      nacos_ok || die "给 $role 加权限 $line：$(detail)"
      added=$((added + 1))
    fi
  done <<<"$desired"
  while IFS= read -r line; do
    [ -z "$line" ] && continue
    if ! grep -Fxq -- "$line" <<<"$desired"; then
      nacos_api DELETE /v3/auth/permission "$AT" "role=$role" "resource=${line%|*}" "action=${line##*|}"
      nacos_ok || die "删除 $role 的多余权限 $line：$(detail)"
      removed=$((removed + 1))
    fi
  done <<<"$current"
  log "角色 $role 的权限：新增 $added，删除 $removed"
}

log "等待 Nacos 就绪：$NACOS_CONSOLE_ADDR"
nacos_wait_ready || die "180 秒内没有就绪"

# 成功时响应体里有明文密码，只取 code，绝不打印响应体
nacos_api POST /v3/auth/user/admin "" "password=$NACOS_ADMIN_PASSWORD"
case "$(nacos_code)" in
  0) log "管理员 nacos 已初始化" ;;
  409) log "已有管理员，跳过初始化" ;;
  *) die "初始化管理员：$(detail)" ;;
esac

nacos_login nacos "$NACOS_ADMIN_PASSWORD" \
  || die "管理员登录（HTTP $NACOS_HTTP）。服务端已有管理员时，nacos-admin.env 要与它现在的密码一致"
AT="$NACOS_TOKEN"

nacos_api GET /v3/admin/core/namespace/check "$AT" namespaceId=dev
nacos_ok || die "查询 namespace dev：$(detail)"
if [ "$(jq -r '.data' <<<"$NACOS_BODY")" -gt 0 ]; then
  log "namespace dev 已存在"
else
  nacos_api POST /v3/admin/core/namespace "$AT" namespaceId=dev namespaceName=dev "namespaceDesc=Patra dev 环境"
  nacos_ok || die "创建 namespace dev：$(detail)"
  log "namespace dev 已创建"
fi

ensure_user "$NACOS_USERNAME" "$NACOS_PASSWORD"
ensure_user "$NACOS_PUSH_USERNAME" "$NACOS_PUSH_PASSWORD"
ensure_role "$APP_ROLE" "$NACOS_USERNAME"
ensure_role "$PUSH_ROLE" "$NACOS_PUSH_USERNAME"
sync_perms "$APP_ROLE" "$APP_PERMS"
sync_perms "$PUSH_ROLE" "$PUSH_PERMS"
log "完成"
```

```bash
chmod 755 patra-infra/scripts/nacos-init.sh
shellcheck -x patra-infra/scripts/nacos-init.sh && echo "shellcheck 通过"
```

Expected：`shellcheck 通过`。

- [ ] **Step 2: 首次执行，验收全部通过**

```bash
SP=<路径>; . "$SP/test.env"
bash patra-infra/scripts/nacos-init.sh > "$SP/init-1.log" 2>&1; echo "init exit=$?"; cat "$SP/init-1.log"
bash patra-infra/scripts/nacos-verify.sh; echo "verify exit=$?"
```

Expected：
- `init exit=0`。日志依次是：
  - `管理员 nacos 已初始化`、`namespace dev 已创建`；
  - 两个 `用户 ... 已创建`、两个 `角色 ... 已绑定到 ...`；
  - `角色 PATRA_APP 的权限：新增 2，删除 0`、`角色 PATRA_PUSH 的权限：新增 2，删除 0`；
  - `完成`。
- 第 1～10 项都是 PASS，`通过 10 / 失败 0`，`verify exit=0`。

失败时：
- 第 5 项失败而 `detail` 显示 `HTTP 200`，看是不是 `.data.content` 的路径不对，用 `PRINT_JQ='.data | keys'` 和 `admin.sh` 查 `GET /v3/admin/cs/config` 的返回结构，按实际改 `nacos-verify.sh`。
- 第 7 项失败，先确认 `ephemeral=false` 的注册在这个版本是否被接受，按 `detail` 修正。

- [ ] **Step 3: 再跑一次：幂等，输出不含密钥**

```bash
SP=<路径>; . "$SP/test.env"
bash patra-infra/scripts/nacos-init.sh > "$SP/init-2.log" 2>&1; echo "init exit=$?"
grep -c '新增 0，删除 0' "$SP/init-2.log"
grep -c '密码已按文件重置' "$SP/init-2.log"
PRINT_JQ='.data.pageItems | length' bash "$SP/admin.sh" GET /v3/auth/permission/list pageNo=1 pageSize=100 role=PATRA_PUSH search=accurate
bash patra-infra/scripts/nacos-verify.sh > "$SP/verify-2.log" 2>&1; tail -1 "$SP/verify-2.log"
cat "$SP/init-1.log" "$SP/init-2.log" "$SP/verify-2.log" \
  | grep -c -F -f <(sed -n -E 's/^NACOS_(AUTH_TOKEN|AUTH_IDENTITY_KEY|AUTH_IDENTITY_VALUE|PASSWORD|PUSH_PASSWORD|ADMIN_PASSWORD)=//p' "$NACOS_SECRETS_DIR"/*.env)
```

Expected：
- `init exit=0`。
- `2`：两个角色都是新增 0、删除 0。
- `2`：两个用户都走了重置。
- `HTTP 200 code 0`，接着一行 `2`。
- `通过 10 / 失败 0`。
- 最后一个计数是 `0`：两次初始化、一次验收的输出里，一个密钥值都没有出现（Review Focus 第 1 条）。

- [ ] **Step 4: 重建容器，数据还在 PG 里**

```bash
SP=<路径>; . "$SP/test.env"; bash "$SP/harness.sh" nacos && bash patra-infra/scripts/nacos-verify.sh | tail -1
docker exec pap71-pg psql -U postgres -d patra_nacos -Atc 'SELECT count(*) FROM users'
```

Expected：`通过 10 / 失败 0`、`3`。

- [ ] **Step 5: 反向检查：权限被放宽时验收能发现，初始化能恢复，探针不残留**

```bash
SP=<路径>; . "$SP/test.env"
bash "$SP/admin.sh" POST /v3/auth/permission role=PATRA_PUSH 'resource=dev:*:*' action=rw
bash patra-infra/scripts/nacos-verify.sh | grep -E '^  FAIL|^通过'
PRINT_JQ='[.data.pageItems[]? | select(.dataId | startswith("nacos-verify-probe"))] | length' \
  bash "$SP/admin.sh" GET /v3/admin/cs/config/list namespaceId=dev pageNo=1 pageSize=100 dataId= groupName=
bash patra-infra/scripts/nacos-init.sh | grep PATRA_PUSH
bash patra-infra/scripts/nacos-verify.sh | tail -1
```

Expected：
- `HTTP 200 code 0`：多余的权限加上了。
- 只有一行 `FAIL 10  patra-push 写入、删除 PATRA_SECRETS 都被拒（写入 HTTP 200，删除 HTTP 200）`，然后是 `通过 9 / 失败 1`。
- `HTTP 200 code 0`，接着 `0`：探针已被清掉，dev 里没有任何 `nacos-verify-probe*`（Review Focus 第 3 条）。
- `[nacos-init] 角色 PATRA_PUSH 的权限：新增 0，删除 1`。
- `通过 10 / 失败 0`。

- [ ] **Step 6: 改密码：改文件后重跑，新密码生效**

```bash
SP=<路径>; . "$SP/test.env"; F="$NACOS_SECRETS_DIR/nacos.env"
cp "$F" "$SP/nacos.env.old"
(umask 077; printf 'NACOS_USERNAME=patra-app\nNACOS_PASSWORD=%s\n' "$(openssl rand -hex 24)" > "$F")
bash patra-infra/scripts/nacos-init.sh | grep patra-app
bash patra-infra/scripts/nacos-verify.sh | tail -1
W="$(git rev-parse --show-toplevel)"
bash -c '. "$1/patra-infra/scripts/nacos-lib.sh"; P="$(sed -n "s/^NACOS_PASSWORD=//p" "$2")";
  nacos_login patra-app "$P" && echo "旧密码仍能登录" || echo "旧密码登录失败（HTTP $NACOS_HTTP）"' _ "$W" "$SP/nacos.env.old"
```

Expected：
- `[nacos-init] 用户 patra-app 已存在，密码已按文件重置`、`[nacos-init] 角色 PATRA_APP 已绑定 patra-app`。
- `通过 10 / 失败 0`：验收用的是新密码。
- `旧密码登录失败（HTTP 403）`（Review Focus 第 2 条）。

如果重置一步报 `HTTP 400`，说明服务端不解析 PUT 的表单请求体。这时停下来报告用户，不要把密码挪进 URL。

- [ ] **Step 7: 缺凭据时，不发请求就退出**

```bash
SP=<路径>; . "$SP/test.env"
mkdir -p "$SP/partial" && cp "$NACOS_SECRETS_DIR/nacos.env" "$SP/partial/"
NACOS_SECRETS_DIR="$SP/partial" NACOS_ADDR=http://127.0.0.1:9 bash patra-infra/scripts/nacos-init.sh; echo "exit=$?"
```

Expected：
- 只有一行 `缺少 NACOS_ADMIN_PASSWORD：环境变量没有设置，.../partial/nacos-admin.env 也不存在`，没有 `等待 Nacos 就绪`。
- `exit=1`（Review Focus 第 5 条）。

- [ ] **Step 8: 提交**

```bash
git add patra-infra/scripts/nacos-init.sh
git add --chmod=+x patra-infra/scripts/nacos-init.sh
git diff --cached --stat
git commit -F - <<'EOF'
feat(infra): 新增幂等的 Nacos 初始化脚本 (PAP-71)

nacos-init.sh 初始化管理员、建 dev namespace、建 patra-app 和 patra-push 两个账号并绑定角色，
再把两个角色的权限同步成脚本里的清单：缺的补上，多的删掉。已有账号的密码按文件重置。

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
```

---

### Task 5: 初始化脚本在旧的 3.0.2 上可用（切换第 2 步的前提）

**Files:**
- Modify: `docs/patra/specs/2026-10-11-nacos-hardening-design.md` 第 12 节（写回实测）

**Interfaces:**
- Consumes: `harness.sh nacos-old`、Task 3～4 的两个脚本。
- Produces: spec 第 12 节第 1～3 条的结论。上线 2 依赖这些结论。

- [ ] **Step 1: 起 3.0.2，模拟 mini 现状**

```bash
SP=<路径>; bash "$SP/harness.sh" nacos-old && . "$SP/test.env" && W="$(git rev-parse --show-toplevel)" && \
bash -c '. "$1/patra-infra/scripts/nacos-lib.sh"
  nacos_api POST /v3/auth/user/admin "" password=nacos; echo "初始化管理员：code $(nacos_code)"
  nacos_api GET /v3/admin/core/state ""; jq -r ".data | \"\(.version) [\(.datasource_platform)]\"" <<<"$NACOS_BODY"
  nacos_api POST /v3/auth/user/login "" username=nacos password=wrong-password; echo "错误密码登录：HTTP $NACOS_HTTP"
  nacos_login nacos nacos && echo "默认密码登录成功，返回字段：$(jq -c keys <<<"$NACOS_BODY")"' _ "$W"
```

Expected：
- `pap71-nacos 就绪（nacos/nacos-server:v3.0.2）`。
- `初始化管理员：code 0`、`3.0.2 []`。
- 一行 `错误密码登录：HTTP <状态码>`：记下状态码，写回 spec。
- `默认密码登录成功，返回字段：["accessToken","globalAdmin","tokenTtl","username"]`。

- [ ] **Step 2: 按切换第 2 步的方式运行初始化**

本机只有 `nacos.env`、`nacos-push.env`，管理员密码用环境变量给出默认的 `nacos`：

```bash
SP=<路径>; . "$SP/test.env"
mkdir -p "$SP/macbook" && cp "$NACOS_SECRETS_DIR/nacos.env" "$NACOS_SECRETS_DIR/nacos-push.env" "$SP/macbook/"
NACOS_SECRETS_DIR="$SP/macbook" NACOS_ADMIN_PASSWORD=nacos bash patra-infra/scripts/nacos-init.sh; echo "exit=$?"
NACOS_SECRETS_DIR="$SP/macbook" NACOS_ADMIN_PASSWORD=nacos bash patra-infra/scripts/nacos-verify.sh; echo "exit=$?"
```

Expected：
- 初始化：`已有管理员，跳过初始化`、`namespace dev 已创建`，账号、角色、权限与 Task 4 第 2 步相同，`exit=0`。
- 验收：只有两项 FAIL：
  - 第 1 项（实际 `3.0.2  true`）；
  - 第 2 项（默认密码登录成功）。

  这两项在旧服务端上本就该失败。其余 8 项 PASS，`通过 8 / 失败 2`，`exit=1`。

其他项失败，说明 3.0.2 的接口与 3.2.4 有出入，切换第 2 步不成立。停下来把失败项报告给用户，不要往下做。

- [ ] **Step 3: 收拾临时环境**

```bash
SP=<路径>; bash "$SP/harness.sh" down; docker ps -a --format '{{.Names}}' | grep -c pap71
```

Expected：`0`。

- [ ] **Step 4: 写回 spec 第 12 节**

把第 12 节第 1～3 条改为结论，第 4 条保持不动（上线时观察）：

1. 3.0.2 上 `nacos-init.sh` 可用：管理员初始化接口在已有管理员时返回 409；namespace、用户、角色、权限接口与 3.2.4 一致。登录成功时返回的字段相同；密码错误时返回 HTTP `<第 1 步记下的状态码>`。
2. 不依赖「重复建用户」的返回：脚本先查用户列表，存在就用 `PUT /v3/auth/user`（`username`、`newPassword`）重置密码。Task 4 第 6 步实测：PUT 的表单请求体会被解析，新密码生效、旧密码失效。
3. `DELETE /v3/auth/permission` 的参数是 `role`、`resource`、`action`，与源码一致。Task 4 第 5 步实测删除多余权限成功。

- [ ] **Step 5: 提交**

```bash
git add docs/patra/specs/2026-10-11-nacos-hardening-design.md
git diff --cached --stat
git commit -F - <<'EOF'
docs(infra): 写回初始化脚本在 3.0.2 和 3.2.4 上的实测结果 (PAP-71)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
```

---

### Task 6: 6 个服务改用 patra-app

**Files:**
- Modify: `patra-infra/docker/docker-compose.apps.yaml`：头部注释第 10～13 行；registry、object-storage、catalog、ingest、identity、gateway 的 `env_file`
- Modify: `patra-infra/docker/.env.common:16-20`
- Modify: `patra-api/patra-registry/patra-registry-boot/src/main/resources/application-dev.yml`
- Modify: `patra-api/patra-object-storage/patra-object-storage-boot/src/main/resources/application-dev.yml`
- Modify: `patra-api/patra-catalog/patra-catalog-boot/src/main/resources/application-dev.yml`
- Modify: `patra-api/patra-ingest/patra-ingest-boot/src/main/resources/application-dev.yml`
- Modify: `patra-api/patra-identity/patra-identity-boot/src/main/resources/application-dev.yml`
- Modify: `patra-api/patra-gateway-boot/src/main/resources/application-dev.yml`
- Test（不进仓库）: `$SP/t-import.sh`

**Interfaces:**
- Consumes: `nacos.env` 的变量名 `NACOS_USERNAME`、`NACOS_PASSWORD`（Task 2 的模板）。`application.yml` 已经在读这两个变量，不改。
- Produces: 容器和本机进程都从 `~/.patra/secrets/nacos.env` 取 Nacos 账号。

- [ ] **Step 1: 跑一次，确认容器还在用管理员账号**

```bash
SP=<路径>; H="$SP/apps-home/.patra/secrets"; mkdir -p "$H"
printf 'REDIS_PASSWORD=x\n' > "$H/redis.env"; printf 'PATRA_GATEWAY_IDENTITY_ASSERTION_PRIVATE_KEY=x\n' > "$H/gateway.env"
printf 'NACOS_USERNAME=patra-app\nNACOS_PASSWORD=x\n' > "$H/nacos.env"
DOCKER_CONFIG="$HOME/.docker" HOME="$SP/apps-home" docker compose -f patra-infra/docker/docker-compose.apps.yaml \
  config --format json | jq -r '[.services | to_entries[] | select(.value.environment.NACOS_HOST)
  | "\(.key)=\(.value.environment.NACOS_USERNAME)"] | join(" ")'
```

Expected：6 个服务都是 `=nacos`。

- [ ] **Step 2: 改 apps compose 和 `.env.common`**

头部注释的密钥清单，在 `gateway.env` 那行后面加一行：

```yaml
#     nacos.env     Nacos 应用账号 patra-app。6 个后端服务必需
```

registry、object-storage、catalog、ingest、identity、gateway 六个服务的 `env_file`，都在 `- .env.<服务>` 那一行后面插入：

```yaml
      - path: ${HOME}/.patra/secrets/nacos.env
        required: true
```

portal、learn 不加：它们不连 Nacos。

`.env.common` 第 16～20 行改为：

```bash
# --- Nacos 服务发现（patra-net 服务名 nacos:8848，gRPC 不经 tailscale）---
# 账号 patra-app 在仓库外的 ~/.patra/secrets/nacos.env（compose env_file，必需），这里不放
NACOS_HOST=nacos
NACOS_PORT=8848
```

- [ ] **Step 3: 跑 compose 检查，确认通过**

```bash
SP=<路径>
DOCKER_CONFIG="$HOME/.docker" HOME="$SP/apps-home" docker compose -f patra-infra/docker/docker-compose.apps.yaml \
  config --format json | jq -r '[.services | to_entries[] | select(.value.environment.NACOS_HOST)
  | "\(.key)=\(.value.environment.NACOS_USERNAME)"] | join(" ")'
mv "$SP/apps-home/.patra/secrets/nacos.env" "$SP/apps-home/.patra/secrets/nacos.env.off"
DOCKER_CONFIG="$HOME/.docker" HOME="$SP/apps-home" docker compose -f patra-infra/docker/docker-compose.apps.yaml \
  config --quiet 2>&1 | grep -o 'nacos.env not found'
mv "$SP/apps-home/.patra/secrets/nacos.env.off" "$SP/apps-home/.patra/secrets/nacos.env"
```

Expected：
- 6 个服务都是 `=patra-app`。
- `nacos.env not found`：缺文件时 compose 拒绝执行。

- [ ] **Step 4: 写导入测试**

`$SP/t-import.sh`：

```bash
#!/bin/bash
# 6 个服务的 dev profile 都导入 nacos.env：user.home 里没有它时，启动失败并报出路径。
# redis.env、gateway.env 放假的，保证失败只来自 nacos.env；Nacos 地址指向 127.0.0.1:1，导入没生效也连不到 mini。
W="$(git rev-parse --show-toplevel)"
SP="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
H="$SP/jvm-home"
mkdir -p "$H/.patra/secrets"
rm -f "$H/.patra/secrets/nacos.env"
printf 'REDIS_PASSWORD=x\n' > "$H/.patra/secrets/redis.env"
printf 'PATRA_GATEWAY_IDENTITY_ASSERTION_PRIVATE_KEY=x\n' > "$H/.patra/secrets/gateway.env"
PASS=0; FAIL=0
for jar in \
  patra-api/patra-registry/patra-registry-boot/build/libs/patra-registry-boot-0.1.0-SNAPSHOT.jar \
  patra-api/patra-object-storage/patra-object-storage-boot/build/libs/patra-object-storage-boot-0.1.0-SNAPSHOT.jar \
  patra-api/patra-catalog/patra-catalog-boot/build/libs/patra-catalog-boot-0.1.0-SNAPSHOT.jar \
  patra-api/patra-ingest/patra-ingest-boot/build/libs/patra-ingest-boot-0.1.0-SNAPSHOT.jar \
  patra-api/patra-identity/patra-identity-boot/build/libs/patra-identity-boot-0.1.0-SNAPSHOT.jar \
  patra-api/patra-gateway-boot/build/libs/patra-gateway-boot-0.1.0-SNAPSHOT.jar; do
  name="$(basename "$jar" -0.1.0-SNAPSHOT.jar)"
  log="$SP/import-$name.log"
  NACOS_HOST=127.0.0.1 NACOS_PORT=1 java -Duser.home="$H" -jar "$W/$jar" --spring.profiles.active=dev > "$log" 2>&1 &
  pid=$!
  for _ in $(seq 1 60); do kill -0 "$pid" 2>/dev/null || break; sleep 1; done
  if kill -0 "$pid" 2>/dev/null; then
    kill "$pid"; wait "$pid" 2>/dev/null
    FAIL=$((FAIL + 1)); echo "  ✗ $name：60 秒内没有退出，导入没生效"
  elif grep -q 'nacos.env.*does not exist' "$log"; then
    PASS=$((PASS + 1)); echo "  ✓ $name：缺 nacos.env 时启动失败并报出路径"
  else
    FAIL=$((FAIL + 1)); echo "  ✗ $name：退出了，但没有报 nacos.env，见 $log"
  fi
done
echo "通过 $PASS / 失败 $FAIL"
[ "$FAIL" -eq 0 ]
```

- [ ] **Step 5: 改 6 个 `application-dev.yml`**

catalog、identity：把 import 段上方的注释和列表改为：

```yaml
    # Redis 密码和 Nacos 账号与 mini 容器是同一批文件，放在仓库外（patra-infra/docker/README.md「密钥」）。
    # 不加 optional：缺文件时启动失败并报出路径
    import:
      - file:${user.home}/.patra/secrets/redis.env[.properties]
      - file:${user.home}/.patra/secrets/nacos.env[.properties]
```

gateway：注释不变，列表末尾加一行：

```yaml
      - file:${user.home}/.patra/secrets/nacos.env[.properties]
```

registry、ingest：在 `      on-profile: dev` 之后插入：

```yaml
    # Nacos 账号与 mini 容器是同一个文件，放在仓库外（patra-infra/docker/README.md「密钥」）。
    # 不加 optional：缺文件时启动失败并报出路径
    import:
      - file:${user.home}/.patra/secrets/nacos.env[.properties]
```

object-storage：这个文件没有 `spring.config` 段。在 `spring:` 之后、`  # dev 场景：用 TAILSCALE_IP 注册` 之前插入：

```yaml
  config:
    # Nacos 账号与 mini 容器是同一个文件，放在仓库外（patra-infra/docker/README.md「密钥」）。
    # 不加 optional：缺文件时启动失败并报出路径
    import:
      - file:${user.home}/.patra/secrets/nacos.env[.properties]
```

- [ ] **Step 6: 构建并跑导入测试**

```bash
SP=<路径>; L="$(mktemp)"
./gradlew $(jq -r '[.services[] | select(.gradleTask) | .gradleTask] | join(" ")' patra-infra/cd/services.json) \
  -q > "$L" 2>&1; echo "bootJar exit=$?"; tail -3 "$L"
bash "$SP/t-import.sh"; echo "exit=$?"
```

Expected：`bootJar exit=0`；6 个 `✓`，`通过 6 / 失败 0`，`exit=0`。

- [ ] **Step 7: 提交**

```bash
git add patra-infra/docker/docker-compose.apps.yaml patra-infra/docker/.env.common \
  patra-api/patra-registry/patra-registry-boot/src/main/resources/application-dev.yml \
  patra-api/patra-object-storage/patra-object-storage-boot/src/main/resources/application-dev.yml \
  patra-api/patra-catalog/patra-catalog-boot/src/main/resources/application-dev.yml \
  patra-api/patra-ingest/patra-ingest-boot/src/main/resources/application-dev.yml \
  patra-api/patra-identity/patra-identity-boot/src/main/resources/application-dev.yml \
  patra-api/patra-gateway-boot/src/main/resources/application-dev.yml
git diff --cached --stat
git commit -F - <<'EOF'
feat(infra): 6 个服务改用应用账号 patra-app 连 Nacos (PAP-71)

账号从仓库外的 nacos.env 读取：容器经 compose env_file（必需），本机进程经 dev profile 导入
（不加 optional）。.env.common 删掉提交在仓库里的管理员账号。

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
```

---

### Task 7: 文档

**Files:**
- Modify: `patra-infra/CLAUDE.md`、`patra-infra/docker/README.md`、`CLAUDE.md`（仓库根）
- Modify: `patra-api/patra-gateway-boot/README.md:107`、`patra-api/patra-object-storage/README.md:304-312`

**Interfaces:**
- Consumes: Task 1～6 的文件名、命令和行为。
- Produces: 无。

- [ ] **Step 1: `patra-infra/CLAUDE.md`**

非显性约束第 4 条整条替换为：

```markdown
4. **`.env` / `.env.dev` 含 dev 默认凭据且随仓库提交。** 这是有意为之 —— Mac mini 靠 `git pull` 同步这些配置（PG/MinIO/MySQL 密码等）。这些是 dev 默认值、对应服务仅在 tailscale 内网暴露，公网打不到端口。**Nacos 不在此例**：token 签名密钥、identity 和三个账号的密码只放 `~/.patra/secrets/`（见 README「密钥」「Nacos」）；原先提交在 `.env` 里的那组鉴权值已经公开，PAP-71 已轮换作废。新增密钥一律放 `~/.patra/secrets/`，不再往 `.env` 加；如未来引入真正敏感的生产密钥，须改走外部 secret 注入。
```

「环境文件三层，密钥在仓库外」一条里，把括号中的密钥文件说明改为：

```markdown
（`redis.env` 给 catalog/identity/gateway、`gateway.env` 给 gateway、`nacos.env` 给 6 个后端服务，三者必需；`<svc>.env` 可选；后者覆盖同名）
```

scripts 一览表末尾加三行：

```markdown
| `nacos-init.sh` | Mac mini | Nacos 初始化：管理员、`dev` namespace、`patra-app` / `patra-push` 账号、角色和权限。幂等；权限以脚本里的清单为准，多的会被删掉 |
| `nacos-verify.sh [--expect-services]` | Mac mini | Nacos 加固验收，11 项 PASS / FAIL；只读写探针 |
| `nacos-lib.sh` | — | 上面两个脚本共用的函数，只供 source |
```

- [ ] **Step 2: `patra-infra/docker/README.md`**

首次部署的步骤：

4c 的注释和 scp 改为：

```bash
# 4c. (MacBook) 把密钥文件复制到 Mac mini：与 MacBook 上的是同一份，生成方法见下文「密钥」。
#     目录由第 4 步建好；缺了这几个文件，core 栈的 redis、nacos 和整个 apps 栈都起不来
scp -p ~/.patra/secrets/redis.env ~/.patra/secrets/gateway.env ~/.patra/secrets/nacos.env \
  ~/.patra/secrets/nacos-push.env linqibin@linqibins-mac-mini:.patra/secrets/
# 4d. (Mac mini) 生成只放 mini 的 nacos-server.env、nacos-admin.env，命令见 secrets/ 下对应的 *.example。
#     管理员密码另存一份到密码管理器
```

第 7 步改为：

```bash
# 7. (Mac mini) 初始化 Nacos（nacos 容器 healthy 后执行，幂等）：管理员、dev namespace、账号、角色和权限
bash patra-infra/scripts/nacos-init.sh
bash patra-infra/scripts/nacos-verify.sh    # 预期 通过 10 / 失败 0
```

「MacBook 应用访问 Nacos」一节第一段改为：

```markdown
MacBook 上的应用直连 Mac mini 的 Nacos（HTTP 8848 + gRPC 9848），**无需 ssh tunnel**：各服务 yml 的 `NACOS_HOST` 缺省跟随 `PATRA_INFRA_HOST`。账号 `patra-app` 来自 `~/.patra/secrets/nacos.env`，由 dev profile 导入，缺文件时启动失败。Nacos 控制台同样直连：`http://$PATRA_INFRA_HOST:8080`，用管理员登录。
```

「密钥」一节：
- 开头一句改为：

  ```markdown
  密钥放在仓库外的 `~/.patra/secrets/`（目录 0700，文件 0600）。除了只放 mini 的 `nacos-server.env`、`nacos-admin.env`，MacBook 和 Mac mini 各放一份，内容相同。
  ```

- 表格在 `gateway.env` 一行之后加四行：

```markdown
| `nacos-server.env` | Nacos 的 `NACOS_AUTH_TOKEN`、`NACOS_AUTH_IDENTITY_KEY`、`NACOS_AUTH_IDENTITY_VALUE`。**只放 mini** | core 栈的 nacos | compose 报出路径并拒绝执行 |
| `nacos-admin.env` | Nacos 管理员密码 `NACOS_ADMIN_PASSWORD`。**只放 mini**，另存密码管理器 | `nacos-init.sh`、`nacos-verify.sh` | 脚本说明缺哪个变量并退出 |
| `nacos.env` | 应用账号 `NACOS_USERNAME=patra-app`、`NACOS_PASSWORD` | 6 个后端服务的容器和本机进程；两个 Nacos 脚本 | 同 `redis.env` |
| `nacos-push.env` | 推送账号 `NACOS_PUSH_USERNAME=patra-push`、`NACOS_PUSH_PASSWORD` | 两个 Nacos 脚本；PAP-75 的推送 | 脚本说明缺哪个变量并退出 |
```

- 「生成」代码块末尾加：

```bash
printf 'NACOS_USERNAME=patra-app\nNACOS_PASSWORD=%s\n' "$(openssl rand -hex 24)" > ~/.patra/secrets/nacos.env
printf 'NACOS_PUSH_USERNAME=patra-push\nNACOS_PUSH_PASSWORD=%s\n' "$(openssl rand -hex 24)" > ~/.patra/secrets/nacos-push.env
```

  代码块后面加一句：

  ```markdown
  `nacos-server.env`、`nacos-admin.env` 在 mini 上生成，命令见 `secrets/` 下对应的模板。
  ```
- 「复制到 Mac mini」的 scp 改成与 4c 相同的四个文件。

「服务访问 URL」核心服务里的 Nacos 一行改为：

```markdown
- **Nacos 控制台**: http://linqibins-mac-mini:8080（用管理员 `nacos` 登录，密码在 mini 的 `~/.patra/secrets/nacos-admin.env`）
```

在「密钥」一节之后、「应用侧配置」之前新增一节：

````markdown
## Nacos

- 3.2.4，开着鉴权。存储在业务 PG 的 `patra_nacos` 库（官方 PG 插件，镜像自带）；`data/` 卷只剩服务发现的持久化数据和 jraft 元数据。
- 账号：管理员 `nacos`；应用账号 `patra-app`（public 服务注册读写、dev 配置只读）；推送账号 `patra-push`（只能写 dev 的 `DEFAULT_GROUP`）。密钥放 dev 的 `PATRA_SECRETS` 分组，只有管理员能改。
- 账号、角色、权限由 `scripts/nacos-init.sh` 按脚本里的清单同步，不要在控制台手改：下次执行会被改回。验收用 `scripts/nacos-verify.sh`（加 `--expect-services` 时另查 6 个服务都已注册）。
- 改应用或推送账号的密码：两台机器都改密钥文件，mini 上跑 `nacos-init.sh`，再按「改了密钥文件之后」重建 6 个应用容器、重启本机进程。
- 换 token 签名密钥：改 mini 上的 `nacos-server.env`，执行 `docker compose -f docker-compose.core.yaml up -d --force-recreate nacos`。已签出的令牌全部失效，客户端会自动重新登录。改密码或删用户**不会**作废已签出的令牌（默认 18000 秒有效），要立刻撤销只能换签名密钥。
- 镜像启动脚本开着 `set -x`，token 签名密钥和 identity 会出现在 `docker logs patra-nacos` 里（3.3 起改掉）。排查时不要把这段日志贴到任何地方。

### 建表

全新部署时，PG 的 init 脚本会自动执行 `postgres/init-scripts/03-nacos-schema.sh`。已有数据的 PG（如 mini）手动执行：

```bash
docker exec patra-postgres psql -U postgres -c 'CREATE DATABASE patra_nacos;'
docker exec patra-postgres bash /docker-entrypoint-initdb.d/03-nacos-schema.sh
```

脚本先判断库的状态：空库才建表，表齐了就跳过，只有一部分时报错、不动库。建表在一个事务里，失败会回滚到空库。官方 schema 每张表都先 DROP 再建，不要绕过 03 直接执行 `pg-schema-*.sql`。

### 升级

- 对比新旧两个 tag 的 `pg-schema.sql`，手动执行差异部分；把新文件放进 `postgres/init-scripts/nacos/`，并改 03 里的默认路径。
- 升到 3.3 时，compose 里数据库相关的环境变量要改名为 `NACOS_PLUGIN_DATASOURCE_DB_*`：3.3 镜像的配置文件改用新键，新键优先于旧的 `db.*`。
````

- [ ] **Step 3: 根 `CLAUDE.md` 安全红线第 3 条**

整条替换为：

```markdown
3. **已知例外**：`patra-infra/docker/.env`、`.env.dev` 含 dev 默认凭据且有意随仓库提交（Mac mini 靠 git pull 同步），对应服务仅在 tailscale 内网暴露，公网打不到端口。Nacos 的 token 签名密钥、identity 和各账号密码**不在**此例外内，只放 `~/.patra/secrets/`。详见 `patra-infra/CLAUDE.md`。
```

- [ ] **Step 4: 两个服务 README**

`patra-api/patra-gateway-boot/README.md` 配置表的 Nacos 一行改为：

```markdown
| `NACOS_HOST` / `NACOS_PORT` | Nacos | 跟随 `PATRA_INFRA_HOST`、`8848` |
| `NACOS_USERNAME` / `NACOS_PASSWORD` | Nacos 应用账号 `patra-app` | 无，来自 `~/.patra/secrets/nacos.env`（容器经 `env_file`，dev 经 `spring.config.import`） |
```

`patra-api/patra-object-storage/README.md` 第 2 步的代码块里，删掉 `export NACOS_USERNAME=nacos`、`export NACOS_PASSWORD=nacos  # 生产必须改密` 两行，换成一行：

```bash
# Nacos 账号来自 ~/.patra/secrets/nacos.env，dev profile 自动导入，不用设
```

- [ ] **Step 5: 检查残留说法**

```bash
git grep -nE 'NACOS_PASSWORD=nacos|NACOS_USERNAME=nacos|password=nacos|NACOS_AUTH_\* 三件套在 \.env' \
  -- ':!docs/patra/specs' ':!docs/patra/plans' ':!docs/patra/runbooks' ':!.claude'; echo "exit=$?"
```

Expected：`exit=1`。`.claude/skills/.../configuration.md` 里的默认值写法由 PAP-77 统一改（spec 第 14 节），这里不动。

- [ ] **Step 6: 提交**

```bash
git add patra-infra/CLAUDE.md patra-infra/docker/README.md CLAUDE.md \
  patra-api/patra-gateway-boot/README.md patra-api/patra-object-storage/README.md
git diff --cached --stat
git commit -F - <<'EOF'
docs(infra): 同步 Nacos 的存储、账号、密钥位置和运维说明 (PAP-71)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
```

---

### Task 8: 分支门禁

**Files:** 无改动。

**Interfaces:**
- Consumes: Task 0～7 的全部改动。
- Produces: 进入最终评审和用户验收的依据。

- [ ] **Step 1: 后端测试**

```bash
L="$(mktemp)"; ./gradlew check > "$L" 2>&1; echo "check exit=$?"; tail -3 "$L"
L2="$(mktemp)"
./gradlew :patra-api:patra-gateway-boot:integrationTest :patra-api:patra-identity:patra-identity-boot:integrationTest \
  > "$L2" 2>&1; echo "integrationTest exit=$?"; tail -3 "$L2"
```

Expected：两个 `exit=0`，日志末尾都是 `BUILD SUCCESSFUL`。`check` 不包含 `integrationTest`，所以两条都要跑（spec 11.3 第 6 条）。

- [ ] **Step 2: 模块图、CD 路由**

```bash
./gradlew dumpModuleGraph -q && git diff --exit-code patra-infra/cd/module-graph.json && echo "模块图无变化"
bash patra-infra/cd/detect-changes.test.sh | tail -1
for f in patra-infra/docker/docker-compose.core.yaml patra-infra/docker/postgres/init-scripts/03-nacos-schema.sh \
         patra-infra/docker/postgres/init-scripts/nacos/pg-schema-3.2.4.sql patra-infra/docker/secrets/nacos.env.example \
         patra-infra/scripts/nacos-init.sh patra-infra/docker/.env patra-infra/docker/.env.dev \
         patra-infra/docker/docker-compose.apps.yaml patra-infra/docker/.env.common; do
  printf '%-70s %s\n' "$f" "$(printf '%s\n' "$f" | bash patra-infra/cd/detect-changes.sh classify | jq -c '{full_run, backend_units}')"
done
git diff --name-only origin/main...HEAD | bash patra-infra/cd/detect-changes.sh classify | jq -c '{full_run, backend_units}'
```

Expected：
- `模块图无变化`；`通过 N / 失败 0`。
- 前 7 个文件是 `{"full_run":false,"backend_units":[]}`。
- 后 2 个是 `full_run` 为 `true`。
- 最后一行，也就是推送后 CD 要做的事，是全量。

- [ ] **Step 3: 脚本检查**

```bash
shellcheck -x patra-infra/scripts/nacos-lib.sh patra-infra/scripts/nacos-init.sh patra-infra/scripts/nacos-verify.sh \
  patra-infra/docker/postgres/init-scripts/03-nacos-schema.sh && echo "shellcheck 通过"
git ls-files -s patra-infra/scripts/nacos-init.sh patra-infra/scripts/nacos-verify.sh \
  patra-infra/docker/postgres/init-scripts/03-nacos-schema.sh | cut -c1-6 | sort -u
```

Expected：`shellcheck 通过`、`100755`。

- [ ] **Step 4: 仓库里没有密钥**

```bash
SP=<路径>
git grep -nE 'NACOS_AUTH_(TOKEN|IDENTITY_KEY|IDENTITY_VALUE)=[A-Za-z0-9+/=]{5,}'; echo "exit=$?"
git grep -q -F -f <(sed -n -E 's/^NACOS_(AUTH_TOKEN|AUTH_IDENTITY_KEY|AUTH_IDENTITY_VALUE|PASSWORD|PUSH_PASSWORD|ADMIN_PASSWORD)=//p' "$SP"/home/.patra/secrets/*.env); echo "exit=$?"
git log -p origin/main..HEAD | grep -c -F -f <(sed -n -E 's/^NACOS_(AUTH_TOKEN|AUTH_IDENTITY_KEY|AUTH_IDENTITY_VALUE|PASSWORD|PUSH_PASSWORD|ADMIN_PASSWORD)=//p' "$SP"/home/.patra/secrets/*.env)
```

Expected：两个 `exit=1`，然后 `0`。只输出退出码和计数。

只取密码和鉴权值做比对：用户名 `patra-app`、`patra-push` 本来就写在仓库里。

第一部分到此结束。接着对整个分支做最终评审，然后把改动和测试结果交给用户本地验收。

---

# 第二部分：上线

照 spec 第 10 节的顺序执行。每一步改动 mini 之前先向用户确认，并把实际输出记下来，留给上线 8 写回。

本部分的命令里：
- `MINI=linqibin@linqibins-mac-mini`；
- mini 上的仓库在 `~/Projects/patra`；
- 本机访问 mini 的 IP 取 `source ~/.config/zsh/env.zsh` 之后的 `$PATRA_INFRA_HOST`。

### 上线 1：生成密钥文件

- [ ] **Step 1: 确认两边都还没有这些文件**

```bash
ls ~/.patra/secrets/ | grep -c '^nacos'; ssh linqibin@linqibins-mac-mini 'ls ~/.patra/secrets/ | grep -c "^nacos"'
```

Expected：`0`、`0`。不是 0 就停下来问用户，不要覆盖。

- [ ] **Step 2: 本机生成 `nacos.env`、`nacos-push.env`，复制到 mini**

用户确认后执行：

```bash
(umask 077 && mkdir -p ~/.patra/secrets
 printf 'NACOS_USERNAME=patra-app\nNACOS_PASSWORD=%s\n' "$(openssl rand -hex 24)" > ~/.patra/secrets/nacos.env
 printf 'NACOS_PUSH_USERNAME=patra-push\nNACOS_PUSH_PASSWORD=%s\n' "$(openssl rand -hex 24)" > ~/.patra/secrets/nacos-push.env)
scp -p ~/.patra/secrets/nacos.env ~/.patra/secrets/nacos-push.env linqibin@linqibins-mac-mini:.patra/secrets/
```

- [ ] **Step 3: mini 上生成 `nacos-server.env`、`nacos-admin.env`**

用户确认后执行：

```bash
ssh linqibin@linqibins-mac-mini 'umask 077 && mkdir -p ~/.patra/secrets &&
  printf "NACOS_AUTH_TOKEN=%s\nNACOS_AUTH_IDENTITY_KEY=%s\nNACOS_AUTH_IDENTITY_VALUE=%s\n" \
    "$(openssl rand -base64 32)" "$(openssl rand -hex 16)" "$(openssl rand -hex 32)" > ~/.patra/secrets/nacos-server.env &&
  printf "NACOS_ADMIN_PASSWORD=%s\n" "$(openssl rand -hex 24)" > ~/.patra/secrets/nacos-admin.env'
```

请用户自己把 mini 上 `nacos-admin.env` 的值存进密码管理器。Claude 不读取、不打印这个值。

- [ ] **Step 4: 核对**

```bash
for h in local mini; do
  if [ "$h" = local ]; then run() { bash -c "$1"; }; else run() { ssh linqibin@linqibins-mac-mini "$1"; }; fi
  echo "== $h"; run 'cd ~/.patra/secrets && for f in nacos*.env; do printf "%s %s " "$f" "$(stat -f %Lp "$f")";
    awk -F= "{printf \"%s:%d \", \$1, length(\$2)}" "$f"; echo; done'
done
```

Expected：
- 本机两个文件，mini 四个文件，权限都是 `600`。
- 长度：`NACOS_PASSWORD`、`NACOS_PUSH_PASSWORD`、`NACOS_ADMIN_PASSWORD` 都是 48；`NACOS_AUTH_TOKEN` 是 44；`NACOS_AUTH_IDENTITY_KEY` 是 32；`NACOS_AUTH_IDENTITY_VALUE` 是 64。

### 上线 2：在旧服务端上预建账号

- [ ] **Step 1: 核对 mini 现状与 spec 第 3 节一致**

```bash
source ~/.config/zsh/env.zsh
curl -sS --noproxy '*' "http://$PATRA_INFRA_HOST:8848/nacos/v3/admin/core/state" \
  | jq -r '.data | "\(.version) [\(.datasource_platform)] \(.auth_enabled)"'
```

Expected：`3.0.2 [] true`。

- [ ] **Step 2: 运行初始化**

用户确认后执行：

```bash
source ~/.config/zsh/env.zsh
NACOS_ADDR="http://$PATRA_INFRA_HOST:8848" NACOS_CONSOLE_ADDR="http://$PATRA_INFRA_HOST:8080" NACOS_ADMIN_PASSWORD=nacos \
  bash patra-infra/scripts/nacos-init.sh
NACOS_ADDR="http://$PATRA_INFRA_HOST:8848" NACOS_CONSOLE_ADDR="http://$PATRA_INFRA_HOST:8080" NACOS_ADMIN_PASSWORD=nacos \
  bash patra-infra/scripts/nacos-verify.sh
```

Expected：
- 初始化输出与 Task 5 第 2 步相同。
- 验收只有第 1、2 项 FAIL，`通过 8 / 失败 2`。

- [ ] **Step 3: 记下旧管理员令牌**

```bash
SP=<路径>; source ~/.config/zsh/env.zsh; W="$(git rev-parse --show-toplevel)"
(umask 077; NACOS_ADDR="http://$PATRA_INFRA_HOST:8848" bash -c '. "$1/patra-infra/scripts/nacos-lib.sh"
  nacos_login nacos nacos && printf "%s" "$NACOS_TOKEN" > "$2" && echo "已记下，有效期 $(jq -r .tokenTtl <<<"$NACOS_BODY") 秒"' \
  _ "$W" "$SP/old-admin-token")
date '+%H:%M'
```

Expected：`已记下，有效期 18000 秒`，并记下当前时刻。上线 7 要在 5 小时内完成，否则旧令牌自己就过期了，检查就失去意义。

### 上线 3：本机冒烟

用 dev profile 在本机起 registry，确认它用 `patra-app` 注册到 mini 的旧服务端。

- [ ] **Step 1: 起 registry**

先确认端口空着：`lsof -nP -iTCP:6000 -sTCP:LISTEN`，预期没有输出。有输出可能是用户自己在 IDEA 里起的：停下来问用户，不要杀。

用 Bash 工具的 `run_in_background` 起：

```bash
source ~/.config/zsh/env.zsh
export JAVA_TOOL_OPTIONS="-Dhttp.nonProxyHosts=localhost|127.*|100.*|192.168.*|172.*|10.* -Dhttps.nonProxyHosts=localhost|127.*|100.*|192.168.*|172.*|10.* -Dsocks.nonProxyHosts=localhost|127.*|100.*|192.168.*|172.*|10.*"
exec java -jar patra-api/patra-registry/patra-registry-boot/build/libs/patra-registry-boot-0.1.0-SNAPSHOT.jar \
  --spring.profiles.active=dev > "${TMPDIR:-/tmp}/pap71-registry.log" 2>&1
```

- [ ] **Step 2: 确认注册成功**

```bash
for i in $(seq 1 60); do grep -q 'Started PatraRegistryApplication' "${TMPDIR:-/tmp}/pap71-registry.log" && break; sleep 2; done
grep -ho 'Started PatraRegistryApplication in [0-9.]* seconds' "${TMPDIR:-/tmp}/pap71-registry.log"
grep -ciE 'user not found|unknown user|403|authorization failed' "${TMPDIR:-/tmp}/pap71-registry.log"
source ~/.config/zsh/env.zsh; W="$(git rev-parse --show-toplevel)"
NACOS_ADDR="http://$PATRA_INFRA_HOST:8848" bash -c '. "$1/patra-infra/scripts/nacos-lib.sh"
  nacos_load NACOS_USERNAME nacos.env; nacos_load NACOS_PASSWORD nacos.env; nacos_login "$NACOS_USERNAME" "$NACOS_PASSWORD"
  nacos_api GET /v3/admin/ns/instance/list "$NACOS_TOKEN" namespaceId=public groupName=DEFAULT_GROUP serviceName=patra-registry
  jq -r ".data[] | \"\(.ip):\(.port)\"" <<<"$NACOS_BODY"' _ "$W"
echo "本机 TAILSCALE_IP=$TAILSCALE_IP"
```

Expected：
- 一行 `Started PatraRegistryApplication in ...`。
- 鉴权错误计数 `0`。
- 实例列表里有一行 `$TAILSCALE_IP:6000`（本机），另有 mini 容器的那一行。

- [ ] **Step 3: 停掉 registry**

Run: `pkill -f 'patra-registry-boot-0.1.0-SNAPSHOT.jar'; sleep 3; pgrep -f 'patra-registry-boot-0.1.0-SNAPSHOT.jar' || echo "已停"`

Expected：`已停`。正常停止时，进程会从 Nacos 注销。

### 上线 4：推送 main，CD 部署

- [ ] **Step 1: 对齐最新的 origin/main**

```bash
git fetch origin main && git rebase origin/main && git log --oneline origin/main..HEAD
```

Expected：rebase 成功，列出本分支的提交（Task 0～7 和 Task 5 的写回）。

如果 rebase 带进了别人的改动，重跑 Task 8 第 2、4 步再继续。

- [ ] **Step 2: 推送**

把提交列表给用户看。等用户在当轮明确说推送后，把当前分支推到远端 main（`HEAD:main`）。

- [ ] **Step 3: 看 CD 并核对**

```bash
gh run list --workflow cd.yml -L 1 --json databaseId,status,headSha -q '.[0]'
```

用 `run_in_background` 跑 `gh run watch <databaseId> --exit-status`，完成后会收到通知。

完成后核对：

```bash
source ~/.config/zsh/env.zsh
ssh linqibin@linqibins-mac-mini 'docker ps --filter name=patra- --format "{{.Names}} {{.Image}} {{.Status}}"' \
  | grep -E 'registry|object-storage|catalog|ingest|identity|gateway'
NACOS_ADDR="http://$PATRA_INFRA_HOST:8848" NACOS_CONSOLE_ADDR="http://$PATRA_INFRA_HOST:8080" NACOS_ADMIN_PASSWORD=nacos \
  bash patra-infra/scripts/nacos-verify.sh --expect-services | grep -E 'FAIL|PASS 11|^通过'
```

Expected：
- CD 成功，6 个后端容器的镜像 tag 是本次推送的提交 sha，状态 `healthy`。
- 验收只有第 1、2 项 FAIL，第 11 项 PASS（6 个服务都用 `patra-app` 注册到了旧服务端），`通过 9 / 失败 2`。

CD 失败或服务没注册上：deploy.sh 会自动回滚失败的服务。停下来报告用户，不要进入上线 5。

### 上线 5：mini 上切换服务端

每个小步都先向用户确认。

- [ ] **Step 1: 更新 mini 的检出**

```bash
ssh linqibin@linqibins-mac-mini 'cd ~/Projects/patra && git pull --ff-only && git log --oneline -1'
```

Expected：快进到刚推送的提交。

- [ ] **Step 2: 建库建表**

```bash
ssh linqibin@linqibins-mac-mini "docker exec patra-postgres psql -U postgres -c 'CREATE DATABASE patra_nacos;' &&
  docker exec patra-postgres bash /docker-entrypoint-initdb.d/03-nacos-schema.sh"
```

Expected：`CREATE DATABASE`、`[nacos-schema] patra_nacos 是空库，开始建表（13 张）`、`[nacos-schema] 建表完成`。

- [ ] **Step 3: 预演**

```bash
ssh linqibin@linqibins-mac-mini 'cd ~/Projects/patra/patra-infra/docker &&
  docker compose -f docker-compose.core.yaml up -d --dry-run 2>&1 | grep -E "Recreate|Create|Running"'
```

Expected：只有 `patra-nacos` 是 `Recreate`，`patra-postgres`、`patra-redis` 是 `Running`。否则停下来报告用户。

- [ ] **Step 4: 停旧服务端，清空旧数据，起新服务端**

清空数据目录会删掉旧的 Derby 和 jraft 数据，spec 第 3 节确认没有要迁移的数据。执行前单独向用户确认这一步：

```bash
ssh linqibin@linqibins-mac-mini 'cd ~/Projects/patra/patra-infra/docker &&
  docker compose -f docker-compose.core.yaml stop nacos && docker compose -f docker-compose.core.yaml rm -f nacos &&
  rm -rf ~/.patra/docker/nacos/data && mkdir -p ~/.patra/docker/nacos/data &&
  cd ~/Projects/patra && bash patra-infra/scripts/compose-all.sh up core'
for i in $(seq 1 60); do
  s="$(ssh linqibin@linqibins-mac-mini 'docker inspect -f "{{.State.Health.Status}}" patra-nacos')"
  [ "$s" = healthy ] && break; sleep 5
done; echo "patra-nacos: $s"
```

Expected：`patra-nacos: healthy`。

起不来时，看 `docker logs patra-nacos` 的末尾，用 `grep -v NACOS_AUTH` 过滤后再看（`set -x` 会打出密钥）。看完停下来报告用户。

从这一步到上线 6 结束，服务发现不可用，网关找不到下游。这在 dev 环境可以接受。

### 上线 6：初始化，重启应用

- [ ] **Step 1: 初始化**

```bash
ssh linqibin@linqibins-mac-mini 'cd ~/Projects/patra && bash patra-infra/scripts/nacos-init.sh'
```

Expected：`管理员 nacos 已初始化`、`namespace dev 已创建`，其余与 Task 4 第 2 步相同，最后是 `完成`。

- [ ] **Step 2: 重启 6 个应用容器**

```bash
ssh linqibin@linqibins-mac-mini 'docker restart patra-registry patra-object-storage patra-catalog patra-ingest patra-identity patra-gateway'
```

`nacos.env` 没变，`docker restart` 就够了，不用重建。

- [ ] **Step 3: 本机进程**

```bash
for p in 6000 6100 6200 6300 6400 9528; do lsof -nP -iTCP:$p -sTCP:LISTEN 2>/dev/null | tail -n +2; done
```

有输出，就请用户在 IDEA 里重启对应的服务。重启后它们会从 `nacos.env` 取账号。

### 上线 7：验收（对应 PAP-71 的 AC）

- [ ] **Step 1: 验收脚本**

等 6 个容器都 `healthy` 后执行：

```bash
ssh linqibin@linqibins-mac-mini 'cd ~/Projects/patra && bash patra-infra/scripts/nacos-verify.sh --expect-services'
```

Expected：11 项 PASS，`通过 11 / 失败 0`。

- [ ] **Step 2: 旧令牌被拒，数据在 PG**

```bash
SP=<路径>; source ~/.config/zsh/env.zsh
curl -sS --noproxy '*' -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $(cat "$SP/old-admin-token")" \
  "http://$PATRA_INFRA_HOST:8848/nacos/v3/admin/core/namespace/list"
ssh linqibin@linqibins-mac-mini "docker exec patra-postgres psql -U postgres -d patra_nacos -Atc 'SELECT count(*) FROM users'"
rm -f "$SP/old-admin-token"
```

Expected：`403`，`3`。

- [ ] **Step 3: 健康状态（spec 第 12 节第 4 条）**

```bash
ssh linqibin@linqibins-mac-mini 'for p in 6000 6100 6200 6300 6400 9528; do
  printf "%s " $p; curl -s http://127.0.0.1:$p/actuator/health | jq -r .status; done'
```

Expected：6 行都是 `UP`。

- [ ] **Step 4: 经 mini 的网关走一遍**

```bash
source ~/.config/zsh/env.zsh
GW="http://$PATRA_INFRA_HOST:9528/patra-identity"
code() { curl --noproxy '*' -s -o /dev/null -w '%{http_code}' "$@"; }
EMAIL="pap71-smoke-$(date +%s)@example.com"; PW="$(openssl rand -hex 12)"
R="$(curl --noproxy '*' -s -w '\n%{http_code}' -X POST "$GW/auth/register" -H 'Content-Type: application/json' \
  -d "{\"email\":\"${EMAIL}\",\"password\":\"${PW}\",\"clientType\":\"web\"}")"
echo "注册：$(tail -n1 <<<"$R")"; T="$(head -n1 <<<"$R" | jq -r .sessionToken)"
echo "带令牌 /auth/me：$(code -H "Authorization: Bearer ${T}" "$GW/auth/me")"
echo "登出：$(code -X POST -H "Authorization: Bearer ${T}" "$GW/auth/logout")"
```

Expected：`201`、`200`、`204`。得到 503 就等 10 秒再跑：网关的实例列表还没刷新。

然后请用户在浏览器里打开门户，浏览一下文献页、登录一次。这一步由用户确认。

- [ ] **Step 5: 仓库里没有鉴权密钥（AC 第 2 条）**

```bash
git fetch origin main
git grep -nE 'NACOS_AUTH_(TOKEN|IDENTITY_KEY|IDENTITY_VALUE)=[A-Za-z0-9+/=]{5,}' origin/main; echo "exit=$?"
git grep -nE '^NACOS_AUTH_TOKEN=' origin/main
```

Expected：
- `exit=1`：main 上没有任何非空的鉴权值。
- 只有一行 `origin/main:patra-infra/docker/secrets/nacos-server.env.example:...:NACOS_AUTH_TOKEN=`，值为空。

### 上线 8：写回与收尾

- [ ] **Step 1: 写回 spec**

- 第 12 节第 4 条写成结论：切换后 6 个服务的 actuator health 都是 `UP`，没有触发 CD 回滚。
- 文件头的「状态」改为 `已上线（2026-10-xx）`，填实际日期。

```bash
git add docs/patra/specs/2026-10-11-nacos-hardening-design.md
git commit -F - <<'EOF'
docs(infra): 写回 Nacos 切换上线的实测结果 (PAP-71)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
```

这个提交留在分支上，随 v0.9 后端 PR 合入，不单独推送。

- [ ] **Step 2: Linear**

征得用户同意后，把 PAP-71 置为 Done，并在评论里附上：上线 7 的验收结果，以及推送到 main 的提交 sha。
