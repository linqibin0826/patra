# mysql-ops 容器实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `patra-infra` 仓库引入一个独立的 `mysql-ops` MySQL 8.0 容器，仅服务于不支持 PG 的运维组件（当前唯一消费者：`xxl-job-admin`），从而恢复 patra-api 中 catalog/ingest 服务的 XXL-Job 调度功能。

**Architecture:** 在 `docker-compose.jobs.yaml` 中加一个轻量 MySQL 实例（不暴露端口、压缩内存到 ~150MB、按 env 注入凭据），通过 docker network `patra-net` 让 `xxl-job-admin` 以 `mysql-ops:3306` 连接。`patra-api` 零改动（yml 早已指向 `localhost:7070`）。

**Tech Stack:** Docker Compose / MySQL 8.0 / XXL-Job admin 3.2.0

**Spec 链接:** `docs/superpowers/specs/2026-05-17-mysql-ops-container-design.md`

---

## ⚠️ 跨仓库重要提示

- 本 plan 所在仓库：`patra-api`（spec/plan 文档放这里）
- **实际改动落在**：`patra-infra` 仓库（绝对路径：`/Users/linqibin/Projects/Products/Patra/patra-infra`）
- 执行任何文件改动前 **必须先 `cd /Users/linqibin/Projects/Products/Patra/patra-infra`**
- `patra-infra` 仓库惯例：单人项目，直接在 `main` 分支上 commit，无需开 feature 分支
- 当前 `patra-infra/main` 是干净的（HEAD: `eda295f`），可直接开工

---

## 文件结构

实施过程中将创建或修改的文件：

| 文件 | 操作 | 职责 |
|---|---|---|
| `patra-infra/docker/mysql-ops/init-scripts/01-schema.sql` | **新建** | XXL-Job 3.2.0 官方表结构 + 种子数据（admin 默认账号 / 默认执行器组）|
| `patra-infra/docker/.env.dev` | **修改** | 新增 3 个 `MYSQL_OPS_*` 变量 |
| `patra-infra/docker/.env.example` | **修改** | 同步示例（不含真实值）|
| `patra-infra/docker/docker-compose.jobs.yaml` | **修改** | 新增 `mysql-ops` 服务；调整 `xxl-job-admin` 的连接串与 `depends_on` |
| `patra-infra/docker/README.md` | **修改** | "数据目录"小节加 mysql-ops；"任务服务"小节加 mysql-ops 端口说明；"服务凭据"小节加密码 |

---

## Task 1：准备工作 + schema 文件下载

**Files:**
- Create: `patra-infra/docker/mysql-ops/init-scripts/01-schema.sql`

- [ ] **Step 1.1: 切换到 patra-infra 仓库并确认状态**

```bash
cd /Users/linqibin/Projects/Products/Patra/patra-infra
git status
git log --oneline -1
```

Expected: 工作树干净（`nothing to commit, working tree clean`），HEAD 为 `eda295f` 或更新的 commit。

- [ ] **Step 1.2: 创建 mysql-ops 目录结构**

```bash
mkdir -p docker/mysql-ops/init-scripts
ls -la docker/mysql-ops/init-scripts
```

Expected: 目录已创建（空目录）。

- [ ] **Step 1.3: 下载 XXL-Job 官方 schema**

XXL-Job 自 2.x 起 schema 基本未变，从 master 分支拉取，对 3.2.0 admin 镜像兼容。

```bash
curl -fsSL -o docker/mysql-ops/init-scripts/01-schema.sql \
  https://raw.githubusercontent.com/xuxueli/xxl-job/master/doc/db/tables_xxl_job.sql

# 如果上面 404 或失败，备用：
# curl -fsSL -o docker/mysql-ops/init-scripts/01-schema.sql \
#   https://raw.githubusercontent.com/xuxueli/xxl-job/2.4.2/doc/db/tables_xxl_job.sql
```

