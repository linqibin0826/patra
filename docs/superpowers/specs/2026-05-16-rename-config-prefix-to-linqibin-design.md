# 重命名配置前缀：patra.* → linqibin.starter.* / patra.starter.*（设计文档）

**日期**：2026-05-16
**作者**：linqibin + Claude（superpowers:brainstorming）
**状态**：待 review（spec phase）
**分支**：`refactor/rename-config-prefix`（待创建）
**关联前序**：PR #19（com.patra → dev.linqibin），完成报告 `docs/superpowers/2026-05-16-refactor-completion-report.html` Follow-up ii

---

## 1. 背景与动机

### 1.1 问题陈述

PR #19 完成了 `com.patra → dev.linqibin` 的包名重构与 11 个通用 starter 的目录/artifact 改名，但 **Spring 配置 key 体系仍停留在 `patra.*`**——starter 命名与配置 prefix 在语义上错位：

```
模块名:        linqibin-spring-boot-starter-batch
Java 包名:     dev.linqibin.starter.batch.*
配置 prefix:   patra.batch.*               ← 不一致
```

完成报告第 08 章「Follow-ups」明确将此项列为 breaking change，建议独立 PR 处理。

### 1.2 目标

在配置 prefix 层面达到与「Java 包名 + artifact ID + Gradle group」对齐的终态：

```
模块名:        linqibin-spring-boot-starter-batch
Java 包名:     dev.linqibin.starter.batch.*
配置 prefix:   linqibin.starter.batch.*    ← 一致
Micrometer:    linqibin.starter.object_storage.*  ← 同步
```

对 patra 业务专用 starter（expr、provenance）同步整理为 `patra.starter.{expr,provenance}.*`，与其 Java 包 `dev.linqibin.patra.starter.{expr,provenance}` 严格对齐。

### 1.3 非目标（明确排除）

- **不动业务 prefix** `patra.catalog.*`、`patra.ingest.*`——那是业务领域配置（如 `patra.catalog.import.batch-size`），不是 starter 提供
- **不打版本号节点**（greenfield 项目，无 SemVer 约束）
- **不引入兼容旧 key 的 alias 机制**（CLAUDE.md 禁止向后兼容）
- **不改 Gradle group / artifact ID**（PR #19 已定型）
- **不改包名 / 类名**（PR #19 已定型）

---

## 2. 设计决策汇总

通过 brainstorming 流程的 3 个核心问题逐项决策：

| # | 决策 | 选择 | 理由 |
|---|------|------|------|
| 1 | prefix 命名约定 | `linqibin.starter.{name}.*` 与 Java 包名严格对齐 | 语义明确「来自 starter」、与 `dev.linqibin.starter.{name}.*` 一一对应 |
| 2 | Micrometer 指标名 | 同步改为 `linqibin.starter.object_storage.*`（underscore 保留） | 与配置 prefix 一致；greenfield 无外部依赖 |
| 3 | patra 业务专用 starter | 顺手整理为 `patra.starter.{expr,provenance}.*` | 与本次「Java 包名严格对齐」原则保持一致；不留 follow-up |

### 2.1 starter-core 子组归一化

所有 `linqibin-spring-boot-starter-core` 内的 `@ConfigurationProperties` 类（AsyncProperties / CommandBusProperties / ErrorProperties / TracingProperties）一律置于 `linqibin.starter.core.*` 下，与 Java 包 `dev.linqibin.starter.core.{async,cqrs,error}` 对齐。

### 2.2 顺手规整 dot→hyphen

`patra.http.interface`（双段 dot）规整为 `linqibin.starter.http-interface`（kebab-case 单段）——原表达将 `interface` 误置于 `http` 之下；新表达正确反映「这是一个完整 starter 的名字」。符合 Spring 配置 key 主流风格。

### 2.3 `web.problem` 例外（不严格对齐 Java 包名）

