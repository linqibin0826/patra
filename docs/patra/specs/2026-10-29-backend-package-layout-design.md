# 后端分包规范：层模块内子域优先

- 日期：2026-10-29
- 分支：`refactor/package-layout`
- 范围：`patra-api` 下 5 个六边形服务（catalog / identity / interaction / object-storage / registry）、`patra-tool-test` 的 ArchUnit 规则、`.claude/rules` 与 patra-hexagonal skill 的分包说明。ingest 本次不迁（§8.3）
- 附录：`2026-10-29-backend-package-layout-mapping.md`（各服务旧包 → 新包映射）

## 1. 为什么做

### 1.1 现状

6 个服务有 6 种分包写法：

| 服务 | 取向 | app 层 |
|---|---|---|
| interaction | 层模块内**子域优先**，ArchUnit 守子域边界 | `app.{子域}.usecase.{用例族}`，Command / Handler / Result 同包 |
| identity | domain、infra 类型优先，app 子域优先 | `app.usecase.{动作}`，读侧在 `query/` |
| catalog | 类型优先，app 内再按子域 | `app.usecase.{子域}.{command,dto,query}` |
| ingest | 类型优先，混合 | `app.usecase.{子域}` 下十几种职责包，另有并列的 `outbox`、`eventhandler`、`config` |
| registry | 类型优先 | 没有 usecase，只有 `service` + `converter` |
| object-storage | 类型优先 | `app.recordupload` |

三个放任漂移的原因：

1. `.claude/rules/layers/*.md` 的 `paths:` 写成 `patra-*/*-app/**`，真实路径是 `patra-api/patra-x/patra-x-app/…`，**从未自动加载**（10/29 实测：读 interaction 的 Handler 时 `tech/transaction.md` 注入了，`layers/application.md` 没有）。
2. ArchUnit 只在 interaction 守包结构。identity、registry、object-storage 只启用了 3 条事务规则；共享规则里 `*DO` 与 `*Orchestrator` 两条因项目实际用 `*Entity`、`*Handler` 而永远空转。
3. 规则文件、skill 模板、各 README 彼此矛盾，也和代码不符。

### 1.2 业界做法（调研结论）

- **阿里 COLA**（与 Patra 形态最像：每层一个 Maven 模块）3.1 起改为「模块按层，模块内顶层包按领域，领域下再按功能」：`app.customer.executor`、`domain.customer.gateway`、`infrastructure.customer.CustomerDO`。理由是领域内聚、改一个功能只动一处、复杂的域好整体拆出去。
- 美团 DDD 实践、ddd-by-examples/library、CodelyTV、Sairyss/domain-driven-hexagon、Spring Modulith、Simon Brown、Drotbohm、Graça：都是业务子域在外、技术分层在内；共同强调边界要靠可执行的检查守。
- 按类型分包的样本（buckpal、RuoYi、mall）要么只有一个子域，要么是领域逻辑很薄的 CRUD。
- 同一服务、同一个库内的跨子域纯查询：微软 eShopOnContainers 的 CQRS 读端做法是独立读侧直接 SQL 联表、返回页面 DTO，不受聚合边界约束。
- Spring Modulith 只认「顶层包 = 模块」，识别不了「层模块里套子域包」；jMolecules 只管层不管子域。Patra 继续用自写 ArchUnit 规则。

## 2. 目标、非目标、反目标

**目标**

1. 所有六边形服务用同一套分包模板，单子域服务也带子域层。
2. 模板由 ArchUnit 机器强制：起了白名单之外的包名、跨子域碰对方内部类，`./gradlew check` 失败。
3. 除 ingest 外的 5 个服务迁移到模板。ingest 去留未定，本次只改它对 registry / object-storage api 的 import（§8.3）。
4. 分层规则文件能按路径自动加载；规则文件、skill、README 的分包说明只有一个版本且与代码一致。

**非目标**