Expected: 文件下载成功（无报错）。

- [ ] **Step 1.4: 验证 schema 文件内容**

```bash
wc -l docker/mysql-ops/init-scripts/01-schema.sql
grep -c "CREATE TABLE" docker/mysql-ops/init-scripts/01-schema.sql
grep -E "^CREATE TABLE|^USE|^CREATE DATABASE" docker/mysql-ops/init-scripts/01-schema.sql | head -15
```

Expected:
- 行数 > 150
- `CREATE TABLE` 至少 8 次
- 输出应包含 `CREATE DATABASE`、`USE xxl_job`、以及 `xxl_job_info` / `xxl_job_log` / `xxl_job_log_report` / `xxl_job_logglue` / `xxl_job_registry` / `xxl_job_group` / `xxl_job_user` / `xxl_job_lock` 8 张表

如果输出的 `CREATE DATABASE` 名字不是 `xxl_job`，立刻停下来 — 镜像 env 里 `MYSQL_DATABASE=xxl_job` 与 schema 必须对齐，否则要么改 env 要么改 schema。**首选不动 schema，调整 env**。

- [ ] **Step 1.5: 暂不 commit（等 Task 2/3/4 一起合并 commit）**

---

## Task 2：env 变量

**Files:**
- Modify: `patra-infra/docker/.env.dev`
- Modify: `patra-infra/docker/.env.example`

- [ ] **Step 2.1: 在 .env.dev 末尾追加 mysql-ops 凭据**

完整追加内容：

```bash
cat >> docker/.env.dev <<'EOF'

# mysql-ops 配置（运维组件用 MySQL，业务库走 PG）
MYSQL_OPS_ROOT_PASSWORD=patra_ops_root_dev
MYSQL_OPS_USERNAME=xxl_job
MYSQL_OPS_PASSWORD=xxl_job_dev
EOF
```

- [ ] **Step 2.2: 验证 .env.dev 内容**

```bash
cat docker/.env.dev
```

Expected: 文件包含原 PostgreSQL/Redis/Consul 三段配置，外加新增的 mysql-ops 三个变量。

- [ ] **Step 2.3: 在 .env.example 末尾追加示例（不含真实值）**

```bash
cat >> docker/.env.example <<'EOF'

# mysql-ops 凭据（运维组件用 MySQL，业务库走 PG）
# 复制到 .env.dev 后改成本地实际值
MYSQL_OPS_ROOT_PASSWORD=
MYSQL_OPS_USERNAME=xxl_job
MYSQL_OPS_PASSWORD=
EOF
```

- [ ] **Step 2.4: 验证 .env.example**

```bash
cat docker/.env.example
```

Expected: 原 RocketMQ 段 + 新增 mysql-ops 段，密码字段为空。

---

## Task 3：docker-compose.jobs.yaml 改造

**Files:**
- Modify: `patra-infra/docker/docker-compose.jobs.yaml`

### 3.1：新增 mysql-ops 服务

- [ ] **Step 3.1.1: 阅读 jobs.yaml 当前结构，定位 services 段开头**

```bash
grep -n "^services:\|^  [a-z]" docker/docker-compose.jobs.yaml | head -10
```

Expected: 看到 `services:` 在第 9 行（左右），下面依次是 `  xxl-job-admin:`、`  rocketmq-namesrv:`、`  rocketmq-broker:`、`  rocketmq-dashboard:`。

- [ ] **Step 3.1.2: 在 `services:` 行之后、`xxl-job-admin:` 之前，插入 mysql-ops 服务**

使用文本编辑器（不要用 sed，YAML 缩进敏感）在 `services:` 行下方第一个空行后、`xxl-job-admin:` 块前面，插入以下完整段落：