`WebErrorProperties` Java 包位于 `dev.linqibin.starter.web.error.config.*`，若严格对齐应改为 `linqibin.starter.web.error`。**本次保留为 `linqibin.starter.web.problem`**——因为「problem」对应 RFC 7807 Problem Details for HTTP APIs 的标准术语，配置 key 命名优先反映 **协议语义** 而非 Java 包路径。这是「严格对齐 Java 包名」原则的唯一明确例外。

---

## 3. 命名映射矩阵

### 3.1 11 个通用 starter

| Starter | 旧 prefix | 新 prefix | 来源 |
|---|---|---|---|
| starter-core | `patra.async` | `linqibin.starter.core.async` | AsyncProperties |
| starter-core | `patra.command-bus` | `linqibin.starter.core.command-bus` | CommandBusProperties |
| starter-core | `patra.command-bus.interceptors` | `linqibin.starter.core.command-bus.interceptors` | @ConditionalOnProperty ×3 |
| starter-core | `patra.error` | `linqibin.starter.core.error` | ErrorProperties |
| starter-core | `patra.tracing` | `linqibin.starter.core.tracing` | TracingProperties |
| starter-batch | `patra.batch` | `linqibin.starter.batch` | BatchProperties |
| starter-batch | `patra.batch.metrics` | `linqibin.starter.batch.metrics` | @ConditionalOnProperty |
| starter-batch | `patra.batch.schema` | `linqibin.starter.batch.schema` | @ConditionalOnProperty |
| starter-batch | `patra.batch.datasource` | `linqibin.starter.batch.datasource` | @ConditionalOnProperty |
| starter-http-interface | `patra.http.interface` | `linqibin.starter.http-interface` | HttpInterfaceProperties（dot→hyphen） |
| starter-object-storage | `patra.object-storage` | `linqibin.starter.object-storage` | ObjectStorageProperties |
| starter-observability | `patra.observability` | `linqibin.starter.observability` | ObservabilityProperties |
| starter-observability | `patra.observability.metrics` | `linqibin.starter.observability.metrics` | @ConditionalOnProperty |
| starter-openapi | `patra.openapi` | `linqibin.starter.openapi` | OpenApiProperties |
| starter-redisson | `patra.redisson` | `linqibin.starter.redisson` | RedissonProperties |
| starter-redisson | `patra.redisson.lock` | `linqibin.starter.redisson.lock` | @ConditionalOnProperty |
| starter-rest-client | `patra.rest-client` | `linqibin.starter.rest-client` | RestClientProperties |
| starter-rest-client | `patra.rest-client.download` | `linqibin.starter.rest-client.download` | DownloadProperties |
| starter-rest-client | `patra.rest-client.download.ftp` | `linqibin.starter.rest-client.download.ftp` | @ConditionalOnProperty |
| starter-rest-client | `patra.rest-client.streaming` | `linqibin.starter.rest-client.streaming` | @ConditionalOnProperty |
| starter-rest-client | `patra.rest-client.proxy.tunnel` | `linqibin.starter.rest-client.proxy.tunnel` | @ConditionalOnProperty |
| starter-rest-client | `patra.rest-client.interceptors.logging` | `linqibin.starter.rest-client.interceptors.logging` | @ConditionalOnProperty |
| starter-rest-client | `patra.rest-client.clients.long-running` | `linqibin.starter.rest-client.clients.long-running` | LongRunningClientEnabledCondition Environment.getProperty |
| starter-web | `patra.web.problem` | `linqibin.starter.web.problem` | WebErrorProperties（保留语义化 problem） |

### 3.2 2 个 patra 专用 starter

| Starter | 旧 prefix | 新 prefix | 备注 |
|---|---|---|---|
| starter-expr | `expr` | `patra.starter.expr.mode` | ExprModeProperties，原 `expr` 是历史不规范，顺手规整 |
| starter-expr | `patra.expr.compiler` | `patra.starter.expr.compiler` | CompilerProperties |
| starter-provenance | `patra.provenance` | `patra.starter.provenance` | ProvenanceProperties |

