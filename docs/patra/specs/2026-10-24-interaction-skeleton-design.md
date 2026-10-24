# interaction 服务骨架与部署接入工程设计（PAP-111）

> 状态：已实施（2026-10-24，计划 `docs/patra/plans/2026-10-24-interaction-skeleton.md`）；用户决定不走 Codex 评审直接实施；实施中的偏离与核对结果见第 17 节
> 日期：2026-10-24
> Issue：PAP-111（GitHub #281），Linear Project P-PAP-10 v0.13 Library，Milestone BE Ready
> 依据：release spec `docs/patra/release-specs/v0.13-library.md` 决策 A、B、G，第 3 节 F（不建 Outbox），Done 判定 D2、D3、D12
> 参照：identity 的接入（`docs/patra/specs/2026-10-10-identity-deploy-design.md`、`docs/patra/runbooks/v0.8-accounts-go-live-runbook.md`），以及它之后 v0.9 配置中心、v0.10 工具模块重组带来的变化

## 1. 要解决的问题

v0.13 的收藏、收藏夹，以及以后的讨论、关注、通知、行为数据，都放进新服务 `patra-interaction`（决策 A）。PAP-112（对象登记表）和 PAP-113（收藏与收藏夹）要在一个已经能构建、能测试、能部署的服务里写代码。本 Issue 把这个服务搭起来，并一次接好部署：

1. 六层模块接进构建，每层按子域分包，本版只有 `subject`、`collection` 两个子域。
2. 子域之间的边界由 ArchUnit 规则守住：只经对方公开的包交互，不碰对方的实体和仓储。
3. 库 `patra_interaction`，每个子域一个 schema，整个服务一份 Flyway 历史（决策 B）。
4. 部署：Nacos、网关路由、CD、compose、XXL-Job 执行器全部接好；合并后 CD 把它部署到 mini 并通过健康检查。
5. 写上线 runbook，包括 PAP-112 / PAP-113 两个每日任务的登记步骤（决策 G）。

接入新服务大约要改 27 处，其中五处漏了不会有任何报错：`unitOf`、`ALL_UNITS`、`services.json`、`nacos-verify.sh`、CI 镜像常量。尤其 `unitOf`：漏改时 interaction 的模块被归进 foundation，CI 照样是绿的，CD 却永远不会部署它。所以第 10 节的核对单逐项写明位置和验证方法。

**完成标准**分两个时间点，因为本版后端只开一个 PR（根 CLAUDE.md「PR 与代码评审」）：PAP-111 先合回版本分支 `feat/v0.13-library-api` 让 PAP-112 开工，部署要等整个后端 PR 合进 main。

- **合回版本分支前**：`./gradlew check` 中 interaction 各模块、新 ArchUnit 规则、R6 都通过；`detect-changes.test.sh`、`deploy.test.sh` 通过；服务在本机能启动并注册到 Nacos；核对单里不依赖部署的各项打勾并附证据。
- **后端 PR 合进 main 后**：runbook 第 5 步走完——容器 healthy、直连健康 UP、经网关访问 OpenAPI 文档 200、`nacos-verify.sh --expect-services` 含 interaction、执行器在线；核对单剩余各项打勾。这些写进 PR 描述和 release spec D3。

## 2. 范围

**做**：

- `patra-api/patra-interaction/` 下六个模块，以及 `settings.gradle.kts` 的注册。
- `patra-tool-test`：放宽四条层规则里写死的连续包路径；新增子域规则 `SubdomainArchitectureRules`，两者都有测试。
- interaction-boot：启动类、`application.yml`、XXL-Job 执行器配置、`InteractionArchitectureTest`、`ApplicationConfigTest`、启动集成测试。
- interaction-infra：两份建 schema 的 Flyway 脚本、集成测试的启动类和 PG 容器初始化器、迁移集成测试。
- 网关：路由、OpenAPI 文档聚合、路由集成测试。
- `patra-infra`：compose、建库脚本、Nacos 配置、CD 清单、`detect-changes.sh` 及其测试、`nacos-verify.sh`。
- 根构建脚本：`dumpModuleGraph` 的 `unitOf` 与 `units`，并重新生成 `module-graph.json`。
- IDEA 运行配置 `.run/PatraInteractionApplication.run.xml`。
- 服务 README（含拆分条件）、上线 runbook，同步各处提到服务清单的文档（第 13 节）。

**不做**：

- 任何表、实体、端口、用例、接口：表属于 PAP-112 / PAP-113，代码随表一起来（第 3 节第 3 条）。
- 安全 starter 接入（`patra-tool-security`）：骨架没有业务接口；PAP-113 加第一个要登录的接口时一起接。
- 调 catalog、identity 的 HTTP Interface 客户端：PAP-112 / PAP-113 的事。
- `set_updated_at()` 触发器函数：其他服务都在首个脚本里定义它，但本版骨架没有表，留给第一个建表的 Issue（第 6.4 节）。
- 两个每日任务的代码与登记：任务名和 Cron 在这里定下（第 11.2 节），代码和在调度中心建任务随 PAP-112 / PAP-113。
- Outbox、Redis、对象存储：本版 interaction 都不用（release spec 第 3 节 F、J）。

## 3. 已定的前提

| 来源 | 内容 |
|---|---|
| 决策 A | 新服务 `patra-interaction`，六层模块；每层按子域分包 `dev.patra.interaction.{layer}.{子域}`；本版只建 `subject`、`collection`；不建其他子域的空包和空 schema；子域之间只经对方的读端口与底座交互；拆分条件写进 README；错误码前缀 `INTERACTION` |
| 决策 B | 库 `patra_interaction` 在现有 PG 实例上；每个子域一个 schema，在迁移脚本里 `CREATE SCHEMA`；整个服务一份 Flyway 历史，版本号用时间戳；子域表不对 `subject` 建跨 schema 外键；新迁移照常过 R6 |
| 决策 G | interaction 注册 XXL-Job 执行器，跑对象对账、注销清理两个每日任务；执行器与两个任务的登记写进本 Issue 的核对单和上线 runbook |
| 第 3 节 F | 不建 Outbox |
| 用户 2026-10-24 答复 ① | 网关前缀用 `/patra-interaction/**`，与现有四条路由一致。release spec 与 Issue 里的 `/interaction/**` 按此修订 |
| 用户 2026-10-24 答复 ② | 网关对所有服务的 `/*/actuator/**` 返回 403（`GatewaySecurityConfiguration.BLOCKED_PATHS`，`GatewayBlockedPathsIT` 有测试）。验收「经网关能访问健康端点」改为两条：直连 6500 端口时 `/actuator/health` 为 UP；经网关访问 `/patra-interaction/v3/api-docs` 返回 200 |
| 用户 2026-10-24 答复 ③ | 骨架各层只放说明子域职责的 `package-info.java`，模块只声明现在真正 import 到的依赖；层间依赖、读端口、值对象随 PAP-112 / PAP-113 的代码一起加。api 模块照建，零个类 |
| R5（`DeclaredDependencyCheck`） | `api` / `implementation` 声明的 project 依赖，主代码里必须至少 import 一个目标模块的包；目标带自动配置登记的放行。boot 层不查。这就是答复 ③ 的原因：identity 的骨架（10/06）早于 R5（10/15），照抄过不了 `check` |
| Nacos 签到 | 服务启动时检查读到了 `patra.config-source.service: <服务名>.yaml`，Nacos 里没有这份配置就启动失败（`patra-infra/nacos/dev/README.md`） |