```yaml
  mysql-ops:
    image: mysql:8.0
    container_name: patra-mysql-ops
    restart: unless-stopped
    environment:
      TZ: Asia/Shanghai
      MYSQL_ROOT_PASSWORD: ${MYSQL_OPS_ROOT_PASSWORD}
      MYSQL_DATABASE: xxl_job
      MYSQL_USER: ${MYSQL_OPS_USERNAME}
      MYSQL_PASSWORD: ${MYSQL_OPS_PASSWORD}
    command:
      - --character-set-server=utf8mb4
      - --collation-server=utf8mb4_unicode_ci
      - --innodb-buffer-pool-size=64M
      - --performance-schema=OFF
      - --skip-host-cache
      - --skip-name-resolve
    volumes:
      - ${HOME}/.patra/docker/mysql-ops/data:/var/lib/mysql
      - ./mysql-ops/init-scripts:/docker-entrypoint-initdb.d:ro
    healthcheck:
      test: ["CMD-SHELL", "mysqladmin ping -h 127.0.0.1 -u root -p${MYSQL_OPS_ROOT_PASSWORD} --silent"]
      interval: 10s
      timeout: 5s
      retries: 10
      start_period: 20s

```

### 3.2：调整 xxl-job-admin 服务

- [ ] **Step 3.2.1: 修改 xxl-job-admin 的 spring.datasource.url**

把 `--spring.datasource.url=jdbc:mysql://mysql:3306/xxl_job?...` 中的 `mysql:3306` 替换为 `mysql-ops:3306`。

完整替换前：

```
        --spring.datasource.url=jdbc:mysql://mysql:3306/xxl_job?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
```

替换后：

```
        --spring.datasource.url=jdbc:mysql://mysql-ops:3306/xxl_job?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
```

- [ ] **Step 3.2.2: 改 xxl-job-admin 的 datasource 账号密码为 env 占位符**

完整替换前：

```
        --spring.datasource.username=xxl_job
        --spring.datasource.password=xxl_job_123456
```

替换后：

```
        --spring.datasource.username=${MYSQL_OPS_USERNAME}
        --spring.datasource.password=${MYSQL_OPS_PASSWORD}
```

- [ ] **Step 3.2.3: 给 xxl-job-admin 添加 depends_on（如果还没有）**

在 `xxl-job-admin` 的 `ports:` 段之后、`healthcheck:` 段之前插入：

```yaml
    depends_on:
      mysql-ops:
        condition: service_healthy
```

### 3.3：验证 compose 文件语法

- [ ] **Step 3.3.1: 用 `docker compose config` 验证 yaml 语法 + env 解析**

```bash
docker compose --env-file docker/.env.dev -f docker/docker-compose.jobs.yaml config > /tmp/jobs-config-rendered.yaml 2>&1
echo "Exit: $?"
head -40 /tmp/jobs-config-rendered.yaml
```

Expected:
- Exit 0
- 渲染输出中 mysql-ops 段的 `MYSQL_ROOT_PASSWORD` 被解析为 `patra_ops_root_dev`（而不是 `${MYSQL_OPS_ROOT_PASSWORD}` 字面值）
- xxl-job-admin 的 `PARAMS` 中 url 是 `jdbc:mysql://mysql-ops:3306/xxl_job?...`，账号被替换为 `xxl_job`、密码为 `xxl_job_dev`

如果失败：检查缩进（YAML 2 空格）、检查 env 文件路径。

- [ ] **Step 3.3.2: 检查 mysql-ops 段的所有关键字段都正确渲染**

```bash
grep -A 25 "mysql-ops:" /tmp/jobs-config-rendered.yaml | head -30
```

Expected:
- `MYSQL_ROOT_PASSWORD: patra_ops_root_dev`
- `MYSQL_DATABASE: xxl_job`
- `MYSQL_USER: xxl_job`
- `MYSQL_PASSWORD: xxl_job_dev`
- volumes 绑定 `~/.patra/docker/mysql-ops/data` 与 `./mysql-ops/init-scripts`
- 不出现任何字面的 `${...}` 占位符

---

## Task 4：本地启动 mysql-ops 单独验证

