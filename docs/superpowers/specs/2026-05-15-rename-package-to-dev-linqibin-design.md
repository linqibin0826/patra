# 重命名顶层包：com.patra → dev.linqibin（设计文档）

**日期**：2026-05-15
**作者**：linqibin（baobig80@gmail.com）+ Claude（superpowers:brainstorming）
**状态**：待 review（spec phase）
**分支**：refactor/rename-pkg-to-dev-linqibin（待创建）

---

## 1. 背景与动机

### 1.1 问题陈述

patra-api 项目当前所有 Java 源码使用 `com.patra` 作为顶层包名。这跳过了 Java 反向域名命名约定中的"组织"层（`com.{org}.{product}`）。当未来 linqibin 开发其他产品时：

- 新产品无法复用 patra-api 中的通用基础设施（CQRS 框架、错误处理、JPA starter 等），因为它们被锁在 `com.patra.*` 命名空间下
- 兄弟产品出现命名割裂，无统一组织根
- 若发布到 Maven Central，`com.patra` 作为 group 没有 DNS 所有权背书（domain reverse 约定）

### 1.2 用户的 pain

用户域名为 `linqibin.dev`，希望所有自有项目共用 `dev.linqibin.*` 命名空间，建立长期可演化的"产品矩阵"前缀。

### 1.3 为什么现在做

- Greenfield 项目，零历史包袱
- 单人团队，无协作成本
- patra-api 是闭合系统（无外部 consumer）— 重构不破坏任何下游
- 越早改成本越低（项目规模随时间线性增长）

---

## 2. 设计决策汇总

通过 `superpowers:brainstorming` 流程的 8 个问题逐项决策，每项决策都有显式 rationale。

| # | 决策 | 选择 | 理由 |
|---|------|------|------|
| 1 | 下游消费者 | 无外部 consumer，闭合系统 | 不需要追加"下游升级"阶段 |
| 2 | linqibin-commons-* 投资档次 | B（为未来预留） | 包名/目录/边界按未来独立准备，但不立即加 SemVer / @PublicApi / 独立文档 |
| 3 | 业务枚举归属 | 独立模块 patra-common-enums | Vocabulary 与 Model 解耦，符合 Stable Dependencies |
| 4 | build-logic 改名 | 全部改：plugin ID + Kotlin 包名 | 与"档次 B"一致，未来抽离零摩擦 |
| 5 | Gradle group 分层 | 多 group（Spring 风格） | dev.linqibin.commons + dev.linqibin.patra |
| 6 | artifact ID 前缀 | 保留（patra-* / linqibin-*） | Spring 自描述风格 |
| 7 | 仓库根目录布局 | 保持扁平，后续单独重构 | 关切层分离，避免本次 PR 过大 |
| 8 | CI/CD 扫描 | 假设极少，现场遇到再处理 | 影响有限，分散到 Phase 5 |

---

## 3. 命名约定矩阵

| 类别 | Java 包根 | Gradle group | artifact ID | 示例 |
|------|-----------|--------------|------------|------|
| **通用 commons libs** | `dev.linqibin.commons.*` | `dev.linqibin.commons` | `linqibin-commons-{name}` | `linqibin-commons-core`、`linqibin-commons-storage` |
| **通用 starters**（11 个） | `dev.linqibin.starter.{name}.*` | `dev.linqibin.commons` | `linqibin-spring-boot-starter-{name}` | `linqibin-spring-boot-starter-jpa` |
| **patra 业务 common**（3 个） | `dev.linqibin.patra.common.*` | `dev.linqibin.patra` | `patra-common-{name}` | `patra-common-model`、`patra-common-enums`、`patra-common-provenance-api` |
| **patra 专用 starters**（2 个） | `dev.linqibin.patra.starter.{name}.*` | `dev.linqibin.patra` | `patra-spring-boot-starter-{name}` | `patra-spring-boot-starter-provenance`、`patra-spring-boot-starter-expr` |
| **patra 业务服务**（4 个） | `dev.linqibin.patra.{svc}.*` | `dev.linqibin.patra` | `patra-{svc}-{layer}` | `patra-catalog-domain`、`patra-ingest-app` |
| **build-logic** | `dev.linqibin.patra.buildlogic.*` | n/a（included build） | n/a | Plugin IDs：`linqibin.hexagonal-{domain,app,infra,adapter,api,boot}` |

