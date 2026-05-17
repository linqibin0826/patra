# 设计：为不支持 PostgreSQL 的运维组件提供 mysql-ops 容器

- **状态**：Draft（等用户审阅）
- **日期**：2026-05-17
- **作者**：linqibin（与 Claude Code 协作）
- **关联**：Issue #22（XXL-Job 调度方案决策） / PR #21 PG 迁移 follow-up §1
- **范围**：仅 `patra-infra/docker` 一处；`patra-api` **零改动**
- **前提**：绿地项目，单人开发，单实例部署（用户已确认）

---

## 1. 背景

PR #21 将 Patra 后端从 MySQL 8.0 完整迁移到 PostgreSQL 17 后，`patra-infra/docker/docker-compose.jobs.yaml` 中的 `xxl-job-admin` 容器因失去 MySQL 后端而无法启动，导致 catalog/ingest 启动后反复向 `localhost:7070/xxl-job-admin/api/registry` 抛 `ConnectionRefused`，调度功能完全失效（共 11 个 `@XxlJob` handler：catalog 9 个 + ingest 2 个）。

Issue #22 列了三个候选方案；本设计落地的是 **方案 A**：单独引入一个 MySQL 容器，仅服务于"不支持 PG 的运维/管理面组件"，业务库继续走 PG。

## 2. 决策与拒绝项

| 方案 | 描述 | 评估 |
|---|---|---|
| **A. 独立 mysql-ops 容器（采用）** | 在 `docker-compose.jobs.yaml` 加一个轻量 MySQL 实例，专供 admin 类组件，业务无关 | **采用** — 零代码改动；保留 XXL-Job 全部能力（手动触发、jobParam、执行历史、告警）；为未来其他只支持 MySQL 的运维工具预留扩展点 |
| B. 替换调度器为 `@Scheduled` | 11 个 handler 改造 + 失去手动触发/参数能力 + 需补 admin 端点 | 否决 — 4 个 handler 用了 `jobParam`，运行时参数能力必须补一个 admin REST 端点；改造成本 > 一个 MySQL 容器的运行成本 |
| C. 等 XXL-Job 上游 PG 支持 | XXL-Job 3.4.0 (2024-04) 仍未支持 PG，时间不可控 | 否决 — 调度功能持续失效不可接受 |

### 命名理由

服务命名为 `mysql-ops` 而非 `mysql-xxl-job`：

- **定位清晰**：业务库 = PG / 运维管理面 = MySQL，形成自然对照
- **延展性**：未来其它只支持 MySQL 的运维工具（监控面板、其它调度器等）可共用此实例
- **去耦合**：命名不绑定到当前唯一消费者，避免 XXL-Job 未来替换时再次重命名

## 3. 范围

### In scope

- `patra-infra/docker/docker-compose.jobs.yaml`：新增 `mysql-ops` 服务；调整 `xxl-job-admin` 的连接串与 `depends_on`
- `patra-infra/docker/mysql-ops/init-scripts/01-schema.sql`：新建，包含 XXL-Job 3.2.0 官方表结构与种子数据
- `patra-infra/docker/.env.dev`：新增 `MYSQL_OPS_ROOT_PASSWORD` / `MYSQL_OPS_USERNAME` / `MYSQL_OPS_PASSWORD`
- `patra-infra/docker/.env.dev.example`（若存在）：同步示例
- `patra-infra/docker/README.md`：补充 mysql-ops 段落

### Out of scope

- `patra-api` 仓库的所有代码与配置（catalog/ingest 的 yml 已指向 `localhost:7070`，无需改动）
- `patra-infra/k8s/`（Kubernetes 编排作为后续 follow-up，本次只覆盖本地开发栈）
- mysql-ops 的备份策略（XXL-Job admin 数据丢失只丢调度记录和执行日志，任务按 cron 下次自动跑，无业务影响）
- mysql-ops 端口对外暴露（仅容器网络内访问；如需排查走 `docker exec`）

## 4. 容器规格

| 项 | 值 |
|---|---|
| 服务名 | `mysql-ops` |
| 容器名 | `patra-mysql-ops` |
| 镜像 | `mysql:8.0`（XXL-Job 3.2.0 官方推荐版本） |
| 网络 | `patra-net`（与 `xxl-job-admin` 同网，admin 通过 `mysql-ops:3306` 访问） |
| 对外端口 | **不暴露**（避免污染宿主机端口） |
| 数据卷 | `${HOME}/.patra/docker/mysql-ops/data:/var/lib/mysql`（与既有 `${HOME}/.patra/docker/...` 模式一致） |
| 初始化脚本卷 | `./mysql-ops/init-scripts:/docker-entrypoint-initdb.d:ro` |
| 字符集 | `utf8mb4` / `utf8mb4_unicode_ci` |
| 资源调优 | `--innodb-buffer-pool-size=64M --performance-schema=OFF --skip-host-cache --skip-name-resolve`（把内存占用从 ~400MB 压到 ~150MB） |
| Healthcheck | `mysqladmin ping -h 127.0.0.1 -u root -p${MYSQL_OPS_ROOT_PASSWORD}`，10s/5s/10/20s |
| Restart 策略 | `unless-stopped`（与 jobs 栈其他服务一致） |
| TZ | `Asia/Shanghai` |

### 账号

- `root` / `${MYSQL_OPS_ROOT_PASSWORD}` — 仅容器内使用
- `${MYSQL_OPS_USERNAME}` / `${MYSQL_OPS_PASSWORD}` — admin 业务账号，对 `xxl_job` 库 ALL PRIVILEGES

### 初始化脚本（`docker/mysql-ops/init-scripts/01-schema.sql`）

