# identity 部署接入与密钥注入 实施计划（PAP-66）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 identity 和带鉴权的网关在 mini 上跑起来。密钥放到仓库外，Redis 加密码，CD 能部署 identity，然后按设计的顺序上线并验收。

**Architecture:**
- 密钥文件放在两台机器的 `~/.patra/secrets/`。容器由 compose 按 `${HOME}` 绝对路径加载；本机进程由 dev profile 的 `spring.config.import` 读同一批文件。
- catalog、identity、gateway 三个 Redis 客户端改成「共享地址、用户名 `default`、共享密码」。这样认证在 Redis 加密码前后都能成功，所以可以先部署、后给 Redis 加密码。
- `deploy.sh` 改为按 `services.json` 的条目顺序部署，并加上 identity 条目。

**Tech Stack:** Docker Compose 5.1.2、Bash（`deploy.sh` 的 stub 单测）、Spring Boot 4.0.8 config data（`spring.config.import`）、Lettuce、Redisson 4.1.0、Redis 7.0.15、GitHub Actions（mini 自托管 runner）。

**Spec:** `docs/patra/specs/2026-10-10-identity-deploy-design.md`

## Global Constraints

- **密钥不进仓库。** 仓库是公开的。Redis 密码和签名私钥只放在 `~/.patra/secrets/`（目录 0700，文件 0600）。
  - 绝不进仓库，不打印到终端，不写进文档和提交信息。
  - 检查密钥时只输出计数、退出码、哈希或文件属性。
  - 公钥不是机密，提交在三处：`.env.common`，以及 identity、gateway 的 `application-dev.yml`。
- **本机开发和 mini 是同一个环境。** 两边共用 Redis 的 0 号库、一个 Redis 密码（设在 `default` 用户上）、一对签名密钥（spec 第 4 节决定 1、2）。
- **Redis 客户端配置。**
  - catalog、identity、gateway 的连接写成 `host`、`port`、`username: default`、`password` 四项，不用 `spring.data.redis.url`。
  - 这四项只写在 dev、container 两个 profile 里。它们和公钥都不写进 `application.yml`。
  - 超时设置不动。
- **健康检查保留 Redis。** gateway、identity 不加 `management.health.redis.enabled=false`（spec 第 4 节决定 3）。
- **密钥文件哪些必需。**
  - `redis.env` 必需：core 栈的 redis，以及 catalog、identity、gateway。
  - `gateway.env` 必需：gateway。
  - `<服务>.env` 可选。
  - 本机 dev 的导入不加 `optional:`。
- **mini 上的操作。** 改变 mini 状态的每一步，执行前先向用户确认；只读检查不用确认。推送 main 要等用户开口。
- **提交。**
  - 提交信息格式是 `type(scope): 中文开头的 subject (PAP-66)`。body 每行不超过 100 个字符，最后一行是 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。
  - 只暂存本任务列出的路径，提交前看一眼 `git diff --cached --stat`。
  - 本计划不改 `*.gradle.kts`，所以不用提醒「加载 Gradle 更改」。
- **命令环境。**
  - 命令都在仓库根目录执行，Bash 工具里不要 `cd`。
  - macOS 没有 `timeout`；本机的 `grep` 是 ugrep。
- **本机起连 mini 的 JVM。**
  - 先 `source ~/.config/zsh/env.zsh`，再把 `JAVA_TOOL_OPTIONS` 设成完整的 nonProxyHosts（命令见上线 3）。
  - Nacos 账号只从 `patra-infra/docker/.env.common` 取 `NACOS_USERNAME`、`NACOS_PASSWORD` 两行。不要 source 整个文件，否则会把 container profile 一起带进来。

## Review Focus

| # | 输入或故障 | 期望 | 由哪一步钉住 |
|---|---|---|---|
| 1 | 本机 dev 配置的 `spring.config.import` 没有被处理 | 缺文件时启动失败，并报出缺的文件路径 | Task 5 第 7 步 |
| 2 | 公钥 JSON 写进 YAML 不加引号会被解析成映射，写进 compose 环境文件可能被改写 | 三处的值与生成的 JWK Set 逐字相同 | Task 4 第 8 步、Task 5 第 6 步 |
| 3 | `REDIS_PASSWORD` 为空，`--requirepass ""` 让 `default` 回到无密码 | 容器以 1 退出并打出提示 | Task 3 第 2 步 |
| 4 | 加密码后 `redis-cli ping` 被拒时退出码仍为 0，健康检查会误报 healthy | 没带密码时检查失败，带密码时成功 | Task 3 第 2 步 |
| 5 | 必需的密钥文件缺失 | compose 拒绝执行（apps 项目含 portal，core 项目也一样），并报出完整路径 | Task 4 第 8 步 |

第 1 条要钉住，是因为导入没生效时，配置里的 `${REDIS_PASSWORD}` 会原样成为密码。mini 的 Redis 在加密码之前接受任何密码，这个问题会一直藏到加密码那一刻才暴露。

## 写计划时已实测的点（2026-10-10）

| 实测的点 | 结果 |
|---|---|
| spec 第 13 节第 1 条：`application-dev.yml`（`on-profile: dev`）里的 `spring.config.import` | 用现有的 identity 启动包实测成立：导入会被处理，`${user.home}` 会展开，`[.properties]` 能读 `.env` 文件，导入的值能用在占位符里 |
| 导入的文件缺失时 | 报 `Config data resource 'file [...]' via location 'file:...[.properties]' does not exist` |
| `docker compose config` 的输出 | 保留 `$$`，没有替换成 `$` |
| 临时改 `HOME` 执行 compose | 要带上 `DOCKER_CONFIG="$HOME/.docker"`（在改之前展开），才找得到 compose 插件 |
| 缺必需的 `env_file` | 报 `env file <完整路径> not found` |
| `--dry-run up` | 没变的容器打 `Running`，要重建的打 `Recreate` |
| Redis 运行中执行 `CONFIG SET requirepass ""` | `default` 回到 `nopass`；从无密码状态执行 `CONFIG SET requirepass "$REDIS_PASSWORD"` 能恢复 |

这些结果在上线 6 写回 spec 第 13 节。

## 与 spec 的出入

| 出入 | 理由 |
|---|---|
| catalog `application.yml` 第 75 行注释里的 `.env.catalog.secret` 一并改成 `~/.patra/secrets/catalog.env` | spec 第 10 节没列这一处，但它是同一次改名的直接后果 |
| `patra-infra/CLAUDE.md` 除第 14 节列的几处以外，还改 `deploy.sh` 顺序那一句，以及「共享分层 Dockerfile」里的服务个数 | 这两处同样随本 Issue 变化 |
| spec 第 12.1 节说的「catalog-boot 集成测试」用它的 `e2eTest` 代替 | catalog-boot 没有 `integrationTest` |
| spec 第 12.2 节「把 `redis.env` 临时改名再起 identity」，改成 Task 5 用空的 `user.home` 起三个启动包 | 证明的是同一件事，而且不碰真密钥文件 |
| spec 第 12.3 节的「Nacos 实例列表里有 patra-identity」，改为核对 identity 容器日志里 Nacos 的 `register finished` | 等价，而且不用为 Nacos 鉴权去取令牌 |

## 文件结构

| 文件 | 动作 | 任务 |
|---|---|---|
| `patra-infra/cd/services.json` | 改：加 identity 条目，按部署顺序重排，改 `_comment` | 1 |
| `patra-infra/cd/deploy.sh` | 改：部署顺序取自 `services.json` | 1 |
| `patra-infra/cd/deploy.test.sh` | 改：加场景 6 | 1 |
| `patra-infra/docker/secrets/redis.env.example`、`gateway.env.example` | 新建 | 2 |
| `patra-infra/docker/.gitignore` | 改 | 2 |
| `patra-infra/docker/.env.registry.secret.example` | 删 | 2 |
| `patra-infra/scripts/init-volumes.sh` | 改：建 `~/.patra/secrets` | 2 |
| `patra-infra/docker/docker-compose.core.yaml` | 改：redis | 3 |
| `patra-infra/docker/docker-compose.apps.yaml` | 改：新增 identity，改 `env_file`，改头部注释 | 4 |
| `patra-infra/docker/.env.common` | 改 | 4 |
| `patra-infra/docker/.env.identity` | 新建 | 4 |
| `patra-infra/docker/.env.catalog`、`.env.registry`、`.env.ingest`、`.env.gateway`、`.env.object-storage`、`.env.portal` | 改 | 4 |
| `patra-infra/docker/postgres/init-scripts/02-create-databases.sql` | 改 | 4 |
| gateway、identity 的 `application-dev.yml`、`application-container.yml` | 改 | 5 |
| catalog 的 `application.yml`、`application-dev.yml`、`application-container.yml` | 改 | 5 |
| registry、ingest 的 `application-dev.yml`、`application-container.yml` | 改 | 5 |
| `.run/PatraIdentityApplication.run.xml` | 新建 | 5 |
| `docs/patra/runbooks/v0.8-accounts-go-live-runbook.md` | 新建 | 6 |
| `patra-infra/docker/README.md`、`patra-infra/CLAUDE.md`、`.claude/rules/project-info.md` | 改 | 6 |
| `README.md`、`patra-api/README.md`、identity 和 gateway 的 `README.md` | 改 | 6 |
| `docs/patra/release-specs/v0.8-accounts.md`、`docs/patra/specs/2026-10-09-gateway-auth-design.md` | 改 | 6 |
| `docs/patra/specs/2026-10-10-identity-deploy-design.md`、`docs/patra/specs/2026-10-05-identity-account-design.md` | 改：写回实测结果 | 上线 6 |

本机另外产生、不进仓库的文件：

- `~/.patra/secrets/redis.env`、`~/.patra/secrets/gateway.env`
- `/tmp/patra-identity-assertion-public.jwks`（公钥，不是机密）

## 执行顺序

1. **第一部分：Task 1～7。** 在分支 `feat/v0.8-accounts-api` 上完成，可以连续执行。
2. **最终评审。** 第一部分完成后，按 executing-plans 做整分支的最终评审，并修掉评审意见。
3. **第二部分：上线 1～6。** 这部分是对 mini 的操作。
   - 不走连续执行，每一步改动 mini 之前都先向用户确认。
   - 上线 4 先在本地合并，再等用户开口推送。

---

# 第一部分：分支上的改动

### Task 1: CD 按 services.json 的条目顺序部署，并加上 identity 条目

**Files:**
- Modify: `patra-infra/cd/services.json`（整份重写）
- Modify: `patra-infra/cd/deploy.sh:12`、`patra-infra/cd/deploy.sh:106-107`
- Test: `patra-infra/cd/deploy.test.sh`（加场景 6）

**Interfaces:**
- Consumes: 无
- Produces:
  - `services.json` 的条目顺序是 `object-storage registry identity gateway catalog ingest portal learn`。
  - identity 条目：`name: identity`、`port: 6400`、`image: ghcr.io/linqibin0826/patra-identity`。`deploy.sh` 由服务名推出的镜像变量是 `IDENTITY_IMAGE_TAG`，Task 4 的 compose 服务名也必须叫 `identity`。

- [ ] **Step 1: 重写 services.json**

把 `patra-infra/cd/services.json` 整份改成：

```json
{
  "_comment": "后端服务 CD 单一事实源（SSOT）。cd.yml 的 detect-changes / build / deploy 与 deploy.sh 都从这里按 name 查询，代码里不写死服务清单。加新服务=加一个条目，不改 workflow 逻辑。条目顺序就是 deploy.sh 的部署顺序（object-storage 最先：catalog / ingest 运行时依赖它；identity 先于 gateway）。healthPath/healthMatch 供 deploy.sh 健康检查（healthMatch 缺省=仅要求 HTTP 成功）。portal / learn 条目为 deploy-only（gradleTask=null，构建在 portal-cd.yml / learn-cd.yml 的 docker build 内完成，勿在 cd.yml dispatch 填 portal/learn）。srcPrefix 保留供人工查阅路径归属；context 是 docker build 上下文（boot 模块，build/libs 所在）。",
  "services": [
    {
      "name": "object-storage",
      "gradleTask": ":patra-api:patra-object-storage:patra-object-storage-boot:bootJar",
      "context": "patra-api/patra-object-storage/patra-object-storage-boot",
      "srcPrefix": "patra-api/patra-object-storage/",
      "port": 6200,
      "image": "ghcr.io/linqibin0826/patra-object-storage",
      "healthPath": "/actuator/health",
      "healthMatch": "\"status\":\"UP\""
    },
    {
      "name": "registry",
      "gradleTask": ":patra-api:patra-registry:patra-registry-boot:bootJar",
      "context": "patra-api/patra-registry/patra-registry-boot",
      "srcPrefix": "patra-api/patra-registry/",
      "port": 6000,
      "image": "ghcr.io/linqibin0826/patra-registry",
      "healthPath": "/actuator/health",
      "healthMatch": "\"status\":\"UP\""
    },
    {
      "name": "identity",
      "gradleTask": ":patra-api:patra-identity:patra-identity-boot:bootJar",
      "context": "patra-api/patra-identity/patra-identity-boot",
      "srcPrefix": "patra-api/patra-identity/",
      "port": 6400,
      "image": "ghcr.io/linqibin0826/patra-identity",
      "healthPath": "/actuator/health",
      "healthMatch": "\"status\":\"UP\""
    },
    {
      "name": "gateway",
      "gradleTask": ":patra-api:patra-gateway-boot:bootJar",
      "context": "patra-api/patra-gateway-boot",
      "srcPrefix": "patra-api/patra-gateway-boot/",
      "port": 9528,
      "image": "ghcr.io/linqibin0826/patra-gateway",
      "healthPath": "/actuator/health",
      "healthMatch": "\"status\":\"UP\""
    },
    {
      "name": "catalog",
      "gradleTask": ":patra-api:patra-catalog:patra-catalog-boot:bootJar",
      "context": "patra-api/patra-catalog/patra-catalog-boot",
      "srcPrefix": "patra-api/patra-catalog/",
      "port": 6300,
      "image": "ghcr.io/linqibin0826/patra-catalog",
      "healthPath": "/actuator/health",
      "healthMatch": "\"status\":\"UP\""
    },
    {
      "name": "ingest",
      "gradleTask": ":patra-api:patra-ingest:patra-ingest-boot:bootJar",
      "context": "patra-api/patra-ingest/patra-ingest-boot",
      "srcPrefix": "patra-api/patra-ingest/",
      "port": 6100,
      "image": "ghcr.io/linqibin0826/patra-ingest",
      "healthPath": "/actuator/health",
      "healthMatch": "\"status\":\"UP\""
    },
    {
      "name": "portal",
      "gradleTask": null,
      "context": "patra-portal",
      "srcPrefix": "patra-portal/",
      "port": 4000,
      "image": "ghcr.io/linqibin0826/patra-portal",
      "healthPath": "/api/health"
    },
    {
      "name": "learn",
      "gradleTask": null,
      "context": "patra-learn",
      "srcPrefix": "patra-learn/",
      "port": 4001,
      "image": "ghcr.io/linqibin0826/patra-learn",
      "healthPath": "/api/health"
    }
  ]
}
```

