# 统一两种运行方式访问基础设施的路径 实施计划（PAP-72）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 mini 上的应用容器和 MacBook 的 IDE 用同一种写法连基础设施（`PATRA_INFRA_HOST` 加宿主机映射端口），两者的差异只剩 Nacos 之外的几个本地变量。

**Architecture:**
- 应用容器和 rocketmq-dashboard 加 `extra_hosts: linqibins-mac-mini:host-gateway`，容器里的主机名指向 OrbStack 宿主机网关，不经过 tailscale。
- env 文件里的坐标从"容器名加容器内端口"换成"主机名加映射端口"。
- 应用里的"对外通告地址"统一读 `PATRA_ADVERTISE_IP`。
- ingest 的 bucket 统一为 `patra-ingest-cache`。

**Tech Stack:** Docker Compose（OrbStack）、Spring Boot 4 配置占位符、Spring Cloud Alibaba Nacos Discovery、xxl-job executor、RocketMQ 5.3.1、MinIO mc。

**Spec:** `docs/patra/specs/2026-10-13-infra-access-unification-design.md`

## Global Constraints

- **本地变量**（spec 6.2）：

  | 变量 | IDE | 容器 |
  |---|---|---|
  | `PATRA_INFRA_HOST` | `100.103.73.27`（`env.zsh`） | `linqibins-mac-mini`（`.env.common`） |
  | `PATRA_ADVERTISE_IP` | `env.zsh` 里 `export PATRA_ADVERTISE_IP="$TAILSCALE_IP"` | 不设 |
  | `PATRA_LOG_DIR` | 不设 | `/app/logs` |
  | Nacos 凭据 | `~/.patra/secrets/nacos.env` | 同名文件 |

- **映射端口**：PG 15432、Redis 16379、Nacos 8848 / 9848、MinIO 19000、xxl-job-admin 7070（路径 `/xxl-job-admin`）、namesrv 9876、broker 10911。
- **`extra_hosts` 的值**：`linqibins-mac-mini:host-gateway`。加在 registry、object-storage、catalog、ingest、identity、gateway、rocketmq-dashboard 上；portal、learn 不加。
- **bucket**：ingest 统一为 `patra-ingest-cache`；catalog 的 `patra-catalog-cache`、`patra-catalog` 不变。
- **不在本 Issue 做**：去掉 `dev` / `container` profile、把配置搬进 Nacos、重命名 `NACOS_HOST`、动 `NACOS_USERNAME` 的默认值。
- **密钥不打印。** 不执行不带 `--quiet` 的 `docker compose config`，不对容器做完整的 `docker inspect`：两者都会把 Redis 密码和网关私钥打到终端。只取指定的非敏感键，或只列变量名。
- **mini 上的操作。** 改变 mini 状态的每一步，执行前先向用户确认；只读检查不用确认。推送 main 要等用户在当轮明确开口。
- **本机 `~/.config/zsh/env.zsh` 在仓库外**，由用户自己改，计划只给改法。
- **提交。**
  - 提交信息格式是 `type(scope): 中文开头的 subject (PAP-72)`。subject 不以类名、Issue key 或英文开头。
  - body 每行不超过 100 个字符，最后一行是 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。
  - 只暂存本任务列出的路径，提交前看一眼 `git diff --cached --stat`。
  - 命令文本里不要出现推送命令的字面量，包括 heredoc 正文：会被 ship-gate 钩子整条拒绝。
- **路径。** 一律用绝对路径，不 `cd`。下文 `$R` 指 `/Users/linqibin/Projects/Products/patra`，`$D` 指 `$R/patra-infra/docker`；每条命令开头自己设 `R=...`。
- **TDD。** 本计划全是配置与文档改动，按项目约定不走 TDD。每个任务用带 `Expected:` 的 grep / compose / 启动检查验证。

## Review Focus

1. **用旧镜像回滚**：deploy.sh 用旧镜像配新 env 文件时，Nacos、namesrv、xxl 地址要能回退到 `PATRA_INFRA_HOST`。Task 2 第 5 步核对 main 上的 yml 默认值。
2. **`PATRA_ADVERTISE_IP` 为空**：容器不设这个变量，`discovery.ip` 解析成空串，必须退回自动探测，不能注册成空地址。Task 6 核对注册地址是 `192.168.97.x`。
3. **Nacos gRPC 长连接**：现在经 OrbStack 端口转发，空闲后不能掉线。Task 6 隔 10 分钟复查。
4. **xxl 执行器日志目录**：`XXL_JOB_EXECUTOR_LOG_PATH` 删掉后，容器里要算出 `/app/logs/xxl-job/<服务>`。Task 6 核对目录存在。
5. **catalog 的 batch 库**：`BATCH_DB_URL` 容易漏改。Task 2 的 grep 覆盖全部 DB URL。

---

### Task 1: compose（apps 的 `extra_hosts`、dashboard、minio-init）

**Files:**
- Modify: `patra-infra/docker/docker-compose.apps.yaml`
- Modify: `patra-infra/docker/docker-compose.jobs.yaml`
- Modify: `patra-infra/docker/docker-compose.storage.yaml`

