# 配置迁移（二）：ingest、catalog，并收尾清理（PAP-77）

> **状态**：设计已确认（2026-10-14），待实现
> **Issue**：PAP-77 [BE] 配置迁移（二）：ingest、catalog，并收尾清理
> **版本**：v0.9 配置中心（`docs/patra/release-specs/v0.9-config-center.md` 决策 A、C、E、F，Done 判定 D2）
> **前置**：
> - PAP-76：配置迁移（一）（`docs/patra/specs/2026-10-13-config-migration-batch1-design.md`，决定 1–7 与第 10 节交来的约束全部沿用）
> - PAP-74：starter 与启动器（`docs/patra/specs/2026-10-13-nacos-config-starter-design.md`）
> - PAP-75：配置目录与推送（`docs/patra/specs/2026-10-13-nacos-config-push-design.md`）

## 1. 目标

ingest、catalog 改为从 Nacos 读配置，6 个服务全部迁完。之后 compose 的环境文件只剩 PAP-72 的本地变量、Nacos 账号和密钥，不生效的 `.env.dev` 删除。

**验收**（Issue AC）：

1. ingest、catalog 在 IDE 与 mini 容器中都能启动；采集与导入 Job 能正常触发运行。
2. 仓库里不再有 `application-dev.yml`、`application-container.yml` 与 `.env.dev`。
3. Nacos 配置里没有真正的密钥，只有 `${变量}` 引用。
4. `./gradlew check` 通过。

## 2. 现状事实（2026-10-14 核对）

1. 两种运行方式的差异在两个 profile 文件里，容器侧的值来自 `.env.common`、`.env.ingest`、`.env.catalog`：
   - 数据库：ingest 的 `patra_ingest`；catalog 的 `patra_catalog` 和 Spring Batch 用的 `patra_batch`。
   - MinIO：地址、账号在 `.env.common`，bucket 在 `.env.<svc>`（`patra-ingest-cache`、`patra-catalog-cache`）。
   - Redis（只有 catalog）：IDE 写在 `application-dev.yml`，容器用 `.env.common` 的 `REDIS_HOST`、`REDIS_PORT`。
   - 本机密钥导入：`application-dev.yml` 里的 `file:${user.home}/.patra/secrets/*.env[.properties]`。
2. 除基础设施坐标外，两份 profile 有 6 处值不同（见决定 2）。
3. xxl-job 管理端地址、RocketMQ namesrv 写在 jar 里，默认值跟随 `PATRA_INFRA_HOST`，另留了 `XXL_JOB_ADMIN_ADDRESSES`、`ROCKETMQ_NAMESRV` 两个环境变量入口，两种运行方式都没设。
4. catalog 的 jar 默认开着青果代理（`PROXY_TUNNEL_ENABLED:true`），没有凭据时 `TunnelProxyAutoConfiguration` 断言失败、启动中止。容器靠 `.env.catalog` 的 `PROXY_TUNNEL_ENABLED=false` 关掉。本机和 mini 都没有 `~/.patra/secrets/catalog.env`、`ingest.env`：代理凭据、Scopus key、RocketMQ ACL 在两台机器上都没配。
5. catalog 引入了 RocketMQ 依赖，但没有任何 RocketMQ 配置，本次不动。
6. 两个服务的集成测试与 e2e 测试用各自的测试 profile（`application-test*.yml`、`application-e2e-test.yml`、`application-integration-test.yml`），在测试 profile 里自己设服务名、Nacos 和代理，不依赖 dev profile，不连 Nacos。
7. `.env.dev` 不被任何 compose 文件或脚本加载：compose 只自动读 `.env`；Postgres 密码用 `docker-compose.core.yaml` 里的默认值 `${POSTGRES_PASSWORD:-123456}`，mysql-ops 的账号以 `.env` 为准。提到它的地方：根 `CLAUDE.md` 与 `AGENTS.md` 的安全红线例外、`patra-infra/CLAUDE.md`、`patra-infra/docker/README.md`、`patra-infra/cd/detect-changes.sh` 及其测试、`.env` 与 `.env.example` 的注释。
8. PAP-76 已把 starter 换进构建约定插件，并给容器镜像配好了 nacos-client 的日志与快照目录；两个服务现在已经带着 starter，只是没用启动器。

## 3. 决定

PAP-76 的决定 1（Nacos 只放随环境变化的值）、3（数据库密码写 `${PATRA_DB_PASSWORD:123456}`）、5（删服务专属环境文件）、7（`ApplicationConfigTest` 驱动）原样沿用。

