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
| **C1. 砍掉等价默认项，仅保留差异化字段（采用）** | `health-check-*` / `register-health-check` / `heartbeat` / `prefer-ip-address` / `instance-id` / `scheme` 全删；只保留 `server-addr`、`service`、`fail-fast`、dev 的 `ip`。**采用** — 配置噪音最小、可读性最强。（注：原计划给 gateway/object-storage 保留 `metadata.scheme: http`，但 Task 2 code-review 通过字节码分析确认 `NacosServiceInstance.getScheme()` 只读 `isSecure()` 不读 metadata，Nacos 默认 `secure=false` 即返回 `http`，故无需配置。） |
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

> **强约束**：改造完成后，除 `docs/superpowers/specs/**` 与 `docs/superpowers/plans/**` 外，项目内**不得遗留任何 consul / Consul / CONSUL_HOST / CONSUL_PORT 字样**（包括 yml/Java 注释、JavaDoc、README 段落、Skill 文档）。验收用 §11 的 grep 断言客观执行。

**Gradle 层**：

- `gradle/libs.versions.toml`：升级 spring-boot/spring-cloud；新增 spring-cloud-alibaba；删除 consul starter；新增 nacos starter
- `build-logic/src/main/kotlin/LinqibinDependencyManagement.kt`：引入 SCA BOM（必须在 spring-cloud BOM **之后** import — `spring-dependency-management` 的规则是先 import 的 BOM 优先，把 SCA 放后面可以保证 spring-cloud 主版本不被 SCA 内部声明覆盖）
- `build-logic/src/main/kotlin/linqibin.hexagonal-boot.gradle.kts:72`：切换 starter 引用
- `build-logic/bin/` 旧 IDE/Gradle 编译产物：清理（顺手检查 `.gitignore`）

**应用配置层**（5 个 boot 模块，10 个 yml 文件）：

| 模块 | 文件 | 改造 |
|---|---|---|
| patra-registry-boot | `src/main/resources/application.yml` | 模板 A + 同步清理"Consul Service Discovery"等中文/英文注释 |
| patra-registry-boot | `src/main/resources/application-dev.yml` | 模板 B + 清理 "Consul 跑在 Mac mini docker" 类注释 |
| patra-ingest-boot | `src/main/resources/application.yml` | 模板 A + 注释清理 |
| patra-ingest-boot | `src/main/resources/application-dev.yml` | 模板 B + 注释清理 |
| patra-ingest-boot | `src/test/resources/application-test-common.yml` | 模板 C（含 logging 包名） |
| patra-catalog-boot | `src/main/resources/application.yml` | 模板 A + 注释清理 |
| patra-catalog-boot | `src/main/resources/application-dev.yml` | 模板 B + 注释清理 |
| patra-catalog-boot | `src/test/resources/application-e2e-test.yml` | 模板 C |
| patra-gateway-boot | `src/main/resources/application.yml` | 模板 A + 注释清理（含顶部 banner "Consul-based service discovery"） |
| patra-object-storage-boot | `src/main/resources/application.yml` | 模板 A + 注释清理 |

**E2E / IT Java 测试**（5 个文件、`spring.cloud.consul.enabled=false` 字符串属性切换 — 模板 D）：

| 模块 | 文件 |
|---|---|
| patra-catalog-boot | `src/test/java/dev/linqibin/patra/catalog/integration/mesh/MeshScrImportE2E.java:71` |
| patra-catalog-boot | `src/test/java/dev/linqibin/patra/catalog/integration/mesh/MeshDescriptorImportE2E.java:69` |
| patra-ingest-boot | `src/test/java/dev/linqibin/patra/ingest/integration/outbox/OutboxPatternE2E.java:112` |
| patra-ingest-boot | `src/test/java/dev/linqibin/patra/ingest/integration/messaging/RocketMqOutboxPublisherIT.java:76` |
| patra-ingest-boot | `src/test/java/dev/linqibin/patra/ingest/integration/messaging/TaskReadyMessageListenerIT.java:72` |

**Java 源码 JavaDoc**：