### 3.1 依赖方向（Stable Dependencies Principle）

```
linqibin-commons-*                                  ← 最稳定
   ↑ 被依赖
linqibin-spring-boot-starter-*  (11 个)
   ↑
patra-common-{model, enums, provenance-api}
   ↑
patra-spring-boot-starter-{provenance, expr}
   ↑
patra-{registry, ingest, catalog, gateway}-*       ← 最不稳定
```

依赖严格自下而上，无环。未来 linqibin-commons-* 抽离独立仓库时零摩擦（group 已分层、artifact 已带前缀）。

---

## 4. 模块结构变更

### 4.1 新增 3 个模块

#### linqibin-commons-core
- **Group**：`dev.linqibin.commons`
- **Package root**：`dev.linqibin.commons.*`
- **接收 26 个通用类**（从 patra-common-core）：
  - **CQRS 框架**：Command、CommandBus、CommandHandler、CommandInterceptor、CommandHandlerNotFoundException
  - **DDD 框架**：AggregateRoot、ReadOnlyAggregate、DomainEvent、ChildEntityChange
  - **错误处理框架**：ApplicationException、DomainException、ErrorCodeLike、ErrorTrait、HasErrorTraits、StandardErrorTrait、CoreErrorCode、HttpStdErrors、ErrorKeys、RemoteCallException、RemoteErrorHelper
  - **JSON 工具**：JsonMapperHolder、JsonNormalizer、JsonNodeMappings、JsonNormalizerConfig、JsonNormalizerResult、JsonNormalizationException、TemporalAccessorWrapper、TemporalCoercion
  - **分页与查询**：PageResult、PagingParams
  - **消息**：ChannelKey
  - **类型与工具**：TypeReference、HashUtils、StringUtils
  - **通用枚举**：Priority、SortDirection

#### linqibin-commons-storage
- **Group**：`dev.linqibin.commons`
- **Package root**：`dev.linqibin.commons.storage.*`
- **接收 4 个类**（从 patra-common-storage）：ObjectKeyGenerator、DatePartitionedKeyGenerator、ObjectKeyContext、ObjectKeyTemplate

#### patra-common-enums
- **Group**：`dev.linqibin.patra`
- **Package root**：`dev.linqibin.patra.common.enums.*`
- **接收 4 个业务枚举**（从 patra-common-core）：ProvenanceCode、IngestDateType、RegistryConfigScope、DataType

### 4.2 重命名 11 个通用 starter

物理目录 + artifact ID 同步改：

| 旧名 | 新名 |
|------|------|
| patra-spring-boot-starter-core | linqibin-spring-boot-starter-core |
| patra-spring-boot-starter-web | linqibin-spring-boot-starter-web |
| patra-spring-boot-starter-jpa | linqibin-spring-boot-starter-jpa |
| patra-spring-boot-starter-batch | linqibin-spring-boot-starter-batch |
| patra-spring-boot-starter-rest-client | linqibin-spring-boot-starter-rest-client |
| patra-spring-boot-starter-object-storage | linqibin-spring-boot-starter-object-storage |
| patra-spring-boot-starter-observability | linqibin-spring-boot-starter-observability |
| patra-spring-boot-starter-redisson | linqibin-spring-boot-starter-redisson |
| patra-spring-boot-starter-test | linqibin-spring-boot-starter-test |
| patra-spring-boot-starter-http-interface | linqibin-spring-boot-starter-http-interface |
| patra-spring-boot-starter-openapi | linqibin-spring-boot-starter-openapi |

包名：`com.patra.starter.{name}.*` → `dev.linqibin.starter.{name}.*`

### 4.3 删除 2 个模块

- **patra-common-core**：内容拆完后为空，删除该 module
- **patra-common-storage**：整体迁出到 linqibin-commons-storage，删除该 module

### 4.4 包名 rename（保留 artifact ID 不变）