Run: `jq -r '.services[].name' patra-infra/cd/services.json | xargs`
Expected: `object-storage registry identity gateway catalog ingest portal learn`

- [ ] **Step 2: 写失败的测试：场景 6**

在 `patra-infra/cd/deploy.test.sh` 头部注释的场景 5 那一行后面，加一行：

```bash
#   场景6 按 services.json 的条目顺序部署：请求的服务一个不漏，identity 先于 gateway
```

在 `echo "----"` 前面插入：

```bash
# ---- 场景6：按 services.json 的条目顺序部署，请求的服务一个不漏 ----
setup
for s in gateway identity object-storage; do
  echo "ghcr.io/linqibin0826/patra-$s:newsha" >> "$STUB_IMAGES"
done
echo newsha >> "$STUB_HEALTHY"
bash "$SCRIPT_DIR/deploy.sh" newsha '["gateway","identity","object-storage"]' > "$TMP/out" 2>&1; rc=$?
check "场景6 按 services.json 顺序部署" 0 "$rc" \
  '[ "$(sed -n "s/.* up -d //p" "$STUB_LOG" | xargs)" = "object-storage identity gateway" ]' \
  '[ "$(cat "$LAST_GOOD_DIR/last-good-object-storage")" = newsha ]' \
  '[ "$(cat "$LAST_GOOD_DIR/last-good-identity")" = newsha ]' \
  '[ "$(cat "$LAST_GOOD_DIR/last-good-gateway")" = newsha ]'

```

- [ ] **Step 3: 跑测试，确认场景 6 失败**

Run: `bash patra-infra/cd/deploy.test.sh; echo "exit=$?"`

Expected:
- 场景 1 到 5 打 `✓`。
- `✗ 场景6 按 services.json 顺序部署`，有两条断言失败：
  - 部署顺序断言失败，实际只部署了 `object-storage gateway`；
  - `last-good-identity` 断言失败，cat 报文件不存在。
- 最后是 `PASS=5 FAIL=1`、`exit=1`。

失败的原因：`deploy.sh` 写死的 `ORDER` 里没有 identity，它被静默跳过了。

- [ ] **Step 4: 改 deploy.sh**

第 12 行的

```bash
#   3. 按依赖顺序 compose up（object-storage 优先，catalog/ingest 运行时依赖它）
```

改成

```bash
#   3. 按 services.json 的条目顺序 compose up（object-storage 最先：catalog/ingest 运行时依赖它）
```

第 106～107 行的

```bash
mkdir -p "$LAST_GOOD_DIR"
ORDER='object-storage registry gateway catalog ingest portal learn'
```

改成

```bash
mkdir -p "$LAST_GOOD_DIR"
# 部署顺序只取 services.json 的条目顺序：请求部署的服务只要在 services.json 里就一定会部署，
# 不会因为漏改另一份清单而被静默跳过（不在 services.json 里的服务已被上面的入参校验拒绝）
ORDER="$(jq -r '.services[].name' "$SERVICES_FILE")"
```

- [ ] **Step 5: 跑测试，确认全部通过**

Run: `bash patra-infra/cd/deploy.test.sh && shellcheck patra-infra/cd/deploy.sh patra-infra/cd/deploy.test.sh && bash patra-infra/cd/detect-changes.test.sh | tail -1`

Expected:
- `deploy.test.sh` 的 6 个场景都打 `✓`，最后是 `PASS=6 FAIL=0`。
- shellcheck 没有输出。
- 最后一行是 `通过 44 / 失败 0`。

- [ ] **Step 6: 提交**

```bash
git add patra-infra/cd/services.json patra-infra/cd/deploy.sh patra-infra/cd/deploy.test.sh
git diff --cached --stat
git commit -F - <<'EOF'
fix(cd): 部署顺序改取 services.json 的条目顺序，补上 identity 条目 (PAP-66)

detect-changes.sh 的 ALL_UNITS 已含 identity，services.json 却没有它的条目，
main 上的 CD 自 10/09 起每次失败在构建镜像；deploy.sh 写死的 ORDER 也没有
identity，只补条目它仍会被静默跳过。部署顺序现在只取 services.json 一处。

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
```

---

### Task 2: 密钥目录、模板和忽略规则，本机生成密钥

**Files:**
- Create: `patra-infra/docker/secrets/redis.env.example`
- Create: `patra-infra/docker/secrets/gateway.env.example`
- Modify: `patra-infra/docker/.gitignore`（整份重写）
- Delete: `patra-infra/docker/.env.registry.secret.example`
- Modify: `patra-infra/scripts/init-volumes.sh`
- 本机生成，不提交：`~/.patra/secrets/redis.env`、`~/.patra/secrets/gateway.env`、`/tmp/patra-identity-assertion-public.jwks`

**Interfaces:**
- Consumes: 无
- Produces:
  - `~/.patra/secrets/redis.env`：一行 `REDIS_PASSWORD=<64 位十六进制>`。
  - `~/.patra/secrets/gateway.env`：一行 `PATRA_GATEWAY_IDENTITY_ASSERTION_PRIVATE_KEY=<含私钥的 JWK JSON>`。
  - `/tmp/patra-identity-assertion-public.jwks`：一行公钥 JWK Set JSON，末尾有换行。Task 4、5 从这个文件取公钥。

- [ ] **Step 1: 新建 redis.env 的模板**

`patra-infra/docker/secrets/redis.env.example`：

```bash
# Redis 密码（default 用户）。真文件在仓库外：~/.patra/secrets/redis.env，目录 0700、文件 0600。
# MacBook 和 mini 放同一份：本机开发和 mini 是同一个环境，共用 mini 的 Redis。
# 谁读：core 栈的 redis（启动时设密码）；catalog、identity、gateway 的容器（compose env_file）
#       和这三个服务在本机以 dev profile 起的进程（application-dev.yml 的 spring.config.import）。
#
# 生成（只写文件，不打印到终端）：
#   umask 077 && mkdir -p ~/.patra/secrets
#   printf 'REDIS_PASSWORD=%s\n' "$(openssl rand -hex 32)" > ~/.patra/secrets/redis.env
# 复制到 mini：scp -p ~/.patra/secrets/redis.env mini:.patra/secrets/
#
# 值只用十六进制：这个文件同时按 compose 的 env_file 和 Java properties 解析。
# 留空时 redis 拒绝启动。
REDIS_PASSWORD=
```

- [ ] **Step 2: 新建 gateway.env 的模板**

`patra-infra/docker/secrets/gateway.env.example`：

```bash
# 网关签身份断言的私钥：含私钥的 EC P-256 JWK JSON，一行，带 kid。
# 真文件在仓库外：~/.patra/secrets/gateway.env，目录 0700、文件 0600。MacBook 和 mini 放同一份。
# 谁读：gateway 的容器（compose env_file）和本机以 dev profile 起的网关（spring.config.import）。
# 对应的公钥不是机密，提交在三处：.env.common 的 PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS，
# 以及 identity、gateway 的 application-dev.yml。
#
# 生成（在仓库根目录执行；私钥只写文件，不打印到终端）：
#   umask 077 && mkdir -p ~/.patra/secrets
#   K="$(mktemp -u ~/.patra/secrets/key.XXXXXX)"
#   ./gradlew -q :patra-starters:patra-spring-boot-starter-security:generateIdentityAssertionKey -PkeyOut="$K" \
#     | tail -1 > /tmp/patra-identity-assertion-public.jwks
#   { printf 'PATRA_GATEWAY_IDENTITY_ASSERTION_PRIVATE_KEY='; cat "$K"; } > ~/.patra/secrets/gateway.env
#   rm -f "$K"
# 复制到 mini：scp -p ~/.patra/secrets/gateway.env mini:.patra/secrets/
# 换密钥的顺序见 patra-infra/docker/README.md「密钥」。
PATRA_GATEWAY_IDENTITY_ASSERTION_PRIVATE_KEY=
```

- [ ] **Step 3: 重写 .gitignore**

`patra-infra/docker/.gitignore` 整份改成：

```gitignore
# 真实敏感密钥：只存在于运行容器的机器上，绝不提交到公开仓库

# tailscale 网关的 TS_AUTHKEY（模板见 .env.secret.example）
.env.secret

# 应用和 Redis 的密钥放在仓库外的 ~/.patra/secrets/（见 README「密钥」）。
# secrets/ 下只放模板；其余文件一律忽略，防止有人把真文件放进工作区
secrets/*
!secrets/*.example

# 兜底：旧做法的 .env.<服务>.secret 已不再使用，误放进来也不会被提交
.env.*.secret
```

- [ ] **Step 4: 核对忽略规则**

Run:

```bash
git check-ignore -v patra-infra/docker/secrets/redis.env patra-infra/docker/secrets/gateway.env patra-infra/docker/.env.catalog.secret
git check-ignore -q patra-infra/docker/secrets/redis.env.example; echo "模板被忽略吗：exit=$?"
git status --short patra-infra/docker/secrets/
```

Expected:
- 前两个路径命中 `patra-infra/docker/.gitignore` 的 `secrets/*`，第三个命中 `.env.*.secret`。
- `模板被忽略吗：exit=1`，即模板不被忽略。
- `?? patra-infra/docker/secrets/`。

- [ ] **Step 5: 删掉过时的模板**

Run: `git rm -q patra-infra/docker/.env.registry.secret.example`

这个模板写的「复制为 `.env.apps.secret`」早已不存在，由上面两个模板取代。

- [ ] **Step 6: init-volumes.sh 增加建密钥目录的一步**

改 `patra-infra/scripts/init-volumes.sh`。

1. 第 10 行的 `# 完成后即可：` 改成 `# 放好密钥文件（见 docker/README.md「密钥」）后即可：`。
2. 步骤编号从 4 步改成 5 步：`[1/4]` 改成 `[1/5]`；两处 `[2/4]` 改成 `[2/5]`；两处 `[3/4]` 改成 `[3/5]`；`[4/4]` 改成 `[4/5]`。
3. 在第 4 节（ES data 目录权限）最后的 `echo ""` 后面、`echo "===================================="` 前面，插入：

```bash
# ---------------- 5. 密钥目录 ----------------
# 应用和 Redis 的密钥放在仓库外的 ~/.patra/secrets/（见 docker/README.md「密钥」）。
# 这里只建目录、收紧权限，不生成任何密钥。
SECRETS="${HOME}/.patra/secrets"
echo "[5/5] 准备密钥目录 ${SECRETS}..."
mkdir -p "$SECRETS"
chmod 700 "$SECRETS"
echo "  ✓ 密钥目录就绪（redis.env、gateway.env 要另外放进去）"
echo ""
```

4. 结尾「下一步」里的前两行

```bash
echo "下一步："
echo "  bash patra-infra/scripts/compose-all.sh up   # 拉起全部子栈（各为独立 project）"
```

改成

```bash
echo "下一步："
echo "  把 redis.env、gateway.env 放进 ~/.patra/secrets/（见 patra-infra/docker/README.md「密钥」）"
echo "  bash patra-infra/scripts/compose-all.sh up   # 拉起全部子栈（各为独立 project）"
```

- [ ] **Step 7: 在临时 HOME 里跑 init-volumes.sh**

Run:

```bash
T="$(mktemp -d)"
env HOME="$T" bash patra-infra/scripts/init-volumes.sh | grep -E '^\[[0-9]/5\]|密钥目录就绪'
stat -f '%Lp' "$T/.patra/secrets"
env HOME="$T" bash patra-infra/scripts/init-volumes.sh > /dev/null && echo "再跑一次成功"
rm -rf "$T"
shellcheck patra-infra/scripts/init-volumes.sh
```

Expected:
- `[1/5]` 到 `[5/5]` 五行，以及 `✓ 密钥目录就绪（redis.env、gateway.env 要另外放进去）`。
- `700`，然后是 `再跑一次成功`。
- shellcheck 没有输出。

- [ ] **Step 8: 本机生成密钥**

已有的文件不覆盖。私钥和密码只写文件，不打印。

```bash
set -euo pipefail
umask 077
mkdir -p ~/.patra/secrets && chmod 700 ~/.patra/secrets
if [ -e ~/.patra/secrets/redis.env ]; then
  echo "redis.env 已存在，不覆盖"
else
  printf 'REDIS_PASSWORD=%s\n' "$(openssl rand -hex 32)" > ~/.patra/secrets/redis.env
fi
if [ -e ~/.patra/secrets/gateway.env ]; then
  echo "gateway.env 已存在，不覆盖"
else
  K="$(mktemp -u ~/.patra/secrets/key.XXXXXX)"
  ./gradlew -q :patra-starters:patra-spring-boot-starter-security:generateIdentityAssertionKey -PkeyOut="$K" \
    | tail -1 > /tmp/patra-identity-assertion-public.jwks
  { printf 'PATRA_GATEWAY_IDENTITY_ASSERTION_PRIVATE_KEY='; cat "$K"; } > ~/.patra/secrets/gateway.env
  rm -f "$K"
fi
```

`/tmp/patra-identity-assertion-public.jwks` 丢了的话（比如重启过），用私钥推出公钥，不打印私钥：

```bash
sed 's/^PATRA_GATEWAY_IDENTITY_ASSERTION_PRIVATE_KEY=//' ~/.patra/secrets/gateway.env \
  | jq -c '{keys: [del(.d)]}' > /tmp/patra-identity-assertion-public.jwks
```

核对，只输出文件属性、计数和 kid：

```bash
ls -la ~/.patra/secrets
grep -cE '^REDIS_PASSWORD=[0-9a-f]{64}$' ~/.patra/secrets/redis.env
jq -e '(.keys | length) == 1 and (.keys[0] | has("d") | not) and .keys[0].crv == "P-256"' /tmp/patra-identity-assertion-public.jwks
KID="$(jq -r '.keys[0].kid' /tmp/patra-identity-assertion-public.jwks)"
sed 's/^PATRA_GATEWAY_IDENTITY_ASSERTION_PRIVATE_KEY=//' ~/.patra/secrets/gateway.env \
  | jq -e --arg kid "$KID" '.kid == $kid and has("d") and .crv == "P-256"' > /dev/null \
  && echo "私钥与公钥配对，kid=${KID}"
```

Expected:
- 目录是 `drwx------`。里面只有 `gateway.env`、`redis.env` 两个文件，都是 `-rw-------`，没有残留的 `key.*`。
- 然后依次是 `1`、`true`、`私钥与公钥配对，kid=<43 个字符>`。

- [ ] **Step 9: 提交**