- 不改 HTTP 路径、`/_internal` 契约路径、数据库 schema、Flyway 脚本、配置键、指标名。
- 不改类名（§6.2 为让规则不空转而改的规则除外，规则改的是匹配串，不是业务类名）。
- 不动 `patra-gateway-boot`、`patra-common`、`patra-tool/*`（`patra-tool-test` 的规则类除外）、`patra-ingest-expr`、`patra-ingest-provenance` 的包结构；它们只改指向迁移后类的 import。
- 不引入 Spring Modulith、jMolecules。
- 不删除现有类，§8 明确列出的除外。

**反目标**（技术上完成了也算失败）

- 迁移改变运行时行为。允许的实现改动只有 §8 列出的几项，且都必须保持行为不变：portal 读适配器改写为 SQL（查询结果等价）、跨子域写改为同事务同步事件、新增读端口替代直接读对方 DAO、底座反向引用的小清理。
- 为了过规则造空壳包或空壳子域。
- 子域切得过碎：一个子域只有一两个类、却要在多层各建一个包。
- 规则里的例外清单越写越长。出现例外先怀疑子域划错了。
- 底座变成杂物间：只被一个子域用的东西放进底座。

## 3. 分包模板

`{根}` 是服务根包，如 `dev.patra.catalog`；`{子域}` 是该服务声明的子域名。

```
{根}.domain.{子域}
├─ model.aggregate      聚合根
├─ model.entity         聚合内实体
├─ model.vo             值对象、输入草稿
├─ model.enums          枚举
├─ model.read           读模型（读端口的返回值）
├─ policy               无状态规则与限额（*Rules、*Limits、*Policy）
├─ service              领域服务（跨聚合的纯计算）
├─ event                本子域发布的领域事件
├─ exception            领域异常与本子域的 *ErrorTrait
└─ port
   ├─ repository        聚合持久化端口 *Repository
   ├─ read              读端口 *ReadPort
   ├─ gateway           驱动端口 *Gateway（实现在 app）
   └─ <协作者>          其余被驱动端口 *Port，按外部协作者命名（mail、store、source、storage……）

{根}.app.{子域}
├─ usecase.<用例族>     写用例：*Command + *Handler + *Result 同包
├─ usecase.query        读用例：*QueryService + *View / *Page 及其内部辅助类型
├─ service              *GatewayImpl，以及被本子域多个用例复用的协作类
├─ listener             事件监听 *Listener
└─ config               本层的 @Configuration 与 *Properties

{根}.infra.{子域}
├─ persistence          *RepositoryAdapter、*ReadAdapter、*Row
│  ├─ entity            JPA *Entity
│  ├─ dao               Spring Data *Dao
│  ├─ mapper            MapStruct *JpaMapper
│  └─ converter         JPA AttributeConverter
├─ <协作者>             实现 domain.{子域}.port.<协作者>，包名与端口包同名
├─ support              本子域 infra 内被多个适配器复用的技术辅助类（如 StAX 解析基座、SQL 片段工具）
└─ config

{根}.adapter.{子域}
├─ rest                 面向门户的 *Controller、*ProblemFieldContributor
│  ├─ request           *Request
│  ├─ response          *Response
│  └─ converter         *ApiConverter
├─ internal             *EndpointImpl 与它用的 *ApiConverter（实现 api 的 /_internal 契约）
├─ scheduler            XXL-Job *ScheduleJob 与其参数
├─ mq                   消息监听
└─ config

{根}.api.{子域}
├─ endpoint             HTTP Interface 契约 *Endpoint
└─ dto                  契约 DTO
{根}.api.error          服务级错误码（保留包，不属于任何子域）

{根}                    启动类 Patra{Service}Application
{根}.config             组装（XXL-Job 执行器等），不属于任何子域
```

要点：