### 3.3 Micrometer 指标名（仅 starter-object-storage）

| 旧名 | 新名 |
|---|---|
| `patra.object_storage.upload.total` | `linqibin.starter.object_storage.upload.total` |
| `patra.object_storage.upload.bytes` | `linqibin.starter.object_storage.upload.bytes` |
| `patra.object_storage.upload.duration` | `linqibin.starter.object_storage.upload.duration` |
| `patra.object_storage.download.total` | `linqibin.starter.object_storage.download.total` |
| `patra.object_storage.download.bytes` | `linqibin.starter.object_storage.download.bytes` |
| `patra.object_storage.download.duration` | `linqibin.starter.object_storage.download.duration` |
| `patra.object_storage.delete.total` | `linqibin.starter.object_storage.delete.total` |

保留 underscore 不改 hyphen，符合 Micrometer / Prometheus exposition format 主流约定。

### 3.4 Java 硬编码字符串

| 文件 | 改动 |
|---|---|
| `LongRunningClientEnabledCondition.java:17` | `Environment.getProperty("patra.rest-client.clients.long-running.enabled")` → 对应新 prefix |
| `CoreErrorAutoConfiguration.java:76` | 日志提示 `"patra.error.context-prefix 未配置..."` → 对应新 prefix |
| `TunnelProxyAutoConfiguration.java:51-54` | Assert 提示中 `"patra.rest-client.proxy.tunnel.host"` 等 → 对应新 prefix |

---

## 4. 影响清单

### 4.1 按文件类别汇总

| 类别 | 文件/位点数 | 说明 |
|---|---|---|
| `@ConfigurationProperties` 类 | 13（11 通用 + 2 patra 专用） | 每类一行 `prefix = "…"` |
| `@ConditionalOnProperty` 用点 | 23 处 | 分布在 22 个 AutoConfiguration / Condition 文件 |
| Java 硬编码字符串 | 3 处 | 见 § 3.4 |
| Micrometer 指标名 | 7 行 | `ObjectStorageMetrics.java` 内 |
| yml/properties（生产 + 测试） | 14 个文件 | 见 § 4.2 |
| README/技术文档 | 9 个文件 | 见 § 4.3 |
| `additional-spring-configuration-metadata.json` | 2 文件 / 11 keys | 见 § 4.4 |
| **合计** | **~70 个文件** | |

### 4.2 yml/properties 文件清单

需要改的 starter prefix 段：

| 文件 | 需要改的 prefix 段 |
|---|---|
| `patra-catalog/patra-catalog-boot/src/main/resources/application.yml` | `patra.rest-client.*`、`patra.http.interface.*` |
| `patra-catalog/patra-catalog-boot/src/main/resources/application-dev.yml` | `patra.batch.*`、`patra.object-storage.*` |
| `patra-catalog/patra-catalog-boot/src/main/resources/application-prod.yml` | `patra.batch.*`、`patra.object-storage.*` |
| `patra-catalog/patra-catalog-boot/src/main/resources/catalog-error-config.yaml` | `patra.error.*` |
| `patra-catalog/patra-catalog-boot/src/test/resources/application-e2e-test.yml` | `patra.batch.*`、`patra.redisson.*`、`patra.rest-client.*` |
| `patra-ingest/patra-ingest-boot/src/main/resources/application.yml` | `patra.http.interface.*` |
| `patra-ingest/patra-ingest-boot/src/main/resources/application-dev.yml` | `patra.batch.*` |
| `patra-ingest/patra-ingest-boot/src/main/resources/application-prod.yml` | `patra.batch.*` |
| `patra-ingest/patra-ingest-boot/src/main/resources/ingest-error-config.yaml` | `patra.error.*` |
| `patra-ingest/patra-ingest-boot/src/test/resources/application-e2e-test.yml` | `patra.batch.*`、`patra.redisson.*`、`patra.rest-client.*` |
| `patra-ingest/patra-ingest-boot/src/test/resources/application-test-common.yml` | `patra.observability.*` |
| `patra-registry/patra-registry-boot/src/main/resources/registry-error-config.yaml` | `patra.error.*` |
| `linqibin-spring-boot-starter-batch/src/test/resources/application-test.yml` | `patra.batch.*` |
| `patra-spring-boot-starter-expr/src/main/resources/application-expr-reference.yml` | `patra.expr.*` |

