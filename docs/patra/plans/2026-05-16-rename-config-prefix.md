# 配置前缀重命名实施计划（patra.* → linqibin.starter.* / patra.starter.*）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 11 个通用 starter 与 2 个 patra 专用 starter 的 Spring 配置 prefix（`@ConfigurationProperties` + `@ConditionalOnProperty` + Java 硬编码 + Micrometer 常量 + yml/properties + IDE metadata + README）从 `patra.*` 完整迁移到 `linqibin.starter.*` / `patra.starter.*`，并以**单一 commit** 完成原子提交。

**Architecture:** 重构按"文件类型层"分阶段执行（Java → yml/JSON → README），每阶段后 `./gradlew compileJava` 验证。对外 atomic（单 commit），对内增量（每阶段 checkpoint）。yml 文件因 nested 结构需逐文件块级重写，其他文件用 `Edit` 精确替换。

**Tech Stack:** Java 25 / Spring Boot 4.0.1 / Gradle 9.2.1 / Spring Configuration Properties / Micrometer

**关联 spec:** `docs/patra/specs/2026-05-16-rename-config-prefix-to-linqibin-design.md`（HEAD `13ef313fa`）

**关联分支:** 已切换至 `refactor/rename-config-prefix`（HEAD `13ef313fa`）

---

## 完整映射表（贯穿全 plan 使用）

### 通用 starter（11 个 → `linqibin.starter.*`）

| # | 旧 prefix | 新 prefix |
|---|---|---|
| 1 | `patra.async` | `linqibin.starter.core.async` |
| 2 | `patra.command-bus` | `linqibin.starter.core.command-bus` |
| 3 | `patra.command-bus.interceptors` | `linqibin.starter.core.command-bus.interceptors` |
| 4 | `patra.error` | `linqibin.starter.core.error` |
| 5 | `patra.error.circuit-breaker` | `linqibin.starter.core.error.circuit-breaker` |
| 6 | `patra.tracing` | `linqibin.starter.core.tracing` |
| 7 | `patra.batch` | `linqibin.starter.batch` |
| 8 | `patra.batch.metrics` | `linqibin.starter.batch.metrics` |
| 9 | `patra.batch.schema` | `linqibin.starter.batch.schema` |
| 10 | `patra.batch.datasource` | `linqibin.starter.batch.datasource` |
| 11 | `patra.http.interface` | `linqibin.starter.http-interface` |
| 12 | `patra.object-storage` | `linqibin.starter.object-storage` |
| 13 | `patra.observability` | `linqibin.starter.observability` |
| 14 | `patra.observability.metrics` | `linqibin.starter.observability.metrics` |
| 15 | `patra.openapi` | `linqibin.starter.openapi` |
| 16 | `patra.redisson` | `linqibin.starter.redisson` |
| 17 | `patra.redisson.lock` | `linqibin.starter.redisson.lock` |
| 18 | `patra.rest-client` | `linqibin.starter.rest-client` |
| 19 | `patra.rest-client.download` | `linqibin.starter.rest-client.download` |
| 20 | `patra.rest-client.download.ftp` | `linqibin.starter.rest-client.download.ftp` |
| 21 | `patra.rest-client.streaming` | `linqibin.starter.rest-client.streaming` |
| 22 | `patra.rest-client.proxy.tunnel` | `linqibin.starter.rest-client.proxy.tunnel` |
| 23 | `patra.rest-client.interceptors.logging` | `linqibin.starter.rest-client.interceptors.logging` |
| 24 | `patra.rest-client.clients.long-running` | `linqibin.starter.rest-client.clients.long-running` |
| 25 | `patra.web.problem` | `linqibin.starter.web.problem` |
| 26 | `patra.object_storage` (Micrometer underscore) | `linqibin.starter.object_storage` |

### patra 专用 starter（2 个 → `patra.starter.*`）

| # | 旧 prefix | 新 prefix |
|---|---|---|
| 27 | `expr` (standalone, ExprModeProperties) | `patra.starter.expr.mode` |
| 28 | `patra.expr.compiler` | `patra.starter.expr.compiler` |
| 29 | `patra.expr.compiler.registry-api` | `patra.starter.expr.compiler.registry-api` |
| 30 | `patra.provenance` | `patra.starter.provenance` |

### 反向白名单（绝对不能动）

| 模式 | 原因 |
|---|---|
| `patra.catalog.*`、`patra.catalog-*` | 业务 prefix |
| `patra.ingest.*`、`patra.ingest.outbox.*` | 业务 prefix |
| `dev.linqibin.patra.*` | Java 包名 |
| `patra-{registry,ingest,catalog,gateway,common,object-storage,expr-kernel,spring-boot-starter-*}` | artifact ID |
| `com.patra`（出现在 spec/plan/report） | 历史档案 |

---

## Task 1：预飞行检查 + 基线测试

**Files:**
- Read-only: 仓库当前状态

- [ ] **Step 1.1：确认分支**

Run: `git rev-parse --abbrev-ref HEAD`
Expected: `refactor/rename-config-prefix`

如果不是，先 `git checkout refactor/rename-config-prefix`。

- [ ] **Step 1.2：确认工作树干净**

Run: `git status --porcelain`
Expected: 空输出（无未提交修改）

- [ ] **Step 1.3：记录 baseline 测试结果**

Run: `./gradlew test 2>&1 | tee /tmp/rename-config-baseline-test.log | tail -50`
Expected: BUILD SUCCESSFUL；记录通过的 test 数量到 `/tmp/rename-config-baseline-test.log`。

后续任务结束时对比 `tail -50 /tmp/rename-config-baseline-test.log` 与新的测试结果，确保测试数量未减少（防止误删测试）。

如果 baseline 已有 failing tests，本次重构**不应**新引入 failure；记录 baseline failure 数作为对比基准。

- [ ] **Step 1.4：本任务不提交**

本任务只读，不修改文件，跳过 commit。

---

## Task 2：替换 @ConfigurationProperties prefix（13 个类，13 行）

