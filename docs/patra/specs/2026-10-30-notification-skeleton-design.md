# notification 服务骨架与部署接入工程设计（PAP-139）

> 状态：已实施（2026-10-30，计划 `docs/patra/plans/2026-10-30-notification-skeleton.md`）；子域方案、端口与 mini 上的两项操作经编排会话转达用户同意；实施中的偏离与核对结果见第 20 节
> 日期：2026-10-30
> Issue：PAP-139（GitHub #326），Linear Project P-PAP-13 v0.16 Notification，Milestone BE Ready
> 依据：release spec `docs/patra/release-specs/v0.16-notification.md` 2.1、第 3 节 C（只跑一个实例）、第 4 节决策 A / D / H / I，第 5 节 D2、D3、D10
> 模板：interaction 的骨架（`docs/patra/specs/2026-10-24-interaction-skeleton-design.md`，下称「interaction 骨架 spec」）。本文只写和它不同的地方与本服务自己的决定，相同的做法注明「同 interaction」

## 1. 要解决的问题

v0.16 把站内提醒、提醒偏好与退订、邮件、实时推送都交给新服务 `patra-notification`（决策 A）。后续 Issue（PAP-143 信箱、PAP-144 有帮助、PAP-142 偏好、PAP-105 / PAP-146 邮件、PAP-145 推送）要在一个已经能构建、能测试、能部署的服务里写代码。本 Issue 把服务搭起来，并一次接好部署：

1. 六层模块接进构建，每层按子域分包；定下这个服务的子域，以及后续每个 Issue 的代码落在哪个子域。
2. 子域边界由架构测试守住（`NotificationArchitectureTest`）。
3. 库 `patra_notification`，Flyway 接好。
4. 部署：Nacos、网关路由、CD、compose、XXL-Job 执行器全部接好。
5. 写 v0.16 的上线 runbook 起稿，后续 Issue 往里加步骤。

**完成标准**分两个时间点（本版后端只开一个 PR）：

- **汇入版本分支前**（本 Issue 交付）：`./gradlew check` 与 notification、网关的集成测试通过，domain 层纯净；两个 CD 脚本测试通过；核对单（第 12 节）里不依赖部署的各项打勾。用户同意在 mini 上建库、推 Nacos 配置后，再加本机启动验收（第 15 节）。
- **后端 PR 合进 main 后**（编排会话按 runbook 做）：容器 healthy、直连健康 UP、经网关访问 OpenAPI 文档 200、Nacos 里有实例、执行器在线。

**关于「经网关能访问健康检查」**：网关对所有服务的 `/*/actuator/**` 一律回 403（`GatewaySecurityConfiguration.BLOCKED_PATHS`）。照 interaction 的先例（interaction 骨架 spec 第 3 节答复 ②），这条验收拆成两条：直连 6600 端口时 `/actuator/health` 为 UP；经网关访问 `/patra-notification/v3/api-docs` 返回 200。

## 2. 先讲清三个概念

这一版想借通知服务把 DDD 的几个概念弄明白，下面先用 patra 现有的东西讲，再讲 notification 怎么选。

### 2.1 六层各管什么

一个服务拆成六个 Gradle 模块，叫「六边形架构」：业务规则在正中间，数据库、HTTP、消息队列这些外部技术都在外圈，外圈依赖里圈，里圈不知道外圈的存在。拿 interaction 的「有帮助」举例：

| 层 | 职责 | 「有帮助」在这一层的样子 |
|---|---|---|
| domain（领域层） | 纯 Java 的业务规则，不依赖 Spring、JPA | 「不能给自己点」的判断；仓储接口 `HelpfulRepository`（只说要什么，不说怎么存） |
| app（应用层） | 编排一个用例：开事务、调领域对象、调端口 | `ToggleHelpfulHandler`：查资格 → 拿锁 → 插明细 → 计数 +1，事务边界只在这一层 |
| infra（基础设施层） | 实现领域层定义的接口：SQL、调别的服务 | `HelpfulRepositoryAdapter` 用 JdbcClient 写 `reaction.helpful` 表 |
| adapter（适配器层） | 把外部请求翻译成用例调用：REST、定时任务、消息监听 | `HelpfulController` 接 `PUT /helpful`，转成命令交给命令总线 |
| api（契约层） | 给别的服务调用的接口与 DTO | interaction 没有别的服务调它，这层是空的 |
| boot（启动层） | 启动类、把以上各层装配起来、配置 | `PatraInteractionApplication`、XXL-Job 执行器配置 |

好处是换存储、换协议时只动外圈；代价是一个功能要在几层各写一点。domain 层是否纯净由构建插件强制（R3：编译类路径白名单），不是靠自觉。

### 2.2 子域

**子域**是一个服务内部按业务切出来的一块，有自己的数据、规则和用例。interaction 一个服务里有收藏（`collection`）、讨论（`discussion`）、投稿经验（`post`）、有帮助（`reaction`）等六个子域。每一层里都先按子域分包，比如 `dev.patra.interaction.domain.discussion`、`dev.patra.interaction.infra.discussion`，所以讨论相关的代码在每层都聚在一起。

为什么要切：子域之间只能经对方**公开的包**交互（读端口 `port.read`、读模型 `model.read`、事件 `event`），不能碰对方的表、实体和仓储；跨子域的依赖还要在架构测试里**声明**。这样一来，哪天某个子域长大了要拆成独立服务（这一版的提醒就是这样从 interaction 搬出来的），它和别人之间只剩几条声明过的边，把公开包换成 HTTP 或消息就行。

### 2.3 底座

**底座**（每个服务一个，也叫共享内核）是一个特殊的子域：对同服务的其他子域完全公开，谁都能用、不用声明；但它自己不依赖任何别的子域，而且**只放被两个以上子域共用的东西**。interaction 的底座是 `subject`（对象登记表、用户名片、发表守卫、注销清理流程），catalog 和 registry 的底座叫 `kernel`。

底座的代价是它一改，所有子域都受影响；所以「只有一个子域用的东西」哪怕看着通用，也放回那个子域。

## 3. 范围

**做**：