## 4. 模块与包

### 4.1 模块

| 模块 | 约定插件 | 本 Issue 的内容 | 依赖 |
|---|---|---|---|
| `patra-interaction-domain` | `patra.hexagonal-domain` | `domain.subject`、`domain.collection` 的 `package-info.java` | 插件自带的 patra-common、tool-core（R5 豁免） |
| `patra-interaction-app` | `patra.hexagonal-app` | `app.subject`、`app.collection` 的 `package-info.java` | 无 project 依赖 |
| `patra-interaction-infra` | `patra.hexagonal-infra` | 两个子域的 `package-info.java`；Flyway 脚本；集成测试基座与迁移测试 | `api(project(":patra-tool:patra-tool-jpa"))`（带自动配置登记，R5 放行；带来 JPA、Flyway、PG 驱动） |
| `patra-interaction-adapter` | `patra.hexagonal-adapter` | 两个子域的 `package-info.java` | 无 project 依赖 |
| `patra-interaction-api` | `patra.hexagonal-api` | 零个类：本版没有别的服务调用 interaction。`build.gradle.kts` 的头注释写明「服务间契约，第一个内部调用方出现时加」 | 插件自带的 patra-common、tool-core |
| `patra-interaction-boot` | `patra.hexagonal-boot` | 启动类、配置、XXL-Job 执行器、架构测试、启动测试 | 见 4.3 |

聚合模块 `patra-api/patra-interaction/build.gradle.kts` 只有注释，写法同 identity。`settings.gradle.kts` 按 identity 那段加六行 `includeAt` 和一行 `mapParent`，放在 identity 之后、gateway 之前。

api 模块连 `package-info.java` 也不建：在子域包里放 `package-info` 表示这一层已经有这个子域，而 api 层两个子域都还没有契约。模块目录里只有 `build.gradle.kts`。

### 4.2 包

根包 `dev.patra.interaction`。

| 位置 | 包 | 说明 |
|---|---|---|
| domain / app / infra / adapter | `dev.patra.interaction.{layer}.subject`、`dev.patra.interaction.{layer}.collection` | 层下面只有子域包。子域内部的分包沿用各服务现有写法（如 `domain.collection.model.aggregate`、`domain.collection.port.repository`、`infra.collection.persistence`、`adapter.collection.rest`） |
| api | `dev.patra.interaction.api.{子域}` | 本版没有 |
| boot | `dev.patra.interaction`（启动类）、`dev.patra.interaction.config`（XXL-Job 执行器等装配） | 组装根，不属于任何子域，子域规则不管它 |

XXL-Job 执行器配置放 boot，不放 adapter：其他服务放在 `adapter.scheduler.config`，在 interaction 里那会被当成一个叫 `scheduler` 的子域。任务处理器（`@XxlJob`）随 PAP-112 / PAP-113 放进各自子域的 adapter 包（`adapter.subject.scheduler`、`adapter.collection.scheduler`），adapter 模块那时再加 `libs.xxl.job`。

每个 `package-info.java` 用 `///` 写一两句子域的职责：

- `subject`：对象底座。每个被互动过的外部对象一行登记，互动数据只引用本地 `subject_id`；对象身份锚定在外部标识上（release spec 决策 C）。其他子域可以用的只有第 5.2 节列的公开包。
- `collection`：文献库与收藏夹（release spec 决策 D）。

### 4.3 boot 的依赖

```kotlin
dependencies {
    implementation(project(":patra-api:patra-interaction:patra-interaction-adapter"))
    implementation(project(":patra-api:patra-interaction:patra-interaction-infra"))

    implementation(project(":patra-tool:patra-tool-web"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation(project(":patra-tool:patra-tool-observability"))
    implementation(project(":patra-tool:patra-tool-openapi"))

    // XXL-Job 执行器（执行器端口 = 服务端口 + 1 = 6501）
    implementation(libs.xxl.job)

    // 架构测试要看到全部五层：domain、app、api 在 PAP-112 接上层间依赖之前不在运行时类路径上
    testImplementation(project(":patra-api:patra-interaction:patra-interaction-domain"))
    testImplementation(project(":patra-api:patra-interaction:patra-interaction-app"))
    testImplementation(project(":patra-api:patra-interaction:patra-interaction-api"))
    testImplementation(testFixtures(project(":patra-api:patra-interaction:patra-interaction-infra")))
}
```

boot 的插件已自带 `patra-tool-launch`（Nacos 与 `PatraApplication`）、`patra-tool-test`、`testcontainers-jdbc`。`springBoot.mainClass = "dev.patra.interaction.PatraInteractionApplication"`。

## 5. ArchUnit 规则

### 5.1 放宽现有层规则

`HexagonalArchitectureRules` 里有四条规则把子包路径写成连续的，按子域分包后会误报：

| 规则 | 现在 | 改成 | 按子域分包时的实际包 |
|---|---|---|---|
| `doClassesShouldResideInPersistenceEntity` | `..infra.persistence.entity..` | `..infra..persistence.entity..` | `infra.collection.persistence.entity` |
| `aggregatesShouldResideInDomainModelAggregate` | `..domain.model.aggregate..` | `..domain..model.aggregate..` | `domain.collection.model.aggregate` |
| `orchestratorsShouldResideInAppUsecase` | `..app.usecase..` | `..app..usecase..` | `app.collection.usecase` |
| `eventsShouldResideInDomainEvent` | `..domain.event..` | `..domain..event..` | `domain.collection.event` |

ArchUnit 的 `..` 匹配零到多段，所以新写法同时接受旧布局，只会更宽，catalog、ingest 现有的架构测试不受影响。`portsShouldResideInDomainPortPackage` 已经写成 `..domain..port..`，是现成的先例。其余规则按层根包（`basePackage + ".domain.."`）或 `..domain..` 判断，本来就兼容。