**Files (Modify):**
- `linqibin-spring-boot-starter-batch/src/main/java/dev/linqibin/starter/batch/config/BatchProperties.java:28`
- `linqibin-spring-boot-starter-core/src/main/java/dev/linqibin/starter/core/async/AsyncProperties.java:44`
- `linqibin-spring-boot-starter-core/src/main/java/dev/linqibin/starter/core/cqrs/CommandBusProperties.java:21`
- `linqibin-spring-boot-starter-core/src/main/java/dev/linqibin/starter/core/error/config/ErrorProperties.java:16`
- `linqibin-spring-boot-starter-core/src/main/java/dev/linqibin/starter/core/error/config/TracingProperties.java:16`
- `linqibin-spring-boot-starter-http-interface/src/main/java/dev/linqibin/starter/httpinterface/config/HttpInterfaceProperties.java:37`
- `linqibin-spring-boot-starter-object-storage/src/main/java/dev/linqibin/starter/objectstorage/ObjectStorageProperties.java:9`
- `linqibin-spring-boot-starter-observability/src/main/java/dev/linqibin/starter/observability/config/ObservabilityProperties.java:20`
- `linqibin-spring-boot-starter-openapi/src/main/java/dev/linqibin/starter/openapi/config/OpenApiProperties.java:17`
- `linqibin-spring-boot-starter-redisson/src/main/java/dev/linqibin/starter/redisson/config/RedissonProperties.java:13`
- `linqibin-spring-boot-starter-rest-client/src/main/java/dev/linqibin/starter/restclient/config/RestClientProperties.java:36`
- `linqibin-spring-boot-starter-rest-client/src/main/java/dev/linqibin/starter/restclient/config/DownloadProperties.java:16`
- `linqibin-spring-boot-starter-web/src/main/java/dev/linqibin/starter/web/error/config/WebErrorProperties.java:12`
- `patra-spring-boot-starter-expr/src/main/java/dev/linqibin/patra/starter/expr/compiler/boot/CompilerProperties.java:44`
- `patra-spring-boot-starter-expr/src/main/java/dev/linqibin/patra/starter/expr/compiler/boot/ExprModeProperties.java`（查找：`@ConfigurationProperties(prefix = "expr")`）
- `patra-spring-boot-starter-provenance/src/main/java/dev/linqibin/patra/starter/provenance/boot/ProvenanceProperties.java:27`

- [ ] **Step 2.1：BatchProperties**

Edit `linqibin-spring-boot-starter-batch/src/main/java/dev/linqibin/starter/batch/config/BatchProperties.java`:
- old: `@ConfigurationProperties(prefix = "patra.batch")`
- new: `@ConfigurationProperties(prefix = "linqibin.starter.batch")`

- [ ] **Step 2.2：AsyncProperties**

Edit `linqibin-spring-boot-starter-core/src/main/java/dev/linqibin/starter/core/async/AsyncProperties.java`:
- old: `@ConfigurationProperties(prefix = "patra.async")`
- new: `@ConfigurationProperties(prefix = "linqibin.starter.core.async")`

- [ ] **Step 2.3：CommandBusProperties**

Edit `linqibin-spring-boot-starter-core/src/main/java/dev/linqibin/starter/core/cqrs/CommandBusProperties.java`:
- old: `@ConfigurationProperties(prefix = "patra.command-bus")`
- new: `@ConfigurationProperties(prefix = "linqibin.starter.core.command-bus")`

- [ ] **Step 2.4：ErrorProperties**

Edit `linqibin-spring-boot-starter-core/src/main/java/dev/linqibin/starter/core/error/config/ErrorProperties.java`:
- old: `@ConfigurationProperties(prefix = "patra.error")`
- new: `@ConfigurationProperties(prefix = "linqibin.starter.core.error")`

- [ ] **Step 2.5：TracingProperties**

Edit `linqibin-spring-boot-starter-core/src/main/java/dev/linqibin/starter/core/error/config/TracingProperties.java`:
- old: `@ConfigurationProperties(prefix = "patra.tracing")`
- new: `@ConfigurationProperties(prefix = "linqibin.starter.core.tracing")`

- [ ] **Step 2.6：HttpInterfaceProperties**

Edit `linqibin-spring-boot-starter-http-interface/src/main/java/dev/linqibin/starter/httpinterface/config/HttpInterfaceProperties.java`:
- old: `@ConfigurationProperties(prefix = "patra.http.interface")`
- new: `@ConfigurationProperties(prefix = "linqibin.starter.http-interface")`

- [ ] **Step 2.7：ObjectStorageProperties（短语法）**

Edit `linqibin-spring-boot-starter-object-storage/src/main/java/dev/linqibin/starter/objectstorage/ObjectStorageProperties.java`:
- old: `@ConfigurationProperties("patra.object-storage")`
- new: `@ConfigurationProperties("linqibin.starter.object-storage")`

注：此类用短语法（无 `prefix =`），Edit 时要精确匹配。

- [ ] **Step 2.8：ObservabilityProperties**

Edit `linqibin-spring-boot-starter-observability/src/main/java/dev/linqibin/starter/observability/config/ObservabilityProperties.java`:
- old: `@ConfigurationProperties(prefix = "patra.observability")`
- new: `@ConfigurationProperties(prefix = "linqibin.starter.observability")`

- [ ] **Step 2.9：OpenApiProperties**

Edit `linqibin-spring-boot-starter-openapi/src/main/java/dev/linqibin/starter/openapi/config/OpenApiProperties.java`:
- old: `@ConfigurationProperties(prefix = "patra.openapi")`
- new: `@ConfigurationProperties(prefix = "linqibin.starter.openapi")`

- [ ] **Step 2.10：RedissonProperties**

Edit `linqibin-spring-boot-starter-redisson/src/main/java/dev/linqibin/starter/redisson/config/RedissonProperties.java`:
- old: `@ConfigurationProperties(prefix = "patra.redisson")`
- new: `@ConfigurationProperties(prefix = "linqibin.starter.redisson")`

- [ ] **Step 2.11：RestClientProperties**

Edit `linqibin-spring-boot-starter-rest-client/src/main/java/dev/linqibin/starter/restclient/config/RestClientProperties.java`:
- old: `@ConfigurationProperties(prefix = "patra.rest-client")`
- new: `@ConfigurationProperties(prefix = "linqibin.starter.rest-client")`

- [ ] **Step 2.12：DownloadProperties**

Edit `linqibin-spring-boot-starter-rest-client/src/main/java/dev/linqibin/starter/restclient/config/DownloadProperties.java`:
- old: `@ConfigurationProperties(prefix = "patra.rest-client.download")`
- new: `@ConfigurationProperties(prefix = "linqibin.starter.rest-client.download")`

- [ ] **Step 2.13：WebErrorProperties**

Edit `linqibin-spring-boot-starter-web/src/main/java/dev/linqibin/starter/web/error/config/WebErrorProperties.java`:
- old: `@ConfigurationProperties(prefix = "patra.web.problem")`
- new: `@ConfigurationProperties(prefix = "linqibin.starter.web.problem")`

- [ ] **Step 2.14：CompilerProperties（patra 专用）**

Edit `patra-spring-boot-starter-expr/src/main/java/dev/linqibin/patra/starter/expr/compiler/boot/CompilerProperties.java`:
- old: `@ConfigurationProperties(prefix = "patra.expr.compiler")`
- new: `@ConfigurationProperties(prefix = "patra.starter.expr.compiler")`

- [ ] **Step 2.15：ExprModeProperties（patra 专用）**

先 Read `patra-spring-boot-starter-expr/src/main/java/dev/linqibin/patra/starter/expr/compiler/boot/ExprModeProperties.java` 确认完整 `@ConfigurationProperties` 字符串。

Edit:
- old: `@ConfigurationProperties(prefix = "expr")`
- new: `@ConfigurationProperties(prefix = "patra.starter.expr.mode")`