- `patra-api/patra-notification/` 下六个模块与 `settings.gradle.kts` 注册。
- 五个子域的 `package-info.java`（domain / app / infra / adapter 四层，各写子域职责）。
- boot：启动类、`application.yml`、XXL-Job 执行器、`NotificationArchitectureTest`、`ApplicationConfigTest`、启动集成测试。
- infra：集成测试的启动类、PG 容器初始化器（放 testFixtures，boot 共用）、迁移集成测试。
- 网关：路由、OpenAPI 文档聚合、路由集成测试。
- `patra-infra`：compose、建库脚本、Nacos 配置、CD 清单、`detect-changes.sh` 及其测试、`deploy.test.sh`、`nacos-verify.sh`。
- 根构建脚本 `dumpModuleGraph` 的 `unitOf` 与 `units`，重新生成 `module-graph.json`。
- IDEA 运行配置 `.run/PatraNotificationApplication.run.xml`。
- 服务 README、v0.16 上线 runbook 起稿、同步各处的服务清单（第 16 节）。

**不做**（归后续 Issue）：

- 任何表、实体、端口、用例、接口，以及业务 schema：随 PAP-143 / 142 / 105 / 146 / 145 的代码来。
- 安全 starter（`patra-tool-security`）、调 identity 的 HTTP Interface 客户端：第一个要登录的接口、第一个调用 identity 的 Issue 再接。
- RabbitMQ 的客户端依赖与消费（PAP-143 / 105）；RabbitMQ 容器与公共配置（PAP-138）；事件模块（PAP-137）；SMTP 配置与 `mail.env`（PAP-105）。凭据接线按 PAP-138 的约定由本 Issue 做（9.4、第 13 节）。
- 推送路由的独立连接池与 SSE 超时（PAP-145）。
- 具体的定时任务与它们在调度中心的登记（第 14 节列出归属）。

## 4. 已定的前提

| 来源 | 内容 |
|---|---|
| 决策 A | 新服务 `patra-notification` 接管站内提醒、偏好、邮件、汇总与推送；interaction 只发事件 |
| 第 3 节 C | 只跑一个实例：SSE 连接挂在实例内存里 |
| 决策 D | 消费端按事件 ID 去重：「已处理事件」表与业务数据同一事务写入 |
| 决策 H | 在站信号由通知服务自己判断：推送连接在线，或拉过未读数 |
| 决策 I | SSE 放在通知服务里，提醒写入的事务提交后再推 |
| 分包规范 | `.claude/rules/tech/package-layout.md`，由 `SubdomainArchitectureRules` 在 `check` 里强制：层下第二级只能是已声明子域；每个声明的子域在 domain 层必须有类；跨子域依赖必须声明 |
| R5（`DeclaredDependencyCheck`） | `api` / `implementation` 声明的 project 依赖，主代码里必须 import 到（带自动配置登记的放行，boot 不查）。骨架里只有 `package-info`，所以 app / adapter 不声明任何 project 依赖，层间依赖随后续 Issue 的第一个类一起加。**不能照抄现在的 interaction 构建脚本**，要照它骨架期的写法（interaction 骨架 spec 4.1、4.3） |
| 网关拒绝名单 | `/*/_internal/**`、`/*/admin/**`、`/*/actuator/**` 按第一段通配，自动覆盖新服务 |
| Nacos 签到 | 服务启动时检查读到了 `patra.config-source.service: patra-notification.yaml`，没有就启动失败 |

## 5. 子域

### 5.1 五个子域

| 子域 | 职责 | 落地的 Issue |
|---|---|---|
| `kernel`（底座） | 被两个以上子域共用的东西，见 5.2 | 随第一个用到的 Issue（多半是 PAP-143） |
| `inbox` | 站内信箱：消费 interaction 的事件写提醒，收件人规则（从 `NotificationRules` 搬来），去重与乱序防护（墓碑、版本号），未读数（含 304）、列表、已读、删除联动、5 个月保留；有帮助按「内容 × 北京时间一天」合并也是信箱里的一种提醒 | PAP-143、PAP-144 |
| `preference` | 提醒偏好（类型 × 关 / 离站逐条 / 每日汇总）、每个用户一个退订令牌、RFC 8058 一键退订的 POST、退订落地页的数据接口 | PAP-142 |
| `mail` | 邮件：渠道部分（模板、SMTP 加 `Resend-Idempotency-Key`、重试与死信、发送记录，消费 identity 的安全邮件事件）和提醒邮件部分（离站逐条：等 10 分钟、到点复查已读与在站；每日汇总 8:00；退订头） | PAP-105、PAP-146 |
| `realtime` | SSE 实时推送：「用户 → 连接」表、提交后推三个未读数、25 秒心跳、连接最长 30 分钟、显式异步超时 | PAP-145 |

### 5.2 底座 `kernel` 放什么

按「两个以上子域共用才进底座」逐项判断（下表是给后续 Issue 的落点建议，具体类名由各 Issue 定）：

| 东西 | 谁用 | 为什么进底座 |
|---|---|---|
| 提醒类别（回复我的 / @ 我的 / 有帮助，三个标签） | inbox 按它分标签和计未读，preference 按它存偏好（有帮助不提供邮件选项），mail 按它查偏好 | 三个子域都用 |
| 按 `user_id` 取用户名的端口（调 identity） | inbox 的列表显示触发人、`<@ID>` 换成名字；mail 的邮件正文同样要换名字 | 两个子域都用。**邮箱地址、是否已验证只有 mail 用**，放 mail 自己的端口，不进底座 |
| 在站信号（决策 H） | inbox 拉未读数时写、realtime 连接在线时写、mail 发离站提醒前读 | 三个子域都用；写入经底座的驱动端口（`port.gateway`，实现在 `app.kernel`），存在哪（内存、表）由第一个写它的 Issue 定 |
| 注销清理扩展点 | inbox、preference、mail 都存了按用户的数据，账号注销要一起清 | 同 interaction 的 `UserDataCleanupGateway`：底座定流程与扩展点，各子域实现自己的那份 |

**「已处理事件」表（决策 D）不进任何子域**：它的表结构与建法由 PAP-137 的事件模块定，是和 `flyway_schema_history` 一样的技术表；notification 库里只建一张，inbox（PAP-143）与 mail（PAP-105）的消费者共用。**由 PAP-143 建**（它是第一个消费事件的 Issue），PAP-105 只复用；若 PAP-105 先合入，则由它建、PAP-143 复用——总之谁先到谁建，后到的不再建第二张。放在哪个 schema 按 PAP-137 的约定。

### 5.3 子域之间的依赖

计划中的边（无环；底座不依赖任何子域）：

```
inbox ──────────┐
preference ─────┤
mail ── inbox ──┼──→ kernel
     └ preference
realtime ─ inbox┘
```