- [ ] **Step 4.1: 创建数据卷目录**

```bash
mkdir -p ~/.patra/docker/mysql-ops/data
ls -la ~/.patra/docker/mysql-ops/
```

Expected: `data` 子目录存在且为空。

- [ ] **Step 4.2: 启动 mysql-ops（仅这一个服务）**

```bash
docker compose --env-file docker/.env.dev -f docker/docker-compose.jobs.yaml up -d mysql-ops
```

Expected: 输出 `Container patra-mysql-ops Started`。

- [ ] **Step 4.3: 监控容器状态直到 healthy**

```bash
# 持续观察约 30 秒
for i in 1 2 3 4 5 6; do
  STATUS=$(docker inspect --format='{{.State.Health.Status}}' patra-mysql-ops 2>/dev/null)
  echo "[$i] status=$STATUS"
  if [ "$STATUS" = "healthy" ]; then echo "OK"; break; fi
  sleep 5
done
```

Expected: 在 30 秒内变为 `healthy`。

如果一直 `starting` 或 `unhealthy`：

```bash
docker logs patra-mysql-ops --tail 50
```

常见问题：
- init 脚本语法错误 → 看 `[ERROR] ... near '...'` 行
- 字符集冲突 → schema 文件含 `DEFAULT CHARACTER SET utf8` 改成 `utf8mb4`（一般不会，官方 schema 是 utf8mb4）
- 数据卷残留旧数据 → `docker compose -f docker/docker-compose.jobs.yaml down mysql-ops && rm -rf ~/.patra/docker/mysql-ops/data && mkdir -p ~/.patra/docker/mysql-ops/data`，再回到 Step 4.2

- [ ] **Step 4.4: 验证 schema 导入成功**

```bash
docker exec patra-mysql-ops mysql -u root -p"${MYSQL_OPS_ROOT_PASSWORD:-patra_ops_root_dev}" -e "USE xxl_job; SHOW TABLES;"
```

Expected: 输出 8 张表：

```
Tables_in_xxl_job
xxl_job_group
xxl_job_info
xxl_job_lock
xxl_job_log
xxl_job_log_report
xxl_job_logglue
xxl_job_registry
xxl_job_user
```

- [ ] **Step 4.5: 验证 xxl_job admin 账号可登录数据库**

```bash
docker exec patra-mysql-ops mysql -u xxl_job -pxxl_job_dev -e "USE xxl_job; SELECT COUNT(*) AS user_count FROM xxl_job_user;"
```

Expected: 输出 `user_count: 1`（XXL-Job 默认 admin 用户）。

- [ ] **Step 4.6: 验证内存占用**

```bash
docker stats --no-stream --format "table {{.Name}}\t{{.MemUsage}}\t{{.MemPerc}}" patra-mysql-ops
```

Expected: `MEM USAGE` 列 < 200 MiB。

---

## Task 5：启动 xxl-job-admin 验证

- [ ] **Step 5.1: 确保 logs 目录存在**

```bash
mkdir -p ~/.patra/docker/xxl-job-admin/logs
```

- [ ] **Step 5.2: 启动 xxl-job-admin**

```bash
docker compose --env-file docker/.env.dev -f docker/docker-compose.jobs.yaml up -d xxl-job-admin
```

Expected: 输出 `Container patra-xxl-job-admin Started`（mysql-ops 已 healthy，不会因依赖等待）。

- [ ] **Step 5.3: 监控 admin 容器变 healthy**

```bash
for i in 1 2 3 4 5 6 7 8; do
  STATUS=$(docker inspect --format='{{.State.Health.Status}}' patra-xxl-job-admin 2>/dev/null)
  echo "[$i] status=$STATUS"
  if [ "$STATUS" = "healthy" ]; then echo "OK"; break; fi
  sleep 5
done
```

Expected: 40 秒内变 `healthy`。

失败排查：