```bash
git add patra-infra/docker/secrets/redis.env.example patra-infra/docker/secrets/gateway.env.example \
  patra-infra/docker/.gitignore patra-infra/scripts/init-volumes.sh
git diff --cached --stat
git commit -F - <<'EOF'
feat(infra): 密钥改放仓库外的 ~/.patra/secrets，补模板和目录初始化 (PAP-66)

CD 每次运行前的 git clean 会删掉 runner 工作区里被忽略的文件，放在 compose
目录的 .env.<服务>.secret 从来没生效过。secrets/ 下只放模板，其余一律忽略；
删掉指向早已不存在的 .env.apps.secret 的旧模板。

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
```

`--stat` 里应有 5 个文件，其中 `.env.registry.secret.example` 是删除（上面的 `git rm` 已暂存）。

---

### Task 3: Redis 服务端带密码启动

**Files:**
- Modify: `patra-infra/docker/docker-compose.core.yaml:29-42`（redis 服务）

**Interfaces:**
- Consumes: 本机 `~/.patra/secrets/redis.env` 的格式（Task 2）。本任务的检查用临时 HOME 里的假文件，不读真文件。
- Produces: core 栈的 redis 必需 `${HOME}/.patra/secrets/redis.env`，以 `--requirepass "$REDIS_PASSWORD"` 启动。上线 5 在 mini 上重建它。

- [ ] **Step 1: 改 redis 服务**

把 `docker-compose.core.yaml` 里整个 `redis:` 服务（第 29～42 行）改成：

```yaml
  redis:
    image: redis:7.0.15
    container_name: patra-redis
    restart: unless-stopped
    ports: [ "16379:6379" ]
    env_file:
      - path: ${HOME}/.patra/secrets/redis.env
        required: true
    # 密码只来自 redis.env。为空时拒绝启动：--requirepass "" 会让 default 回到 nopass
    command: [ "sh", "-c", "[ -n \"$$REDIS_PASSWORD\" ] || { echo 'REDIS_PASSWORD 为空，拒绝无密码启动' >&2; exit 1; }; exec redis-server /usr/local/etc/redis/redis.conf --requirepass \"$$REDIS_PASSWORD\"" ]
    volumes:
      - ${HOME}/.patra/docker/redis/data:/data
      - ${HOME}/.patra/docker/redis/redis.conf:/usr/local/etc/redis/redis.conf:ro
    healthcheck:
      # redis-cli 被拒时退出码也是 0，所以要核对输出是 PONG
      test: [ "CMD-SHELL", "REDISCLI_AUTH=\"$$REDIS_PASSWORD\" redis-cli ping | grep -q PONG" ]
      interval: 10s
      timeout: 5s
      retries: 10
```

- [ ] **Step 2: 用一次性容器核对启动命令和健康检查（Review Focus 3、4）**

从 compose 解析出启动命令和健康检查，在名字唯一的一次性容器里运行。不碰本机可能存在的 `patra-redis`。

```bash
T="$(mktemp -d)"; mkdir -p "$T/home/.patra/secrets"
printf 'REDIS_PASSWORD=fake\n' > "$T/home/.patra/secrets/redis.env"
printf 'port 6379\n' > "$T/redis.conf"
J="$(env HOME="$T/home" DOCKER_CONFIG="$HOME/.docker" docker compose -f patra-infra/docker/docker-compose.core.yaml config --format json)"
CMD="$(jq -r '.services.redis.command[2] | gsub("\\$\\$"; "$")' <<<"$J")"
HC="$(jq -r '.services.redis.healthcheck.test[1] | gsub("\\$\\$"; "$")' <<<"$J")"
V="$T/redis.conf:/usr/local/etc/redis/redis.conf:ro"
docker run --rm -e REDIS_PASSWORD= -v "$V" redis:7.0.15 sh -c "$CMD"; echo "空密码 exit=$?"
docker run -d --name pap66-redis-probe -e REDIS_PASSWORD=probe-pass -v "$V" redis:7.0.15 sh -c "$CMD" > /dev/null; sleep 2
docker exec pap66-redis-probe sh -c "$HC"; echo "健康检查 exit=$?"
docker exec pap66-redis-probe redis-cli ping
docker exec pap66-redis-probe sh -c 'redis-cli ping | grep -q PONG'; echo "不带密码的检查 exit=$?"
docker exec pap66-redis-probe redis-cli --user default --pass probe-pass --no-auth-warning ping
echo "进程信息里出现密码的次数：$(docker top pap66-redis-probe | grep -c probe-pass)"
docker rm -f pap66-redis-probe > /dev/null; rm -rf "$T"
```

Expected（`config` 可能先在 stderr 打一行 `version` 已过时的警告，可以忽略）：

```text
REDIS_PASSWORD 为空，拒绝无密码启动
空密码 exit=1
健康检查 exit=0
NOAUTH Authentication required.

不带密码的检查 exit=1
PONG
进程信息里出现密码的次数：0
```

- [ ] **Step 3: 提交**

```bash
git add patra-infra/docker/docker-compose.core.yaml
git diff --cached --stat
git commit -F - <<'EOF'
feat(infra): Redis 带密码启动，密码为空时拒绝启动 (PAP-66)

密码只来自 ~/.patra/secrets/redis.env。健康检查核对输出是 PONG：
redis-cli 被拒时退出码也是 0。

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
```

---

### Task 4: apps 栈接入 identity，密钥改由仓库外文件注入

**Files:**
- Modify: `patra-infra/docker/docker-compose.apps.yaml`（整份重写）
- Create: `patra-infra/docker/.env.identity`
- Modify: `patra-infra/docker/.env.common`
- Modify: `patra-infra/docker/.env.catalog`、`.env.registry`、`.env.ingest`、`.env.gateway`、`.env.object-storage`、`.env.portal`
- Modify: `patra-infra/docker/postgres/init-scripts/02-create-databases.sql`

**Interfaces:**
- Consumes:
  - `/tmp/patra-identity-assertion-public.jwks`（Task 2）。
  - 服务名 `identity` 和变量名 `IDENTITY_IMAGE_TAG`（Task 1）。
- Produces，容器里能拿到的环境变量：
  - 来自 `.env.common`：`REDIS_HOST=redis`、`REDIS_PORT=6379`、`PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS=<JWK Set>`。
  - 来自 `redis.env`：`REDIS_PASSWORD`，只给 catalog、identity、gateway。
  - 来自 `gateway.env`：`PATRA_GATEWAY_IDENTITY_ASSERTION_PRIVATE_KEY`，只给 gateway。
  - 来自 `.env.identity`：`IDENTITY_DB_URL`、`IDENTITY_DB_USERNAME`、`IDENTITY_DB_PASSWORD`。

  Task 5 的 container 配置按这些名字读。

- [ ] **Step 1: 重写 docker-compose.apps.yaml**

整份改成：

```yaml
name: patra-apps

# Patra 应用容器栈（与基础设施容器同在外部网络 patra-net）。
# 镜像由 CD（.github/workflows/cd.yml）构建推送到 GHCR，本文件只负责在 Mac mini 上编排运行。
# 镜像 tag 由 deploy job 通过 {SVC}_IMAGE_TAG 注入；手动 compose-all.sh up apps 时回退 latest。
#
# env_file 按「共享坐标、服务专属、密钥」叠加（后者覆盖同名）：
#   .env.common   共享基建坐标（Nacos / MinIO / RocketMQ / xxl-job / Redis 地址）和身份断言公钥，dev 默认
#   .env.<svc>    服务专属（DB / bucket / 日志路径，dev 默认）
#   ${HOME}/.patra/secrets/*.env   密钥，放在仓库外（见 README「密钥」）：
#     redis.env     Redis 密码。catalog、identity、gateway 必需
#     gateway.env   网关签身份断言的私钥。gateway 必需
#     <svc>.env     外部数据源密钥等。可选，缺失时跳过
# 必需文件缺一个，compose 对本项目的任何命令都会失败（portal 也部署不了），报错里有完整路径。
# 密钥不放在 compose 目录：CD 每次运行前的 git clean 会删掉 runner 工作区里被忽略的文件。
#
# 服务清单 SSOT 见 patra-infra/cd/services.json（端口/镜像/构建上下文/部署顺序）。

x-app-healthcheck: &app-healthcheck
  interval: 10s
  timeout: 5s
  retries: 15
  start_period: 60s

services:
  registry:
    image: ghcr.io/linqibin0826/patra-registry:${REGISTRY_IMAGE_TAG:-latest}
    container_name: patra-registry
    restart: unless-stopped
    ports: [ "6000:6000" ]
    env_file:
      - .env.common
      - .env.registry
      - path: ${HOME}/.patra/secrets/registry.env
        required: false
    healthcheck:
      test: [ "CMD", "curl", "-fs", "http://localhost:6000/actuator/health" ]
      <<: *app-healthcheck

  object-storage:
    image: ghcr.io/linqibin0826/patra-object-storage:${OBJECT_STORAGE_IMAGE_TAG:-latest}
    container_name: patra-object-storage
    restart: unless-stopped
    ports: [ "6200:6200" ]
    env_file:
      - .env.common
      - .env.object-storage
      - path: ${HOME}/.patra/secrets/object-storage.env
        required: false
    healthcheck:
      test: [ "CMD", "curl", "-fs", "http://localhost:6200/actuator/health" ]
      <<: *app-healthcheck

  catalog:
    image: ghcr.io/linqibin0826/patra-catalog:${CATALOG_IMAGE_TAG:-latest}
    container_name: patra-catalog
    restart: unless-stopped
    ports: [ "6300:6300" ]
    env_file:
      - .env.common
      - .env.catalog
      - path: ${HOME}/.patra/secrets/redis.env
        required: true
      - path: ${HOME}/.patra/secrets/catalog.env
        required: false
    healthcheck:
      test: [ "CMD", "curl", "-fs", "http://localhost:6300/actuator/health" ]
      <<: *app-healthcheck

  ingest:
    image: ghcr.io/linqibin0826/patra-ingest:${INGEST_IMAGE_TAG:-latest}
    container_name: patra-ingest
    restart: unless-stopped
    ports: [ "6100:6100" ]
    env_file:
      - .env.common
      - .env.ingest
      - path: ${HOME}/.patra/secrets/ingest.env
        required: false
    healthcheck:
      test: [ "CMD", "curl", "-fs", "http://localhost:6100/actuator/health" ]
      <<: *app-healthcheck

  identity:
    image: ghcr.io/linqibin0826/patra-identity:${IDENTITY_IMAGE_TAG:-latest}
    container_name: patra-identity
    restart: unless-stopped
    ports: [ "6400:6400" ]
    env_file:
      - .env.common
      - .env.identity
      - path: ${HOME}/.patra/secrets/redis.env
        required: true
      - path: ${HOME}/.patra/secrets/identity.env
        required: false
    healthcheck:
      test: [ "CMD", "curl", "-fs", "http://localhost:6400/actuator/health" ]
      <<: *app-healthcheck

  gateway:
    image: ghcr.io/linqibin0826/patra-gateway:${GATEWAY_IMAGE_TAG:-latest}
    container_name: patra-gateway
    restart: unless-stopped
    ports: [ "9528:9528" ]
    env_file:
      - .env.common
      - .env.gateway
      - path: ${HOME}/.patra/secrets/redis.env
        required: true
      - path: ${HOME}/.patra/secrets/gateway.env
        required: true
    healthcheck:
      test: [ "CMD", "curl", "-fs", "http://localhost:9528/actuator/health" ]
      <<: *app-healthcheck

  portal:
    image: ghcr.io/linqibin0826/patra-portal:${PORTAL_IMAGE_TAG:-latest}
    container_name: patra-portal
    restart: unless-stopped
    ports: [ "4000:4000" ]
    # portal 是 BFF（Next.js Node server），不读 .env.common（那是 Spring/Nacos/MinIO，portal 用不到）
    env_file:
      - .env.portal
      - path: ${HOME}/.patra/secrets/portal.env
        required: false
    healthcheck:
      # busybox wget（node:alpine 自带，免装 curl）；/api/health 不依赖 gateway
      # 用 127.0.0.1 而非 localhost：容器内 localhost 先解析 ::1（IPv6），
      # 而 Next.js standalone 只监听 IPv4，busybox wget 无回退会 Connection refused
      test: [ "CMD", "wget", "-q", "-O", "-", "http://127.0.0.1:4000/api/health" ]
      <<: *app-healthcheck

  learn:
    image: ghcr.io/linqibin0826/patra-learn:${LEARN_IMAGE_TAG:-latest}
    container_name: patra-learn
    restart: unless-stopped
    ports: [ "4001:4001" ]
    # learn 是纯静态学习站（无后端依赖），不读 .env.common
    env_file:
      - .env.learn
    healthcheck:
      # 用 127.0.0.1 而非 localhost：容器内 localhost 先解析 ::1（IPv6），
      # 而 Next.js standalone 只监听 IPv4（同 portal 的坑）
      test: [ "CMD", "wget", "-q", "-O", "-", "http://127.0.0.1:4001/api/health" ]
      <<: *app-healthcheck

networks:
  default:
    # 各子栈独立 project，共享外部网络 patra-net（由 scripts/compose-all.sh 首次创建）
    name: patra-net
    external: true
```

- [ ] **Step 2: 新建 .env.identity**

数据库密码沿用其他服务那个随仓库提交的 dev 默认值，从 `.env.catalog` 复制，不手写：

```bash
{
  cat <<'EOF'
# identity 专属（非敏感 dev 默认值）。共享坐标（Nacos / Redis 地址 / 身份断言公钥）见 .env.common；
# Redis 密码在仓库外的 ~/.patra/secrets/redis.env（见 README「密钥」）。

# 数据库（patra-net 服务名 postgres:5432）
IDENTITY_DB_URL=jdbc:postgresql://postgres:5432/patra_identity
IDENTITY_DB_USERNAME=postgres
EOF
  sed -n 's/^CATALOG_DB_PASSWORD=/IDENTITY_DB_PASSWORD=/p' patra-infra/docker/.env.catalog
} > patra-infra/docker/.env.identity
grep -cE '^IDENTITY_DB_(URL|USERNAME|PASSWORD)=.+' patra-infra/docker/.env.identity
```

Expected: `3`

- [ ] **Step 3: .env.common 加 Redis 坐标和公钥**

先改头部注释。把第 4～6 行

```bash
# 安全：这里只放内网 dev 默认值（人尽皆知、服务仅在 tailscale 内网暴露，公网打不到端口）。
# 真敏感密钥（外部数据源 API key / token 等）一律写进对应 .env.<svc>.secret
# （已 gitignore，绝不进公开仓库），compose 的 env_file 会自动叠加加载。
```

改成

```bash
# 安全：这里只放内网 dev 默认值（人尽皆知、服务仅在 tailscale 内网暴露，公网打不到端口）和公钥。
# 密钥（Redis 密码、网关私钥、外部数据源 API key 等）放在仓库外的 ~/.patra/secrets/，
# 由 compose 的 env_file 按绝对路径叠加加载，见 README「密钥」。
```

然后在文件末尾追加，公钥从文件读，不手抄：

