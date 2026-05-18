# Issue #29 Spring Boot 4.0.6 / Spring Cloud 2025.1.1 升级 + Consul → Nacos 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 patra-api 全部 5 个 boot 模块的注册中心从 Consul 切换为 Nacos，同步升级 Spring Boot 4.0.1 → 4.0.6 / Spring Cloud 2025.1.0 → 2025.1.1 / 新增 spring-cloud-alibaba 2025.1.0.0；改造完成后除 spec/plan 外项目内零 Consul 残留。

**Architecture:** 绿地项目，无任何向后兼容包袱。Consul 相关配置全部直接删除（不保留 fallback），Nacos 配置只保留 Nacos 模型下有意义的字段。Nacos 鉴权 dev/prod 一致开启（与生产形态一致）；数据存储用内嵌 Derby（与 Consul 单机用法对等）；服务发现配置通过环境变量 `NACOS_HOST/NACOS_PORT/NACOS_AUTH_*` 注入。

**Tech Stack:** Java 25 · Spring Boot 4.0.6 · Spring Cloud 2025.1.1 · spring-cloud-alibaba 2025.1.0.0 · nacos-client 3.1.1 · nacos-server v3.0.2 · Gradle 9.2.1 (Kotlin DSL Convention Plugins)

**Spec:** `docs/superpowers/specs/2026-05-18-issue-29-spring-cloud-nacos-upgrade-design.md`

---

## 前置知识（必读）

执行任何 Task 前，浏览以下文件以理解项目约定：

| 文件 | 关注点 |
|---|---|
| `gradle/libs.versions.toml` | Version Catalog 单一来源；BOM 在 `[libraries]` 块声明，`version.ref` 引用 `[versions]` |
| `build-logic/src/main/kotlin/LinqibinDependencyManagement.kt` | BOM 注入实现；`imports { mavenBom(...) }` 顺序很重要（先 import 的 BOM 优先） |
| `build-logic/src/main/kotlin/linqibin.hexagonal-boot.gradle.kts` | 所有 boot 模块共用的 convention plugin；服务发现 starter 在这里统一引入 |
| `infra/docker/docker-compose.core.yaml` | core 栈：postgres + redis + (consul → nacos) |
| `docs/superpowers/specs/2026-05-18-issue-29-spring-cloud-nacos-upgrade-design.md` §6 | Consul → Nacos 字段映射决策表 |

**统一约定**：

- 绿地项目，禁止保留任何 Consul fallback / 兼容代码 / 注释
- Nacos 字段以 spec §6 表为准；Consul 特有但 Nacos 默认行为已覆盖的字段全部**直接删除**
- 测试 yml 中 `nacos.discovery.enabled=false` 完全关闭客户端（避免测试需要连 Nacos）
- gateway 与 object-storage 与其它服务用相同 Nacos 模板（无需 `metadata.scheme` — Nacos `NacosServiceInstance` 默认 `secure=false` 即返回 `scheme=http`，scheme 决策不读 metadata；Task 2 code-review 字节码验证）

---

## 文件影响清单

### 创建（无新建文件）

本次改造为依赖与配置切换，不引入新源码文件。

### 修改

**Gradle 层（4 个）**：

| 文件 | 责任 |
|---|---|
| `gradle/libs.versions.toml` | 版本号 + 依赖声明 |
| `build-logic/src/main/kotlin/LinqibinDependencyManagement.kt` | 新增 SCA BOM 注入 |
| `build-logic/src/main/kotlin/linqibin.hexagonal-boot.gradle.kts` | 切换服务发现 starter 引用 |
| `build-logic/bin/` | 清理旧编译产物（如残留） |

**应用配置层（10 个 yml + 5 个 Java 测试 + 1 个 Java 主代码）**：

| 文件 | 模板 |
|---|---|
| `patra-registry/patra-registry-boot/src/main/resources/application.yml` | 模板 A |
| `patra-registry/patra-registry-boot/src/main/resources/application-dev.yml` | 模板 B |
| `patra-ingest/patra-ingest-boot/src/main/resources/application.yml` | 模板 A |
| `patra-ingest/patra-ingest-boot/src/main/resources/application-dev.yml` | 模板 B |
| `patra-ingest/patra-ingest-boot/src/test/resources/application-test-common.yml` | 模板 C |
| `patra-catalog/patra-catalog-boot/src/main/resources/application.yml` | 模板 A |
| `patra-catalog/patra-catalog-boot/src/main/resources/application-dev.yml` | 模板 B |
| `patra-catalog/patra-catalog-boot/src/test/resources/application-e2e-test.yml` | 模板 C |
| `patra-gateway-boot/src/main/resources/application.yml` | 模板 A |
| `patra-object-storage/patra-object-storage-boot/src/main/resources/application.yml` | 模板 A |
| `patra-catalog-boot/.../MeshScrImportE2E.java:71` | 模板 D |
| `patra-catalog-boot/.../MeshDescriptorImportE2E.java:69` | 模板 D |
| `patra-ingest-boot/.../OutboxPatternE2E.java:112` | 模板 D |
| `patra-ingest-boot/.../RocketMqOutboxPublisherIT.java:76` | 模板 D |
| `patra-ingest-boot/.../TaskReadyMessageListenerIT.java:72` | 模板 D |
| `patra-gateway-boot/.../PatraGatewayApplication.java` | JavaDoc 注释切换 |

**基础设施层（6 个）**：

| 文件 | 改造 |
|---|---|
| `infra/docker/docker-compose.core.yaml` | 删 consul 服务、加 nacos 服务 |
| `infra/docker/docker-compose.dev.yaml` | include 注释同步 |
| `infra/docker/.env` | 新增 NACOS_AUTH_* 与 NACOS_PORT |
| `infra/docker/.env.dev` | Consul 区块 → Nacos 区块 |
| `infra/docker/.env.example` | NACOS_AUTH_* 占位与生成命令注释 |
| `infra/scripts/init-volumes.sh` | `consul/data` → `nacos/data` + `nacos/logs` |

**文档与 AI 上下文层（14 个）**：

| 文件 | 说明 |
|---|---|
| `README.md`（根） | 行 102、145 |
| `infra/docker/README.md` | 行 14、131、151、283 |
| `patra-registry/README.md` | 行 141 |
| `patra-catalog/README.md` | 行 137 |
| `patra-gateway-boot/README.md` | 多处，需重写 "Consul 集成" 整节 |
| `patra-object-storage/README.md` | 多处，含 env 命令、依赖表、部署步骤 |
| `.claude/rules/project-info.md` | 行 8（技术栈：Spring Boot 版本号 + Consul → Nacos） |
| `.claude/skills/patra-hexagonal/resources/configuration.md` | "Consul 服务发现配置" 整节 |
| `.claude/skills/patra-hexagonal/resources/patra-starters.md` | 行 32 |
| `.claude/skills/patra-troubleshooter/SKILL.md` | 行 7 |
| `.agents/skills/patra-hexagonal/resources/configuration.md` | 同 .claude 版本 |
| `.agents/skills/patra-hexagonal/resources/patra-starters.md` | 行 32 |
| `plugins/patra-codex/skills/patra-hexagonal/references/configuration.md` | 同上 |
| `plugins/patra-codex/skills/patra-hexagonal/references/patra-starters.md` | 行 32 |