**Interfaces:**
- Produces: 6 个后端服务和 rocketmq-dashboard 的容器里，`linqibins-mac-mini` 解析为宿主机网关；minio-init 建 `patra-ingest-cache`。

- [ ] **Step 1: apps compose 加锚点和文件头说明**

  在 `docker-compose.apps.yaml` 的 `x-app-healthcheck` 锚点之前加：

  ```yaml
  # 容器里把 mini 的主机名指向 OrbStack 宿主机网关：应用与 IDE 同用「主机名 + 宿主机映射端口」
  # 访问基础设施，且不经过 tailscale（见 docs/patra/specs/2026-10-13-infra-access-unification-design.md）。
  x-infra-hosts: &infra-hosts
    - "linqibins-mac-mini:host-gateway"

  ```

  文件头注释里 `.env.common` 那一行改为：

  ```
  #   .env.common   共享基建坐标（mini 主机名 + 宿主机映射端口，与 IDE 一致）和身份断言公钥，dev 默认
  ```

- [ ] **Step 2: 6 个后端服务引用锚点**

  在 registry、object-storage、catalog、ingest、identity、gateway 六个服务的 `ports:` 行之后各加一行（缩进 4 格）：

  ```yaml
      extra_hosts: *infra-hosts
  ```

  portal、learn 不加。

- [ ] **Step 3: rocketmq-dashboard 加 `extra_hosts`**

  `docker-compose.jobs.yaml` 的 `rocketmq-dashboard` 服务里，在 `ports:` 块之后加：

  ```yaml
      # broker 以 mini 主机名注册（BROKER_IP1），容器里要能解析它才能取 broker 统计
      extra_hosts:
        - "linqibins-mac-mini:host-gateway"
  ```

- [ ] **Step 4: minio-init 改建 `patra-ingest-cache`**

  `docker-compose.storage.yaml` 的 minio-init 命令里，把 3 处 `dev-ingest` 改为 `patra-ingest-cache`：`mc mb --ignore-existing`、`mc anonymous set none`、`echo 'Bucket: ...'`。提示语 `Creating default bucket for development environment...` 改为 `Creating ingest cache bucket...`。

- [ ] **Step 5: 校验 compose**

  ```bash
  R=/Users/linqibin/Projects/Products/patra; D=$R/patra-infra/docker
  docker compose -f $D/docker-compose.apps.yaml config --quiet && echo apps-ok
  docker compose -f $D/docker-compose.apps.yaml config --format json \
    | jq -r '.services | to_entries[] | "\(.key) \(.value.extra_hosts // [] | tostring)"'
  docker compose -f $D/docker-compose.jobs.yaml config --format json \
    | jq -r '.services["rocketmq-dashboard"].extra_hosts | tostring'
  grep -c 'dev-ingest' $D/docker-compose.storage.yaml
  ```

  Expected:
  - 第一行 `apps-ok`；
  - registry、object-storage、catalog、ingest、identity、gateway 6 行都带 `linqibins-mac-mini:host-gateway`，portal、learn 两行是 `[]`；
  - dashboard 一行带同样的映射；
  - 最后一行 `0`。

  compose 把 `host-gateway` 原样保留或展开成具体地址都算通过，只要 6 个后端服务和 dashboard 有、portal 和 learn 没有。

- [ ] **Step 6: 提交**

  ```bash
  R=/Users/linqibin/Projects/Products/patra
  git -C $R add patra-infra/docker/docker-compose.apps.yaml patra-infra/docker/docker-compose.jobs.yaml patra-infra/docker/docker-compose.storage.yaml
  git -C $R diff --cached --stat
  git -C $R commit -q -F - <<'EOF'
  feat(infra): 应用容器和 rocketmq-dashboard 经宿主机网关解析 mini 主机名 (PAP-72)

  容器里 linqibins-mac-mini 解析不了，broker 注册的地址在容器里一直连不上。
  extra_hosts 把它指向 OrbStack 宿主机网关，不经过 tailscale。minio-init 改建
  ingest 统一后的 bucket patra-ingest-cache。

  Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
  EOF
  ```

---

### Task 2: env 文件改为主机名加映射端口

**Files:**
- Modify: `patra-infra/docker/.env.common`
- Modify: `patra-infra/docker/.env.registry`、`.env.object-storage`、`.env.catalog`、`.env.ingest`、`.env.identity`

**Interfaces:**
- Consumes: Task 1 的 `extra_hosts`（没有它，容器里解析不了这些主机名）。
- Produces: 容器里 `PATRA_INFRA_HOST=linqibins-mac-mini`；不再有 `NACOS_HOST`、`NACOS_PORT`、`ROCKETMQ_NAMESRV`、`XXL_JOB_ADMIN_ADDRESSES`、`XXL_JOB_EXECUTOR_LOG_PATH`。