注：原 prefix 是 standalone `"expr"`，新加 `patra.starter.` 前缀 + `.mode` 后缀（因属"mode 子配置"语义，见 spec § 3.2）。

- [ ] **Step 2.16：ProvenanceProperties（patra 专用）**

Edit `patra-spring-boot-starter-provenance/src/main/java/dev/linqibin/patra/starter/provenance/boot/ProvenanceProperties.java`:
- old: `@ConfigurationProperties(prefix = "patra.provenance")`
- new: `@ConfigurationProperties(prefix = "patra.starter.provenance")`

- [ ] **Step 2.17：验证（编译 + 字符串残留扫描）**

Run: `./gradlew compileJava --offline 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

Run: `grep -rn '@ConfigurationProperties.*"patra\.' --include="*.java" .`
Expected: 零结果

Run: `grep -rn '@ConfigurationProperties(prefix = "expr"' --include="*.java" .`
Expected: 零结果

- [ ] **Step 2.18：本任务不提交**

继续到 Task 3。所有 Java 修改累积到最终 commit。

---

## Task 3：替换 @ConditionalOnProperty prefix（22 个文件，23 处用点）

**Files (Modify):**
- `linqibin-spring-boot-starter-batch/src/main/java/dev/linqibin/starter/batch/autoconfigure/BatchAutoConfiguration.java:46`
- `linqibin-spring-boot-starter-batch/src/main/java/dev/linqibin/starter/batch/autoconfigure/BatchProgressMetricsAutoConfiguration.java:57`
- `linqibin-spring-boot-starter-batch/src/main/java/dev/linqibin/starter/batch/autoconfigure/BatchSchemaInitializerConfiguration.java:41`
- `linqibin-spring-boot-starter-batch/src/main/java/dev/linqibin/starter/batch/autoconfigure/BatchDataSourceConfiguration.java:48`
- `linqibin-spring-boot-starter-core/src/main/java/dev/linqibin/starter/core/async/AsyncAutoConfiguration.java:60`
- `linqibin-spring-boot-starter-core/src/main/java/dev/linqibin/starter/core/cqrs/interceptor/TracingCommandInterceptor.java:43`
- `linqibin-spring-boot-starter-core/src/main/java/dev/linqibin/starter/core/cqrs/interceptor/MetricsCommandInterceptor.java:35`
- `linqibin-spring-boot-starter-core/src/main/java/dev/linqibin/starter/core/cqrs/interceptor/LoggingCommandInterceptor.java:30`
- `linqibin-spring-boot-starter-core/src/main/java/dev/linqibin/starter/core/error/config/CoreErrorAutoConfiguration.java:42`
- `linqibin-spring-boot-starter-core/src/main/java/dev/linqibin/starter/core/error/config/CircuitBreakerErrorAutoConfiguration.java:53`
- `linqibin-spring-boot-starter-http-interface/src/main/java/dev/linqibin/starter/httpinterface/config/HttpInterfaceAutoConfiguration.java:57`
- `linqibin-spring-boot-starter-object-storage/src/main/java/dev/linqibin/starter/objectstorage/ObjectStorageAutoConfiguration.java:110`
- `linqibin-spring-boot-starter-object-storage/src/main/java/dev/linqibin/starter/objectstorage/S3StorageAutoConfiguration.java:64`
- `linqibin-spring-boot-starter-observability/src/main/java/dev/linqibin/starter/observability/autoconfigure/ObservabilityAutoConfiguration.java:32`
- `linqibin-spring-boot-starter-observability/src/main/java/dev/linqibin/starter/observability/autoconfigure/ObservationInterceptorsAutoConfiguration.java:28`
- `linqibin-spring-boot-starter-observability/src/main/java/dev/linqibin/starter/observability/autoconfigure/MicrometerAutoConfiguration.java:47`
- `linqibin-spring-boot-starter-openapi/src/main/java/dev/linqibin/starter/openapi/autoconfigure/OpenApiAutoConfiguration.java:31`
- `linqibin-spring-boot-starter-redisson/src/main/java/dev/linqibin/starter/redisson/autoconfigure/RedissonAutoConfiguration.java:25`
- `linqibin-spring-boot-starter-redisson/src/main/java/dev/linqibin/starter/redisson/autoconfigure/LockAutoConfiguration.java:31`
- `linqibin-spring-boot-starter-rest-client/src/main/java/dev/linqibin/starter/restclient/config/RestClientAutoConfiguration.java:46, 188`
- `linqibin-spring-boot-starter-rest-client/src/main/java/dev/linqibin/starter/restclient/config/TunnelProxyAutoConfiguration.java:37`
- `linqibin-spring-boot-starter-rest-client/src/main/java/dev/linqibin/starter/restclient/config/StreamingWebClientAutoConfiguration.java:64`
- `linqibin-spring-boot-starter-rest-client/src/main/java/dev/linqibin/starter/restclient/config/DownloadClientAutoConfiguration.java:44, 94, 107`
- `linqibin-spring-boot-starter-web/src/main/java/dev/linqibin/starter/web/error/config/WebErrorAutoConfiguration.java:50`
- `patra-spring-boot-starter-expr/src/main/java/dev/linqibin/patra/starter/expr/compiler/boot/ExprCompilerAutoConfiguration.java:72, 94, 167`
- `patra-spring-boot-starter-provenance/src/main/java/dev/linqibin/patra/starter/provenance/boot/ProvenanceAutoConfiguration.java:48`

**通用替换规则**（Edit 时严格按映射表）：
- `prefix = "patra.batch"` → `prefix = "linqibin.starter.batch"`
- `prefix = "patra.batch.metrics"` → `prefix = "linqibin.starter.batch.metrics"`
- `prefix = "patra.batch.schema"` → `prefix = "linqibin.starter.batch.schema"`
- `prefix = "patra.batch.datasource"` → `prefix = "linqibin.starter.batch.datasource"`
- `prefix = "patra.async"` → `prefix = "linqibin.starter.core.async"`
- `prefix = "patra.command-bus.interceptors"` → `prefix = "linqibin.starter.core.command-bus.interceptors"`
- `prefix = "patra.error"` → `prefix = "linqibin.starter.core.error"`
- `prefix = "patra.error.circuit-breaker"` → `prefix = "linqibin.starter.core.error.circuit-breaker"`
- `prefix = "patra.http.interface"` → `prefix = "linqibin.starter.http-interface"`
- `prefix = "patra.object-storage"` → `prefix = "linqibin.starter.object-storage"`
- `prefix = "patra.observability"` → `prefix = "linqibin.starter.observability"`
- `prefix = "patra.observability.metrics"` → `prefix = "linqibin.starter.observability.metrics"`
- `prefix = "patra.openapi"` → `prefix = "linqibin.starter.openapi"`
- `prefix = "patra.redisson"` → `prefix = "linqibin.starter.redisson"`
- `prefix = "patra.redisson.lock"` → `prefix = "linqibin.starter.redisson.lock"`
- `prefix = "patra.rest-client"` → `prefix = "linqibin.starter.rest-client"`
- `prefix = "patra.rest-client.download"` → `prefix = "linqibin.starter.rest-client.download"`
- `prefix = "patra.rest-client.download.ftp"` → `prefix = "linqibin.starter.rest-client.download.ftp"`
- `prefix = "patra.rest-client.streaming"` → `prefix = "linqibin.starter.rest-client.streaming"`
- `prefix = "patra.rest-client.proxy.tunnel"` → `prefix = "linqibin.starter.rest-client.proxy.tunnel"`
- `prefix = "patra.rest-client.interceptors.logging"` → `prefix = "linqibin.starter.rest-client.interceptors.logging"`
- `prefix = "patra.web.problem"` → `prefix = "linqibin.starter.web.problem"`
- `prefix = "patra.expr.compiler"` → `prefix = "patra.starter.expr.compiler"`
- `prefix = "patra.expr.compiler.registry-api"` → `prefix = "patra.starter.expr.compiler.registry-api"`
- `prefix = "patra.provenance"` → `prefix = "patra.starter.provenance"`

- [ ] **Step 3.1：逐文件 Edit**

对每个 Files 列表中的文件，Read → 找到 `@ConditionalOnProperty(prefix = "patra...")` 行 → 按上面通用替换规则 Edit。

**注意事项**：
1. 多个文件有多行需改（如 `RestClientAutoConfiguration.java:46,188` 各一行；`DownloadClientAutoConfiguration.java:44,94,107` 各一行；`ExprCompilerAutoConfiguration.java:72,94,167` 各一行）—— 全部改完
2. 只动 `prefix = "..."` 部分，不要动 `name = "..."` 或 `matchIfMissing = ...`
3. Edit 时建议带上下文（前后各 1-2 行）以确保 old_string 唯一性

- [ ] **Step 3.2：验证（编译 + 字符串残留扫描）**

Run: `./gradlew compileJava --offline 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