---

## 配置模板（多 Task 复用）

### 模板 A — 生产 application.yml 改造

**Consul 块（删除）**：

```yaml
# Consul Service Discovery
cloud:
  consul:
    host: ${CONSUL_HOST:${PATRA_INFRA_HOST:localhost}}   # gateway/object-storage 是 ${CONSUL_HOST:localhost}
    port: ${CONSUL_PORT:8500}
    discovery:
      service-name: ${spring.application.name}
      health-check-interval: 10s
      health-check-path: /actuator/health
      # 使用固定 instance-id，服务重启时覆盖旧注册，避免僵尸实例
      instance-id: ${spring.application.name}:${server.port}     # 仅 gateway/object-storage 有
      # 使用宿主机实际 IP 注册服务（Consul 容器和宿主机服务都能访问）
      prefer-ip-address: true                                     # 仅 gateway/object-storage 有
      # 指定服务注册的 scheme...
      scheme: http                                                # 仅 gateway/object-storage 有
```

**Nacos 块（替换写入）** — `registry / ingest / catalog`：

```yaml
# Nacos Service Discovery
cloud:
  nacos:
    username: ${NACOS_USERNAME:nacos}
    password: ${NACOS_PASSWORD:nacos}
    discovery:
      server-addr: ${NACOS_HOST:127.0.0.1}:${NACOS_PORT:8848}
      service: ${spring.application.name}
      # 显式 false：SCA 2025.1.0.0 默认 true 会让 nacos 3.x gRPC 异步握手未完成时
      # 同步 register 的 -401 Client-not-connected 直接 abort 整个应用启动
      fail-fast: false
```

注意：gateway/object-storage 与 registry/ingest/catalog 共用同一模板，无需 `metadata.scheme`（Nacos 默认 `secure=false` 即返回 `scheme=http`，scheme 决策不读 metadata；Task 2 code-review 字节码验证）。`server-addr` 默认 `127.0.0.1:8848` 而非 `PATRA_INFRA_HOST`：dev 通过 launchd ssh tunnel 转发到 mac mini Nacos（绕过 tailscale wireguard MTU 1280 对 gRPC HTTP/2 SETTINGS frame 的限制），prod 通过 `NACOS_HOST=<prod-nacos>` env 显式覆盖。

### 模板 B — application-dev.yml 改造

**Consul 块（删除整块，包含 3 行注释）**：

```yaml
# dev 场景：应用跑在 MacBook，Consul 跑在 Mac mini docker。
# 容器内 → MacBook tailscale 100.x 不通（Docker bridge 不路由到 utun），
# 所以用 TTL heartbeat 替代反向 HTTP 健康检查；
# 用 TAILSCALE_IP 注册，确保跨主机服务发现拿到的 IP 可达。
cloud:
  consul:
    discovery:
      ip-address: ${TAILSCALE_IP:}
      register-health-check: false
      heartbeat:
        enabled: true
```

注：上述注释中"Consul 跑在 Mac mini docker"和 "TTL heartbeat 替代反向 HTTP 健康检查" 是 Consul 特有语义。Nacos 默认就是心跳模型，注释无需保留。

**Nacos 块（替换写入）**：

```yaml
# dev 场景：用 TAILSCALE_IP 注册，确保跨主机服务发现拿到的 IP 可达。
cloud:
  nacos:
    discovery:
      ip: ${TAILSCALE_IP:}
```

### 模板 C — 测试 yml 改造

```diff
- spring:
-   cloud:
-     consul:
-       enabled: false  # 禁用服务注册（测试环境）
+ spring:
+   cloud:
+     nacos:
+       discovery:
+         enabled: false  # 禁用服务注册（测试环境）
```

logging 段同步：

```diff
- org.springframework.cloud.consul: WARN
+ com.alibaba.nacos.client: WARN
```

### 模板 D — Java 测试字符串属性

```diff
- "spring.cloud.consul.enabled=false",
+ "spring.cloud.nacos.discovery.enabled=false",
```

---

## Task 1: Gradle 依赖切换

**目标**：升级 Spring Boot/Cloud 版本，引入 SCA BOM，切换服务发现 starter；编译通过、依赖树正确。

**Files**:
- Modify: `gradle/libs.versions.toml`
- Modify: `build-logic/src/main/kotlin/LinqibinDependencyManagement.kt`
- Modify: `build-logic/src/main/kotlin/linqibin.hexagonal-boot.gradle.kts:72`
- Clean: `build-logic/bin/` (若存在)

- [ ] **Step 1.1: 修改 `gradle/libs.versions.toml` — 版本号**

Edit `gradle/libs.versions.toml`:

```diff
 [versions]
 # Core
 java = "25"
 patra = "0.1.0-SNAPSHOT"

 # Spring
-spring-boot = "4.0.1"
-spring-cloud = "2025.1.0"
+spring-boot = "4.0.6"
+spring-cloud = "2025.1.1"
+spring-cloud-alibaba = "2025.1.0.0"
 spring-dependency-management = "1.1.7"
```

- [ ] **Step 1.2: 修改 `gradle/libs.versions.toml` — BOM 库声明**

Edit `gradle/libs.versions.toml`（在 `spring-cloud-bom` 之后追加 `spring-cloud-alibaba-bom`）:

```diff
 # BOMs
 spring-boot-bom = { module = "org.springframework.boot:spring-boot-dependencies", version.ref = "spring-boot" }
 spring-cloud-bom = { module = "org.springframework.cloud:spring-cloud-dependencies", version.ref = "spring-cloud" }
+spring-cloud-alibaba-bom = { module = "com.alibaba.cloud:spring-cloud-alibaba-dependencies", version.ref = "spring-cloud-alibaba" }
 resilience4j-bom = { module = "io.github.resilience4j:resilience4j-bom", version.ref = "resilience4j" }
```

- [ ] **Step 1.3: 修改 `gradle/libs.versions.toml` — 服务发现 starter 切换**

Edit `gradle/libs.versions.toml`（删除 consul-discovery、追加 nacos-discovery）:

```diff
 # Spring Cloud
-spring-cloud-starter-consul-discovery = { module = "org.springframework.cloud:spring-cloud-starter-consul-discovery" }
+spring-cloud-starter-alibaba-nacos-discovery = { module = "com.alibaba.cloud:spring-cloud-starter-alibaba-nacos-discovery" }
 spring-cloud-starter-gateway = { module = "org.springframework.cloud:spring-cloud-starter-gateway-server-webflux" }
 spring-cloud-starter-loadbalancer = { module = "org.springframework.cloud:spring-cloud-starter-loadbalancer" }
```

- [ ] **Step 1.4: 修改 `LinqibinDependencyManagement.kt` — 注入 SCA BOM**