| # | 问题 | 决定 | 理由 |
|---|------|------|------|
| 1 | 两个服务共用的坐标放哪 | MinIO 地址与账号、xxl-job 管理端地址放 `patra-common.yaml`；bucket、库地址、RocketMQ namesrv 放各服务文件 | 两个服务都用 MinIO 和 xxl-job；其他服务读到这些键也不会绑定 |
| 2 | dev 与 container 不一致的 6 处取哪个值（2026-10-14 用户确认） | 见下表 | 两种运行方式只差本地变量 |
| 3 | 可选的外部凭据怎么写 | Nacos 里写成带空默认值的引用：`${PROXY_AUTH_KEY:}`、`${PROXY_AUTH_PWD:}`、`${SCOPUS_API_KEY:}`、`${ROCKETMQ_AK:}`、`${ROCKETMQ_SK:}` | 两台机器都没配，空默认值让 PAP-76 加的变量检查放行；要用时在 `~/.patra/secrets/<svc>.env` 放值 |
| 4 | 青果代理开关 | Nacos 的 `patra-catalog.yaml` 写 `false`；代理主机、端口留在 jar | 开关取决于这台机器有没有凭据，随环境变；主机和端口和代码一起变 |
| 5 | 不再使用的环境变量入口 | jar 里删掉 `XXL_JOB_ADMIN_ADDRESSES`、`ROCKETMQ_NAMESRV`、`PROXY_TUNNEL_ENABLED` 三个入口，以及 `SCOPUS_API_KEY`、`PROXY_AUTH_*`、`ROCKETMQ_AK/SK` 的引用（改写在 Nacos）；container 文件里的 `OBJECT_STORAGE_PROVIDER`、`MINIO_BUCKET`、`MAX_FILE_SIZE` 随文件删除，值按第 3 节末尾写进 jar 或 Nacos。与环境无关的入口（`INGEST_LEASE_TTL`、`TOPIC_PREFIX`、`XXL_JOB_LOG_RETENTION_DAYS` 等）不动 | 只清理本次搬走的值，避免 jar 和 Nacos 两处都能改同一个值 |
| 6 | `.env.dev` | 删除，并删除所有提及；安全红线的「已知例外」只剩 `.env` | 不被加载，留着会误导 |

决定 2 的取值：

| # | 配置 | dev（IDE） | container（mini） | 取值，放在 jar |
|---|------|-----------|------------------|---------------|
| 1 | ingest 表达式编译 | 宽松，长度、参数数上限都是 0（不限） | 严格，5000 / 50 / 100 | 严格，5000 / 50 / 100 |
| 2 | 日志级别 | ingest `dev.linqibin: DEBUG`；catalog 两个类单独设 INFO | INFO | INFO（不写，用默认）。要看 DEBUG 时在 Nacos 里临时覆盖，PAP-78 之后不重启即可生效 |
| 3 | catalog 导入上限 `import-limit.max-records` | 50 万条 | 不限 | 不限（不写） |
| 4 | 青果代理开关 | 开 | 关 | 关（决定 4，放 Nacos） |
| 5 | 调内部服务的连接超时 `http-interface.connect-timeout` | 10s | 2s | 2s（jar 现有值） |
| 6 | catalog 的 Redis 超时 | 连接 10s、读 30s | 默认 | 默认（不写） |

两份 profile 里相同、与环境无关的值搬进 jar 的 `application.yml`：

- ingest：只写决定 2 的严格模式和三个上限。表达式编译的其余参数（`multi.repeat-enabled: false`、`compiler.enabled: true`、`query-param-bridge.enabled: true`）和对象存储的 `active-provider: minio`、重试 `max-attempts: 3`、`wait-duration: 1000` 与 starter 的默认值相同，不写。
- catalog：`spring.batch.job.enabled: false`；对象存储 `max-file-size: 5368709120`（starter 默认 100MB）。`active-provider: minio` 是默认值，不写。

## 4. 每个服务的改动

做法与 PAP-76 第 4 节相同，顺序是 ingest → catalog。

1. **启动类**：改为 `PatraApplication.run("patra-ingest", …)`、`PatraApplication.run("patra-catalog", …)`。
2. **`application.yml`**：
   - 删掉 `spring.application.name`、`spring.profiles.active`、`spring.cloud.nacos` 整段；
   - 按决定 5 删掉被 Nacos 接管的环境变量入口；xxl-job 管理端地址整项删除（改由 Nacos 提供），执行器的 `ip`、`logpath` 跟随本地变量，保留；
   - catalog 删掉 `patra.catalog.scopus.api-key` 与代理的 `enabled`、`auth-key`、`auth-pwd`（改由 Nacos 提供）；
   - 按第 3 节搬入与环境无关的值；
   - `classpath:ingest-error-config.yaml`、`classpath:ingest-rocketmq.yaml`、`classpath:catalog-error-config.yaml` 导入保留。