- [ ] **Step 1: `.env.common`**

  - 文件头注释 `# 坐标用 patra-net 容器服务名 + 容器内端口（非宿主映射端口）。` 改为：

    ```
    # 坐标用 mini 主机名 + 宿主机映射端口，与 IDE 的 dev 配置写法一致；容器经 compose 的 extra_hosts
    # 把 linqibins-mac-mini 解析到 OrbStack 宿主机网关，不经过 tailscale。
    ```

  - 在 `PATRA_LOG_DIR=/app/logs` 之后加：

    ```
    # 基础设施所在主机（IDE 里是 mini 的 tailscale IP；yml 里 Nacos、RocketMQ namesrv、xxl-job admin 的地址默认跟随它）
    PATRA_INFRA_HOST=linqibins-mac-mini
    ```

  - 删掉 Nacos 段的 `NACOS_HOST=nacos`、`NACOS_PORT=8848`。段标题改为 `# --- Nacos 服务发现（地址跟随 PATRA_INFRA_HOST:8848）---`，保留"账号在 nacos.env"那行注释。
  - MinIO 段：标题改为 `# --- MinIO 对象存储（宿主机映射端口 19000，内网 dev 默认凭据）---`，`MINIO_ENDPOINT=http://linqibins-mac-mini:19000`。
  - 删掉 RocketMQ 段和 xxl-job 段（两个标题、注释、`ROCKETMQ_NAMESRV`、`XXL_JOB_ADMIN_ADDRESSES`）。改为一行注释：

    ```
    # --- RocketMQ namesrv、xxl-job admin：yml 默认跟随 PATRA_INFRA_HOST（9876、7070），这里不设 ---
    ```

  - Redis 段：标题改为 `# --- Redis（宿主机映射端口 16379，所有服务共用 0 号库）---`，`REDIS_HOST=linqibins-mac-mini`，`REDIS_PORT=16379`。

- [ ] **Step 2: 各服务的 DB URL**

  把 `jdbc:postgresql://postgres:5432/` 换成 `jdbc:postgresql://linqibins-mac-mini:15432/`，涉及以下 6 行：

  - `.env.registry` 的 `REGISTRY_DB_URL`
  - `.env.object-storage` 的 `STORAGE_DB_URL`
  - `.env.catalog` 的 `CATALOG_DB_URL`、`BATCH_DB_URL`
  - `.env.ingest` 的 `INGEST_DB_URL`
  - `.env.identity` 的 `IDENTITY_DB_URL`

  这几个文件里的 `# 数据库（patra-net 服务名 postgres...）` 注释统一改为 `# 数据库（mini 主机名 + 宿主机映射端口 15432）`。

- [ ] **Step 3: 删掉 xxl 日志路径**

  删掉 `.env.catalog` 和 `.env.ingest` 里的 `XXL_JOB_EXECUTOR_LOG_PATH` 及其上方注释。yml 默认值 `${PATRA_LOG_DIR}/xxl-job/<服务>` 在容器里算出的就是同一个路径。

  两个文件第 1 行的"共享坐标"说明里去掉 `xxl-job admin`、`RocketMQ namesrv` 字样，改为"（Nacos / MinIO / Redis 地址等）"。

- [ ] **Step 4: 校验**

  ```bash
  R=/Users/linqibin/Projects/Products/patra; D=$R/patra-infra/docker
  grep -nE 'postgres:5432|NACOS_HOST|NACOS_PORT|ROCKETMQ_NAMESRV|XXL_JOB_ADMIN_ADDRESSES|XXL_JOB_EXECUTOR_LOG_PATH|=redis$|minio:9000' $D/.env.common $D/.env.registry $D/.env.object-storage $D/.env.catalog $D/.env.ingest $D/.env.identity $D/.env.gateway
  docker compose -f $D/docker-compose.apps.yaml config --format json | jq -r '
    .services | to_entries[] | select(.key|test("registry|object-storage|catalog|ingest|identity|gateway")) |
    "\(.key) \(.value.environment | with_entries(select(.key|test("^(PATRA_INFRA_HOST|REDIS_HOST|REDIS_PORT|MINIO_ENDPOINT|MINIO_BUCKET|[A-Z]+_DB_URL)$"))) | tostring)"'
  ```

  Expected:
  - grep 无输出（退出码 1）；
  - 6 行 JSON 都有 `"PATRA_INFRA_HOST":"linqibins-mac-mini"`、`"REDIS_HOST":"linqibins-mac-mini"`、`"REDIS_PORT":"16379"`、`"MINIO_ENDPOINT":"http://linqibins-mac-mini:19000"`；
  - 带 DB URL 的服务，DB URL 都是 `jdbc:postgresql://linqibins-mac-mini:15432/...`，catalog 有两个；
  - ingest 的 `MINIO_BUCKET` 是 `patra-ingest-cache`。

  jq 只输出这几个非敏感键，不会把密码打到终端。

- [ ] **Step 5: 核对旧镜像能配新 env 文件跑（Review Focus 1）**

  ```bash
  R=/Users/linqibin/Projects/Products/patra
  for f in $(git -C $R ls-tree -r --name-only main -- patra-api | grep -E 'boot/src/main/resources/(application\.yml|ingest-rocketmq\.yaml)$'); do
    git -C $R show main:$f | grep -oE '\$\{(NACOS_HOST|ROCKETMQ_NAMESRV|XXL_JOB_ADMIN_ADDRESSES):[^}]*\}[^ ]*' | sed "s|^|$f: |"
  done
  ```

  Expected:
  - 6 个 `application.yml` 都有 `${NACOS_HOST:${PATRA_INFRA_HOST:127.0.0.1}}`；
  - ingest 的 `ingest-rocketmq.yaml` 有 `${ROCKETMQ_NAMESRV:${PATRA_INFRA_HOST:127.0.0.1}:9876}`；
  - ingest 和 catalog 的 `application.yml` 有 `${XXL_JOB_ADMIN_ADDRESSES:http://${PATRA_INFRA_HOST:127.0.0.1}:7070/xxl-job-admin}`。

  即 main 上线中的镜像删掉这几个变量后，会回退到 `PATRA_INFRA_HOST` 加映射端口。