- **每层的第二级必须是已声明子域**（`api.error` 除外），第三级必须在上表的白名单里；`model`、`port`、`persistence`、`rest` 的下一级同样受白名单约束。
- `usecase.<用例族>`、`port.<协作者>` 的名字自定，用业务词（`importing`、`sync`、`letpub`），不用技术词。
- `infra.{子域}` 下除 `persistence`、`support`、`config` 外的包，名字必须与 `domain.{子域}.port` 下的某个包同名（实现谁就叫谁）。实现 `port.read` 的读适配器与查找适配器（带缓存的 `*LookupAdapter` 一族）放 `persistence`。
- 白名单里不需要的包就不建。值对象多时 `model.vo` 下可以再按主题分子包（`model.vo.body`），子包名自定。
- 测试源集（`test`、`integrationTest`、`testFixtures`）镜像 main 的包。IT 引导类、容器初始化器等夹具按 `.claude/rules/testing/conventions.md` 放，不受子域规则管。

## 4. 子域

### 4.1 子域是什么

子域是服务内的业务边界，也是将来拆服务的最小单位。划分依据是业务内聚，不是技术类别：「enrichment」「lookup」「batch」不是子域，它们是某个子域的协作者端口。

### 4.2 底座子域

底座（DDD 里的共享内核）是被同服务多个子域共用的部分。规则：

- **底座对同服务的其他子域完全公开**：所有层、所有包都可以用，依赖底座不用声明。
- **底座不依赖任何其他子域**。
- **只放真被两个以上子域共用的东西。** 只被一个子域用的，归那个子域。
- 每个服务恰好声明一个底座。业务上有自然的公共概念就用业务名（interaction 的 `subject`、ingest 的 `outbox`、identity 的 `session`）；没有就叫 `kernel`（registry、catalog）。单子域服务把唯一的子域声明为底座。

### 4.3 普通子域之间

- 只经对方 domain 层的**公开包**交互：`port.read`、`model.read`、`event`。app、infra、adapter、api 没有公开包。
- 跨子域依赖要显式声明为**依赖边**（经全部公开包）或**订阅边**（只经 `event`）。
- 子域之间无环。
- 跨子域的写不直接调对方仓储：发本子域的事件，对方用同步 `@EventListener` 在同一事务里处理（`tech/transaction.md`「同成同败的事件」）。

### 4.4 SQL 读与边界

读适配器可以用 SQL 读本服务库里的任何表。ArchUnit 只看 Java 类型依赖，看不见 SQL；类型依赖仍受 §4.2、§4.3 约束（不 import 对方的 entity、dao、Row）。

### 4.5 portal 读子域（catalog）

catalog 的门户查询要一条 SQL 联多个子域的表，独立成 `portal` 读子域：

- 包：`domain.portal.{port.read, model.read}`、`app.portal.usecase.query`、`infra.portal.persistence`、`adapter.portal.rest`；没有聚合、仓储与写用例。
- `infra.portal.persistence` 的读适配器用 `JdbcClient` 写 SQL，返回 portal 自己的 `*Row` record 与读模型。
- 只在门户用、按页面形状定制的读归 portal；被导入作业、内部 RPC、全文端点用的读留在各业务子域。
- portal 类型上只依赖底座，不声明任何依赖边。

## 5. 用词

| 东西 | 包 | 类名 |
|---|---|---|
| JPA 实体 | `persistence.entity` | `*Entity` |
| JPA 映射器 | `persistence.mapper` | `*JpaMapper` |
| 接口层转换器 | `rest.converter`；内部 RPC 用的放 `internal` | `*ApiConverter` |
| 事件监听 | `listener` | `*Listener` |
| 定时任务 | `scheduler` | `*ScheduleJob` |
| 内部 RPC 实现 | `internal` | `*EndpointImpl` |

本次只统一包名；类名后缀（如 api 的 `*Req` / `*Resp` 与 adapter 的 `*Request` / `*Response`）保持现状。

## 6. 机器强制

### 6.1 `SubdomainArchitectureRules`（patra-tool-test）