Edit `build-logic/src/main/kotlin/LinqibinDependencyManagement.kt`:

读取版本变量（追加到第 44 行 `testcontainersVersion` 之后）：

```diff
     val springBootVersion = libs.findVersion("spring-boot").get().requiredVersion
     val springCloudVersion = libs.findVersion("spring-cloud").get().requiredVersion
+    val springCloudAlibabaVersion = libs.findVersion("spring-cloud-alibaba").get().requiredVersion
     val resilience4jVersion = libs.findVersion("resilience4j").get().requiredVersion
     val testcontainersVersion = libs.findVersion("testcontainers").get().requiredVersion
```

BOM imports（在 `spring-cloud-dependencies` 之后插入 SCA BOM；**顺序重要**，先 import 的 BOM 优先，spring-cloud 必须保持在前）：

```diff
         imports {
             mavenBom("org.springframework.boot:spring-boot-dependencies:$springBootVersion")
             mavenBom("org.springframework.cloud:spring-cloud-dependencies:$springCloudVersion")
+            mavenBom("com.alibaba.cloud:spring-cloud-alibaba-dependencies:$springCloudAlibabaVersion")
             mavenBom("io.github.resilience4j:resilience4j-bom:$resilience4jVersion")
             mavenBom("org.testcontainers:testcontainers-bom:$testcontainersVersion")
         }
```

- [ ] **Step 1.5: 修改 `linqibin.hexagonal-boot.gradle.kts` — 切换 starter 引用**

Edit `build-logic/src/main/kotlin/linqibin.hexagonal-boot.gradle.kts:72`:

```diff
 dependencies {
     // 服务发现
-    implementation(libs.findLibrary("spring-cloud-starter-consul-discovery").get())
+    implementation(libs.findLibrary("spring-cloud-starter-alibaba-nacos-discovery").get())

     // 测试依赖
     testImplementation(project(":linqibin-spring-boot-starter-test"))
     testImplementation(libs.findLibrary("testcontainers-jdbc").get())
 }
```

- [ ] **Step 1.6: 清理 `build-logic/bin/` 旧编译产物**

Run:

```bash
rm -rf build-logic/bin/
grep -q '^build-logic/bin/' .gitignore || grep -q '/bin/' .gitignore || \
  echo "WARN: build-logic/bin not in .gitignore; check root .gitignore manually"
```

Expected: 命令完成，无 stderr。若提示 WARN，需手工 `cat .gitignore | grep -E 'bin|/bin'` 确认是否已有兜底规则；通常 Gradle 项目根 `.gitignore` 已包含 `bin/` 全局忽略，无需新增。

- [ ] **Step 1.7: 验证 Gradle 构建**

Run:

```bash
./gradlew clean build -x test
```

Expected: `BUILD SUCCESSFUL`，无编译错误，无 deprecation 弃用警告（与 SB 4.0 弃用项无关的可忽略）。

- [ ] **Step 1.8: 验证依赖树**

Run:

```bash
./gradlew :patra-registry-boot:dependencies --configuration runtimeClasspath | grep -iE 'nacos|consul'
```

Expected: 至少看到以下条目，且**不应见任何 consul 字样**：

```
\--- com.alibaba.cloud:spring-cloud-starter-alibaba-nacos-discovery -> 2025.1.0.0
     \--- com.alibaba.nacos:nacos-client:3.1.1
```

也验证 spring-cloud 版本未被 SCA 覆盖：

```bash
./gradlew :patra-registry-boot:dependencyInsight --configuration runtimeClasspath --dependency spring-cloud-context
```

Expected: 看到 `spring-cloud-context:4.4.1`（或 spring-cloud 2025.1.1 对应版本），不应被 SCA 覆盖到旧版。

- [ ] **Step 1.9: 提交**

```bash
git add gradle/libs.versions.toml \
        build-logic/src/main/kotlin/LinqibinDependencyManagement.kt \
        build-logic/src/main/kotlin/linqibin.hexagonal-boot.gradle.kts
git commit -m "$(cat <<'EOF'
build(deps): SpringBoot 4.0.6 / SpringCloud 2025.1.1 升级 + 引入 SCA + 切换 nacos starter (#29)

- Spring Boot 4.0.1 → 4.0.6（2026-04-23 release）
- Spring Cloud 2025.1.0 → 2025.1.1（2026-01-29 release）
- 新增 spring-cloud-alibaba 2025.1.0.0（GA 2026-02-06）
- 服务发现 starter：consul-discovery → alibaba-nacos-discovery
- SCA BOM 在 spring-cloud BOM 之后 import（先 import 的 BOM 优先）

应用 yml 仍指向 spring.cloud.consul.*，由后续 commit 切换；
本 commit 后 build -x test 通过，full test 暂不可跑（Nacos auto-config 会
尝试连接默认 server-addr）。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: 应用配置 + 测试 + JavaDoc 切换

**目标**：全部 boot 模块的 yml/Java 从 Consul 切换到 Nacos；测试通过；项目内除 spec/plan/文档/基础设施 yaml 外不再有 `spring.cloud.consul` 引用。

**Files**:
- Modify: 10 个 yml 文件、5 个 Java 测试文件、1 个 Java 主代码文件（见上方文件影响清单）

- [ ] **Step 2.1: 修改 `patra-registry/patra-registry-boot/src/main/resources/application.yml`**

将第 36-55 行（含 `# Consul Service Discovery` 注释起始）整块替换为模板 A 的 Nacos 块（registry 版）。

Edit：把以下原块

```yaml
  # ========================================
  # Consul Service Discovery
  # ========================================
  cloud:
    consul:
      # 默认从 PATRA_INFRA_HOST 取（zsh env 中已 export 为 Mac mini IP）；
      # 生产环境通过 CONSUL_HOST 直接覆盖。
      host: ${CONSUL_HOST:${PATRA_INFRA_HOST:localhost}}
      port: ${CONSUL_PORT:8500}
      discovery:
        service-name: ${spring.application.name}
        health-check-interval: 10s
        health-check-path: /actuator/health
        # 使用固定 instance-id，服务重启时覆盖旧注册，避免僵尸实例
        instance-id: ${spring.application.name}:${server.port}
        # 使用宿主机实际 IP 注册服务（Consul 容器和宿主机服务都能访问）
        prefer-ip-address: true
        # 指定服务注册的 scheme，让 LoadBalancer 能正确重建 URL
        # 解决 lb:// 协议与 JDK HttpClient 不兼容问题
        # 参考：https://github.com/spring-cloud/spring-cloud-commons/issues/1320
        scheme: http
```

替换为：

```yaml
  # ========================================
  # Nacos Service Discovery
  # ========================================
  cloud:
    nacos:
      username: ${NACOS_USERNAME:nacos}
      password: ${NACOS_PASSWORD:nacos}
      discovery:
        server-addr: ${NACOS_HOST:127.0.0.1}:${NACOS_PORT:8848}
        service: ${spring.application.name}
        fail-fast: false
```