- [ ] **Step 6: 提交**

  ```bash
  R=/Users/linqibin/Projects/Products/patra
  git -C $R add patra-infra/docker/.env.common patra-infra/docker/.env.registry patra-infra/docker/.env.object-storage patra-infra/docker/.env.catalog patra-infra/docker/.env.ingest patra-infra/docker/.env.identity
  git -C $R diff --cached --stat
  git -C $R commit -q -F - <<'EOF'
  feat(infra): 应用容器改用 mini 主机名加映射端口访问基础设施 (PAP-72)

  与 IDE 的 dev 配置写法一致，差异只剩 PATRA_INFRA_HOST 的值。删掉 Nacos、
  namesrv、xxl admin 的地址变量，由 yml 默认值跟随 PATRA_INFRA_HOST；xxl 执行器
  日志目录改用 PATRA_LOG_DIR 推导。

  Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
  EOF
  ```

---

### Task 3: 应用配置读 `PATRA_ADVERTISE_IP`，ingest 的 bucket 统一

**Files:**
- Modify: 6 个服务的 `application.yml` 和 `application-dev.yml`：
  - `patra-api/patra-registry/patra-registry-boot/src/main/resources/`
  - `patra-api/patra-object-storage/patra-object-storage-boot/src/main/resources/`
  - `patra-api/patra-catalog/patra-catalog-boot/src/main/resources/`
  - `patra-api/patra-ingest/patra-ingest-boot/src/main/resources/`
  - `patra-api/patra-identity/patra-identity-boot/src/main/resources/`
  - `patra-api/patra-gateway-boot/src/main/resources/`

**Interfaces:**
- Produces: `spring.cloud.nacos.discovery.ip` 和 `xxl.job.executor.ip` 都读 `${PATRA_ADVERTISE_IP:}`；ingest 的 dev bucket 是 `patra-ingest-cache`。

- [ ] **Step 1: `discovery.ip` 移到 `application.yml`**

  6 个 `application.yml` 里，在 `discovery:` 下 `service: ${spring.application.name}` 之后加：

  ```yaml
          # 本实例通告给 Nacos 的地址。IDE 里是 MacBook 的 tailscale IP（env.zsh），容器里不设：
          # 留空时自动探测，容器拿到的是 patra-net 上的 IP。
          ip: ${PATRA_ADVERTISE_IP:}
  ```

  缩进与同级的 `service:` 一致（8 个空格）。

- [ ] **Step 2: 从 `application-dev.yml` 删掉旧配置**

  6 个 `application-dev.yml` 里删掉以下整块：

  ```yaml
    # dev 场景：用 TAILSCALE_IP 注册，确保跨主机服务发现拿到的 IP 可达。
    cloud:
      nacos:
        discovery:
          ip: ${TAILSCALE_IP:}
  ```

  删完后，若前后留下两个连续空行，压成一个。

- [ ] **Step 3: xxl 执行器地址**

  ingest 和 catalog 的 `application.yml` 里，`executor:` 下的三行注释和 `ip: ${XXL_JOB_EXECUTOR_IP:}` 换成：

  ```yaml
        # 回调地址：admin 按它回调本执行器。与 Nacos 注册地址同一个变量；容器里不设，自动探测 patra-net 上的 IP。
        ip: ${PATRA_ADVERTISE_IP:}
  ```

- [ ] **Step 4: ingest 的 dev bucket**

  `patra-ingest-boot` 的 `application-dev.yml` 里，`bucket: dev-ingest` 改为 `bucket: patra-ingest-cache`。

- [ ] **Step 5: 校验**

  ```bash
  R=/Users/linqibin/Projects/Products/patra
  grep -rnE 'TAILSCALE_IP|XXL_JOB_EXECUTOR_IP|dev-ingest' $R/patra-api --include='*.yml' --include='*.yaml' | grep -v /build/
  grep -rn 'PATRA_ADVERTISE_IP' $R/patra-api --include='application.yml' | grep -v /build/ | wc -l
  ```

  Expected: 第一条无输出；第二条输出 `8`（6 个 `discovery.ip` 加 2 个 `executor.ip`）。

- [ ] **Step 6: 编译和单元测试**

  ```bash
  R=/Users/linqibin/Projects/Products/patra
  $R/gradlew -p $R :patra-api:patra-registry:patra-registry-boot:test :patra-api:patra-ingest:patra-ingest-boot:test :patra-api:patra-catalog:patra-catalog-boot:test > $SP/task3-test.log 2>&1; echo "rc=$?"; tail -5 $SP/task3-test.log
  ```

  `$SP` 是会话 scratchpad 目录。Expected: `rc=0`，`BUILD SUCCESSFUL`。