1. **底座完全公开**：`isPublished` 对底座的任何包返回真，删除 `FOUNDATION_PUBLISHED` 常量。
2. **保留包**：`{根}.api.error`（含子包）不属于任何子域，`layersContainOnlyDeclaredSubdomains` 放行；构造器拒绝把 `error` 声明为子域名。
3. **新增 `componentPackagesFollowTemplate`**：子域下的包必须落在 §3 白名单里（自定名的位置除外）。
4. **新增 `infraPackagesMatchPorts`**：`infra.{子域}.X`（X ∉ {`persistence`, `support`, `config`}）必须存在 `domain.{子域}.port.X`。
5. **新增 `everyDeclaredSubdomainHasDomainPackage`**：每个已声明子域至少在 domain 层有类，防止子域名拼错或声明了空子域。取代 `InteractionArchitectureTest` 自带的「一层出现则四层都要有」检查。
6. 其余规则（层下只有已声明子域、无环、跨子域只经公开包、边要声明、底座不依赖他人）语义不变。
7. **整分支评审后补充**：`infraPackagesMatchPorts` 另拒绝 `read`、`repository`、`gateway` 作 infra 包名；新增 `nonLayerClassesStayInRootOrConfig`（层外的类只放根包与 `{根}.config`）。`allRules()` 共 9 条，各服务架构测试执行 `allRules()` 而不是逐条调用。

### 6.2 `HexagonalArchitectureRules`（patra-tool-test）

1. `doClassesShouldResideInPersistenceEntity` 改为匹配**带 JPA `@Entity` 注解**的本服务类，要求在 `..infra..persistence.entity..`；`dataObjectsShouldNotBeAccessedOutsideInfra` 同步改。按注解而不是按类名后缀匹配，避免误伤 Spring 的 `ResponseEntity` 与 registry 的 `TemporalEntity` 接口。
2. 删除 `orchestratorsShouldResideInAppUsecase`（项目不用 Orchestrator / Coordinator，见 `tech/commandbus.md`）。ingest 现有的 `*Coordinator` 类名不改，迁移后仍在 `usecase` 下。
3. 其余规则不变。

### 6.3 各服务的架构测试

每个服务 boot 模块的 `*ArchitectureTest` 都执行三组规则的 `allRules()`：`HexagonalArchitectureRules`、`TestingRules`、`SubdomainArchitectureRules`（声明本服务的子域、底座、边），并照 interaction 排除 testFixtures 的导入。

目前 identity、registry、object-storage 只跑 3 条事务规则。第 0 步先在这三个服务上试跑 `HexagonalArchitectureRules` 与 `TestingRules` 的 `allRules()`，列出既有违规：纯属类放错包、命名不符的，随本服务迁移一起改掉；涉及行为改动的，停下来交用户决定。不用 `FreezingArchRule`、`@Disabled` 或少接规则来让 check 变绿。

## 7. 规则文件与文档

1. 修 `.claude/rules/layers/*.md`、`tech/commandbus.md`、`tech/jpa.md`、`tech/observability.md` 的 `paths:`，改成能匹配 `patra-api/<服务>/<模块>/…` 的写法，并实测确认能注入。
2. 新增 `.claude/rules/tech/package-layout.md`（`paths: patra-api/**/*.java`）：只写 §3 模板、§4 子域与底座规则、§5 用词。推导留在本 spec。
3. 删改与模板冲突的旧说法：`layers/domain.md`、`application.md`、`infrastructure.md`、`adapter.md`、`tech/port-service.md` 的包位置表，patra-hexagonal skill 的 `resources/port-service-guide.md`、`controller-guide.md` 的目录示例。
4. 各服务 README 的分包说明按新结构更新；interaction README §2、§3 改为引用新规则（底座公开范围按 §4.2 放宽）。

## 8. 各服务迁移

旧包 → 新包的完整映射见附录。这里列每个服务的子域、底座、边，以及允许的实现改动。

### 8.1 object-storage

- 子域：`file`（同时是底座）。只搬包。

### 8.2 registry