- [ ] **Step 2.2: 修改 `patra-registry/patra-registry-boot/src/main/resources/application-dev.yml`**

将第 23-33 行（4 行 Consul 注释 + cloud.consul 块）整块替换为模板 B：

替换前：

```yaml
  # dev 场景：应用跑在 MacBook，Consul 跑在 Mac mini docker。
  # 容器内 → MacBook tailscale 100.x 不通（Docker bridge 不路由到 utun），
  # 所以用 TTL heartbeat 替代反向 HTTP 健康检查；
  # 用 TAILSCALE_IP 注册，确保跨主机服务发现拿到的 IP 可达。
  cloud:
    consul:
      discovery:
        ip-address: ${TAILSCALE_IP:}
        register-health-check: false
        heartbeat:
          enabled: true
```

替换后：

```yaml
  # dev 场景：用 TAILSCALE_IP 注册，确保跨主机服务发现拿到的 IP 可达。
  cloud:
    nacos:
      discovery:
        ip: ${TAILSCALE_IP:}
```

- [ ] **Step 2.3: 修改 `patra-ingest/patra-ingest-boot/src/main/resources/application.yml`**

将第 14-29 行（不含 `cloud:` 行，从 `consul:` 开始）整块替换。这个文件没有 `# Consul Service Discovery` 注释，直接从 `cloud:` 块的 `consul:` 子块替换。

Edit：把第 15-29 行

```yaml
    consul:
      # 默认从 PATRA_INFRA_HOST 取（zsh env 中已 export 为 Mac mini IP）；
      # 生产环境通过 CONSUL_HOST 直接覆盖。
      host: ${CONSUL_HOST:${PATRA_INFRA_HOST:localhost}}
      port: ${CONSUL_PORT:8500}
      discovery:
        service-name: ${spring.application.name}
        health-check-interval: 10s
        health-check-path: /actuator/health
        # 使用固定 instance-id，服务重启时覆盖旧注册，避免僵尸实例
        instance-id: ${spring.application.name}:${server.port}
        # 使用宿主机实际 IP 注册服务（Consul 容器和宿主机服务都能访问）
        prefer-ip-address: true
        # 指定服务注册的 scheme，解决 lb:// 协议与 HttpClient 不兼容问题
        scheme: http
```

替换为：

```yaml
    nacos:
      username: ${NACOS_USERNAME:nacos}
      password: ${NACOS_PASSWORD:nacos}
      discovery:
        server-addr: ${NACOS_HOST:127.0.0.1}:${NACOS_PORT:8848}
        service: ${spring.application.name}
        fail-fast: false
```

- [ ] **Step 2.4: 修改 `patra-ingest/patra-ingest-boot/src/main/resources/application-dev.yml`**

Edit 第 17-22 行：

替换前：

```yaml
    consul:
      discovery:
        ip-address: ${TAILSCALE_IP:}
        register-health-check: false
        heartbeat:
          enabled: true
```

替换后：

```yaml
    nacos:
      discovery:
        ip: ${TAILSCALE_IP:}
```

- [ ] **Step 2.5: 修改 `patra-ingest/patra-ingest-boot/src/test/resources/application-test-common.yml`**

两处编辑：

第 15-16 行：

```diff
-    consul:
-      enabled: false  # 禁用服务注册（测试环境）
+    nacos:
+      discovery:
+        enabled: false  # 禁用服务注册（测试环境）
```

第 110 行（logging 段）：

```diff
-    org.springframework.cloud.consul: WARN
+    com.alibaba.nacos.client: WARN
```

- [ ] **Step 2.6: 修改 `patra-catalog/patra-catalog-boot/src/main/resources/application.yml`**

Edit 第 29-46 行：

替换前：

```yaml
  # Consul Service Discovery
  cloud:
    consul:
      # 默认从 PATRA_INFRA_HOST 取（zsh env 中已 export 为 Mac mini IP）；
      # 生产环境通过 CONSUL_HOST 直接覆盖。
      host: ${CONSUL_HOST:${PATRA_INFRA_HOST:localhost}}
      port: ${CONSUL_PORT:8500}
      discovery:
        service-name: ${spring.application.name}
        health-check-interval: 10s
        health-check-path: /actuator/health
        # 使用固定 instance-id，服务重启时覆盖旧注册，避免僵尸实例
        instance-id: ${spring.application.name}:${server.port}
        # 使用宿主机实际 IP 注册服务（Consul 容器和宿主机服务都能访问）
        prefer-ip-address: true
        # 指定服务发现返回的 scheme，解决 lb:// 协议与 JDK HttpClient 不兼容问题
        # 参考：https://github.com/spring-cloud/spring-cloud-commons/issues/1320
        scheme: http
```

替换后：

```yaml
  # Nacos Service Discovery
  cloud:
    nacos:
      username: ${NACOS_USERNAME:nacos}
      password: ${NACOS_PASSWORD:nacos}
      discovery:
        server-addr: ${NACOS_HOST:127.0.0.1}:${NACOS_PORT:8848}
        service: ${spring.application.name}
        fail-fast: false
```

- [ ] **Step 2.7: 修改 `patra-catalog/patra-catalog-boot/src/main/resources/application-dev.yml`**

Edit 第 37-42 行：

替换前：

```yaml
    consul:
      discovery:
        ip-address: ${TAILSCALE_IP:}
        register-health-check: false
        heartbeat:
          enabled: true
```

替换后：

```yaml
    nacos:
      discovery:
        ip: ${TAILSCALE_IP:}
```

- [ ] **Step 2.8: 修改 `patra-catalog/patra-catalog-boot/src/test/resources/application-e2e-test.yml`**

两处编辑（同 Step 2.5 模式）：

第 28-29 行：

```diff
-    consul:
-      enabled: false  # 禁用服务注册（测试环境）
+    nacos:
+      discovery:
+        enabled: false  # 禁用服务注册（测试环境）
```

第 88 行：

```diff
-    org.springframework.cloud.consul: WARN
+    com.alibaba.nacos.client: WARN
```

- [ ] **Step 2.9: 修改 `patra-gateway-boot/src/main/resources/application.yml`**

两处编辑：

文件头注释第 5 行：

```diff
-# Routes are configured below with Consul-based service discovery.
+# Routes are configured below with Nacos-based service discovery.
```

第 25-38 行（Consul 块完整替换为 Nacos 块）：

替换前：

```yaml
    # Consul Service Discovery
    consul:
      host: ${CONSUL_HOST:localhost}
      port: ${CONSUL_PORT:8500}
      discovery:
        service-name: ${spring.application.name}
        health-check-interval: 10s
        health-check-path: /actuator/health
        # 使用固定 instance-id，服务重启时覆盖旧注册，避免僵尸实例
        instance-id: ${spring.application.name}:${server.port}
        # 使用宿主机实际 IP 注册服务（Consul 容器和宿主机服务都能访问）
        prefer-ip-address: true
        # 指定服务注册的 scheme，解决 lb:// 协议与 HttpClient 不兼容问题
        scheme: http
```

