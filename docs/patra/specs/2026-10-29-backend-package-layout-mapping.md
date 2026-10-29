# 附录：各服务子域划分与旧包 → 新包映射

配套 `2026-10-29-backend-package-layout-design.md`。本附录是 10/29 四个子代理按**窄模型**（底座只公开 6 个 domain 包）分析的结果。10/29 用户定为**宽模型**（底座对同服务其他子域完全公开），以下按宽模型修正，**冲突时以 spec §8 为准**：

- ingest 的 **R1（OutboxGateway）不做**：orchestration 直接用底座 outbox 的发送骨架、属性、指标、仓储与技术重试端口，`TechnicalRetryAdapter` 保留。R2 照做。
- identity 的底座改为 **`session`**，account 是普通子域，**不新增 `SessionGateway`**，新设备判定不挪动；见 §4 修正。
- catalog 的书目值对象（`PublicationDate` 等）与 `FullTextStatus` 进底座 `kernel`；文件下载、运行锁、字典解析、StAX 解析基座留在底座 `kernel` 的 domain / infra，不新建 tool 模块；底座名定为 `kernel`。
- registry 的 `kernel.policy` 不再需要规则特批。

分析中间产物（类级依赖图、脚本）在会话 scratchpad，不进仓库：

- catalog：`py/scan.py`、`py/final2.py`、`deps.json`、`cls3.json`
- identity：`identity-subdomain-report.md`、`graph.py`、`fin.py`

包名省略 `dev.patra.{服务}.`。测试源集（test / integrationTest / testFixtures）随被测类镜像。

---

## 1. object-storage（单子域 `file`，同时声明为底座）

| 旧 | 新 |
|---|---|
| `domain.model.aggregate.FileMetadata` | `domain.file.model.aggregate` |
| `domain.model.enums.*` | `domain.file.model.enums` |
| `domain.model.vo.*` | `domain.file.model.vo` |
| `domain.port.FileMetadataRepository` | `domain.file.port.repository` |
| `app.recordupload.*` | `app.file.usecase.recordupload` |
| `infra.adapter.persistence.FileMetadataRepositoryAdapter` | `infra.file.persistence` |
| `infra.adapter.persistence.converter.mapper.*` / `dao.*` / `entity.*` | `infra.file.persistence.{mapper,dao,entity}` |
| `adapter.rest.internal.StorageEndpointImpl` | `adapter.file.internal` |
| `api.endpoint.StorageEndpoint` / `api.dto.*` | `api.file.endpoint` / `api.file.dto` |

外部调用方：ingest 3 个文件（`HttpClientConfiguration`、`StorageMetadataAdapter`、`StorageMetadataRequestConverter`）。`spotbugs-exclude.xml:645` 有 `StorageEndpointImpl` 精确全类名。

## 2. registry（`dictionary`、`provenance`、`expr` + 底座 `kernel`）

- `reference` 并入 `dictionary`：`ReferenceStandard` 挂在字典类型上，唯一消费者是 `DictionaryQueryService`。
- 子域间真实依赖只有 `expr → provenance`（`ExprRepositoryAdapter` 用 `ProvenanceDao` 把 code 解析成 id）。化解：provenance 新增 `port.read.ProvenanceReadPort` + `infra.provenance.persistence.ProvenanceReadAdapter`，声明依赖边 `expr → provenance`。备选：expr 并入 provenance。
- 底座 `kernel` 只在 domain 层：6 个异常根（`RegistryException`、`RegistryNotFound`、`RegistryConflict`、`RegistryRuleViolation`、`RegistryQuotaExceeded`、`DomainValidationException`，后者被几乎所有 VO 用作静态校验）+ `RegistryKeyStandardizer`、`RegistryKeyPlaceholders`（放 `kernel.policy`）。