```bash
{
  cat <<'EOF'

# --- Redis（patra-net 服务名 redis:6379，所有服务共用 0 号库）---
# 密码不在这里：在 ~/.patra/secrets/redis.env，只有 catalog、identity、gateway 加载。
REDIS_HOST=redis
REDIS_PORT=6379

# --- 身份断言公钥（网关签断言用的公钥，JWK Set JSON，不是机密）---
# identity、gateway 的 container 配置读它；两者 application-dev.yml 里是同一个值。
# 换密钥时三处一起改，顺序见 README「密钥」。
EOF
  printf 'PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS=%s\n' "$(cat /tmp/patra-identity-assertion-public.jwks)"
} >> patra-infra/docker/.env.common
tail -1 patra-infra/docker/.env.common | cut -c1-60
```

Expected: `PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS={"keys":[{"kty":"EC",`。字段顺序可能不同，开头是 `PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS={"keys":[{` 即可。

- [ ] **Step 4: 改 .env.catalog**

1. 第 1～2 行改成：

```bash
# catalog 专属（非敏感 dev 默认值）。共享坐标（Nacos / MinIO endpoint+凭据 / xxl-job admin / Redis 地址）见 .env.common。
# 密钥写 ~/.patra/secrets/catalog.env（仓库外，见 README「密钥」）；Redis 密码在 ~/.patra/secrets/redis.env。
```

2. 删掉下面三行（Redis 的注释、地址，以及其后的空行）：

```bash
# Redis
CATALOG_REDIS_URL=redis://redis:6379

```

3. 把第 23～25 行注释里的 `.env.catalog.secret 提供 PROXY_AUTH_KEY / PROXY_AUTH_PWD。` 改成 `~/.patra/secrets/catalog.env 提供 PROXY_AUTH_KEY / PROXY_AUTH_PWD。`。
4. 最后的注释块改成：

```bash
# 🔴 外部数据源凭据【不在此文件】（公开仓库安全红线）：
#    SCOPUS_API_KEY / PROXY_AUTH_KEY / PROXY_AUTH_PWD —— 一律写入 ~/.patra/secrets/catalog.env（仓库外）。
#    application.yml 对这些均为空默认（不再硬编码任何 key）；缺失不阻塞启动，但运行时调用
#    对应外部源会因无凭据失败——需要该能力时务必在 catalog.env 提供真 key。
```

- [ ] **Step 5: 改其余五个环境文件**

1. `.env.registry`：
   - 第 2 行改成 `# 密钥写 ~/.patra/secrets/registry.env（仓库外，见 README「密钥」）。`
   - 删掉 `REGISTRY_DB_PASSWORD=` 那一行之后的空行，以及 `# Redis（patra-net 服务名 redis:6379）`、`REGISTRY_REDIS_URL=redis://redis:6379` 两行。删完以后文件以 `REGISTRY_DB_PASSWORD=` 那一行结尾。
2. `.env.ingest`：
   - 第 2 行改成 `# 密钥写 ~/.patra/secrets/ingest.env（仓库外，见 README「密钥」）。`
   - 删掉 `# Redis`、`INGEST_REDIS_URL=redis://redis:6379` 两行，以及其后的空行。
   - `# 需要时 ROCKETMQ_AK / ROCKETMQ_SK 写入 .env.ingest.secret（已 gitignore）。` 改成 `# 需要时 ROCKETMQ_AK / ROCKETMQ_SK 写入 ~/.patra/secrets/ingest.env（仓库外）。`
3. `.env.object-storage`：第 2 行改成 `# 密钥写 ~/.patra/secrets/object-storage.env（仓库外，见 README「密钥」）。`
4. `.env.portal`：第 6 行改成 `# 密钥（如有）写进 ~/.patra/secrets/portal.env（仓库外，缺失时跳过，见 README「密钥」）。`
5. `.env.gateway`：整份改成：

```bash
# gateway 专属：目前没有。网关的共享坐标（Nacos、Redis 地址、身份断言公钥）在 .env.common；
# Redis 密码和签断言的私钥在仓库外的 ~/.patra/secrets/redis.env、gateway.env（见 README「密钥」）。
# 本文件为占位，保持 compose 各服务 env_file 引用结构一致；以后有 gateway 专属的非密钥配置在此追加。
```

- [ ] **Step 6: 建库脚本加 patra_identity**

在 `02-create-databases.sql` 的 `CREATE DATABASE patra_storage;` 后面加一行：

```sql
CREATE DATABASE patra_identity;
```

- [ ] **Step 7: 确认旧变量和旧密钥文件名都清干净了**

Run: `git grep -n '_REDIS_URL\|\.env\.[a-z-]*\.secret' -- patra-infra/docker ':!patra-infra/docker/.gitignore'; echo "exit=$?"`

Expected: 没有匹配行，`exit=1`。

- [ ] **Step 8: compose 静态检查（Review Focus 2、5）**

建一个临时 HOME，放假的密钥文件，再解析两个栈：

```bash
T="$(mktemp -d)"; S="$T/home/.patra/secrets"; mkdir -p "$S"
printf 'REDIS_PASSWORD=fake-redis-password\n' > "$S/redis.env"
printf 'PATRA_GATEWAY_IDENTITY_ASSERTION_PRIVATE_KEY={"kty":"EC","fake":true}\n' > "$S/gateway.env"
dc() { env HOME="$T/home" DOCKER_CONFIG="$HOME/.docker" docker compose "$@"; }
dc -f patra-infra/docker/docker-compose.apps.yaml config --format json > "$T/apps.json" && echo "apps 解析通过"
jq -e --rawfile pub /tmp/patra-identity-assertion-public.jwks '
  .services as $s
  | ($pub | rtrimstr("\n")) as $p
  | $s.identity.image == "ghcr.io/linqibin0826/patra-identity:latest"
  and $s.identity.environment.SPRING_PROFILES_ACTIVE == "container"
  and $s.identity.environment.IDENTITY_DB_URL == "jdbc:postgresql://postgres:5432/patra_identity"
  and $s.identity.environment.REDIS_HOST == "redis"
  and $s.identity.environment.REDIS_PORT == "6379"
  and $s.identity.environment.REDIS_PASSWORD == "fake-redis-password"
  and $s.identity.environment.PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS == $p
  and $s.gateway.environment.PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS == $p
  and $s.gateway.environment.PATRA_GATEWAY_IDENTITY_ASSERTION_PRIVATE_KEY == "{\"kty\":\"EC\",\"fake\":true}"
  and $s.gateway.environment.REDIS_PASSWORD == "fake-redis-password"
  and $s.catalog.environment.REDIS_PASSWORD == "fake-redis-password"
  and ($s.registry.environment | has("REDIS_PASSWORD") | not)
  and ($s.portal.environment | has("REDIS_PASSWORD") | not)
  and ([$s[] | (.environment // {}) | keys[] | select(endswith("_REDIS_URL"))] | length == 0)
' "$T/apps.json"
mv "$S/gateway.env" "$T/gateway.env.bak"
dc -f patra-infra/docker/docker-compose.apps.yaml config > /dev/null 2> "$T/err"; echo "缺 gateway.env：exit=$?"
grep -o 'env file [^ ]*gateway.env not found' "$T/err"
mv "$T/gateway.env.bak" "$S/gateway.env"
dc -f patra-infra/docker/docker-compose.core.yaml config --format json 2> /dev/null \
  | jq -e '.services.redis.environment.REDIS_PASSWORD == "fake-redis-password"'
rm "$S/redis.env"
dc -f patra-infra/docker/docker-compose.core.yaml config > /dev/null 2> "$T/err"; echo "缺 redis.env（core）：exit=$?"
grep -o 'env file [^ ]*redis.env not found' "$T/err"
dc -f patra-infra/docker/docker-compose.apps.yaml config > /dev/null 2> "$T/err"; echo "缺 redis.env（apps）：exit=$?"
grep -o 'env file [^ ]*redis.env not found' "$T/err"
rm -rf "$T"
```

Expected（`<T>` 是那个临时目录）：

```text
apps 解析通过
true
缺 gateway.env：exit=1
env file <T>/home/.patra/secrets/gateway.env not found
true
缺 redis.env（core）：exit=1
env file <T>/home/.patra/secrets/redis.env not found
缺 redis.env（apps）：exit=1
env file <T>/home/.patra/secrets/redis.env not found
```

第二行的 `true` 同时证明了 Review Focus 2 在 compose 这一侧：公钥 JSON 原样进了 identity 和 gateway 的环境变量。

- [ ] **Step 9: 提交**

```bash
git add patra-infra/docker/docker-compose.apps.yaml patra-infra/docker/.env.common patra-infra/docker/.env.identity \
  patra-infra/docker/.env.catalog patra-infra/docker/.env.registry patra-infra/docker/.env.ingest \
  patra-infra/docker/.env.gateway patra-infra/docker/.env.object-storage patra-infra/docker/.env.portal \
  patra-infra/docker/postgres/init-scripts/02-create-databases.sql
git diff --cached --stat
git commit -F - <<'EOF'
feat(infra): identity 接入 apps 栈，密钥改由仓库外文件注入 (PAP-66)

各服务的 env_file 改成 .env.common、.env.<服务>、~/.patra/secrets/ 下的密钥文件；
Redis 地址和身份断言公钥进 .env.common，删掉从未生效的 REGISTRY_/INGEST_REDIS_URL
和改由共享坐标取代的 CATALOG_REDIS_URL；建库脚本加 patra_identity。

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
```

如果 gitleaks 把 `.env.common` 里的公钥报成泄露：公钥不是机密，这是误报。把报告里的 Fingerprint 写进仓库根的 `.gitleaksignore`（没有就新建），和本任务一起提交，并在提交信息里说明。

---

### Task 5: 服务配置：Redis 用共享地址加密码，本机从仓库外文件读密钥，公钥写进 dev 配置

**Files:**
- Modify: `patra-api/patra-gateway-boot/src/main/resources/application-dev.yml`（整份重写）
- Modify: `patra-api/patra-gateway-boot/src/main/resources/application-container.yml`（整份重写）
- Modify: `patra-api/patra-identity/patra-identity-boot/src/main/resources/application-dev.yml`
- Modify: `patra-api/patra-identity/patra-identity-boot/src/main/resources/application-container.yml`（整份重写）
- Modify: `patra-api/patra-catalog/patra-catalog-boot/src/main/resources/application.yml:75`、`application-dev.yml`、`application-container.yml`
- Modify: `patra-api/patra-registry/patra-registry-boot/src/main/resources/application-dev.yml`、`application-container.yml`
- Modify: `patra-api/patra-ingest/patra-ingest-boot/src/main/resources/application-dev.yml`、`application-container.yml`
- Create: `.run/PatraIdentityApplication.run.xml`

**Interfaces:**
- Consumes:
  - Task 4 的环境变量名：`REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`、`PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS`、`PATRA_GATEWAY_IDENTITY_ASSERTION_PRIVATE_KEY`、`IDENTITY_DB_*`。
  - Task 2 的本机密钥文件和公钥文件。
- Produces:
  - dev profile 导入 `file:${user.home}/.patra/secrets/redis.env[.properties]`；gateway 另外导入 `gateway.env[.properties]`。
  - 公钥直接写在 identity、gateway 的 `application-dev.yml` 里。

- [ ] **Step 1: 重写 gateway 的 application-dev.yml**

`'PUBLIC_JWKS'` 是标记，第 5 步用公钥替换它。

```yaml
# ============================================================================
# Patra API Gateway - Development Environment Configuration
# ============================================================================
# Enhanced logging for development and debugging purposes.
# ============================================================================

spring:
  config:
    activate:
      on-profile: dev
    # 密钥与 mini 容器是同一批文件，放在仓库外（patra-infra/docker/README.md「密钥」）。
    # 不加 optional：缺文件时启动失败并报出路径
    import:
      - file:${user.home}/.patra/secrets/redis.env[.properties]
      - file:${user.home}/.patra/secrets/gateway.env[.properties]

  # dev 场景：用 TAILSCALE_IP 注册，确保跨主机服务发现拿到的 IP 可达。
  cloud:
    nacos:
      discovery:
        ip: ${TAILSCALE_IP:}
  data:
    redis:
      # 本机和 mini 容器共用这个 Redis 的 0 号库。带用户名 default：Redis 加密码前后都能认证
      host: ${PATRA_INFRA_HOST:127.0.0.1}
      port: 16379
      username: default
      password: ${REDIS_PASSWORD}

# ----------------------------------------------------------------------------
# Development Logging Configuration
# ----------------------------------------------------------------------------
# DEBUG level for detailed gateway routing and load balancing information
logging:
  level:
    org.springframework.cloud.gateway: DEBUG
    org.springframework.cloud.loadbalancer: DEBUG

patra:
  gateway:
    identity-assertion:
      # 含私钥的 EC P-256 JWK JSON，带 kid，来自上面导入的 gateway.env；没配启动失败
      private-key: ${PATRA_GATEWAY_IDENTITY_ASSERTION_PRIVATE_KEY}
  security:
    identity-assertion:
      # 网关自己那把公钥（JWK Set JSON）：安全 starter 的验签器和启动自检用。与 .env.common 的
      # PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS、identity 的 application-dev.yml 同值，换密钥时三处一起改
      public-keys: 'PUBLIC_JWKS'
```

- [ ] **Step 2: 重写 gateway 的 application-container.yml**

```yaml
# ============================================================================
# Patra API Gateway - Container Deployment Configuration
# ============================================================================
# 容器部署：全部从环境变量读，没有默认值。Redis 地址和公钥来自 .env.common，
# Redis 密码和私钥来自 ~/.patra/secrets/ 下的 redis.env、gateway.env（compose env_file）。
# ============================================================================

spring:
  config:
    activate:
      on-profile: container
  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT}
      username: default
      password: ${REDIS_PASSWORD}

patra:
  gateway:
    identity-assertion:
      private-key: ${PATRA_GATEWAY_IDENTITY_ASSERTION_PRIVATE_KEY}
  security:
    identity-assertion:
      public-keys: ${PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS}
```

- [ ] **Step 3: 改 identity 的两份配置**

`application-dev.yml` 改三处，`datasource` 等其余部分不动。

1. 在 `on-profile: dev` 下面加导入：

```yaml
spring:
  config:
    activate:
      on-profile: dev
    # Redis 密码与 mini 容器是同一个文件，放在仓库外（patra-infra/docker/README.md「密钥」）。
    # 不加 optional：缺文件时启动失败并报出路径
    import:
      - file:${user.home}/.patra/secrets/redis.env[.properties]
```

2. 把

```yaml
  data:
    redis:
      url: ${IDENTITY_REDIS_URL:redis://${PATRA_INFRA_HOST:127.0.0.1}:16379}
```

改成

```yaml
  data:
    redis:
      # 本机和 mini 容器共用这个 Redis 的 0 号库。带用户名 default：Redis 加密码前后都能认证
      host: ${PATRA_INFRA_HOST:127.0.0.1}
      port: 16379
      username: default
      password: ${REDIS_PASSWORD}
```

3. 把