四条规则的 Javadoc 一并改成「`domain` 下任意层级的 `model.aggregate` 包」这类说法。

### 5.2 子域规则 `SubdomainArchitectureRules`

放在 `patra-tool-test` 的 `dev.patra.tool.test.archunit`，与 `HexagonalArchitectureRules`、`TestingRules` 并列；构造参数是根包、底座子域名、全部子域名：

```java
new SubdomainArchitectureRules("dev.patra.interaction", "subject", Set.of("subject", "collection"))
```

「层」固定为 `domain`、`app`、`infra`、`adapter`、`api`。一个类的子域 = 它所在的 `{根包}.{层}.{子域}` 的第三段；不在这五个层下的类（boot 的启动类和配置）不属于任何子域，规则不管。

**公开包**：一个子域让其他子域使用的包。

| 子域 | 公开包 |
|---|---|
| 任意子域 X | `{根包}.domain.X.port.read..`（读端口）、`{根包}.domain.X.model.read..`（读端口用到的读模型） |
| 底座（`subject`）另加 | `{根包}.domain.subject.port.gateway..`（驱动端口，如「首次互动时解析对象」，实现在 `app.subject`）、`{根包}.domain.subject.model.vo..`、`{根包}.domain.subject.model.enums..`（`SubjectId`、`SubjectType` 这类所有互动都引用的值） |

由此推出一条约束：非底座子域的读端口，签名里只能用 JDK 类型、自己 `model.read` 里的类型和底座公开的类型。比如 `CollectionReadPort.find(CollectionId)` 别的子域调不了，因为 `CollectionId` 在不公开的 `model.vo` 里。本版没有子域读 collection，这条约束先写在这里，PAP-113 定读端口签名时照此办理。

公开包之外的一切（聚合、实体、仓储、领域服务、app / infra / adapter 的任何类）只能在子域内部用。这就是 release spec「只经对方的读端口与底座交互，不直接用对方的实体或仓储」的精确范围。`model.read` 列进公开包，是因为读端口的返回类型按各服务现有写法放在 `domain.model.read`（identity 就是这样）。

规则：

| 方法 | 内容 |
|---|---|
| `layersContainOnlyDeclaredSubdomains()` | 五个层根包下的类必须落在某个已声明子域的包里。直接放在 `domain` 根下的类、未声明的子域包（比如有人顺手建了 `domain.common`）都违规。新增子域 = 改构造参数，这一步是刻意的 |
| `subdomainsAreFreeOfCycles()` | 按子域切片（跨层合并成一片），切片之间无环 |
| `crossSubdomainDependenciesGoThroughPublishedPackages()` | 子域 A 的类依赖子域 B（B ≠ A）的类时，目标必须在 B 的公开包里 |
| `foundationDependsOnNoOtherSubdomain()` | 底座的类不依赖任何其他子域的类，公开包也不行 |
| `allRules()` | 以上四条 |

切片用自定义 `SliceAssignment` 按上面的子域定义分配，不用 `slices().matching("…*.(*)..")`：后者会把 boot 的 `dev.patra.interaction.config.X` 这类包也当成子域。

### 5.3 `InteractionArchitectureTest`

在 interaction-boot 的 `src/test/java/dev/patra/interaction/architecture/`，写法照 `CatalogArchitectureTest`：导入 `dev.patra.interaction` 下全部模块的主代码（路径含 `/patra-interaction-` 的 jar 加当前模块），**排除** test-fixtures jar（路径含 `test-fixtures`）——`DO_NOT_INCLUDE_TESTS` 不排除它，不排除的话 infra 夹具里的 `dev.patra.interaction.infra.config` 会被判成未声明的子域。测试代码不受子域规则管。对 `HexagonalArchitectureRules("dev.patra.interaction", List.of("dev.patra.common..", "dev.patra.tool.."))` 的全部规则、`TestingRules`、`SubdomainArchitectureRules` 的全部规则逐条执行。`src/test/resources/archunit.properties` 照 catalog 设 `archRule.failOnEmptyShould = false`：骨架阶段多数规则没有要查的类。

本 Issue 里这个测试几乎没有东西可查，它的价值在 PAP-112 起每加一个类都被它管着。规则本身的正确性由 5.4 的测试保证。

### 5.4 规则的测试

`patra-tool-test/src/test/java/dev/patra/tool/test/archunit/` 下：

- `HexagonalArchitectureRulesTest`：夹具包 `…archunit.fixtures.subdomainlayout`，按子域布局放一个 `FooAggregate`、`FooEvent`、`FooOrchestrator`、`FooDO`；四条放宽的规则都通过。先写测试、看它在现在的写法下失败。
- `SubdomainArchitectureRulesTest`：每个场景一个夹具根包，各自是一个微型服务：

| 场景 | 夹具 | 期望 |
|---|---|---|
| 合规 | collection 的 app 类用 subject 的读端口、驱动端口、值对象；collection 的 adapter 用 collection 自己的仓储 | 四条都通过 |
| 用对方仓储 | collection 的 app 类用 `domain.subject.port.repository.SubjectRepository` | 公开包规则违规 |
| 用对方 app 类 | collection 的 adapter 用 `app.subject.SomeHandler` | 公开包规则违规 |
| 非底座的驱动端口 | subject 以外的子域 B 的 `domain.B.port.gateway` 被 collection 用 | 公开包规则违规 |
| 底座反向依赖 | subject 的类用 collection 的读端口 | 底座规则违规（同时形成环时环规则也违规） |
| 环 | collection 用 C 的读端口，C 用 collection 的读端口 | 环规则违规 |
| 层根下的类 | `{根}.domain.Loose` | 子域声明规则违规 |
| 未声明的子域 | `{根}.domain.common.Shared` | 子域声明规则违规 |
| boot 类 | `{根}.config.Wiring` 依赖两个子域的内部类 | 四条都通过（组装根不受管） |

夹具在测试源码里，导入时用 `ClassFileImporter().importPackages(夹具根包)`（不排除测试类）。断言用 `rule.evaluate(classes).hasViolation()`，违规场景再核对违规文本里点名了那个类。

现有的 `testfixtures/infra/InfraCompliantClass`、`InfraViolatingClass` 没有任何测试引用，是旧的死代码，本 Issue 不动它们。

## 6. 存储与 Flyway

### 6.1 库与 schema