- [ ] **Step 7: 提交**

  ```bash
  R=/Users/linqibin/Projects/Products/patra
  git -C $R add $(git -C $R diff --name-only -- patra-api)
  git -C $R diff --cached --stat
  git -C $R commit -q -F - <<'EOF'
  feat(api): 通告地址统一读 PATRA_ADVERTISE_IP，ingest 本机 bucket 与容器一致 (PAP-72)

  discovery.ip 从 dev profile 移到 application.yml，与 xxl 执行器回调地址共用一个
  按用途命名的变量；容器不设时自动探测。ingest 的 dev bucket 改为 patra-ingest-cache。

  Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
  EOF
  ```

  提交前确认 `--stat` 只列出这 12 个 yml。

---

### Task 4: 文档

**Files:**
- Modify: `patra-infra/docs/mac-mini-connectivity.md`
- Modify: `patra-infra/CLAUDE.md`
- Modify: `patra-infra/docker/README.md`
- Modify: `patra-api/README.md`
- Modify: `patra-api/patra-gateway-boot/README.md`
- Modify: `.claude/skills/patra-backend/skills/patra-hexagonal/resources/configuration.md`
- Modify: `docs/patra/release-specs/v0.9-config-center.md`

- [ ] **Step 1: `mac-mini-connectivity.md`**

  - **开头"通用原则"那段**：末句 `本机 tailscale IP 见 env.zsh 的 $TAILSCALE_IP（tailscale ip -4 | head -1）。` 改为：

    ```
    应用读的是按用途命名的 `PATRA_ADVERTISE_IP`，本机在 `env.zsh` 里把它设成 tailscale IP（`export PATRA_ADVERTISE_IP="$TAILSCALE_IP"`）。
    ```

  - **§1**：
    - "根因"里的 `未注入 XXL_JOB_EXECUTOR_IP 时` 改为 `未注入 PATRA_ADVERTISE_IP 时`。
    - "解决"整条换成：

      ~~~
      - **解决**：显式注入本机 tailscale IP。已在 `env.zsh` 配置：
        ```sh
        export PATRA_ADVERTISE_IP="$TAILSCALE_IP"
        ```
        对应应用配置 `xxl.job.executor.ip: ${PATRA_ADVERTISE_IP:}`（catalog/ingest 的 `application.yml`），Nacos 注册地址 `spring.cloud.nacos.discovery.ip` 读同一个变量。
      ~~~

  - **§3**："解决"之后加一条：

    ```
    - **容器侧**：容器里解析不了 MagicDNS 名。应用容器和 rocketmq-dashboard 靠 compose 的 `extra_hosts: linqibins-mac-mini:host-gateway` 把这个名字指向宿主机网关，见 §8。2026-10-13 之前容器里的 ingest 一直连不上 broker。
    ```

  - **§6 "现状"**：末尾加一句：

    ```
    2026-10-13 起应用容器访问 mini 自己的基础设施改走宿主机网关（§8），不再经 100.x。
    ```

  - **新增 §8**（放在 §7 之后、`---` 之前）：

    ~~~markdown
    ### 8. 容器经主机名访问基础设施（2026-10-13，PAP-72）

    - **做法**：应用容器与 IDE 同用 `PATRA_INFRA_HOST` 加宿主机映射端口。容器里 `PATRA_INFRA_HOST=linqibins-mac-mini`，由 compose 的 `extra_hosts: linqibins-mac-mini:host-gateway` 解析到 OrbStack 宿主机网关（0.250.250.254）；IDE 里是 `100.103.73.27`。
    - **为什么不直接用主机名**：容器的 DNS 经 OrbStack 转发，查不到 MagicDNS 名，短名和 `*.ts.net` 都不行。
    - **为什么不用 tailscale IP**：100.x 只在 tailscale 在线时存在。宿主机网关这条路不经过 tailscale 网卡，tailscale 断了也不影响容器访问基础设施。
    - **实测代价**：大批量传输（`\copy` 114 MB、MinIO 512 MB）与直连容器名没有差别；PG 小查询每次往返多 0.07～0.2 ms（OrbStack 端口转发）。PG 看到的来源地址是 patra-net 网关 `192.168.97.1`。
    - **验证**：
      ```sh
      docker exec patra-ingest getent ahostsv4 linqibins-mac-mini   # 应为 0.250.250.254
      docker exec patra-ingest bash -c 'exec 3<>/dev/tcp/linqibins-mac-mini/15432 && echo OK'
      ```
    - **新增连基础设施的容器**：同样加 `extra_hosts`（apps compose 里引用 `*infra-hosts` 锚点）。
    ~~~