```bash
docker logs patra-xxl-job-admin --tail 80
```

常见报错：
- `Communications link failure` → mysql-ops 没起来，重检 Task 4
- `Access denied for user 'xxl_job'@...` → env 凭据不匹配，检查 .env.dev
- `Unknown database 'xxl_job'` → init 脚本未生效，删数据卷重来

- [ ] **Step 5.4: HTTP 端到端验证**

```bash
curl -s -o /dev/null -w "HTTP=%{http_code}\n" http://localhost:7070/xxl-job-admin/toLogin
```

Expected: `HTTP=200`。

如果是 302，跟 Location 一次：

```bash
curl -sL -o /dev/null -w "FINAL HTTP=%{http_code}\n" http://localhost:7070/xxl-job-admin/
```

- [ ] **Step 5.5: 浏览器手工登录验证**

打开浏览器访问 `http://localhost:7070/xxl-job-admin`，用 `admin / 123456` 登录。

Expected: 登录成功，进入"任务管理"页面（此时执行器列表为空，因为 catalog/ingest 还没启动）。

---

## Task 6：端到端验证（patra-api executor 注册 + 手动触发任务）

> 本 Task 涉及 patra-api 服务启动。如果你没有装好 Java 25 / Gradle 9.2.1 环境，可跳过 Step 6.2-6.5，让用户手动验证。

- [ ] **Step 6.1: 确保依赖服务运行（PG、Redis、Consul、RocketMQ）**

```bash
cd /Users/linqibin/Projects/Products/Patra/patra-infra
docker compose --env-file docker/.env.dev -f docker/docker-compose.core.yaml up -d
docker compose --env-file docker/.env.dev -f docker/docker-compose.jobs.yaml up -d
docker ps --format "table {{.Names}}\t{{.Status}}" | grep -E "patra-(postgres|redis|consul|mysql-ops|xxl-job-admin|rocketmq)"
```

Expected: 所有相关容器 `Up (healthy)`。

- [ ] **Step 6.2: 切到 patra-api 启动 catalog 服务**

```bash
cd /Users/linqibin/Projects/Products/Patra/patra-api
./gradlew :patra-catalog:patra-catalog-boot:bootRun &
CATALOG_PID=$!
# 等启动完成（约 30s）
sleep 40
```

Expected: catalog 启动日志末尾出现 `Started CatalogBootApplication`。

如果手动观察日志：

```bash
tail -f patra-catalog/patra-catalog-boot/logs/patra-catalog.log
# 看到 "Started CatalogBootApplication" 即可
```

- [ ] **Step 6.3: 启动 ingest 服务**

```bash
./gradlew :patra-ingest:patra-ingest-boot:bootRun &
INGEST_PID=$!
sleep 40
```

- [ ] **Step 6.4: 验证 executor 在 admin 注册成功**

打开 `http://localhost:7070/xxl-job-admin/jobgroup`（执行器管理），或用 API：

```bash
# 登录获取 cookie
curl -s -c /tmp/xxl-cookie.txt -X POST \
  -d "userName=admin&password=123456" \
  http://localhost:7070/xxl-job-admin/login

# 查执行器列表
curl -s -b /tmp/xxl-cookie.txt \
  http://localhost:7070/xxl-job-admin/jobgroup/pageList \
  -d "start=0&length=20&appname=&title=" | python3 -m json.tool | head -40
```

Expected: 返回 JSON 中有 2 个 `online` 执行器，`appname` 包含 `patra-catalog` 与 `patra-ingest`，每个的 `addressList` 显示宿主机 IP。

如果执行器是 `offline`：等 30 秒（XXL-Job 默认心跳 30s）再查。

- [ ] **Step 6.5: 手动触发一个任务验证调度通路**

最容易验证的：触发 `ingestOutboxRelayJob`（无需外部数据依赖）。在 admin UI："任务管理" → 选择 `patra-ingest` 执行器组 → 找到 `ingestOutboxRelayJob` → 点 "执行一次"。