| 边 | 类型 | 用途 | 由谁声明 |
|---|---|---|---|
| mail → inbox | 依赖（`allowingDependency`） | 订阅「提醒生成」事件，排入 10 分钟后的离站邮件；到点经读端口复查是否已读；每日汇总经读端口取过去 24 小时未读的回复和 @ | PAP-146 |
| mail → preference | 依赖 | 查某人某类提醒的邮件档位；取退订令牌拼 `List-Unsubscribe` | PAP-146 |
| realtime → inbox | 依赖 | 订阅未读数变化的事件（提交后推）；连接建立时经读端口取当前三个未读数 | PAP-145 |

骨架里一条边都不声明：声明边是一个设计决定，留给第一次真的 import 对方公开包的 Issue，在 `NotificationArchitectureTest` 里加一行 `allowingDependency`，这样 diff 里看得见「从这次起多了一条依赖」。

几条推论，后续 Issue 照此办理：

- inbox 不依赖 preference：偏好只管邮件，「只关这一类邮件，站内提醒照常」（2.1）。
- preference 不依赖任何业务子域。
- 别的子域要「写」底座的在站信号、触发清理，经底座的驱动端口；要让 inbox 做事（比如账号注销时删提醒），经底座的扩展点，不直接调 inbox 的仓储。

### 5.4 考虑过、没选的切法

| 切法 | 没选的原因 |
|---|---|
| mail 再拆成「渠道」和「提醒邮件」两个子域 | 非底座子域只公开读端口、读模型和事件，「提醒邮件」要让「渠道」发信只能绕一个事件，多一层间接；两者都围着邮件转，安全邮件和提醒邮件共用模板、发送记录和幂等键，放一起更内聚 |
| realtime 并进 inbox | 推送是另一种渠道，技术形态（长连接、内存表、心跳）和信箱的读写完全不同；分开后 inbox 不用知道 SSE 的存在 |
| 在站信号放 realtime | inbox 拉未读数时也要写它，只能经事件让 realtime 记，又要 mail 读 realtime，三方绕在一起；它被三个子域用，正是底座该放的 |
| 有帮助单独一个子域 | 合并后的有帮助就是信箱里一种特殊的提醒，写的是同一张信箱表、同一套已读与未读数；单独成子域就要跨子域写信箱 |
| 子域 `push` | 第 3 节 A 刚说过不做浏览器系统通知（Web Push），叫 `push` 容易误读，改叫 `realtime` |
| 子域 `notification` | 服务已经叫 notification，包名会成 `dev.patra.notification.domain.notification`；release spec 里叫「信箱」，用 `inbox` |

## 6. 模块与包

### 6.1 模块

| 模块 | 约定插件 | 本 Issue 的内容 | 依赖 |
|---|---|---|---|
| `patra-notification-domain` | `patra.hexagonal-domain` | 五个子域的 `package-info.java` | 插件自带 tool-core（R5 豁免） |
| `patra-notification-app` | `patra.hexagonal-app` | 五个子域的 `package-info.java` | 无 project 依赖 |
| `patra-notification-infra` | `patra.hexagonal-infra` | 五个子域的 `package-info.java`；集成测试基座与迁移测试 | `api(project(":patra-tool:patra-tool-jpa"))`（带自动配置登记，R5 放行；带来 JPA、Flyway、PG 驱动）；`testFixturesImplementation(project(":patra-tool:patra-tool-test"))` |
| `patra-notification-adapter` | `patra.hexagonal-adapter` | 五个子域的 `package-info.java` | 无 project 依赖 |
| `patra-notification-api` | `patra.hexagonal-api` | 零个类：v0.16 没有别的服务调用 notification。头注释写明「第一个内部调用方出现时加」 | 插件自带 tool-core |
| `patra-notification-boot` | `patra.hexagonal-boot` | 启动类、配置、XXL-Job 执行器、架构测试、启动测试 | 见 6.3 |

domain / app / infra / adapter 四个模块的 `compileJava` 加 `-Xpkginfo:always`（同 interaction 骨架 spec 17.1 #1）：只有注释的 `package-info.java` 默认不产出 class，架构测试里层就是空的，层规则会报「Layer is empty」，子域规则会报「子域在 domain 层没有任何类」。

聚合模块 `patra-api/patra-notification/build.gradle.kts` 只有注释。`settings.gradle.kts` 加六行 `includeAt` 与一行 `mapParent`，放在 interaction 之后、gateway 之前。

### 6.2 包

根包 `dev.patra.notification`。

| 位置 | 包 |
|---|---|
| domain / app / infra / adapter | `dev.patra.notification.{层}.{kernel｜inbox｜preference｜mail｜realtime}`，子域内部按分包模板 |
| api | `dev.patra.notification.api.{子域}.{endpoint｜dto}`，本版没有 |
| boot | `dev.patra.notification`（启动类）、`dev.patra.notification.config`（XXL-Job 执行器等装配） |

每个 `package-info.java` 用 `///` 写这个子域的职责、对应的 Issue，底座另写「对其他子域完全公开」，普通子域写它的公开包。20 个文件（5 子域 × 4 层）的写法同 interaction 现有的 `package-info`：各层正文相同，第一行点明层。

### 6.3 boot 的依赖

```kotlin
dependencies {
    implementation(project(":patra-api:patra-notification:patra-notification-adapter"))
    implementation(project(":patra-api:patra-notification:patra-notification-infra"))

    implementation(project(":patra-tool:patra-tool-web"))
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation(project(":patra-tool:patra-tool-observability"))
    implementation(project(":patra-tool:patra-tool-openapi"))

    // XXL-Job 执行器（执行器端口 = 服务端口 + 1 = 6601）
    implementation(libs.xxl.job)

    // 架构测试要看到全部五层：domain、app、api 在后续 Issue 接上层间依赖之前不在运行时类路径上
    testImplementation(project(":patra-api:patra-notification:patra-notification-domain"))
    testImplementation(project(":patra-api:patra-notification:patra-notification-app"))
    testImplementation(project(":patra-api:patra-notification:patra-notification-api"))
    // 集成测试的 PG 容器初始化器
    testImplementation(testFixtures(project(":patra-api:patra-notification:patra-notification-infra")))
}
```

`springBoot.mainClass = "dev.patra.notification.PatraNotificationApplication"`。插件已自带 `patra-tool-launch`、`patra-tool-test`、`testcontainers-jdbc`。

## 7. 架构测试 `NotificationArchitectureTest`