- [ ] **Step 2: `patra-infra/CLAUDE.md`**

  - 第 16 行"应用侧单一开关"改为：

    ```
    - **应用侧单一开关**：所有微服务用 `PATRA_INFRA_HOST` 加宿主机映射端口访问基础设施。IDE 里是 mini 的 tailscale IP，容器里是 `linqibins-mac-mini`（compose 的 `extra_hosts` 指向宿主机网关，见 `docs/mac-mini-connectivity.md` §8）。yml 默认值 `127.0.0.1` 仅作本地应急 fallback。服务对外通告的地址统一读 `PATRA_ADVERTISE_IP`。改基建地址范式时要同步 patra-api 各 boot 模块的 yml（本目录范围外）。
    ```

  - 非显性约束第 1 条：`容器内由 .env.common 显式覆盖为 NACOS_HOST=nacos。` 改为 `容器里同样跟随 PATRA_INFRA_HOST（.env.common 设为 linqibins-mac-mini）。`
  - "容器内 Nacos 走服务名"那条整条换成：

    ```
    - **容器经宿主机网关访问基础设施**：应用容器和 rocketmq-dashboard 加 `extra_hosts: linqibins-mac-mini:host-gateway`，PG、Redis、Nacos（含 gRPC 9848）、MinIO、namesrv、broker、xxl admin 都走映射端口，不经 tailscale。新增连基础设施的容器要同样加。
    ```

  - "环境文件三层"那条里，`.env.common`（共享基建坐标：patra-net 服务名、内网 dev 默认、...）改为 `.env.common`（共享基建坐标：mini 主机名加映射端口、内网 dev 默认、...）。

- [ ] **Step 3: `patra-infra/docker/README.md`**

  - "应用侧配置"一节的代码块：
    - `export PATRA_INFRA_HOST=linqibins-mac-mini` 改为 `export PATRA_INFRA_HOST=100.103.73.27   # IDE；容器里由 .env.common 设为 linqibins-mac-mini`。
    - yml 示例的 `discovery:` 下加 `ip: ${PATRA_ADVERTISE_IP:}`。
  - 该节末尾加一段：

    ```
    容器与 IDE 用同一种写法：容器里 `linqibins-mac-mini` 由 compose 的 `extra_hosts` 解析到宿主机网关，端口同样是映射端口（原理与实测见 `../docs/mac-mini-connectivity.md` §8）。服务对外通告的地址读 `PATRA_ADVERTISE_IP`，IDE 设为本机 tailscale IP，容器不设。
    ```

  - 故障排查"RocketMQ 客户端连不上 broker"一节，在代码块之前加：

    ```
    若只有容器里的客户端连不上：检查该容器是否带 `extra_hosts: linqibins-mac-mini:host-gateway`，`docker exec <容器> getent ahostsv4 linqibins-mac-mini` 应返回 0.250.250.254。
    ```

- [ ] **Step 4: `patra-api/README.md`、gateway README、configuration.md**

  - `patra-api/README.md` 第 146 行的 `(基建地址走 PATRA_INFRA_HOST / TAILSCALE_IP 注册)` 改为 `(基建地址走 PATRA_INFRA_HOST 加映射端口，注册地址读 PATRA_ADVERTISE_IP)`；`container` 的说明后补"，基建地址同样是 PATRA_INFRA_HOST 加映射端口"。
  - `patra-gateway-boot/README.md` 配置表里 `TAILSCALE_IP` 那行改为：

    ```
    | `PATRA_ADVERTISE_IP` | 向 Nacos 注册的地址 | 空，自动探测；IDE 设为本机 tailscale IP |
    ```

  - `configuration.md`：在 profile 说明之后加一小节：

    ```markdown
    ### 本地变量约定（PAP-72）

    IDE 与容器的差异只放在 Nacos 之外的本地变量里，变量按用途命名，不按来源命名：

    | 变量 | 含义 | IDE | 容器 |
    |---|---|---|---|
    | `PATRA_INFRA_HOST` | dev 环境里基础设施所在的主机，配合宿主机映射端口使用 | mini 的 tailscale IP | `linqibins-mac-mini`（`extra_hosts` 指向宿主机网关） |
    | `PATRA_ADVERTISE_IP` | 通告给 Nacos 和 xxl 的地址，留空自动探测 | 本机 tailscale IP | 不设 |
    | `PATRA_LOG_DIR` | 日志目录 | 不设 | `/app/logs` |

    详见 `docs/patra/specs/2026-10-13-infra-access-unification-design.md`。
    ```

- [ ] **Step 5: release spec**

  `docs/patra/release-specs/v0.9-config-center.md`：

  - 第 75 行（"现状事实"里容器用容器名那条）末句 `例外是 RocketMQ broker：BROKER_IP1 是 tailscale 的 MagicDNS 短名，两种运行方式都经它连接。` 改为：

    ```
    例外是 RocketMQ broker：`BROKER_IP1` 是 tailscale 的 MagicDNS 短名，但容器里解析不了它，容器里的 ingest 实际上连不上 broker（2026-10-13 PAP-72 实测）。
    ```

  - 第 136 行"已排除出本字段"里的 `PAP-72 选 B 还是 C` 改为 `PAP-72 的做法（已选定 E：容器经宿主机网关用主机名加映射端口，见 specs/2026-10-13-infra-access-unification-design.md）`。

- [ ] **Step 6: 校验**

  ```bash
  R=/Users/linqibin/Projects/Products/patra
  grep -nE 'TAILSCALE_IP|XXL_JOB_EXECUTOR_IP|NACOS_HOST=nacos|容器内 Nacos 走服务名' $R/patra-infra/CLAUDE.md $R/patra-infra/docker/README.md $R/patra-api/README.md $R/patra-api/patra-gateway-boot/README.md $R/.claude/skills/patra-backend/skills/patra-hexagonal/resources/configuration.md
  grep -nE 'TAILSCALE_IP|XXL_JOB_EXECUTOR_IP' $R/patra-infra/docs/mac-mini-connectivity.md
  grep -c '### 8\. 容器经主机名访问基础设施' $R/patra-infra/docs/mac-mini-connectivity.md
  ```

  Expected:
  - 第一条无输出；
  - 第二条只剩 `export PATRA_ADVERTISE_IP="$TAILSCALE_IP"` 这类说明来源的行；
  - 第三条 `1`。