Run: `grep -rn '@ConditionalOnProperty.*prefix.*"patra\.' --include="*.java" .`
Expected: 零结果

Run: `grep -rn 'prefix = "patra\.' --include="*.java" .`
Expected: 零结果（已完成 @ConfigurationProperties + @ConditionalOnProperty 全部）

- [ ] **Step 3.3：本任务不提交**

继续到 Task 4。

---

## Task 4：替换 Java 硬编码字符串（5 文件，14 处）

**Files (Modify):**
- `linqibin-spring-boot-starter-rest-client/src/main/java/dev/linqibin/starter/restclient/config/LongRunningClientEnabledCondition.java:17`
- `linqibin-spring-boot-starter-core/src/main/java/dev/linqibin/starter/core/error/config/CoreErrorAutoConfiguration.java:76`
- `linqibin-spring-boot-starter-rest-client/src/main/java/dev/linqibin/starter/restclient/config/TunnelProxyAutoConfiguration.java:51-54`
- `linqibin-spring-boot-starter-test/src/main/java/dev/linqibin/starter/test/container/initializer/MinIOContainerInitializer.java:196-199`
- `linqibin-spring-boot-starter-object-storage/src/main/java/dev/linqibin/starter/objectstorage/metrics/ObjectStorageMetrics.java:14-30`（涵盖 Task 5）

- [ ] **Step 4.1：LongRunningClientEnabledCondition.java**

Edit `linqibin-spring-boot-starter-rest-client/src/main/java/dev/linqibin/starter/restclient/config/LongRunningClientEnabledCondition.java`:
- old: `private static final String ENABLED_PROPERTY = "patra.rest-client.clients.long-running.enabled";`
- new: `private static final String ENABLED_PROPERTY = "linqibin.starter.rest-client.clients.long-running.enabled";`

- [ ] **Step 4.2：CoreErrorAutoConfiguration.java（日志提示字符串）**

Edit `linqibin-spring-boot-starter-core/src/main/java/dev/linqibin/starter/core/error/config/CoreErrorAutoConfiguration.java`:
- old: `log.warn("patra.error.context-prefix 未配置,使用 UNKNOWN 作为统一错误前缀");`
- new: `log.warn("linqibin.starter.core.error.context-prefix 未配置,使用 UNKNOWN 作为统一错误前缀");`

- [ ] **Step 4.3：TunnelProxyAutoConfiguration.java（4 个 Assert 提示）**

Edit `linqibin-spring-boot-starter-rest-client/src/main/java/dev/linqibin/starter/restclient/config/TunnelProxyAutoConfiguration.java` — 改 4 行 Assert 字符串：

- old: `Assert.hasText(tunnel.getHost(), "patra.rest-client.proxy.tunnel.host 未配置");`
- new: `Assert.hasText(tunnel.getHost(), "linqibin.starter.rest-client.proxy.tunnel.host 未配置");`

- old: `Assert.isTrue(tunnel.getPort() > 0, "patra.rest-client.proxy.tunnel.port 必须大于 0");`
- new: `Assert.isTrue(tunnel.getPort() > 0, "linqibin.starter.rest-client.proxy.tunnel.port 必须大于 0");`

- old: `Assert.hasText(tunnel.getAuthKey(), "patra.rest-client.proxy.tunnel.auth-key 未配置");`
- new: `Assert.hasText(tunnel.getAuthKey(), "linqibin.starter.rest-client.proxy.tunnel.auth-key 未配置");`

- old: `Assert.hasText(tunnel.getAuthPwd(), "patra.rest-client.proxy.tunnel.auth-pwd 未配置");`
- new: `Assert.hasText(tunnel.getAuthPwd(), "linqibin.starter.rest-client.proxy.tunnel.auth-pwd 未配置");`

- [ ] **Step 4.4：MinIOContainerInitializer.java（4 个测试容器属性字符串）**

Edit `linqibin-spring-boot-starter-test/src/main/java/dev/linqibin/starter/test/container/initializer/MinIOContainerInitializer.java`:

Read 上下文：lines 195-200 应是 `TestPropertyValues.of(...)` 调用。

- old: `"patra.object-storage.active-provider=minio",`
- new: `"linqibin.starter.object-storage.active-provider=minio",`

- old: `"patra.object-storage.providers.minio.endpoint=" + minio.getS3URL(),`
- new: `"linqibin.starter.object-storage.providers.minio.endpoint=" + minio.getS3URL(),`

- old: `"patra.object-storage.providers.minio.access-key=" + minio.getUserName(),`
- new: `"linqibin.starter.object-storage.providers.minio.access-key=" + minio.getUserName(),`

- old: `"patra.object-storage.providers.minio.secret-key=" + minio.getPassword())`
- new: `"linqibin.starter.object-storage.providers.minio.secret-key=" + minio.getPassword())`

- [ ] **Step 4.5：验证**