```yaml
      # 网关的公钥（JWK Set JSON）。本地先用 generateIdentityAssertionKey 生成，见 README
      public-keys: ${PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS}
```

改成

```yaml
      # 网关的公钥（JWK Set JSON）。与 .env.common 的 PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS、
      # gateway 的 application-dev.yml 同值，换密钥时三处一起改（patra-infra/docker/README.md「密钥」）
      public-keys: 'PUBLIC_JWKS'
```

`application-container.yml` 整份改成：

```yaml
# 容器部署：所有连接信息都从环境变量读，没有默认值。库来自 .env.identity；Redis 地址和公钥来自
# .env.common；Redis 密码来自 ~/.patra/secrets/redis.env（compose env_file）。
spring:
  config:
    activate:
      on-profile: container
  datasource:
    url: ${IDENTITY_DB_URL}
    username: ${IDENTITY_DB_USERNAME}
    password: ${IDENTITY_DB_PASSWORD}
  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT}
      username: default
      password: ${REDIS_PASSWORD}

patra:
  security:
    identity-assertion:
      # 网关的公钥（JWK Set JSON），来自 .env.common
      public-keys: ${PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS}
```

- [ ] **Step 4: 改 catalog、registry、ingest**

catalog 改三份文件。

1. `application-dev.yml`：
   - 在 `on-profile: dev` 下面加导入：

```yaml
spring:
  config:
    activate:
      on-profile: dev
    # Redis 密码与 mini 容器是同一个文件，放在仓库外（patra-infra/docker/README.md「密钥」）。
    # 不加 optional：缺文件时启动失败并报出路径
    import:
      - file:${user.home}/.patra/secrets/redis.env[.properties]
```

   - 把

```yaml
  # Development Redis (Mac mini docker)
  data:
    redis:
      url: redis://${PATRA_INFRA_HOST:127.0.0.1}:16379
```

   改成（下面的 `connect-timeout`、`timeout` 两行不动）

```yaml
  # Development Redis (Mac mini docker)。本机和 mini 容器共用这个 Redis 的 0 号库；
  # 带用户名 default：Redis 加密码前后都能认证
  data:
    redis:
      host: ${PATRA_INFRA_HOST:127.0.0.1}
      port: 16379
      username: default
      password: ${REDIS_PASSWORD}
```

2. `application-container.yml`：把

```yaml
  # Production Redis (environment variables)
  data:
    redis:
      url: ${CATALOG_REDIS_URL}
```

   改成

```yaml
  # Redis：地址来自 .env.common，密码来自 ~/.patra/secrets/redis.env（compose env_file）
  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT}
      username: default
      password: ${REDIS_PASSWORD}
```

3. `application.yml` 第 75 行：`# SCOPUS_API_KEY 注入（容器部署走 .env.catalog.secret，已 gitignore）。` 改成 `# SCOPUS_API_KEY 注入（容器部署走仓库外的 ~/.patra/secrets/catalog.env）。`

registry 和 ingest 只删配置。它们的运行时类路径里没有 Redis 客户端，这些配置从未生效。

1. registry 的 `application-dev.yml`：删掉这几行，以及其后的空行：

```yaml
  # Development Redis (Mac mini docker)
  data:
    redis:
      url: redis://${PATRA_INFRA_HOST:127.0.0.1}:16379
```

2. registry 的 `application-container.yml`：删掉这几行，以及其前的空行。删完以后文件以 `password: ${REGISTRY_DB_PASSWORD}` 那一行结尾：

```yaml
  # Production Redis (URL from environment)
  data:
    redis:
      url: ${REGISTRY_REDIS_URL}
```

3. ingest 的 `application-dev.yml`：删掉这三行：

```yaml
  data:
    redis:
      url: redis://${PATRA_INFRA_HOST:127.0.0.1}:16379
```

4. ingest 的 `application-container.yml`：删掉这三行：

```yaml
  data:
    redis:
      url: ${INGEST_REDIS_URL}
```

- [ ] **Step 5: 把公钥写进两份 dev 配置**

```bash
python3 - <<'PY'
import pathlib
jwks = pathlib.Path('/tmp/patra-identity-assertion-public.jwks').read_text().strip()
for p in ['patra-api/patra-identity/patra-identity-boot/src/main/resources/application-dev.yml',
          'patra-api/patra-gateway-boot/src/main/resources/application-dev.yml']:
    f = pathlib.Path(p)
    s = f.read_text()
    assert s.count("'PUBLIC_JWKS'") == 1, p
    f.write_text(s.replace("'PUBLIC_JWKS'", "'" + jwks + "'"))
print('已写入')
PY
```

Expected: `已写入`

- [ ] **Step 6: 核对三处公钥逐字相同，YAML 里是字符串（Review Focus 2）**

```bash
python3 - <<'PY'
import json, pathlib, yaml
jwks = pathlib.Path('/tmp/patra-identity-assertion-public.jwks').read_text().strip()
for p in ['patra-api/patra-identity/patra-identity-boot/src/main/resources/application-dev.yml',
          'patra-api/patra-gateway-boot/src/main/resources/application-dev.yml']:
    v = yaml.safe_load(pathlib.Path(p).read_text())['patra']['security']['identity-assertion']['public-keys']
    assert isinstance(v, str) and v == jwks, p
env = dict(l.split('=', 1) for l in pathlib.Path('patra-infra/docker/.env.common').read_text().splitlines()
           if l and not l.startswith('#'))
assert env['PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS'] == jwks
key = json.loads(jwks)['keys'][0]
assert 'd' not in key
print('三处公钥一致，kid =', key['kid'])
PY
git grep -n 'GATEWAY_REDIS_URL\|IDENTITY_REDIS_URL\|CATALOG_REDIS_URL\|REGISTRY_REDIS_URL\|INGEST_REDIS_URL' -- patra-api patra-infra; echo "exit=$?"
```

Expected:
- `三处公钥一致，kid = <与 Task 2 第 8 步相同的 kid>`。
- `git grep` 没有匹配行，`exit=1`。

- [ ] **Step 7: 用空的 user.home 起三个启动包，确认导入会被处理（Review Focus 1）**

```bash
./gradlew -q :patra-api:patra-identity:patra-identity-boot:bootJar :patra-api:patra-gateway-boot:bootJar \
  :patra-api:patra-catalog:patra-catalog-boot:bootJar
E="$(mktemp -d)"
probe() { # $1=启动包：用空的 user.home 以 dev 启动，打出它报的缺失文件
  local log="$E/$(basename "$1").log" pid i
  java -Duser.home="$E" -jar "$1" --spring.profiles.active=dev > "$log" 2>&1 &
  pid=$!
  for i in $(seq 1 40); do kill -0 "$pid" 2>/dev/null || break; sleep 1; done
  kill "$pid" 2>/dev/null; wait "$pid" 2>/dev/null
  printf '%s -> %s\n' "$(basename "$1")" "$(grep -o "secrets/[a-z]*\.env]' via location" "$log" | head -1)"
}
probe patra-api/patra-identity/patra-identity-boot/build/libs/patra-identity-boot-0.1.0-SNAPSHOT.jar
probe patra-api/patra-catalog/patra-catalog-boot/build/libs/patra-catalog-boot-0.1.0-SNAPSHOT.jar
probe patra-api/patra-gateway-boot/build/libs/patra-gateway-boot-0.1.0-SNAPSHOT.jar
mkdir -p "$E/.patra/secrets" && printf 'REDIS_PASSWORD=x\n' > "$E/.patra/secrets/redis.env"
probe patra-api/patra-gateway-boot/build/libs/patra-gateway-boot-0.1.0-SNAPSHOT.jar
rm -rf "$E"
```

Expected：每个启动包几秒内退出，打出：

```text
patra-identity-boot-0.1.0-SNAPSHOT.jar -> secrets/redis.env]' via location
patra-catalog-boot-0.1.0-SNAPSHOT.jar -> secrets/redis.env]' via location
patra-gateway-boot-0.1.0-SNAPSHOT.jar -> secrets/redis.env]' via location
patra-gateway-boot-0.1.0-SNAPSHOT.jar -> secrets/gateway.env]' via location
```

箭头右边为空，说明导入没有生效，应用越过了配置阶段，这时第 1 条失败。去看 `$E` 里的日志：先查 `on-profile` 与 `import` 的缩进，再查文件名。

- [ ] **Step 8: 集成测试，确认 test profile 不受影响**

本机已经有密钥文件。即使测试误用了 dev profile，测试也会通过，所以还要看测试报告里激活的 profile。

```bash
L="$(mktemp)"
./gradlew :patra-api:patra-gateway-boot:integrationTest :patra-api:patra-identity:patra-identity-boot:integrationTest \
  :patra-api:patra-identity:patra-identity-session:integrationTest > "$L" 2>&1; echo "exit=$?"; tail -5 "$L"
grep -rhoE 'profiles? (is|are) active: [^<]*' \
  patra-api/patra-gateway-boot/build/test-results/integrationTest \
  patra-api/patra-identity/patra-identity-boot/build/test-results/integrationTest \
  patra-api/patra-identity/patra-identity-session/build/test-results/integrationTest \
  | sed 's/&quot;/"/g' | sort | uniq -c
```

Expected:
- `exit=0`，日志末尾是 `BUILD SUCCESSFUL`。
- 所有计数行都只有 `profile is active: "test"`，不出现 `dev`。session 模块的测试不启动 Spring 应用，可能没有这一行。

如果报 `bad class file`、`NoSuchFileException` 或 SpotBugs `No files to analyze`，是 IDEA 在并发写 `build/classes`，重跑一次即可。

- [ ] **Step 9: identity 的 IDEA 运行配置**

`.run/PatraIdentityApplication.run.xml`，照抄现有服务的写法：

```xml
<component name="ProjectRunConfigurationManager">
  <configuration default="false" name="PatraIdentityApplication" type="SpringBootApplicationConfigurationType" factoryName="Spring Boot" folderName="Otel" nameIsGenerated="true">
    <option name="ALTERNATIVE_JRE_PATH" value="zulu-25" />
    <option name="ALTERNATIVE_JRE_PATH_ENABLED" value="true" />
    <module name="patra.patra-api.patra-identity.patra-identity-boot.main" />
    <option name="SPRING_BOOT_MAIN_CLASS" value="dev.linqibin.patra.identity.PatraIdentityApplication" />
    <option name="VM_PARAMETERS" value="-javaagent:$PROJECT_DIR$/patra-infra/docker/opentelemetry-javaagent.jar -Dotel.javaagent.configuration-file=$PROJECT_DIR$/patra-infra/docker/otel-agent/otel-dev.properties" />
    <method v="2">
      <option name="Make" enabled="true" />
    </method>
  </configuration>
</component>
```

- [ ] **Step 10: 提交**

```bash
git add patra-api/patra-gateway-boot/src/main/resources/application-dev.yml \
  patra-api/patra-gateway-boot/src/main/resources/application-container.yml \
  patra-api/patra-identity/patra-identity-boot/src/main/resources/application-dev.yml \
  patra-api/patra-identity/patra-identity-boot/src/main/resources/application-container.yml \
  patra-api/patra-catalog/patra-catalog-boot/src/main/resources/application.yml \
  patra-api/patra-catalog/patra-catalog-boot/src/main/resources/application-dev.yml \
  patra-api/patra-catalog/patra-catalog-boot/src/main/resources/application-container.yml \
  patra-api/patra-registry/patra-registry-boot/src/main/resources/application-dev.yml \
  patra-api/patra-registry/patra-registry-boot/src/main/resources/application-container.yml \
  patra-api/patra-ingest/patra-ingest-boot/src/main/resources/application-dev.yml \
  patra-api/patra-ingest/patra-ingest-boot/src/main/resources/application-container.yml \
  .run/PatraIdentityApplication.run.xml
git diff --cached --stat
git commit -F - <<'EOF'
feat(config): Redis 改用共享地址加密码，本机进程从仓库外文件读密钥 (PAP-66)

catalog、identity、gateway 拆成 host、port、username（default）、password 四项：
用 URL 时 Redisson 只发 AUTH <密码>，Redis 加密码前会被拒。dev profile 用
spring.config.import 读 ~/.patra/secrets/ 下的文件；公钥直接写进 identity、
gateway 的 dev 配置。registry、ingest 没有 Redis 客户端，删掉从未生效的配置。
新增 identity 的 IDEA 运行配置。

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
```

gitleaks 误报公钥时，按 Task 4 第 9 步末尾的做法处理。

---

### Task 6: runbook 与文档

**Files:**
- Create: `docs/patra/runbooks/v0.8-accounts-go-live-runbook.md`
- Modify: `patra-infra/docker/README.md`、`patra-infra/CLAUDE.md`、`.claude/rules/project-info.md`、`README.md`、`patra-api/README.md`
- Modify: `patra-api/patra-identity/README.md`、`patra-api/patra-gateway-boot/README.md`
- Modify: `docs/patra/release-specs/v0.8-accounts.md`、`docs/patra/specs/2026-10-09-gateway-auth-design.md`

**Interfaces:**
- Consumes: Task 1～5 定下的路径、变量名和命令。
- Produces: runbook 第 1～6 节，第二部分照它执行。

- [ ] **Step 1: 新建 runbook**

`docs/patra/runbooks/v0.8-accounts-go-live-runbook.md`：

````markdown
# v0.8 Accounts 上线 runbook

> 一次性操作手册：把 identity、带鉴权的网关和 Redis 密码上到 mini。
> 设计依据：`docs/patra/specs/2026-10-10-identity-deploy-design.md` 第 11 节（上线顺序）、第 12 节（验证）。
>
> - 在 MacBook 的仓库根目录执行；mini 上的命令经 `ssh mini` 执行。
> - 每步给出命令、预期结果和失败时的动作。改动 mini 的步骤，先确认上一步的预期全部满足再执行。
> - 全程不打印 Redis 密码、私钥和会话令牌：命令只输出状态码、计数、哈希和文件属性。
> - 实测结果写回设计文档第 13 节。

## 0. 环境与约定

| 项 | 值 |
|---|---|
| mini SSH | `ssh mini` |
| core 栈的检出 | mini `~/Projects/patra`（sparse checkout，只有 `patra-infra`），core 栈从这里起 |
| apps 栈的检出 | mini `~/actions-runner/_work/patra/patra`，由 CD 部署 |
| 密钥 | 两台机器的 `~/.patra/secrets/`：`redis.env`、`gateway.env`，内容相同 |
| last-good | mini `~/.patra/cd/last-good-<服务>` |
| 网关 | 在 mini 上访问 `http://127.0.0.1:9528` |

前提：

- 分支已通过门禁。
- MacBook 的 `~/.patra/secrets/` 下已有两个文件，生成方法见 `patra-infra/docker/README.md`「密钥」。

## 1. mini：放密钥文件

```bash
ssh mini 'umask 077 && mkdir -p ~/.patra/secrets && chmod 700 ~/.patra/secrets && ls -A ~/.patra/secrets | wc -l'
```