- [ ] **Step 7: 提交**

  ```bash
  R=/Users/linqibin/Projects/Products/patra
  git -C $R add patra-infra/docs/mac-mini-connectivity.md patra-infra/CLAUDE.md patra-infra/docker/README.md patra-api/README.md patra-api/patra-gateway-boot/README.md .claude/skills/patra-backend/skills/patra-hexagonal/resources/configuration.md docs/patra/release-specs/v0.9-config-center.md
  git -C $R diff --cached --stat
  git -C $R commit -q -F - <<'EOF'
  docs(infra): 记录容器经宿主机网关访问基础设施和本地变量约定 (PAP-72)

  Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
  EOF
  ```

---

### Task 5: 本机验证

**Interfaces:**
- Consumes: Task 1～3 的全部改动。

- [ ] **Step 1: 全量门控**

  ```bash
  R=/Users/linqibin/Projects/Products/patra
  $R/gradlew -p $R check > $SP/task5-check.log 2>&1; echo "rc=$?"; tail -5 $SP/task5-check.log
  ```

  Expected: `rc=0`，`BUILD SUCCESSFUL`。

  失败时先看是不是本次改动引起的：IDEA 并发写 `build/classes`、iCloud 负载这类已知干扰按 memory 处理后重跑。

- [ ] **Step 2: 用户改 `env.zsh`（停下来等用户）**

  把下面的改法发给用户，等用户回复已改：

  ```
  # 删掉这一行：
  export XXL_JOB_EXECUTOR_IP="$TAILSCALE_IP"
  # 在 TAILSCALE_IP 那行之后加：
  # 本实例通告给 Nacos 和 xxl 调度中心的地址（patra-api 读这个变量）
  export PATRA_ADVERTISE_IP="$TAILSCALE_IP"
  ```

- [ ] **Step 3: 本机起 ingest、catalog，核对通告地址**

  ```bash
  R=/Users/linqibin/Projects/Products/patra
  zsh -c "source ~/.config/zsh/env.zsh; $R/gradlew -p $R :patra-api:patra-ingest:patra-ingest-boot:bootRun" > $SP/ingest-boot.log 2>&1 &
  zsh -c "source ~/.config/zsh/env.zsh; $R/gradlew -p $R :patra-api:patra-catalog:patra-catalog-boot:bootRun" > $SP/catalog-boot.log 2>&1 &
  ```

  用 Monitor 等两个日志都出现 `Started` 或报错，再核对：

  ```bash
  grep -hE 'nacos registry, DEFAULT_GROUP patra-(ingest|catalog) .* register finished' $SP/ingest-boot.log $SP/catalog-boot.log
  ssh linqibin@linqibins-mac-mini 'export PATH=/usr/local/bin:$PATH; docker exec patra-mysql-ops sh -c '"'"'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N xxl_job -e "select registry_key, registry_value from xxl_job_registry order by registry_key" 2>/dev/null'"'"''
  ```

  Expected:
  - 两行注册日志，地址都是 `100.70.109.37:<端口>`；
  - `xxl_job_registry` 里 ingest、catalog 执行器各有一行 `http://100.70.109.37:<端口>/`，与容器执行器的 `http://192.168.97.x:<端口>/` 并存。

  核对完停掉两个 bootRun（`pkill -f 'patra-(ingest|catalog)-boot'` 后用 `pgrep` 确认）。查询 xxl_job 库只读，不需要确认。

  已知副作用：本机实例在线期间，mini 网关会把部分请求轮询到本机，停掉后恢复。

---

### Task 6: mini 切换与验证（每个改变 mini 的步骤先问用户）

**Interfaces:**
- Consumes: Task 1～5 全部完成。

- [ ] **Step 1: 推到 main（等用户当轮明确授权）**

  向用户说明：
  - 要推的提交：本分支相对 main 的全部提交，包括 PAP-71 的 `2693fe7e6`、本设计的 spec 和 plan、Task 1～4；
  - env 文件改动会触发 CD 全量重建并重部署 6 个服务。

  拿到授权后按 PAP-71 的方式把分支推到 main。CD 的运行用 `gh run watch` 盯到结束；网络中断时循环重试。

  Expected: build-deploy 成功，deploy.sh 的 6 个服务健康检查全部通过。

- [ ] **Step 2: mini 上更新 storage 和 jobs 栈（先问用户）**

  ```bash
  ssh linqibin@linqibins-mac-mini 'export PATH=/usr/local/bin:$PATH; cd ~/Projects/patra && git pull -q && bash patra-infra/scripts/compose-all.sh up storage jobs && bash patra-infra/scripts/compose-all.sh ps'
  ```

  Expected: minio-init 正常退出，日志里有 `Bucket: patra-ingest-cache`；rocketmq-dashboard 重建后 healthy；其余容器没有 unhealthy。