或 API（替换 `JOB_ID` 为实际 ID）：

```bash
curl -s -b /tmp/xxl-cookie.txt -X POST \
  http://localhost:7070/xxl-job-admin/jobinfo/trigger \
  -d "id=<JOB_ID>&executorParam=&addressList="
```

Expected: 在 "调度日志" 看到一条执行记录，状态 `成功`。

- [ ] **Step 6.6: 确认 patra-api 启动日志不再有 `localhost:7070` 报错**

```bash
grep -c "ConnectionRefused\|localhost:7070.*refused" patra-catalog/patra-catalog-boot/logs/*.log patra-ingest/patra-ingest-boot/logs/*.log 2>/dev/null || echo "0"
```

Expected: `0`。

- [ ] **Step 6.7: 关停 patra-api 服务**

```bash
kill $CATALOG_PID $INGEST_PID 2>/dev/null
wait 2>/dev/null
```

---

## Task 7：补充 README 文档

**Files:**
- Modify: `patra-infra/docker/README.md`

- [ ] **Step 7.1: 在"数据目录"小节加 mysql-ops**

定位 README.md 中 `xxl-job-admin/` 数据目录段（约第 37-38 行），在其前面插入：

```markdown
├── mysql-ops/
│   └── data/           # mysql-ops 数据库文件（运维组件用，业务库走 PG）
```

最终该段落看起来：

```
├── consul/
│   └── data/           # Consul 数据
├── mysql-ops/
│   └── data/           # mysql-ops 数据库文件（运维组件用，业务库走 PG）
├── minio/
│   └── data/           # MinIO 对象存储数据
...
├── xxl-job-admin/
│   └── logs/           # XXL-Job 日志
```

- [ ] **Step 7.2: 在"首次设置" mkdir 命令中加入 mysql-ops/data**

定位约第 127 行的 `mkdir -p ~/.patra/docker/{postgres/{data,init},redis/data,...}`，加入 `mysql-ops/data`：

替换前：

```bash
mkdir -p ~/.patra/docker/{postgres/{data,init},redis/data,consul/data,minio/data,es/data,xxl-job-admin/logs,rocketmq/{namesrv/{logs,store},broker/{logs,store,conf}}}
```

替换后：

```bash
mkdir -p ~/.patra/docker/{postgres/{data,init},redis/data,consul/data,mysql-ops/data,minio/data,es/data,xxl-job-admin/logs,rocketmq/{namesrv/{logs,store},broker/{logs,store,conf}}}
```

- [ ] **Step 7.3: 在"任务服务"小节加 mysql-ops 说明**

定位约第 82 行 `### 任务服务 (`docker-compose.jobs.yaml`)`，把服务列表更新为：

```markdown
### 任务服务 (`docker-compose.jobs.yaml`)
异步工作负载和定时任务调度服务:

- **mysql-ops**（不暴露端口）: 仅供 xxl-job-admin 使用的 MySQL 8.0；业务数据走 PG，不受影响
- **XXL-Job Admin**（端口 7070）: 任务调度面板（依赖 mysql-ops，自动启动顺序）
- **RocketMQ NameServer / Broker / Dashboard**: 消息队列
```

- [ ] **Step 7.4: 在"服务凭据"小节加 mysql-ops 信息**

定位约第 281 行 `- **PostgreSQL**: localhost:15432 (postgres/123456)`，在 XXL-Job Admin 行后面插入：

```markdown
- **mysql-ops**: 不暴露宿主机端口；容器内 `mysql-ops:3306`，凭据见 `.env.dev` 的 `MYSQL_OPS_*`
```

- [ ] **Step 7.5: 验证 README 改动**

```bash
cd /Users/linqibin/Projects/Products/Patra/patra-infra
grep -n "mysql-ops" docker/README.md
```

Expected: 至少 4 处命中（数据目录、mkdir 命令、任务服务列表、服务凭据）。