在 boot 的 `src/test/java/dev/patra/notification/architecture/`，写法照现在的 `InteractionArchitectureTest`：导入路径含 `/patra-notification-` 的模块主代码，排除 test-fixtures；逐条执行 `HexagonalArchitectureRules("dev.patra.notification", List.of("dev.patra.tool.."))` 的规则、`TestingRules`，以及汇总执行 `SubdomainArchitectureRules` 的 `allRules()`。

```java
private static final String FOUNDATION = "kernel";
private static final List<String> SUBDOMAINS =
    List.of(FOUNDATION, "inbox", "preference", "mail", "realtime");
private static final SubdomainArchitectureRules subdomainRules =
    new SubdomainArchitectureRules("dev.patra.notification", FOUNDATION, Set.copyOf(SUBDOMAINS));
```

`src/test/resources/archunit.properties` 设 `archRule.failOnEmptyShould = false`（骨架阶段多数规则没有要查的类）。

**它在骨架期能管住什么**：`everyDeclaredSubdomainHasDomainPackage` 要求五个子域在 domain 层都有类（漏建、拼错子域名就红）；层规则要求四层都不空；`layersContainOnlyDeclaredSubdomains` 不许在层下建清单外的包。先写测试、看它在没有 `package-info` 时红，再补上变绿。从 PAP-143 起每加一个类都被它管着。

## 8. 存储与 Flyway

- 库 `patra_notification`，在 mini 现有的 `patra-postgres` 上；应用账号沿用 Nacos 公共配置里的账号（同其他服务）。
- 每个子域一个 schema、schema 名就是子域名，整个服务一份 Flyway 历史（`public.flyway_schema_history`），脚本放 `patra-notification-infra/src/main/resources/db/migration/{子域}/`，版本号是写脚本时的时间戳 `yyyyMMddHHmmss`；不设 `spring.flyway.schemas`、不设 Hibernate 默认 schema，实体写 `@Table(schema = "…")`。以上全部同 interaction 的 README 第 4 节，理由同 interaction 骨架 spec 6.3。
- **骨架不建任何 schema 与脚本**：不建没有表的空 schema（interaction README 的经验：`realtime` 很可能一张表都没有）。每个子域随自己的第一张表 `CREATE SCHEMA`。没有脚本时 Flyway 对缺失的 `db/migration` 目录只告警、照样建历史表，迁移测试照常有意义（interaction 骨架 spec 17.1 #5）。
- `set_updated_at()` 触发器函数每个 schema 一份（同 interaction），随第一张带 `updated_at` 的表建。
- 表里只存用户 ID，不存用户名（R6 检查守着迁移脚本）。
- 建库：`patra-infra/docker/postgres/init-scripts/02-create-databases.sql` 加 `CREATE DATABASE patra_notification;`（只在数据目录为空时执行）；mini 现有的 PG 手工建（runbook 第 2 步），必须在服务第一次启动之前。

## 9. boot

### 9.1 启动类

```java
/// notification 服务的启动入口：站内提醒、提醒偏好与退订、邮件、实时推送。
///
/// 配置从 Nacos 读（`patra-common.yaml`、`patra-notification.yaml`），由启动器导入。
@SpringBootApplication
public class PatraNotificationApplication {
  public static void main(String[] args) {
    PatraApplication.run("patra-notification", PatraNotificationApplication.class, args);
  }
}
```

### 9.2 `application.yml`

| 键 | 值 | 说明 |
|---|---|---|
| `server.port` | `6600` | 现有服务占 6000、6300、6400、6500，网关 9528；6600 / 6601 在 `patra-infra` 与各服务配置里都没有人用 |
| `server.forward-headers-strategy` | `framework` | 同 interaction：OpenAPI 文档里的地址指向网关 |
| `spring.datasource.driver-class-name` 与 `hikari.*` | 同 interaction（池 10、最小空闲 2） | 库地址、账号在 Nacos |
| `spring.flyway.enabled` / `locations` | `true` / `classpath:db/migration` | 第 8 节 |
| `spring.jpa.open-in-view` | `false` | 同 interaction |
| `patra.web.error.context-prefix` | `NOTIFICATION` | 错误码形如 `NOTIFICATION-0404` |
| `xxl.job.executor.ip` / `logpath` / `logretentiondays` | 同 interaction，日志目录 `…/xxl-job/patra-notification` | 管理端地址在 Nacos 公共配置 |

不写 `spring.application.name`、`spring.profiles.active`、`spring.cloud.nacos.*`、`spring.data.redis.*`（`ApplicationConfigTest` 守着）。

错误码前缀：已有服务里 registry、catalog、identity、网关用三字母缩写（`REG`、`CAT`、`IDN`、`GW`），最新的 interaction 用全名 `INTERACTION`。本服务随最新的模板用全名。

### 9.3 XXL-Job 执行器

`dev.patra.notification.config.XxlJobConfig`，内容照 interaction 的 `XxlJobConfig`：`@ConditionalOnProperty(prefix = "xxl.job", name = "enabled", havingValue = "true", matchIfMissing = true)`，appname 取 `spring.application.name`（`patra-notification`），端口 = `server.port + 1` = 6601。没有单元测试，理由同 interaction 骨架 spec 7.3：由启动集成测试断言测试里不起执行器、部署后「执行器在线、地址是 6601」验收。

### 9.4 Nacos 配置 `patra-infra/nacos/dev/patra-notification.yaml`

```yaml
patra.config-source.service: patra-notification.yaml

spring:
  datasource:
    url: jdbc:postgresql://${PATRA_INFRA_HOST:127.0.0.1}:15432/patra_notification
  rabbitmq:
    password: ${RABBITMQ_PASSWORD}
```

库账号、XXL-Job 管理端地址在公共配置里。RabbitMQ 按 PAP-138 的约定（共享笔记 `pap-138-rabbitmq-contract.md`，编排会话 10/30 确认）：host、port、username 在公共配置，密码引用只写在用到它的服务文件里，值在 `~/.patra/secrets/rabbitmq.env`。骨架还没有 AMQP 客户端，这一行先接好，PAP-143 / PAP-105 加依赖时不用再动配置；代价是服务从现在起启动就要求 `RABBITMQ_PASSWORD` 有值（Nacos 变量检查）。`NacosConfigFilesTest`、`JarConfigOverlapChecker` 按目录自动发现新文件。SMTP 配置由 PAP-105 加。

## 10. 测试