- [ ] **Step 3: 逐项验证（只读）**

  ```bash
  ssh linqibin@linqibins-mac-mini 'export PATH=/usr/local/bin:$PATH
  APPS="patra-registry patra-object-storage patra-catalog patra-ingest patra-identity patra-gateway"
  echo "== 健康"; for c in $APPS; do docker inspect -f "{{.Name}} {{.State.Health.Status}}" $c; done
  echo "== 主机名"; for c in $APPS; do echo "$c $(docker exec $c getent ahostsv4 linqibins-mac-mini | head -1)"; done
  echo "== 旧变量"; for c in $APPS; do docker inspect -f "{{range .Config.Env}}{{println .}}{{end}}" $c | cut -d= -f1 | grep -E "^(NACOS_HOST|NACOS_PORT|ROCKETMQ_NAMESRV|XXL_JOB_ADMIN_ADDRESSES|XXL_JOB_EXECUTOR_LOG_PATH)$" | sed "s|^|$c |"; done
  echo "== PG 来源"; IPS=$(for c in $APPS; do docker inspect -f "{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}" $c; done | tr "\n" "|" | sed "s/|$//")
  docker exec -e PGPASSWORD=123456 patra-postgres psql -U postgres -Atc "select client_addr, datname, count(*) from pg_stat_activity where client_addr is not null group by 1,2 order by 1,2" | tee /tmp/pap72-pg.txt; echo "应用容器 IP 命中: $(grep -cE "^($IPS)\|" /tmp/pap72-pg.txt)"; rm -f /tmp/pap72-pg.txt
  echo "== xxl 日志目录"; for c in patra-ingest patra-catalog; do docker exec $c ls -d /app/logs/xxl-job/${c}; done
  echo "== 注册地址"; for c in $APPS; do docker logs $c 2>&1 | grep "register finished" | tail -1 | grep -oE "patra-[a-z-]+ [0-9.]+:[0-9]+"; done'
  ```

  Expected:
  - 6 个都是 `healthy`；
  - 6 个容器里 `linqibins-mac-mini` 都是 `0.250.250.254`；
  - "旧变量"下无输出；
  - "应用容器 IP 命中: 0"，各业务库的连接来自 `192.168.97.1`；
  - 两个 xxl 日志目录存在；
  - 6 个注册地址都是 `192.168.97.x`，不是空、不是 0.250.250.x（Review Focus 2）。

- [ ] **Step 4: Nacos、xxl、broker、dashboard**

  ```bash
  ssh linqibin@linqibins-mac-mini 'export PATH=/usr/local/bin:$PATH; cd ~/Projects/patra
  bash patra-infra/scripts/nacos-verify.sh --expect-services | tail -3
  docker exec patra-mysql-ops sh -c '"'"'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N xxl_job -e "select registry_key, registry_value from xxl_job_registry order by registry_key" 2>/dev/null'"'"'
  docker exec patra-rocketmq-namesrv sh -c "/home/rocketmq/rocketmq-5.3.1/bin/mqadmin consumerConnection -n 127.0.0.1:9876 -g ingest-task-consumer-group 2>&1 | grep -vE \"^\s+at \" | tail -4"
  curl -s http://127.0.0.1:4002/cluster/list.query | head -c 300; echo'
  ```

  Expected:
  - nacos-verify 通过 11 / 失败 0；
  - xxl 两个容器执行器以 `http://192.168.97.x:<端口>/` 在册；
  - `consumerConnection` 列出 ingest 容器的客户端（切换前是 `No topic route info`）；
  - dashboard 返回的 JSON 里有 `broker-a`，没有错误信息。

- [ ] **Step 5: 10 分钟后复查 Nacos（Review Focus 3）**

  用 Monitor 等 10 分钟，再跑一次 `nacos-verify.sh --expect-services`。

  Expected: 仍是通过 11 / 失败 0。

- [ ] **Step 6: 旧 bucket（问用户）**

  问用户是否删除 mini 上空的 `dev-ingest` bucket（0 个对象）。同意后：

  ```bash
  ssh linqibin@linqibins-mac-mini 'export PATH=/usr/local/bin:$PATH; docker run --rm --network patra-net --entrypoint sh -e MC_HOST_d=http://minioadmin:minioadmin123@minio:9000 minio/mc:RELEASE.2024-10-08T09-37-26Z -c "mc du d/dev-ingest && mc rb d/dev-ingest && mc ls d/"'
  ```

  Expected: `mc du` 显示 0 个对象后才执行 `mc rb`（不加 `--force`，非空会失败）；最后的列表里没有 `dev-ingest`。

- [ ] **Step 7: 写回结果**

  - spec 的状态行改为 `已上线（<日期>）`。
  - Task 6 实测与设计不一致的地方写进 spec 对应小节，标"实际："。

  提交：

  ```bash
  R=/Users/linqibin/Projects/Products/patra
  git -C $R add docs/patra/specs/2026-10-13-infra-access-unification-design.md
  git -C $R commit -q -F - <<'EOF'
  docs(config-center): 写回统一访问路径上线的实测结果 (PAP-72)

  Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
  EOF
  ```

  Linear 上给 PAP-72 发证据评论（对照 spec 9.3），经用户确认后标 Done。