预期：`0`。不是 0 就停下，先弄清已有文件的来源，不要覆盖。

```bash
scp -p ~/.patra/secrets/redis.env ~/.patra/secrets/gateway.env mini:.patra/secrets/
ssh mini 'ls -l ~/.patra/secrets'
diff <(shasum -a 256 ~/.patra/secrets/redis.env ~/.patra/secrets/gateway.env | awk '{print $1}') \
     <(ssh mini 'shasum -a 256 ~/.patra/secrets/redis.env ~/.patra/secrets/gateway.env' | awk '{print $1}') \
  && echo "两台机器内容一致"
```

预期：两个文件都是 `-rw-------`；最后一行是 `两台机器内容一致`。

## 2. mini：建 patra_identity 库

必须在推送 main 之前做：identity 启动时 Flyway 要求库已经存在。

```bash
ssh mini 'bash -s' <<'EOF'
lsof -nP -iTCP:6400 -sTCP:LISTEN || echo "6400 空闲"
docker exec patra-postgres psql -U postgres -tAc "SELECT count(*) FROM pg_database WHERE datname = 'patra_identity'"
EOF
```

预期：`6400 空闲`、`0`。

```bash
ssh mini 'docker exec patra-postgres psql -U postgres -c "CREATE DATABASE patra_identity"'
```

预期：`CREATE DATABASE`。

## 3. 本机冒烟

用 dev profile 在 MacBook 上起 identity 和 gateway，连 mini 的 Nacos、PG、Redis。

此时 mini 的 Redis 还没有密码，只有带用户名 `default` 的认证会成功。所以能连上，本身就证明 Lettuce 发出了用户名。

1. 在 IDEA 里起 `PatraIdentityApplication`、`PatraGatewayApplication`（默认 dev profile，不用设任何密钥变量）。

   预期：网关日志里有 `身份断言签名密钥就绪，kid=<kid>`，`<kid>` 等于下面命令的输出：

   ```bash
   sed -n 's/^PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS=//p' patra-infra/docker/.env.common | jq -r '.keys[0].kid'
   ```

2. 经本机网关走一遍。令牌只存在变量里，不打印：

   ```bash
   GW=http://127.0.0.1:9528/patra-identity
   code() { curl --noproxy '*' -s -o /dev/null -w '%{http_code}' "$@"; }
   EMAIL="pap66-smoke-$(date +%s)@example.com"; PW="$(openssl rand -hex 12)"
   echo "匿名 /auth/me：$(code "$GW/auth/me")"
   R="$(curl --noproxy '*' -s -w '\n%{http_code}' -X POST "$GW/auth/register" -H 'Content-Type: application/json' \
     -d "{\"email\":\"${EMAIL}\",\"password\":\"${PW}\",\"clientType\":\"web\"}")"
   echo "注册：$(tail -n1 <<<"$R")"; T="$(head -n1 <<<"$R" | jq -r .sessionToken)"
   echo "带令牌 /auth/me：$(code -H "Authorization: Bearer ${T}" "$GW/auth/me")"
   echo "登出：$(code -X POST -H "Authorization: Bearer ${T}" "$GW/auth/logout")"
   echo "登出后 /auth/me：$(code -H "Authorization: Bearer ${T}" "$GW/auth/me")"
   ```

   预期：`401`、`201`、`200`、`204`、`401`。网关刚起来时实例列表可能还是空的：注册得到 503 就等 10 秒再跑一遍。

3. 停掉两个进程。

失败时不要推送。看两个进程的日志，修好后重做本节。

## 4. 推送 main，CD 部署

前提：第 1～3 节都满足。

推送 main（`git push origin main`）后，CD 构建 6 个后端服务，按 `services.json` 的顺序部署：object-storage、registry、identity、gateway、catalog、ingest。

推送十几秒后：

```bash
RUN_ID="$(gh run list --workflow cd.yml --branch main -L 1 --json databaseId -q '.[0].databaseId')"
gh run watch "$RUN_ID" --exit-status > /dev/null; echo "CD exit=$?"
gh run view "$RUN_ID" --log | grep -E '===== deploy|全部部署成功|自动回滚|✗'
```

预期：`CD exit=0`；依次出现六行 `===== deploy <服务>`，最后是 `✓ 全部部署成功`。

```bash
ssh mini 'docker ps --filter name=patra- --format "{{.Names}}\t{{.Image}}\t{{.Status}}" | sort'
ssh mini 'docker logs patra-gateway 2>&1 | grep -m1 "身份断言签名密钥就绪"'
```

预期：

- 六个后端容器的镜像 tag 都是本次推送的提交 sha，状态是 `healthy`。
- kid 与第 3 节一致。

退路：

| 情况 | 会发生什么 | 怎么办 |
|---|---|---|
| gateway 不健康 | `deploy.sh` 自动回滚到 last-good，也就是 10/05 那版不带鉴权的网关，匿名浏览照常 | 用 `ssh mini 'docker logs patra-gateway --tail 100'` 查原因，修好后重新推送 |
| identity 不健康 | 第一次部署没有 last-good，它停在 unhealthy，不影响其他服务 | 同样看日志 |
| catalog 不健康 | 自动回滚到旧镜像。此时 Redis 还没加密码，旧镜像照常工作 | 看日志 |

## 5. mini：Redis 加密码

前提：第 4 节的 CD 成功，catalog、identity、gateway 都健康。这时它们已经带用户名 `default` 认证。

```bash
ssh mini 'cd ~/Projects/patra && git status -sb | head -1 && git pull --ff-only && git log --oneline -1'
```

预期：分支是 `main`，快进到刚推送的提交。

```bash
ssh mini 'cd ~/Projects/patra && docker compose -f patra-infra/docker/docker-compose.core.yaml --dry-run up -d --remove-orphans 2>&1 | grep Container'
```

预期：`patra-postgres Running`、`patra-nacos Running`、`patra-redis Recreate`（之后是 `Recreated`、`Starting`、`Started`），没有别的容器要重建。

切换的同时，在 mini 上每 0.5 秒带令牌请求一次 `/auth/me`，记下切换期间的状态码：

```bash
ssh mini 'bash -s' <<'EOF'
cd ~/Projects/patra
GW=http://127.0.0.1:9528/patra-identity
EMAIL="pap66-switch-$(date +%s)@example.com"; PW="$(openssl rand -hex 12)"
T="$(curl --noproxy '*' -s -X POST "$GW/auth/register" -H 'Content-Type: application/json' \
  -d "{\"email\":\"${EMAIL}\",\"password\":\"${PW}\",\"clientType\":\"web\"}" | jq -r .sessionToken)"
if [ -z "$T" ] || [ "$T" = null ]; then echo "注册失败，不切换"; exit 1; fi
LOG="$(mktemp)"
( for i in $(seq 1 160); do
    printf '%s %s\n' "$(date +%s)" "$(curl --noproxy '*' -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer ${T}" "$GW/auth/me")"
    sleep 0.5
  done > "$LOG" ) &
sleep 5
bash patra-infra/scripts/compose-all.sh up core
wait
echo "== 状态码分布"; awk '{print $2}' "$LOG" | sort | uniq -c
echo "== 非 200 的起止时刻（epoch 秒）"; awk '$2 != "200" {print $1}' "$LOG" | sed -n '1p;$p'
rm -f "$LOG"
EOF
```

预期：

- `compose-all.sh` 只重建 redis。
- 状态码以 200 为主。切换的几秒里可能出现 503，之后全部回到 200。
- 把分布和非 200 的持续时间记下来，写回设计文档第 13 节第 3 条。

```bash
printf 'PING\r\n' | nc -w 3 100.103.73.27 16379
ssh mini 'docker inspect -f "{{.State.Health.Status}}" patra-redis; for p in 6300 6400 9528; do curl --noproxy "*" -s http://127.0.0.1:$p/actuator/health; echo; done'
```

预期：

- `-NOAUTH Authentication required.`
- `healthy`。
- 三个服务都返回 `{"status":"UP"...}`。

退路：有客户端连不上时，立即撤掉密码。这一步不重启、不丢数据，排查完再重做本节：

```bash
ssh mini 'bash -s' <<'EOF'
docker exec patra-redis sh -c 'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli CONFIG SET requirepass ""'
EOF
```

撤掉只在本次容器运行期间有效，redis 容器重启后又会带上密码。不重启就恢复密码：

```bash
ssh mini 'bash -s' <<'EOF'
docker exec patra-redis sh -c 'redis-cli CONFIG SET requirepass "$REDIS_PASSWORD"'
EOF
```

## 6. 验收（对应 PAP-66 的 AC）

### 6.1 identity 已部署并注册到 Nacos

```bash
ssh mini 'docker inspect -f "{{.Config.Image}} {{.State.Health.Status}}" patra-identity; docker logs patra-identity 2>&1 | grep -m1 "register finished"'
```

预期：

- `ghcr.io/linqibin0826/patra-identity:<sha> healthy`。
- 一行含 `patra-identity` 和 `register finished` 的 Nacos 注册日志。

### 6.2 库存在，Flyway 执行成功

```bash
ssh mini 'docker exec patra-postgres psql -U postgres -d patra_identity -tAc "SELECT count(*), bool_and(success) FROM flyway_schema_history"'
```

预期：`<迁移数>|t`。

### 6.3 Redis 不带密码连不上，带密码的服务正常

第 5 节最后一组命令。

### 6.4 仓库里没有 Redis 密码和签名私钥，模板齐全

```bash
git grep -nE 'REDIS_PASSWORD=[0-9a-f]{16,}|"d" *: *"'; echo "exit=$?"
git grep -q -F -f <(sed -n 's/^REDIS_PASSWORD=//p' ~/.patra/secrets/redis.env); echo "exit=$?"
git grep -q -F -f <(sed 's/^PATRA_GATEWAY_IDENTITY_ASSERTION_PRIVATE_KEY=//' ~/.patra/secrets/gateway.env | jq -r .d); echo "exit=$?"
git log -p --since=2026-10-10 | grep -c -F -f <(sed -n 's/^REDIS_PASSWORD=//p' ~/.patra/secrets/redis.env)
git log -p --since=2026-10-10 | grep -c -F -f <(sed 's/^PATRA_GATEWAY_IDENTITY_ASSERTION_PRIVATE_KEY=//' ~/.patra/secrets/gateway.env | jq -r .d)
ls patra-infra/docker/secrets/
```

预期：三个 `exit=1`、两个 `0`，最后是 `gateway.env.example  redis.env.example`。

### 6.5 Redis 重启后，重启前登录的会话仍然有效

```bash
ssh mini 'bash -s' <<'EOF'
GW=http://127.0.0.1:9528/patra-identity
EMAIL="pap66-restart-$(date +%s)@example.com"; PW="$(openssl rand -hex 12)"
T="$(curl --noproxy '*' -s -X POST "$GW/auth/register" -H 'Content-Type: application/json' \
  -d "{\"email\":\"${EMAIL}\",\"password\":\"${PW}\",\"clientType\":\"web\"}" | jq -r .sessionToken)"
echo "重启前：$(curl --noproxy '*' -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer ${T}" "$GW/auth/me")"
docker restart patra-redis > /dev/null
for i in $(seq 1 30); do [ "$(docker inspect -f '{{.State.Health.Status}}' patra-redis)" = healthy ] && break; sleep 2; done
sleep 3
echo "重启后：$(curl --noproxy '*' -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer ${T}" "$GW/auth/me")"
EOF
```

预期：`重启前：200`、`重启后：200`。

### 6.6 经 mini 的网关能注册、登录、取当前用户，后台和 actuator 被拒

```bash
ssh mini 'bash -s' <<'EOF'
GW=http://127.0.0.1:9528/patra-identity
code() { curl --noproxy '*' -s -o /dev/null -w '%{http_code}' "$@"; }
EMAIL="pap66-accept-$(date +%s)@example.com"; PW="$(openssl rand -hex 12)"
BODY="{\"email\":\"${EMAIL}\",\"password\":\"${PW}\",\"clientType\":\"web\"}"
echo "注册：$(code -X POST "$GW/auth/register" -H 'Content-Type: application/json' -d "$BODY")"
R="$(curl --noproxy '*' -s -w '\n%{http_code}' -X POST "$GW/auth/login" -H 'Content-Type: application/json' -d "$BODY")"
echo "登录：$(tail -n1 <<<"$R")"; T="$(head -n1 <<<"$R" | jq -r .sessionToken)"
echo "当前用户：$(code -H "Authorization: Bearer ${T}" "$GW/auth/me")"
echo "admin：$(code -H "Authorization: Bearer ${T}" "$GW/admin/users")"
echo "actuator：$(code "$GW/actuator/health")"
EOF
```

预期：`201`、`200`、`200`、`403`、`403`。

### 6.7 runbook 覆盖三项手工步骤

建库见第 2 节，Redis 加密码见第 5 节，签名密钥的生成、复制和更换见 `patra-infra/docker/README.md`「密钥」。
````

- [ ] **Step 2: 改 patra-infra/docker/README.md**

1. 「结构」代码块里，在 `.env.secret` 那一行后面加：

```text
├── secrets/                          # 密钥模板（*.example）；真文件在仓库外的 ~/.patra/secrets/
```

2. 「首次部署 / 步骤」里，在 `# 4b.` 那一段之后、`# 5.` 之前插入：

```bash
# 4c. (MacBook) 把密钥文件复制到 Mac mini：与 MacBook 上的是同一份，生成方法见下文「密钥」。
#     目录由第 4 步建好；缺了这两个文件，core 栈的 redis 和整个 apps 栈都起不来
scp -p ~/.patra/secrets/redis.env ~/.patra/secrets/gateway.env linqibin@linqibins-mac-mini:.patra/secrets/
```

3. 在「日常迭代」一节结束后（`---` 之前）插入新的一节：

````markdown
## 密钥

密钥放在仓库外的 `~/.patra/secrets/`（目录 0700，文件 0600），MacBook 和 Mac mini 各放一份，内容相同。

- 两边放同一份，是因为本机开发和 mini 是同一个环境，共用 mini 的 Redis 和同一对签名密钥。
- 不放在 compose 目录，是因为 CD 每次运行前的 `git clean` 会删掉 runner 工作区里被忽略的文件。

| 文件 | 内容 | 谁加载 | 缺失时 |
|---|---|---|---|
| `redis.env` | `REDIS_PASSWORD`，64 位十六进制 | core 栈的 redis；catalog、identity、gateway 的容器；这三个服务在本机以 dev profile 起的进程 | compose 报出路径并拒绝执行；本机进程启动失败并报出路径 |
| `gateway.env` | `PATRA_GATEWAY_IDENTITY_ASSERTION_PRIVATE_KEY`，含私钥的 EC P-256 JWK JSON，一行 | gateway 的容器和本机进程 | 同上 |
| `<服务>.env` | 该服务的外部数据源密钥，例如 catalog 的 `SCOPUS_API_KEY` | 对应服务的容器 | 跳过 |