Run: `./gradlew compileJava --offline 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

Run: `grep -rn '"patra\.\(rest-client\|object-storage\|error\)' --include="*.java" linqibin-spring-boot-starter-rest-client/ linqibin-spring-boot-starter-core/ linqibin-spring-boot-starter-test/ linqibin-spring-boot-starter-object-storage/`
Expected: 零结果（除 ObjectStorageMetrics 还未改，待 Task 5）

---

## Task 5：替换 Micrometer 指标常量 + JavaDoc（1 文件，12 处）

**Files (Modify):**
- `linqibin-spring-boot-starter-object-storage/src/main/java/dev/linqibin/starter/objectstorage/metrics/ObjectStorageMetrics.java`

- [ ] **Step 5.1：替换 7 个 metric 常量字符串（lines 24-30）**

Edit `linqibin-spring-boot-starter-object-storage/src/main/java/dev/linqibin/starter/objectstorage/metrics/ObjectStorageMetrics.java`:

- old: `private static final String UPLOAD_TOTAL = "patra.object_storage.upload.total";`
- new: `private static final String UPLOAD_TOTAL = "linqibin.starter.object_storage.upload.total";`

- old: `private static final String UPLOAD_DURATION = "patra.object_storage.upload.duration";`
- new: `private static final String UPLOAD_DURATION = "linqibin.starter.object_storage.upload.duration";`

- old: `private static final String UPLOAD_SIZE = "patra.object_storage.upload.size";`
- new: `private static final String UPLOAD_SIZE = "linqibin.starter.object_storage.upload.size";`

- old: `private static final String DOWNLOAD_TOTAL = "patra.object_storage.download.total";`
- new: `private static final String DOWNLOAD_TOTAL = "linqibin.starter.object_storage.download.total";`

- old: `private static final String DOWNLOAD_DURATION = "patra.object_storage.download.duration";`
- new: `private static final String DOWNLOAD_DURATION = "linqibin.starter.object_storage.download.duration";`

- old: `private static final String DOWNLOAD_SIZE = "patra.object_storage.download.size";`
- new: `private static final String DOWNLOAD_SIZE = "linqibin.starter.object_storage.download.size";`

- old: `private static final String RETRY_COUNT = "patra.object_storage.retry.count";`
- new: `private static final String RETRY_COUNT = "linqibin.starter.object_storage.retry.count";`

- [ ] **Step 5.2：替换 5 行 JavaDoc 注释（lines 14-18）**

Edit 同文件 5 个 `///` 注释行：

- old: `/// - `patra.object_storage.upload.total` - 上传总数(成功/失败)`
- new: `/// - `linqibin.starter.object_storage.upload.total` - 上传总数(成功/失败)`

- old: `///   - `patra.object_storage.upload.duration` - 上传时长分布`
- new: `///   - `linqibin.starter.object_storage.upload.duration` - 上传时长分布`

- old: `///   - `patra.object_storage.upload.size` - 上传文件大小分布`
- new: `///   - `linqibin.starter.object_storage.upload.size` - 上传文件大小分布`

- old: `///   - `patra.object_storage.download.total` - 下载总数`
- new: `///   - `linqibin.starter.object_storage.download.total` - 下载总数`

- old: `///   - `patra.object_storage.retry.count` - 重试次数`
- new: `///   - `linqibin.starter.object_storage.retry.count` - 重试次数`

- [ ] **Step 5.3：验证**

Run: `./gradlew :linqibin-spring-boot-starter-object-storage:compileJava --offline`
Expected: BUILD SUCCESSFUL

Run: `grep -n "patra\." linqibin-spring-boot-starter-object-storage/src/main/java/dev/linqibin/starter/objectstorage/metrics/ObjectStorageMetrics.java`
Expected: 零结果

---

## Task 6：编译 + Java 层全量检查 checkpoint

- [ ] **Step 6.1：全量 compileJava**

Run: `./gradlew compileJava 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6.2：Java 层零残留 grep**

Run（主 grep — 严格白名单已排除）：
```bash
grep -rnE "(^|[^a-zA-Z0-9_])patra\.(batch|async|command-bus|error|tracing|http\.interface|object-storage|object_storage|observability|openapi|redisson|rest-client|web\.problem|expr|provenance)\b" \
  --include="*.java" .
```
Expected: 零结果

Run（补充 grep — 旧 standalone `expr` prefix）：
```bash
grep -rn 'prefix = "expr"' --include="*.java" .
```
Expected: 零结果

如有残留，回头检查 Task 2-5 是否漏改。

注：如果 boot 模块（如 `patra-catalog-boot`）的 Java 代码中有 `@Value("${patra.…}")` 或 `Environment.getProperty("patra.…")` 引用，会在主 grep 中显出来——需要同步改。这些不在 spec 已知清单内，但本步骤兜底捕获。

- [ ] **Step 6.3：本任务不提交**

Java 层完成，继续到配置文件层。

---

## Task 7：重构 yml/properties 配置文件（14 文件）

yml 文件使用 nested 结构（如 `patra:\n  batch:\n    enabled: true`）。重构涉及：
1. 业务 prefix `patra.catalog.*`、`patra.ingest.*` 等保留在 `patra:` 块下
2. 通用 starter prefix 从 `patra:` 块下移到新的 `linqibin:` 块下的 `starter:` 子树
3. patra 专用 starter（expr / provenance）从 `patra:` 直接子键移到 `patra:` → `starter:` 子树

**Files (Modify):**
- `patra-catalog/patra-catalog-boot/src/main/resources/application.yml`
- `patra-catalog/patra-catalog-boot/src/main/resources/application-dev.yml`
- `patra-catalog/patra-catalog-boot/src/main/resources/application-prod.yml`
- `patra-catalog/patra-catalog-boot/src/main/resources/catalog-error-config.yaml`
- `patra-catalog/patra-catalog-boot/src/test/resources/application-e2e-test.yml`
- `patra-ingest/patra-ingest-boot/src/main/resources/application.yml`
- `patra-ingest/patra-ingest-boot/src/main/resources/application-dev.yml`
- `patra-ingest/patra-ingest-boot/src/main/resources/application-prod.yml`
- `patra-ingest/patra-ingest-boot/src/main/resources/ingest-error-config.yaml`
- `patra-ingest/patra-ingest-boot/src/test/resources/application-e2e-test.yml`
- `patra-ingest/patra-ingest-boot/src/test/resources/application-test-common.yml`
- `patra-registry/patra-registry-boot/src/main/resources/registry-error-config.yaml`
- `linqibin-spring-boot-starter-batch/src/test/resources/application-test.yml`
- `patra-spring-boot-starter-expr/src/main/resources/application-expr-reference.yml`

- [ ] **Step 7.1：处理每个 yml 文件**

对每个文件，按以下流程：

1. Read 整个文件
2. 定位 `patra:` 根块
3. 识别每个 `patra:` 直接子键是 **业务**（catalog / ingest）还是 **starter**（其他全部）
4. Edit：
   - 业务子键保留在 `patra:` 下不动
   - 11 通用 starter 子键移到新增的 `linqibin:` → `starter:` 子树下
   - 2 patra 专用 starter（expr / provenance）改为 `patra:` → `starter:` → `{expr,provenance}:` 子树

**Edit 模板示例**（catalog application.yml 部分）：

读到的原内容（line 67 起）：
```yaml
patra:
  rest-client:
    proxy:
      tunnel:
        host: ${PROXY_HOST:127.0.0.1}
        port: ${PROXY_PORT:7890}
        # ...
  http:
    interface:
      enabled: true
      # ...
  catalog:
    # business config