- 子域：`dictionary`（`reference` 并入）、`provenance`、`expr`；底座 `kernel`（只在 domain 层：6 个异常根、`RegistryKey*` 两个类）。
- 边：`expr → provenance`（依赖边）。
- 实现改动：`ExprRepositoryAdapter` 现在直接用 `ProvenanceDao` 把 code 解析成 id。provenance 新增 `port.read.ProvenanceReadPort` 与 `infra.provenance.persistence.ProvenanceReadAdapter`，expr 改用它（TDD）。

### 8.3 ingest（本次不迁）

ingest 的去留另行决定，本次保持现有分包，不接 `SubdomainArchitectureRules`，`IngestArchitectureTest` 只随 §6.2 的共享规则改动保持通过。只改它（含 `patra-ingest-expr`）对 registry、object-storage api 的 import 与字符串全类名（第 0 步，§9.1）。

以下是 10/29 分析得出的方案，留作以后迁移时参考：

- 子域：`orchestration`（plan 与 execution 合并：两者互相引用 79 处，`TaskAggregate` 两边都写）；底座 `outbox`（消息落库、中继、RocketMQ 发布、通道目录与消息头、发送骨架）。
- 边：无（orchestration 只依赖底座）。
- 实现改动（底座不能反向依赖 orchestration，7 处）：删除只有测试在用的 `IngestPublishingChannels.payloadType()`；`OutboxPublisherProperties` 的默认值写成字面量；Outbox 三个异常改继承 `DomainException`；`OutboxRelayJob` 改抛 outbox 自己的参数异常。`app.config.IngestAppConfig` 是空类，层根下不能放类，删除。

### 8.4 identity

- 子域：`account`（用户与围绕用户的全部流程：注册、登录、再认证、密码、用户名、邮箱、封禁、注销、资料、头像、公开主页、邮件）；底座 `session`（会话签发与撤销、会话存储、登录记录、再认证守卫、会话生命周期、登录设备列表、网关查会话、IP 归类、Redis 限流脚本与故障判定、新设备提醒限流端口）。
- 边：无。
- 依据：account 用 session 24 处（`SessionIssuer`、`SessionTerminator`、`ReauthenticationGuard`、`SessionStorePort` 等），session 反过来用 account 的只有一批放错了位置的公共件，按 §4.2「两个以上子域共用即进底座」全部归 session：
  - `IpAddressScope`（account 的 `ClientOriginResolver` 与 session 的查询都用）、`ActionThrottleScript`（两边的限流适配器都用）、`TransientRedisFailures`、`NewDeviceMailThrottlePort`（登录与踢设备都用）。
  - 登录记录与会话签发用到的客户端与设备值：`ClientOrigin`、`RequestOrigin`、`LoginClient`、`DeviceId`、`DeviceProfile`、`DeviceClass`、`LoginEndReason`。
  - `ClientOrigin.toNotice()` 返回的 `SecurityNotice` 及它依赖的 `OutgoingMail`（邮件的公共数据结构）。`EmailDeliveryPort`、各邮件类型与邮件模板仍归 account。
  - 服务级的 `IdentityErrorTrait`：两个子域的异常都用。
  - 新设备提醒的判定与发信（`NewDeviceLoginPolicy`、`NewDeviceLoginMail`）只有登录用，留在 account。
- 实现改动：无，只搬包。`AuthController` 的 `POST /auth/logout` 拆成 `adapter.session.rest.LogoutController`（路径不变）。
- **实际（10/29 迁移按代码实测依赖修正）**：`OutgoingMail`、`RequestOrigin`、`DeviceProfile` session 并未使用，归 account；`SecurityNotice` 不依赖 `OutgoingMail`。`NewDeviceLoginPolicy`、`InvalidUserFieldsException`、`UserFieldViolations`、`UserModifiedConcurrentlyException`、`TemporarilyUnavailableException` 被 session 使用，进底座 session。13 个邮件 record 放 `domain.account.model.vo.mail`；`ActiveUserSession` 等会话值对象放 `domain.session.model.vo`（端口包只放端口）。