两种进程怎么读到这些文件：

- 模板在 `secrets/*.example`。
- 容器：由 `docker-compose.apps.yaml`、`docker-compose.core.yaml` 的 `env_file` 按 `${HOME}/.patra/secrets/...` 加载。
- 本机进程：由各服务 `application-dev.yml` 的 `spring.config.import` 读同一批文件，IDEA 和 shell 都不用设这些变量。

### 生成

在 MacBook 的仓库根目录执行。全程只写文件，不要把密钥打印到终端或粘进任何地方。

```bash
umask 077 && mkdir -p ~/.patra/secrets
printf 'REDIS_PASSWORD=%s\n' "$(openssl rand -hex 32)" > ~/.patra/secrets/redis.env

K="$(mktemp -u ~/.patra/secrets/key.XXXXXX)"   # 任务要求私钥文件事先不存在
./gradlew -q :patra-starters:patra-spring-boot-starter-security:generateIdentityAssertionKey -PkeyOut="$K" \
  | tail -1 > /tmp/patra-identity-assertion-public.jwks   # 前两行是提示，最后一行是公钥 JWK Set
{ printf 'PATRA_GATEWAY_IDENTITY_ASSERTION_PRIVATE_KEY='; cat "$K"; } > ~/.patra/secrets/gateway.env
rm -f "$K"
```

公钥不是机密，提交在三处：

- `.env.common` 的 `PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS`；
- identity 的 `application-dev.yml`；
- gateway 的 `application-dev.yml`。

两份 YAML 里的值要用单引号包住。

### 复制到 Mac mini

```bash
ssh linqibin@linqibins-mac-mini 'umask 077 && mkdir -p ~/.patra/secrets && chmod 700 ~/.patra/secrets'
scp -p ~/.patra/secrets/redis.env ~/.patra/secrets/gateway.env linqibin@linqibins-mac-mini:.patra/secrets/
```

### 改了密钥文件之后

容器只在创建时读环境文件，`docker restart` 不会重新读，要用 `--force-recreate` 重建对应容器。

apps 栈的服务要在 runner 工作区的 compose 目录里重建，不要在 CD 运行期间执行。以 gateway 为例：

```bash
cd ~/actions-runner/_work/patra/patra/patra-infra/docker
GATEWAY_IMAGE_TAG="$(cat ~/.patra/cd/last-good-gateway)" docker compose -f docker-compose.apps.yaml up -d --force-recreate gateway
```

本机以 dev profile 起的进程重启一次即可。

### 换签名密钥

1. 按上面的方法生成一对新密钥。新私钥先存成别的文件名，不要覆盖 `gateway.env`。
2. 把新公钥加进三处的 `keys` 数组，旧公钥保留。提交、推送，让 CD 部署 identity 和 gateway。

   此时新旧两把公钥都被信任，网关仍用旧私钥签。
3. 两台机器上都把 `gateway.env` 换成新私钥。
   - mini：按「改了密钥文件之后」重建 gateway。
   - 本机：网关重启一次。
4. 等至少 60 秒。断言有效期是 60 秒，等完旧私钥签出的断言就全部过期了。
5. 从三处删掉旧公钥，提交、推送。
````

4. 「服务访问 URL / 核心服务」里的

```markdown
- **Redis**: `linqibins-mac-mini:16379`
```

改成

```markdown
- **Redis**: `linqibins-mac-mini:16379`（要密码：用户 `default`，密码在 `~/.patra/secrets/redis.env`）
```

- [ ] **Step 3: 改 patra-infra/CLAUDE.md**

1. 第 32 行表格里的 `registry / object-storage / catalog / ingest / gateway / portal / learn` 改成 `registry / object-storage / catalog / ingest / identity / gateway / portal / learn`。
2. 第 51 行（第 4 条）后面加第 5 条：

```markdown
5. **CD 在 runner 工作区里执行，被 git 忽略的文件每次都会被清掉。** `actions/checkout` 默认 `clean: true`，每次运行前执行 `git clean -ffdx`，放在 compose 目录里的密钥文件活不过下一次部署。所以密钥放在仓库外的 `~/.patra/secrets/`，compose 按 `${HOME}` 绝对路径加载（runner 进程的 `HOME` 就是用户主目录），见 README「密钥」。
```

3. 第 55 行：`5 个后端应用 + portal + learn` 改成 `6 个后端应用 + portal + learn`。
4. 第 57 行：在 `workflow 逻辑不变。` 后面加一句：条目顺序就是 `deploy.sh` 的部署顺序。
5. 第 59 行：`→ **重建全部 5 个**` 改成 `→ **重建全部 6 个**`。
6. 第 61 行：`→ 依赖顺序 up（**object-storage 优先**）` 改成 `→ 按 services.json 的条目顺序 up（**object-storage 最先**，identity 先于 gateway）`。
7. 第 62 行：`一份供 5 服务共用` 改成 `一份供 6 服务共用`，`5 服务都用` 改成 `6 服务都用`。
8. 第 69 行整条改成：

```markdown
- **环境文件三层，密钥在仓库外**：`env_file` 顺序叠加 `.env.common`（共享基建坐标：patra-net 服务名、内网 dev 默认、Redis 地址、身份断言公钥）→ `.env.<svc>`（服务专属 DB/bucket/日志路径）→ `${HOME}/.patra/secrets/` 下的密钥文件（`redis.env` 给 catalog/identity/gateway、`gateway.env` 给 gateway，两者必需；`<svc>.env` 可选；后者覆盖同名）。**Redis 密码、网关私钥、外部数据源 API key（Scopus / 青果 proxy / RocketMQ ACL 等）一律只进 `~/.patra/secrets/`**，committed 文件只放内网 dev 默认值和公钥。必需文件缺一个，compose 对整个 apps 项目的命令都会失败（portal 也部署不了）。
```

9. 第 75 行 scripts 表：`首次部署建数据卷目录骨架（幂等）` 改成 `首次部署建数据卷目录骨架和密钥目录 ~/.patra/secrets（幂等，不生成密钥）`。

- [ ] **Step 4: 改服务清单类的三处**

1. `.claude/rules/project-info.md` 的「核心服务」里，在 `patra-object-storage` 那一行后面加：

```markdown
- `patra-identity` - 身份服务 (前台用户的注册、登录、会话、封禁)
```

2. `README.md` 第 168 行：`（registry / ingest / catalog / object-storage / gateway）` 改成 `（registry / ingest / catalog / object-storage / identity / gateway）`。
3. `patra-api/README.md` 的「微服务」表，在 `patra-ingest` 那一行后面加三行：

```markdown
| [**patra-catalog**](./patra-catalog/README.md) | 文献、期刊、主题词等目录数据 | `patra-catalog-boot` |
| [**patra-object-storage**](./patra-object-storage/README.md) | 上传到 MinIO/S3 的文件元数据，只对内 | `patra-object-storage-boot` |
| [**patra-identity**](./patra-identity/README.md) | 前台用户的账号：注册、登录、会话、封禁 | `patra-identity-boot` |
```

- [ ] **Step 5: 改 identity 和 gateway 的 README**

1. `patra-api/patra-identity/README.md` 第 7 节第 88 行：

```markdown
- dev 配置连 mini 上的 `patra_identity` 库和 Redis（`PATRA_INFRA_HOST`）。库由 PAP-66 建，建好之前本地起不来。
```

改成

```markdown
- dev 配置连 mini 上的 `patra_identity` 库和 Redis（`PATRA_INFRA_HOST`），和 mini 上的容器共用同一个 Redis 0 号库。Redis 密码从 `~/.patra/secrets/redis.env` 读（`application-dev.yml` 的 `spring.config.import`），缺了启动失败并报出路径；生成和复制见 `patra-infra/docker/README.md`「密钥」。
```

2. 同一节第 97～104 行，从 `identity 从网关签的身份断言取当前用户，启动时必须配网关的公钥：` 到 `私钥文件交给本地网关后删掉。集成测试不需要这一步：安全 starter 的测试支持会自动注入测试公钥。`，整段改成：

```markdown
identity 从网关签的身份断言取当前用户，要配网关的公钥：dev 直接写在 `application-dev.yml` 里，容器从
`.env.common` 的 `PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS` 读，两处同值，换密钥时一起改。集成测试不读这些：
安全 starter 的测试支持会自动注入测试公钥。

容器里的其余变量：库连接来自 `.env.identity`，`REDIS_HOST`、`REDIS_PORT` 来自 `.env.common`，
`REDIS_PASSWORD` 来自 `~/.patra/secrets/redis.env`。
```

3. `patra-api/patra-gateway-boot/README.md` 的「配置」一节，从表头 `| 环境变量 | 说明 | 默认值 |` 到 `export PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS=...` 所在代码块的结束（第 104～122 行），整段改成：

```markdown
| 环境变量 | 说明 | 默认值 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | profile | `dev` |
| `NACOS_HOST` / `NACOS_PORT` / `NACOS_USERNAME` / `NACOS_PASSWORD` | Nacos | 跟随 `PATRA_INFRA_HOST`、`8848`、`nacos` / `nacos` |
| `TAILSCALE_IP` | dev 下向 Nacos 注册的 IP | 空 |
| `REDIS_HOST` / `REDIS_PORT` | 会话所在的 Redis（container） | 无，来自 `.env.common`；dev 固定为 `${PATRA_INFRA_HOST:127.0.0.1}` / `16379` |
| `REDIS_PASSWORD` | Redis 密码，用户 `default` | 无，来自 `~/.patra/secrets/redis.env` |
| `PATRA_GATEWAY_IDENTITY_ASSERTION_PRIVATE_KEY` | 签断言的私钥，含私钥的 EC P-256 JWK JSON，带 `kid` | 无，来自 `~/.patra/secrets/gateway.env`；缺了启动失败 |
| `PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS` | 网关自己那把公钥，JWK Set JSON | container 来自 `.env.common`；dev 直接写在 `application-dev.yml` |
| `PATRA_LOG_DIR` | 日志目录 | `logs` |

端口 9528。

- 容器：`REDIS_PASSWORD` 和私钥由 compose 从 `~/.patra/secrets/` 加载。
- 本地以 dev profile 启动：`application-dev.yml` 用 `spring.config.import` 读同一批文件，不用设环境变量；缺文件时启动失败并报出路径。
- 密钥的生成、复制和换密钥的顺序，见 `patra-infra/docker/README.md`「密钥」。
```

- [ ] **Step 6: 改两份设计文档**

1. `docs/patra/release-specs/v0.8-accounts.md` 第 161 行（「G. 密钥与凭据不进仓库」的「决策」）改成：

```markdown
- **决策**：Redis 密码、网关签身份断言的私钥放在仓库外的 `~/.patra/secrets/`，容器由 compose 按绝对路径加载，本机进程由 dev profile 导入同一批文件；仓库里只保留 `.example` 模板。对应的公钥不是机密，随各下游的配置给出。会话的真相在 Redis 里，所以 Redis 加密码是本版的硬前提。
- **说明**（2026-10-10）：原定的 `.env.*.secret` 放在 compose 目录里，而 CD 每次运行前的 `git clean` 会删掉 runner 工作区里被忽略的文件，这套机制在 CD 下从来没生效过，所以改放仓库外。见 `docs/patra/specs/2026-10-10-identity-deploy-design.md` 第 5 节。
```

2. `docs/patra/specs/2026-10-09-gateway-auth-design.md` 第 15 节表格第一行里的 `、`GATEWAY_REDIS_URL`（带密码）。` 改成 `、Redis 的地址和密码（变量由 PAP-66 定稿，见 `2026-10-10-identity-deploy-design.md` 第 6.2 节）。`。

- [ ] **Step 7: 自查**

Run: `git grep -n 'GATEWAY_REDIS_URL\|\.env\.\*\.secret\|env\.apps\.secret' -- ':!docs/patra/specs/*' ':!docs/patra/plans/*' ':!docs/patra/runbooks/v0.6-*'; echo "exit=$?"`

Expected: 只剩两行。
- `docs/patra/release-specs/v0.8-accounts.md` 第 162 行的「说明」，这是有意保留的历史说明。
- `patra-infra/docker/.gitignore` 里 `.env.*.secret` 那条兜底规则。

设计文档、计划和 v0.6 runbook 是历史记录，只读保留，不在检查范围内。

- [ ] **Step 8: 提交**

```bash
git add docs/patra/runbooks/v0.8-accounts-go-live-runbook.md patra-infra/docker/README.md patra-infra/CLAUDE.md \
  .claude/rules/project-info.md README.md patra-api/README.md patra-api/patra-identity/README.md \
  patra-api/patra-gateway-boot/README.md docs/patra/release-specs/v0.8-accounts.md \
  docs/patra/specs/2026-10-09-gateway-auth-design.md
git diff --cached --stat
git commit -F - <<'EOF'
docs(infra): 新增 v0.8 上线 runbook，各文档同步密钥位置和 identity 部署 (PAP-66)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
```

---

### Task 7: 分支门禁

**Files:** 无改动。只有 spotless 改了文件时才提交。

**Interfaces:**
- Consumes: Task 1～6 的全部改动。
- Produces: 第二部分可以开始的依据。

- [ ] **Step 1: 格式和全量检查**

```bash
./gradlew spotlessApply -q && git status --short
L="$(mktemp)"; ./gradlew check --no-configuration-cache > "$L" 2>&1; echo "check exit=$?"; tail -5 "$L"
```

Expected:
- `git status` 没有输出：本 Issue 没改 Java，spotless 不该动文件。如果它改了文件，那是改动之前就有的偏差：不提交，报告给用户。
- `check exit=0`，日志末尾是 `BUILD SUCCESSFUL`。

- [ ] **Step 2: 集成测试与 catalog 的端到端测试**

```bash
L="$(mktemp)"
./gradlew :patra-api:patra-gateway-boot:integrationTest :patra-api:patra-identity:patra-identity-boot:integrationTest \
  :patra-api:patra-identity:patra-identity-session:integrationTest :patra-api:patra-catalog:patra-catalog-boot:e2eTest \
  > "$L" 2>&1; echo "exit=$?"; tail -5 "$L"
grep -rhoE 'profiles? (is|are) active: [^<]*' patra-api/patra-catalog/patra-catalog-boot/build/test-results/e2eTest \
  | sed 's/&quot;/"/g' | sort | uniq -c
```

Expected:
- `exit=0`。
- catalog 的计数行只有 e2e 用的 profile（如 `"e2e-test"`），不出现 `dev`。

catalog-boot 没有 `integrationTest`，所以用 `e2eTest` 确认它的测试 profile 不受影响。

- [ ] **Step 3: 模块图没有变化**

Run: `./gradlew dumpModuleGraph -q && git diff --exit-code patra-infra/cd/module-graph.json && echo "模块图无变化"`

Expected: `模块图无变化`

- [ ] **Step 4: CD 脚本与路径归类**