```

改后：
```yaml
patra:
  catalog:
    # business config
  starter:
    # (此处暂无 patra-专用 starter 子键)
linqibin:
  starter:
    rest-client:
      proxy:
        tunnel:
          host: ${PROXY_HOST:127.0.0.1}
          port: ${PROXY_PORT:7890}
          # ...
    http-interface:
      enabled: true
      # ...
```

注意点：
- 业务 prefix 与 starter prefix **物理分块** 放在不同 root（`patra:` vs `linqibin:`）
- 缩进重新对齐——`linqibin:` 下的 `starter:` 下的子键比原来多 1 层（2 spaces）
- `http.interface` 改名为 `http-interface`（kebab-case 单段，见 spec § 2.2）

**Edit 策略：每个 yml 文件做一次完整块替换**（一个 Edit 操作覆盖整个 `patra:` 块到第一个其他 root key 之间）。如果 Edit 太大，分成 2 个 Edit：先删除原 starter 块，再插入新的 `linqibin:` 块。

- [ ] **Step 7.2：处理 catalog-error-config.yaml / ingest-error-config.yaml / registry-error-config.yaml**

这 3 个文件结构特殊——主要内容是 `patra: error: …`。Read 后按相同原则：业务 error code 保留 在 `patra:`，starter error 配置改到 `linqibin:` → `starter:` → `core:` → `error:`。

Read 文件后判断具体哪些是 starter 错误码配置（如 `patra.error.context-prefix`、`patra.error.translations` 等是 starter）vs 业务错误码列表（业务列表通常是 `patra.error.codes` 这种数组形式，可能也是 starter — Read 后再决定）。

如不确定，**默认全部视为 starter 配置移到 `linqibin.starter.core.error.*`**——`linqibin-spring-boot-starter-core` 的 ErrorProperties 的 prefix 就是 `linqibin.starter.core.error`。

- [ ] **Step 7.3：处理 patra-spring-boot-starter-expr 的 application-expr-reference.yml**

Read 文件后定位 `expr:` 块（standalone，不在 `patra:` 下）和 `patra.expr.compiler:` 块。

改造：
- standalone `expr:` 子树 → `patra: starter: expr: mode:` 子树
- `patra: expr: compiler:` → `patra: starter: expr: compiler:`

- [ ] **Step 7.4：处理 linqibin-spring-boot-starter-batch 的 test application-test.yml**

Read 后定位 `patra: batch:` 子树，改为 `linqibin: starter: batch:`。如果还有其他 `patra.*` starter 子键，按同规则迁移。

- [ ] **Step 7.5：验证（compile + yml 残留）**

Run: `./gradlew compileJava --offline 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL（yml 不参与编译，但 sanity check）

Run:
```bash
grep -rnE "(^|[^a-zA-Z0-9_])patra\.(batch|async|command-bus|error|tracing|http\.interface|object-storage|observability|openapi|redisson|rest-client|web\.problem)\b" \
  --include="*.yml" --include="*.yaml" --include="*.properties" .
```
Expected: 零结果（业务 `patra.catalog.*`、`patra.ingest.*`、`patra.expr.*`、`patra.provenance.*` 不在上面 grep 模式内，仍可能出现 — 这些已在 starter prefix 表里覆盖）

Run（检查 patra-专用 starter）：
```bash
grep -rnE "(^|[^a-zA-Z0-9_])patra\.(expr|provenance)\b" \
  --include="*.yml" --include="*.yaml" .
```
Expected: 零结果（已迁移到 `patra.starter.{expr,provenance}.*`）

Run（旧 standalone `expr:`）：
```bash
grep -rnE "^expr:" --include="*.yml" --include="*.yaml" .
```
Expected: 零结果

- [ ] **Step 7.6：本任务不提交**

继续到 JSON metadata。

---

## Task 8：更新 spring-configuration-metadata.json（2 文件，11 keys）

**Files (Modify):**
- `linqibin-spring-boot-starter-observability/src/main/resources/META-INF/additional-spring-configuration-metadata.json`
- `linqibin-spring-boot-starter-core/src/main/resources/META-INF/additional-spring-configuration-metadata.json`

- [ ] **Step 8.1：observability metadata.json**

Read `linqibin-spring-boot-starter-observability/src/main/resources/META-INF/additional-spring-configuration-metadata.json` 完整内容。

Edit 所有出现的 `"name": "patra.observability...` 字段，将值中的 `patra.observability` 替换为 `linqibin.starter.observability`。

注意：JSON 字段名也可能在 `properties:`、`hints:`、`groups:` 三类下出现；需要全部替换。

如果 Edit 时 old_string 有歧义（多处相同），可用 `replace_all = true`，因为 JSON 内 `patra.observability` 出现的位置都需要改。

Run after edit: `python3 -m json.tool linqibin-spring-boot-starter-observability/src/main/resources/META-INF/additional-spring-configuration-metadata.json > /dev/null`
Expected: 静默退出（JSON 合法）

- [ ] **Step 8.2：core metadata.json**

Read `linqibin-spring-boot-starter-core/src/main/resources/META-INF/additional-spring-configuration-metadata.json` 完整内容。

Edit：
- `"name": "patra.error.enabled"` → `"name": "linqibin.starter.core.error.enabled"`
- `"name": "patra.error.context-prefix"` → `"name": "linqibin.starter.core.error.context-prefix"`
- `"name": "patra.tracing.header-names"` → `"name": "linqibin.starter.core.tracing.header-names"`

Run after edit: `python3 -m json.tool linqibin-spring-boot-starter-core/src/main/resources/META-INF/additional-spring-configuration-metadata.json > /dev/null`
Expected: 静默退出（JSON 合法）

- [ ] **Step 8.3：验证**

Run:
```bash
grep -rn "patra\." --include="*.json" linqibin-spring-boot-starter-*/src/main/resources/META-INF/ patra-spring-boot-starter-*/src/main/resources/META-INF/
```
Expected: 零结果

- [ ] **Step 8.4：本任务不提交**

继续到 README 文档。

---

## Task 9：更新 README 文档（9 文件）

**Files (Modify):**
- `linqibin-spring-boot-starter-redisson/README.md`
- `linqibin-spring-boot-starter-observability/README.md`
- `linqibin-spring-boot-starter-core/README.md`
- `linqibin-spring-boot-starter-batch/README.md`
- `linqibin-spring-boot-starter-rest-client/README.md`
- `linqibin-spring-boot-starter-openapi/README.md`
- `linqibin-spring-boot-starter-web/README.md`
- `linqibin-spring-boot-starter-object-storage/README.md`
- `patra-object-storage/README.md`