- `patra-gateway-boot/src/main/java/dev/linqibin/patra/gateway/PatraGatewayApplication.java:9, 14`：JavaDoc 中"通过 Consul 服务发现" → "通过 Nacos 服务发现"

**基础设施层**：

- `infra/docker/docker-compose.core.yaml`：删 consul 服务块（44–60 行）、加 nacos 服务块
- `infra/docker/docker-compose.dev.yaml:9`：`include` 注释 `# PostgreSQL, Redis, Consul` → `# PostgreSQL, Redis, Nacos`
- `infra/docker/.env`：删 `CONSUL_PORT=8500` / `CONSUL_HOST=...`，新增 `NACOS_AUTH_TOKEN` / `NACOS_AUTH_IDENTITY_KEY` / `NACOS_AUTH_IDENTITY_VALUE` / `NACOS_PORT=8848`
- `infra/docker/.env.dev:9-10`：删 Consul 区块、加 Nacos 区块
- `infra/docker/.env.example`：同步占位 + `openssl rand -base64 32` / `openssl rand -base64 24` 生成命令注释
- `infra/scripts/init-volumes.sh:29`：`consul/data` → `nacos/data` + `nacos/logs`
- `infra/docker/README.md`：4 处涉及（行 14 结构图、行 131 示例 yml、行 151 Consul UI、行 283 栈描述图）

**项目文档**（README）：

| 文件 | 位置 |
|---|---|
| `README.md`（根） | 行 102、145 |
| `patra-registry/README.md` | 行 141 |
| `patra-catalog/README.md` | 行 137 |
| `patra-gateway-boot/README.md` | 行 12、24、76–84、153–158、205（多处，需重写 "Consul 集成" 整节为 "Nacos 集成"） |
| `patra-object-storage/README.md` | 行 256–258、283、295、308–309、324、347（含 env 命令、依赖表、部署步骤） |

**Claude / Codex 上下文（Skill + Rule，必须同步，否则后续 AI 生成代码会回吐 consul 配置）**：

| 文件 | 位置 |
|---|---|
| `.claude/rules/project-info.md` | 行 8（技术栈列表："Spring Boot 4.0.1 \| ... \| Consul" → "Spring Boot 4.0.6 \| ... \| Nacos"） |
| `.claude/skills/patra-hexagonal/resources/configuration.md` | 行 21、28–30、38（"Consul 服务发现配置"整节重写） |
| `.claude/skills/patra-hexagonal/resources/patra-starters.md` | 行 32（http-interface 描述中的 "Consul 服务发现" → "Nacos 服务发现"） |
| `.claude/skills/patra-troubleshooter/SKILL.md` | 行 7（trigger 描述中的 "Consul 服务发现问题" → "Nacos 服务发现问题"） |
| `.agents/skills/patra-hexagonal/resources/configuration.md` | 行 21、28–30、38（同 .claude 版本） |
| `.agents/skills/patra-hexagonal/resources/patra-starters.md` | 行 32 |
| `plugins/patra-codex/skills/patra-hexagonal/references/configuration.md` | 行 21、28–30、38 |
| `plugins/patra-codex/skills/patra-hexagonal/references/patra-starters.md` | 行 32 |

**已确认无需改动**（grep 命中但属于误报）：

- `scripts/letpub/venues_issn.tsv`：医学期刊名（"Consultant"、"Consulting"、"Consultation"等）—— 这是数据而非配置，不在改造范围；§11 的 grep 断言需要排除该文件

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
│   ├─ E2E/IT Java ×5（catalog ×2、ingest ×3 — 见 §3 表格）         │
│   └─ JavaDoc ×1（PatraGatewayApplication）                        │
├──────────────────────────────────────────────────────────────────┤
│  基础设施层                                                        │
│   docker-compose.core.yaml: 删 consul 服务 / 加 nacos             │
│   docker-compose.dev.yaml:9 注释同步                              │
│   init-volumes.sh:29       : consul/data → nacos/data + logs      │
│   .env / .env.dev / .env.example: NACOS_AUTH_* 三件套 + NACOS_PORT │
├──────────────────────────────────────────────────────────────────┤
│  文档与 AI 上下文层（零残留）                                       │
│   README ×6（根、infra/docker、registry、catalog、gateway、storage）│
│   .claude/rules/project-info.md（项目背景规则）                    │
│   Skill 文档 ×7（.claude / .agents / plugins 三套）                │
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
    test: ["CMD-SHELL", "curl -fs http://localhost:8080/v3/console/health/readiness || exit 1"]
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
        server-addr: ${NACOS_HOST:127.0.0.1}:${NACOS_PORT:8848}
        service: ${spring.application.name}
        fail-fast: false