- 库 `patra_interaction`，在 mini 现有的 `patra-postgres` 上。
- 两个 schema：`subject`、`collection`，名字就是子域名。
- 应用账号沿用 Nacos 公共配置里的 `postgres`（与其他服务一致）。

### 6.2 脚本

位置 `patra-interaction-infra/src/main/resources/db/migration/{子域}/`，每个子域一个子目录。Flyway 的 `classpath:db/migration` 会递归扫描子目录，所以只有一份历史；R6 的扫描范围是 `db/migration/**/*.sql`，同样覆盖子目录。

| 文件 | 内容 |
|---|---|
| `subject/V20261024100000__create_subject_schema.sql` | `CREATE SCHEMA subject;` 加一行注释说明这个 schema 放什么 |
| `collection/V20261024100100__create_collection_schema.sql` | `CREATE SCHEMA collection;` 加注释 |

版本号是 `yyyyMMddHHmmss`，取写脚本时的时间。子域各自加脚本时不会撞号；Flyway 按数值排序，跨子域的先后就是时间先后。README 写明这条约定。

### 6.3 Flyway 与 JPA 配置

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
```

**不**设 `spring.flyway.schemas`：设了 Flyway 会自己建这些 schema，并把历史表放进第一个，和脚本里的 `CREATE SCHEMA` 冲突，也违背「一份历史」的意图。不设时历史表 `flyway_schema_history` 在默认的 `public` schema。

不设 Hibernate 的 `default_schema`：PAP-112 / PAP-113 的实体用 `@Table(schema = "…")` 写明 schema，原生 SQL 用带 schema 的表名。这样每张表属于哪个子域在代码里一眼可见。

### 6.4 留给第一个建表的 Issue

- `set_updated_at()` 触发器函数：其他服务的首个脚本都定义它。interaction 有多个 schema，函数放在 `public` 还是每个 schema 一份，由 PAP-112 建第一张带 `updated_at` 的表时定。
- 表命名：有了 schema，表名不再需要服务前缀（identity 用 `idn_`）。PAP-112 定，README 那时补一行。

### 6.5 建库

- `patra-infra/docker/postgres/init-scripts/02-create-databases.sql` 加 `CREATE DATABASE patra_interaction;`，只在数据目录为空时执行。
- mini 现有的 PG 手工建（runbook 第 2 步），必须在服务第一次启动之前。

## 7. boot

### 7.1 启动类

```java
/// interaction 服务的启动入口：社区互动（本版是文献库与收藏夹）。
///
/// 配置从 Nacos 读（`patra-common.yaml`、`patra-interaction.yaml`），由启动器导入。
@SpringBootApplication
public class PatraInteractionApplication {
  public static void main(String[] args) {
    PatraApplication.run("patra-interaction", PatraInteractionApplication.class, args);
  }
}
```

### 7.2 `application.yml`

| 键 | 值 | 说明 |
|---|---|---|
| `server.port` | `6500` | 现有服务占 6000–6400，6500 / 6501 在 `patra-infra` 和各服务配置里都没有人用 |
| `server.forward-headers-strategy` | `framework` | 同 identity：OpenAPI 文档里的地址指向网关 |
| `spring.datasource.driver-class-name` 与 `hikari.*` | 同 identity（池 10、最小空闲 2、超时同） | 库地址、账号在 Nacos |
| `spring.flyway.*` | 6.3 | |
| `spring.jpa.open-in-view` | `false` | 同 identity |
| `patra.web.error.context-prefix` | `INTERACTION` | 决策 A。错误码形如 `INTERACTION-0404` |
| `xxl.job.executor.ip` / `logpath` / `logretentiondays` | 同 identity，日志目录 `…/xxl-job/patra-interaction` | 管理端地址在 Nacos 公共配置 |

不写 `spring.application.name`、`spring.profiles.active`、`spring.cloud.nacos.*`（`ApplicationConfigTest` 守着）。不写 `spring.data.redis.*`。

### 7.3 XXL-Job 执行器

`dev.patra.interaction.config.XxlJobConfig`，内容照 identity 的 `XxlJobConfig`：`@ConditionalOnProperty(prefix = "xxl.job", name = "enabled", havingValue = "true", matchIfMissing = true)`，appname 取 `spring.application.name`（即 `patra-interaction`），端口 = `server.port + 1` = 6501。复制而不是抽进工具模块：本版不新增基础组件，三个服务现在也都是各自一份。

执行器在零个任务时也会向调度中心注册地址，所以部署后在调度中心就能看到它在线（runbook 第 5 步）。

它没有单元测试：`XxlJobSpringExecutor` 不暴露端口、appname 的读取方法，不用反射就断言不了；构造它的上下文会在 6501 上起 netty 服务。它是三个服务现有配置的原样复制，由部署后「执行器在线、地址是 6501」验收（核对单 #14）。集成测试里 `xxl.job.enabled: false` 关掉它，漏关时测试上下文会因缺 `xxl.job.admin.addresses` 启动失败，这一点由启动集成测试兜住。

### 7.4 Nacos 配置 `patra-infra/nacos/dev/patra-interaction.yaml`

```yaml
patra.config-source.service: patra-interaction.yaml

spring:
  datasource:
    url: jdbc:postgresql://${PATRA_INFRA_HOST:127.0.0.1}:15432/patra_interaction
```

账号、密码、XXL-Job 管理端地址都在公共配置里。没有密钥，也就没有 `~/.patra/secrets/interaction.env` 的必需项。`NacosConfigFilesTest` 和 `JarConfigOverlapChecker` 都按目录自动发现新文件，不用登记；后者按 `patra-interaction/patra-interaction-boot/src/main/resources` 找 jar 内配置，正好是本服务的布局。

## 8. 测试

| 测试 | 位置 | 断言 |
|---|---|---|
| `HexagonalArchitectureRulesTest`、`SubdomainArchitectureRulesTest` | patra-tool-test `src/test` | 第 5.4 节 |
| `InteractionArchitectureTest` | interaction-boot `src/test` | 第 5.3 节 |
| `ApplicationConfigTest` | interaction-boot `src/test` | 照 identity：classpath 上没有 `application-dev.yml` / `application-container.yml`；`application.yml` 不写服务名、激活的 profile、Nacos 接入；错误码前缀是 `INTERACTION`；不设 `spring.flyway.schemas` |
| `InteractionMigrationIT` | interaction-infra `src/integrationTest` | `@SpringBootTest(classes = InteractionITBootstrap.class)` 加 PG 容器：`subject`、`collection` 两个 schema 存在；`flyway_schema_history` 只在 `public` 有一张，记录两条成功的迁移，版本号就是两个时间戳；库里除 `public`、两个子域 schema 与 PG 自带的 schema 外没有别的 |
| `PatraInteractionApplicationIT` | interaction-boot `src/integrationTest` | 应用以 `test` profile 起来（不连 Nacos、不起执行器）；`/actuator/health` 为 UP；`patra.web.error.context-prefix` 是 `INTERACTION` |
| 网关 `GatewayRoutingIT` 新增一例 | gateway `src/integrationTest` | `/patra-interaction/x?y=1` 剥掉前缀转到 `lb://patra-interaction`（WireMock 顶替，`SimpleDiscoveryClient` 指过去），查询串原样 |