| 旧 | 新 |
|---|---|
| `domain.model.aggregate.ProvenanceConfiguration` | `domain.provenance.model.aggregate` |
| `domain.model.vo.{dictionary,expr,provenance,reference}` | `domain.{x}.model.vo`（reference → dictionary） |
| `domain.model.read.{x}` | `domain.{x}.model.read` |
| `domain.port.{Dictionary,ReferenceStandard}Repository` | `domain.dictionary.port.repository` |
| `domain.port.ProvenanceConfigRepository` / `ExprRepository` | `domain.provenance.port.repository` / `domain.expr.port.repository` |
| `domain.exception.{dictionary,provenance}.*` | `domain.{x}.exception` |
| `domain.exception.*`（6 个根） | `domain.kernel.exception` |
| `domain.support.RegistryKey*` | `domain.kernel.policy` |
| `domain.support.TemporalEntity` | `domain.expr.model.vo` |
| `app.service.*QueryService` + `app.converter.*QueryAssembler` | `app.{x}.usecase.query` |
| `infra.adapter.persistence.*RepositoryAdapter` | `infra.{x}.persistence` |
| `infra.adapter.persistence.converter.mapper` / `dao.{x}` / `entity.{x}` | `infra.{x}.persistence.{mapper,dao,entity}` |
| `adapter.rest.*EndpointImpl` + `adapter.rest.converter.*ApiConverter` | `adapter.{x}.internal` |
| `adapter.rest.dictionary.{Controller,response,mapper}` | `adapter.dictionary.rest.{,response,converter}` |
| `api.endpoint.*` / `api.dto.{dict,expr,provenance}` | `api.{x}.endpoint` / `api.{x}.dto`（`dict` → `dictionary`） |
| `api.error` | 不变（保留包） |

外部调用方 12 个文件（catalog 4、ingest 8）。**坑**：`patra-ingest-expr` 的 `ExprCompilerAutoConfiguration:89-90` 用字符串全类名 `@ConditionalOnClass(name = "dev.patra.registry.api.endpoint…")`，改名后自动配置静默失效。4 个 infra IT 写死 `@ComponentScan(basePackages = "…persistence.converter.mapper")`。

## 3. ingest（`orchestration` + 底座 `outbox`）

**10/29 用户定：本次不迁 ingest**，本节留作以后迁移时参考。

- plan 与 execution 不能拆：execution→plan 19 处、plan→execution 60 处，`TaskAggregate` 两边都写，拆开必有环。合并为 `orchestration`，包内保留 `usecase.plan`、`usecase.execution`。
- 底座 `outbox`：OutboxMessage / OutboxRelayLog、relay 用例、RocketMQ 发布器、`OutboxRelayJob`、通道目录与消息头。

前置重构（窄模型下）：

- **R1**：orchestration 的 `TaskOutboxPublisher` / `PublicationEventPublisher` 继承 `app.outbox.core.AbstractOutboxPublisher`，并用 `OutboxPublisherProperties`、`OutboxMetrics`、`OutboxMessageRepository`、`TechnicalRetryPort`（21 处 / 6 个类型）。新增 `domain.outbox.port.gateway.OutboxGateway` + `app.outbox.service.OutboxGatewayImpl` 吸收骨架，配置键 `patra.outbox.publisher.*` 与指标标签不变。
- **R2**（outbox→orchestration 7 处）：去掉 `IngestPublishingChannels.payloadType()`（只有测试用）；属性默认值写字面量；Outbox 异常改继承 `DomainException`；`OutboxRelayJob` 改抛自己的参数异常。

