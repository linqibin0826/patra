# 工具层重组 工程 spec（PAP-79 ~ PAP-82）

> **Issue**：[PAP-79](https://linear.app/papertrace/issue/PAP-79) 命名与模块就位 · [PAP-80](https://linear.app/papertrace/issue/PAP-80) 依赖规则 R1–R5 · [PAP-81](https://linear.app/papertrace/issue/PAP-81) starter 模板与默认配置 · [PAP-82](https://linear.app/papertrace/issue/PAP-82) 扫描收窄与收尾
> **版本**：[v0.10 Tool Reorg](../release-specs/v0.10-tool-reorg.md)（决策 A–H；Done 判定 D1–D7）
> **日期**：2026-10-15
> **状态**：待审阅
> **盘点基线**：PR #234（`refactor/json-single-mapper`，`5a7852ecd`）的代码树，即它合并后的 main。本文所有"现状"都指这个基线；写本文时 #234 尚未合并，代码改动要等它合并后再开始。

## 1. 这份文档做什么

release spec 定了方向（按技术能力分 12 个工具模块、去 linqibin、规则挂 `check`、默认配置随模块走、扫描收窄）。本文把它落到能直接照着改的程度：每个类从哪来到哪去、每条规则怎么实现、每个配置键怎么改、每个测试怎么调。按 4 个 Issue 分节，plan 按同样的顺序切任务。

不在本文范围的事以 release spec 第 3 节为准：不补能力、不做对外发布库、不换领域内核、不动 Hutool / Jackson 在 domain 的使用、不动 ingest 去留、不修两个已登记 bug、不保留兼容层。

盘点方法：对共享区每个公开类型，按 `import` 全限定名统计被哪些 Gradle 模块的哪个 source set 引用（本仓库无通配 import，google-java-format 禁止）；配置文件、`imports` 文件、`spring.factories`、`.kts` 里的全限定名另计为"配置引用"；零引用类型再用简单类名全仓二次 grep 排除反射和字符串用法。盘点原件在会话 scratchpad（五份报告 G1–G5），本文只保留结论。

一处与 release spec 不同的数字：共享区是 **23** 个模块，不是 22（多出 `patra-common-security` 与 `patra-spring-boot-starter-security`）。下文缩写：`L:` = `linqibin-commons/linqibin-spring-boot-starter-*`，`P:` = `patra-starters/patra-spring-boot-starter-*`，`C:` = `patra-api/patra-common/patra-common-*`。

## 2. 目标结构总览

### 2.1 目录

```
patra/
├── build-logic/                       约定插件 patra.*（第 4.1 节）
├── patra-tool/                        12 个技术能力模块，group dev.patra，包根 dev.patra.tool.<能力>
│   ├── patra-tool-core                纯 Java：DDD 基类、错误类型、当前用户抽象、PatraOrders
│   ├── patra-tool-cqrs                命令总线接口 + SimpleCommandBus + 拦截器 + 自动配置
│   ├── patra-tool-web                 MVC 错误处理（含原 starter-core 的错误解析引擎）+ Long→String
│   ├── patra-tool-jpa                 实体基类、审计、Hibernate 定制、JsonColumnCodec、JPA 错误映射
│   ├── patra-tool-batch               Spring Batch 基础设施
│   ├── patra-tool-http-client         RestClient / 下载 / 隧道代理（原 rest-client）+ HTTP Interface（原 http-interface）
│   ├── patra-tool-object-storage      MinIO / S3 + 对象键生成（原 commons-storage）
│   ├── patra-tool-observability       Micrometer / Observation + 日志 converter + logback + 默认配置 EPP
│   ├── patra-tool-security            身份断言、当前用户、审计人、转发（原 P:security）
│   ├── patra-tool-openapi             springdoc + Scalar
│   ├── patra-tool-launch              PatraApplication + Nacos 默认值 EPP + 签到检查 + Clock（原 P:nacos）
│   └── patra-tool-test                Testcontainers 初始化器、ArchUnit 规则、测试自动配置
├── patra-api/
│   ├── patra-common/                  一个模块：CanonicalPublication、DataType、PublicationIdentifierType、ProvenanceCode
│   ├── patra-ingest/
│   │   ├── patra-ingest-provenance    原 P:provenance + C:provenance-api
│   │   ├── patra-ingest-expr          原 P:expr + patra-expr-kernel + RegistryConfigScope
│   │   └── patra-ingest-{domain,api,app,infra,adapter,boot}
│   ├── patra-identity/patra-identity-session   不动，只改包名
│   └── …（其余服务不变）
```

删除的目录：`linqibin-commons/`、`patra-starters/`、`patra-api/patra-expr-kernel/`、`patra-api/patra-common/patra-common-{model,enums,provenance-api,security}/`。

### 2.2 四层命名映射

| 层 | 旧 | 新 |
|---|---|---|
| 目录与 Gradle 坐标 | `linqibin-commons/linqibin-commons-core`（`:linqibin-commons:linqibin-commons-core`）、`linqibin-commons/linqibin-spring-boot-starter-<x>`、`patra-starters/patra-spring-boot-starter-<x>`、`patra-api/patra-common/patra-common-<x>`、`patra-api/patra-expr-kernel` | `patra-tool/patra-tool-<能力>`（`:patra-tool:patra-tool-<能力>`）、`patra-api/patra-common`（`:patra-api:patra-common`）、`patra-api/patra-ingest/patra-ingest-{provenance,expr}` |
| Gradle group | `dev.linqibin.commons` / `dev.linqibin.patra`（`gradle.properties` 的 `commonsGroup` / `patraGroup`） | 全部 `dev.patra`，写死在 `patra.java-base`，删掉两个 property |
| 约定插件 id | `linqibin.*`（18 个文件） | `patra.*`（第 4.1 节） |
| Java 包根 | `dev.linqibin.commons.*`、`dev.linqibin.starter.*`、`dev.linqibin.patra.starter.*`、`dev.linqibin.patra.*` | `dev.patra.tool.<能力>.*`、`dev.patra.common`、`dev.patra.ingest.{provenance,expr}.*`、`dev.patra.<service>.*` |
| 配置前缀 | `linqibin.starter.<模块>`、`patra.starter.<模块>`、`patra.security` | `patra.<能力>`（第 5.2 节） |

能力名在三处的写法：模块名用横线（`http-client`、`object-storage`），包名去横线（`httpclient`、`objectstorage`），配置前缀用横线（`patra.http-client`、`patra.object-storage`），类名前缀用大驼峰（`HttpClient`、`ObjectStorage`）。R4 用这张对应关系做检查。

### 2.3 模块一览

| 模块 | 包根 | 插件 | 硬依赖（api / implementation） | compileOnly（可选协作） | 自动配置类 | Properties 前缀 | EPP |
|---|---|---|---|---|---|---|---|
| patra-tool-core | `dev.patra.tool.core` | `patra.tool-module` + `patra.java-library` | 无 patra 模块；第三方只有 `slf4j-api`（compileOnly） | — | 无（纯 Java） | 无 | 无 |
| patra-tool-cqrs | `dev.patra.tool.cqrs` | `patra.tool-module` + `patra.spring-starter` | core | micrometer-core、micrometer-observation | `CqrsAutoConfiguration` | `patra.cqrs` | 无 |
| patra-tool-web | `dev.patra.tool.web` | 同上 | core；starter-webmvc、starter-jackson、starter-validation | — | `WebErrorAutoConfiguration`、`WebJsonAutoConfiguration` | `patra.web` | 无（`spring.jackson.*` 归它，当前不出默认值） |
| patra-tool-jpa | `dev.patra.tool.jpa` | 同上 | core；starter-data-jpa、flyway、postgresql | web（`ErrorMappingContributor`） | `JpaAutoConfiguration`（+ `JpaAuditingConfiguration`、`PatraHibernatePropertiesCustomizer` 两个 `@Configuration`） | `patra.jpa` | 无 |
| patra-tool-batch | `dev.patra.tool.batch` | 同上 | core；spring-boot-starter-batch | starter-data-jdbc、HikariCP、postgresql、micrometer | `BatchAutoConfiguration`、`BatchDataSourceAutoConfiguration`、`BatchSchemaAutoConfiguration`、`BatchMetricsAutoConfiguration` | `patra.batch` | 无 |
| patra-tool-http-client | `dev.patra.tool.httpclient` | 同上 | core；spring-web、spring-boot-restclient、httpclient5、spring-cloud-starter-loadbalancer、micrometer-core、commons-net | spring-webflux、reactor-netty-http | `HttpClientAutoConfiguration`、`HttpClientProxyAutoConfiguration`、`HttpClientStreamingAutoConfiguration`、`HttpClientDownloadAutoConfiguration`、`HttpClientInterfaceAutoConfiguration` | `patra.http-client` | 无 |
| patra-tool-object-storage | `dev.patra.tool.objectstorage` | 同上 | spring-boot-starter、minio、spring-retry、micrometer-core | aws-sdk-s3 | `ObjectStorageAutoConfiguration`、`ObjectStorageS3AutoConfiguration` | `patra.object-storage` | 无 |
| patra-tool-observability | `dev.patra.tool.observability` | 同上 | micrometer-observation、micrometer-core、spring-boot-starter-actuator、logback-classic（converter 用） | web（`ResolutionInterceptor`） | `ObservabilityAutoConfiguration`、`ObservabilityMicrometerAutoConfiguration`、`ObservabilityErrorAutoConfiguration` | `patra.observability` | `ObservabilityDefaultsPostProcessor` |
| patra-tool-security | `dev.patra.tool.security` | 同上 | web（R2 白名单）、core；spring-boot-starter-security、spring-security-oauth2-jose | jpa（`CurrentAuditorProvider`）、http-client（`InternalCallInterceptor`） | `SecurityAutoConfiguration`、`SecurityAuditingAutoConfiguration`、`SecurityForwardingAutoConfiguration`、`SecurityServletAutoConfiguration` | `patra.security` | 无（testFixtures 的测试 EPP 保留） |
| patra-tool-openapi | `dev.patra.tool.openapi` | 同上 | springdoc-openapi-scalar、therapi-javadoc | spring-web | `OpenApiAutoConfiguration` | `patra.openapi` | 无 |
| patra-tool-launch | `dev.patra.tool.launch` | 同上 | spring-boot-starter、SCA nacos-config、SCA nacos-discovery | — | `LaunchAutoConfiguration`（`Clock`） | `patra.launch` | `NacosDefaultsPostProcessor`、`ConfigSourceCheckPostProcessor` |
| patra-tool-test | `dev.patra.tool.test` | 同上 | JUnit、AssertJ、Mockito、spring-boot-starter-test 及各切片 test starter、Testcontainers、ArchUnit、WireMock、Awaitility、micrometer-core、postgresql | jackson-databind | `TestAutoConfiguration` | `patra.test` | 无 |
| patra-common | `dev.patra.common` | `patra.java-library` | jackson-annotations、jackson-databind（`CanonicalPublication` 用 `@JsonDeserialize`/`@JsonPOJOBuilder`） | — | 无 | 无 | 无 |
| patra-ingest-provenance | `dev.patra.ingest.provenance` | `patra.spring-starter` | patra-common；spring-boot-autoconfigure、spring-web（`RestClient` 类型，原来经 rest-client 的 api 传递，现在要显式声明）、jackson-databind、jackson-dataformat-xml、hutool-core、slf4j-api | micrometer-core；`runtimeOnly` http-client（只要它的 `defaultRestClient` bean） | `ProvenanceAutoConfiguration`、`ProvenanceCodeConverterAutoConfiguration` | `patra.ingest.provenance` | 无 |
| patra-ingest-expr | `dev.patra.ingest.expr` | `patra.spring-starter` | patra-common、core、patra-ingest-domain（`HashUtils`）、patra-registry-api；spring-boot-autoconfigure、jackson-databind | micrometer-core | `ExprCompilerAutoConfiguration`、`ExprFunctionAutoConfiguration` | `patra.ingest.expr` | 无 |

工具模块之间的硬依赖图（只允许指向 core，白名单 security → web）：

```
cqrs ─┐
web ──┤
jpa ──┼──► core          security ──► web（白名单）
batch ┤
http-client ┘
object-storage、observability、openapi、launch、test：不依赖任何 patra 模块
compileOnly（@ConditionalOnClass）：jpa → web、observability → web、security → jpa、security → http-client
```

## 3. PAP-79 命名与模块就位

### 3.1 去 linqibin：改名的顺序与方式

改名用 JetBrains MCP 的 `rename_refactoring`（IDEA rename package / move），不做文本替换；非 Java 引用按第 3.8 节清单改。分三个提交落地，让后面的 diff 可读：

**提交 1：纯改名（包、目录、坐标、插件 id、group，不搬类）**

| 改名操作 | 旧 | 新 |
|---|---|---|
| rename package | `dev.linqibin.patra` | `dev.patra`（覆盖 6 个服务、patra-common、identity-session、P:starters 的 `dev.linqibin.patra.starter.*`） |
| rename package | `dev.linqibin.commons`（commons-core） | `dev.patra.tool.core` |
| rename package | `dev.linqibin.commons.storage` | `dev.patra.tool.objectstorage.key` |
| rename package | `dev.linqibin.starter.core` | `dev.patra.tool.core`（与 commons-core 暂时共享包根，两边 `cqrs` / `error` 子包没有同名类；提交 2 把它的内容拆进 cqrs / web / observability / launch 后该模块删除） |
| rename package | `dev.linqibin.starter.{web,jpa,batch,observability,openapi,test}` | `dev.patra.tool.{web,jpa,batch,observability,openapi,test}` |
| rename package | `dev.linqibin.starter.restclient`、`dev.linqibin.starter.httpinterface` | 都改成 `dev.patra.tool.httpclient`（两模块暂时共享一个包根；两边的 `config` / `interceptor` 子包没有同名类，提交 2 合并模块时文件原地不动） |
| rename package | `dev.linqibin.starter.objectstorage` | `dev.patra.tool.objectstorage` |
| rename package | `dev.linqibin.starter.redisson` | 不改（提交 3 整个删除） |
| rename package | `dev.patra.starter.{security,nacos}`（提交 1 第一行改完后的名字） | `dev.patra.tool.security`、`dev.patra.tool.launch` |
| rename package | `dev.patra.starter.{provenance,expr}`、`dev.patra.expr`（expr-kernel）、`dev.patra.common.provenance.api` | `dev.patra.ingest.provenance`、`dev.patra.ingest.expr`（kernel 的 `And`/`Atom`… 与 starter 的 `compiler.*` 子包互不重名）、`dev.patra.ingest.provenance.api` |
| rename package | `dev.patra.common.security`（C:security） | `dev.patra.tool.core.security` |
| rename package | `dev.patra.common.model`、`dev.patra.common.model.enums`、`dev.patra.common.enums` | 都并到 `dev.patra.common`（4 个类同包，无重名） |
| 目录 + `settings.gradle.kts` | 第 3.7 节的 `includeAt` 表 | 同表 |
| 约定插件 id | `linqibin.*` | `patra.*`（第 4.1 节），`LinqibinDependencyManagement.kt` → `PatraDependencyManagement.kt`，`applyLinqibinDependencyManagement` → `applyPatraDependencyManagement` |
| Gradle group | `gradle.properties` 的 `patraGroup` / `commonsGroup` | 删除；`patra.java-base` 里 `group = "dev.patra"` |

提交 1 做完必须 `./gradlew compileJava compileTestJava compileIntegrationTestJava compileE2eTestJava` 通过、全仓 `grep -r linqibin --include='*.java' --include='*.kts' --include='*.yml' --include='*.yaml'` 只剩 `docs/patra/`。此时模块数还是 23 个，只是名字换了。

**提交 2：结构搬迁（合并、拆分、归位）**——第 3.2～3.5 节的搬迁表。
**提交 3：删除**——第 3.6 节，提交信息里记提交 2 的 SHA 方便找回。

### 3.2 12 个工具模块的类清单与来源

下表只列"搬动"的类；没出现的类留在原模块、只改包名。"→ 删除"见第 3.6 节。

**patra-tool-core**（原 commons-core 的大部分 + C:security + starter-core 的 `PatraOrders` 新建）

| 类（新包 `dev.patra.tool.core`） | 来源 | 说明 |
|---|---|---|
| `domain.{AggregateRoot, DomainEvent, ChildEntityChange}` | commons-core `domain` | `ChildEntityChange` 被 `AggregateRoot` 内部使用，catalog-domain / catalog-infra 的生产代码经 `registerChild*` / `pullChildChanges` 用到它——**不能删**（PAP-82 清单里这条是错的，见第 9 节 J5） |
| `error.{DomainException, ApplicationException}`、`error.codes.{ErrorCodeLike, HttpStdErrors}`、`error.field.*`、`error.problem.ErrorKeys`、`error.remote.{RemoteCallException, RemoteErrorHelper}`、`error.retry.HasRetryAfter`、`error.trait.*` | commons-core `error` | `RemoteErrorHelper` 只有 ingest-infra 直接用，但 `RemoteCallException` 内部也用它，留在 core |
| `security.{CurrentUser, CurrentUserPort, AccountType, ClientType, AuthenticationRequiredException}` | C:security | identity-domain 直接 import `AccountType` / `ClientType`，所以必须在 R3 白名单模块里，进 core 而不是 security（见 J2） |
| `PatraOrders` | 新建 | 第 5.7 节 |

从 commons-core 移出的：`cqrs.*` → cqrs；`error.codes.CoreErrorCode` → cqrs（改名 `CqrsErrorCode`）；`enums.Priority`、`messaging.ChannelKey`、`type.TypeReference`、`util.HashUtils` → ingest-domain；`json.*`（7 个）→ ingest-app；`query.{PageResult, PagingParams}` → catalog-domain；`util.StringUtils` → catalog-infra；`enums.SortDirection`、`domain.ReadOnlyAggregate` → 删除。移完后 core 的第三方 import 只剩 `org.slf4j`（`RemoteCallException`）和 Lombok，`build.gradle.kts` 里 `api(hutool-core)`、`api(jackson-*)` 全部删掉，core 不再把任何第三方库传递给 domain 层。

测试跟着走：`AggregateRootTest`、`FieldViolationTest`、`RemoteCallExceptionTest`、`RemoteErrorHelperTest`、`HasRetryAfterTest` 与 C:security 的 4 个测试留在 core；`JsonNormalizerTest` → ingest-app；`PageResultTest`、`PagingParamsTest` → catalog-domain；`TypeReferenceTest` → ingest-domain；`StringUtilsTest` → catalog-infra。

**patra-tool-cqrs**（原 commons-core `cqrs` + starter-core `cqrs`）

| 类（新包 `dev.patra.tool.cqrs`） | 来源 |
|---|---|
| `Command`、`CommandBus`、`CommandHandler`、`CommandInterceptor`、`CommandHandlerNotFoundException` | commons-core `cqrs` |
| `CqrsErrorCode`（原 `CoreErrorCode`，唯一常量 `COMMAND_HANDLER_NOT_FOUND`，码 `CQRS-0500`） | commons-core `error.codes.CoreErrorCode` |
| `SimpleCommandBus` | starter-core `cqrs` |
| `interceptor.{LoggingCommandInterceptor, TracingCommandInterceptor, MetricsCommandInterceptor}` | starter-core `cqrs.interceptor`，去 `@Component`，`@Order` 改常量 |
| `autoconfigure.{CqrsAutoConfiguration, CqrsProperties}` | 合并 `CommandBusAutoConfiguration` + `CommandInterceptorAutoConfiguration` + `CommandBusProperties` |

app 层模块（catalog / identity / ingest / object-storage 的 `*-app`）改为显式 `api(project(":patra-tool:patra-tool-cqrs"))`；registry-app 不用命令总线，不加。

**patra-tool-web**（原 starter-web + starter-core 的错误解析引擎）

| 类（新包 `dev.patra.tool.web`） | 来源 |
|---|---|
| `error.adapter.*`、`error.builder.*`、`error.formatter.*`、`error.handler.*`、`error.model.*`、`error.spi.{ValidationErrorsFormatter, WebProblemFieldContributor}`、`error.util.*` | starter-web `error` |
| `error.engine.{ErrorResolutionEngine, DefaultErrorResolutionEngine}`、`error.model.{ErrorResolution, ResolutionStrategy, SimpleErrorCode}`、`error.pipeline.{ErrorResolutionPipeline, ResolutionInterceptor, ResolutionInvocation}`、`error.spi.{ErrorMappingContributor, ProblemFieldContributor, TraceProvider}`、`error.trace.HeaderBasedTraceProvider` | starter-core `error` |
| `json.LongToStringHttpMessageConvertersCustomizer` | starter-web `json` |
| `autoconfigure.{WebErrorAutoConfiguration, WebJsonAutoConfiguration, WebProperties}` | `WebErrorAutoConfiguration` 吸收 `CoreErrorAutoConfiguration` 的 4 个 `@Bean`；`WebProperties` 合并 `ErrorProperties` + `WebErrorProperties` + `TracingProperties` |

错误解析引擎放 web 而不是 core 的理由：core 要进 R3 白名单，必须零 Spring 依赖；引擎的使用方全是 servlet 服务（6 个 boot 都依赖 starter-web）；release spec 已把错误码前缀定为 `patra.web.error.context-prefix`，PAP-80 也把 observability 对它的依赖叫"web 错误拦截器"。实现 `ErrorMappingContributor` 的 jpa、security、observability 的 `ResolutionInterceptor` 因此改为对 web 的 `compileOnly` + `@ConditionalOnClass`（security 本来就硬依赖 web）。见 J1。

**patra-tool-jpa**（原 starter-jpa，类不增减）

`autoconfig` 包改名 `autoconfigure`；`JpaAuditingConfig` → `JpaAuditingConfiguration`、`HibernatePropertiesCustomizer` → `PatraHibernatePropertiesCustomizer`（和 Spring Boot 同名接口撞名，现在靠全限定名消歧，违反"禁止 FQN"规范，还要一条 spotbugs 排除；见 J12）；`JpaErrorMappingContributor` 去 `@Component` / `@Order`；`JpaAuditingConfiguration` 删 `Clock` bean（launch 提供，`dateTimeProvider` 改用 `ObjectProvider<Clock>`）。

**patra-tool-batch**（原 starter-batch，类不增减）

4 个自动配置类统一前缀：`BatchDataSourceConfiguration` → `BatchDataSourceAutoConfiguration`、`BatchSchemaInitializerConfiguration` → `BatchSchemaAutoConfiguration`、`BatchProgressMetricsAutoConfiguration` → `BatchMetricsAutoConfiguration`；`config.BatchProperties` → `autoconfigure.BatchProperties`。`build.gradle.kts` 删 `api(redisson)`、`api(starter-core)`。`BatchMetricsAutoConfiguration` 的 `afterName` 改指 `dev.patra.tool.observability.autoconfigure.ObservabilityMicrometerAutoConfiguration`。

**patra-tool-http-client**（原 rest-client + http-interface）

| 类（新包 `dev.patra.tool.httpclient`） | 来源 |
|---|---|
| `download.**`、`interceptor.LoggingInterceptor`、`proxy.TunnelProxyConfigurer` | rest-client |
| `error.ProblemDetailErrorHandler`、`factory.RestClientFactory`、`interceptor.InternalCallInterceptor` | http-interface |
| `autoconfigure.HttpClientAutoConfiguration`（`defaultRestClient`、`longRunningRestClient`、`loggingInterceptor`） | rest-client `RestClientAutoConfiguration` |
| `autoconfigure.HttpClientProxyAutoConfiguration` | `TunnelProxyAutoConfiguration` |
| `autoconfigure.HttpClientStreamingAutoConfiguration` | `StreamingWebClientAutoConfiguration` |
| `autoconfigure.HttpClientDownloadAutoConfiguration` | `DownloadClientAutoConfiguration` |
| `autoconfigure.HttpClientInterfaceAutoConfiguration` | http-interface `HttpInterfaceAutoConfiguration` |
| `autoconfigure.HttpClientProperties` | `RestClientProperties` + `DownloadProperties` + `HttpInterfaceProperties` 合一（第 5.2 节） |
| `autoconfigure.LongRunningClientEnabledCondition` | rest-client `config` |

两个模块之间没有相互引用，合并不改任何实现；bean 名保持（`defaultRestClient` 被 provenance 按参数名注入，`httpInterfaceLoadBalancedRestClientBuilder` / `httpInterfaceRestClientBuilder` 被 catalog-boot、ingest-boot、security IT `@Qualifier`）。依赖取并集：`httpclient5`、`micrometer-core` 在一边是 `compileOnly` 一边是 `api`，合并后都是 `api`（catalog-infra 为此单独加的 `httpclient5` 随之删掉）；rest-client 的 `compileOnly(spring-retry)` 主代码没用，删。

**patra-tool-object-storage**（原 starter-object-storage + commons-storage）

根包只留对外类型（`ObjectStorageOperations`、`ObjectStorageTemplate`、`StorageContext`、`StorageLocation`、`StorageLocationResolver`、`domain.*`、`metrics.*`）；`provider.{ObjectStorageProvider, AbstractObjectStorageProvider, MinioStorageProvider, S3StorageProvider, ProviderType}`；`key.{ObjectKeyGenerator, ObjectKeyContext, DatePartitionedKeyGenerator}`（来自 commons-storage，`ObjectKeyTemplate` 删）；`autoconfigure.{ObjectStorageAutoConfiguration, ObjectStorageS3AutoConfiguration, ObjectStorageProperties}`。

**patra-tool-observability**（原 starter-observability + starter-core 的 logging）

| 类 / 资源 | 来源 |
|---|---|
| `logging.{TraceIdConverter, SpanIdConverter}` | starter-core `logging` |
| `src/main/resources/dev/patra/tool/observability/logback-patra.xml` | starter-core jar 根的 `logback-spring.xml`，去掉写死的 `dev.linqibin` logger（级别改由 `logging.level.dev.patra` 属性给，第 5.3 节） |
| `autoconfigure.ObservabilityDefaultsPostProcessor` + `META-INF/spring.factories` | 新建 |
| `autoconfigure.{ObservabilityAutoConfiguration, ObservabilityMicrometerAutoConfiguration, ObservabilityErrorAutoConfiguration, ObservabilityProperties}` | `MicrometerAutoConfiguration`、`ObservationInterceptorsAutoConfiguration`、`config.ObservabilityProperties` 改名搬包 |
| `filter.*`、`interceptor.ObservationResolutionInterceptor` | 不动 |

`ObservabilityErrorAutoConfiguration` 的 `@ConditionalOnClass(name = "dev.patra.tool.web.error.pipeline.ResolutionInterceptor")` 必须在**类级**：方法级条件挡不住返回类型 `ObservationResolutionInterceptor`（实现了 web 的接口）被加载时抛 `NoClassDefFoundError`。`build.gradle.kts` 删 `api(commons-core)`、`api(starter-core)`、`api(caffeine)`、`api(jakarta.validation-api)`、`compileOnly(jakarta.servlet-api)`（主代码零 import），加 `compileOnly(project(web))`、`implementation(logback-classic)`。

**patra-tool-security**（原 P:security，减去搬去 core 的 5 个类型）

`config` 包 → `autoconfigure`，`SecurityCoreAutoConfiguration` → `SecurityAutoConfiguration`，`PatraSecurityProperties` → `SecurityProperties`；`SecurityExceptionRethrowAdvice` 保留 `@RestControllerAdvice`（见 J4），`@Order` 改常量。`build.gradle.kts`：`api(C:security)` 删（类型已在 core，经 web 传递），`generateIdentityAssertionKey` 任务的 `mainClass` 改新包名。

**patra-tool-openapi**：`config.OpenApiProperties` → `autoconfigure.OpenApiProperties`，其余不动。

**patra-tool-launch**（原 P:nacos）：3 个主类搬包；新建 `autoconfigure.{LaunchAutoConfiguration, LaunchProperties}`，`LaunchAutoConfiguration` 只有一个 `@Bean @ConditionalOnMissingBean Clock clock()`（`Clock.systemUTC()`，原 starter-core `CoreAutoConfiguration`）和 imports 文件。测试类（`NacosConfigFilesTest`、`JarConfigOverlapCheckerTest`、`NacosPushScriptIT` 等）名字不变，它们按 `patra.repo-root` 找仓库根，模块深度仍是 2 层，不用改。`build.gradle.kts` 里 `patra-infra/nacos/dev` 等路径不变。

**patra-tool-test**（原 starter-test）：`TestAutoConfiguration` 吸收 `TestMeterRegistryAutoConfiguration`（后者不在 imports 里，靠 `@Import`，违反 R4 的双向一致）；删 RocketMQ 三件套与 `docker-compose-rocketmq.yml`（第 3.6 节）。

### 3.3 patra-common 合一

`patra-api/patra-common/` 直接是一个模块（`:patra-api:patra-common`，包 `dev.patra.common`）：`CanonicalPublication`、`DataType`（+ `DataTypeTest`）、`PublicationIdentifierType`（去掉 `enums` 子包）、`ProvenanceCode`。`IngestDateType` 删（零引用），`RegistryConfigScope` → ingest-expr。依赖 `api(jackson-annotations)`、`api(jackson-databind)`。`DataType` 只有 ingest 系在用，按归位规则该进 ingest，但 release spec 决策 A 的原文把它列在 patra-common，本文照办。

`patra.hexagonal-domain` / `patra.hexagonal-api` 自动加的 `api(C:enums)` + `api(commons-core)` 改为 `api(patra-common)` + `api(patra-tool-core)`。

### 3.4 ingest 收回 provenance / expr

| 新模块 | 内容 | 删除 |
|---|---|---|
| `patra-ingest-provenance`（`dev.patra.ingest.provenance`） | P:provenance 全部（`autoconfig` + `boot` 两个包合为 `autoconfigure`）+ C:provenance-api 的 `api.{constants,params,values}` | `common.gateway.GatewayRequestBuilder`、`epmc.request.EpmcSearchRequestAssembler`、`api.endpoints.CrossrefEndpoints`、`api.values.epmc.ResultType`、`api.values.pubmed.{DateType, UseHistory}`（全部零引用，`DateType` 在 provenance 里的命中只是 Jackson 属性名字符串） |
| `patra-ingest-expr`（`dev.patra.ingest.expr`） | expr-kernel 全部（根包 `And`…`TextMatch`、`canonical`、`json`）+ P:expr 的 `compiler.**`（`boot` 包改 `autoconfigure`）+ `compiler.snapshot.RegistryConfigScope`（原 C:enums） | `compiler.model.OperationTypes`（只在 Javadoc 里被提到）、`application-expr-reference.yml`（全仓零引用） |

两个模块应用 `patra.spring-starter`，不应用 `patra.tool-module`（R1/R2/R4 只管 `patra-tool/`）。ingest-boot 收窄扫描到 `dev.patra.ingest` 后，provenance 的 `ProcessorRegistry`（`@Component`，没有 `@Bean`）仍被 ingest 自己扫到，行为不变。`ExprCompilerAutoConfiguration` 对 http-interface 自动配置的 `@AutoConfigureAfter` 改成 `@AutoConfiguration(afterName = "dev.patra.tool.httpclient.autoconfigure.HttpClientInterfaceAutoConfiguration")`，expr 对 http-client 的 `compileOnly` 随之删掉。

ingest 各层的依赖改动：`ingest-app` 与 `ingest-infra` 的 `api(P:expr)`、`api(expr-kernel)`、`api(P:provenance)` → `api(patra-ingest-expr)`、`api(patra-ingest-provenance)`（app 对两者实际零 import，见第 6.4 节死依赖 A7 等；app 用的是 `Expr`、`ExprCanonicalizer` 等 kernel 类，所以 `api(ingest-expr)` 保留，`api(ingest-provenance)` 删）。

commons-core 里只有 ingest 用的类进 ingest 对应层：

| 类 | 去向 |
|---|---|
| `Priority` | `dev.patra.ingest.domain.model.enums.Priority` |
| `ChannelKey` | `dev.patra.ingest.domain.messaging.ChannelKey` |
| `TypeReference`、`HashUtils` | `dev.patra.ingest.domain.support.{TypeReference, HashUtils}`（新包；`HashUtils` 被 ingest-domain、ingest-app、expr-kernel 用，kernel 进 ingest 后它就是 ingest 专属；ingest-expr 为此 `implementation(project(ingest-domain))`，见 J6） |
| `JsonNormalizer`、`JsonNormalizerConfig`、`JsonNormalizerResult`、`JsonNormalizationException`、`NormalizationPath`、`TemporalAccessorWrapper`、`TemporalCoercion` | `dev.patra.ingest.app.usecase.plan.json.*`（只有 `usecase.plan` 下的 4 个类在用） |

### 3.5 其他归位

| 类 | 现状 | 去向 |
|---|---|---|
| `PageResult`、`PagingParams` | 只有 catalog 全栈在用（domain 的两个 ReadPort 起） | `dev.patra.catalog.domain.query.{PageResult, PagingParams}`；portal 的 `src/types/portal.ts` 注释里的全限定名同步 |
| `StringUtils`（`trimToNull`、`escapeLike`） | 只有 catalog-infra 的 `PublicationRepositoryAdapter` 在用 | `dev.patra.catalog.infra.persistence.support.StringUtils` |
| `IdentityAssertionKeyGenerator` 等 security 类、`identity-session` | 不动 | 只改包名 |

### 3.6 删除清单

| 删什么 | 依据 |
|---|---|
| 模块 `L:redisson`（16 个类、`libs.versions.toml` 的 `redisson` / `redisson-spring-boot`） | 没有任何业务模块 import，`@DistributedLock` 只在它自己的 IT 里用；它进 catalog 全靠 batch 的空挂 `api` |
| starter-core 的 `async` 包（4 个类）、`AsyncAutoConfiguration` imports 行 | 零引用、全仓无 `async.*` 配置键 |
| starter-core 的 `CircuitBreakerErrorAutoConfiguration`、`CircuitBreakerInterceptor`、`ErrorProperties.CircuitBreakerProperties`、`compileOnly(resilience4j)`、`libs.versions.toml` 与依赖管理里的 resilience4j BOM | resilience4j 在所有服务里都不在 classpath，这套熔断从未生效，三份 `*-error-config.yaml` 里的 `circuit-breaker.enabled: true` 是假象 |
| `CoreAutoConfiguration`（Clock 搬去 launch）、`CommandInterceptorAutoConfiguration`（`@ComponentScan` 入口，由 cqrs 自动配置的 `@Bean` 取代） | 第 6.2 节 |
| 零引用类型：`SortDirection`、`ReadOnlyAggregate`、`ObjectKeyTemplate`、`IngestDateType`、`CrossrefEndpoints`、`ResultType`、`UseHistory`、pubmed `DateType`、`GatewayRequestBuilder`、`EpmcSearchRequestAssembler`、`OperationTypes`、`SoftDeletableChildJpaEntity`（含其测试）、`RocketMQContainerInitializer` + `RocketMQContainerSupport` + `RocketMQTopicAdmin` + starter-test 的 `docker-compose-rocketmq.yml` | G1–G4 二次确认全仓无代码引用；RocketMQ 三件套只有 `spotbugs-exclude.xml` 提到（ingest-boot 自己的 `docker-compose-rocketmq.yml` 不在本清单，它和 `ci.yml` 第 88 行的检查不动） |
| `application-expr-reference.yml` | 零引用 |
| `patra-common/README.md`、各模块旧 README | 重写（第 5.8 节） |
| `spotbugs-exclude.xml` 里指向已不存在类的条目：`dev.linqibin.patra.common.error.ApplicationException`、`dev.linqibin.patra.common.json.JsonNormalizer$Result`、`dev.linqibin.starter.core.json.ObjectMapperProvider`、`HibernatePropertiesCustomizer` 的 `NM_SAME_SIMPLE_NAME_AS_INTERFACE`、RocketMQ 两条 | 改这个文件时顺手清掉指向空的条目 |

不删：`ChildEntityChange`（生产在用）、`ExprVisitor`（kernel 内部访问者）、`ContainerRegistry` / `ContainerType`（初始化器内部用）、`ApiRequest`（5 个 request record 实现它）、`ProcessorRegistry`（ingest 自己的 `@Component`）。

### 3.7 settings.gradle.kts 与约定插件

`settings.gradle.kts` 的 `includeAt` 改为：

```
:patra-api:patra-common                       patra-api/patra-common
:patra-tool:patra-tool-core                   patra-tool/patra-tool-core
:patra-tool:patra-tool-cqrs                   patra-tool/patra-tool-cqrs
:patra-tool:patra-tool-web                    patra-tool/patra-tool-web
:patra-tool:patra-tool-jpa                    patra-tool/patra-tool-jpa
:patra-tool:patra-tool-batch                  patra-tool/patra-tool-batch
:patra-tool:patra-tool-http-client            patra-tool/patra-tool-http-client
:patra-tool:patra-tool-object-storage         patra-tool/patra-tool-object-storage
:patra-tool:patra-tool-observability          patra-tool/patra-tool-observability
:patra-tool:patra-tool-security               patra-tool/patra-tool-security
:patra-tool:patra-tool-openapi                patra-tool/patra-tool-openapi
:patra-tool:patra-tool-launch                 patra-tool/patra-tool-launch
:patra-tool:patra-tool-test                   patra-tool/patra-tool-test
:patra-api:patra-ingest:patra-ingest-provenance   patra-api/patra-ingest/patra-ingest-provenance
:patra-api:patra-ingest:patra-ingest-expr         patra-api/patra-ingest/patra-ingest-expr
（mapParent(":patra-tool", "patra-tool")；删 patra-common 四个子模块、linqibin-commons 13 个、patra-starters 4 个、patra-expr-kernel 的 include）
```

约定插件改名见第 4.1 节。

### 3.8 非 Java 引用清单（76 个文件按类别）

| 类别 | 文件 | 改什么 |
|---|---|---|
| 构建 | `settings.gradle.kts`、`build.gradle.kts`（根）、`gradle.properties`、`build-logic/src/main/kotlin/*`（18 个文件改名重写）、60 个模块 `build.gradle.kts` | 坐标、插件 id、group、`mainClass`、注释 |
| 资源 | 14 份 `AutoConfiguration.imports`、2 份 `spring.factories`、`logback-spring.xml`（移位改名）、2 份 `additional-spring-configuration-metadata.json`（observability 的 `type` 还写着 `com.patra.*`，重写）、`META-INF/spring/org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureWebMvc.imports`（新增） | 全限定名 |
| 配置 | 6 个 boot 的 `application.yml`、3 份 `*-error-config.yaml`（删）、`ingest-rocketmq.yaml`（不动）、catalog-boot `application-e2e-test.yml`、ingest-boot `application-test-common.yml` / `application-integration-test.yml` / `application-e2e-test.yml`、batch `application-test.yml`、web / security / nacos 的 IT `application.yml`、`patra-infra/nacos/dev/patra-common.yaml`、`patra-catalog.yaml` | 第 5.4、5.5 节逐键 |
| CI/CD | `.github/workflows/ci.yml`（第 76、81、87 行三条 starter-test 路径）、`cd.yml`（第 26–27 行触发路径、第 108 行 Gradle 坐标）、`patra-infra/cd/module-graph.json`（`dumpModuleGraph` 重生成）、`patra-infra/CLAUDE.md`（第 61 行"改公共面"路径列表、第 64 行插件名）、`patra-infra/docker/service.Dockerfile`（注释） | 路径 |
| IDE | `.run/Patra{Catalog,Gateway,Identity,Ingest,ObjectStorage,Registry}Application.run.xml` | `SPRING_BOOT_MAIN_CLASS` |
| 静态分析 | `spotbugs-exclude.xml`（约 40 处 `dev.linqibin` 包/类名） | 包名；删陈旧条目 |
| 文档与规则（需在主检出做，见第 6.5 节） | `.claude/rules/project-info.md`、`tech/commandbus.md`、`tech/error-handling.md`、`tech/observability.md`、`layers/infrastructure.md`、`testing/integration-test.md`；`.claude/skills/patra-backend/skills/patra-hexagonal/{SKILL.md, resources/patra-starters.md, resources/configuration.md}`、`patra-troubleshooter/{SKILL.md, references/log-config.md}`；`AGENTS.md`、`README.md`、`patra-api/README.md`、`patra-api/patra-common/README.md`、各模块 README | 模块名、包名、旧前缀、"仓库外下游"说法 |
| 前端 | `patra-portal/src/types/portal.ts`（注释里的 `PageResult` 全限定名） | 注释 |
| 不动 | `docs/patra/**`、`patra-learn/**`、`patra-infra/docker/*.yaml` 与 `.env*`（`linqibins-mac-mini` 是主机名）、`ghcr.io/linqibin0826/*`、`scripts/*.py`、`.claude/skills/*/plugin.json`（作者信息） | — |

### 3.9 PAP-79 验收

- `./gradlew compileJava compileTestJava compileIntegrationTestJava compileE2eTestJava` 与 `./gradlew check` 通过（PAP-79 阶段 `check` 还是旧规则，R1–R5 在 PAP-80 挂上）。
- `grep -r linqibin --include='*.java' --include='*.kts' --include='*.yml' --include='*.yaml' .` 只命中 `docs/patra/`。
- `ls patra-tool | wc -l` = 12；`linqibin-commons/`、`patra-starters/`、`patra-api/patra-expr-kernel/` 不存在；`patra-api/patra-common/` 直接含 `build.gradle.kts`。
- `./gradlew dumpModuleGraph` 后 `git diff --stat patra-infra/cd/module-graph.json` 非空且已提交。
- 6 个服务本机各启动一次并注册到 Nacos（`.run` 配置）。

## 4. PAP-80 依赖规则 R1–R5

### 4.1 约定插件重组

| 新插件 | 替代 | 内容 |
|---|---|---|
| `patra.java-base` | `linqibin.java-base` | 原内容 + `group = "dev.patra"` |
| `patra.java-library`、`patra.spring-library` | 同名 linqibin.* | 不变 |
| `patra.spring-starter` | `linqibin.spring-boot-starter` | 原内容 + `annotationProcessor(spring-boot-autoconfigure-processor)`（Boot 官方要求的自动配置元数据处理器，目前只有 object-storage 加了） |
| `patra.tool-module` | `linqibin.module-commons` + `linqibin.boundary-check` | 注册 `checkToolBoundary`（R1）、`checkToolDependencies`（R2）、`checkAutoConfiguration`（R4），三个都 `tasks.named("check") { dependsOn(...) }` |
| `patra.hexagonal-domain` | `linqibin.hexagonal-domain` | `checkDomainPurity`（R3，替代 `enforceDomainPurity` 黑名单）；依赖改 `api(patra-common)`、`api(patra-tool-core)` |
| `patra.hexagonal-{api,app,infra,adapter,boot}` | 同名 linqibin.* | api：`api(patra-common)`、`api(patra-tool-core)`；app / infra / adapter / boot：`testImplementation(patra-tool-test)`；boot：`implementation(patra-tool-launch)` |
| `patra.dependency-management`、`patra.spotless`、`patra.spotbugs` | 同名 linqibin.* | 不变；`PatraDependencyManagement.kt` 删 resilience4j BOM |
| 删除 | `linqibin.module-patra`（group 已全局）、`linqibin.boundary-check` 文件头的"Gradle 9.5 bug 不能挂 check"说明 | `hexagonal-domain` 同一套 build-logic 里一直在 `tasks.named("check")`，说明是错的 |

三个检查任务都写成 `abstract class … : DefaultTask()`，违规列表在配置阶段用 `provider {}` 算好作为 `@Input`，与现有 `DomainPurityCheck` 同一写法，兼容 configuration cache。失败统一抛 `GradleException`，信息第一行固定格式 `R<n> 违规：<模块路径>`，后面逐条列命中，最后一行写怎么改。

### 4.2 R1：工具不依赖业务

- **范围**：`patra.tool-module` 应用到的 12 个模块，所有配置（含测试配置）。
- **实现**：遍历 `configurations`，收集 `ProjectDependency.path`，命中 `startsWith(":patra-api")` 即违规（`patra-common` 在 `patra-api/` 下，也在禁止之列——security 原来依赖的 C:security 已并入 core）。
- **失败信息**：

```
R1 违规：:patra-tool:patra-tool-web
  implementation -> :patra-api:patra-catalog:patra-catalog-api
工具模块不得依赖 patra-api/* 下的任何模块；业务契约放回对应服务。
```

### 4.3 R2：工具之间只允许指向 core

- **范围**：同 R1。
- **规则**：
  - `api` / `implementation` / `runtimeOnly` 里的 `:patra-tool:*` 目标只允许 `:patra-tool:patra-tool-core`；白名单常量 `mapOf(":patra-tool:patra-tool-security" to setOf(":patra-tool:patra-tool-web"))` 写在插件里。
  - `compileOnly` 可以指向任何 `:patra-tool:*`（可选协作，自动配置必须配 `@ConditionalOnClass`）。
  - 测试配置（`testImplementation`、`testRuntimeOnly`、`integrationTest*`、`e2eTest*`、`testFixtures*`）允许：core、`patra-tool-test`、以及本模块主代码已 `compileOnly` 的目标（要测可选集成就得把它放上测试类路径）。security 的 `integrationTestImplementation(jpa)`、`(http-client)` 因此合法。
  - `testFixturesApi` 对 `:patra-tool:*` 的目标按主配置的规则判（它会传递给使用方）。
- **失败信息**：

```
R2 违规：:patra-tool:patra-tool-jpa
  api -> :patra-tool:patra-tool-web
工具间硬依赖只允许指向 :patra-tool:patra-tool-core（白名单：security -> web）。
要协作改成 compileOnly，并在自动配置类上加 @ConditionalOnClass。
```

- **现有横向边的处理**：

| 边 | 现状 | 改法 |
|---|---|---|
| web → core（错误引擎） | starter-web `api(starter-core)` | 引擎并入 web，边消失 |
| jpa → core(starter) | `api(starter-core)`（为 `ErrorMappingContributor`） | `compileOnly(web)`；`JpaErrorMappingContributor` 的 `@Bean` 放进嵌套 `@ConditionalOnClass(ErrorMappingContributor.class)` 配置 |
| observability → core(starter) | `api(starter-core)`（为 `ResolutionInterceptor`） | `compileOnly(web)`；类级 `@ConditionalOnClass(name=…)`（第 3.2 节） |
| batch → redisson、batch → core(starter)、rest-client → core(starter)、redisson → core(starter)、provenance → commons-core / rest-client、expr → core(starter) | 主代码零 import 的空挂 | 删 |
| security → web | `api` | 白名单 |
| security → jpa、security → http-interface | `compileOnly` | 不变（目标改名） |

### 4.4 R3：domain 编译类路径白名单

- **范围**：`patra.hexagonal-domain` 应用到的 5 个 `*-domain`。
- **实现**：解析 `compileClasspath` 的 `resolvedConfiguration.resolvedArtifacts`，每个构件取 `group:name`，不在下表即违规。不再检查 `runtimeClasspath`（domain 模块没有自己的运行时，运行时由 boot 决定）。

| 允许的 `group:name` | 为什么 |
|---|---|
| `dev.patra:patra-tool-core`、`dev.patra:patra-common` | 领域基类、错误类型、共享词汇 |
| `org.projectlombok:lombok` | 注解处理 |
| `com.fasterxml.jackson.core:jackson-annotations` | 枚举的 `@JsonValue` / `@JsonCreator` |
| `tools.jackson.core:jackson-core`、`tools.jackson.core:jackson-databind` | ingest-domain 的 `ExecutionContext`、`ExprCompilationResult` 直接用 `JsonNode`；`CanonicalPublication` 用 databind 的注解。release spec 写的是"Jackson annotations"，实际需要 databind（见 J3） |
| `cn.hutool:hutool-core` | 决策 D 保留 |
| `org.mapstruct:mapstruct` | 值对象映射 |

JDK 不是构件，天然允许。slf4j 不在名单里：5 个 domain 主代码没有 `org.slf4j` import，core 对它是 `compileOnly` 不传递。

- **失败信息**：

```
R3 违规：:patra-api:patra-catalog:patra-catalog-domain
  compileClasspath 含 org.springframework:spring-context:7.0.6
domain 编译类路径只允许：dev.patra:patra-tool-core, dev.patra:patra-common, org.projectlombok:lombok, …
```

### 4.5 R4：自动配置类检查

- **范围**：12 个工具模块的 `src/main/java`、`src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`、`src/test/java`。Gradle 任务扫源码文本，不用 ArchUnit：ArchUnit 要先编译再跑测试、每模块一份测试类，而且查不了"imports 文件 ⇔ 源码"和"测试文件存在"这类文件级规则；正则扫源码一处实现覆盖 12 个模块，`check` 里编译前就能失败。
- **能力名推导**：从项目名 `patra-tool-<x>` 得到包段 `x.replace("-", "")` 与类名前缀（每段首字母大写拼接，`http-client` → `HttpClient`）。
- **检查项**：

| # | 规则 | 判定方式 |
|---|---|---|
| a | 带 `@AutoConfiguration` 的类必须在 `dev.patra.tool.<x>.autoconfigure` 包、声明为 `public final class`、类名匹配 `^<Prefix>\w*AutoConfiguration$`、全限定名出现在 imports 文件、`src/test/java/<同包路径>/<类名>Test.java` 存在 | 文件级正则：`package` 行、`public final class` 行、注解行 |
| b | imports 文件每一行都对应一个存在且带 `@AutoConfiguration` 的源文件（与 a 合成双向一致） | 逐行查文件 |
| c | 主代码任何类型声明上不得有 `@Component`、`@Service`、`@Repository` | 行首 `@Component\b` 等正则（`@ControllerAdvice` / `@RestControllerAdvice` 不在禁止之列，见 J4） |
| d | `@Configuration` 只能出现在 `autoconfigure` 包下的文件 | 路径 + 正则 |
| e | 不得有裸数字 `@Order(…)`（`@Order(100)`、`@Order(-1)`） | 正则 `@Order\(\s*-?\d` |
| f | `autoconfigure` 包以外不得出现 `JsonMapper.builder()`、`XmlMapper.builder()`、`new ObjectMapper(`、`new JsonMapper(` | 正则 |
| g | `@ConfigurationProperties` 只能出现在 `autoconfigure` 包、所在类型必须是 `record`、前缀必须等于 `patra.<x>`（横线写法）或以它开头 | 正则取 `prefix = "…"` 或位置参数 |

没有自动配置的模块（core）a、b 空成立，c–g 照查。EPP 不走 imports（`spring.factories`），不在 a、b 范围。

- **失败信息**：

```
R4 违规：:patra-tool:patra-tool-jpa
  src/main/java/dev/patra/tool/jpa/error/JpaErrorMappingContributor.java:33 类上有 @Component（规则 c）
  src/main/java/dev/patra/tool/jpa/autoconfigure/JpaAutoConfiguration.java 缺少契约测试 src/test/java/dev/patra/tool/jpa/autoconfigure/JpaAutoConfigurationTest.java（规则 a）
工具模块的 bean 一律由 autoconfigure 包里的自动配置注册，每个自动配置类配同名 ApplicationContextRunner 测试。
```

### 4.6 R5：声明的依赖必须被使用

- **主方案**：根 `build.gradle.kts` 应用 `com.autonomousapps.dependency-analysis`（版本 `3.19.2`，2026-09-14 发布，3.x 起支持 Gradle 9，写进 `libs.versions.toml`），`tasks.check { dependsOn("buildHealth") }`。
- **探针**（plan 的独立任务，先于挂 `check`）：以 `severity("warn")` 跑一次 `./gradlew buildHealth`，把 advice 按类型计数（unused、used-transitive、ABI 升降级、runtimeOnly、annotation-processor），逐条判断是真问题还是噪音。预期要处理的两类：boot 模块对 starter 的 `implementation` 会被判"只用到运行时"而建议 `runtimeOnly`——这是对的，按建议改；`patra-tool-test` 这类纯传递型模块会被判"未使用"——用 `dependencyAnalysis { issues { all { onUnusedDependencies { exclude(":patra-tool:patra-tool-test") } } } }` 排除。
- **退回条件**：探针后仍有某类 advice 既不能按建议改（改了会让构建或运行错）、又不能用 DAGP 的 `exclude` / `severity` 按模块以 ≤ 10 条规则压掉，就退回自写检查：在 `patra.tool-module` 与 `patra.hexagonal-*` 里注册 `checkDeclaredDependencies`，对 `api` / `implementation` 的每条 project 依赖，要求本模块 `src/main/java` 至少有一个 `import` 落在目标模块 `src/main/java` 实际拥有的包里；目标含 `META-INF/spring/*.imports` 或 `spring.factories` 的视为"靠自动配置生效"放行。这正是盘点 G5 用的算法，已在 236 条依赖上跑过。
- 探针结果（条数、处理方式、最终采用主方案还是退回）回写到本文第 6.4 节。

### 4.7 `dumpModuleGraph` 与 CI

每次改 `build.gradle.kts` 后重跑 `./gradlew dumpModuleGraph` 并提交 `patra-infra/cd/module-graph.json`（CI preflight 用 `git diff` 守陈旧）。`unitOf` 里 `patra-tool/*` 与 `patra-api/patra-common`、`patra-ingest-{provenance,expr}` 的归类：前两者落 `foundation`（默认分支），后两者按路径前缀落 `ingest`——现有 `startsWith("patra-api/patra-ingest/")` 已覆盖。

### 4.8 PAP-80 验收（D3 的四条实测）

每条都做一次"加违规 → `./gradlew check` 失败并指出规则 → 撤销 → 通过"，结果（失败信息原文）记到本文第 4.9 节：

| 实测 | 操作 |
|---|---|
| R1 | `patra-tool-web/build.gradle.kts` 加 `implementation(project(":patra-api:patra-catalog:patra-catalog-api"))` |
| R2 | `patra-tool-jpa/build.gradle.kts` 把 `compileOnly(project(web))` 改成 `api(...)` |
| R3 | `patra-catalog-domain/build.gradle.kts` 加 `implementation("org.springframework:spring-context")` |
| R4 | `patra-tool-cqrs` 的 `LoggingCommandInterceptor` 加回 `@Component` |

另加一条 D3 要求的"删掉任一契约测试文件 `check` 失败"：删 `WebJsonAutoConfigurationTest.java` 跑一次。

### 4.9 实测记录

2026-10-15 在主检出实测，每条都是「改 → 对应的 check 子任务失败 → 贴首段失败信息 → `git checkout --` 撤销 → 同一任务通过」。跑的是 `check` 直接依赖的那个子任务（`checkToolBoundary` / `checkToolDependencies` / `checkDomainPurity` / `checkAutoConfiguration`），结论与跑整个 `check` 相同。

**R1 工具不依赖业务**（改动：`patra-tool-web/build.gradle.kts` 加 `implementation(project(":patra-api:patra-catalog:patra-catalog-api"))`）

`./gradlew :patra-tool:patra-tool-web:checkToolBoundary` 失败，首段信息原文：

```
Execution failed for task ':patra-tool:patra-tool-web:checkToolBoundary' (registered by plugin 'patra.tool-module').
> R1 违规：:patra-tool:patra-tool-web
    implementation -> :patra-api:patra-catalog:patra-catalog-api
  工具模块不得依赖 patra-api/* 下的任何模块；业务契约放回对应服务。
```

撤销后同一任务通过。

**R2 工具间只指向 core**（改动：`patra-tool-jpa/build.gradle.kts` 把 `compileOnly(project(":patra-tool:patra-tool-web"))` 改成 `api(...)`）

`./gradlew :patra-tool:patra-tool-jpa:checkToolDependencies` 失败，首段信息原文：

```
Execution failed for task ':patra-tool:patra-tool-jpa:checkToolDependencies' (registered by plugin 'patra.tool-module').
> R2 违规：:patra-tool:patra-tool-jpa
    api -> :patra-tool:patra-tool-web
    testImplementation -> :patra-tool:patra-tool-web
  工具间硬依赖只允许指向 :patra-tool:patra-tool-core（白名单：security -> web）。要协作改成 compileOnly，并在自动配置类上加 @ConditionalOnClass。
```

撤销后同一任务通过。

**R3 domain 编译类路径白名单**（改动：`patra-catalog-domain/build.gradle.kts` 加 `implementation("org.springframework:spring-context")`）

`./gradlew :patra-api:patra-catalog:patra-catalog-domain:checkDomainPurity` 失败，首段信息原文：

```
Execution failed for task ':patra-api:patra-catalog:patra-catalog-domain:checkDomainPurity' (registered by plugin 'patra.hexagonal-domain').
> R3 违规：:patra-api:patra-catalog:patra-catalog-domain
    compileClasspath 含 commons-logging:commons-logging
    compileClasspath 含 io.micrometer:micrometer-commons
    compileClasspath 含 io.micrometer:micrometer-observation
    compileClasspath 含 org.jspecify:jspecify
    compileClasspath 含 org.springframework:spring-aop
    compileClasspath 含 org.springframework:spring-beans
    compileClasspath 含 org.springframework:spring-context
    compileClasspath 含 org.springframework:spring-core
    compileClasspath 含 org.springframework:spring-expression
  domain 编译类路径只允许：dev.patra:patra-tool-core, dev.patra:patra-common, org.projectlombok:lombok, com.fasterxml.jackson.core:jackson-annotations, tools.jackson.core:jackson-core, tools.jackson.core:jackson-databind, cn.hutool:hutool-core, org.mapstruct:mapstruct
```

撤销后同一任务通过。

**R4 自动配置写法**（改动：`patra-tool-cqrs` 的 `LoggingCommandInterceptor` 加 `@Component`）

`./gradlew :patra-tool:patra-tool-cqrs:checkAutoConfiguration` 失败，首段信息原文：

```
Execution failed for task ':patra-tool:patra-tool-cqrs:checkAutoConfiguration' (registered by plugin 'patra.tool-module').
> R4 违规：:patra-tool:patra-tool-cqrs
    dev/patra/tool/cqrs/interceptor/LoggingCommandInterceptor.java:29 类上有 @Component（规则 c：工具模块不用构造型注解）
  工具模块的 bean 一律由 autoconfigure 包里的自动配置注册，每个自动配置类配同名 ApplicationContextRunner 测试。
```

撤销后同一任务通过。

**删掉契约测试**（改动：`git rm patra-tool/patra-tool-web/src/test/java/dev/patra/tool/web/autoconfigure/WebJsonAutoConfigurationTest.java`）

`./gradlew :patra-tool:patra-tool-web:checkAutoConfiguration` 失败，首段信息原文：

```
Execution failed for task ':patra-tool:patra-tool-web:checkAutoConfiguration' (registered by plugin 'patra.tool-module').
> R4 违规：:patra-tool:patra-tool-web
    dev/patra/tool/web/autoconfigure/WebJsonAutoConfiguration.java 缺少契约测试 src/test/java/dev/patra/tool/web/autoconfigure/WebJsonAutoConfigurationTest.java（规则 a）
  工具模块的 bean 一律由 autoconfigure 包里的自动配置注册，每个自动配置类配同名 ApplicationContextRunner 测试。
```

撤销后同一任务通过。

**R4 挂 `check` 前后的余量**：Task 16 实现规则时全仓 121 条违规（规则 a 75 / b 1 / c 3 / d 5 / e 4 / g 33，11 个模块各有、core 零）；PAP-81 逐模块重写后降到 1 条（`OpenApiAutoConfiguration` 与模块名推出的前缀 `Openapi` 只差大小写），规则 a 的前缀比较改为忽略大小写后为 0，随后 `checkAutoConfiguration` 挂上 `check`。

**R5**：主方案 DAGP 探针不通过退回条件（结果见第 6.4 节），按本节 4.6 的退回方案自写 `checkDeclaredDependencies`。挂上 `check` 前全仓扫描 8 条违规（5 个模块）：

```
> R5 违规：:patra-api:patra-registry:patra-registry-app
    api -> :patra-api:patra-registry:patra-registry-api（src/main/java 没有 import 它的任何包）
    api -> :patra-tool:patra-tool-core（src/main/java 没有 import 它的任何包）
  主代码没用到的 project 依赖请删掉；只在测试里用的改成测试配置；目标靠自动配置生效的改 runtimeOnly。
```

其余 6 条：catalog-adapter → catalog-api（只有测试在用，改 `testImplementation`）、catalog-app → patra-common、object-storage-app → patra-common / patra-tool-core、registry-infra → patra-tool-core（都删）。处理后全仓 `checkDeclaredDependencies` 与 `check` 通过；规则单测 `DeclaredDependencyRulesTest`（6 条）与 TestKit 用例 `check_fails_with_r5_when_declared_project_dependency_is_never_imported` 覆盖正反例。

## 5. PAP-81 starter 模板与默认配置

### 5.1 包结构模板

```
dev.patra.tool.<x>/                      公开类型（接口、模板、异常、record）
dev.patra.tool.<x>.autoconfigure/        <X>AutoConfiguration（public final）、<X>[<Aspect>]AutoConfiguration、<X>Properties（record）、EPP
dev.patra.tool.<x>.<实现包>/              实现类，包内可见优先；不被其他模块按名引用
```

规则：

1. 每个模块一个 `autoconfigure` 包、一份 imports 文件；自动配置类名 `<X>AutoConfiguration` 或 `<X><Aspect>AutoConfiguration`（batch 4 个、http-client 5 个、security 4 个、observability 3 个、web 2 个、object-storage 2 个，其余 1 个）。拆成多个是为了各自带不同的 `@ConditionalOnClass` / `after`，不是为了分文件。
2. 按名协作（`afterName`、`@ConditionalOnClass(name=)`）只许引用别的模块 `autoconfigure` 包里的类名。
3. 自动配置类里 `@Bean` 方法包私有，类 `public final`（Boot 自己的写法）。
4. 工具模块主代码不出现 `@Component` 系注解；`@ControllerAdvice` 系是 MVC 的功能标记，保留但只经 `@Bean` 注册（J4）。
5. 顺序值一律引用 `PatraOrders` 常量（第 5.7 节）。

### 5.2 配置类模板与前缀映射

模板：

```java
package dev.patra.tool.cqrs.autoconfigure;

/// 命令总线配置，前缀 `patra.cqrs`。
@Validated
@ConfigurationProperties(prefix = "patra.cqrs")
public record CqrsProperties(
    /// 总开关，关掉后本模块不注册任何 bean。
    @DefaultValue("true") boolean enabled,
    @DefaultValue Async async,
    @DefaultValue Interceptors interceptors) {

  /// 异步执行器。
  public record Async(
      @DefaultValue("4") @Min(1) int corePoolSize,
      @DefaultValue("16") @Min(1) int maxPoolSize,
      @DefaultValue("100") @Min(0) int queueCapacity,
      @DefaultValue("cmd-bus-") String threadNamePrefix) {}

  /// 三个内置拦截器的开关。
  public record Interceptors(
      @DefaultValue("true") boolean logging,
      @DefaultValue("true") boolean tracing,
      @DefaultValue("true") boolean metrics) {}
}
```

总开关统一是 `patra.<x>.enabled`，自动配置类上 `@ConditionalOnProperty(prefix = "patra.<x>", name = "enabled", havingValue = "true", matchIfMissing = true)`；launch 的 EPP 不受开关影响（它在容器之前运行，开关只管 `Clock` bean）。需要在线刷新的配置用可变类（目前工具模块没有）。

前缀映射（17 → 12 + ingest 2；"同"表示键名不变只换前缀）：

| 旧前缀 / 键 | 新前缀 / 键 | 备注 |
|---|---|---|
| `linqibin.starter.core.command-bus.async.*`、`.interceptors.{logging,tracing,metrics}` | `patra.cqrs.async.*`、`patra.cqrs.interceptors.*` | 同 |
| `linqibin.starter.core.error.{enabled, context-prefix, engine.*, observation.*}` | `patra.web.error.{enabled, context-prefix, engine.*, observation.*}` | `circuit-breaker.*` 删 |
| `linqibin.starter.core.tracing.header-names` | `patra.web.error.trace.header-names` | 默认 `["traceId","X-B3-TraceId","traceparent"]` |
| `linqibin.starter.web.problem.{enabled, type-base-url, include-stack}` | `patra.web.error.{enabled, type-base-url, include-stack}` | 与上一行合成一个 `error` 段；模块开关是 `patra.web.enabled` |
| `linqibin.starter.core.async.*` | 删 | 模块删除 |
| （jpa 无） | `patra.jpa.enabled` | 新增总开关 |
| `linqibin.starter.batch.{enabled, table-prefix, chunk.*, datasource.*, schema.initialize, import-limit.max-records}`、`batch.metrics.enabled`（原来只被条件读） | `patra.batch.*` 同名；`metrics.enabled` 进 record | `batch.observability.*` 是死键，删 |
| `linqibin.starter.rest-client.{enabled}` | `patra.http-client.enabled` | 模块总开关 |
| `linqibin.starter.rest-client.timeout.*`、`.interceptors.logging.*`、`.clients.<name>.*`、`.streaming.enabled` | `patra.http-client.rest.timeout.*`、`.rest.logging.*`、`.rest.clients.<name>.*`、`.rest.streaming.enabled` | `retry.*`、`interceptors.tracing.*`、`interceptors.metrics.*` 主代码不读，删 |
| `linqibin.starter.rest-client.proxy.tunnel.*` | `patra.http-client.proxy.tunnel.*` | 同 |
| `linqibin.starter.rest-client.download.*` | `patra.http-client.download.*` | 同 |
| `linqibin.starter.http-interface.{enabled, connect-timeout, read-timeout, connection-pool.*, error-handling.*, groups.*}` | `patra.http-client.http-interface.*` 同名（`interface` 是 Java 关键字，record 组件名用 `httpInterface`，键随之是 `http-interface`） | `retry.*` 主代码不读，删；`observation.*` 是死键，删 |
| `linqibin.starter.object-storage.*` | `patra.object-storage.*` | 同 |
| `linqibin.starter.observability.*` | `patra.observability.*` | 同 |
| `linqibin.starter.openapi.*` | `patra.openapi.*` | 同 |
| `linqibin.starter.redisson.*` | 删 | 模块删除 |
| `patra.security.*` | `patra.security.*` | 不变；加 `enabled` |
| （launch 无） | `patra.launch.enabled` | 新增 |
| （test 无） | `patra.test.enabled` | 新增 |
| `patra.starter.provenance.*` | `patra.ingest.provenance.*` | 同 |
| `patra.starter.expr.{mode,compiler}.*` | `patra.ingest.expr.{mode,compiler}.*` | 同 |

Java 里读旧前缀的地方：catalog-boot、ingest-boot 的 `HttpClientConfiguration` 上 `@ConditionalOnProperty(prefix = "linqibin.starter.http-interface", name = "enabled")` → `patra.http-client.http-interface`。

### 5.3 默认值归属与 logback

自己的键靠 record 的 `@DefaultValue`；别人的键由唯一归属模块的 `EnvironmentPostProcessor` 用 `DefaultPropertiesPropertySource.addOrMerge` 并入，并入前先剔除 `defaultProperties` 里已有的键（与 `NacosDefaultsPostProcessor.addDefaults` 同一写法，`addOrMerge` 会用新 map 覆盖旧 map 的同名键）。

| 归属模块 | 键 | 默认值 |
|---|---|---|
| observability（`ObservabilityDefaultsPostProcessor`，`Ordered.LOWEST_PRECEDENCE`，`META-INF/spring.factories`） | `logging.file.path` | `${PATRA_LOG_DIR:logs}` |
| | `logging.level.dev.patra` | `DEBUG` |
| | `logging.config` | `classpath:dev/patra/tool/observability/logback-patra.xml` |
| | `management.endpoints.web.exposure.include` | `health,info,metrics` |
| | `management.endpoint.health.show-details` | `when-authorized` |
| | `management.metrics.tags.application` | `${spring.application.name}` |
| launch（`NacosDefaultsPostProcessor`，不变） | `spring.cloud.nacos.{server-addr, username, password, discovery.service, discovery.ip, discovery.fail-fast}`、`spring.cloud.nacos.config.enabled`（无 `nacos:` 导入时） | 现值 |
| launch（`PatraApplication`，default properties，不变） | `spring.application.name`、`spring.config.import`、`spring.cloud.nacos.config.{namespace, preference}`、`patra.config-source.required` | 现值 |
| web | `spring.jackson.*` | 当前不出任何默认值，只登记归属 |

其他键没有归属模块，只能写在服务 `application.yml`（如 `server.forward-headers-strategy`，4 个服务各写一遍，本版不动，见 J17）。

EPP 时序：`EnvironmentPostProcessorApplicationListener`（`HIGHEST_PRECEDENCE + 10`）先于 `LoggingApplicationListener`（`HIGHEST_PRECEDENCE + 20`），所以 `logging.config`、`logging.file.path`、`logging.level.*` 在日志系统初始化前就在 Environment 里。服务 yml、Nacos、环境变量都高于 `defaultProperties`，覆盖只要写一个键。

`logback-patra.xml`：从 `logback-spring.xml` 搬来，`conversionRule` 的类名改 `dev.patra.tool.observability.logging.*`；删掉 `<logger name="dev.linqibin" level="DEBUG" additivity="false">`（级别改由 `logging.level.dev.patra` 属性给，root 已挂两个 appender，不再需要 additivity 技巧）；文件名不带 `-spring` 后缀也能用 `springProperty`——Boot 对 `logging.config` 指定的文件同样用 `SpringBootJoranConfigurator`。

### 5.4 删三份 `*-error-config.yaml`：逐服务 yml 改动

原则：值等于默认值的键不写；`error-catalog` 段原样搬进 `application.yml`；`spring.config.import` 里对应的 `classpath:` 行删掉（ingest 的 `classpath:ingest-rocketmq.yaml` 保留）。

| 服务 | 删 | 改 | 加 |
|---|---|---|---|
| catalog | `logging`、`management` 段；`spring.config.import` 的 `catalog-error-config.yaml`；`linqibin.starter.http-interface.{enabled, error-handling.*}`（都是默认值） | `linqibin.starter.rest-client.proxy.tunnel.{host,port}` → `patra.http-client.proxy.tunnel.{host,port}`；`linqibin.starter.object-storage.max-file-size` → `patra.object-storage.max-file-size`；`linqibin.starter.http-interface.{connect-timeout, read-timeout, groups.registry.*}` → `patra.http-client.http-interface.*` | `patra.web.error.context-prefix: CAT`；`catalog.error-catalog.*`（原样） |
| registry | 同上两段 + import 行；`web.problem.{enabled, include-stack}`、`http-interface.*`（默认值或死键） | — | `patra.web.error.context-prefix: REG`；`patra.web.error.type-base-url: https://errors.patra.com`（只有它改过）；`registry.error-catalog.*` |
| ingest | 同上两段 + `ingest-error-config.yaml` import 行；`http-interface.{enabled, error-handling.*}` | `http-interface.{connect-timeout, read-timeout, groups.{registry,storage}.*}` → `patra.http-client.http-interface.*`；`patra.starter.expr.*` → `patra.ingest.expr.*` | `patra.web.error.context-prefix: ING`；`ingest.error-catalog.*` |
| identity | `logging`、`management` 段 | `linqibin.starter.core.error.context-prefix: IDN` → `patra.web.error.context-prefix: IDN` | — |
| object-storage | `logging`、`management` 段（原来暴露 `health,info,prometheus`——没有 prometheus registry 依赖，该端点从来不存在，见 J9） | — | — |
| gateway | `logging.file`、`management` 段（`logging.level.org.springframework.cloud.*` 保留） | `linqibin.starter.core.error.context-prefix: GW` → `patra.web.error.context-prefix: GW` | — |

测试配置同步：catalog `application-e2e-test.yml`（`linqibin.starter.{batch.enabled, redisson.enabled, rest-client.proxy.tunnel.enabled}` 与 `spring.autoconfigure.exclude` 里的 Redisson、`dev.linqibin.starter.objectstorage.*`）、ingest `application-test-common.yml`（`object-storage.*`）、ingest 两份 e2e / integration 测试 yml 的 `spring.config.import`、batch `application-test.yml`（删 Redisson 排除与 `batch.observability.*`）、security IT `application.yml`（`linqibin.starter.core.error.context-prefix: TEST`）。

### 5.5 `patra-infra/nacos/dev/` 逐键

| 文件 | 旧键 | 新键 |
|---|---|---|
| `patra-common.yaml` | `linqibin.starter.object-storage.providers.minio.{endpoint, access-key, secret-key}` | `patra.object-storage.providers.minio.{endpoint, access-key, secret-key}` |
| `patra-common.yaml` | `patra.security.identity-assertion.public-keys` | 不变 |
| `patra-catalog.yaml` | `linqibin.starter.batch.datasource.{url, username, password}` | `patra.batch.datasource.{url, username, password}` |
| `patra-catalog.yaml` | `linqibin.starter.rest-client.proxy.tunnel.{enabled, auth-key, auth-pwd}` | `patra.http-client.proxy.tunnel.{enabled, auth-key, auth-pwd}` |
| `patra-catalog.yaml` | `spring.data.redis.password` | 删：catalog 的 Redis 全靠 batch → redisson 传递进来，模块删除后 catalog 不再有 spring-data-redis，这个键和它要求的 `REDIS_PASSWORD` 变量都成了死配置 |
| 其余 5 个文件 | 无旧前缀 | 不变 |

同一 PR 改，合入 main 后 CD 的 `nacos-push` job 先推再部署；`NacosConfigFilesTest` 与 `JarConfigOverlapCheckerTest` 在 CI 守住键不重复、敏感键只写 `${…}`。

### 5.6 契约测试模板与切片登记

每个自动配置类一份 `src/test/java/<同包>/<类名>Test.java`，用 `ApplicationContextRunner`，固定 4 个用例（按模块裁剪，没有可选依赖的模块省略第 4 条）：

```java
class CqrsAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(CqrsAutoConfiguration.class));

  @Test
  void should_register_command_bus_by_default() {
    runner.run(ctx -> assertThat(ctx).hasSingleBean(CommandBus.class));
  }

  @Test
  void should_register_nothing_when_disabled() {
    runner.withPropertyValues("patra.cqrs.enabled=false")
        .run(ctx -> assertThat(ctx).doesNotHaveBean(CommandBus.class));
  }

  @Test
  void should_back_off_when_user_defines_bean() {
    runner.withBean("custom", CommandBus.class, () -> mock(CommandBus.class))
        .run(ctx -> assertThat(ctx).getBean(CommandBus.class).isSameAs(ctx.getBean("custom")));
  }

  @Test
  void should_skip_tracing_interceptor_without_observation_on_classpath() {
    runner.withClassLoader(new FilteredClassLoader(ObservationRegistry.class))
        .run(ctx -> assertThat(ctx).doesNotHaveBean(TracingCommandInterceptor.class));
  }
}
```

R4 的规则 a 保证文件存在；用例内容靠评审。现有 5 个契约测试（http-interface、openapi、security 三个、web json）改名后保留并补齐 4 条。

web 新增 `META-INF/spring/org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureWebMvc.imports`，登记 `WebErrorAutoConfiguration`、`WebJsonAutoConfiguration`（Boot 自己的同名文件已含 `JacksonAutoConfiguration`，`JsonMapper` bean 在切片里有）。随后删掉 4 个 adapter 切片根（object-storage、identity、registry、catalog 的 `*ITWebMvcConfig`）、starter-web 的 `WebErrorITWebMvcConfig`、security 的 `SecurityWebMvcSliceIT.SliceConfig` 里手写的 `@ImportAutoConfiguration({CoreError…, WebError…, WebJson…})`；object-storage 切片原来漏了 `WebJsonAutoConfiguration`，登记后四个服务一致。`.claude/rules/testing/integration-test.md` 第 38–52 行"必须显式导入"的说明改成"由 web 模块登记进切片，不用导入"。

`@DataJpaTest` 切片不登记（PAP-82 决定保留显式 `@Import`），只改导入的包名和类名：`JpaAuditingConfiguration`、`PatraHibernatePropertiesCustomizer`、`JsonColumnCodec`。

### 5.7 `PatraOrders`

`dev.patra.tool.core.PatraOrders`（core 是纯 Java，用 `Integer.MIN_VALUE` / `MAX_VALUE` 表示 `Ordered.HIGHEST_PRECEDENCE` / `LOWEST_PRECEDENCE`）：

| 常量 | 值 | 用在 |
|---|---|---|
| `ERROR_ADVICE_SECURITY_RETHROW` | `Integer.MIN_VALUE` | `SecurityExceptionRethrowAdvice` |
| `ERROR_ADVICE_GLOBAL` | `Integer.MIN_VALUE + 1` | `GlobalRestExceptionHandler` |
| `ERROR_MAPPING_JPA` | `Integer.MIN_VALUE` | `JpaErrorMappingContributor` 的 `@Bean`（`@Order` 写在方法上，集合注入时生效） |
| `CQRS_INTERCEPTOR_TRACING` / `_LOGGING` / `_METRICS` | 50 / 100 / 200 | 三个命令拦截器 |
| `METER_FILTER_HIGH_CARDINALITY` | `Integer.MIN_VALUE` | `ObservabilityMicrometerAutoConfiguration.highCardinalityMeterFilter` |
| `METER_FILTER_COMMON_TAGS` | `Integer.MAX_VALUE` | 同上 `commonTagsMeterFilter` |
| `HTTP_CLIENT_LOGGING_INTERCEPTOR` | 100 | `HttpClientAutoConfiguration.loggingInterceptor` |
| `WEB_JSON_LONG_TO_STRING` | 100 | `LongToStringHttpMessageConvertersCustomizer`（删它自己的 `ORDER` 常量） |
| `DEFAULTS_POST_PROCESSOR` | `Integer.MAX_VALUE` | `ObservabilityDefaultsPostProcessor.getOrder()` |

launch 两个 EPP 的顺序是相对 Boot `ConfigDataEnvironmentPostProcessor.ORDER` 的，留在各自类里（R4 的规则 e 只管 `@Order(数字)` 注解）。服务自己的 `@Order`（registry / ingest 的 `ErrorMappingContributor`）不在工具模块，不受 R4 约束，本版不动。

### 5.8 README 模板

每个工具模块一页 `README.md`，固定四节：能力（两三句）；配置键表（键、类型、默认值、说明，和 record 一一对应）；怎么覆盖（服务 yml 示例一段）；出哪些默认值（EPP 归属表的本模块部分，没有就写"无"）。不列 API 清单（根 `.claude/rules/code-style.md` 的 README 规范）。`patra-api/patra-common/README.md` 重写成一页，`patra-api/README.md` 的 starter 表改指 `patra-tool/`。

### 5.9 PAP-81 验收（D4）

- 6 个 `application.yml` 无 `logging`、`management` 段；仓库无 `*-error-config.yaml`；`grep -r 'linqibin.starter\|patra.starter' --include='*.yml' --include='*.yaml' --include='*.java' .` 零命中。
- 本机各服务启动一次，对照改前：日志文件路径 `logs/<服务名>.log`、控制台 `dev.patra` DEBUG 可见、`/actuator` 列出 `health,info,metrics`、`/actuator/health` 的 `show-details` 行为、`/actuator/metrics/jvm.memory.used` 的 `application` 标签、任一 404 的 `ProblemDetail` 错误码前缀（`CAT-0404` 等）。
- 任一服务 `application.yml` 临时加 `logging.config: classpath:logback-test.xml` 启动，日志格式变化即覆盖生效。
- 12 个模块契约测试通过；`./gradlew check` 通过。

## 6. PAP-82 扫描收窄与收尾

### 6.1 启动类

6 个 `@SpringBootApplication(scanBasePackages = "dev.linqibin")` 改为裸 `@SpringBootApplication`。catalog 的 `exclude = DataRedisRepositoriesAutoConfiguration.class` 一并删掉：catalog 自己的 build 文件没有任何 Redis 依赖，spring-data-redis 是经 batch → redisson 传进来的，redisson 删除后这条 exclude 指向一个不在类路径上的类（见 J18；release spec 写"保留"是基于 redisson 还在）。扫描范围变为启动类所在包 `dev.patra.<service>`，ingest 因此扫到 `dev.patra.ingest.{provenance,expr}`。

### 6.2 9 个靠扫描的 bean

| # | bean | 现状 | 改法 |
|---|---|---|---|
| 1–3 | `Tracing` / `Logging` / `MetricsCommandInterceptor` | `@Component` + `@Order(数字)` + 类上 `@ConditionalOnBean`（扫描阶段评估，结果取决于顺序）；`CommandInterceptorAutoConfiguration` 再 `@ComponentScan` 一次 | `CqrsAutoConfiguration` 里三个嵌套 `static class`：`LoggingInterceptorConfiguration`（`@ConditionalOnProperty(patra.cqrs.interceptors.logging)`）、`TracingInterceptorConfiguration`（`@ConditionalOnClass(ObservationRegistry.class)` + `@ConditionalOnBean(ObservationRegistry.class)` + 属性）、`MetricsInterceptorConfiguration`（`@ConditionalOnClass(MeterRegistry.class)` + `@ConditionalOnBean(MeterRegistry.class)` + 属性），各自 `@Bean @Order(PatraOrders.CQRS_INTERCEPTOR_*)`；类级 `@AutoConfiguration(after = TaskExecutionAutoConfiguration.class, afterName = {"…ObservabilityAutoConfiguration", "org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration", "org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration"})`（`after TaskExecutionAutoConfiguration` 是因为 `commandBusAsyncExecutor` 的类型是 `Executor`，先注册会让 Boot 的 `applicationTaskExecutor` 因 `@ConditionalOnMissingBean(Executor.class)` 退让，见 J14） |
| 4 | `GlobalRestExceptionHandler` | `@RestControllerAdvice` + `WebErrorAutoConfiguration` 的 `@Bean`，双重注册，扫描路径绕过 `@ConditionalOnWebApplication` 与 `enabled` 开关 | 保留 `@RestControllerAdvice`（MVC 靠它识别 advice，Boot 自己的 `ProblemDetailsExceptionHandler` 就是这么做的）；`@Order` 改常量；收窄扫描后只剩 `@Bean` 一条路 |
| 5 | `SecurityExceptionRethrowAdvice` | 同上 | 同上 |
| 6 | `JpaErrorMappingContributor` | `@Component` + `JpaAutoConfiguration` 的 `@Bean` | 删 `@Component` / `@Order`；`@Bean @Order(PatraOrders.ERROR_MAPPING_JPA)` 放进嵌套 `@ConditionalOnClass(ErrorMappingContributor.class) static class JpaErrorMappingConfiguration` |
| 7 | `JpaAuditingConfig` | `@Configuration` 被 `@Import` 又被扫描 | 搬进 `autoconfigure`，改名 `JpaAuditingConfiguration`，删 `Clock` bean |
| 8 | `HibernatePropertiesCustomizer` | 同上 | 搬进 `autoconfigure`，改名 `PatraHibernatePropertiesCustomizer` |
| 9 | `CoreErrorAutoConfiguration` | 只有 `@Configuration` 没有 `@AutoConfiguration`，却登记在 imports | 并入 `WebErrorAutoConfiguration`（`@AutoConfiguration`） |

验证：任一服务 `--debug` 启动，`CONDITIONS EVALUATION REPORT` 的 Positive matches 里出现 `CqrsAutoConfiguration.LoggingInterceptorConfiguration`、`WebErrorAutoConfiguration#globalRestExceptionHandler`、`JpaAutoConfiguration.JpaErrorMappingConfiguration` 等；`/actuator/beans` 里这些 bean 的 `resource` 是自动配置类而不是 class 文件路径。

### 6.3 `SimpleCommandBus`

`registerHandlers` 里 `handlers.put` 返回非 null 时改为 `throw new IllegalStateException("命令 " + commandType.getName() + " 注册了两个 Handler：" + existing.getClass().getName() + " 与 " + handler.getClass().getName())`，`SimpleCommandBusTest` 加一条 `should_fail_when_two_handlers_claim_same_command`。先写测试再改（TDD）。

### 6.4 零引用类型、死依赖、重复声明

零引用类型：第 3.6 节清单。`OperationTypes`、pubmed `DateType` 核实为零使用（PAP-82 待核项），删。

死依赖（G5 复现 14 条显式 `api` 死依赖，不是此前记的 8 条；下表是处理结果）：

| # | 模块 → 目标 | 处理 |
|---|---|---|
| A1 | observability → commons-core | 删（模块重组后 observability 不依赖 core） |
| A2 | provenance → commons-core | 删（它用的 Jackson 经 patra-common 来） |
| A3 | expr-kernel → C:enums | 合并进 ingest-expr 后 `api(patra-common)` 真实在用（`ProvenanceCode`） |
| A4 | registry-app → registry-api | 删；它要的 `ProvenanceCode` 经 registry-domain 的 api 来 |
| A5 | registry-infra → commons-core | 删（JsonNode 经 patra-common 的 databind 来） |
| A6 / A9 / A12 | ingest-api / catalog-api / object-storage-api → C:enums | 显式声明与 `patra.hexagonal-api` 重复，删显式；插件那条对 object-storage-api 也是死的（B5），但插件按层统一加，接受 |
| A7 / A8 | ingest-app / ingest-adapter → ingest-api | 删 |
| A10 / A14 | catalog-app / object-storage-app → C:enums | 删（经 domain 的 api 来） |
| A11 | catalog-adapter → catalog-api | 改 `testImplementation`（只有测试在用） |
| A13 | object-storage-api → commons-core | 删显式 |
| B3 / B4 / B7 | object-storage-domain、identity-domain 的插件隐式依赖 | 接受（约定插件按层统一加，R5 探针若报噪音用 exclude 压） |
| C1 | catalog-api `testImplementation(starter-test)` | 删（模块无测试） |

28 处"显式声明与约定插件重复"的 `api(C:enums)` / `api(commons-core)` / `testImplementation(starter-test)` 一并删掉。6 处 `api(starter-web)` / `api(starter-http-interface)` / `implementation(starter-object-storage)` 只有测试在用（G5 1.2(b)），由 R5 探针决定降级为测试配置还是保留（它们提供自动配置，运行时确实需要——按 DAGP 建议改 `runtimeOnly` + `testImplementation`）。

R5 探针结果（Task 28）：DAGP 3.19.2 以 `severity("warn")` 跑 `buildHealth`，覆盖 40 个 library 模块（boot 模块不参与：它们关了 plain jar，DAGP 分析测试类路径要 plain jar，而 `service.Dockerfile` 的 `COPY build/libs/*.jar` 只容一个 jar），advice 计数：undeclared（用了传递依赖建议显式声明）2207、unused 575（含注解处理器 278；其中 junit-jupiter 121、mockito 45、patra-tool-test 20、spring-boot-starter-test 14、mapstruct 13，其余是各 starter 空聚合包）、misDeclared（api / implementation 升降级）36、建议 runtimeOnly 10。starter 与 `patra-tool-test` 这类空聚合模块必被判 unused，undeclared 两千余条也不是本版要改的事，压到可接受的噪音远超 10 条规则——触发第 4.6 节的退回条件，R5 改为自写 `checkDeclaredDependencies`（`build-logic/src/main/kotlin/DeclaredDependencyRules.kt` + `DeclaredDependencyCheck.kt`，注册在 `patra.tool-module` 与 `patra.hexagonal-{domain,api,app,infra,adapter}`，boot 同样不参与）。约定插件按层统一给的 `patra-common` / `patra-tool-core`（domain、api 层）作为豁免集传入，不要求模块逐个 import。上表 A4 / A5 / A11 / A14 由该检查复现并处理（A5 对应的目标现为 patra-tool-core）；上段"6 处只有测试在用的 starter 依赖"在 PAP-81 模块重组后已不复存在（相关 starter 已合并或删除），无需降级。探针原始报告留在实施工作区，不入库。

### 6.5 CI/CD、文档、规则、skill

| 文件 | 改什么 |
|---|---|
| `.github/workflows/ci.yml` 第 76、81、87 行 | `linqibin-commons/linqibin-spring-boot-starter-test/...` → `patra-tool/patra-tool-test/src/main/java/dev/patra/tool/test/container/initializer/...`；第 87 行 starter-test 的 `docker-compose-rocketmq.yml` 随 RocketMQ 支持删除，只留 ingest-boot 那条 |
| `.github/workflows/cd.yml` | 第 26–27 行 `linqibin-commons/**`、`patra-starters/**` → `patra-tool/**`；第 108 行 `:patra-starters:patra-spring-boot-starter-nacos:test` → `:patra-tool:patra-tool-launch:test` |
| `patra-infra/cd/module-graph.json` | `./gradlew dumpModuleGraph` 重生成 |
| `patra-infra/CLAUDE.md` 第 61、64 行 | 公共面路径列表改 `patra-tool/*`、`patra-api/patra-common`；`linqibin.hexagonal-boot` → `patra.hexagonal-boot` |
| `.claude/rules/project-info.md` | 模块表改为 `patra-tool/` 12 个 + `patra-common`；删"有仓库外下游，改动前见根 CLAUDE.md「下游消费者」"（已过时）；约定插件名 |
| `.claude/rules/tech/commandbus.md`、`tech/error-handling.md`、`tech/observability.md`、`layers/infrastructure.md`、`testing/integration-test.md` | 模块名；错误引擎位置（web）；切片不再手写 `@ImportAutoConfiguration` |
| `.claude/skills/patra-backend/skills/patra-hexagonal/{SKILL.md, resources/patra-starters.md, resources/configuration.md}` | starter 选择树改成 `patra-tool-*`，删 redisson 行，加 cqrs；`starter-nacos` → `patra-tool-launch` |
| `.claude/skills/patra-backend/skills/patra-troubleshooter/{SKILL.md, references/log-config.md}` | `dev.linqibin` → `dev.patra`；logback 路径 |
| `AGENTS.md` 第 87 行、根 `README.md` 布局表 / 坐标表 / 边界约束段、`patra-api/README.md` 第 73–77 行 | 目录名、group、规则名（`checkToolBoundary` 等） |
| `.run/*.xml` | 主类名 |

`.claude/` 下的文件在 worktree 会话里改不了（PreToolUse 钩子拒绝），plan 里把这组改动做成一个独立任务：在主检出会话里改，或由本会话产出 `git format-patch` 交给用户。

### 6.6 PAP-82 验收（D5–D7）

- 6 个启动类 `grep scanBasePackages` 零命中；`./gradlew check` 的 R4 通过（工具模块无 `@Component` 系注解）。
- 任一服务 `--debug` 启动，自动配置报告显示第 6.2 节的 bean 来自自动配置；6 个服务本机启动成功。
- `SimpleCommandBusTest` 新用例通过。
- `./gradlew check`、`./gradlew integrationTest`、`./gradlew :patra-api:patra-catalog:patra-catalog-boot:e2eTest` 本地通过；CI 全绿；`detect-changes.sh` 对一次只改 `patra-tool/patra-tool-jpa` 的提交路由到 `foundation` + 5 个用 jpa 的服务（看 CI 的 `detect-changes` 输出）。
- 合并后 mini：CD 推送 Nacos（`nacos-push` job 日志有两处 diff：`patra-common.yaml`、`patra-catalog.yaml`），6 个容器健康；`PATRA_GATEWAY_BASE_URL=http://100.103.73.27:9528 pnpm test:e2e`（portal）全部通过。

## 7. 测试策略

| 类型 | 做法 |
|---|---|
| 契约测试（新） | 12 个模块、每个自动配置类一份，模板见第 5.6 节；R4 守文件存在 |
| 单元测试（搬迁） | 随类走（第 3.2 节表），只改包名 |
| 单元测试（新行为，TDD） | `SimpleCommandBus` 重复 handler；`ObservabilityDefaultsPostProcessor`（不覆盖已有键、键值表）；`LaunchAutoConfiguration` 的 Clock；`CqrsErrorCode`；build-logic 的四个检查任务用 Gradle TestKit 各写一个最小项目跑一遍正反例（`build-logic/src/test/kotlin`，这是 build-logic 第一批测试） |
| 集成测试（改导入） | 4 个 adapter 切片根 + web IT + security SliceIT 删 `@ImportAutoConfiguration`；9 个 `@DataJpaTest` IT 改导入的包名；`RestClientFactoryIT` 的 `excludeName` 全限定名改为类引用；batch 两个 IT 删 Redisson 排除与死键；catalog e2e yml 改排除项的全限定名；security IT yml 改前缀 |
| ArchUnit | `CatalogArchitectureTest`、`IngestArchitectureTest` 的 `allowedCommonPackages` 改 `List.of("dev.patra.common..", "dev.patra.tool..")`，`basePackage` 改 `dev.patra.catalog` / `dev.patra.ingest` |
| 启动验证 | 6 个服务本机启动（PAP-79 后一次、PAP-81 后对照一次、PAP-82 后 `--debug` 一次） |
| 全栈门控 | 每个任务结束 `./gradlew check`（含 SpotBugs、spotless、R1–R5）；PR 前 `./gradlew check integrationTest` |

## 8. 验证步骤（对应 Done 判定）

| Done | 命令 / 动作 |
|---|---|
| D1 | 4 个 Issue 由 PR 的 `Closes` 关闭 |
| D2 | 第 3.9 节的 `grep` 与 `ls`；`find patra-tool -name '*.imports' \| wc -l` = 11（core 没有）、`find patra-tool -type d -name autoconfigure \| wc -l` = 11 |
| D3 | 第 4.8 节五条实测，结果记入第 4.9 节 |
| D4 | 第 5.9 节 |
| D5 | 第 6.6 节前两条 |
| D6 | `./gradlew check integrationTest :patra-api:patra-catalog:patra-catalog-boot:e2eTest`；CI 绿；`module-graph.json` 已更新 |
| D7 | 第 6.6 节最后一条 |

## 9. 需要用户确认的判断项

写 spec 时发现 release spec / Linear 的个别表述与代码事实冲突，或留了没写明的选择。以下是我的取舍，请逐条确认或改：

| # | 事项 | 本文的取舍 | 依据 |
|---|---|---|---|
| J1 | 错误解析引擎放 web 还是 core | **web**；core 零 Spring 依赖 | core 要进 R3 白名单；全部使用方是 servlet 服务；release spec 的键名 `patra.web.error.context-prefix` 与 PAP-80 的"web 错误拦截器"已经这么假定。副作用：D2"每个模块只有一个 autoconfigure 包"、D3"每个工具模块都有契约测试"对 core 读作"没有 / 不适用" |
| J2 | C:security 的 5 个类型去 core 还是 security | **core**（`dev.patra.tool.core.security`） | release spec 正文写"common-security 进 core"，Linear PAP-79 写"security（+ common-security）"；identity-domain 直接 import `AccountType` / `ClientType`，放 security 会让 domain 编译类路径带上 Spring Security，违反 R3 |
| J3 | R3 白名单加 Jackson 3 `jackson-core` / `jackson-databind` | **加** | ingest-domain 两处用 `JsonNode`；`CanonicalPublication` 用 databind 注解。release spec 只写了 annotations |
| J4 | R4 禁 `@ControllerAdvice` / `@RestControllerAdvice` | **不禁**，只禁 `@Component` / `@Service` / `@Repository`（`@Configuration` 限 autoconfigure 包） | Spring MVC 靠类上的 `@ControllerAdvice` 识别全局异常处理器（`ControllerAdviceBean.findAnnotatedBeans`），删了注解 `@Bean` 注册也不生效；Boot 的 `ProblemDetailsExceptionHandler` 就是"保留注解 + `@Bean`"。扫描收窄后不再双重注册 |
| J5 | `ChildEntityChange` 删不删 | **不删** | catalog-domain `OrganizationRepository`、catalog-infra `VenueRepositoryAdapter` / `AuthorJpaMapper` 的生产代码经 `AggregateRoot.registerChild*` / `pullChildChanges` 在用；PAP-82 清单这条有误 |
| J6 | 只有一个服务用的 commons-core 类去哪 | `PageResult` / `PagingParams` → catalog-domain；`StringUtils` → catalog-infra；`HashUtils` → ingest-domain，ingest-expr 为它 `implementation(ingest-domain)` | 决策 A"只有一个服务用的留在该服务"。`HashUtils` 的替代方案是留在 core（40 行 SHA-256 工具），但它只有 ingest 系在用 |
| J7 | 死依赖条数 | 按 G5 的 14 + 7 + 1 条处理（第 6.4 节），不限于"8 条" | 此前的 8 条名单已丢失，无法逐条对照 |
| J8 | RocketMQ 测试支持 | `RocketMQContainerInitializer` 连同 `RocketMQContainerSupport`、`RocketMQTopicAdmin`、starter-test 的 compose 文件、`ci.yml` 对应检查一起删 | 三件套互相引用、全仓无其他使用方；PAP-82 只点了第一个 |
| J9 | object-storage 的 `management.endpoints.web.exposure.include: health,info,prometheus` | 统一成默认 `health,info,metrics` | 没有 prometheus registry 依赖，该端点一直 404；D4 对照时核实 |
| J10 | `Clock` 统一为 UTC | 删 jpa 的 `systemDefaultZone()` Clock，只留 launch 的 `systemUTC()` | 现状带 jpa 的 5 个服务实际拿到的是本机时区（扫描先注册），只有 gateway 是 UTC；审计时间走 `Instant`，不受时区影响；identity 的会话到期也用 `Instant` |
| J11 | 删除主代码不读的配置字段 | 删：rest-client `retry.*`、`interceptors.tracing.*`、`interceptors.metrics.*`；http-interface `retry.*`；yaml 死键 `http-interface.observation.*`、`batch.observability.*`、`redisson.observability.*` | PAP-81"死键一并删"；record 重写时不保留没人读的字段 |
| J12 | 两个改名 | `CoreErrorCode` → `CqrsErrorCode`（`CQRS-0500`）；`HibernatePropertiesCustomizer` → `PatraHibernatePropertiesCustomizer` | 前者随 cqrs 搬家，名字跟能力；后者与 Boot 接口同名，现在靠全限定名消歧并需要一条 spotbugs 排除 |
| J13 | R2 对测试配置的解释 | 测试配置可依赖 core、test、以及主代码已 `compileOnly` 的工具模块 | 不这样写 security 的集成测试没法测审计人与转发 |
| J14 | `CqrsAutoConfiguration` 声明 `after = TaskExecutionAutoConfiguration` | 加 | `commandBusAsyncExecutor` 是 `Executor` 类型，先注册会顶掉 Boot 的 `applicationTaskExecutor`；现状没有顺序声明，是否已经发生取决于类名排序 |
| J15 | 一个模块多个自动配置类 | 允许，命名 `<X><Aspect>AutoConfiguration`，各带契约测试 | batch / http-client / security 的条件与顺序各不相同，硬合成一个要重写它们 |
| J16 | `.claude/` 改动 | 独立任务，主检出会话做或交补丁 | worktree 会话被钩子拒绝 |
| J17 | `server.forward-headers-strategy: framework`（4 个服务） | 本版不动 | 决策 D 的归属表没有它 |
| J18 | catalog 的 `exclude = DataRedisRepositoriesAutoConfiguration` 与 Nacos `patra-catalog.yaml` 的 `spring.data.redis.password` | 都删 | catalog 没有自己的 Redis 依赖，spring-data-redis 只经 batch → redisson 传入；release spec 写"保留 exclude"是基于 redisson 还在。catalog e2e yml 里的 Redisson / `DataRedisAutoConfiguration` 排除同理删 |
| J19 | `DataType` 留 patra-common | 照决策 A 原文 | 它只有 ingest 系在用，按归位规则该进 ingest；两种都行，照原文 |
| J20 | build-logic 的四个检查任务要不要写 TestKit 测试 | 写（`build-logic/src/test/kotlin`，每条规则一个最小项目的正反例） | R1–R5 是新行为，CLAUDE.md 的 TDD 对新行为强制；build-logic 目前没有任何测试，这是第一批，要引入 Gradle TestKit 依赖 |

## 10. 对 release spec 与 Linear 的修正建议

spec 获批后由我在同一 PR 里改 release spec、用 Linear MCP 改 Issue 描述：

- release spec 决策 C 的 R3 白名单补 `jackson-core` / `jackson-databind`；R4 的禁用注解列表去掉 `@ControllerAdvice` / `@RestControllerAdvice`；D2、D3 对 core 加"纯 Java 模块除外"。
- PAP-79：模块清单改"common-security 进 core"；补"错误解析引擎进 web"。
- PAP-80：R4 禁用列表同上。
- PAP-82：零引用清单去掉 `ChildEntityChange`，补 `SoftDeletableChildJpaEntity`、RocketMQ 三件套；死依赖条数改按本文第 6.4 节；catalog `exclude` 改为"核实后删"。