测试基座：

- `InteractionITBootstrap`：interaction-infra `src/integrationTest/java/dev/patra/interaction/infra/`，`@SpringBootApplication` 空类，同 identity。
- `InteractionITPostgreSQLContainerInitializer`：继承 `PostgreSQLContainerInitializer`，库名 `patra_interaction`。放在 interaction-infra 的 `src/testFixtures`（`dev.patra.interaction.infra.config`），infra 的迁移测试和 boot 的启动测试共用，不像 identity 那样两处各一份。
- `application-test.yml`：infra、boot 的集成测试各一份，关 Nacos 发现与注册、`xxl.job.enabled: false`，写法同 identity。

`detect-changes.sh` 与 `deploy.sh` 的 shell 测试见第 10 节。

## 9. 网关

`patra-gateway-boot/src/main/resources/application.yml`：

```yaml
            # Interaction Service Route（文献库、收藏夹，以后的讨论、关注、通知）
            # Example: /patra-interaction/... -> lb://patra-interaction/...
            - id: patra-interaction
              uri: lb://patra-interaction
              predicates:
                - Path=/patra-interaction/**
              filters:
                - StripPrefix=1
```

`scalar.sources` 加 `/patra-interaction/v3/api-docs`（title `Interaction Service`，slug `interaction`）。`PatraGatewayApplication` 的 Javadoc 里的服务清单补上 interaction。

鉴权规则不改：第二条过滤器链对 identity 以外的路径 `anyRequest().permitAll()`，带令牌时照样查会话、写身份断言，匿名时不写。哪些接口要登录由 interaction 自己的安全 starter 判定（PAP-113 接入），这和 release spec 决策 H「公开收藏夹页匿名可看、已登录时带会话」一致。拒绝名单 `/*/_internal/**`、`/*/admin/**`、`/*/actuator/**` 按第一段通配，自动覆盖 interaction。

网关 jar 改了，CD 会重新部署网关（`patra-gateway-boot` 属于 gateway 单元）。

## 10. 新服务接入核对单

「证据」一栏是打勾要附的东西；后端 PR 的描述逐条复制这张表。#9 的 mini 部分、#11、#13 的 mini 部分、#14、#15 要等后端 PR 合进 main、CD 部署之后才能打勾，其余在合回版本分支前打勾。

`module-graph.json` 是生成文件，`detect-changes.test.sh` 里有写死的单元数组，并行的 catalog、identity 分支也可能动到它们。各分支合回 `feat/v0.13-library-api` 时如有冲突，以重新执行 `./gradlew dumpModuleGraph` 的结果为准（CI preflight 会拦下陈旧的模块图），测试数组按合并后的单元集合改。

| # | 项 | 位置 | 改动 | 证据 |
|---|---|---|---|---|
| 1 | Gradle 注册 | `settings.gradle.kts` | 4.1 | `./gradlew projects` 列出六个模块 |
| 2 | **`unitOf`** | `build.gradle.kts` 的 `dumpModuleGraph` | 加 `patra-api/patra-interaction` → `interaction` 分支 | `module-graph.json` 里 interaction 模块的 `unit` 都是 `interaction`，不是 `foundation` |
| 3 | `units` 列表 | 同上，`root["units"]` | 加 `interaction` | `module-graph.json` 的 `units` 含 `interaction` |
| 4 | 模块图 | `patra-infra/cd/module-graph.json` | `./gradlew dumpModuleGraph` 重新生成 | 生成后 `git diff` 只多出 interaction 的模块，以及 `patra-common`、tool 模块的 `impacts` 多出 `interaction` |
| 5 | **`ALL_UNITS`** | `patra-infra/cd/detect-changes.sh` 第 22 行及第 6 行注释 | 加 `interaction` | 下一行 |
| 6 | `detect-changes` 测试 | `patra-infra/cd/detect-changes.test.sh` | 第 44、99 行写死的单元数组加 `interaction`；照场景 13 新增场景：interaction 私有改动只跑 interaction、全量运行包含 interaction、`nacos/dev/patra-interaction.yaml` 只重部署 interaction | `bash patra-infra/cd/detect-changes.test.sh` 全部通过（新场景先红后绿） |
| 7 | **`services.json`** | `patra-infra/cd/services.json` | 新条目放在 catalog 之后、ingest 之前：`name` interaction、`gradleTask` `:patra-api:patra-interaction:patra-interaction-boot:bootJar`、`context` 与 `srcPrefix` 按路径、`port` 6500、`image` `ghcr.io/linqibin0826/patra-interaction`、`healthPath` `/actuator/health`、`healthMatch` `"status":"UP"`；`_comment` 的部署顺序说明补一句 | `deploy.test.sh` 新增场景 7：请求 `["interaction","catalog"]`，两个都按 catalog、interaction 的顺序 `up` 并记下 last-good（加条目前以「未知服务」退出 2，先红后绿）；全部场景通过 |
| 8 | compose | `patra-infra/docker/docker-compose.apps.yaml` | 新服务块（第 10.1 节）；头部注释「6 个后端服务必需」改为 7 个 | 临时 `HOME` 里放假的 `nacos.env` 后 `docker compose -f docker-compose.apps.yaml config` 能解析，且 interaction 的 `env_file` 与端口正确 |
| 9 | 建库 | `02-create-databases.sql` + mini 手工 | 6.5 | 文件 diff；mini 上 `SELECT datname FROM pg_database` 有 `patra_interaction` |
| 10 | Nacos 配置 | `patra-infra/nacos/dev/patra-interaction.yaml` | 7.4 | `NacosConfigFilesTest`、`JarConfigOverlapCheckerTest` 通过 |
| 11 | **`nacos-verify.sh`** | `patra-infra/scripts/nacos-verify.sh` | `SERVICES` 加 `patra-interaction`；第 10 项描述与头部注释的「6 个」改为 7 个 | mini 上部署后 `nacos-verify.sh --expect-services` 第 10 项 PASS |
| 12 | **CI 镜像常量** | `.github/workflows/ci.yml` 的 preflight | 不改：它只核对 `patra-tool-test` 的 PG、Redis 初始化器常量和 ingest 的 RocketMQ compose；interaction 的 PG 初始化器继承基座，不引入新镜像 | 在 PR 里引用 preflight 那段的行号说明 |
| 13 | 网关路由 | `patra-gateway-boot` 的 `application.yml` | 第 9 节 | `GatewayRoutingIT` 新例；mini 上经网关访问 `/patra-interaction/v3/api-docs` 200 |
| 14 | XXL-Job 执行器 | boot `XxlJobConfig` + 调度中心建执行器 | 7.3、11.1 | 调度中心「执行器管理」里 `patra-interaction` 在线，地址是容器 IP 的 6501 |
| 15 | 执行器端口 | compose | 不映射 6501：调度中心经 `patra-net` 回调容器 IP，identity 也只映射了 6400 | 同上，手动触发一次空跑验证可达（PAP-112 有任务后） |
| 16 | IDEA 运行配置 | `.run/PatraInteractionApplication.run.xml` | 照 identity | 文件存在，module 名 `patra.patra-api.patra-interaction.patra-interaction-boot.main` |
| 17 | 文档 | 第 13 节 | | `git grep` 第 13 节的关键字，没有遗漏的「6 个后端服务」 |

