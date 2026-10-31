# notification 服务骨架与部署接入 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans（本会话内联执行，不派 subagent）. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新建 `patra-notification` 六层模块骨架（五个子域），接好构建、架构测试、Flyway、Nacos、网关、CD、compose、XXL 执行器与文档。

**Architecture:** 照 interaction 骨架期的写法：各层只有子域 `package-info`（`-Xpkginfo:always` 让架构测试有类可查），boot 装配启动类、配置与执行器；部署接入逐项对照 spec 第 12 节核对单。

**Tech Stack:** Java 25、Spring Boot 4.0.8、Gradle 9（Kotlin DSL + build-logic 约定插件）、ArchUnit、Testcontainers PG17、WireMock、Bash 3.2（CD 脚本测试）

**Spec:** `docs/patra/specs/2026-10-30-notification-skeleton-design.md`

## Global Constraints

- 根包 `dev.patra.notification`；服务名 `patra-notification`；端口 `6600`，XXL 执行器 `6601`；错误码前缀 `NOTIFICATION`；库 `patra_notification`
- 子域：底座 `kernel`，另有 `inbox`、`preference`、`mail`、`realtime`；骨架不声明任何子域依赖边
- R5：app / adapter 不声明 project 依赖；infra 只 `api(project(":patra-tool:patra-tool-jpa"))` + `testFixturesImplementation(project(":patra-tool:patra-tool-test"))`；boot 的 domain / app / api 用 `testImplementation`
- 骨架不建 schema、不写迁移脚本、不接安全 starter / identity 客户端 / RabbitMQ
- 所有方法写 `///` Javadoc；测试方法 snake_case；验证前先 `./gradlew spotlessApply`
- 每个任务**不提交**：全部做完向编排会话汇报、得到同意后一次性提交（只提交本 Issue 的路径，message 中文、中文动词起头、带 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`）

## Review Focus

1. 架构测试导入为空时规则「全绿」是假象——Task 1 用临时放一个层根下的类（`dev.patra.notification.domain.Loose`）确认子域规则会红，再删掉。
2. `unitOf` 漏改时 CI 照样绿——Task 6 用 `module-graph.json` 里 notification 模块的 `unit` 字段核对，不只看测试通过。
3. Nacos 全量推送会删掉别的会话推上去的配置——Task 10 只在 `--check` 差异恰好是「新增 patra-notification.yaml」时推。
4. 网关对 `/patra-notification/actuator/**` 必须仍是 403——Task 5 跑 `GatewayBlockedPathsIT` 确认新路由没打开它。
5. compose 必需文件缺失时 notification 不该悄悄起——Task 7 删掉临时 `nacos.env` 确认 `docker compose config` 报出完整路径。

---

### Task 1: 模块注册与架构测试

**Files:**
- Modify: `settings.gradle.kts`（interaction 段之后加六行 `includeAt` + `mapParent(":patra-api:patra-notification", "patra-api/patra-notification")`）
- Create: `patra-api/patra-notification/build.gradle.kts`（只有注释，同 interaction）
- Create: `patra-api/patra-notification/patra-notification-{domain,app,infra,adapter,api,boot}/build.gradle.kts`（spec 6.1 / 6.3；domain、app、infra、adapter 加 `tasks.compileJava { options.compilerArgs.add("-Xpkginfo:always") }`）
- Create: `patra-api/patra-notification/patra-notification-boot/src/test/java/dev/patra/notification/architecture/NotificationArchitectureTest.java`
- Create: `patra-api/patra-notification/patra-notification-boot/src/test/resources/archunit.properties`
- Create: 20 个 `package-info.java`：`patra-notification-{domain,app,infra,adapter}/src/main/java/dev/patra/notification/{层}/{kernel,inbox,preference,mail,realtime}/package-info.java`

**Interfaces:**
- Produces: Gradle 路径 `:patra-api:patra-notification:patra-notification-{层}`；boot 的 `springBoot.mainClass = "dev.patra.notification.PatraNotificationApplication"`（类在 Task 4 建）

- [ ] **Step 1: 注册模块、写构建脚本**（boot 依赖照 spec 6.3；boot 先不放主代码）

- [ ] **Step 2: 验证注册** — Run: `./gradlew :patra-api:patra-notification:projects -q` → 列出六个子模块

- [ ] **Step 3: 写架构测试**：照 `InteractionArchitectureTest` 全文复制改名，`FOUNDATION = "kernel"`，`SUBDOMAINS = List.of(FOUNDATION, "inbox", "preference", "mail", "realtime")`，`new SubdomainArchitectureRules("dev.patra.notification", FOUNDATION, Set.copyOf(SUBDOMAINS))` 不带边；导入过滤 `location.contains("/patra-notification-")`；`archunit.properties` 同 interaction

- [ ] **Step 4: 跑测试确认红** — Run: `./gradlew :patra-api:patra-notification:patra-notification-boot:test --tests "*NotificationArchitectureTest*"` → FAIL：层规则报 `Layer 'Domain' is empty` 一类，子域规则报「已声明的子域 inbox 在 domain 层没有任何类」

- [ ] **Step 5: 写 20 个 `package-info.java`**：domain 层正文写子域职责与落地 Issue（spec 5.1 / 5.2 的一两句）、底座写「对同服务其他子域完全公开」、普通子域写公开包 `domain.{子域}.port.read`、`model.read`、`event`；app / infra / adapter 第一行换成对应层名，正文同 domain

- [ ] **Step 6: 跑测试确认绿** — 同 Step 4 → PASS

- [ ] **Step 7: 负向核对** — 临时建 `patra-notification-domain/src/main/java/dev/patra/notification/domain/Loose.java`（空类），重跑 → 子域规则 FAIL 且点名 `Loose`；删掉后再跑 → PASS

- [ ] **Step 8: domain 纯净** — Run: `./gradlew :patra-api:patra-notification:patra-notification-domain:check` → BUILD SUCCESSFUL（含 `checkDomainPurity`、R5）

### Task 2: jar 内配置

**Files:**
- Create: `patra-api/patra-notification/patra-notification-boot/src/test/java/dev/patra/notification/ApplicationConfigTest.java`
- Create: `patra-api/patra-notification/patra-notification-boot/src/main/resources/application.yml`

- [ ] **Step 1: 写测试**：照 interaction 的 `ApplicationConfigTest`，保留 `should_not_ship_profile_files`、`should_leave_name_profile_and_nacos_to_launcher_and_starter`、`should_keep_flyway_history_in_default_schema`、`should_not_configure_redis`；端口与前缀改为 `should_listen_on_6600_with_notification_error_prefix`：`assertThat(property("server.port")).isEqualTo("6600")`、`assertThat(property("patra.web.error.context-prefix")).isEqualTo("NOTIFICATION")`；不要 catalog / identity 分组的两个用例

- [ ] **Step 2: 确认红** — Run: `./gradlew :patra-api:patra-notification:patra-notification-boot:test --tests "*ApplicationConfigTest*"` → FAIL（没有 `application.yml`，`ClassPathResource` 读不到）

- [ ] **Step 3: 写 `application.yml`**：spec 9.2 的表；结构与注释照 interaction 的 `application.yml`，去掉 `patra.http-client` 段，日志目录 `patra-notification`

- [ ] **Step 4: 确认绿** — 同 Step 2 → PASS

### Task 3: infra 集成测试基座与迁移测试

**Files:**
- Create: `patra-api/patra-notification/patra-notification-infra/src/integrationTest/java/dev/patra/notification/infra/NotificationMigrationIT.java`
- Create: `patra-api/patra-notification/patra-notification-infra/src/integrationTest/java/dev/patra/notification/infra/NotificationITBootstrap.java`（`@SpringBootApplication` 空类，包级可见）
- Create: `patra-api/patra-notification/patra-notification-infra/src/integrationTest/resources/application-test.yml`（同 interaction infra 那份）
- Create: `patra-api/patra-notification/patra-notification-infra/src/testFixtures/java/dev/patra/notification/infra/config/NotificationITPostgreSQLContainerInitializer.java`

**Interfaces:**
- Produces: `public class NotificationITPostgreSQLContainerInitializer extends PostgreSQLContainerInitializer`，`getDatabaseName()` 返回 `"patra_notification"`（Task 4 用）

- [ ] **Step 1: 写迁移测试**（`@SpringBootTest(classes = NotificationITBootstrap.class)`、`@ContextConfiguration(initializers = NotificationITPostgreSQLContainerInitializer.class)`、`@ActiveProfiles("test")`）：
  - `should_keep_a_single_flyway_history_in_public`：`information_schema.tables` 里 `flyway_schema_history` 的 `table_schema` `containsExactly("public")`
  - `should_only_have_public_and_subdomain_schemas`：非 `pg_%`、非 `information_schema` 的 schema `contains("public")` 且 `isSubsetOf("public", "kernel", "inbox", "preference", "mail", "realtime")`

- [ ] **Step 2: 确认红** — Run: `./gradlew :patra-api:patra-notification:patra-notification-infra:integrationTest` → FAIL（编译：找不到 `NotificationITBootstrap` / 初始化器）

- [ ] **Step 3: 建启动类、初始化器与 `application-test.yml`**

- [ ] **Step 4: 确认绿** — 同 Step 2 → PASS（日志里 Flyway 建了 `public.flyway_schema_history`，没有迁移）

### Task 4: 启动类、执行器与启动测试

**Files:**
- Create: `patra-api/patra-notification/patra-notification-boot/src/integrationTest/java/dev/patra/notification/PatraNotificationApplicationIT.java`
- Create: `patra-api/patra-notification/patra-notification-boot/src/integrationTest/resources/application-test.yml`（同 interaction boot 那份）
- Create: `patra-api/patra-notification/patra-notification-boot/src/main/java/dev/patra/notification/PatraNotificationApplication.java`（spec 9.1）
- Create: `patra-api/patra-notification/patra-notification-boot/src/main/java/dev/patra/notification/config/XxlJobConfig.java`（照 interaction，Javadoc 改为 notification 的任务、端口 6601）
- Create: `.run/PatraNotificationApplication.run.xml`（照 interaction，module `patra.patra-api.patra-notification.patra-notification-boot.main`）

- [ ] **Step 1: 写启动测试**：照 interaction 的 `PatraInteractionApplicationIT`，三个用例 `should_report_up_on_health_endpoint`、`should_use_notification_error_prefix`（`isEqualTo("NOTIFICATION")`）、`should_not_start_xxl_job_executor_in_tests`；初始化器用 Task 3 的

- [ ] **Step 2: 确认红** — Run: `./gradlew :patra-api:patra-notification:patra-notification-boot:integrationTest` → FAIL（找不到 `@SpringBootConfiguration`）

- [ ] **Step 3: 写启动类、`XxlJobConfig`、`application-test.yml`、运行配置**

- [ ] **Step 4: 确认绿** — 同 Step 2 → PASS

- [ ] **Step 5: 架构测试仍绿**（boot 有了主代码）— Run: `./gradlew :patra-api:patra-notification:patra-notification-boot:test` → PASS

### Task 5: 网关路由

**Files:**
- Modify: `patra-api/patra-gateway-boot/src/integrationTest/java/dev/patra/gateway/GatewayRoutingIT.java`
- Modify: `patra-api/patra-gateway-boot/src/main/resources/application.yml`（interaction 路由后加 spec 第 11 节的路由；`scalar.sources` 加一项）
- Modify: `patra-api/patra-gateway-boot/src/main/java/dev/patra/gateway/PatraGatewayApplication.java`（Javadoc 服务清单加 `patra-notification`）

- [ ] **Step 1: 写测试**：新增 `notification` 的 `WireMockExtension`（同 interaction 那个的配置）、`@DynamicPropertySource` 加 `spring.cloud.discovery.client.simple.instances.patra-notification[0].uri`；用例 `should_route_notification_prefix_to_notification_service`：桩 `get(urlPathEqualTo("/notifications")).withQueryParam("tab", equalTo("reply"))`，请求 `/patra-notification/notifications?tab=reply` 期望 200，`verify` 下游收到同路径同查询参数

- [ ] **Step 2: 确认红** — Run: `./gradlew :patra-api:patra-gateway-boot:integrationTest --tests "*GatewayRoutingIT*"` → 新用例 FAIL（404）

- [ ] **Step 3: 加路由、scalar 源、Javadoc**

- [ ] **Step 4: 确认绿，拒绝名单照旧** — Run: `./gradlew :patra-api:patra-gateway-boot:integrationTest --tests "*GatewayRoutingIT*" --tests "*GatewayBlockedPathsIT*"` → PASS

### Task 6: CD 清单与变更检测

**Files:**
- Modify: `patra-infra/cd/deploy.test.sh`（头注释加场景 8；文件末尾 `echo "----"` 之前加场景 8）
- Modify: `patra-infra/cd/services.json`（spec 第 12 节 #7）
- Modify: `patra-infra/cd/deploy.sh:12`（注释补「notification 在 interaction 之后」）
- Modify: `patra-infra/cd/detect-changes.test.sh:44,98`（单元数组改为 `["catalog","foundation","gateway","identity","interaction","notification","registry"]`，描述「全部 6 个服务」）；末尾加场景 16（照场景 15，文件 `patra-api/patra-notification/patra-notification-infra/src/main/java/X.java` → `["notification"]`；`settings.gradle.kts` 含 notification；`patra-infra/nacos/dev/patra-notification.yaml` → `["foundation","notification"]`）
- Modify: `patra-infra/cd/detect-changes.sh:6,22`（`ALL_UNITS` 与注释加 `notification`）
- Modify: `build.gradle.kts`（`unitOf` 加一行 `patra-api/patra-notification` → `"notification"`；`units` 在 `"interaction"` 后加 `"notification"`）
- Modify: `patra-infra/cd/module-graph.json`（生成）

- [ ] **Step 1: 写 deploy 场景 8**：镜像桩 `notification interaction`，`deploy.sh newsha '["notification","interaction"]'`，断言 `up -d` 顺序 `"interaction notification"`、两个 last-good 都是 `newsha`

- [ ] **Step 2: 确认红** — Run: `bash patra-infra/cd/deploy.test.sh` → 场景 8 FAIL（未知服务，退出 2）

- [ ] **Step 3: 加 `services.json` 条目与注释** → 重跑 → `PASS=8 FAIL=0`

- [ ] **Step 4: 改 detect-changes 测试（两条旧断言 + 场景 16）** — Run: `bash patra-infra/cd/detect-changes.test.sh` → 新改的断言 FAIL

- [ ] **Step 5: 改 `ALL_UNITS`、`unitOf` / `units`，重新生成模块图** — Run: `./gradlew dumpModuleGraph -q && bash patra-infra/cd/detect-changes.test.sh` → `失败 0`

- [ ] **Step 6: 核对模块图** — Run: `jq -r '.modules[] | select(.dir | startswith("patra-api/patra-notification")) | "\(.dir) \(.unit) \(.tasks|join(","))"' patra-infra/cd/module-graph.json` → 六行，`unit` 全是 `notification`；infra、boot 的 tasks 含 `integrationTest`。`git diff --stat patra-infra/cd/module-graph.json` 只因 notification 而变

### Task 7: compose、建库、Nacos 与校验脚本

**Files:**
- Modify: `patra-infra/docker/docker-compose.apps.yaml`（interaction 块后加 spec 第 13 节的块；第 8、12 行「5 个」改 6 个）
- Modify: `patra-infra/docker/postgres/init-scripts/02-create-databases.sql`（`patra_interaction` 后加 `CREATE DATABASE patra_notification;`）
- Create: `patra-infra/nacos/dev/patra-notification.yaml`（spec 9.4）
- Modify: `patra-infra/scripts/nacos-verify.sh:5,29,151`

- [ ] **Step 1: 改 compose 并解析** — Run（临时 HOME，不碰真实密钥）：
  ```bash
  T=$(mktemp -d) && mkdir -p $T/.patra/secrets && for f in nacos redis gateway; do echo "X=1" > $T/.patra/secrets/$f.env; done
  HOME=$T docker compose -f patra-infra/docker/docker-compose.apps.yaml config | sed -n '/^  notification:/,/^  [a-z]*:$/p'
  ```
  Expected：镜像 `ghcr.io/linqibin0826/patra-notification:latest`、端口 6600、`memory: "1073741824"`、env_file 依次 `.env.common`、`nacos.env`、`notification.env`（后者 `required: false`）

- [ ] **Step 2: 缺必需文件时报错** — 删掉 `$T/.patra/secrets/nacos.env` 重跑 → 报错且含完整路径

- [ ] **Step 3: 建库脚本、Nacos 配置、`nacos-verify.sh`**；Run: `bash -n patra-infra/scripts/nacos-verify.sh && ./gradlew :patra-tool:patra-tool-launch:test --tests "*NacosConfigFilesTest*" --tests "*JarConfigOverlapCheckerTest*"` → PASS

### Task 8: 文档与上线 runbook

**Files:**
- Create: `patra-api/patra-notification/README.md`（spec 第 16 节第一行的内容）
- Create: `docs/patra/runbooks/v0.16-notification-go-live-runbook.md`（spec 第 17 节，写法照 `v0.13-library-go-live-runbook.md`）
- Modify: spec 第 16 节表里其余各文件（`JarConfigOverlapCheckerTest` 只改 `@DisplayName`）

- [ ] **Step 1: 写服务 README 与 runbook**
- [ ] **Step 2: 同步清单** — 逐个改 spec 第 16 节的文件；`.claude/` 下两处被钩子拒绝时，改动做成补丁放 scratchpad，交编排会话
- [ ] **Step 3: 扫漏** — Run: `git grep -nE "5 个|五个|interaction / gateway|interaction、gateway" -- ':!docs/patra/specs/**' ':!docs/patra/plans/**' ':!docs/patra/runbooks/**' ':!docs/patra/release-specs/**' ':!patra-portal/**' ':!patra-learn/**'` → 没有指后端服务清单的命中

### Task 9: 全量门禁

- [ ] **Step 1:** `./gradlew spotlessApply && ./gradlew check` → BUILD SUCCESSFUL
- [ ] **Step 2:** `./gradlew :patra-api:patra-notification:patra-notification-infra:integrationTest :patra-api:patra-notification:patra-notification-boot:integrationTest :patra-api:patra-gateway-boot:integrationTest :patra-tool:patra-tool-launch:test` → BUILD SUCCESSFUL
- [ ] **Step 3:** `bash patra-infra/cd/detect-changes.test.sh && bash patra-infra/cd/deploy.test.sh` → 全部通过
- [ ] **Step 4:** `./gradlew dumpModuleGraph -q && git diff --quiet patra-infra/cd/module-graph.json` → 无差异（模块图与最终依赖一致）

### Task 10: 本机启动验收（编排会话转达用户同意后才做）

- [ ] **Step 1:** 按 runbook 第 2 步在 mini 建库（先查不存在）
- [ ] **Step 2:** `NACOS_ADDR=http://100.103.73.27:8848 bash patra-infra/scripts/nacos-push.sh --check` → 退出码 2 且差异**只有**新增 `patra-notification.yaml` 才推；否则停下报编排会话
- [ ] **Step 3:** 导出 `JAVA_TOOL_OPTIONS` 后 `bootRun` notification（spec 第 15 节命令）→ `curl 127.0.0.1:6600/actuator/health` UP、`/v3/api-docs` 200、日志 `port=6601`、Nacos 有实例
- [ ] **Step 4:** 本机起网关 → `curl 127.0.0.1:9528/patra-notification/v3/api-docs` 200、`/patra-notification/actuator/health` 403
- [ ] **Step 5:** 停掉两个进程，Nacos 实例列表为空