### 4.3 README 文档清单

| 文件 | 段落 |
|---|---|
| `linqibin-spring-boot-starter-redisson/README.md` | L188-191（配置表） |
| `linqibin-spring-boot-starter-observability/README.md` | L138-142, L148-149 |
| `linqibin-spring-boot-starter-core/README.md` | L60, L141, L267, L287-293, L317-322 |
| `linqibin-spring-boot-starter-batch/README.md` | L111 |
| `linqibin-spring-boot-starter-rest-client/README.md` | L133, L284 |
| `linqibin-spring-boot-starter-openapi/README.md` | L41, L79-82 |
| `linqibin-spring-boot-starter-web/README.md` | L113-115 |
| `linqibin-spring-boot-starter-object-storage/README.md` | L140 |
| `patra-object-storage/README.md` | L348 |

### 4.4 additional-spring-configuration-metadata.json

| 文件 | 涉及 key 数 | 涉及 key |
|---|---|---|
| `linqibin-spring-boot-starter-observability/src/main/resources/META-INF/additional-spring-configuration-metadata.json` | 8 | `patra.observability` group / `.enabled` / `.application-name` / `.environment` / `.region` / `.cluster` / `.metrics` group / `.metrics.enabled` |
| `linqibin-spring-boot-starter-core/src/main/resources/META-INF/additional-spring-configuration-metadata.json` | 3 | `patra.error.enabled` / `patra.error.context-prefix` / `patra.tracing.header-names` |

这些 json 文件被 IDE 用于 yml 自动补全；不改不影响运行，但 IDE 提示会显示旧 key。

### 4.5 反向白名单（绝对不能动）

为避免脚本误伤，下列模式显式标记为豁免：

| 模式 | 原因 |
|---|---|
| `patra.catalog.*`、`patra.catalog-*` | 业务 prefix |
| `patra.ingest.*`、`patra.ingest.outbox.*` | 业务 prefix |
| `dev.linqibin.patra.*` | Java 包名（非配置 key） |
| `patra-{registry,ingest,catalog,gateway,common,object-storage,expr-kernel,spring-boot-starter-*}` | artifact ID |
| `com.patra` 出现在 spec / plan / report 历史档案 | 历史档案 |
| 项目名 `patra-api`、目录名 `patra-{catalog,ingest,…}` | 仓库结构 |

### 4.6 其他文件类型已扫描确认无影响

- logback*.xml / logging*.xml：0 匹配
- `.run/*.xml`（IDE run config）：0 匹配（PR #19 已处理 Java 包名）
- `.github/workflows/*.yml`：0 匹配
- Dockerfile / docker-compose*.yml：0 匹配
- `*.gradle.kts` / `*.gradle`：0 匹配
- `*.sh`：0 匹配
- `gradle.properties` / `settings.gradle.kts`：0 匹配
- `META-INF/spring/.../AutoConfiguration.imports`：0 匹配（PR #19 已处理 FQN）

---

## 5. 执行步骤大纲

详细脚本归 writing-plans 阶段。spec 层面定执行**原则**。

### 5.1 五步线性流程（单 commit 完成）

1. **构建映射表**：把 § 3 全部内容编码成 sed/awk 替换列表 + § 4.5 反向白名单
2. **Java 层批量替换**：`@ConfigurationProperties` / `@ConditionalOnProperty` / Micrometer 指标名 / 硬编码字符串
3. **配置层批量替换**：yml / yaml / properties / json metadata
4. **文档层批量替换**：README.md
5. **全量验证**：compile + grep 残留 + boot 启动看 `/actuator/configprops`