| 模块 | 旧包名 | 新包名 |
|------|--------|--------|
| patra-common-model | com.patra.common.model.* | dev.linqibin.patra.common.model.* |
| patra-common-provenance-api | com.patra.common.provenance.api.* | dev.linqibin.patra.common.provenance.api.* |
| patra-registry-* | com.patra.registry.* | dev.linqibin.patra.registry.* |
| patra-ingest-* | com.patra.ingest.* | dev.linqibin.patra.ingest.* |
| patra-catalog-* | com.patra.catalog.* | dev.linqibin.patra.catalog.* |
| patra-gateway-boot | com.patra.gateway.* | dev.linqibin.patra.gateway.* |
| patra-spring-boot-starter-provenance | com.patra.starter.provenance.* | dev.linqibin.patra.starter.provenance.* |
| patra-spring-boot-starter-expr | com.patra.starter.expr.* | dev.linqibin.patra.starter.expr.* |

### 4.5 build-logic 变更

- **Kotlin 包**：`com.patra.buildlogic.*` → `dev.linqibin.patra.buildlogic.*`（物理目录 git mv + 包声明改）
- **Plugin IDs**：`patra.hexagonal-{domain,app,infra,adapter,api,boot}` → `linqibin.hexagonal-{domain,app,infra,adapter,api,boot}`
- **21 个模块的 build.gradle.kts**：`plugins { id("patra.hexagonal-X") }` → `id("linqibin.hexagonal-X")`

### 4.6 不变项

- 仓库根目录扁平布局（保留 patra-catalog/、patra-ingest/ 等业务父目录）
- 数据库 schema、表名、列名
- Consul service 名、Docker image tag、API 路径
- JPMS module-info.java（不引入）
- Java 25 + Spring Boot 4.0.1 版本（重构与升级解耦）
- 现有测试代码逻辑（仅同步 rename 包路径与 import）

---

## 5. 执行计划（5 阶段）

每阶段验证 gate 通过后停下来等用户确认 Phase + 1。

### Phase 0：基线 + 准备

**步骤**：
1. `./gradlew build` 全绿（baseline 验证）
2. 创建分支 `refactor/rename-pkg-to-dev-linqibin`

**验证 gate**：baseline build 通过

### Phase 1：Gradle 模块结构调整（shell-only，不动源码）

**步骤**：
1. 新建 3 个模块的目录与 build.gradle.kts（暂无源码内容）：linqibin-commons-core、linqibin-commons-storage、patra-common-enums
2. 11 个通用 starter 目录改名：`git mv patra-spring-boot-starter-{X} linqibin-spring-boot-starter-{X}`
3. build-logic 内部 Kotlin 文件 git mv 到新包 + 包声明改名 + Plugin ID 改名
4. 21 个模块的 build.gradle.kts 中 `plugins { id("patra.hexagonal-X") }` → `id("linqibin.hexagonal-X")`
5. settings.gradle.kts 注册新模块 + 更新 11 个 rename 的 include 名称
6. gradle.properties 新增 `commonsGroup=dev.linqibin.commons`（patraGroup 暂保持 com.patra）

**验证 gate**：
- `./gradlew projects` 列出所有 24 个模块
- 现有代码仍编译通过（因为只动了"壳"，源码包名还是 com.patra）

### Phase 2：patra-common-* 内容拆分

**微步骤策略**（每步保持编译通过）：
1. 26 个通用类从 patra-common-core 复制到 linqibin-commons-core（包名同步改为 dev.linqibin.commons.*），**保留旧位置**
2. 全局批量替换 import：`com.patra.common.{cqrs,domain,error,json,query,messaging,type,util,enums.Priority,enums.SortDirection}` → `dev.linqibin.commons.*` 对应路径
3. 编译验证 → 删除 patra-common-core 中已迁出的类
4. 4 个业务枚举从 patra-common-core 复制到 patra-common-enums（包名 dev.linqibin.patra.common.enums.*）
5. 全局批量替换 import：`com.patra.common.enums.{ProvenanceCode,IngestDateType,RegistryConfigScope,DataType}` → `dev.linqibin.patra.common.enums.*`
6. 编译验证 → 删除 patra-common-core 中已迁出的枚举
7. 删除 patra-common-core 模块（settings.gradle.kts 移除）
8. patra-common-storage 4 个类 git mv 到 linqibin-commons-storage + 包名改
9. 全局批量替换 import：`com.patra.common.storage.*` → `dev.linqibin.commons.storage.*`
10. 删除 patra-common-storage 模块
11. patra-common-model + patra-common-provenance-api 内部 git mv 物理目录 + 包声明改 + import 改