| 测试 | 位置 | 断言 |
|---|---|---|
| `NotificationArchitectureTest` | boot `src/test` | 第 7 节 |
| `ApplicationConfigTest` | boot `src/test` | 照 interaction：classpath 上没有 profile 文件；`application.yml` 不写服务名、激活的 profile、Nacos 接入；端口 6600、错误码前缀 `NOTIFICATION`；Flyway 递归扫 `db/migration`、不设 `schemas`；不配 Redis |
| `NotificationMigrationIT` | infra `src/integrationTest` | `@SpringBootTest(classes = NotificationITBootstrap.class)` 加 PG 容器：`flyway_schema_history` 只有一张，在 `public`；除 PG 自带的 schema 外，库里的 schema 只能是 `public` 与五个子域名（新子域随自己的迁移建 schema，不用改这条测试） |
| `PatraNotificationApplicationIT` | boot `src/integrationTest` | 以 `test` profile 起来（不连 Nacos、不起执行器）：`/actuator/health` 为 UP；错误码前缀是 `NOTIFICATION`；容器里没有 `XxlJobSpringExecutor` |
| `GatewayRoutingIT` 新增一例 | gateway `src/integrationTest` | `/patra-notification/x?y=1` 剥掉前缀转到 `lb://patra-notification`（WireMock 顶替），查询串原样 |

测试基座：

- `NotificationITBootstrap`：infra `src/integrationTest/java/dev/patra/notification/infra/`，`@SpringBootApplication` 空类。
- `NotificationITPostgreSQLContainerInitializer`：继承 `PostgreSQLContainerInitializer`，库名 `patra_notification`，放 infra 的 `src/testFixtures`（`dev.patra.notification.infra.config`），infra 与 boot 的集成测试共用。
- `application-test.yml`：infra、boot 的集成测试各一份，关 Nacos 发现与注册；boot 那份另加 `xxl.job.enabled: false`。

## 11. 网关

`patra-gateway-boot/src/main/resources/application.yml` 在 interaction 路由之后加：

```yaml
            # Notification Service Route（站内提醒、提醒偏好与退订；以后的实时推送）
            # Example: /patra-notification/notifications -> lb://patra-notification/notifications
            - id: patra-notification
              uri: lb://patra-notification
              predicates:
                - Path=/patra-notification/**
              filters:
                - StripPrefix=1
```

`scalar.sources` 加 `/patra-notification/v3/api-docs`（title `Notification Service`，slug `notification`）；`PatraGatewayApplication` 的 Javadoc 与网关 README 的路由清单补上 notification。鉴权规则不改：identity 以外的路径都走第二条过滤器链（放行，带令牌时照样查会话、写身份断言），哪些接口要登录由 notification 自己的安全 starter 判定（第一个要登录的接口所在的 Issue 接入）。推送路由的独立连接池归 PAP-145。

## 12. 新服务接入核对单

漏了不会报错、要特别当心的四处：`unitOf`（漏了 notification 的模块被归进 foundation，CI 照样绿、CD 永远不部署它）、`ALL_UNITS`（漏了全量运行不测它）、`services.json`（漏了 `deploy.sh` 拒绝这个服务）、`nacos-verify.sh`（漏了验收查不出它没注册）。

| # | 项 | 位置 | 改动 | 证据 |
|---|---|---|---|---|
| 1 | Gradle 注册 | `settings.gradle.kts` | 6.1 | `./gradlew :patra-api:patra-notification:projects` 列出六个模块 |
| 2 | **`unitOf`** | 根 `build.gradle.kts` 的 `dumpModuleGraph` | 加 `patra-api/patra-notification` → `notification` | `module-graph.json` 里 notification 模块的 `unit` 都是 `notification` |
| 3 | `units` 列表 | 同上 | `interaction` 之后加 `notification` | `module-graph.json` 的 `units` 含 `notification` |
| 4 | 模块图 | `patra-infra/cd/module-graph.json` | `./gradlew dumpModuleGraph` 重新生成 | diff 只多出 notification 的模块，以及 tool 模块的 `impacts` 多出 `notification` |
| 5 | **`ALL_UNITS`** | `patra-infra/cd/detect-changes.sh` 第 22 行与第 6 行注释 | 加 `notification` | 下一行 |
| 6 | `detect-changes` 测试 | `patra-infra/cd/detect-changes.test.sh` | 现有断言第 44、98 行写死的单元数组按字母序插入 `notification`，描述「5 个服务」改 6 个；新增场景 16：notification 私有改动只跑 notification、全量运行包含 notification、`nacos/dev/patra-notification.yaml` 只重部署 notification | 测试全部通过（新场景与两条旧断言先红后绿） |
| 7 | **`services.json`** | `patra-infra/cd/services.json` | interaction 之后、portal 之前加条目：`gradleTask` `:patra-api:patra-notification:patra-notification-boot:bootJar`、`context` / `srcPrefix` 按路径、`port` 6600、`image` `ghcr.io/linqibin0826/patra-notification`、健康检查同其他服务；`_comment` 的部署顺序补一句 | `deploy.test.sh` 新增场景 8：请求 `["notification","interaction"]`，按 interaction、notification 的顺序 `up` 并记 last-good（加条目前以「未知服务」退出 2，先红后绿） |
| 8 | compose | `patra-infra/docker/docker-compose.apps.yaml` | 第 13 节；头部注释「5 个后端服务」改 6 个 | 临时 `HOME` 放假的 `nacos.env` 后 `docker compose config` 能解析，notification 的镜像、端口、内存上限、`env_file` 正确 |
| 9 | 建库 | `02-create-databases.sql` + mini 手工 | 第 8 节 | 文件 diff；mini 上 `pg_database` 有 `patra_notification`（runbook） |
| 10 | Nacos 配置 | `patra-infra/nacos/dev/patra-notification.yaml` | 9.4 | `NacosConfigFilesTest`、`JarConfigOverlapCheckerTest` 通过 |
| 11 | **`nacos-verify.sh`** | `patra-infra/scripts/nacos-verify.sh` | `SERVICES` 加 `patra-notification`；第 5、151 行的「5 个」改 6 个 | `bash -n` 通过；mini 部署后 `--expect-services` 全部 PASS（runbook） |
| 12 | CI 镜像常量 | `.github/workflows/ci.yml` preflight | 不改：它只核对 `patra-tool-test` 的 PG、Redis 镜像常量；notification 的 PG 初始化器继承基座，不引入新镜像 | 在汇报里引用 preflight 那段行号 |
| 13 | 网关路由 | 网关 `application.yml` | 第 11 节 | `GatewayRoutingIT` 新例先红（404）后绿；mini 上经网关访问 `/patra-notification/v3/api-docs` 200（runbook） |
| 14 | XXL-Job 执行器 | boot `XxlJobConfig` + 调度中心建执行器 | 9.3、14 | 本机启动日志有 `appName=patra-notification … port=6601`；调度中心「执行器管理」里在线（runbook） |
| 15 | IDEA 运行配置 | `.run/PatraNotificationApplication.run.xml` | 照 interaction | 文件存在，module 名 `patra.patra-api.patra-notification.patra-notification-boot.main` |
| 16 | 文档 | 第 16 节 | | `git grep` 已无指后端服务清单的「5 个」 |