### 10.1 compose 服务块

```yaml
  interaction:
    image: ghcr.io/linqibin0826/patra-interaction:${INTERACTION_IMAGE_TAG:-latest}
    container_name: patra-interaction
    restart: unless-stopped
    ports: [ "6500:6500" ]
    extra_hosts: *infra-hosts
    env_file:
      - .env.common
      - path: ${HOME}/.patra/secrets/nacos.env
        required: true
      - path: ${HOME}/.patra/secrets/interaction.env
        required: false
    healthcheck:
      test: [ "CMD", "curl", "-fs", "http://localhost:6500/actuator/health" ]
      <<: *app-healthcheck
```

放在 catalog 块之后。`INTERACTION_IMAGE_TAG` 是 `deploy.sh` 的 `tag_var` 按服务名推出的变量名。不加 `redis.env`：interaction 不连 Redis，加了反而让 Redis 密码文件成为它的启动前提。

## 11. XXL-Job

### 11.1 执行器（本 Issue 登记）

| 项 | 值 |
|---|---|
| AppName | `patra-interaction` |
| 名称 | `互动服务` |
| 注册方式 | 自动注册 |

在调度中心「执行器管理」新建。执行器在服务启动时上报地址，建执行器在前在后都可以；runbook 放在合并前，让第一次部署后马上能看到它在线。

### 11.2 两个每日任务（名字在这里定，PAP-112 / PAP-113 实现并登记）

| 任务 | handler | Cron | 归属 | 时间的理由 |
|---|---|---|---|---|
| 对象对账 | `subjectReconciliationJob` | `0 30 6 * * ?` | PAP-112（`adapter.subject.scheduler`） | catalog 的每日增量同步 05:00 开始，对账排在它之后，拿到当天的新 ID 和软删状态 |
| 注销清理 | `deletedUserCleanupJob` | `0 0 6 * * ?` | PAP-113（`adapter.collection.scheduler`） | identity 的注销到期执行每小时第 5 分跑，06:00 清理能接住凌晨到期的账号；避开 identity 04:30、04:40 两个清理任务 |

两个任务都是路由策略「第一个」、阻塞处理「单机串行」、超时与重试按各自 Issue 定。实现的 Issue 若要改名或改时间，同时改 runbook 第 6 步。

## 12. 本机启动与 mini 上的操作

### 12.1 本机启动（完成标准之一）

本机 dev 连 mini 的 PG 和 Nacos，所以先要有两样东西，都在 mini 上，**动手前先问用户**：

1. `patra_interaction` 库（runbook 第 2 步）。
2. Nacos 里的 `patra-interaction.yaml`。`nacos-push.sh` 是全量同步，Nacos 里有、目录里没有的会被删掉，所以先从本分支跑 `--check`：预期差异只有「新增 patra-interaction.yaml」，以及版本分支上已有、main 上还没有的配置改动；出现说不清来源的差异就停下。确认后再推。

   **这份配置在后端 PR 合进 main 之前会被反复删掉**：main 每次有后端路径的推送，CD 的 `nacos-push` 都按 main 的目录全量同步一遍。本机启动报「没读到签到」时，就从版本分支按上面的步骤重推一次。不为此加本地覆盖配置之类的机制：本版后端改动都在版本分支上，main 的后端推送很少，重推是一条命令。PAP-112 / PAP-113 的本机开发照此办理，runbook 第 3 步写明。

启动：IDEA 运行配置或 `bootRun`，环境变量 `PATRA_INFRA_HOST=100.103.73.27`、`PATRA_ADVERTISE_IP=<本机 tailscale IP>`；JVM 会继承 macOS 系统代理，按本机惯例先导出 `JAVA_TOOL_OPTIONS` 覆盖代理设置。验证：日志里 Flyway 执行了两个脚本；`curl 127.0.0.1:6500/actuator/health` 为 UP；Nacos `public` 命名空间下 `patra-interaction` 有本机实例。验证完立即停掉，避免 mini 网关把请求分到本机实例。

### 12.2 mini 上的操作

建库、Nacos 推送、在调度中心建执行器、合并后的部署检查，全部按 runbook 执行，每一步动手前先问用户。

## 13. 文档

| 文件 | 改动 |
|---|---|
| `patra-api/patra-interaction/README.md` | 新建。职责；子域与包结构；公开包（5.2 的表）；存储（schema、一份历史、时间戳版本号、不建跨 schema 外键、引用完整性由写入路径和对账保证）；端口 6500 / 6501、错误码前缀、网关前缀；**拆分条件**：某个子域的负载、发布节奏或存储引擎明显和其余部分分化时，把它剥成独立服务——它的 schema 整体搬走、公开包变成 HTTP 契约（放进 api 模块）；拆分前先确认它和其他子域之间只剩公开包上的依赖（ArchUnit 已保证） |
| `patra-api/README.md` | 微服务表加一行 |
| 根 `README.md` | 第 166 行的服务清单 |
| `.claude/rules/project-info.md` | 核心服务列表加 `patra-interaction` |
| `patra-infra/CLAUDE.md` | 第 32 行 apps 栈清单；第 72 行「6 个后端服务」 |
| `patra-infra/docker/README.md` | 服务与端口清单、数据库清单；identity 任务那段之后加一句 interaction 的执行器 |
| `patra-infra/nacos/dev/README.md`、`.env.common` 头注释 | 「6 个后端服务」改为 7 个 |
| `docs/patra/runbooks/v0.13-library-go-live-runbook.md` | 新建，第 14 节 |