- [ ] **Step 9.1：逐文件 Edit**

对每个 README 文件：

1. Read 完整文件
2. 找到所有 `patra.{batch|async|command-bus|error|tracing|http.interface|object-storage|object_storage|observability|openapi|redisson|rest-client|web.problem}` 配置示例
3. 按映射表 Edit（建议每个文件用 `replace_all = true` 对每个旧 prefix 全文档替换）
4. 同时检查 `http.interface` → `http-interface`（这是顺手 dot→hyphen 修正）

**示例**（`linqibin-spring-boot-starter-core/README.md` L287-293 可能是这种格式）：

```markdown
```yaml
patra:
  async:
    core-pool-size: 8
    max-pool-size: 32
```
```

改后：

```markdown
```yaml
linqibin:
  starter:
    core:
      async:
        core-pool-size: 8
        max-pool-size: 32
```
```

注：README 示例代码块中的 yml 结构也按 § 7 同样原则改。

- [ ] **Step 9.2：验证**

Run:
```bash
grep -rnE "patra\.(batch|async|command-bus|error|tracing|http\.interface|object-storage|object_storage|observability|openapi|redisson|rest-client|web\.problem)\b" \
  --include="README.md" linqibin-spring-boot-starter-*/ patra-spring-boot-starter-*/ patra-object-storage/
```
Expected: 零结果

- [ ] **Step 9.3：本任务不提交**

继续到最终验证。

---

## Task 10：全量 clean build + 终态零残留 grep

- [ ] **Step 10.1：`./gradlew clean build`**

Run: `./gradlew clean build 2>&1 | tail -60`
Expected: BUILD SUCCESSFUL

如果失败，按错误类型处理：
- **编译错**：检查是否漏改某个 prefix 字符串
- **测试错**：对比 baseline `/tmp/rename-config-baseline-test.log`，看是否是 binding-related
  - 如果是新增 binding 失败（如 yml 配置没找到），检查 yml 文件是否漏改
  - 如果是 baseline 已有的失败，不在本次修复范围
- **ArchUnit 错**：本次重构未改包结构，不应触发 ArchUnit；如有触发，是误伤

- [ ] **Step 10.2：终态零残留 grep（主 grep）**

Run:
```bash
grep -rnE "(^|[^a-zA-Z0-9_])patra\.(batch|async|command-bus|error|tracing|http\.interface|object-storage|object_storage|observability|openapi|redisson|rest-client|web\.problem|expr|provenance)\b" \
  --include="*.java" --include="*.yml" --include="*.yaml" \
  --include="*.properties" --include="*.md" --include="*.json" \
  . 2>/dev/null | grep -vE "(docs/patra|\.git/|build/|out/|node_modules/|target/)"
```
Expected: 零结果（注：排除 docs/patra 历史档案、构建产物）

- [ ] **Step 10.3：终态零残留 grep（standalone `expr`）**

Run:
```bash
grep -rn 'prefix = "expr"' --include="*.java" .
grep -rnE '^expr:' --include="*.yml" --include="*.yaml" .
```
Expected: 全部零结果

- [ ] **Step 10.4：业务 prefix 完整性 sanity check**

确认业务 prefix 仍存在（**应有结果**）：

Run:
```bash
grep -rnE "patra\.(catalog|ingest)\b" --include="*.yml" --include="*.yaml" --include="*.java" . | wc -l
```
Expected: 远大于 0（业务 prefix 不动）

如果意外为 0，说明 Task 7/9 误伤了业务 prefix——需要回滚检查。

- [ ] **Step 10.5：本任务不提交**

继续到 boot 启动验证。

---

## Task 11：boot 启动 + /actuator/configprops 验证

- [ ] **Step 11.1：起 patra-catalog-boot 验证（推荐，引用 starter 最多）**

⚠️ 此步骤需依赖本地基础设施（MySQL / Consul / Redis）。如果不便启动 boot，可改用集成测试代替——见 Step 11.3。

Run（前台启动，验证后 Ctrl-C 退出）：
```bash
./gradlew :patra-catalog-boot:bootRun
```
Expected:
- 启动成功，无 `Failed to bind properties under ...` 错误
- 日志看到 starter 自动配置加载

另开一个 terminal：
```bash
curl -s http://localhost:6300/actuator/configprops | jq '.contexts[].beans | keys[] | select(test("linqibin|patra"))' | head -30
```
Expected: 看到 `linqibin.starter.batch-org.springframework.boot.context.properties.ConfigurationProperties` 等 bean key，前缀是 `linqibin.starter.*` / `patra.starter.*`（不是 `patra.*` 旧版本）

- [ ] **Step 11.2：检查 /actuator/configprops 显示新 prefix**

仍在 Step 11.1 的 boot 实例运行时，查询具体 starter 的 properties：

Run:
```bash
curl -s http://localhost:6300/actuator/configprops | jq '.contexts[].beans["linqibin.starter.batch-org.springframework.boot.context.properties.ConfigurationProperties"]'
```
Expected: 返回 BatchProperties 的当前绑定值（如 `chunkSize`、`enabled` 等），prefix 显示 `linqibin.starter.batch`

如果返回 `null` 或者 key 仍是旧的 `patra.batch-...`，说明 BatchProperties 的 `@ConfigurationProperties` 没改成功——回头 Task 2 检查。

- [ ] **Step 11.3：备用 — 跑一个跨 starter 集成测试**

如果 Step 11.1/11.2 不便操作（缺基础设施），用集成测试代替：

Run:
```bash
./gradlew :linqibin-spring-boot-starter-batch:integrationTest --tests "*BatchAutoConfigurationIT" --info 2>&1 | tail -40
```
Expected: BUILD SUCCESSFUL；测试通过证明 BatchProperties 在新 prefix `linqibin.starter.batch.*` 下能正确 bind。

如果该测试本来就不存在或失败，换其他 starter 的集成测试（如 `RedissonAutoConfigurationIT`、`ObservabilityAutoConfigurationIT`），任选 1 个通过即可。

- [ ] **Step 11.4：测试数量回归 check**

Run: `./gradlew test --offline 2>&1 | tail -30 > /tmp/rename-config-final-test.log`

比对 `/tmp/rename-config-baseline-test.log` 与 `/tmp/rename-config-final-test.log` 的"通过测试数量"：
- 通过数量**未减少** → ✅ 合格
- 通过数量减少 → 说明引入了新 failure，需要回头检查
- baseline 已有 failing test 数量未增加 → ✅ 合格

- [ ] **Step 11.5：本任务不提交**

继续最终 commit。

---

## Task 12：单 commit 提交

- [ ] **Step 12.1：检查暂存区与工作树**

Run: `git status`
Expected: 工作树有大量未暂存的修改（Task 2-9 的累积），暂存区空。