3. **`ingest-rocketmq.yaml`**：删掉 `name-server`、`access-key`、`secret-key`（改由 Nacos 提供），其余不动。
4. **删除** 两个服务的 `application-dev.yml`、`application-container.yml`。
5. **测试**：测试 profile 原则上不改。某个测试因 jar 新增的值失败时（最可能是表达式编译改为严格模式），在它的测试 profile 里补回原值，并在计划里记下原因。
6. **`ApplicationConfigTest`**：照抄 PAP-76 的两条用例到两个 boot 模块，先失败再迁移。

## 5. Nacos 配置

```yaml
# patra-common.yaml（在现有内容上新增）
linqibin:
  starter:
    object-storage:
      providers:
        minio:
          endpoint: http://${PATRA_INFRA_HOST:127.0.0.1}:19000
          access-key: ${MINIO_ACCESS_KEY:minioadmin}
          secret-key: ${MINIO_SECRET_KEY:minioadmin123}

xxl:
  job:
    admin:
      addresses: http://${PATRA_INFRA_HOST:127.0.0.1}:7070/xxl-job-admin
```

```yaml
# patra-ingest.yaml
patra.config-source.service: patra-ingest.yaml

spring:
  datasource:
    url: jdbc:postgresql://${PATRA_INFRA_HOST:127.0.0.1}:15432/patra_ingest

linqibin:
  starter:
    object-storage:
      providers:
        minio:
          bucket: patra-ingest-cache

rocketmq:
  name-server: ${PATRA_INFRA_HOST:127.0.0.1}:9876
  access-key: ${ROCKETMQ_AK:}
  secret-key: ${ROCKETMQ_SK:}
```

```yaml
# patra-catalog.yaml
patra.config-source.service: patra-catalog.yaml

spring:
  datasource:
    url: jdbc:postgresql://${PATRA_INFRA_HOST:127.0.0.1}:15432/patra_catalog
  data:
    redis:
      password: ${REDIS_PASSWORD}

patra:
  catalog:
    scopus:
      api-key: ${SCOPUS_API_KEY:}

linqibin:
  starter:
    batch:
      datasource:
        url: jdbc:postgresql://${PATRA_INFRA_HOST:127.0.0.1}:15432/patra_batch
        username: postgres
        password: ${PATRA_DB_PASSWORD:123456}
    object-storage:
      providers:
        minio:
          bucket: patra-catalog-cache
    rest-client:
      proxy:
        tunnel:
          enabled: false
          auth-key: ${PROXY_AUTH_KEY:}
          auth-pwd: ${PROXY_AUTH_PWD:}
```

说明：

- MinIO 账号是已公开的 dev 默认值；键名以 `access-key`、`secret-key` 结尾，按 PAP-75 的检查只能写成带默认值的引用。
- 数据库账号沿用公共配置的 `postgres` 与 `${PATRA_DB_PASSWORD:123456}`（PAP-76 第 10 节要求确认：两个服务的库账号确实是 `postgres`）。
- 公共配置新增的引用都带默认值，不违反 PAP-76 定下的「公共配置不写没有默认值的引用」。
- 文件内容须通过 `NacosConfigFilesTest`。

## 6. 容器侧

- 删除 `.env.ingest`、`.env.catalog`、`.env.dev`。
- `.env.common` 只留 `PATRA_INFRA_HOST`、`PATRA_LOG_DIR`，删掉 `SPRING_PROFILES_ACTIVE`、Redis 坐标、MinIO 坐标及其注释；文件头注释改写为「只放本地变量」。
- `docker-compose.apps.yaml`：
  - ingest：`.env.common`、`nacos.env`（必需）、`ingest.env`（可选）；
  - catalog：`.env.common`、`nacos.env`、`redis.env`（必需）、`catalog.env`（可选）；
  - 头部注释同步。
- mini 上不用新建文件。

## 7. 测试与验收

按顺序进行。**凡动 mini 的步骤，执行前先征得用户同意。**

1. **本机测试**：
   - 每个服务先写 `ApplicationConfigTest`，看它失败，再迁移；
   - 两个服务各跑 `./gradlew :<module>:check`；
   - PR 前跑全量 `./gradlew check integrationTest e2eTest`；
   - `patra-infra/cd/detect-changes.test.sh` 通过。