替换后：

```yaml
    # Nacos Service Discovery
    nacos:
      username: ${NACOS_USERNAME:nacos}
      password: ${NACOS_PASSWORD:nacos}
      discovery:
        server-addr: ${NACOS_HOST:127.0.0.1}:${NACOS_PORT:8848}
        service: ${spring.application.name}
        fail-fast: false
```

- [ ] **Step 2.10: 修改 `patra-object-storage/patra-object-storage-boot/src/main/resources/application.yml`**

Edit 第 10-22 行：

替换前：

```yaml
    consul:
      host: ${CONSUL_HOST:localhost}
      port: ${CONSUL_PORT:8500}
      discovery:
        service-name: ${spring.application.name}
        health-check-interval: 10s
        health-check-path: /actuator/health
        # 使用固定 instance-id，服务重启时覆盖旧注册，避免僵尸实例
        instance-id: ${spring.application.name}:${server.port}
        # 使用宿主机实际 IP 注册服务（Consul 容器和宿主机服务都能访问）
        prefer-ip-address: true
        # 指定服务注册的 scheme，解决 lb:// 协议与 HttpClient 不兼容问题
        scheme: http
```

替换后：

```yaml
    nacos:
      username: ${NACOS_USERNAME:nacos}
      password: ${NACOS_PASSWORD:nacos}
      discovery:
        server-addr: ${NACOS_HOST:127.0.0.1}:${NACOS_PORT:8848}
        service: ${spring.application.name}
        fail-fast: false
```

- [ ] **Step 2.11: 修改 5 个 Java 测试文件 — 字符串属性切换**

对以下 5 个文件，将 `"spring.cloud.consul.enabled=false"` 全部替换为 `"spring.cloud.nacos.discovery.enabled=false"`：

文件列表：

```
patra-catalog/patra-catalog-boot/src/test/java/dev/linqibin/patra/catalog/integration/mesh/MeshScrImportE2E.java
patra-catalog/patra-catalog-boot/src/test/java/dev/linqibin/patra/catalog/integration/mesh/MeshDescriptorImportE2E.java
patra-ingest/patra-ingest-boot/src/test/java/dev/linqibin/patra/ingest/integration/outbox/OutboxPatternE2E.java
patra-ingest/patra-ingest-boot/src/test/java/dev/linqibin/patra/ingest/integration/messaging/RocketMqOutboxPublisherIT.java
patra-ingest/patra-ingest-boot/src/test/java/dev/linqibin/patra/ingest/integration/messaging/TaskReadyMessageListenerIT.java
```

每个文件通过 Edit tool 替换（每个文件只有一处出现）：

```diff
- "spring.cloud.consul.enabled=false",
+ "spring.cloud.nacos.discovery.enabled=false",
```

替换后验证：

```bash
grep -rn "spring.cloud.consul" patra-catalog patra-ingest --include="*.java"
```

Expected: 输出为空。

- [ ] **Step 2.12: 修改 `patra-gateway-boot/.../PatraGatewayApplication.java` — JavaDoc 注释**

Edit `patra-gateway-boot/src/main/java/dev/linqibin/patra/gateway/PatraGatewayApplication.java`:

替换前（第 5-15 行的 JavaDoc 区域）：

```java
/// Patra API网关主入口。
///
/// Spring Cloud Gateway 服务，为所有 Patra 微服务提供统一的路由和服务发现。
/// 处理请求路由、通过 Consul 服务发现进行负载均衡，并作为外部客户端的单一入口点。
///
/// 核心职责:
///
/// - 将请求路由到下游微服务（patra-registry、patra-ingest 等）
/// - 通过 Consul 进行服务发现和负载均衡
/// - 请求/响应日志记录与分布式追踪
/// - CORS 处理和全局过滤器
```

替换后：

```java
/// Patra API网关主入口。
///
/// Spring Cloud Gateway 服务，为所有 Patra 微服务提供统一的路由和服务发现。
/// 处理请求路由、通过 Nacos 服务发现进行负载均衡，并作为外部客户端的单一入口点。
///
/// 核心职责:
///
/// - 将请求路由到下游微服务（patra-registry、patra-ingest 等）
/// - 通过 Nacos 进行服务发现和负载均衡
/// - 请求/响应日志记录与分布式追踪
/// - CORS 处理和全局过滤器
```

- [ ] **Step 2.13: 中间验证 — 编译通过**

Run:

```bash
./gradlew compileJava compileTestJava
```

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 2.14: 验证 — 单元测试与轻量集成测试通过**

Run:

```bash
./gradlew test
```

Expected: 全部测试 PASS。带 Testcontainers 的测试（catalog e2e、ingest e2e/IT）应正常跑过 Postgres 容器，但不应尝试连接 Nacos（因 `spring.cloud.nacos.discovery.enabled=false`）。

若有测试因 Nacos 连接超时失败，检查对应模块的 test yml / Java 字符串是否已正确切换。

- [ ] **Step 2.15: 局部 grep 自检 — 应用层零 consul 残留**

Run:

```bash
grep -rni "consul" \
  patra-registry patra-ingest patra-catalog patra-gateway-boot \
  patra-object-storage \
  --include="*.yml" --include="*.yaml" --include="*.properties" --include="*.java"
```

Expected: 输出为空。若有命中，回到对应 Step 修复。

- [ ] **Step 2.16: 提交**