- [ ] **Step 12.2：精确暂存（避免误带 build 产物）**

Run:
```bash
# 只暂存源代码与配置文件，排除 build / out / .gradle 等构建产物
git add -A linqibin-spring-boot-starter-*/src/ \
         patra-spring-boot-starter-*/src/ \
         patra-catalog/patra-catalog-boot/src/ \
         patra-ingest/patra-ingest-boot/src/ \
         patra-registry/patra-registry-boot/src/ \
         linqibin-spring-boot-starter-*/README.md \
         patra-object-storage/README.md
```

如果还有其他文件被修改（如 `docs/`），单独添加；不要 `git add -A` 全部，避免误带 IDE 缓存。

- [ ] **Step 12.3：审阅暂存区差异**

Run: `git diff --cached --stat | tail -30`
Expected: 大约 ~70 文件被修改，~100-150 行净变更。

Run: `git diff --cached | head -100`
快速 sanity check 前 100 行 diff 内容看起来正确（应看到 `-prefix = "patra...."`、`+prefix = "linqibin.starter...."` 等）。

- [ ] **Step 12.4：单 commit 提交**

Run:
```bash
git commit -m "$(cat <<'EOF'
refactor(config): rename patra.* config prefix → linqibin.starter.* / patra.starter.*

PR #19 完成报告 Follow-up ii。把 11 个通用 starter 与 2 个 patra 专用 starter
的 Spring 配置 prefix 从 patra.* 统一迁移到与 Java 包名严格对齐的命名：
- 11 通用 starter:  patra.{name}.* → linqibin.starter.{name}.*
- 2 patra 专用 starter: patra.{name}.* → patra.starter.{name}.*
- Micrometer 7 个指标名同步迁移
- 2 个 IDE additional-spring-configuration-metadata.json 同步

涉及 ~70 文件，~100-150 行变更。无 alias / 向后兼容（绿地项目）。

详细映射表与 DoD 见:
- spec: docs/patra/specs/2026-05-16-rename-config-prefix-to-linqibin-design.md
- plan: docs/patra/plans/2026-05-16-rename-config-prefix.md

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```
Expected: pre-commit hook 通过（spotless 跑过），commit 成功。

如果 hook 失败（如 spotless 检查 import 顺序）：
- 阅读 hook 输出
- Fix forward：修复指出的 import 顺序问题
- 再次 `git add` + `git commit`
- ⚠️ **绝不**用 `--no-verify`

- [ ] **Step 12.5：commit 后验证**

Run: `git log --oneline -3`
Expected: 看到刚创建的 commit 在 HEAD。

Run: `git status`
Expected: 工作树干净（`nothing to commit, working tree clean`）。

- [ ] **Step 12.6：（可选）推送到 origin**

⚠️ 仅在用户明确要求时执行。本 plan 默认**不 push**。

如用户要求 push：
```bash
git push -u origin refactor/rename-config-prefix
```

后续创建 PR 走 `gh pr create`，需要用户授权。

---

## 全 plan 完成的 Definition of Done

按 spec § 7.3，逐项 check：

- [ ] § 3 映射矩阵 100% 落地（~70 文件、~150 行变更）
- [ ] `./gradlew clean build` 全绿（Task 10.1）
- [ ] § 7.1 gate 2 grep 零残留（Task 10.2 + Task 10.3）
- [ ] 至少 1 个 boot bootRun 启动成功，`/actuator/configprops` 显示新 prefix（Task 11.1-11.2）—— 或集成测试代替（Task 11.3）
- [ ] 至少 1 个跨 starter 集成测试通过（Task 11.3）
- [ ] Spec 文档（已 commit 094c671f5 + 13ef313fa）、plan 文档（本文件，需 commit）
- [ ] 单 commit 提交（Task 12.4）
- [ ] PR 描述包含 § 3 映射矩阵 + 验证证据

---

## 故障应对手册

### 问题 1：编译错（symbol resolve 失败）

**症状**：`./gradlew compileJava` 报 `cannot find symbol` 之类错误。

**根因**：往往是漏改一个 prefix 字符串导致编译期 String literal 用对，但运行期 binding 用错。但实际上 prefix 字符串变更不会导致编译错——如果编译错出现，说明改错了类型/方法/import，而不是 prefix。

**处理**：
1. 看具体错误信息找到文件
2. `git diff <file>` 看改动
3. 回滚该文件：`git checkout <file>`，重新精确 Edit

### 问题 2：boot 启动 binding 失败

**症状**：boot 启动报 `Failed to bind properties under 'linqibin.starter.batch' to ...`。

**根因**：yml 配置 key 还是旧 prefix，但 @ConfigurationProperties 已经改成新 prefix。

**处理**：
1. 找到具体 boot 的 application*.yml
2. 检查 Task 7 是否漏改这个文件
3. 补改 yml，重启 boot

### 问题 3：grep 仍有 `patra.*` 残留

**症状**：Task 10.2 grep 有非零结果。

**根因**：分情况：
- (a) 业务 prefix（`patra.catalog.*`、`patra.ingest.*`）—— **正常**，应保留
- (b) starter prefix 未改完 —— 需要回头改
- (c) 在 docs/patra/ 历史档案中 —— **正常**，应保留
- (d) 在 build/ 或 out/ 等构建产物中 —— **正常**，会被 clean 清除

**处理**：根据出现位置判断属于哪一种。`grep -vE "..."` 过滤掉正常的，剩下的才是需要修复的。

### 问题 4：测试通过数减少

**症状**：Task 11.4 比对显示通过测试数比 baseline 少。

**根因**：本次重构引入了新的测试失败。

**处理**：
1. `./gradlew test --info 2>&1 | grep -E "FAILED|PASSED" | head -20` 找到失败的具体测试
2. 看测试日志（通常是 `*-test-results/` 目录）
3. 如果是 binding 失败（如 yml prefix 未改对应），按"问题 2"处理
4. 如果是其他原因，分析根因，不要直接 ignore

### 问题 5：pre-commit hook 失败（spotless）

**症状**：`git commit` 时 hook 报 import 顺序、格式问题。

**根因**：Edit 操作可能动了 Java 文件的某行，spotless 检查发现该文件的整体格式不符合规范（往往是 PR #19 遗留的 import 顺序）。

**处理**：
1. 看 hook 输出指明的文件与行
2. Run `./gradlew :模块:spotlessApply` 自动修复
3. `git add` + 重新 `git commit`（生成 NEW commit，**不**用 --amend）

---

## 备注

- 本 plan 完成后**不自动创建 PR**——用户决定何时 push 与 PR。
- 如果中途任何 Task 不顺利，优先 fix forward；若 fix 超过 1 小时无果，回滚到上一 Task 结束的状态：`git checkout -- <file...>`，重新 Plan。
- 本 plan 是单 commit 流程；如果想做更细的内部 commit（用于 squash），engineer 自行决定（spec § 5.2 P4 允许内部增量 commit，但**对外**只有一个 PR commit）。