## 13. compose 服务块

放在 interaction 块之后：

```yaml
  notification:
    image: ghcr.io/linqibin0826/patra-notification:${NOTIFICATION_IMAGE_TAG:-latest}
    container_name: patra-notification
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 1g
    ports: [ "6600:6600" ]
    extra_hosts: *infra-hosts
    # 不连 Redis，不加 redis.env；XXL-Job 执行器 6601 不映射：调度中心经 patra-net 回调容器 IP
    env_file:
      - .env.common
      - path: ${HOME}/.patra/secrets/nacos.env
        required: true
      - path: ${HOME}/.patra/secrets/rabbitmq.env
        required: true
      - path: ${HOME}/.patra/secrets/notification.env
        required: false
    healthcheck:
      test: [ "CMD", "curl", "-fs", "http://localhost:6600/actuator/health" ]
      <<: *app-healthcheck
```

内存上限 1g 同 interaction（D10 要求设上限；JVM 堆为上限的 75%）。`NOTIFICATION_IMAGE_TAG` 是 `deploy.sh` 按服务名推出的变量名。`rabbitmq.env` 必需（PAP-138 的约定，与 interaction、identity 一致）：mini 上要先放好它、起好 RabbitMQ 再合入后端 PR，否则 compose 对整个 apps 项目的命令都会失败。`mail.env` 由 PAP-105 加进这个块。

## 14. XXL-Job

**执行器**（本 Issue 登记，runbook 第 4 步）：AppName `patra-notification`，名称「通知服务」，自动注册。执行器在零个任务时也会向调度中心注册地址，部署后就能看到它在线。

**以后挂在这个执行器上的任务**（名字与 Cron 由各 Issue 定，登记步骤写进 runbook 第 7 步之后）：

| 任务 | 归属 |
|---|---|
| 提醒保留清理（5 个月） | PAP-143（`adapter.inbox.scheduler`） |
| 注销清理 | PAP-143（底座流程，`adapter.kernel.scheduler`） |
| 离站逐条邮件的到点扫描 | PAP-146（`adapter.mail.scheduler`） |
| 每日汇总（北京时间 8:00） | PAP-146（`adapter.mail.scheduler`） |

interaction / identity 的事件补投任务在发布方的执行器上（PAP-137 / PAP-141 / PAP-104），不在这里。

## 15. 本机启动与 mini 上的操作

本机 dev 连 mini 的 PG 与 Nacos，所以本机启动前要在 mini 上有两样东西，**都要先经编排会话征得用户同意**：

1. `patra_notification` 库（runbook 第 2 步）。
2. Nacos 里的 `patra-notification.yaml`（runbook 第 3 步）。`nacos-push.sh` 是全量同步：Nacos 里有、目录里没有的会被删掉。本版有多个会话并行，别的会话可能已经推了自己的配置，从本分支推会把它们删掉。所以先 `--check`，差异**只有**「新增 patra-notification.yaml」才推；出现别的差异就停下，交给编排会话统一调度。runbook 第 3 步把这条写成通用规则：任何「删除」一律停，「新增 / 更新」只认本分支相对 main 改过的文件。后端 PR 合进 main 之前，main 每次有后端推送，CD 的 `nacos-push` 都会按 main 的目录把这份配置删掉，本机再起时报「没读到签到」就照上面重做一次（同 interaction 骨架 spec 12.1，不加本地覆盖机制）。

启动（同 interaction 骨架 spec 17.4）：`PATRA_INFRA_HOST=100.103.73.27 PATRA_ADVERTISE_IP=<本机 tailscale IP> RABBITMQ_PASSWORD=<占位> ./gradlew :patra-api:patra-notification:patra-notification-boot:bootRun -Potel.agent.path=`，先导出 `JAVA_TOOL_OPTIONS` 覆盖 JVM 继承的系统代理。`RABBITMQ_PASSWORD` 只为过 Nacos 的变量检查：骨架不连 RabbitMQ，本机在 PAP-138 的 `rabbitmq.env` 放到 MacBook 之前用一个占位值，不在密钥目录造假文件。验证：Flyway 建了历史表；`curl 127.0.0.1:6600/actuator/health` 为 UP、`/v3/api-docs` 为 200；Nacos `public` 命名空间下有本机实例；执行器日志 `port=6601`。

「经网关」在本机的验证：mini 上的网关跑的是 main，没有 notification 路由。本机再起一个网关（同样连 mini 的 Nacos，从 Nacos 发现本机的 notification 实例），访问 `127.0.0.1:9528/patra-notification/v3/api-docs` 应为 200、`/patra-notification/actuator/health` 应为 403。验证完立即停掉两个进程，再查 Nacos 实例列表为空。

用户不同意改 mini 时，本 Issue 只交集成测试层面的证据（启动 IT 健康 UP、网关路由 IT），真机验收全部留给 runbook。

## 16. 文档

| 文件 | 改动 |
|---|---|
| `patra-api/patra-notification/README.md` | 新建：职责；先讲六层、子域、底座（第 2 节的精简版）；子域表与底座内容（5.1、5.2）；子域依赖（5.3，标「计划」）；存储约定（第 8 节）；运行（端口、执行器、网关前缀、错误码前缀、配置、上线 runbook） |
| `patra-api/README.md` | 微服务表加一行；架构图加 notification；「5 个后端服务」改 6 个 |
| 根 `README.md` | 第 139 行 CI 单元清单、第 166 行服务清单 |
| `.claude/rules/project-info.md` | 核心服务列表加 `patra-notification` |
| `patra-infra/CLAUDE.md` | 第 32 行 apps 栈清单；第 45、55、62、70 行的「5 个」 |
| `patra-infra/docker/README.md` | 第 162、224 行的「5 个」；第 267 行 interaction 执行器那句之后加 notification 的执行器 |
| `patra-infra/docker/.env.common`、`secrets/nacos.env.example`、`service.Dockerfile` 头注释 | 「5 个」改 6 个，`service.Dockerfile` 的服务名清单加 notification |
| `JarConfigOverlapCheckerTest` | 显示名「5 个服务」改 6 个 |
| `.claude/skills/patra-backend/skills/patra-hexagonal/resources/configuration.md` | 第 5 行「5 个后端服务」改 6 个 |
| `patra-api/patra-gateway-boot/README.md` | 路由清单、「五个服务」、第二条链的服务清单 |
| `patra-infra/cd/deploy.sh` 头注释 | 部署顺序补「notification 在 interaction 之后」 |
| `docs/patra/runbooks/v0.16-notification-go-live-runbook.md` | 新建，第 17 节 |