实施时用 `git grep -n "6 个\|六个\|identity / gateway\|identity、gateway"` 再扫一遍，按命中补齐。

## 14. 上线 runbook 的结构

`docs/patra/runbooks/v0.13-library-go-live-runbook.md`。这是整个 v0.13 的上线手册，本 Issue 写 interaction 的部分并留出其他 Issue 的位置；每步给出命令、预期结果、失败时的动作，改动 mini 的步骤先确认上一步的预期全部满足。

| 步 | 时机 | 内容 |
|---|---|---|
| 0 | — | 环境与约定（SSH、两份检出、last-good、调度中心地址），表格写法同 v0.8 runbook |
| 1 | 合并前 | 记录基线（D12）：`docker stats --no-stream`、`df -h /` 的剩余空间，要求 ≥ 20 GB |
| 2 | 合并前 | 6500、6501 端口空闲；`patra_interaction` 不存在 → 建库 → 再查一次存在 |
| 3 | 本机冒烟需要时（开发期可能多次） | Nacos：`nacos-push.sh --check`，差异只有新增 `patra-interaction.yaml` 和版本分支上的配置改动才推；后端 PR 合进 main 之前，main 的每次后端推送都会把它删掉，本机启动报缺签到时重做这一步（12.1） |
| 4 | 合并前 | 调度中心建执行器 `patra-interaction`（11.1），可以用控制台，也可以用接口（附 curl） |
| 5 | 后端 PR 合进 main、CD 结束 | 核对：CD 的 nacos-push 成功；`docker ps` 里 `patra-interaction` healthy；`curl 127.0.0.1:6500/actuator/health` 为 UP；`curl 127.0.0.1:9528/patra-interaction/v3/api-docs` 为 200；`nacos-verify.sh --expect-services` 全部 PASS；调度中心里执行器在线；再记一次 `docker stats` 与 `df -h /`，与第 1 步对比写进 release spec D12 的「实际：」 |
| 6 | 第 5 步之后（后端 PR 已含 PAP-112 / PAP-113） | 按 11.2 建两个任务；各手动触发一次，执行日志成功、服务日志有处理条数 |
| — | — | 其他 Issue 的上线步骤（如 catalog 回填重导）由对应 Issue 补在第 6 步之后 |
| 退路 | — | interaction 第一次部署没有 last-good，不健康时停在 unhealthy，不影响其他服务；网关回滚由 `deploy.sh` 自动完成，回滚后 `/patra-interaction/**` 只是 404。改了 `patra-tool-test` 会让 CD 重部署全部后端服务（foundation 单元扇出），这是预期的 |

## 15. 我的决定（请评审时重点看）

1. 子域的公开包范围（5.2）：所有子域公开 `port.read`、`model.read`；底座另公开 `port.gateway`、`model.vo`、`model.enums`。release spec 只说了「读端口与底座」，这张表是它的落地，直接决定 PAP-112 的包怎么分。
2. 子域规则放 `patra-tool-test`，用夹具做红绿测试；`InteractionArchitectureTest` 只负责应用。
3. 「层下面只有已声明的子域」作为一条硬规则：不留 `common` 之类的共享包。
4. boot 的测试类路径显式带上 domain、app、api，让架构测试覆盖全部模块。
5. 迁移脚本按子域放子目录，版本号 `yyyyMMddHHmmss`；不设 `spring.flyway.schemas`；不设 Hibernate 默认 schema。
6. `set_updated_at()` 与表命名留给 PAP-112。
7. XXL-Job 执行器配置放 boot 的 `dev.patra.interaction.config`；任务处理器以后放各子域的 adapter 包。
8. 端口 6500 / 6501；部署顺序在 catalog 之后、ingest 之前。
9. 两个每日任务的 handler 名与 Cron（11.2）。
10. PG 容器初始化器放 infra 的 `testFixtures`，infra 与 boot 共用。
11. compose 不加 `redis.env`，不映射 6501。
12. 安全 starter 留给 PAP-113。
13. 架构测试排除 test-fixtures jar，测试代码不受子域规则管。
14. 后端 PR 合进 main 之前，本机开发需要时手动重推 Nacos 配置，不另加本地覆盖机制（12.1）。

## 16. 需要同步给其他地方的修订

- release spec 决策 A 与 Issue PAP-111 的网关前缀 `/interaction/**` → `/patra-interaction/**`；PAP-111 验收第 1 条「经网关能访问健康端点」→ 第 3 节答复 ② 的两条。Linear 的 PAP-111 由本会话更新；release spec 在主检出里尚未提交，由发起本会话的规划会话改。
- PAP-114（门户调 interaction 的公共层）按 `/patra-interaction/**` 拼地址。

## 17. 实施记录（2026-10-24）

### 17.1 与本设计的偏离

| # | 偏离 | 原因 |
|---|---|---|
| 1 | domain、app、infra、adapter 四个模块的 `compileJava` 加 `-Xpkginfo:always`；`InteractionArchitectureTest` 多一条「四层的两个子域包都在导入结果里」 | 只有注释的 `package-info.java` 默认不产出 class 文件，骨架的四层在 ArchUnit 里是空的，层规则报「Layer is empty」。实施时先给共享层规则加了 `withOptionalLayers(true)`，终审指出这会让 catalog、ingest 失去唯一的「没导入到类」信号，改成现在的做法：共享规则保持严格，interaction 用编译参数让子域包有 class，再用导入断言做长期护栏（先红后绿） |
| 2 | `SubdomainArchitectureRules` 声明为 `final` | SpotBugs 的 `CT_CONSTRUCTOR_THROW`（构造器校验参数时抛异常）；final 类不触发，不加排除规则 |
| 3 | `detect-changes.test.sh` 原第 44 行的期望补上 `gateway` | 该行在 HEAD 上就失败：PAP-106 后网关经 `identity-api` 间接依赖 `patra-common`，模块图早已包含 gateway，期望没更新 |
| 4 | 新场景编号为 15（不是计划写的「场景 13 之后」），放在文件末尾 | 场景 14 已存在 |
| 5 | 迁移测试的 `should_keep_a_single_flyway_history_in_public` 在没有脚本时就通过 | Flyway 没有迁移时也建历史表。保留它作护栏：防止有人设 `spring.flyway.schemas` 把历史挪走 |
| 6 | 本机 `bootRun` 加 `-Potel.agent.path=` | worktree 里没有 `patra-infra/docker/opentelemetry-javaagent.jar`（被 git 忽略，只在主检出） |
| 7 | `./gradlew check` 只编译集成测试、不执行，集成测试另外显式跑 | 与 `.claude/rules/testing/conventions.md` 的写法不符，以实际输出为准 |
| 8 | 文档同步多改了 `patra-infra/docker/service.Dockerfile` 头注释、`secrets/nacos.env.example`、`JarConfigOverlapCheckerTest` 的显示名与 `JarConfigOverlapChecker` 的注释、`patra-tool-launch/build.gradle.kts` 的注释、`patra-hexagonal` 技能的 `configuration.md`、网关 README 的路由清单 | 第 13 节的 `git grep` 扫到的「6 个后端服务」 |
| 9 | `InteractionArchitectureTest` 比 catalog 多跑 `gatewayImplsShouldResideInApp` | 第 5.3 节要求全部规则逐条执行；底座的 `port.gateway` 实现要放 `app.subject`，这条规则正好守它 |

