# 设计：Spring Boot 4.0.6 / Spring Cloud 2025.1.1 升级 + 注册中心 Consul → Nacos

- **状态**：Draft（等用户审阅）
- **日期**：2026-05-18
- **作者**：linqibin（与 Claude Code 协作）
- **关联 Issue**：[#29](https://github.com/linqibin0826/patra-api/issues/29)
- **范围**：Gradle 版本对齐、SCA 2025.1.0.0 引入、5 个 boot 模块 + 基础设施 compose 由 Consul 全量切换到 Nacos
- **前提**：单人 greenfield 项目；无 bootstrap 配置遗留；SCA 2025.1.0.0 GA（2026-02-06）已正式支持 SB 4.0.x + SC 2025.1.x

---

## 1. 背景

此前 Nacos 官方未适配 Spring Boot 4.x，patra-api 临时采用 Consul 作为注册中心。2026-02-06 `spring-cloud-alibaba 2025.1.0.0` GA 后，可以切回 Nacos 并把 Spring Boot/Cloud 一起升到最新补丁版：

- Spring Boot：4.0.1 → 4.0.6（2026-04-23）
- Spring Cloud：2025.1.0 → 2025.1.1（2026-01-29）
- 新增 spring-cloud-alibaba：2025.1.0.0（内嵌 nacos-client 3.1.1）

绿地项目原则下，Consul 相关配置全部直接删除，不保留任何 fallback 或并行实现。

## 2. 决策与拒绝项

### 决策 A：Nacos 鉴权模式（dev 环境）

| 方案 | 评估 |
|---|---|
| **A1. dev 与 prod 一致开启鉴权（采用）** | `NACOS_AUTH_ENABLE=true` + token / identity 三件套写入 `.env`，`.env.example` 给占位与 `openssl rand` 生成命令。客户端用 `spring.cloud.nacos.username/password=nacos/nacos`（首启默认账号）。**采用** — Nacos 3.x 默认即鉴权开启，dev 关掉会让生产迁移踩坑；保持 dev/prod 形态一致更划算。 |
| A2. dev 关闭鉴权 | 否决 — 短期省一行配置，长期掩盖鉴权相关 bug；与"绿地项目用最优方案"原则相悖。 |

### 决策 B：Nacos 数据存储

| 方案 | 评估 |
|---|---|
| **B1. 内嵌 Derby（采用）** | 单机 standalone 模式默认行为，数据挂载到 `${HOME}/.patra/docker/nacos/data`，与 Consul 原用法（`/consul/data` 挂载）对等。**采用**。 |
| B2. 复用 patra-postgres，独立 schema | 否决 — 多一层启动顺序依赖（nacos 必须等 postgres ready）；本 issue 范围明确为"仅服务发现的等价替换"，不引入额外耦合。 |
| B3. MySQL | 否决 — 项目刚完成 MySQL → PostgreSQL 迁移（issue #21），不再回头引入 MySQL。 |

### 决策 C：服务注册配置策略（Consul 字段映射）

绿地项目下，凡是 Consul 特有但 Nacos 默认行为已覆盖的配置项，**全部直接删除**，不保留语义注释。映射表见 §6。

| 方案 | 评估 |
|---|---|
| **C1. 砍掉等价默认项，仅保留差异化字段（采用）** | `health-check-*` / `register-health-check` / `heartbeat` / `prefer-ip-address` / `instance-id` 全删；只保留 `server-addr`、`service`、`fail-fast`、dev 的 `ip`、gateway 的 `metadata.scheme`。**采用** — 配置噪音最小、可读性最强。 |
| C2. 一比一映射（保留所有语义） | 否决 — 大部分字段写出来等价于 Nacos 默认值，纯噪音。 |

### 决策 D：环境变量命名

| 方案 | 评估 |
|---|---|
| **D1. `NACOS_HOST` / `NACOS_PORT`（采用）** | 与现有 `CONSUL_HOST/PORT`、`POSTGRES_HOST`、`REDIS_HOST` 命名同款，迁移心智成本最小。`spring.cloud.nacos.discovery.server-addr` 在 yml 中拼接为 `${NACOS_HOST}:${NACOS_PORT}`。**采用**。 |
| D2. `NACOS_SERVER_ADDR` | 否决 — 与 Spring 配置项同名虽优雅，但破坏 patra 既有 env 命名一致性。 |

### 决策 E：迁移粒度

| 方案 | 评估 |
|---|---|
| **E1. 4 个原子 commit 串行推进（采用）** | (1) Gradle 切换 → (2) 应用 yml 切换 → (3) Docker compose 切换 → (4) 联调验证 + 文档清扫。每步独立 build/test 可验证，回滚粒度细。**采用** — 与项目此前 PostgreSQL 迁移、patra-infra 整合的提交风格一致。 |
| E2. 一次性大爆炸 commit | 否决 — 翻车时难以二分定位、回滚成本高。 |

### 决策 F：是否引入 Nacos 配置中心

| 方案 | 评估 |
|---|---|
| **F1. 不引入（采用）** | issue 明确 "本 issue 仅做服务发现的等价替换"。SCA 2025.1.0.0 已移除 bootstrap 配置机制，但本项目无 bootstrap.yml/.properties，影响为零。**采用**。 |
| F2. 顺手引入配置外置化 | 否决 — 超出本 issue 范围；如有需要单独立 issue。 |

## 3. 范围

### In scope

**Gradle 层**：

- `gradle/libs.versions.toml`：升级 spring-boot/spring-cloud；新增 spring-cloud-alibaba；删除 consul starter；新增 nacos starter
- `build-logic/src/main/kotlin/LinqibinDependencyManagement.kt`：引入 SCA BOM（必须在 spring-cloud BOM **之后** import — `spring-dependency-management` 的规则是先 import 的 BOM 优先，把 SCA 放后面可以保证 spring-cloud 主版本不被 SCA 内部声明覆盖）
- `build-logic/src/main/kotlin/linqibin.hexagonal-boot.gradle.kts:72`：切换 starter 引用
- `build-logic/bin/` 旧 IDE/Gradle 编译产物：清理（顺手检查 `.gitignore`）

**应用配置层**（5 个 boot 模块）：

| 模块 | 文件 | 改造 |
|---|---|---|
| patra-registry-boot | `src/main/resources/application.yml` | 模板 A |
| patra-registry-boot | `src/main/resources/application-dev.yml` | 模板 B |
| patra-ingest-boot | `src/main/resources/application.yml` | 模板 A |
| patra-ingest-boot | `src/main/resources/application-dev.yml` | 模板 B |
| patra-ingest-boot | `src/test/resources/application-test-common.yml` | 模板 C |
| patra-catalog-boot | `src/main/resources/application.yml` | 模板 A |
| patra-catalog-boot | `src/main/resources/application-dev.yml` | 模板 B |
| patra-catalog-boot | `src/test/resources/application-e2e-test.yml` | 模板 C |
| patra-gateway-boot | `src/main/resources/application.yml` | 模板 A（含 metadata.scheme） |
| patra-object-storage-boot | `src/main/resources/application.yml` | 模板 A（含 metadata.scheme） |

**E2E Java 测试**（字符串属性切换）：

- `patra-catalog-boot/src/test/java/dev/linqibin/patra/catalog/integration/mesh/MeshScrImportE2E.java:71`
- `patra-ingest-boot/src/test/java/dev/linqibin/patra/ingest/integration/outbox/OutboxPatternE2E.java:112`

**基础设施层**：

- `infra/docker/docker-compose.core.yaml`：删 consul 服务块、加 nacos 服务块
- `infra/docker/docker-compose.dev.yaml`：仅 include 注释提到 consul，同步更新
- `infra/docker/.env`、`.env.example`：新增 `NACOS_AUTH_*` 三个变量
- `infra/scripts/init-volumes.sh:29`：`consul/data` → `nacos/data` + `nacos/logs`
- `infra/docker/README.md`：结构图、栈描述、示例 yml 全部同步

**文档**：

- `patra-gateway-boot/README.md`（行 83、153）
- `patra-object-storage/README.md`（行 257、308）
- `.claude/skills/patra-hexagonal/resources/configuration.md`（行 29）
- `.agents/skills/patra-hexagonal/resources/configuration.md`（行 29）
- `plugins/patra-codex/skills/patra-hexagonal/references/configuration.md`（行 29）

### Out of scope

- 升级 Spring Boot 到 4.1（4.1.0 仍处 RC）
- 顺手升级 Hibernate / Jackson / RocketMQ 等其他依赖（单独 issue 跟踪）
- 引入 Nacos 配置中心做配置外置化
- 旧 Consul 数据卷 `${HOME}/.patra/docker/consul/data` 主动清理（README 提示用户手工 `rm -rf`，避免误删）

## 4. 架构总览

```
┌──────────────────────────────────────────────────────────────────┐
│  Gradle 层                                                        │
│   libs.versions.toml                                              │
│     - spring-boot: 4.0.1 → 4.0.6                                  │
│     - spring-cloud: 2025.1.0 → 2025.1.1                           │
│     + spring-cloud-alibaba: 2025.1.0.0  (新增 BOM)                │
│     - spring-cloud-starter-consul-discovery (删除)                │
│     + spring-cloud-starter-alibaba-nacos-discovery (新增)         │
│   LinqibinDependencyManagement.kt: 注入 SCA BOM (SC BOM 之后)     │
│   linqibin.hexagonal-boot.gradle.kts:72 → 切到 nacos starter      │
├──────────────────────────────────────────────────────────────────┤
│  应用配置层（5 个 boot 模块）                                       │
│   spring.cloud.consul.* → spring.cloud.nacos.*                    │
│   ├─ application.yml ×5（registry/ingest/catalog/gateway/storage）│
│   ├─ application-dev.yml ×3（registry/ingest/catalog）            │
│   ├─ test yml ×2（ingest test-common + catalog e2e）              │
│   └─ E2E Java ×2（OutboxPatternE2E + MeshScrImportE2E）           │
├──────────────────────────────────────────────────────────────────┤
│  基础设施层                                                        │
│   docker-compose.core.yaml: 删 consul 服务 / 加 nacos             │
│   init-volumes.sh:29       : consul/data → nacos/data + logs      │
│   infra/docker/README.md   : 结构图/栈描述同步                     │
│   .env / .env.example      : 新增 NACOS_AUTH_* 三件套              │
└──────────────────────────────────────────────────────────────────┘
```

## 5. 关键组件设计

### 5.1 Nacos compose 服务定义

替换 `infra/docker/docker-compose.core.yaml` 第 44–60 行的 consul 块：

```yaml
nacos:
  image: nacos/nacos-server:v3.0.2          # 3.x 稳定线，与 nacos-client 3.1.1 兼容
  container_name: patra-nacos
  restart: unless-stopped
  environment:
    - MODE=standalone
    - PREFER_HOST_MODE=hostname
    - NACOS_AUTH_ENABLE=true
    - NACOS_AUTH_TOKEN=${NACOS_AUTH_TOKEN}
    - NACOS_AUTH_IDENTITY_KEY=${NACOS_AUTH_IDENTITY_KEY}
    - NACOS_AUTH_IDENTITY_VALUE=${NACOS_AUTH_IDENTITY_VALUE}
    - JVM_XMS=256m
    - JVM_XMX=256m
    - TZ=Asia/Shanghai
  ports:
    - "8848:8848"   # HTTP / OpenAPI / 老控制台
    - "9848:9848"   # gRPC（2.x+ 客户端必需，缺则只能 HTTP 握手不能心跳）
    - "8080:8080"   # 新版控制台
  volumes:
    - ${HOME}/.patra/docker/nacos/data:/home/nacos/data
    - ${HOME}/.patra/docker/nacos/logs:/home/nacos/logs
  healthcheck:
    test: ["CMD-SHELL", "curl -fs http://localhost:8848/nacos/v1/console/health/readiness || exit 1"]
    interval: 10s
    timeout: 5s
    retries: 10
```

**端口语义**：
- 8848：HTTP OpenAPI 与老控制台（客户端首次注册握手走这里）
- 9848：gRPC（客户端心跳与变更推送走这里，**不能省**）
- 8080：3.x 新版控制台 UI

**鉴权变量**：
- `NACOS_AUTH_TOKEN`：必须 Base64 编码、解码后 ≥ 32 字节
- `NACOS_AUTH_IDENTITY_KEY/VALUE`：服务端节点间识别用，dev 单机也必须设
- 三个值由 `.env` 注入，`.env.example` 写占位 + `openssl rand -base64 32` / `openssl rand -base64 24` 生成命令

### 5.2 应用配置改造模板

#### 模板 A — 生产 `application.yml`（5 个）

```yaml
spring:
  cloud:
    nacos:
      username: ${NACOS_USERNAME:nacos}
      password: ${NACOS_PASSWORD:nacos}
      discovery:
        server-addr: ${NACOS_HOST:${PATRA_INFRA_HOST:localhost}}:${NACOS_PORT:8848}
        service: ${spring.application.name}
        fail-fast: true
        # gateway / object-storage 追加以下块：
        metadata:
          scheme: http
```

**为什么这样**：
- `fail-fast: true` — 启动期注册失败立即抛错，避免后续 `lb://` 调用迷之 503
- `username/password` 默认 `nacos/nacos`（Nacos 首启默认账号），由 env 覆盖；生产必须改密
- `metadata.scheme` — gateway/object-storage 原 `scheme: http` 在 Nacos 模型下需挂到 metadata，供 LoadBalancer 决定下游 URL scheme

#### 模板 B — `application-dev.yml`（3 个：registry/ingest/catalog）

```yaml
spring:
  cloud:
    nacos:
      discovery:
        ip: ${TAILSCALE_IP:}
```

从原 Consul 7 行块降到 4 行，保留 tailscale 双网络（家内 LAN / 离家加密隧道）下显式注册 IP 的能力。

#### 模板 C — 测试 yml（2 个）

```diff
- spring.cloud.consul.enabled: false
+ spring.cloud.nacos.discovery.enabled: false

- org.springframework.cloud.consul: WARN
+ com.alibaba.nacos.client: WARN
```

#### 模板 D — E2E Java 字符串属性（2 处）

```diff
- "spring.cloud.consul.enabled=false",
+ "spring.cloud.nacos.discovery.enabled=false",
```

### 5.3 Gradle 变更

#### `gradle/libs.versions.toml`

```diff
 [versions]
-spring-boot = "4.0.1"
-spring-cloud = "2025.1.0"
+spring-boot = "4.0.6"
+spring-cloud = "2025.1.1"
+spring-cloud-alibaba = "2025.1.0.0"

 [libraries]
 # BOMs
 spring-boot-bom = { module = "org.springframework.boot:spring-boot-dependencies", version.ref = "spring-boot" }
 spring-cloud-bom = { module = "org.springframework.cloud:spring-cloud-dependencies", version.ref = "spring-cloud" }
+spring-cloud-alibaba-bom = { module = "com.alibaba.cloud:spring-cloud-alibaba-dependencies", version.ref = "spring-cloud-alibaba" }

 # Spring Cloud
-spring-cloud-starter-consul-discovery = { module = "org.springframework.cloud:spring-cloud-starter-consul-discovery" }
+spring-cloud-starter-alibaba-nacos-discovery = { module = "com.alibaba.cloud:spring-cloud-starter-alibaba-nacos-discovery" }
```

#### `LinqibinDependencyManagement.kt`

文件顶部追加版本变量：

```kotlin
val springCloudAlibabaVersion = libs.findVersion("spring-cloud-alibaba").get().requiredVersion
```

在 `imports { ... }` 块的 spring-cloud BOM **之后**追加：

```kotlin
mavenBom("com.alibaba.cloud:spring-cloud-alibaba-dependencies:$springCloudAlibabaVersion")
```

**顺序重要**：`io.spring.dependency-management` 插件的规则是**先 import 的 BOM 优先**。spring-cloud 在前可确保其声明的工件版本不被 SCA 内部版本声明覆盖。

#### `linqibin.hexagonal-boot.gradle.kts:72`

```diff
- implementation(libs.findLibrary("spring-cloud-starter-consul-discovery").get())
+ implementation(libs.findLibrary("spring-cloud-starter-alibaba-nacos-discovery").get())
```

## 6. Consul → Nacos 配置字段映射表

| Consul 字段 | Nacos 等价 | 处理 |
|---|---|---|
| `consul.host` + `consul.port` | `discovery.server-addr` | 合并为 `${NACOS_HOST:${PATRA_INFRA_HOST:localhost}}:${NACOS_PORT:8848}` |
| `discovery.service-name` | `discovery.service` | 改名 |
| `discovery.health-check-interval` | Nacos 临时实例默认心跳 | **删除** |
| `discovery.health-check-path` | Nacos 不基于 HTTP 探针 | **删除** |
| `discovery.register-health-check` | Nacos 无对等概念 | **删除** |
| `discovery.heartbeat.enabled` | Nacos 默认启用 | **删除** |
| `discovery.ip-address` | `discovery.ip` | 改名（dev profile 用，保留 TAILSCALE_IP 注入） |
| `discovery.prefer-ip-address` | Nacos 默认按 IP 注册 | **删除** |
| `discovery.scheme: http` | `discovery.metadata.scheme: http` | 转 metadata（gateway 用） |
| `discovery.instance-id` | Nacos 同 ip:port 自动覆盖旧实例 | **删除** |
| `consul.enabled: false`（测试） | `nacos.discovery.enabled: false` | 改键 |

## 7. 错误处理与兼容性

| 风险 | 处理 |
|---|---|
| 9848 gRPC 端口被防火墙/路由器挡 | compose 显式 expose；README 在"首次部署"段写明 dev 必须开 8848 + 9848 |
| `NACOS_AUTH_TOKEN` 解码长度不足 32 字节导致 nacos-server 启动失败 | `.env.example` 给 `openssl rand -base64 32` 生成命令，并在注释里写明长度约束 |
| 首启 token 配错，5 个微服务集体 401 | `fail-fast: true` + 启动日志立即看到 4xx 异常；不会静默降级 |
| Spring Cloud BOM 与 SCA BOM 版本冲突 | 强制 import 顺序：spring-cloud 在前（先 import 的 BOM 优先）、SCA 在后；Gradle 切换 commit 后跑 `./gradlew dependencyInsight --dependency spring-cloud-context` 验证 spring-cloud 实际解析版本为 2025.1.1 |
| `lb://` 路由 scheme 解析 | gateway 的 `metadata.scheme: http` 决定下游 URL scheme；验证 `curl http://localhost:9528/patra-ingest/actuator/health` 仍 200 |
| `object-storage` 不在 issue 验收的 "4 个微服务" 列表 | 但其 application.yml 用 consul，必须同步切换；纳入设计范围（issue 自身验收清单已列其 yml） |
| 测试时 nacos 容器未起 | `nacos.discovery.enabled=false` 完全关闭客户端，Testcontainers 不需要关心 nacos |
| 旧 Consul 数据卷残留 | README 提示用户手工 `rm -rf ${HOME}/.patra/docker/consul/`；脚本不主动删（避免误删用户其他数据） |
| 文档/skill 中残留 `${CONSUL_HOST}` 示例 | §3 文档清扫子项统一处理 |

## 8. 测试与验证策略

按 4 个 commit 渐进验证：

### Step 1 — Gradle 切换后
- `./gradlew clean build -x test` 通过
- `./gradlew dependencies | grep -i nacos` 应见 `spring-cloud-starter-alibaba-nacos-discovery` 与 `nacos-client:3.1.1`
- `./gradlew dependencies | grep -i consul` 必须为空
- `./gradlew dependencyInsight --dependency spring-cloud-context` 验证 spring-cloud 版本仍为 2025.1.1

### Step 2 — 应用配置切换后
- `./gradlew test`（单测）全绿 — 测试不依赖 Nacos，由 `nacos.discovery.enabled=false` 保证
- 各 boot 模块 `bootJar` 任务能成功打包

### Step 3 — compose 切换后
- `docker compose -f infra/docker/docker-compose.core.yaml up -d nacos` 单独起 nacos
- `curl -fs http://localhost:8848/nacos/v1/console/health/readiness` 返回 OK
- 浏览器 `http://localhost:8080` 新控制台可登录（默认 nacos/nacos）

### Step 4 — 联调验证
- 起 4 个微服务（registry/ingest/catalog/gateway）+ object-storage
- Nacos 控制台 → 服务管理 → 应见 5 个实例均 healthy
- `curl http://localhost:9528/patra-registry/actuator/health` 200
- `curl http://localhost:9528/patra-ingest/actuator/health` 200
- `curl http://localhost:9528/patra-catalog/actuator/health` 200
- `./gradlew test` + 各 boot 的 E2E IT 全量通过

### 回滚策略

每步都是单个 commit，`git revert <hash>` 即可回滚到 Consul 形态。整 4 步合并 PR 后再 squash 不影响回滚粒度（参照 issue #28 风格）。

## 9. 验收对照（issue #29）

| issue 验收项 | 设计覆盖位置 |
|---|---|
| `gradle/libs.versions.toml` 三个版本号升级与 BOM 新增 | §5.3 |
| 删除 consul starter 声明、新增 nacos starter | §5.3 |
| `linqibin.hexagonal-boot.gradle.kts:72` 切换 | §5.3 |
| 清理 `build-logic/bin/` | §3 In scope |
| 5 个 boot 模块 application.yml 切换 | §5.2 模板 A + §3 表格 |
| 测试配置 logging 包名切换 | §5.2 模板 C |
| docker-compose.core.yaml consul → nacos | §5.1 |
| docker-compose.dev.yaml 注释/依赖同步 | §3 In scope |
| infra 启动脚本与 README 同步 | §3 In scope |
| bootstrap 配置迁移（SCA 重大变更） | §1 前提（本项目无 bootstrap，影响为零） |
| `./gradlew build` 通过 | §8 Step 1 |
| 本地启 Nacos + 4 个微服务相互发现 | §8 Step 3–4 |
| `./gradlew test` 全量通过 | §8 Step 2 / Step 4 |
| e2e 测试通过 | §8 Step 4 |