---

## Task 8：commit patra-infra 改动并 push

- [ ] **Step 8.1: 确认 patra-infra 改动清单**

```bash
cd /Users/linqibin/Projects/Products/Patra/patra-infra
git status
git diff --stat
```

Expected: 看到改动覆盖以下 5 处：
- `docker/.env.dev`（追加）
- `docker/.env.example`（追加）
- `docker/docker-compose.jobs.yaml`（新增 mysql-ops 服务 + 调整 xxl-job-admin）
- `docker/README.md`（4 处插入）
- `docker/mysql-ops/init-scripts/01-schema.sql`（新建）

- [ ] **Step 8.2: 检查不要把秘密/大文件 commit 进去**

```bash
git diff docker/.env.dev
```

Expected: 仅追加了 mysql-ops 三行变量，密码为开发期默认值（与既有 `POSTGRES_PASSWORD=123456` 风格一致），可以提交。

- [ ] **Step 8.3: 分文件 add（避免误带其他改动）**

```bash
git add docker/mysql-ops/init-scripts/01-schema.sql
git add docker/.env.dev
git add docker/.env.example
git add docker/docker-compose.jobs.yaml
git add docker/README.md
git status
```

Expected: 仅以上 5 个文件被 staged。

- [ ] **Step 8.4: commit（消息中文，参照该仓库历史风格）**

```bash
git commit -m "$(cat <<'EOF'
feat(docker): 新增 mysql-ops 容器复活 XXL-Job admin（issue #22 方案 A）

PR #21 PG 迁移后 xxl-job-admin 因失去 MySQL 后端无法启动，
catalog/ingest 启动后反复 ConnectionRefused，调度功能失效。

本次引入独立 mysql-ops 容器（mysql:8.0，不对外暴露端口，
内存调优至 ~150MB），仅服务于不支持 PG 的运维组件，
业务库继续走 PG。命名为 mysql-ops 而非 mysql-xxl-job，
为未来其他只支持 MySQL 的运维工具预留共用空间。

- docker/mysql-ops/init-scripts/01-schema.sql 新建（XXL-Job 官方 schema）
- docker/docker-compose.jobs.yaml 新增 mysql-ops 服务 + xxl-job-admin 改连 mysql-ops
- docker/.env.dev / .env.example 新增 MYSQL_OPS_* 三变量
- docker/README.md 4 处补充 mysql-ops 段落

patra-api 零改动（yml 早已指向 localhost:7070）。
设计与决策见 patra-api/docs/superpowers/specs/2026-05-17-mysql-ops-container-design.md
EOF
)"
```

- [ ] **Step 8.5: 确认 commit 成功**

```bash
git log --oneline -3
git show --stat HEAD
```

Expected: 看到新 commit，stats 显示 5 个文件变更。

- [ ] **Step 8.6: push 到 origin（如果有远端）**

```bash
git remote -v
git push origin main
```

Expected: 推送成功。如果没有 origin 或推送失败，记下来稍后告诉用户。

---

## Task 9：关闭 issue #22

> 这一步在 patra-api 仓库（issue 所在仓库）执行，需要 `gh` CLI。

- [ ] **Step 9.1: 切回 patra-api 仓库 worktree**

```bash
cd /Users/linqibin/Projects/Products/Patra/patra-api/.claude/worktrees/issue-22-mysql-ops-spec
```

- [ ] **Step 9.2: 验证 issue #22 当前状态**

```bash
gh issue view 22 --json state,title
```

Expected: `state: OPEN`。

- [ ] **Step 9.3: 关闭 issue，附决策说明**