| 旧 | 新（O = orchestration，X = outbox） |
|---|---|
| `domain.event` | Outbox* 5 个 → X.event；其余 4 个 → O.event |
| `domain.exception` | Outbox* 3 个 → X.exception；其余 → O.exception |
| `domain.messaging` | `IngestPublishingChannels` → X.model.enums；ChannelKey / OperationType / MessageOperations → X.model.vo |
| `domain.model.aggregate` | O.model.aggregate |
| `domain.model.entity` | Cursor / CursorEvent / TaskRun / TaskRunBatch → O；OutboxMessage / OutboxRelayLog → X |
| `domain.model.enums` | OutboxStatus / RelayStatus → X；其余 13 个 → O |
| `domain.model.snapshot` | O.model.vo |
| `domain.model.vo.<11 个主题>` | O.model.vo（拍平或保留主题子包） |
| `domain.model.vo.relay` | X.model.vo |
| `domain.outbox` | X.model.vo |
| `domain.policy` | X.policy |
| `domain.service` | O.service |
| `domain.strategy.BatchGenerationStrategy` | O.policy |
| `domain.support.{HashUtils,TypeReference}` | O.service（`HashUtils` 被 `patra-ingest-expr` 引用） |
| `domain.port` 19 个 | Outbox 3 个 Repository → X.port.repository；OutboxPublisherPort → X.port.messaging；8 个业务 Repository → O.port.repository；ExpressionCompilerPort → O.port.compiler；PatraRegistryPort → O.port.registry；ProvenanceDataPort → O.port.provenance；Storage 两个 → O.port.storage |
| `app.config.IngestAppConfig` | 删除（空类） |
| `app.eventhandler` | O.listener（app） |
| `app.usecase.plan`（根 + command + dto） / 子包 assembler、expression、json、slicer、validator、window | `app.orchestration.usecase.plan` / `app.orchestration.service.plan.*` |
| `app.usecase.execution`（根 + command） / 子包 complete、coordination、cursor、idempotency、lease、prepare、session、strategy | `app.orchestration.usecase.execution` / `app.orchestration.service.execution.*` |
| `app.usecase.{plan,execution}.publisher` + `app.outbox.{operations,constants}` | `app.orchestration.service.publisher` |
| `app.outbox.{core,metrics}` | `app.outbox.service` |
| `app.outbox.config` + `app.usecase.relay.config` | `app.outbox.config` |
| `app.usecase.relay`（根 + command + dto + coordinator） / 其余子包 | `app.outbox.usecase.relay` / `app.outbox.service` |
| `infra.adapter.persistence` + `persistence.{dao,entity,converter.mapper}` | 8 组 → `infra.orchestration.persistence.*`；2 组 → `infra.outbox.persistence.*` |
| `infra.adapter.compiler` | `infra.orchestration.compiler` |
| `infra.integration.provenance`（+acl）+ `infra.mapper.*` + `infra.exception` | `infra.orchestration.provenance`（参数映射放 `provenance.parameter`） |
| `infra.integration.{registry,storage}` | `infra.orchestration.{registry,storage}` |
| `infra.messaging` / `infra.config` | `infra.outbox.messaging` / `infra.outbox.config` |
| `infra.outbox.TechnicalRetryAdapter` | 删除（并入 R1） |
| `adapter.rocketmq` | `adapter.orchestration.mq` |
| `adapter.scheduler.{job,param}` | Provenance / Pubmed → `adapter.orchestration.scheduler`；OutboxRelay → `adapter.outbox.scheduler` |
| `adapter.scheduler.config.XxlJobConfig` | 子域 `config` 或 boot（boot 需加 xxl-job 依赖） |
| `api.error` | 不变 |

疑似死代码（待确认）：`payloadType()`、`TaskReadyMessage`、`PublicationReadyMessage`、`ConsumerGroups`、`StoragePort`、`IngestAppConfig`。另需同步：`patra-ingest-expr` 的 `ExprCanonicalizer` import `HashUtils`；`spotbugs-exclude.xml` 多处全类名；5 个 IT 的 `@ComponentScan`。

## 4. identity（`account` + 底座 `session`；`auth`、`profile` 不切）

**宽模型修正**：底座是 `session`。下表中原「→ `domain.account.model.vo`」的 `IssuedUserSession` / `NewUserSession` / `SessionRotation` 留在 session（`domain.session.port.store` 或 `model.vo`）；`IpAddressScope` → `domain.session.model.vo`；`ActionThrottleScript`、`TransientRedisFailures` → `infra.session.*`；`NewDeviceMailThrottlePort` → `domain.session.port.throttle`、其适配器 → `infra.session.throttle`；`NewDeviceLoginPolicy`、`NewDeviceLoginMail` 留 account。下面「窄模型下的前置重构」一条作废。