```bash
git add patra-registry/patra-registry-boot/src/main/resources/application.yml \
        patra-registry/patra-registry-boot/src/main/resources/application-dev.yml \
        patra-ingest/patra-ingest-boot/src/main/resources/application.yml \
        patra-ingest/patra-ingest-boot/src/main/resources/application-dev.yml \
        patra-ingest/patra-ingest-boot/src/test/resources/application-test-common.yml \
        patra-catalog/patra-catalog-boot/src/main/resources/application.yml \
        patra-catalog/patra-catalog-boot/src/main/resources/application-dev.yml \
        patra-catalog/patra-catalog-boot/src/test/resources/application-e2e-test.yml \
        patra-gateway-boot/src/main/resources/application.yml \
        patra-gateway-boot/src/main/java/dev/linqibin/patra/gateway/PatraGatewayApplication.java \
        patra-object-storage/patra-object-storage-boot/src/main/resources/application.yml \
        patra-catalog/patra-catalog-boot/src/test/java/dev/linqibin/patra/catalog/integration/mesh/MeshScrImportE2E.java \
        patra-catalog/patra-catalog-boot/src/test/java/dev/linqibin/patra/catalog/integration/mesh/MeshDescriptorImportE2E.java \
        patra-ingest/patra-ingest-boot/src/test/java/dev/linqibin/patra/ingest/integration/outbox/OutboxPatternE2E.java \
        patra-ingest/patra-ingest-boot/src/test/java/dev/linqibin/patra/ingest/integration/messaging/RocketMqOutboxPublisherIT.java \
        patra-ingest/patra-ingest-boot/src/test/java/dev/linqibin/patra/ingest/integration/messaging/TaskReadyMessageListenerIT.java

git commit -m "$(cat <<'EOF'
refactor(boot): 5 个 boot 模块由 Consul 切换到 Nacos（注释清扫） (#29)

- 5 个生产 application.yml：模板 A（gateway/object-storage 含 metadata.scheme）
- 3 个 application-dev.yml：模板 B（保留 TAILSCALE_IP 双网络支持）
- 2 个测试 yml：模板 C（含 logging 包名 com.alibaba.nacos.client）
- 5 个测试 Java：spring.cloud.consul.enabled → spring.cloud.nacos.discovery.enabled
- gateway 主类 JavaDoc：Consul → Nacos
- Consul 字段映射决策见 spec §6（直接删除等价默认项）

./gradlew test 全量通过；应用代码区域已零 consul 残留。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Docker 基础设施切换

**目标**：core compose 由 consul 切换到 nacos；环境变量与初始化脚本同步；本地起 Nacos 容器可健康通过。

**Files**:
- Modify: `infra/docker/docker-compose.core.yaml`
- Modify: `infra/docker/docker-compose.dev.yaml`
- Modify: `infra/docker/.env`
- Modify: `infra/docker/.env.dev`
- Modify: `infra/docker/.env.example`
- Modify: `infra/scripts/init-volumes.sh`

- [ ] **Step 3.1: 修改 `infra/docker/docker-compose.core.yaml` — 替换 consul 服务块**

Edit 第 44-60 行（整段 consul 服务定义）。

替换前：

```yaml
  consul:
    image: hashicorp/consul:1.18
    container_name: patra-consul
    restart: unless-stopped
    environment:
      - TZ=Asia/Shanghai
    ports:
      - "8500:8500"      # HTTP API & Web UI
      - "8600:8600/udp"  # DNS interface
    command: agent -dev -client=0.0.0.0 -ui
    volumes:
      - ${HOME}/.patra/docker/consul/data:/consul/data
    healthcheck:
      test: [ "CMD", "consul", "members" ]
      interval: 10s
      timeout: 5s
      retries: 10
```

替换后：

```yaml
  nacos:
    image: nacos/nacos-server:v3.0.2
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
      - "9848:9848"   # gRPC（2.x+ 客户端心跳与变更推送，缺则只能 HTTP 握手）
      - "8080:8080"   # 新版控制台
    volumes:
      - ${HOME}/.patra/docker/nacos/data:/home/nacos/data
      - ${HOME}/.patra/docker/nacos/logs:/home/nacos/logs
    healthcheck:
      # Nacos 3.x：v1 路径已 deprecated（410 Gone），健康端点搬到 8080 新控制台
      test: [ "CMD-SHELL", "curl -fs http://localhost:8080/v3/console/health/readiness || exit 1" ]
      interval: 10s
      timeout: 5s
      retries: 10
```

- [ ] **Step 3.2: 修改 `infra/docker/docker-compose.dev.yaml` — include 注释同步**

Edit 第 9 行：

```diff
-  - docker-compose.core.yaml          # PostgreSQL, Redis, Consul
+  - docker-compose.core.yaml          # PostgreSQL, Redis, Nacos
```

- [ ] **Step 3.3: 修改 `infra/docker/.env.dev` — Consul 区块 → Nacos 区块**

Edit 第 8-9 行：

```diff
-# Consul Configuration
-CONSUL_PORT=8500
+# Nacos Configuration（NACOS_AUTH_* 三件套在 .env，本文件仅放可公开值）
+NACOS_PORT=8848
```

- [ ] **Step 3.4: 修改 `infra/docker/.env` — 追加 Nacos 鉴权三件套**

在 `infra/docker/.env` 末尾追加（不删除任何现有内容）：

```env

# ----- Nacos 鉴权（compose 必须）-----
# 三个值必须就地填充，否则 nacos-server 启动失败。
# 生成命令：
#   NACOS_AUTH_TOKEN     :  openssl rand -base64 32     # 解码后必须 >= 32 字节
#   NACOS_AUTH_IDENTITY  :  openssl rand -base64 24     # KEY/VALUE 任意非空字符串即可
# 注意：此处填充的是 dev 默认值；生产必须改密并由 vault/外部 secret 注入。
NACOS_AUTH_TOKEN=<RUN: openssl rand -base64 32 AND PASTE HERE>
NACOS_AUTH_IDENTITY_KEY=patra
NACOS_AUTH_IDENTITY_VALUE=<RUN: openssl rand -base64 24 AND PASTE HERE>
```

执行命令生成两个 token 并替换占位符：

```bash
echo "NACOS_AUTH_TOKEN=$(openssl rand -base64 32)"
echo "NACOS_AUTH_IDENTITY_VALUE=$(openssl rand -base64 24)"
```

将输出粘贴到 `.env` 替换占位符。`.env` 不入 git（已被 `.gitignore` 覆盖），实施者自行操作即可。

- [ ] **Step 3.5: 修改 `infra/docker/.env.example` — 追加 Nacos 占位**

在 `infra/docker/.env.example` 末尾追加：

```env

# ----- Nacos 鉴权 -----
# 复制到 .env 后用以下命令生成真实值替换占位：
#   openssl rand -base64 32   # NACOS_AUTH_TOKEN
#   openssl rand -base64 24   # NACOS_AUTH_IDENTITY_VALUE
NACOS_AUTH_TOKEN=
NACOS_AUTH_IDENTITY_KEY=patra
NACOS_AUTH_IDENTITY_VALUE=
```

- [ ] **Step 3.6: 修改 `infra/scripts/init-volumes.sh` — 数据卷目录**

Edit 第 29 行：

```diff
-  "$ROOT"/consul/data \
+  "$ROOT"/nacos/data \
+  "$ROOT"/nacos/logs \
```

- [ ] **Step 3.7: 验证 — 起 Nacos 容器健康**

```bash
docker compose -f infra/docker/docker-compose.core.yaml up -d nacos
# 等待健康检查（最长约 100 秒）
sleep 30
docker compose -f infra/docker/docker-compose.core.yaml ps nacos
```

Expected: `STATUS` 列显示 `Up X seconds (healthy)`。

进一步验证 readiness 端点：

```bash
curl -fs http://localhost:8080/v3/console/health/readiness && echo
```

Expected: 输出 `OK`。

打开浏览器 `http://localhost:8080`，应能看到 Nacos 3.x 控制台登录页，默认账号 `nacos/nacos`。

- [ ] **Step 3.8: 提交**