**验证 gate**：
- 4 个 common 模块（linqibin-commons-{core,storage}、patra-common-{model,enums,provenance-api}）`compileJava` 全绿
- `grep -r "com\.patra\.common" --include="*.java"` 零残留

### Phase 3：业务服务 + patra 专用 starter 包名 rename

**步骤**：
1. 4 业务服务（registry/ingest/catalog/gateway）：git mv `src/{main,test}/java/com/patra/{svc}` → `src/{main,test}/java/dev/linqibin/patra/{svc}` + 包声明改 + import 改
2. 2 patra 专用 starter（provenance/expr）：物理目录 git mv + 包改
3. 删除空残留目录（com/patra/、com/ 父级）

**验证 gate**：
- `./gradlew compileJava` 全绿
- `grep -r "com\.patra\." --include="*.java"` 应仅剩 11 通用 starter 残留

### Phase 4：通用 starter 包名 + artifact ID rename

**步骤**：
1. 11 个通用 starter 内部 git mv 物理目录 + 包声明改 `com.patra.starter.{name}` → `dev.linqibin.starter.{name}` + import 改
2. 互相 dependency 坐标更新 `project(":patra-spring-boot-starter-X")` → `project(":linqibin-spring-boot-starter-X")`
3. 51 个 `META-INF/spring/.../AutoConfiguration.imports` 文件中的 FQN 全部更新
4. 5 个 boot 的 `application { mainClass = "..." }` 更新

**验证 gate**：
- `./gradlew compileJava` 全绿
- `./gradlew :patra-catalog-boot:bootRun --dry-run`（以及其他 boot）能识别 starter
- `grep -r "com\.patra\.starter" --include="*.java" --include="*.imports"` 零残留

### Phase 5：全局配置 + 最终验证