来源：XXL-Job 3.2.0 GitHub release 的 `doc/db/tables_xxl_job.sql`。内容职责：

1. `CREATE DATABASE IF NOT EXISTS xxl_job DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`
2. `USE xxl_job;` 后导入 8 张 XXL-Job 元数据表（`xxl_job_info` / `xxl_job_log` / `xxl_job_log_report` / `xxl_job_logglue` / `xxl_job_registry` / `xxl_job_group` / `xxl_job_user` / `xxl_job_lock`）
3. 插入 XXL-Job 默认 admin 用户 / 默认执行器组等种子数据（沿用官方脚本）

注：MySQL 8.0 容器的 `MYSQL_DATABASE` / `MYSQL_USER` / `MYSQL_PASSWORD` env 变量会在初始化阶段自动建库建账号；`01-schema.sql` 只负责导入 XXL-Job 表与种子数据。

## 5. docker-compose.jobs.yaml 增量片段

新增服务定义：

```yaml
services:
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

调整 `xxl-job-admin` 现有定义：

```yaml
  xxl-job-admin:
    image: xuxueli/xxl-job-admin:3.2.0
    container_name: patra-xxl-job-admin
    restart: unless-stopped
    environment:
      TZ: Asia/Shanghai
      PARAMS: >
        --server.port=8080
        --server.servlet.context-path=/xxl-job-admin
        --spring.datasource.url=jdbc:mysql://mysql-ops:3306/xxl_job?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
        --spring.datasource.username=${MYSQL_OPS_USERNAME}
        --spring.datasource.password=${MYSQL_OPS_PASSWORD}
        --xxl.job.accessToken=
    volumes:
      - ${HOME}/.patra/docker/xxl-job-admin/logs:/data/applogs
    ports:
      - "7070:8080"
    depends_on:
      mysql-ops:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080'"]
      interval: 15s
      timeout: 5s
      retries: 10
      start_period: 20s
```

差异点（相对 PR #21 前的版本）：

- `jdbc:mysql://mysql:3306/...` → `jdbc:mysql://mysql-ops:3306/...`
- 硬编码账号密码 → env 占位符
- 新增 `depends_on: mysql-ops: condition: service_healthy`

## 6. env 变量约定

`patra-infra/docker/.env.dev` 新增：

```
# === mysql-ops（运维组件用 MySQL，业务库走 PG） ===
MYSQL_OPS_ROOT_PASSWORD=patra_ops_root_dev
MYSQL_OPS_USERNAME=xxl_job
MYSQL_OPS_PASSWORD=xxl_job_dev
```

密码值为开发期默认；生产环境另由部署流程注入。命名上 `MYSQL_OPS_*` 与既有 `POSTGRES_*` 风格对齐。

## 7. 失败模式与兜底

| 风险 | 处理 |
|---|---|
| 初始化脚本失败（语法错误、charset 冲突） | mysql-ops healthcheck 一直不通过 → xxl-job-admin 不启动 → 错误立即可见，看 `docker logs patra-mysql-ops` |
| 数据卷损坏（开发机睡眠/异常退出） | `rm -rf ${HOME}/.patra/docker/mysql-ops/data` 重启即可（无业务数据，重新跑 init 脚本即恢复） |
| xxl-job-admin 偶发连不上 | depends_on healthcheck 已保证启动顺序；运行期问题看 admin 容器日志 |
| 内存占用偏高 | 已通过 `innodb-buffer-pool-size=64M` + 关闭 `performance-schema` 控制；如仍偏高可继续调小 |
| 端口冲突（7070） | 与既有部署一致，不变；mysql-ops 不暴露端口故无新增冲突点 |

## 8. 验收清单

- [ ] `docker compose -p patra -f docker/docker-compose.jobs.yaml up -d mysql-ops xxl-job-admin` 两个容器都到 healthy
- [ ] `http://localhost:7070/xxl-job-admin` 可登录（默认 `admin / 123456`）
- [ ] catalog/ingest 启动后 admin "执行器管理" 能看到两个 executor 心跳
- [ ] 在 admin 手动触发 `pubmedBaselineImportJob` 一次（jobParam 传 `2024`），能在 "调度日志" 看到执行结果
- [ ] catalog/ingest 启动日志中不再出现 `ConnectionRefused: localhost:7070`
- [ ] `docker stats patra-mysql-ops` 内存占用 < 200MB
- [ ] Issue #22 关闭，附本 spec 路径

## 9. 不做的事（明示）

- ❌ 不暴露 mysql-ops 端口到宿主机
- ❌ 不做 mysql-ops 备份策略
- ❌ 不复用业务 PG 实例（XXL-Job admin 不支持 PG，物理上没法复用）
- ❌ 不动 patra-api 任何代码或 yml 配置
- ❌ 不动 patra-infra/k8s/（K8s 编排作为后续 follow-up）
- ❌ 不升级 XXL-Job 到 3.4.0（与 3.2.0 schema 有微调，本次保持稳定，升级单独评估）

## 10. 现场验证项

- 10.1 `mysql-ops` 容器首次启动时间（init 脚本导入 8 张表 + 种子数据应在 30s 内）
- 10.2 admin 启动后端到端可访问性（容器 healthy ≠ HTTP 可用，需手动 curl）
- 10.3 catalog/ingest 注册延迟（XXL-Job 默认 30s 心跳一次）
- 10.4 资源占用（`docker stats` 实测 mysql-ops 内存）

## 11. 关联与后续

- 关闭 Issue #22，引用本 spec
- 如未来引入其它只支持 MySQL 的运维工具（如 RocketMQ Console 持久化、监控面板等），可在同一 `mysql-ops` 实例内新增数据库，避免再起新容器
- K8s 编排适配（patra-infra/k8s/）作为单独 issue 跟进