### 5.2 执行原则

**P1 — 长前缀优先**：映射表按 key 字符串长度**从长到短**排序后逐项替换。例：先替换 `patra.batch.metrics`，再替换 `patra.batch`。

**P2 — 全字边界匹配**：替换模式必须锚定 `patra.<key>` 后紧接 `.` / `=` / 空白 / 行尾 / `"` / `'` / `:` 之一，避免误伤粘连情况。

**P3 — 反向白名单**：脚本里对 § 4.5 列出的模式设置短路豁免。

**P4 — 单 commit 但分 5 步内部 staging**：每个 Step 完成后 `./gradlew compileJava` 轻量验证；5 步全过后一次性 commit。对外 atomic、对内增量。

---

## 6. 风险与缓解

| # | 风险 | 影响 | 概率 | 缓解 |
|---|---|---|---|---|
| R1 | sed 替换误伤业务 prefix（`patra.catalog.*` 被改） | 业务配置失效、boot 启动 NPE | 中 | § 5.2 P3 反向白名单 + final grep 验证 |
| R2 | 长前缀短前缀替换顺序错（`patra.batch` 早于 `patra.batch.metrics`） | 部分 key 错位、binding 失败 | 中 | § 5.2 P1 按长度从长到短排序 |
| R3 | `@ConditionalOnProperty(prefix = …, name = …)` 改了 `prefix` 但 `name` 默认值 `enabled` 被脚本误改 | 条件加载错位、starter 静默不生效 | 低 | 替换模式锚定 `prefix = "…"`，不动 `name` 字符串 |
| R4 | `additional-spring-configuration-metadata.json` 漏改 → IDE 提示旧 key | 开发者困惑（不影响运行） | 低 | § 4.4 清单已列出 2 个文件 |
| R5 | Micrometer 指标改名后 Grafana 仪表盘 break | 监控丢数据 | **已关闭** | greenfield 项目无外部 Prometheus / Grafana 仪表盘依赖（用户确认） |
| R6 | scan 漏文件类型 | 个别 key 残留 | 低 | § 7.1 gate 2 最终 grep 兜底 |
| R7 | 重构途中编译过但运行时 binding 失败 | 集成测试红 | 低 | § 7.1 gate 3 / gate 4 boot 启动 + 集成测试 |

---

## 7. 验证策略与 Definition of Done

### 7.1 验证 gates（最终，按重要度）

1. **`./gradlew clean build`** — 编译 + 全量测试全绿
2. **零残留 grep**：
   ```bash
   # 主 grep — 捕 patra.* 残留
   grep -rnE "(^|[^a-zA-Z0-9_])patra\.(batch|async|command-bus|error|tracing|http\.interface|object-storage|object_storage|observability|openapi|redisson|rest-client|web\.problem|expr|provenance)\b" \
     --include="*.java" --include="*.yml" --include="*.yaml" \
     --include="*.properties" --include="*.md" --include="*.json"

   # 补充 — ExprModeProperties 旧 prefix `expr` 残留（standalone，不被主 grep 捕获）
   grep -rn 'prefix = "expr"' --include="*.java"          # 应零结果
   grep -rnE '^expr:' --include="*.yml" --include="*.yaml" # 应零结果
   ```
   所有 grep 排除 § 4.5 白名单后均零结果
3. **任选 1 boot（推荐 catalog-boot）`./gradlew :patra-catalog-boot:bootRun`**：启动成功，`/actuator/configprops` 显示新 prefix
4. **1 个跨 starter 集成测试**（如 `BatchAutoConfigurationIT`）通过：验证 yml binding 在新 prefix 下生效

### 7.2 测试策略

Rename 重构 = 行为不变。**不新增测试用例**（写新单元测试覆盖"新 prefix 也能 bind" 是在测 Spring 框架，无意义）。现有集成测试就是事实上的特征测试，跑通它们 = 行为不变。