实施时用 `git grep -nE "5 个|五个|interaction / gateway|interaction、gateway"` 再扫一遍，按命中补齐（历史 spec、计划、旧 runbook 不改）。只改清单行和数字，不重排段落，减少和并行会话（PAP-137 / PAP-138）的冲突。

## 17. 上线 runbook 的结构

`docs/patra/runbooks/v0.16-notification-go-live-runbook.md`：整个 v0.16 后端的上线手册。本 Issue 写环境约定、notification 的步骤，并给其他 Issue 留好位置；每步给命令、预期结果与失败时的动作，改动 mini 的步骤先确认上一步的预期全部满足、并征得用户同意。

| 步 | 时机 | 内容 | 谁写 |
|---|---|---|---|
| 0 | — | 环境与约定（SSH、两份检出、last-good、调度中心、Nacos 推送） | PAP-139 |
| 1 | 合并前 | 记录资源基线（D10）：`docker stats --no-stream`、`df -h /`，剩余 ≥ 20 GB | PAP-139 |
| 2 | 合并前 | 6600、6601 端口空闲；建 `patra_notification` 库 | PAP-139 |
| 3 | 本机冒烟需要时 | 推 `patra-notification.yaml`：先 `--check`，差异只有它才推（第 15 节） | PAP-139 |
| 4 | 合并前 | 调度中心建执行器 `patra-notification`（控制台或接口） | PAP-139 |
| 5 | 合并前 | 其他 Issue 的合并前步骤：RabbitMQ 容器与凭据（PAP-138）、`mail.env` 放到 mini（PAP-105）等 | 各 Issue |
| 6 | 后端 PR 合进 main、CD 结束 | notification 核对：CD 的 nacos-push 成功；`patra-notification` healthy；直连 `/actuator/health` UP；经网关 `/patra-notification/v3/api-docs` 200；`nacos-verify.sh --expect-services` 全部 PASS；执行器在线；`docker stats` 里在 1 GiB 上限内 | PAP-139 |
| 7 | 第 6 步之后 | 其他 Issue 的合并后步骤：XXL 任务登记（补投、提醒清理、注销清理、离站邮件、每日汇总）、interaction 旧提醒表已删、identity 已无发信代码等 | 各 Issue |
| 8 | 最后 | 再记一次 `docker stats` 与 `df -h /`，与第 1 步对比写进 release spec D10 的「实际：」 | PAP-139 |
| 退路 | — | notification 第一次部署没有 last-good，不健康时停在 unhealthy，不影响其他服务；网关回滚由 `deploy.sh` 自动完成，回滚后 `/patra-notification/**` 只是 404 | PAP-139 |

## 18. 我的决定（请重点看）

1. 五个子域与底座内容（第 5 节）：`kernel` 放类别、用户名端口、在站信号、注销清理扩展点；邮箱地址只给 mail；有帮助并进 inbox；mail 不拆；推送子域叫 `realtime`。
2. 「已处理事件」表不属于任何子域，谁先到谁建（预计 PAP-143），后到的复用（5.2）。
3. 骨架里不声明任何子域依赖边，计划中的三条边写进 spec 与 README，由引入依赖的 Issue 加。
4. 骨架不建任何 schema 与迁移脚本，各子域随第一张表建。
5. 错误码前缀 `NOTIFICATION`（随 interaction 用全名）。
6. 端口 6600 / 6601；部署顺序在 interaction 之后。
7. compose 内存上限 1g，不加 `redis.env`，不映射 6601；`rabbitmq.env` 必需、Nacos 服务文件写 RabbitMQ 密码引用（照 PAP-138 的约定，编排会话确认）。
8. 安全 starter、identity 客户端、RabbitMQ 依赖都留给后续 Issue。
9. 20 个 `package-info` + `-Xpkginfo:always` 让架构测试在骨架期就有东西可查。
10. 「经网关能访问健康检查」照 interaction 先例拆成「直连健康 UP + 经网关文档 200」（第 1 节）。
11. runbook 是整个 v0.16 后端一份，按「合并前 / 合并后」留插入位置（第 17 节）。

## 19. 与并行 Issue 的交接

- **会冲突的文件**（编排会话汇入 `feat/v0.16-notification-api` 时处理）：`settings.gradle.kts`（PAP-137 若新增工具模块）、`patra-infra/cd/module-graph.json`（以汇入后重跑 `./gradlew dumpModuleGraph` 为准，CI preflight 会拦下陈旧的模块图）、`detect-changes.test.sh` 的单元数组、`docker-compose.apps.yaml` 头注释（PAP-138 若加 RabbitMQ 凭据说明）、`patra-infra/CLAUDE.md` 与 `patra-infra/docker/README.md` 的服务清单。
- **runbook**：PAP-138 等要写上线步骤的 Issue，往本 runbook 的第 5 / 7 步里加，不另建文件。
- **后续 Issue 开工时**：照第 5 节把代码放进对应子域；第一次跨子域 import 时在 `NotificationArchitectureTest` 加边；第一张表 `CREATE SCHEMA`；第一个要登录的接口接安全 starter；第一个调 identity 的地方在 boot 加 HTTP Interface 分组与 `IdentityClientConfiguration`（照 interaction）。

## 20. 实施记录（2026-10-30）

### 20.1 与本设计的偏离