- 切 `auth` 要新增 `AccountGateway` + `CredentialGateway` + 邮件令牌网关（auth→account 31 处、account→auth 10 处）；切 `profile` 会与 account 成环（读模型同时 join 用户表与资料表）。
- 窄模型下的前置重构：**account→session 24 处**（`SessionTerminator` 7、`SessionIssuer` 2、`SessionStorePort` 2、`ReauthenticationGuard` 2、`TransientRedisFailures` 2……）方向违规，要在 `domain.account.port.gateway` 新增 `SessionGateway`（issue / rotate / terminateAll / requireRecentReauth），实现在 `app.session.service.SessionGatewayImpl`；新设备判定与发信折进 `SessionGateway.issue(...)`。session→account 只有 4 处（`IpAddressScope`、`OutgoingMail`、`SecurityNotice`、`ActionThrottleScript`）。
- `AuthController` 的 `POST /auth/logout` 拆成 `adapter.session.rest.LogoutController`（路径不变）。

| 旧 | 新 |
|---|---|
| `domain.model.{aggregate,enums,vo(+avatar,profile),read}` | `domain.account.model.*`；`UserLoginRecord` → `domain.session.model.aggregate` |
| `domain.policy`（22） | `domain.account.policy`；SessionLifetime*、ReauthenticationPolicy、NewDeviceLoginPolicy → `domain.session.policy`；`IpAddressScope` → `domain.account.model.vo` |
| `domain.exception`（24） | `domain.account.exception`；CurrentSessionRevocation、ReauthenticationRequired → `domain.session.exception` |
| `domain.service`（6） | ClientOriginResolver、EmailTokenIssuer、IssuedEmailToken → `domain.account.service`；SessionIssuer、SessionTerminator、ReauthenticationGuard → `domain.session.service` |
| `domain.port.repository` | `domain.account.port.repository`；`UserLoginRecordRepository` → `domain.session.port.repository` |
| `domain.port.{read,avatar,catalog,geolocation,hashing,password,useragent,throttle,mail}` | `domain.account.port.<同名>`；NewDeviceMailThrottlePort → `domain.session.port.throttle`；NewDeviceLoginMail → `domain.session.port.mail` |
| `domain.port.session` | IssuedUserSession / NewUserSession / SessionRotation → `domain.account.model.vo`；SessionStorePort / ActiveUserSession → `domain.session.port.store` |
| `app.usecase.{register,login,reauth,password,username,ban,deletion,email,emailchange,profile,avatar}` | `app.account.usecase.<同名>` |
| `app.usecase.auth` | AuthenticatedUser → `app.account.usecase.login`；CurrentPasswordVerifier → `app.account.service` |
| `app.usecase.{avatar.query,user.query}` + PendingEmailChange | `app.account.usecase.query` |
| `app.usecase.{logout,session}` / `session.{query,introspect}` | `app.session.usecase.{logout,revoke,purge}` / `app.session.usecase.query` |
| `infra.adapter.persistence`（+entity、dao、converter.mapper）+ `infra.adapter.read` | `infra.account.persistence.*`；UserLoginRecord 一组 → `infra.session.persistence.*` |
| `infra.adapter.{avatar,catalog,mail,password,hashing,geolocation,useragent,throttle}` | `infra.account.<同名>`；NewDeviceMailThrottleAdapter → `infra.session.throttle` |
| `infra.adapter.session` | `infra.session.store` |
| `adapter.rest.{auth,me,users,admin}` 的 Controller 与 req/resp | `adapter.account.rest.*`；MeSessionController 与会话响应 → `adapter.session.rest.*` |
| `adapter.rest.internal.{User,Session}EndpointImpl` | `adapter.account.internal` / `adapter.session.internal` |
| `adapter.rest.{ClientIp,AvatarUrls}` | `adapter.account.rest` / `adapter.account.rest.converter` |
| `adapter.scheduler.job` | 3 个 → `adapter.account.scheduler`；LoginRecordCleanup → `adapter.session.scheduler` |
| `adapter.scheduler.config.XxlJobConfig` | `dev.patra.identity.config`（boot） |
| `api.endpoint.UserEndpoint` + `api.dto` / `SessionEndpoint` + SessionIntrospection* | `api.account.{endpoint,dto}` / `api.session.{endpoint,dto}` |

外部调用方：interaction 6 个文件、gateway-boot 4 个文件。

## 5. catalog（7 个业务子域 + 底座，约 713 个类）