| 阶段 | 动作 |
|---|---|
| 重构前 | `./gradlew test` baseline 全绿，记录通过测试数量 |
| 重构后 | `./gradlew clean build` 仍全绿，测试数量未减少 |
| 重构后 | 1 个 boot bootRun + `/actuator/configprops` 验证新 prefix |
| 重构后 | 1 个跨 starter 集成测试通过 |

### 7.3 Definition of Done

按递进顺序，全部满足才算完成：

1. § 3 映射矩阵 100% 落地（~70 文件）
2. `./gradlew clean build` 全绿
3. § 7.1 gate 2 grep 零残留
4. 至少 1 个 boot bootRun 启动成功，`/actuator/configprops` 显示新 prefix
5. 至少 1 个跨 starter 集成测试通过
6. Spec 文档与 plan 文档已 commit 到 `docs/superpowers/` 对应位置
7. 单 commit 提交，commit message 形如 `refactor(config): rename patra.* config prefix → linqibin.starter.* / patra.starter.*`
8. PR 描述包含 § 3 映射矩阵 + § 7.1 gate 验证证据

### 7.4 回滚边界

- 任何 Step 失败 → 单 Step 回滚 `git checkout HEAD -- <files>`，重新分析根因
- 提交后发现严重问题 → `git revert <sha>`（单 commit 可干净 revert）
- 不需要 feature flag、不需要 phased rollout（绿地项目、单人团队、闭合系统）

---

## 8. 范围界定

### 8.1 在范围内

- 11 个通用 starter 配置 prefix（`patra.*` → `linqibin.starter.*`）
- 2 个 patra 专用 starter 配置 prefix（`patra.*` → `patra.starter.*`）
- 7 个 Micrometer 指标名（与配置 prefix 一致迁移）
- 3 处 Java 硬编码字符串（`Environment.getProperty` / 日志提示）
- 14 个 yml / properties 文件中的 starter prefix 段
- 9 个 README 文档中的配置示例
- 2 个 `additional-spring-configuration-metadata.json` 文件

### 8.2 不在范围

- 业务 prefix `patra.catalog.*`、`patra.ingest.*`（业务领域配置）
- 不为「未维护 metadata.json 的 9 个通用 starter」补全 IDE 元数据（独立技术债）
- 版本号节点 / SemVer（greenfield 项目）
- 兼容旧 key 的 alias 机制（CLAUDE.md 禁止）
- Gradle group / artifact ID / 包名 / 类名（PR #19 已定型）
- @ConfigurationProperties 类名重命名（如 `WebErrorProperties` → `WebProblemProperties`）
- 新增配置示例文档（沿用现有 README 结构）

---

## 9. 后续工作（不在本 spec 范围）

完成后建议独立 PR 处理：

1. 为其他 9 个未维护 `additional-spring-configuration-metadata.json` 的通用 starter 补齐 IDE 自动补全元数据
2. PR #19 完成报告 Follow-up iii：SpotBugs 配置修复（`spotbugs-exclude.xml` 错位 class FQN）
3. PR #19 完成报告 Follow-up iv：`AuthorJpaMapper.withNameVariants()` production bug 修复

---

## 附录 A：brainstorming 流程 traceback

本 spec 由 `superpowers:brainstorming` 技能引导产出，经历的核心决策点：

1. prefix 命名约定 → `linqibin.starter.{name}.*` 严格对齐 Java 包名
2. Micrometer 指标名同步策略 → 跟随配置 prefix 一起迁移
3. patra 专用 starter（expr / provenance）的处理 → 顺手拉齐 `patra.starter.*`，不留 follow-up
4. 执行策略 → 单 commit rip-and-replace（Approach A，相比按 starter 拆 13 commit 与按层拆 2-phase 风险更小）
5. R5（Grafana 仪表盘依赖）确认 → greenfield 项目无外部依赖，关闭风险项

每个决策点的 trade-off 讨论记录在对话历史中。