```

**为什么这样**：
- `fail-fast: false` — **必须显式 false**。SCA 2025.1.0.0 NacosRegistration 默认 failFast=true，但 nacos-client 3.1.1 的 gRPC channel 异步建立（HTTP login + 拿 token + gRPC connect + server check 约 3 秒），而 Spring `WebServerInitializedEvent` 触发的同步 register 立即调用 NamingService.registerInstance，此时 gRPC channel 常仍在 STARTING，SCA 仅 4 × 100ms 内部重试就放弃，failFast=true 让异常向上抛出导致整个应用启动失败（错误码 -401 Client not connected, status:STARTING，**不是鉴权失败**）。改为 false 后 nacos-client 后台异步重试，应用启动正常，~3 秒内完成注册。联调实测验证。
- `username/password` 默认 `nacos/nacos`（Nacos 3.x 首次启动需手动 `POST /nacos/v3/auth/user/admin -d password=xxx` 初始化 admin，详见 §1 部署说明），由 env 覆盖；生产必须改密
- 不需要配 `scheme: http` —— Nacos `NacosServiceInstance.getScheme()` 通过 `isSecure()` 决定，默认 `secure=false` 即返回 `http`；与 Consul 模型不同，metadata 不参与 scheme 决策（Task 2 code-review 字节码验证）

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

#### 模板 D — E2E/IT Java 字符串属性（5 处）

5 个测试文件的 `@TestPropertySource` / `@SpringBootTest(properties=...)` 中同款字符串替换：

```diff
- "spring.cloud.consul.enabled=false",
+ "spring.cloud.nacos.discovery.enabled=false",
```

涉及文件清单见 §3 "E2E / IT Java 测试" 表格。

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
| `consul.host` + `consul.port` | `discovery.server-addr` | 合并为 `${NACOS_HOST:127.0.0.1}:${NACOS_PORT:8848}` |
| `discovery.service-name` | `discovery.service` | 改名 |
| `discovery.health-check-interval` | Nacos 临时实例默认心跳 | **删除** |
| `discovery.health-check-path` | Nacos 不基于 HTTP 探针 | **删除** |
| `discovery.register-health-check` | Nacos 无对等概念 | **删除** |
| `discovery.heartbeat.enabled` | Nacos 默认启用 | **删除** |
| `discovery.ip-address` | `discovery.ip` | 改名（dev profile 用，保留 TAILSCALE_IP 注入） |
| `discovery.prefer-ip-address` | Nacos 默认按 IP 注册 | **删除** |
| `discovery.scheme: http` | Nacos 默认 `secure=false` 即返回 `http`（`NacosServiceInstance.getScheme()` 只读 `isSecure()`） | **删除** |
| `discovery.instance-id` | Nacos 同 ip:port 自动覆盖旧实例 | **删除** |
| `consul.enabled: false`（测试） | `nacos.discovery.enabled: false` | 改键 |

## 7. 错误处理与兼容性

| 风险 | 处理 |
|---|---|
| 9848 gRPC 端口被防火墙/路由器挡 | compose 显式 expose；README 在"首次部署"段写明 dev 必须开 8848 + 9848 |
| `NACOS_AUTH_TOKEN` 解码长度不足 32 字节导致 nacos-server 启动失败 | `.env.example` 给 `openssl rand -base64 32` 生成命令，并在注释里写明长度约束 |
| 首启 token 配错，5 个微服务集体 401 | nacos-client 后台异步重试，启动日志可见 `nacos register failed` WARN；运维通过 Nacos 控制台 / `/v3/admin/ns/service/list` 验证服务列表是否齐全 |
| Nacos 3.x 首次部署 admin 用户不存在 | 部署文档要求执行 `curl -X POST http://NACOS_HOST:8848/nacos/v3/auth/user/admin -d password=nacos` 初始化 admin（首次调用后该 API 失效，密码可设为强密码） |
| OrbStack 跨 tailscale 端口转发对 gRPC HTTP/2 SETTINGS frame 转发不完整，导致客户端 gRPC handshake 超时 | dev 在 MacBook 跨网络访问 macmini Nacos 时用 `ssh -L 8848 -L 9848 -L 8080 linqibin@linqibins-mac-mini -N` 建立 ssh tunnel，应用 `NACOS_HOST=127.0.0.1`；生产部署应避免跨 OrbStack 用户态网络的 gRPC 流量 |
| Spring Cloud BOM 与 SCA BOM 版本冲突 | 强制 import 顺序：spring-cloud 在前（先 import 的 BOM 优先）、SCA 在后；Gradle 切换 commit 后跑 `./gradlew dependencyInsight --dependency spring-cloud-context` 验证 spring-cloud 实际解析版本为 2025.1.1 |
| `lb://` 路由 scheme 解析 | Nacos `NacosServiceInstance.getScheme()` 通过 `isSecure()` 决定，默认即 `http`；验证 `curl http://localhost:9528/patra-ingest/actuator/health` 仍 200 |
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
- `curl -fs http://localhost:8080/v3/console/health/readiness` 返回 `{"code":0,"message":"success","data":"ok"}`（Nacos 3.x 健康端点在 8080 新控制台，v1 路径已 deprecated）
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
| **用户追加：除 spec/plan 外项目内零 consul 残留** | §3 强约束 + §11 自动化断言 |