| 子域 | 职责 | 约类数 |
|---|---|---|
| publication | 文献主数据：PubMed 同步、重解析、按 PMID/ID 解析、语言查找；**期刊卷期实例（venueinstance）并入** | 165 |
| venue | 期刊主数据：NLM LSIOU 导入、LetPub / Scopus / OpenAlex 富化、封面、解析 | 135 |
| mesh | MeSH 主题词、限定词、SCR；名称 → UI 查询 | 110 |
| organization | ROR 导入、摘要、suggest、资助机构查找 | 60 |
| author | computed author 导入；ORCID → 作者 ID | 31 |
| fulltext | PMC 抓取、JATS 解析；全文正文、PDF、图片端点 | 70 |
| portal | 门户纯查询：feed、检索与 facets、详情、卡片、期刊浏览；**引文格式化与导出（citation 并入）** | 100 |
| 底座（名待定） | 异常基类、DataSourceCode / DictionaryType、SourceStandard、SuggestQuery、MatchMode、VenueId、文件下载、运行锁、字典解析 | 25 |

化解后的依赖边：`publication → venue / organization / author`（依赖边），`venue → mesh`（依赖边），`publication → fulltext`（订阅边）。portal 类型上只依赖底座。无环。

现状违规与化解：

| 边 | 处数 | 化解 |
|---|---|---|
| publication→venue | 14 | venueinstance 整组归 publication；VenueId 进底座；VenueLookupPort → `domain.venue.port.read` |
| publication→organization | 10 | Funder 查找整组搬到 organization，端口放 `port.read` |
| publication→author | 2 | author 开 `port.read`（`findAuthorIdsByOrcidIn`） |
| fulltext→publication | 2（跨聚合写 `markPmcOpenAccess`） | fulltext 发事件，publication 在 `app.publication.listener` 同步订阅 |
| venue→mesh | 2 | mesh 在 `port.read` 加名称 → UI 查询 |
| portal→publication | 21 | 值对象 PublicationDate / PageRange / Pagination / InlineMarkup / ArticleNumber：进底座或 portal 自建 |
| portal↔venue | 10 | 从 `VenueDao`（17 条 native）拆出门户查询改 JdbcClient |
| portal→fulltext | 5 | FullTextStatus：portal 自建或进底座 |
| 底座→venue | 1 | `XmlParsingHelper` 静态 import `LsiouXmlElements`，常量搬进 helper |
| 各子域→底座 infra | mesh 16、publication 8、venue 3、fulltext 1 | StAX 解析基座（`parser.support` 6 个 + `RecordParsingStrategy`）、`LikePatterns`、`ProjectionLists` |

portal 读子域：

- 归 portal：读端口 PublicationSearch / PublicationDetail / VenueBrowse / VenueDetail / CitationRecord；QueryService 9 个；Controller PortalPublication（去掉 fulltext 方法）/ PortalVenue / PortalCitation。
- 留业务子域：PublicationResolve、VenueResolve、MeshDescriptor、Organization（内部 RPC 与 suggest）、VenueEnrichment、FullTextCandidate、FullTextFile、PublicationFullText。
- 改写：**5 个 ReadAdapter** 从 JPA Dao 改 JdbcClient；删 PublicationSearchDao / PublicationDetailDao / CitationRecordDao；从 VenueDao 拆出约 15 条门户查询；19 个 Row 投影改 portal 自己的 record。现有查询都是 native SQL，主要是搬运。

映射规则（按包，`{x}` 按类名前缀定子域）：