```bash
git add infra/docker/docker-compose.core.yaml \
        infra/docker/docker-compose.dev.yaml \
        infra/docker/.env.dev \
        infra/docker/.env.example \
        infra/scripts/init-volumes.sh
git commit -m "$(cat <<'EOF'
chore(infra): docker compose 由 Consul 切换到 Nacos (#29)

- core.yaml：consul → nacos/nacos-server:v3.0.2，standalone + 鉴权开启
- compose.dev.yaml：include 注释同步
- .env.dev：Consul 区块 → Nacos 区块（NACOS_PORT=8848）
- .env.example：追加 NACOS_AUTH_* 占位与生成命令注释
- init-volumes.sh：consul/data → nacos/data + nacos/logs

.env（含真实 token）不入 git；实施者本地用 openssl rand 生成后填入。
Nacos readiness 端点 8080/v3/console/health/readiness 通过验证（v1 路径 410 Gone）。

旧 ${HOME}/.patra/docker/consul/ 数据卷不主动清理，避免误删。
如需回收：rm -rf ${HOME}/.patra/docker/consul

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: 文档清扫 + 零残留断言

**目标**：清扫 6 个 README + 8 个 AI 上下文文件（Skill/Rule）中的 Consul 字样；§11 grep 零残留断言通过；4 个微服务 + object-storage 在 Nacos 上互相发现。

**Files**:
- Modify: `README.md`（根）
- Modify: `infra/docker/README.md`
- Modify: `patra-registry/README.md`
- Modify: `patra-catalog/README.md`
- Modify: `patra-gateway-boot/README.md`
- Modify: `patra-object-storage/README.md`
- Modify: `.claude/rules/project-info.md`
- Modify: `.claude/skills/patra-hexagonal/resources/configuration.md`
- Modify: `.claude/skills/patra-hexagonal/resources/patra-starters.md`
- Modify: `.claude/skills/patra-troubleshooter/SKILL.md`
- Modify: `.agents/skills/patra-hexagonal/resources/configuration.md`
- Modify: `.agents/skills/patra-hexagonal/resources/patra-starters.md`
- Modify: `plugins/patra-codex/skills/patra-hexagonal/references/configuration.md`
- Modify: `plugins/patra-codex/skills/patra-hexagonal/references/patra-starters.md`

> **执行策略**：每个 README 内的"Consul 整节"通常需要整段重写为 Nacos 版本。下面给出关键改动模式；具体行号已在 spec §3 给出参考，文件内还可能有其他散落引用，**以本 Task 末尾的零残留 grep 断言为准**。

- [ ] **Step 4.1: 修改根 `README.md`**

打开文件，定位以下行：

- 行 102：`- Consul (端口 8500) — 服务注册中心` → `- Nacos (端口 8848) — 服务注册中心`
- 行 145：`- 服务注册中心 (Consul) 在端口 8500 运行，提供服务发现和健康检查功能。` → `- 服务注册中心 (Nacos) 在端口 8848 运行，提供服务发现和健康检查功能。`

- [ ] **Step 4.2: 修改 `infra/docker/README.md`**

定位以下行并替换：

- 行 14：`├── docker-compose.core.yaml         # postgres + redis + consul` → `├── docker-compose.core.yaml         # postgres + redis + nacos`
- 行 131：yaml 示例块中的 `consul:` 与 `host:`/`port:` 改为 Nacos 模板 A 等价形式（参考本 plan 顶部"配置模板"节）
- 行 151：`- **Consul UI**: http://linqibins-mac-mini:8500` → `- **Nacos 控制台**: http://linqibins-mac-mini:8080`
- 行 283：栈描述图 `postgres, redis, consul` → `postgres, redis, nacos`

- [ ] **Step 4.3: 修改 `patra-registry/README.md`**

定位行 141：

```diff
-| **Consul** | 服务注册中心 |
+| **Nacos** | 服务注册中心 |
```

- [ ] **Step 4.4: 修改 `patra-catalog/README.md`**

定位行 137：

```diff
-- Consul（服务发现）
+- Nacos（服务发现）
```

- [ ] **Step 4.5: 修改 `patra-gateway-boot/README.md`**

逐处替换：

| 行 | 替换 |
|---|---|
| 12 | `- **服务发现**: 通过 Consul ...` → `- **服务发现**: 通过 Nacos ...` |
| 24 | `# 主配置文件(路由、Consul)` → `# 主配置文件(路由、Nacos)` |
| 76 | `### Consul 集成` → `### Nacos 集成` |
| 77 | `通过 Spring Cloud Consul 实现服务发现:` → `通过 Spring Cloud Alibaba Nacos 实现服务发现:` |
| 153 | `\| `CONSUL_HOST` \| Consul 服务器地址 \| `localhost` \|` → `\| `NACOS_HOST` \| Nacos 服务器地址 \| `localhost` \|` |
| 154 | `\| `CONSUL_PORT` \| Consul 端口 \| `8500` \|` → `\| `NACOS_PORT` \| Nacos 端口 \| `8848` \|` |
| 158 | `- **Consul 端口**: 8500` → `- **Nacos 端口**: 8848` |
| 205 | `\| **Consul Discovery** \| 服务发现和注册 \|` → `\| **Nacos Discovery** \| 服务发现和注册 \|` |

第 82-84 行附近的 yaml 示例块（含 `consul: / host: / port:`）整段替换为：

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

如有 dev 注意事项段提到 Consul 心跳模式或 `register-health-check: false`，删除整段（Nacos 默认即心跳模型，无需说明）。

- [ ] **Step 4.6: 修改 `patra-object-storage/README.md`**

多处替换：

| 行 | 替换 |
|---|---|
| 283 | `\| Consul \| 1.18+ \| 服务注册中心 \|` → `\| Nacos \| 3.0+ \| 服务注册中心 \|` |
| 295 | `- **Consul**: 服务注册中心(开发环境可选)` → `- **Nacos**: 服务注册中心(开发环境可选)` |
| 308-309 | `export CONSUL_HOST=localhost` / `export CONSUL_PORT=8500` → `export NACOS_HOST=localhost` / `export NACOS_PORT=8848` |
| 347 | `- 检查 Consul 服务注册状态` → `- 检查 Nacos 服务注册状态` |

第 256-258 行附近的 yaml 示例块（含 `consul: / host: / port:`）整段替换为：

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

第 324 行附近的服务注册查询示例：

- `# 查看服务注册（Consul）` → `# 查看服务注册（Nacos）`
- 若有 `curl http://localhost:8500/v1/catalog/services` 类命令，改为 `curl 'http://localhost:8848/nacos/v1/ns/catalog/services?pageNo=1&pageSize=10&namespaceId=public'` 或指向控制台 `http://localhost:8080`

- [ ] **Step 4.7: 修改 `.claude/rules/project-info.md`**

定位行 8：

```diff
-**技术栈**: Java 25 | Spring Boot 4.0.1 | Spring Data JPA | PostgreSQL 17 | Consul
+**技术栈**: Java 25 | Spring Boot 4.0.6 | Spring Data JPA | PostgreSQL 17 | Nacos
```

- [ ] **Step 4.8: 修改 3 套 Skill 文档 — configuration.md（同款替换）**

对以下 3 个文件执行同款替换：

```
.claude/skills/patra-hexagonal/resources/configuration.md
.agents/skills/patra-hexagonal/resources/configuration.md
plugins/patra-codex/skills/patra-hexagonal/references/configuration.md
```

每个文件内：