### 8.5 interaction

- 不改子域与边。`app.notification.eventhandler` → `app.notification.listener`；`domain.subject.model.{source,user}` 按 §3 白名单归入 `model.vo` / `model.read`（底座完全公开后 `model.user` 不再需要单列）。

### 8.6 catalog

- 子域：`publication`（含期刊卷期实例）、`venue`、`mesh`、`organization`、`author`、`fulltext`、`portal`（含引文格式化与导出）；底座 `kernel`（异常基类、`DataSourceCode` / `DictionaryType`、`SourceStandard`、`SuggestQuery` / `MatchMode`、跨子域 ID `VenueId`、被 publication 与 portal 共用的书目值对象 `PublicationDate` / `PageRange` / `Pagination` / `InlineMarkup` / `ArticleNumber`、fulltext 与 portal 共用的 `FullTextStatus`、文件下载、运行锁、字典解析、StAX 解析基座、`LikePatterns` / `ProjectionLists`）。
- 边：`publication → venue`、`publication → organization`、`publication → author`、`venue → mesh`（依赖边）；`publication → fulltext`（订阅边）。无环。
- 实现改动：
  1. portal：5 个读适配器（PublicationSearch、PublicationDetail、VenueBrowse、VenueDetail、CitationRecord）从 JPA Dao 改为 `JdbcClient`；删 `PublicationSearchDao`、`PublicationDetailDao`、`CitationRecordDao`；从 `VenueDao` 拆出约 15 条门户查询；19 个 Row 投影接口改为 portal 的 record。SQL 本身原样搬运。
  2. fulltext 写 publication 的 `markPmcOpenAccess` 改为 fulltext 发事件、publication 同步监听（同一事务）。
  3. author 新增 `port.read`（ORCID → 作者 ID）；mesh 在 `port.read` 新增名称 → UI 查询；Funder 查找整组从 publication 搬到 organization 的 `port.read`。各自替换现在直接读对方 DAO / 仓储的代码。
  4. `XmlParsingHelper` 去掉对 `LsiouXmlElements` 的静态 import，常量搬进 helper。
- **实际（10/29 迁移按代码实测依赖修正）**：`ArticleNumber`、`InlineMarkup` 只有 portal 使用，归 portal；`FullTextAvailability`、`Orcid`、`PublicationDateType` 等 7 个类被两个以上子域共用，进底座 kernel。portal 的 Row 投影是 18 个（`PublicationLastSyncedRow` 已删），改为 record 后访问器由 `getXxx()` 变为 `xxx()`。各层 `*Properties` 都放本层的 `config` 包。卷期实例整组归 publication；Funder、Venue 查找端口放 `port.read`、实现放 `persistence`。

## 9. 实施顺序与验证

### 9.1 第 0 步：公共部分（`refactor/package-layout` 上串行）

1. `patra-tool-test` 规则改动（§6.1、§6.2），TDD：每条新规则先写违规样例的失败测试。
2. 规则文件与 `paths:` 修复（§7.1、§7.2），实测注入。
3. **要迁移的 5 个服务的 api 模块改包名**，连同所有调用方的 import 一起改：registry（catalog 4、ingest 8 个文件，含 `patra-ingest-expr` 的 `@ConditionalOnClass` 字符串）、object-storage（ingest 3）、catalog（identity 4、interaction 8）、identity（interaction 6、gateway-boot 4）。interaction 的 api 模块没有类，不用改。这样第 1 步各服务互不碰对方文件，ingest 在第 0 步之后不再改动。
4. `./gradlew check` 通过后提交，作为第 1 步的基线。

### 9.2 第 1 步：各服务并行