| 旧 | 新 |
|---|---|
| `domain.model.aggregate` / `entity` | `domain.{x}.model.aggregate` / `domain.mesh.model.entity`；VenueInstance → publication |
| `domain.model.enums`（44） | publication 18（含 DisambiguationStatus / Method）、venue 7（含 CitationSubset）、mesh 6、organization 6、author 1、fulltext 2、portal 2（CitationStyle、ExportFormat）、底座 2（DataSourceCode、DictionaryType） |
| `domain.model.read.{citation,…}` / `read.portal` | `domain.{x}.model.read`（citation → portal）；FullTextReadModel、FullTextFileRef → fulltext，其余 12 个 → portal |
| `domain.model.vo.*` | `domain.{x}.model.vo`（子包保留）；VenueId、SourceStandard → 底座；VenueInstanceId、JournalInstanceParams → publication；EvidenceLevel → `portal.model.read` |
| `domain.exception`（15） | 5 个通用 → 底座；其余按前缀；PublicationNotFound、VenueNotFound → portal |
| `domain.port.repository` / `read` / `lookup` / `gateway` | 按前缀拆；VenueLookup → `venue.port.read`；FunderLookup → `organization.port.read`；LanguageLookup → `publication.port.lookup`；VenueInstanceGateway → `publication.port.gateway` |
| `domain.port.{batch,parser,storage,image,source,enrichment,citation,lock,registry}` | 按前缀拆到 `domain.{x}.port.*`；FileDownload*、lock、registry → 底座；citation → portal |
| `domain.query` | PageResult、PagingParams → `portal.model.read`；SuggestQuery、MatchMode → 底座 `model.vo` |
| `domain.service.fulltext` | `fulltext.policy` + `fulltext.service` |
| `app.usecase.{author,mesh,organization}`（+command、dto） | `app.{x}.usecase.importing`；query → `usecase.query` |
| `app.usecase.publication.{sync,query,service}` | `app.publication.usecase.{sync,query}` / `app.publication.service` |
| `app.usecase.venue.{letpub,scopus,pubmed,query}` + VenueEnrichRunStats | `app.venue.usecase.*` / `app.venue.service` |
| `app.usecase.fulltext.{fetch,parse}` + FullTextJobProperties | `app.fulltext.usecase.*` / `app.fulltext.service` |
| `app.usecase.portal.query` | 2 个全文 QueryService → `app.fulltext.usecase.query`；其余 → `app.portal.usecase.query` |
| `infra.persistence.{entity,dao}` | 按表前缀到 `infra.{x}.persistence.*`；`cat_venue_instance` → publication |
| `infra.persistence.converter.{mapper,attribute}` + `support.StringUtils` | `infra.{x}.persistence.{mapper,converter}` |
| `infra.adapter.persistence` / `read` / `lookup` | `infra.{x}.persistence` / 按读端口归属 / `infra.{venue,organization,publication}.lookup` |
| `infra.adapter.{integration.{letpub,scopus,openalex},enrichment}` | `infra.venue.enrichment` |
| `infra.adapter.integration.{pmc,registry}` / `citation` | `infra.fulltext.source`、底座 `registry` / `infra.portal.citation` |
| `infra.adapter.{image,lock,source,storage}` | fulltext image；底座 lock；FileDownloadAdapter 底座、PubmedFileListingAdapter → publication；FullTextObjectStorage → fulltext、VenueCoverImage → venue |
| `infra.batch.*` / `infra.parser.*` | `infra.{x}.batch` / `infra.{x}.parser`（Lsiou → venue，Mesh → mesh，Pubmed → publication，jats → fulltext） |
| `adapter.rest.internal` | `adapter.{mesh,organization,publication,venue}.internal` |
| `adapter.rest.portal`（+request、response） | portal 三个 Controller → `adapter.portal.rest`；PortalMesh / PortalOrganization → 各自 `rest`；全文 → `adapter.fulltext.rest`；PortalApiConverter 按子域拆 |
| `adapter.scheduler.{job,config,exception,util}` | `adapter.{x}.scheduler`；XxlJobConfig → boot `config`；五组 `*DataSource{AutoConfiguration,Properties}` 回各子域 |
| `api.endpoint` / `api.dto.{x}` | `api.{x}.endpoint` / `api.{x}.dto` |

外部调用方：identity 4 个文件、interaction 8 个文件。改动面：220 个单测、66 个 IT、7 个 E2E 跟着搬。疑似死代码：`IssnType`、`MeshDataType`、`MeshFileType`、`MeshPersistenceException`。

## 6. interaction

已是目标布局，只需：`app.notification.eventhandler` → `app.notification.listener`；按 §3 白名单核对 `domain.subject.model.{source,user}`（`model.user` 是底座公开包，`model.source` 需归入 `model.vo` 或加白名单）。