```bash
gh issue close 22 --comment "$(cat <<'EOF'
## 决策结论：方案 A（独立 mysql-ops 容器）

### 选择理由

- **零代码改动**：保留 XXL-Job 全部能力（手动触发、jobParam、执行历史、告警面板）；11 个 `@XxlJob` handler 无需任何调整
- **改造工作量小**：仅 5 个文件改动（全部在 patra-infra），无 Java 代码影响
- **延展性好**：命名为 mysql-ops（运维组件用 MySQL）而非绑定 xxl-job，为未来其他只支持 MySQL 的运维工具预留共用空间
- **资源开销可控**：mysql:8.0 经调优后内存占用 ~150MB

### 拒绝方案 B / C 的理由

- **方案 B**（换 @Scheduled）：4 个 handler 用 `jobParam` 接收运行时参数（PubmedBaseline / VenueEnrich / PubmedHarvest / OutboxRelay），切到 @Scheduled 后必须补 admin REST 端点弥补手动触发能力，改造成本 > 一个 MySQL 容器
- **方案 C**（等上游 PG 支持）：XXL-Job 3.4.0（2024-04 发布）仍未支持 PG，时间不可控

### 落地文档

- 设计：`docs/superpowers/specs/2026-05-17-mysql-ops-container-design.md`
- 实施计划：`docs/superpowers/plans/2026-05-17-mysql-ops-container-plan.md`
- 实施 commit：见 patra-infra 仓库
EOF
)"
```

- [ ] **Step 9.4: 验证 issue 已关闭**

```bash
gh issue view 22 --json state,closedAt
```

Expected: `state: CLOSED`，`closedAt` 是当前时间。

---

## Task 10：commit plan 文档（patra-api 侧）

- [ ] **Step 10.1: 确认 worktree 状态**

```bash
cd /Users/linqibin/Projects/Products/Patra/patra-api/.claude/worktrees/issue-22-mysql-ops-spec
git status
```

Expected: 只有 `docs/superpowers/plans/2026-05-17-mysql-ops-container-plan.md` 是 untracked。

- [ ] **Step 10.2: commit plan 文档**

```bash
git add docs/superpowers/plans/2026-05-17-mysql-ops-container-plan.md
git commit -m "$(cat <<'EOF'
docs(plan): 新增 mysql-ops 容器实施计划

实施计划基于 2026-05-17-mysql-ops-container-design.md 落地，
覆盖 patra-infra 仓库的 5 个文件改动 + 端到端验证。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
git log --oneline -3
```

Expected: 看到新 commit。

- [ ] **Step 10.3: 报告完成**

输出最终汇总：spec/plan/实施 commit 全部完成，issue #22 已关闭。

---

## 失败模式与回滚

如果 Task 4-6 任何步骤失败且无法定位，回滚策略：

```bash
# 1. 停止 mysql-ops 和 xxl-job-admin
cd /Users/linqibin/Projects/Products/Patra/patra-infra
docker compose -f docker/docker-compose.jobs.yaml stop mysql-ops xxl-job-admin
docker compose -f docker/docker-compose.jobs.yaml rm -f mysql-ops xxl-job-admin

# 2. 清空数据卷
rm -rf ~/.patra/docker/mysql-ops/data

# 3. 重置 compose 文件（如果尚未 commit）
git checkout -- docker/docker-compose.jobs.yaml docker/.env.dev docker/.env.example docker/README.md
rm -rf docker/mysql-ops

# 4. 重新走 Task 1-5
```

如果 Task 8 commit 后才发现问题：

```bash
git revert HEAD  # 创建反向 commit
# 或如果还没 push：
git reset --hard HEAD~1
```

---

## 自审清单（plan 写完后自检）

- [x] **Spec 覆盖**：spec §3 in-scope 4 个 deliverable 全部对应到 Task 1-3、Task 7；spec §8 验收清单 7 项对应到 Task 4-6、Task 9
- [x] **无占位符**：每个 step 都给出具体命令、完整代码片段、预期输出
- [x] **类型一致**：mysql-ops 服务名、容器名、数据卷路径、env 变量名在所有 Task 中保持一致
- [x] **回滚路径**：失败模式有显式回滚命令