- 每个服务一个会话、一个 worktree，分支从第 0 步的提交切出，只改本服务目录（外加 `spotbugs-exclude.xml` 里本服务的行）。
- 顺序与并发：catalog 改动最大，最先开工；同时跑全量 `check` 的会话不超过 3 个（每个会起 PG 测试容器，本机并行不稳）。
- 搬包用 `git mv` + 改 package / import，worktree 里用不了 IDEA 的重构；靠编译与测试兜底。
- 每个服务内顺序：先做 §8 列出的实现改动（TDD，在旧包里做），再整体搬包，最后接入 §6.3 的架构测试。
- 完成后合回 `refactor/package-layout`。

### 9.3 第 2 步：收尾（串行）

1. 合并后全量 `./gradlew check`。
2. README、skill、旧规则文件按 §7.3、§7.4 更新。
3. 一个后端 PR，按根 CLAUDE.md 的评审流程。

### 9.4 门控

每个服务迁完都要过：

1. `./gradlew :<service>:check`（含 integrationTest）。
2. **旧包名字面量为零**：在整个仓库 grep 迁移前的包名字符串。编译查不出的地方包括：`@ConditionalOnClass(name = …)`（registry → ingest-expr 已知一处）、`@ComponentScan(basePackages = …)`（registry 4 个、ingest 5 个 IT）、`spotbugs-exclude.xml` 的精确全类名、`META-INF/spring.factories`、`*.imports`、`application*.yml` 与 logback 里的 `logging.level.dev.patra…`、`.run/*.run.xml`。
3. 测试数量不减少（搬包前后 `test` / `integrationTest` 用例数一致，§8 新增的测试除外）。
4. catalog portal：改写前先为 5 个读适配器补齐覆盖现有行为的 IT（用现有 IT 能覆盖的就不补），改写后同一批 IT 通过。再做真实数据回归：本机启动迁移后的 catalog，数据源指向 mini 的 PG（本次没有新 Flyway 脚本，不改库；dev 数据可丢弃），**关掉本机的 XXL-Job 执行器注册**，避免 mini 的调度中心把作业派到本机；门户 Playwright e2e 指向本机网关，与 mini 线上版本逐页对照。

## 10. 待定与留给实施者的决定

1. ingest 本次不迁（10/29 用户定），去留以后再定。
2. `XxlJobConfig` 统一放 boot 的 `{根}.config`（interaction 已是这样）；boot 缺 xxl-job 依赖就补，改了 `*.gradle.kts` 要提醒在 IDEA 里点「加载 Gradle 更改」。
3. 附录里标「疑似死代码」的类（ingest 的 `ConsumerGroups`、`StoragePort` 等，catalog 的 `IssnType` 等）照搬不删，另记。
4. 各服务内 `model.vo` 是否按主题分子包、`usecase` 用例族的具体命名，由实施者按 §3 自定。
5. **附录遗漏的处理规则**：搬包时发现附录没列到的类、或按附录搬会违反子域规则时——被底座和至少一个其他子域共用的类型，移入底座；只被一个子域用的，归那个子域；需要改代码（而不只是搬包）才能化解的，停下来交用户决定。
6. §9.4 第 2 条的 grep 范围限定在代码与配置（`patra-api`、`patra-tool`、`spotbugs-exclude.xml`、`.run`），不含 `docs/patra` 下只读的历史 spec。

## 11. 顺带发现（不在本次范围）

- `patra-api/patra-identity/patra-identity-session` 是空壳目录（无构建脚本、未被 include、零 Java 文件）。
- catalog 测试目录有多套一层的情况：`infra/parser/parser/…`、`infra/persistence/converter/converter/…`，包声明本身正确；迁移时随搬包自然消失。
- 陈旧路径：`patra-ingest/README.md` 写 `com/patra/ingest`，`patra-gateway-boot/README.md` 写 `dev/linqibin/patra/gateway`。
- `.claude/rules/project-info.md` 说 patra-common 含「Provenance 服务契约」，代码里只有 5 个数据类。
- registry 的 `RegistryQuotaExceeded` 只被 boot 的错误映射引用，疑似无用。