## 10. 提交粒度（4 个原子 commit）

| # | 主题 | 范围 | 验证命令 |
|---|---|---|---|
| 1 | `build(deps): SpringBoot 4.0.6 / SpringCloud 2025.1.1 升级 + 引入 SCA + 切换 nacos starter` | `libs.versions.toml`、`LinqibinDependencyManagement.kt`、`linqibin.hexagonal-boot.gradle.kts:72`、`build-logic/bin/` 清理 | §8 Step 1 |
| 2 | `refactor(boot): 5 个 boot 模块由 Consul 切换到 Nacos（注释清扫）` | 10 个 yml + 5 个测试 Java + 1 个 JavaDoc 注释 | §8 Step 2 |
| 3 | `chore(infra): docker compose 由 Consul 切换到 Nacos` | `compose.core/dev.yaml`、3 个 `.env*`、`init-volumes.sh` | §8 Step 3 |
| 4 | `docs: 清扫全部模块 README 与 Skill 文档中的 Consul 字样` | 6 个 README + 7 个 Skill 文档 + §11 零残留断言通过 | §8 Step 4 + §11 |

## 11. 完工断言（零残留 grep）

实施完成的客观验收命令。**任意一行命中即视为未完成**（venues_issn.tsv 是数据文件，必须排除）：

```bash
# 在仓库根目录执行
grep -rni -E "consul|CONSUL_HOST|CONSUL_PORT" . \
  --exclude-dir=.git \
  --exclude-dir=.gradle \
  --exclude-dir=build \
  --exclude-dir=bin \
  --exclude-dir=node_modules \
  --exclude-dir=.idea \
  --exclude-dir=.claude/worktrees \
  --exclude='*.tsv' \
  | grep -v 'docs/superpowers/specs/' \
  | grep -v 'docs/superpowers/plans/'
```

预期输出：**空**。

排除项说明：
- `*.tsv` — `scripts/letpub/venues_issn.tsv` 含 "Consultant" / "Consulting" 等期刊名（医学数据），不在范围
- `docs/superpowers/specs/` 与 `docs/superpowers/plans/` — 本 spec 与对应 plan 必然包含 Consul → Nacos 的对照说明
- `.claude/worktrees/` — 其它并行 worktree 与本任务无关
- `.git` / `.gradle` / `build` / `bin` / `node_modules` / `.idea` — 构建产物与 IDE 缓存

如果断言失败，必须把命中行加进 §3 In scope 并继续清理，直到为空。该断言纳入 commit 4 的强制前置检查。