**步骤**：
1. 6 个 application*.yml：`logging.level.com.patra.*` → `logging.level.dev.linqibin.*`；FQN 引用同步改
2. ~80 个 logback*.xml：logger name 改
3. 16 个 .md 文件：README、CLAUDE.md、SKILL.md 等中的 com.patra 字符串 + agent 指引内容同步改
4. 10 个 .run/*.xml：SPRING_BOOT_MAIN_CLASS 等属性更新
5. 4 个 boot 服务的 `@SpringBootApplication(scanBasePackages = "dev.linqibin")` 改写（与旧的 `"com.patra"` 单 root 行为等价，覆盖 dev.linqibin.patra / dev.linqibin.commons / dev.linqibin.starter 全部）
6. gradle.properties：patraGroup `com.patra` → `dev.linqibin.patra`
7. CI/CD 配置 grep + 修正（.github/workflows/、Dockerfile、docker-compose.yml、shell 脚本）

**验证 gate**：
- `grep -r "com\.patra"` 全仓库零残留（除历史文档明确豁免）
- `./gradlew clean build` 编译 + 测试全绿
- 4 boot 全部 bootRun 启动成功，日志显示 starter 正常加载（看 autoconfig 报告）
- 至少 1 个 boot 跑通 1 个集成测试

---

## 6. 风险与缓解

### 6.1 高风险

| 风险 | 影响 | 缓解 |
|------|------|------|
| `AutoConfiguration.imports` 静默失效 | starter 不生效，不报编译错，应用启动后行为缺失 | Phase 4 完成后 grep 二次扫描；启动每个 boot 看 `/actuator/conditions` 自动配置报告 |
| `scanBasePackages` 漏配 | Bean 找不到，启动失败 | Phase 5 统一改为 `scanBasePackages = "dev.linqibin"` 单 root（与旧 `"com.patra"` 行为等价）。如未来发现性能问题再细化为精确多 root |
| Phase 2 中间态破坏编译 | 后续 phase 无法执行 | 采用"复制 + 批量改 import + 验证 + 删旧"四步微步骤，每步必须编译通过 |

### 6.2 中风险

| 风险 | 影响 | 缓解 |
|------|------|------|
| build-logic plugin ID rename 漏改某个 module 的 build.gradle.kts | 该 module 编译失败 | Phase 1 完成后 `grep -r "patra.hexagonal" --include="*.gradle.kts"` 二次扫描 |
| logback.xml 通配 logger name 替换错误（如 `<logger name="com.patra"/>` 通配整个根包） | 日志级别失效 | Phase 5 替换后跑一次 boot 看实际日志输出 |
| `@ComponentScan(basePackages = "com.patra.xxx")` 等代码内字符串 | sed 批量替换可能漏 | Phase 3 后 `grep -r "com\.patra"` Java 文件零残留确认 |

### 6.3 低风险

- IDE cache 陈旧：重构合并后用户手动 `File → Invalidate Caches`
- Maven Central 发布（不在本次范围，未来若发布需要 `dev.linqibin` group ownership via DNS TXT 验证）
- 第三方监控/日志聚合配置（项目暂未接入）

---

## 7. 范围界定

### 7.1 在范围内

- Java 源码包名重命名
- Gradle 模块结构调整（新建 3 + 重命名 11 + 删除 2）
- Gradle group 分层引入
- artifact ID 重命名（11 个通用 starter）
- build-logic 改名（plugin ID + Kotlin 包）
- 配置文件中 com.patra 字符串引用（yml、xml、md、imports）
- IDE Run Config

### 7.2 不在范围

- 仓库根目录分组布局（后续单独 PR）
- 数据库 schema、表名、列名
- Consul service 名、Docker image tag、API 路径
- JPMS module-info.java 引入
- Java / Spring Boot 版本升级
- 新功能开发或现有功能行为变更
- 代码风格统一、unused import 清理（除被重构动了的）

---

## 8. 验证与回滚策略

### 8.1 每 Phase 验证 gate

每个 Phase 必须满足验证 gate 才能进入下一 Phase。验证失败时：
1. 优先 fix forward
2. 若 fix 超过 2 小时无果，`git reset --hard` 回到上一 Phase commit
3. 重新评估 Phase 内步骤拆分

### 8.2 Commit 策略

- 一个 Phase 一个 commit（共 5 个 commit + 1 个 baseline tag）
- Commit message 格式：`refactor(rename): Phase N - {主要内容}`
- 不 squash，保留 phase 级 history 便于将来 review

### 8.3 回滚边界

- 任何 Phase 失败可回滚到上一 Phase commit
- 整个重构失败可 `git branch -D refactor/rename-pkg-to-dev-linqibin`，主分支不受影响

### 8.4 Definition of Done

- 全仓库 `grep -r "com\.patra"` 零残留（除历史文档明确豁免）
- `./gradlew clean build` 编译 + 测试全绿
- 4 个 boot 服务全部能正常启动，starter 自动配置可见
- spec 文档已 commit
- 至少 1 个 boot 跑过 1 个端到端集成测试

---

## 9. 后续工作（不在本 spec 范围）

完成后建议独立 PR 处理：
1. 仓库根目录分组布局重构（`linqibin-commons/`、`linqibin-starters/`、`patra/` 三层）
2. 如果开始有第二个产品需要复用 linqibin-commons-*：抽离到独立仓库 + 引入 SemVer + 加 @PublicApi 标注
3. 如果未来发布到 Maven Central：注册 `dev.linqibin` group ownership（通过 DNS TXT 验证 linqibin.dev）

---

## 附录 A：brainstorming 流程 traceback

本 spec 由 `superpowers:brainstorming` 技能引导产出，共经历 8 个澄清问题：

1. 下游消费者 → 闭合系统
2. linqibin-commons-* 投资档次 → B（为未来预留）
3. 业务枚举归属 → 独立模块 patra-common-enums
4. build-logic 改名 → 全部改
5. Gradle group 分层 → 多 group
6. artifact ID 前缀 → 保留
7. 仓库根目录布局 → 保持扁平，后续单独重构
8. CI/CD 扫描 → 假设极少，现场处理

每个决策的具体讨论记录在对话历史中。