- 行 21：`## Consul 服务发现配置` → `## Nacos 服务发现配置`
- 行 28-30：yaml 示例 `consul:` / `host:` / `port:` 改为 Nacos 模板 A 等价形式：

替换前（典型）：

```yaml
    consul:
      host: ${CONSUL_HOST:localhost}
      port: ${CONSUL_PORT:8500}
```

替换后：

```yaml
    nacos:
      username: ${NACOS_USERNAME:nacos}
      password: ${NACOS_PASSWORD:nacos}
      discovery:
        server-addr: ${NACOS_HOST:127.0.0.1}:${NACOS_PORT:8848}
        service: ${spring.application.name}
        fail-fast: false
```

- 行 38：`Consul 仅用于服务发现，不用于配置管理。` → `Nacos 仅用于服务发现，不用于配置管理。`

具体文件 yaml 示例的上下文行可能略有差异，以 grep 命中行为准。

- [ ] **Step 4.9: 修改 3 套 Skill 文档 — patra-starters.md（同款替换）**

对以下 3 个文件执行同款替换：

```
.claude/skills/patra-hexagonal/resources/patra-starters.md
.agents/skills/patra-hexagonal/resources/patra-starters.md
plugins/patra-codex/skills/patra-hexagonal/references/patra-starters.md
```

每个文件行 32：

```diff
-**功能**：Spring 7 HTTP Interface + RestClient + Consul 服务发现
+**功能**：Spring 7 HTTP Interface + RestClient + Nacos 服务发现
```

- [ ] **Step 4.10: 修改 `.claude/skills/patra-troubleshooter/SKILL.md`**

定位行 7（trigger 描述）：

```diff
-  数据库连接异常、Consul 服务发现问题、HTTP Interface 调用超时或返回错误。
+  数据库连接异常、Nacos 服务发现问题、HTTP Interface 调用超时或返回错误。
```

- [ ] **Step 4.11: 运行 §11 零残留 grep 断言**

```bash
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

Expected: 输出**完全为空**。

若有任何命中：

1. 检查命中文件，若属于本计划范围内的文件，回到对应 Step 修复
2. 若属于范围外（即 spec §3 未列出），且不是 `*.tsv` 数据文件或 spec/plan 本身，则补 Edit 修复后再跑断言
3. 直到断言输出为空才能进入下一步

- [ ] **Step 4.12: 联调验证 — 4 个微服务 + object-storage 注册并互相发现**

确保 Nacos 容器在跑（Task 3 已起）：

```bash
docker compose -f infra/docker/docker-compose.core.yaml ps nacos
# Expected: healthy
```

确保 `.env` 中的 `NACOS_AUTH_TOKEN/IDENTITY` 真实生成（非占位）。

依次在 5 个 boot 模块下执行 `./gradlew bootRun`（建议各开一个 terminal）：

```bash
./gradlew :patra-registry-boot:bootRun
./gradlew :patra-ingest-boot:bootRun
./gradlew :patra-catalog-boot:bootRun
./gradlew :patra-gateway-boot:bootRun
./gradlew :patra-object-storage-boot:bootRun
```

每个服务启动后，检查日志中应出现：

```
... INFO ... Nacos registry, ${app.name} ${ip}:${port} register finished
```

不应见任何 401/403 鉴权失败或连接超时。

打开 Nacos 控制台 `http://localhost:8080` → 登录 → 服务管理 → 服务列表，应见 5 个服务（patra-registry / patra-ingest / patra-catalog / patra-gateway / patra-object-storage），每个均 1 个 healthy 实例。

通过 gateway 验证下游 lb:// 路由：

```bash
curl -fs http://localhost:9528/patra-registry/actuator/health && echo
curl -fs http://localhost:9528/patra-ingest/actuator/health && echo
curl -fs http://localhost:9528/patra-catalog/actuator/health && echo
```

Expected: 每条返回 `{"status":"UP"}`。

- [ ] **Step 4.13: 全量测试 — 含 e2e**

停掉 5 个微服务（保留 Nacos 容器运行）后执行：

```bash
./gradlew test
```

Expected: 全部测试 PASS（与 Task 2 Step 2.14 同款，但本次跑在最终代码状态上）。

如果项目有专门的 e2e gradle task（如 `:patra-catalog-boot:e2eTest` 之类），也一并执行；从 spec 看 e2e 测试通过 Testcontainers 跑且不依赖 Nacos，应正常通过。

- [ ] **Step 4.14: 提交**

```bash
git add README.md \
        infra/docker/README.md \
        patra-registry/README.md \
        patra-catalog/README.md \
        patra-gateway-boot/README.md \
        patra-object-storage/README.md \
        .claude/rules/project-info.md \
        .claude/skills/patra-hexagonal/resources/configuration.md \
        .claude/skills/patra-hexagonal/resources/patra-starters.md \
        .claude/skills/patra-troubleshooter/SKILL.md \
        .agents/skills/patra-hexagonal/resources/configuration.md \
        .agents/skills/patra-hexagonal/resources/patra-starters.md \
        plugins/patra-codex/skills/patra-hexagonal/references/configuration.md \
        plugins/patra-codex/skills/patra-hexagonal/references/patra-starters.md

git commit -m "$(cat <<'EOF'
docs: 清扫全部模块 README 与 Skill 文档中的 Consul 字样 (#29)

- 6 个 README：根、infra/docker、registry、catalog、gateway、object-storage
- .claude/rules/project-info.md：技术栈 Spring Boot 4.0.1 → 4.0.6 / Consul → Nacos
- 7 个 Skill 文档（.claude / .agents / plugins/patra-codex 三套）：
  configuration.md（"Consul 服务发现配置"整节重写）+
  patra-starters.md（http-interface 描述）+
  patra-troubleshooter SKILL.md（trigger 描述）

零残留 grep 断言通过（spec §11）：
除 docs/superpowers/{specs,plans}/、scripts/letpub/venues_issn.tsv 数据文件、
构建产物外，项目内已无 consul / CONSUL_HOST / CONSUL_PORT 字样。

联调验证：5 个微服务在 Nacos 注册并通过 gateway lb:// 互相发现；
./gradlew test 全量通过。

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## 实施后总结

完成全部 4 个 Task 后：

- ✅ Spring Boot 4.0.6 / Spring Cloud 2025.1.1 / SCA 2025.1.0.0 已生效
- ✅ Consul starter 已下线，nacos-client 3.1.1 已上线
- ✅ 5 个 boot 模块全部走 Nacos 服务发现
- ✅ Docker 基础设施切换完成（nacos-server v3.0.2 standalone + 鉴权）
- ✅ 项目内零 Consul 残留（除 spec/plan/venues_issn.tsv 数据文件外）
- ✅ 联调验证 5 个服务相互发现 + gateway lb:// 转发
- ✅ ./gradlew test 全量通过
- ✅ issue #29 验收清单全部满足

回滚：4 个 commit 每个都是原子粒度，`git revert <hash>` 即可回到对应中间状态。