```bash
bash patra-infra/cd/deploy.test.sh | tail -1
bash patra-infra/cd/detect-changes.test.sh | tail -1
shellcheck patra-infra/cd/deploy.sh patra-infra/cd/deploy.test.sh patra-infra/scripts/init-volumes.sh && echo "shellcheck 通过"
for f in patra-infra/docker/docker-compose.apps.yaml patra-infra/docker/.env.common patra-infra/docker/.env.identity \
         patra-infra/cd/deploy.sh patra-infra/docker/docker-compose.core.yaml patra-infra/scripts/init-volumes.sh \
         patra-infra/docker/secrets/redis.env.example patra-infra/docker/postgres/init-scripts/02-create-databases.sql; do
  printf '%-66s %s\n' "$f" "$(printf '%s\n' "$f" | bash patra-infra/cd/detect-changes.sh classify | jq -c '{full_run, backend_units}')"
done
git diff --name-only origin/main...HEAD | bash patra-infra/cd/detect-changes.sh classify | jq -c '{full_run, backend_units}'
```

Expected:
- `PASS=6 FAIL=0`、`通过 44 / 失败 0`、`shellcheck 通过`。
- 前四个文件是 `{"full_run":true,"backend_units":["registry","object-storage","catalog","ingest","identity","gateway","foundation"]}`。
- 后四个文件是 `{"full_run":false,"backend_units":[]}`。
- 最后一行，也就是推送后 CD 要做的事，是 `full_run` 为 `true` 的全量。

- [ ] **Step 5: 仓库里没有密钥**

```bash
git grep -nE 'REDIS_PASSWORD=[0-9a-f]{16,}|"d" *: *"'; echo "exit=$?"
git grep -q -F -f <(sed -n 's/^REDIS_PASSWORD=//p' ~/.patra/secrets/redis.env); echo "exit=$?"
git grep -q -F -f <(sed 's/^PATRA_GATEWAY_IDENTITY_ASSERTION_PRIVATE_KEY=//' ~/.patra/secrets/gateway.env | jq -r .d); echo "exit=$?"
git log -p main..HEAD | grep -c -F -f <(sed -n 's/^REDIS_PASSWORD=//p' ~/.patra/secrets/redis.env)
git log -p main..HEAD | grep -c -F -f <(sed 's/^PATRA_GATEWAY_IDENTITY_ASSERTION_PRIVATE_KEY=//' ~/.patra/secrets/gateway.env | jq -r .d)
```

Expected: 三个 `exit=1`，然后两个 `0`。

这一步只输出退出码和计数，不会打印密钥。第一部分到此结束，接着按 executing-plans 做最终评审。

---

# 第二部分：上线

照 runbook（Task 6 第 1 步写入的 `docs/patra/runbooks/v0.8-accounts-go-live-runbook.md`）执行。

本部分只补充 runbook 里没有的执行要求：

- 每一步改动 mini 之前先向用户确认。
- 本机进程怎么用命令行起、怎么停。
- 结果记到哪里。

把每一步的实际输出记下来，留给上线 6 写回。

### 上线 1：mini 放密钥文件（runbook 第 1 节）

- [ ] 向用户确认后，执行 runbook 第 1 节的两组命令。
- [ ] 核对预期：第一组输出 `0`；两个文件都是 `-rw-------`；最后一行是 `两台机器内容一致`。

### 上线 2：mini 建库（runbook 第 2 节）

- [ ] 执行第 2 节的只读检查。预期是 `6400 空闲`、`0`。
- [ ] 向用户确认后执行 `CREATE DATABASE`。预期是 `CREATE DATABASE`。

### 上线 3：本机冒烟（runbook 第 3 节）

- [ ] **Step 1: 起两个进程**

先确认端口空着：`lsof -nP -iTCP:6400 -sTCP:LISTEN; lsof -nP -iTCP:9528 -sTCP:LISTEN`。

预期没有输出。如果有输出，可能是用户自己在 IDEA 里起的服务：停下来问用户，不要杀。

两个进程分别用 Bash 工具的 `run_in_background` 起，命令里先把环境配好：

```bash
source ~/.config/zsh/env.zsh
export JAVA_TOOL_OPTIONS="-Dhttp.nonProxyHosts=localhost|127.*|100.*|192.168.*|172.*|10.* -Dhttps.nonProxyHosts=localhost|127.*|100.*|192.168.*|172.*|10.* -Dsocks.nonProxyHosts=localhost|127.*|100.*|192.168.*|172.*|10.*"
set -a; eval "$(grep -E '^NACOS_(USERNAME|PASSWORD)=' patra-infra/docker/.env.common)"; set +a
exec java -jar patra-api/patra-identity/patra-identity-boot/build/libs/patra-identity-boot-0.1.0-SNAPSHOT.jar \
  --spring.profiles.active=dev > "${TMPDIR:-/tmp}/pap66-identity.log" 2>&1
```

gateway 那一条照抄这四行，只把最后一条命令换成：

```bash
exec java -jar patra-api/patra-gateway-boot/build/libs/patra-gateway-boot-0.1.0-SNAPSHOT.jar \
  --spring.profiles.active=dev > "${TMPDIR:-/tmp}/pap66-gateway.log" 2>&1
```

启动包用 Task 5 第 7 步构建的那两个。如果之后又改过代码，先重新执行 `bootJar`。

- [ ] **Step 2: 等启动并核对 kid**

```bash
for i in $(seq 1 90); do
  grep -q 'Started PatraIdentityApplication' "${TMPDIR:-/tmp}/pap66-identity.log" \
    && grep -q 'Started PatraGatewayApplication' "${TMPDIR:-/tmp}/pap66-gateway.log" && break
  sleep 2
done
grep -ho 'Started Patra[A-Za-z]* in [0-9.]* seconds' "${TMPDIR:-/tmp}/pap66-identity.log" "${TMPDIR:-/tmp}/pap66-gateway.log"
grep -o '身份断言签名密钥就绪，kid=[A-Za-z0-9_-]*' "${TMPDIR:-/tmp}/pap66-gateway.log"
sed -n 's/^PATRA_IDENTITY_ASSERTION_PUBLIC_KEYS=//p' patra-infra/docker/.env.common | jq -r '.keys[0].kid'
```

Expected:
- 两行 `Started ...`。
- 网关日志里的 kid 与最后一行输出相同。

起不来时，看两个日志的末尾 40 行。

- [ ] **Step 3: 走一遍注册、当前用户、登出**

执行 runbook 第 3 节第 2 步的代码块，预期是 `401`、`201`、`200`、`204`、`401`。

这一步同时证明了 spec 第 13 节第 2 条的 Lettuce 部分：mini 的 Redis 还没有密码，只有带用户名的认证会成功。

- [ ] **Step 4: 停掉两个进程**

Run: `pkill -f 'patra-(identity|gateway)-boot-0.1.0-SNAPSHOT.jar'; sleep 3; pgrep -f 'patra-(identity|gateway)-boot-0.1.0-SNAPSHOT.jar' || echo "已停"`

Expected: `已停`。正常停止时，进程会从 Nacos 注销，mini 的网关就不会再把请求分给本机。

### 上线 4：本地合并，推送 main，CD 部署（runbook 第 4 节）

- [ ] **Step 1: 本地合并**

用 superpowers:finishing-a-development-branch 收尾，向用户给出三个选项。

本版本到目前为止的做法是选项 1：在本地快进合并到 main，保留版本分支。这个做法不用切换主检出：

Run: `git fetch . feat/v0.8-accounts-api:main && git log --oneline -1 main`

Expected: main 快进到分支的最新提交。

如果被拒，说明 main 不是分支的祖先：停下来报告用户。

- [ ] **Step 2: 推送**

等用户在当轮明确说推送后，执行 `git push origin main`。

- [ ] **Step 3: 看 CD 并核对**

执行 runbook 第 4 节推送后的命令。`gh run watch` 用 `run_in_background` 跑，完成后会收到通知。

Expected：见 runbook 第 4 节。另外两项也要看，结果记作 spec 第 13 节第 2 条的 Redisson 部分：

- catalog 在新镜像上通过了健康检查；
- deploy 日志里没有 catalog 的自动回滚。

失败时按 runbook 第 4 节的退路处理，然后停下来报告用户。

### 上线 5：mini 上 Redis 加密码（runbook 第 5 节）

- [ ] **Step 1: 准备检查**

执行第 5 节的 `git pull` 和 `--dry-run` 两条。`git pull` 改动的是 mini 上的检出，执行前向用户确认。

预期：分支 `main` 快进；`--dry-run` 只有 `patra-redis` 是 `Recreate`。

- [ ] **Step 2: 切换并测量**

向用户确认后，执行第 5 节的切换脚本。记下状态码分布和非 200 的起止时刻。

- [ ] **Step 3: 核对**

执行第 5 节最后一组命令。预期是 `-NOAUTH Authentication required.`、`healthy`，三个服务都是 UP。

客户端连不上时，按退路撤掉密码，然后报告用户。

### 上线 6：验收、写回、Linear

- [ ] **Step 1: 执行 runbook 第 6 节的 6.1～6.6**

逐项记下输出。6.5 会重启 mini 的 redis，执行前向用户确认。

AC 里「`module-graph.json` 与构建配置一致」看推送后 main 上的 CI：

```bash
CI_ID="$(gh run list --workflow ci.yml --branch main -L 1 --json databaseId -q '.[0].databaseId')"
gh run view "$CI_ID" --log | grep -m1 'module-graph.json 与依赖图一致'
```

Expected: 一行含 `✓ module-graph.json 与依赖图一致`。CI 还在跑时，用 `gh run watch "$CI_ID"` 以 `run_in_background` 等它结束。

- [ ] **Step 2: 本机在 Redis 有密码之后照常工作**

按上线 3 的第 1～4 步再起一次本机的 identity 和 gateway，走一遍，然后停掉。预期结果同上线 3。

这一步证明本机进程读到的密码是对的：现在 mini 的 Redis 只接受正确的密码。

- [ ] **Step 3: Argon2 在 mini 上的单次耗时**

预编译一个计时类，复制进 identity 容器，用镜像自带的 JRE 和 `/app/lib` 的依赖运行。参数与 `PasswordHashingAdapter` 相同。这个类用完即删，不进仓库。

```bash
A="$(mktemp -d)"
cat > "$A/Argon2Timing.java" <<'EOF'
import java.util.Arrays;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

/// PAP-66 一次性测量：Argon2 单次哈希耗时，参数与 PasswordHashingAdapter 一致。不进仓库。
public final class Argon2Timing {

  /// 预热 5 次，再计时 20 次，打印中位数、最快和最慢。
  ///
  /// @param args 不用
  public static void main(String[] args) {
    Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
    for (int i = 0; i < 5; i++) {
      encoder.encode("warm-up-" + i);
    }
    long[] nanos = new long[20];
    for (int i = 0; i < nanos.length; i++) {
      long start = System.nanoTime();
      encoder.encode("timing-" + i);
      nanos[i] = System.nanoTime() - start;
    }
    Arrays.sort(nanos);
    System.out.printf(
        "Argon2id m=19456 t=2 p=1，20 次：中位数 %.1f ms，最快 %.1f ms，最慢 %.1f ms%n",
        (nanos[9] + nanos[10]) / 2e6, nanos[0] / 1e6, nanos[19] / 1e6);
  }
}
EOF
mkdir -p "$A/lib" "$A/out"
unzip -j -o -q patra-api/patra-identity/patra-identity-boot/build/libs/patra-identity-boot-0.1.0-SNAPSHOT.jar \
  'BOOT-INF/lib/spring-security-crypto-*.jar' -d "$A/lib"
javac --release 25 -cp "$A/lib/*" -d "$A/out" "$A/Argon2Timing.java"
scp "$A/out/Argon2Timing.class" mini:/tmp/
ssh mini 'docker cp /tmp/Argon2Timing.class patra-identity:/tmp/ && docker exec patra-identity java -cp "/tmp:/app/lib/*" Argon2Timing; docker exec -u 0 patra-identity rm -f /tmp/Argon2Timing.class; rm -f /tmp/Argon2Timing.class'
rm -rf "$A"
```

Expected: 一行 `Argon2id m=19456 t=2 p=1，20 次：中位数 <x> ms，最快 <y> ms，最慢 <z> ms`，量级是几十毫秒。

- [ ] **Step 4: 门户的匿名路径指向 mini 网关**

mini 的网关这次从 10/05 的 WebFlux 版直接换成了带鉴权的 WebMVC 版，这一步用来兜底。

```bash
lsof -nP -iTCP:4000 -sTCP:LISTEN || echo "4000 空闲"
env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy \
  PATRA_GATEWAY_BASE_URL=http://100.103.73.27:9528 \
  pnpm -C patra-portal exec playwright test tests/e2e/ --workers=1
```

Expected:
- `4000 空闲`。端口被占时，Playwright 会复用那个 dev server，而它连的网关不一定是 mini：先问用户。
- 全部通过，真实数据用例没有被 skip。

- [ ] **Step 5: 写回设计文档**

1. `docs/patra/specs/2026-10-10-identity-deploy-design.md`：
   - 头部的 `**状态**：设计中，待评审` 改成 `**状态**：已实施，已上线（<上线日期>）`。
   - 第 13 节「实施后把结果写回本节」下面加一张表，列为 `| # | 结果 | 怎么测的 |`，6 行，按本计划的实际输出填。
     - 第 1 条：用「写计划时已实测的点」和 Task 5 第 7 步的结果。
     - 第 2 条：上线 3 第 3 步（Lettuce）和上线 4 第 3 步（Redisson）。
     - 第 3 条：上线 5 第 2 步的状态码分布和持续时间。
     - 第 4 条：runbook 6.5。
     - 第 5 条：第 3 步。
     - 第 6 条：第 4 步。
2. `docs/patra/specs/2026-10-05-identity-account-design.md` 第 14 节第 1 条的「结果」列：把 `mini 上的耗时待 PAP-66 部署 identity 后补测` 改成 `mini 上中位数 <x> 毫秒（20 次，预热后，<日期>，PAP-66）`。
3. 提交，在分支上做，然后快进 main：

```bash
git add docs/patra/specs/2026-10-10-identity-deploy-design.md docs/patra/specs/2026-10-05-identity-account-design.md
git diff --cached --stat
git commit -F - <<'EOF'
docs(infra): 写回 PAP-66 上线的实测结果 (PAP-66)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
git fetch . feat/v0.8-accounts-api:main
```

推送等用户开口。只改了 `*.md`，cd.yml 的 `paths` 排除了它，不会触发部署。

- [ ] **Step 6: Linear**

按 spec 第 16 节更新 PAP-66：
- To-Do 的 Tech Design 指向本设计。
- 把第 4 节的决定和第 3 节第 1、2 条写进描述。
- 勾掉已完成的 To-Do 和 AC。

PAP-65 不动，它的 AC 留给用户勾。

- [ ] **Step 7: 收尾报告**

向用户报告以下内容：
- 上线结果。
- 第 13 节的 6 条实测。
- 执行中所有的 Ruling。
- 遗留的小问题。