| # | 偏离 | 原因 |
|---|---|---|
| 1 | Nacos 服务文件加 `spring.rabbitmq.password: ${RABBITMQ_PASSWORD}`，compose 的 notification 块加必需的 `rabbitmq.env`（已改 9.4、第 13 节） | PAP-138 的 RabbitMQ 约定把这两行分给本 Issue，编排会话确认。代价：服务从骨架起启动就要求 `RABBITMQ_PASSWORD` 有值 |
| 2 | 本机验收用 `java -jar` 起 bootJar，不用 `bootRun`；进程环境里给 `RABBITMQ_PASSWORD` 一个占位值 | 要同时起 notification 与网关两个进程，两个 Gradle 构建并发会抢构建目录；MacBook 上还没有 `rabbitmq.env`，骨架也不连 RabbitMQ，占位值只为过 Nacos 的变量检查 |
| 3 | compose 校验命令要加 `DOCKER_CONFIG=~/.docker` | 临时 `HOME` 下 docker 找不到 compose 插件 |
| 4 | 网关 README「`/scalar` 聚合五个服务」不改 | 加上 notification 后文档源恰好 5 个，原句变准确 |
| 5 | 文档同步多改了 `patra-infra/CLAUDE.md` 第 59 行、`docker/README.md` 第 239 行、`secrets/nacos.env.example` 第 12 行的「5 个」 | 第 16 节的 `git grep` 扫出 |

### 20.2 核对单（第 12 节）

| # | 项 | 结果 | 证据 |
|---|---|---|---|
| 1 | Gradle 注册 | ✅ | `./gradlew :patra-api:patra-notification:projects` 列出六个子模块 |
| 2 | `unitOf` | ✅ | `module-graph.json` 里六个 notification 模块的 `unit` 都是 `notification`；infra、boot 的 tasks 含 `integrationTest` |
| 3 | `units` 列表 | ✅ | `["registry","catalog","identity","interaction","notification","gateway","foundation"]` |
| 4 | 模块图 | ✅ | 重新生成后只多出 notification 模块与依赖方 `impacts` 里的 `notification`（如 `patra-tool-core` 的扇出）；最终再生成一次无差异 |
| 5 | `ALL_UNITS` | ✅ | `detect-changes.sh` 第 22 行含 `notification` |
| 6 | `detect-changes` 测试 | ✅ | 两条旧断言与场景 16 先红（5 处 ✗）后绿；`通过 68 / 失败 0` |
| 7 | `services.json` | ✅ | `deploy.test.sh` 场景 8 先红（退出码 2）后绿；`PASS=8 FAIL=0` |
| 8 | compose | ✅ | 临时 `HOME` 下 `docker compose config`：镜像 `ghcr.io/linqibin0826/patra-notification:latest`、端口 6600、内存上限 1 GiB、env_file 依次 `.env.common` → `nacos.env` → `rabbitmq.env` → `notification.env`，环境里没有 `redis.env` 的变量；删掉 `nacos.env` 或 `rabbitmq.env` 时都报出完整路径 |
| 9 | 建库 | ✅ 脚本；✅ mini | `02-create-databases.sql` 加一行；mini 上 6600 / 6601 空闲、库原先不存在，`CREATE DATABASE` 后查询为 `1` |
| 10 | Nacos 配置 | ✅ | `NacosConfigFilesTest`、`JarConfigOverlapCheckerTest` 通过；mini 上 `--check` 差异只有「新增 patra-notification.yaml」，推送后复查 7 份全部一致 |
| 11 | `nacos-verify.sh` | ✅ 脚本；⏳ mini | `SERVICES` 含 `patra-notification`，`bash -n` 通过；`--expect-services` 待后端 PR 合进 main 后跑（runbook 第 6 步） |
| 12 | CI 镜像常量 | ✅ 不改 | `.github/workflows/ci.yml` 第 64–103 行的 preflight 只核对 PG / Redis 初始化器常量与模块图新鲜度 |
| 13 | 网关路由 | ✅ IT；✅ 本机；⏳ mini | `GatewayRoutingIT` 新例先红（404）后绿，`GatewayBlockedPathsIT` 照旧通过；本机网关经 Nacos 发现本机实例，`/patra-notification/v3/api-docs` 200、`/patra-notification/actuator/health` 403 |
| 14 | XXL-Job 执行器 | ✅ 本机；⏳ mini | 本机启动日志 `XXL-Job 执行器初始化完成，appName=patra-notification … port=6601`，6601 在监听；调度中心建执行器（runbook 第 4 步）待做 |
| 15 | IDEA 运行配置 | ✅ | `.run/PatraNotificationApplication.run.xml`，module `patra.patra-api.patra-notification.patra-notification-boot.main` |
| 16 | 文档 | ✅ | `git grep` 已无指后端服务清单的「5 个」（历史 spec、计划、旧 runbook 不改） |
| 17 | 架构测试确实导入了类 | ✅ | 没有 `package-info` 时先红（四层为空、五个子域在 domain 层无类）；临时放 `dev.patra.notification.domain.Loose`，子域规则失败并点名它，删掉后全绿 |

### 20.3 门禁

- `./gradlew spotlessApply && ./gradlew check`：BUILD SUCCESSFUL（4 分 10 秒）。
- 显式集成测试 `:patra-notification-infra:integrationTest`（2）、`:patra-notification-boot:integrationTest`（3）、`:patra-gateway-boot:integrationTest`（70）、`:patra-tool-launch:test`（68）：全部通过。首轮在本机负载 160～190（其他会话并行构建）时，网关的 `GatewayTrustedProxyIT`、`GatewayFailureIT` 慢客户端用例超时失败，负载降到 50 左右后单独与完整重跑都通过，判为负载抖动。
- `detect-changes.test.sh`：通过 68 / 失败 0；`deploy.test.sh`：PASS=8 FAIL=0。

### 20.4 本机启动（2026-10-30，连 mini 的 PG 与 Nacos）

`java -jar patra-notification-boot-0.1.0-SNAPSHOT.jar`（环境：`PATRA_INFRA_HOST=100.103.73.27`、`PATRA_ADVERTISE_IP=100.70.109.37`、`RABBITMQ_PASSWORD` 占位、`JAVA_TOOL_OPTIONS` 覆盖代理例外）：

- Flyway：`No migrations found`，建了 `public.flyway_schema_history`（mini 库里只有这一张表）。
- `nacos registry, DEFAULT_GROUP patra-notification 100.70.109.37:6600 register finished`；Nacos 管理接口查 `public` 下 `patra-notification`：`[{"ip":"100.70.109.37","port":6600,"healthy":true}]`。
- `curl 127.0.0.1:6600/actuator/health` → `{"status":"UP"}`；`/v3/api-docs` → 200。
- 本机网关（同样 `java -jar`）：`/patra-notification/v3/api-docs` → 200，文档的 `servers` 是 `http://127.0.0.1:9528/patra-notification`；`/patra-notification/actuator/health` → 403。
- 验证后停掉两个进程，再查 Nacos：`patra-notification` 无实例，`patra-gateway` 只剩 mini 容器。