2. **本机 IDE 连 mini 的 Nacos 启动**：
   - 本机运行 `nacos-push.sh` 推送本分支的配置。mini 上 ingest、catalog 的旧镜像不读 Nacos 配置，不受影响；其余 4 个服务读到公共配置新增的键，不绑定，不受影响；
   - 两个服务各启动一次，启动日志里要看到：两份配置都已导入、签到通过、数据库已连上、xxl-job 执行器已注册；catalog 还要看到 Redis 与批处理库已连上；
   - Nacos 控制台能看到两个服务的实例。
3. **mini 部署**：在本分支手动触发 `cd.yml`（service 留空）。验收标准：6 个容器都健康。
4. **Job 能运行**：
   - catalog：经 xxl-job 管理端 API 手动触发一个导入 Job（`meshQualifierImportJob`，文件最小），Job 日志显示执行成功。下载 NLM 文件本身时好时坏（与配置无关），失败原因是下载网络时改触发 `rorOrganizationImportJob`。
   - ingest：调度中心里没有 ingest 的执行器分组和 Job（2026-10-14 查 mini 的 xxl-job 库确认），没有可触发的采集任务。验收改为：新容器的执行器在调度中心注册上（`xxl_job_registry` 里 `patra-ingest` 的地址是新容器的 IP、更新时间在部署之后）。建分组和 Job 不在本次范围。
5. **两种运行方式一致**：只列变量名（`docker inspect … | cut -d= -f1`），6 个容器只剩本地变量、Nacos 账号、各自用到的密钥和镜像自带变量；不再有 `SPRING_PROFILES_ACTIVE`、`REDIS_HOST`、`REDIS_PORT`、`MINIO_*`、`*_DB_*`。
6. **D2 核对**：`git ls-files` 里没有 `application-dev.yml`、`application-container.yml`、`.env.dev`；`patra-infra/nacos/dev/` 里没有真正的密钥。

## 8. 已知代价

- **部署后 ingest、catalog 回滚不到迁移前的旧镜像**：与 PAP-76 相同，旧镜像靠已删除的变量启动。接受，出问题就修代码或改 Nacos 后重新部署。
- **本机 IDE 里 ingest 的日志变少**：不再默认打 DEBUG。
- **本机 IDE 里 catalog 会完整导入**：不再限 50 万条，导入大文件时耗时更长。
- **ingest 的表达式编译在 IDE 里变严格**：超长查询会在本机直接报错，而不是只在 mini 上报。
- **本机 IDE 调 mini 上服务的首个请求可能超时**：调内部服务的连接超时从 10s 回到 2s。`patra-infra/docs/mac-mini-connectivity.md` §7 记录过 tailscale 通道空闲回收后首个请求建连要 3~5s，撞上时这个请求失败，重试即可。

## 9. 文档

- `patra-infra/CLAUDE.md`：环境文件分层只剩 `.env.common` 的本地变量与 `~/.patra/secrets/`；`.env.dev` 的说明删除。
- `patra-infra/docker/README.md`：环境文件清单、密钥表、mysql-ops 凭据出处（改为 `.env`）。
- 根 `CLAUDE.md`、`AGENTS.md`：安全红线「已知例外」去掉 `.env.dev`。
- `patra-api/patra-ingest/README.md`、`patra-api/patra-catalog/README.md`：配置来源改为 Nacos，去掉 profile 文件的描述。
- `.claude/skills/patra-backend/skills/patra-hexagonal/resources/configuration.md`：从 dev / container 两个 profile 的写法改为 Nacos 的写法（PAP-76 第 10 节交来）。
- `patra-infra/nacos/dev/README.md`：公共配置里放什么，补上 MinIO 与 xxl-job。
- `patra-infra/cd/detect-changes.sh` 与其测试：去掉 `.env.dev` 的路由分支与用例。
- `patra-infra/docs/mac-mini-connectivity.md` §7：「应对」一条改为已取消、两种运行方式都用 2s。
- `LetPubConfiguration`（catalog-boot）的注释和告警文字里「设 `PROXY_TUNNEL_ENABLED=true`」改为在 Nacos 的 `patra-catalog.yaml` 打开代理开关。

## 10. 不做的事

- 不接动态刷新（PAP-78）。
- 不给 catalog 配 RocketMQ。
- 不清理与环境无关的环境变量入口（`INGEST_LEASE_TTL`、`TOPIC_PREFIX`、`OUTBOX_STRICT_CHANNEL_WHITELIST` 等）。
- 不在两台机器上补配代理凭据、Scopus key、RocketMQ ACL。
- 不改 registry、object-storage 的 openapi 依赖（PAP-76 验收时发现它们没有 api-docs，与本次无关）。