### 17.2 核对单（第 10 节）

| # | 项 | 结果 | 证据 |
|---|---|---|---|
| 1 | Gradle 注册 | ✅ | `./gradlew :patra-api:patra-interaction:projects` 列出六个子模块 |
| 2 | `unitOf` | ✅ | `module-graph.json` 里六个 interaction 模块的 `unit` 都是 `interaction` |
| 3 | `units` 列表 | ✅ | `module-graph.json` 的 `units` 为 `["registry","object-storage","catalog","ingest","identity","interaction","gateway","foundation"]` |
| 4 | 模块图 | ✅ | `./gradlew dumpModuleGraph` 后 diff 只多出 interaction 模块，以及依赖方的 `impacts` 多出 `interaction` |
| 5 | `ALL_UNITS` | ✅ | `detect-changes.sh` 第 22 行含 `interaction` |
| 6 | `detect-changes` 测试 | ✅ | 场景 15 先红（5 处 ✗）后绿；`通过 66 / 失败 0` |
| 7 | `services.json` | ✅ | `deploy.test.sh` 场景 7 先红后绿，`PASS=7 FAIL=0`；部署顺序 object-storage、registry、identity、gateway、catalog、interaction、ingest |
| 8 | compose | ✅ | 临时 `HOME` 下 `docker compose config`：镜像 `ghcr.io/linqibin0826/patra-interaction:latest`、端口 6500、环境叠加 `.env.common` → `nacos.env` → `interaction.env`、不带 `REDIS_PASSWORD`；删掉 `nacos.env` 时报出完整路径，`interaction.env` 缺失时照常 |
| 9 | 建库 | ✅ 脚本；✅ mini | `02-create-databases.sql` 加了一行；mini 上 6500 / 6501 空闲、库原先不存在，`CREATE DATABASE` 后查询为 `1`（2026-10-24） |
| 10 | Nacos 配置 | ✅ | `NacosConfigFilesTest` 18 个、`JarConfigOverlapCheckerTest` 6 个全部通过；mini 上 `--check` 差异只有「新增 patra-interaction.yaml」，推送后再查 8 份全部未变化 |
| 11 | `nacos-verify.sh` | ✅ 脚本；⏳ mini | `SERVICES` 含 `patra-interaction`，`bash -n` 通过；`--expect-services` 待后端 PR 合进 main、CD 部署后跑 |
| 12 | CI 镜像常量 | ✅ 不改 | `.github/workflows/ci.yml` preflight 只核对 `PostgreSQLContainerInitializer` / `RedisContainerInitializer` 的常量和 ingest 的 RocketMQ compose；`InteractionITPostgreSQLContainerInitializer` 继承基座 |
| 13 | 网关路由 | ✅ IT；⏳ mini | `GatewayRoutingIT.should_route_interaction_prefix_to_interaction_service` 先红（404）后绿，`GatewayBlockedPathsIT` 照旧通过；mini 上经网关访问 `/patra-interaction/v3/api-docs` 待部署后核对 |
| 14 | XXL-Job 执行器 | ✅ 本机；⏳ mini | 本机启动日志 `XXL-Job 执行器初始化完成，appName=patra-interaction … port=6501`；调度中心建执行器（runbook 第 4 步）与在线核对待做 |
| 15 | 执行器端口 | ⏳ | compose 不映射 6501；可达性待 PAP-112 有任务后手动触发验证 |
| 16 | IDEA 运行配置 | ✅ | `.run/PatraInteractionApplication.run.xml`，module `patra.patra-api.patra-interaction.patra-interaction-boot.main` |
| 17 | 文档 | ✅ | `git grep` 已无指后端服务清单的「6 个」（历史 spec、计划、旧 runbook 不改） |
| 18 | 架构测试确实导入了类 | ✅ | 「规则 22b」断言八个子域包都在导入结果里（加 `-Xpkginfo:always` 前红、后绿）；另临时放 `dev.patra.interaction.domain.Loose`，规则 23 失败并点名该类，删掉后全绿；infra testFixtures 的 `infra.config` 在类路径上但未被判违规（排除生效） |

### 17.3 门禁

- `./gradlew spotlessApply && ./gradlew check`：BUILD SUCCESSFUL（4 分 1 秒）。
- 显式集成测试 `:patra-interaction-infra:integrationTest`、`:patra-interaction-boot:integrationTest`、`:patra-gateway-boot:integrationTest`、`:patra-tool-launch:test`：BUILD SUCCESSFUL。
- `detect-changes.test.sh`：通过 66 / 失败 0；`deploy.test.sh`：PASS=7 FAIL=0。

### 17.4 本机启动（2026-10-24，连 mini 的 PG 与 Nacos）

`PATRA_INFRA_HOST=100.103.73.27 PATRA_ADVERTISE_IP=100.70.109.37 ./gradlew :patra-api:patra-interaction:patra-interaction-boot:bootRun -Potel.agent.path=`（导出 `JAVA_TOOL_OPTIONS` 覆盖代理例外）：

- Flyway：`Successfully applied 2 migrations to schema "public", now at version v20261024100100`。
- `nacos registry, DEFAULT_GROUP patra-interaction 100.70.109.37:6500 register finished`；Nacos 接口查 `public` 下 `patra-interaction`：`[{"ip":"100.70.109.37","port":6500,"healthy":true}]`。
- `curl 127.0.0.1:6500/actuator/health` → `{"status":"UP"}`；`/v3/api-docs` → 200。
- 验证后停掉，再查 Nacos 实例列表为空。
