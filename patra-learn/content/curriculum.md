# 学习站课程大纲（PAP-153）

> 8 条路线的全部篇目、难度、类型、前置与写作分工。路线与主题已转成 `content/routes/*.yaml`、`content/topics/*.yaml`（PAP-155 格式），此后路线顺序以 YAML 为准；本文件是写作索引，内容加载器不读它。
>
> **代码路径核实基线**：下文「例子」列里的仓库路径全部在 `cc2956951` 上核实存在。写作会话按决策 F 钉 SHA 时，写作当天再核一次，文件改过就以当时的提交为准。
>
> **公开边界**：本文件进公开仓库。例子里「mini」指作者家里常驻的 Mac mini 部署机，写进条目时按维护技能解释这个说法；不写地址、主机名与凭据。

## 0. 约定

| 项 | 规则 |
|---|---|
| 难度 | 入门 < 基础 < 进阶 < 实战。实战只给 Patra 里真实发生过的事（真实的排查、坑、决策）；用 Patra 代码举例的概念篇仍按概念本身的难度标 |
| 类型 | 概念 / 排查（现象 → 怎么查 → 根因 → 修法）/ 决策（背景、选项、选择、代价）/ 坑（一坑一页） |
| 前置 | 只写「不读就看不懂」的直接前置，不写传递前置；前置可以跨路线 |
| 主路线 | 一篇可以挂多条路线（见第 2 节），总表按它的**主路线**分组，主路线决定默认归属 |
| 挂多条路线 | 只有篇目本身就是那条路线该讲的内容才挂过去（如 JPA 也是数据库路线的内容）；不为凑前置把别的路线的篇目挂进来。跨路线前置由条目页的「先读这几篇」引导 |
| 例子 | 仓库路径写到能定位的文件；「v0.x 决策 Y」指 `docs/patra/release-specs/` 里对应 release spec 第 4 节；「排查记录」指作者以前写给 Agent 的排查笔记，转写时去掉本机细节 |

### 0.1 归属规则（2026-10-31 用户确认）

1. **概念篇**一律归主路线的写作 Issue：Web 与 HTTP、数据库与事务、Java 与 Spring → PAP-158；消息与事件、DDD 与六边形 → PAP-159；测试、部署与 CI/CD、前端 → PAP-160。概念篇即使用了 release spec 里的决策当例子，也由路线 Issue 写。
2. **旧 CI/CD 课程改写的篇目**（含旧事故档案里的 6 个坑）一律归 PAP-160，不论类型。
3. **排查 / 坑 / 决策篇**，主要素材是已有材料（版本总结 Artifact、学习笔记、release spec 决策、排查记录）的，归 PAP-161。
4. 每篇只归一个 Issue；挂在多条路线上的篇目只由归属 Issue 写一次。

## 1. 篇目总表

### 1.1 Web 与 HTTP（主路线 `web-http`）

| slug | 标题 | 类型 | 难度 | 前置 | 讲什么 | 例子 | 归属 |
|---|---|---|---|---|---|---|---|
| web-what-happens-opening-a-page | 打开一个网页时发生了什么 | 概念 | 入门 | — | 从敲下网址到画出页面：查域名、建连接、发请求、收响应、渲染，整条链路先走一遍 | 打开 Patra 门户首页经过的 Cloudflare → 门户 → 网关 → catalog（`patra-infra/docker/README.md`「门户的公网入口」一节） | PAP-158 |
| web-client-server-and-ports | 客户端、服务器与端口 | 概念 | 入门 | web-what-happens-opening-a-page | 服务器就是一直在等请求的程序；IP 找到机器，端口找到机器上的哪个程序 | 每个服务占一个端口：`patra-infra/cd/services.json` | PAP-158 |
| web-url-anatomy | 一个网址由哪几部分组成 | 概念 | 入门 | web-what-happens-opening-a-page | 协议、域名、路径、查询参数、片段各管什么 | 门户用户主页的路径参数：`patra-portal/src/app/u/[username]/page.tsx` | PAP-158 |
| http-request-and-response | 请求与响应：方法、头与正文 | 概念 | 基础 | web-client-server-and-ports、web-url-anatomy | 一次 HTTP 交换长什么样；GET / POST / PUT / PATCH / DELETE 的语义 | 用 curl 看门户健康检查的完整报文：`patra-portal/src/app/api/health/route.ts` | PAP-158 |
| http-status-codes | 状态码：从 2xx 到 5xx | 概念 | 基础 | http-request-and-response | 五组状态码各说明什么；哪些是调用方的错（4xx），哪些是服务端的错（5xx） | 领域异常自动映射成 404 / 409 / 422：`patra-tool/patra-tool-web/.../DefaultErrorResolutionEngine.java` | PAP-158 |
| http-json-and-content-type | JSON 与 Content-Type | 概念 | 基础 | http-request-and-response | 接口用 JSON 传数据，头部告诉对方怎么解析；统一的错误格式 ProblemDetail | `application/problem+json` 错误响应（patra-tool-web）；错误码 `CAT-0404` 的格式（`.claude/rules/tech/error-handling.md`） | PAP-158 |
| http-rest-api-design | REST 接口怎么设计 | 概念 | 基础 | http-status-codes、http-json-and-content-type | 资源、路径、方法、幂等；什么时候用 PUT，什么时候用 PATCH | 收藏夹接口：`patra-interaction-adapter/.../collection/rest/MyCollectionController.java` | PAP-158 |
| http-cookie-and-session | Cookie 与会话 | 概念 | 基础 | http-request-and-response | HTTP 本身不记人，Cookie 和服务端会话怎么让它「记住你」；HttpOnly / Secure / SameSite | `patra-portal/src/lib/auth/session-cookie.ts`；会话存在 Redis：`patra-identity-infra/src/main/resources/redis/session-create.lua` | PAP-158 |
| http-https-and-certificates | HTTPS 与证书 | 概念 | 基础 | web-what-happens-opening-a-page、http-request-and-response | 加密、证书、信任链；一张证书覆盖哪些域名 | v0.17 决策 A：免费证书只覆盖一级子域，所以学习站用 `learn.linqibin.dev` | PAP-158 |
| http-same-origin-and-cors | 同源策略与跨域 | 概念 | 基础 | http-cookie-and-session | 浏览器为什么拦跨域请求；CORS 怎么放行；为什么门户选择同源转发 | `patra-portal/src/lib/same-origin.ts`；v0.11 决策 F「PDF 与图片经门户同源接口转发」 | PAP-158 |
| http-caching | HTTP 缓存：Cache-Control 与 ETag | 概念 | 基础 | http-request-and-response | 浏览器和代理什么时候可以不重新请求；私有数据为什么不能被缓存 | 公开用户信息接口设置缓存头：`patra-identity-adapter/.../account/rest/PublicUserController.java` | PAP-158 |
| web-reverse-proxy-and-gateway | 反向代理与 API 网关 | 概念 | 进阶 | http-rest-api-design、http-cookie-and-session | 对外一个入口，按路径转给后面的多个服务；认证、限流、超时都放在门口 | `patra-api/patra-gateway-boot/src/main/resources/application.yml` 的路由与 `config/GatewayConfiguration.java` | PAP-158 |
| web-rate-limiting | 限流：挡住刷接口的人 | 概念 | 进阶 | http-status-codes、db-redis-basics | 固定窗口与滑动窗口；429 与 Retry-After；为什么计数要原子 | 登录限次：`redis/login-throttle-begin.lua`、`LoginThrottleAdapter.java`；改邮箱返回 Retry-After：`MeEmailController.java` | PAP-158 |
| web-timeouts-and-downstream-failures | 下游慢了怎么办：超时与失败分类 | 概念 | 进阶 | web-reverse-proxy-and-gateway | 连接超时与读超时；502 / 503 / 504 分别是谁的问题 | `patra-gateway-boot/.../error/ProxyFailureClassifier.java`、`DownstreamTimeoutException.java`、`GatewayStalledDownstreamIT.java` | PAP-158 |
| web-server-push-and-sse | 服务器主动推：轮询、SSE 与 WebSocket | 概念 | 进阶 | http-request-and-response、web-reverse-proxy-and-gateway | 三种「让页面知道有新东西」的办法；SSE 的报文格式与心跳 | 未读数推送：`patra-notification-adapter/.../realtime/rest/UnreadCountStreamController.java`、`SseUnreadCountStream.java`、心跳任务 `UnreadCountStreamHeartbeatScheduleJob.java` | PAP-158 |
| web-gateway-auth-by-session-lookup | 网关怎么认人：查会话再给下游签身份断言 | 决策 | 进阶 | web-reverse-proxy-and-gateway、http-cookie-and-session | 业界三类做法（签发方独占 / 网关自持 / 共享存储）对比，Patra 为什么让网关调 identity 查会话 | `security/SessionTokenAuthenticationConverter.java`、`IdentityAssertionRequestHeadersFilter.java`；identity `SessionIntrospectionService.java`；设计 `docs/patra/specs/2026-10-23-session-introspection-design.md` | PAP-161 |
| web-trusted-forwarded-headers | 真实客户端 IP：只信自己人加的转发头 | 决策 | 进阶 | web-reverse-proxy-and-gateway | `X-Forwarded-For` 谁都能伪造；受信转发链怎么定 | v0.12 决策 I | PAP-161 |
| web-gateway-sse-buffered | 网关把事件流攒住了 | 排查 | 实战 | web-server-push-and-sse | 推送经网关后迟迟不到：底层客户端读满 16 KB 才返回，推送路由改成逐字节读、按事件刷出 | `config/PushProxyHandlerFunction.java`、`GatewayPushRouteIT.java`；排查记录 | PAP-161 |
| web-gateway-lb-invalid-scheme | 网关报 Invalid scheme [lb] | 排查 | 实战 | web-reverse-proxy-and-gateway、spring-service-discovery | 换了底层 HTTP 客户端工厂后 `lb://` 不再被解析；客户端工厂的设置顺序 | 排查记录；`IdentityClientConfiguration.java`、`patra-tool-http-client/.../HttpClientInterfaceAutoConfiguration.java`（写作时核对修复落点） | PAP-161 |
| web-security-adds-cache-control | 代理的响应被补上了 Cache-Control | 坑 | 实战 | http-caching、web-reverse-proxy-and-gateway | 安全框架默认给响应加禁止缓存的头，连转发的下游响应也加 | `security/GatewaySecurityConfiguration.java`；排查记录 | PAP-161 |
| web-no-proxy-matches-ip-literal-only | no_proxy 的网段只管 IP 字面量，不管主机名 | 坑 | 实战 | web-reverse-proxy-and-gateway、web-url-anatomy | 命令行工具只认代理环境变量、不看代理软件的规则；网段只和网址里的 IP 比，主机名不先解析 | 排查记录（shell no_proxy、fake-ip 排查里的误判） | PAP-161 |

### 1.2 数据库与事务（主路线 `database`）

| slug | 标题 | 类型 | 难度 | 前置 | 讲什么 | 例子 | 归属 |
|---|---|---|---|---|---|---|---|
| db-what-is-a-database | 数据库和表 | 概念 | 入门 | — | 为什么不直接存文件；表、行、列；关系型数据库是什么 | catalog 建期刊表的第一个迁移：`patra-catalog-infra/src/main/resources/db/migration/V1__create_venue.sql` | PAP-158 |
| db-keys-and-relations | 主键、外键与表之间的关系 | 概念 | 入门 | db-what-is-a-database | 一对一、一对多、多对多与中间表 | 文献、作者、机构三方关系：`PublicationAuthorAffiliationDao.java` 对应的表 | PAP-158 |
| db-sql-select | SQL 查询：SELECT、WHERE 与 ORDER BY | 概念 | 基础 | db-keys-and-relations | 取哪些列、过滤哪些行、怎么排序与分页 | 期刊表上的查询 | PAP-158 |
| db-sql-join | 连表查询：JOIN | 概念 | 基础 | db-sql-select | 内连接与左连接；连表时行数为什么会变多 | 文献连作者 | PAP-158 |
| db-sql-write | 增删改：INSERT、UPDATE 与 DELETE | 概念 | 基础 | db-sql-select | 写入数据；UPDATE 忘了 WHERE 会怎样；插入冲突时更新（upsert） | catalog 导入时的批量写入 | PAP-158 |
| db-constraints-and-null | 约束与 NULL | 概念 | 基础 | db-sql-write | NOT NULL、UNIQUE、CHECK、外键约束；NULL 的三值逻辑 | `V1__create_venue.sql` 里的 CHECK 约束 | PAP-158 |
| db-indexes | 索引：为什么查得快 | 概念 | 基础 | db-sql-select | B 树索引的直觉；索引让写变慢、占空间；联合索引的列顺序 | catalog 迁移里的索引 | PAP-158 |
| db-transactions | 事务：要么全成，要么全不成 | 概念 | 基础 | db-sql-write | ACID；提交与回滚；一个用例一个事务 | v0.15 决策 A：「有帮助」计数与明细同一事务写入 | PAP-158 |
| db-connection-pool | 连接池 | 概念 | 基础 | db-transactions | 建连接很贵所以要复用；池多大合适；连接借了要还 | `patra-catalog-boot/src/main/resources/application.yml` 里的 `maximum-pool-size` | PAP-158 |
| db-redis-basics | Redis：内存里的键值库 | 概念 | 基础 | db-what-is-a-database | 键值、过期时间、原子操作与 Lua 脚本；Redis 适合放什么 | identity 会话：`redis/session-create.lua`、`session-touch.lua` | PAP-158 |
| db-schema-migrations-flyway | Flyway：表结构也要版本管理 | 概念 | 进阶 | db-constraints-and-null、db-indexes | 迁移脚本按版本号只执行一次；执行过的为什么不能改 | 各服务 `src/main/resources/db/migration/`；interaction 按子域分目录：`patra-interaction-infra/src/main/resources/db/migration/` | PAP-158 |
| db-isolation-levels | 隔离级别：两个事务同时改会怎样 | 概念 | 进阶 | db-transactions | 脏读、不可重复读、幻读；PostgreSQL 默认的读已提交 | 两人同时收藏同一篇文献 | PAP-158 |
| db-locks | 锁：乐观锁、行锁与咨询锁 | 概念 | 进阶 | db-isolation-levels | 版本号检测冲突；`SELECT ... FOR UPDATE`；用咨询锁保证同一时刻只跑一个导入 | `@Version`：`AuthorRepositoryAdapter.java`；咨询锁：`patra-catalog-infra/.../kernel/lock/PgAdvisoryRunLockAdapter.java` | PAP-158 |
| db-partial-unique-index | 局部唯一索引：只在部分行上唯一 | 概念 | 进阶 | db-indexes、db-constraints-and-null | `WHERE` 条件的唯一索引；软删除、种子数据唯一性怎么做 | `db/migration/subject/V20261024200000__create_subject_registry.sql` | PAP-158 |
| db-jsonb-columns | JSON 列：jsonb 什么时候用 | 概念 | 进阶 | db-constraints-and-null | 半结构化数据放 jsonb 的利弊；应用里怎么读写 | `patra-tool/patra-tool-jpa/.../json/JsonColumnCodec.java` | PAP-158 |
| db-explain-query-plans | 看懂执行计划：EXPLAIN | 概念 | 进阶 | db-indexes | 顺序扫描与索引扫描；查询为什么没用上索引 | 机构联想查询的执行计划 | PAP-158 |
| db-trigram-search | 模糊搜索：pg_trgm 与 GIN 索引 | 概念 | 进阶 | db-explain-query-plans | 三元组切分；LIKE '%词%' 怎么也能走索引 | `db/migration/V9__add_suggest_trigram_indexes.sql`（机构与 MeSH 联想） | PAP-158 |
| db-schema-per-subdomain | 一个库、多个 schema、一份迁移历史 | 决策 | 进阶 | db-schema-migrations-flyway | 每个子域一个 schema 还是一个库；共用 Flyway 历史的代价 | v0.13 决策 B | PAP-161 |
| db-orphan-connections | too many clients：孤儿连接堆积 | 排查 | 实战 | db-connection-pool | 连接数不知不觉涨满；怎么确认是孤儿连接而不是真负载 | 排查记录 | PAP-161 |
| db-check-passes-null | CHECK 约束遇到 NULL 直接放行 | 坑 | 实战 | db-constraints-and-null | 三值逻辑下 NULL 让 CHECK 结果为「未知」，等于通过；约束测试要断言约束名 | interaction 迁移 `subject/V20261027120000__extend_registry_for_journal_and_post.sql` 里的 CHECK；计划缺陷清单 | PAP-161 |
| db-jsonb-not-null-json-null | jsonb 的 NOT NULL 挡不住 JSON 的 null | 坑 | 实战 | db-jsonb-columns | SQL 的 NULL 与 JSON 里的 `null` 是两回事 | `JsonColumnCodec.java`；计划缺陷清单 | PAP-161 |
| db-trigram-short-pattern-full-scan | 短关键词让 GIN 索引全扫 | 排查 | 实战 | db-trigram-search | 一两个字的联想在通用计划下扫遍整个索引；怎么发现、怎么改 | `V9__add_suggest_trigram_indexes.sql`；计划缺陷清单 | PAP-161 |

### 1.3 Java 与 Spring（主路线 `java-spring`）

| slug | 标题 | 类型 | 难度 | 前置 | 讲什么 | 例子 | 归属 |
|---|---|---|---|---|---|---|---|
| java-how-a-program-runs | Java 程序怎么跑起来 | 概念 | 入门 | — | 源码 → 编译 → 字节码 → JVM；main 方法是入口 | `patra-tool/patra-tool-launch/.../PatraApplication.java` 与各服务的启动类 | PAP-158 |
| java-classes-objects-interfaces | 类、对象与接口 | 概念 | 入门 | java-how-a-program-runs | 类是图纸、对象是实物；接口只约定「能做什么」，实现类给出「怎么做」 | 接口 `LoginThrottlePort.java` 与实现 `LoginThrottleAdapter.java` | PAP-158 |
| java-build-with-gradle | 构建：Gradle 把代码变成能跑的包 | 概念 | 入门 | java-how-a-program-runs | 依赖从哪来、任务怎么串、多模块怎么组织 | `settings.gradle.kts`；约定插件 `build-logic/` | PAP-158 |
| java-records-and-immutability | record 与不可变对象 | 概念 | 基础 | java-classes-objects-interfaces | 值语义、紧凑构造器、防御性拷贝；为什么不可变更安全 | 值对象 `patra-api/patra-interaction/patra-interaction-domain/src/main/java/dev/patra/interaction/domain/collection/model/vo/CollectionName.java`；`.claude/rules/code-style.md` 的 record 规范 | PAP-158 |
| java-exceptions | 异常：出错了怎么往外报 | 概念 | 基础 | java-classes-objects-interfaces | 受检与非受检异常；异常要带上「错在哪」的语义 | `patra-tool-core` 的 `DomainException`、`RemoteCallException.java` | PAP-158 |
| java-collections-and-streams | 集合与 Stream | 概念 | 基础 | java-classes-objects-interfaces | List / Map / Set；不可变集合；用 Stream 写数据管道 | `.claude/rules/code-style.md` 不可变集合规范 | PAP-158 |
| java-annotations | 注解：贴在代码上的标签 | 概念 | 基础 | java-classes-objects-interfaces | 注解本身什么都不做，是框架或编译器读它才生效 | Lombok `@Builder`、Spring `@Component` | PAP-158 |
| spring-dependency-injection | 依赖注入：对象不自己 new 依赖 | 概念 | 基础 | java-annotations | Bean 与容器；构造器注入；为什么这样更好测 | 应用层 Handler 通过构造器拿到端口：`LogoutUserHandler.java` | PAP-158 |
| spring-boot-auto-configuration | 自动配置：引一个依赖就能用 | 概念 | 基础 | spring-dependency-injection、java-build-with-gradle | `AutoConfiguration.imports` 与条件注解；默认值怎么被覆盖 | `patra-tool-events/src/main/resources/META-INF/spring/...AutoConfiguration.imports`；v0.10 决策 D、G | PAP-158 |
| spring-configuration-properties | 配置：application.yml 与 @ConfigurationProperties | 概念 | 基础 | spring-boot-auto-configuration | 配置绑定到对象；配置来源的优先级；环境变量覆盖 | `LoginThrottleProperties.java`；v0.9 决策 C「配置分三层」 | PAP-158 |
| spring-mvc-controller | 写一个接口：从 Controller 到 JSON | 概念 | 基础 | spring-dependency-injection、http-rest-api-design | 路由映射、参数绑定、返回值变成 JSON | `patra-identity-adapter/.../session/rest/MeSessionController.java` | PAP-158 |
| spring-json-jackson | 对象和 JSON 怎么互转：Jackson | 概念 | 基础 | spring-mvc-controller、http-json-and-content-type | 序列化与反序列化；Long 为什么要转成字符串（JS 数字精度） | `patra-tool-web/.../json/LongToStringHttpMessageConvertersCustomizer.java` | PAP-158 |
| spring-proxies-and-transactional | 代理：@Transactional 为什么有时不生效 | 概念 | 进阶 | spring-dependency-injection、db-transactions | Spring 用代理包住 Bean；类内自调用绕过代理 | 应用层 `@Transactional` 的位置：`patra-tool-test` 的 `HexagonalArchitectureRules`（ArchUnit 规则）把事务限定在 app 层，`build-logic/.../patra.hexagonal-app.gradle.kts` 只负责加依赖 | PAP-158 |
| spring-jpa-basics | JPA：用对象读写表 | 概念 | 进阶 | spring-dependency-injection、db-keys-and-relations | Entity、Repository、持久化上下文与 flush | `patra-catalog-infra/.../author/persistence/entity/AuthorEntity.java`、`dao/AuthorDao.java` | PAP-158 |
| spring-jpa-n-plus-one | N+1 查询 | 坑 | 进阶 | spring-jpa-basics | 懒加载在循环里一条一条查；`@EntityGraph` 与 JOIN FETCH | `AuthorDao.java` 的 `findByOrcidIn`（`JOIN FETCH` 一次带回两个集合） | PAP-158 |
| spring-http-interface-clients | 调别的服务：HTTP Interface | 概念 | 进阶 | spring-mvc-controller、http-status-codes | 用接口声明远程调用；契约放在 `-api` 模块；失败统一成 RemoteCallException | `patra-identity-api/.../session/endpoint/SessionEndpoint.java` | PAP-158 |
| spring-service-discovery | 服务发现：`lb://` 怎么找到服务 | 概念 | 进阶 | spring-http-interface-clients | 注册中心、实例列表、客户端负载均衡 | catalog `config/HttpClientConfiguration.java` 里的 `lb://` | PAP-158 |
| spring-nacos-config-and-refresh | Nacos：配置中心与动态刷新 | 概念 | 进阶 | spring-configuration-properties | 配置放 git，CD 推到 Nacos；刷新就是重新绑定，所以要刷新的配置写成可变类 | v0.9 决策 B、D；`IdentityConfigRefreshIT.java`；`patra-infra/scripts/nacos-push.sh` | PAP-158 |
| spring-security-filter-chain | Spring Security：请求要过的一串过滤器 | 概念 | 进阶 | spring-mvc-controller、http-cookie-and-session | 过滤器链的顺序；认证与授权；错误怎么变成 401 / 403 | `patra-gateway-boot/.../security/GatewaySecurityConfiguration.java` | PAP-158 |
| spring-batch-jobs | 批处理：Spring Batch 导入大文件 | 概念 | 进阶 | spring-dependency-injection、db-transactions | Job / Step / Reader / Writer；分块提交；失败后从断点重跑 | `patra-catalog-infra/.../author/batch/AuthorImportJobConfig.java`、`mesh/batch/MeshDescriptorJobConfig.java` | PAP-158 |
| spring-scheduled-jobs | 定时任务：谁在半夜叫醒服务 | 概念 | 进阶 | spring-dependency-injection | 进程内定时与调度中心（XXL-Job）的区别；任务重入与互斥 | `LoginRecordCleanupScheduleJob.java`、`PmcFullTextFetchScheduleJobIT.java` | PAP-158 |
| spring-nacos-revert-no-refresh | 配置推回原文，服务却不刷新 | 排查 | 实战 | spring-nacos-config-and-refresh | 坏值导致刷新失败后，推回出错前的原文 md5 相同，客户端不再通知 | 排查记录 | PAP-161 |
| spring-refresh-strict-enum | 配置刷新时枚举写法不对就失败 | 坑 | 实战 | spring-nacos-config-and-refresh | 启动时宽松、刷新时严格：横线小写的枚举值让每次变更都刷新失败，还不打日志 | `patra-gateway-boot/src/main/resources/application.yml` 的 `DONT_FOLLOW`；排查记录 | PAP-161 |
| spring-no-custom-refreshable-properties | 取消自研的 RefreshableProperties | 决策 | 实战 | spring-nacos-config-and-refresh | 自研刷新组件与 Spring Cloud 自带重新绑定的取舍；主流做法优先 | v0.9 决策 D（2026-10-14 改定） | PAP-161 |
| spring-json-single-source | JSON 只从容器拿一个 JsonMapper | 决策 | 实战 | spring-json-jackson | 到处 new ObjectMapper 的后果；只在 Web 层做 Long 转字符串 | v0.10 决策 F | PAP-161 |
| spring-batch-context-java-types-only | Batch 上下文只认 java.* 类型 | 坑 | 实战 | spring-batch-jobs | 往 ExecutionContext 里放自定义类型，重启时反序列化失败 | 计划缺陷清单 | PAP-161 |
| spring-stepscope-proxy-fields | @StepScope 代理别碰字段 | 坑 | 实战 | spring-batch-jobs、spring-proxies-and-transactional | 拿到的是代理，直接读字段读到的是代理自己的空字段 | `PubmedImportCounters.java`、`PubmedImportJobConfig.java`；计划缺陷清单 | PAP-161 |
| spring-flush-order-insert-before-delete | 先删后插却撞了唯一约束 | 坑 | 实战 | spring-jpa-basics、db-constraints-and-null | Hibernate flush 时先插后删，派生 delete 方法挡不住唯一键冲突 | 计划缺陷清单 | PAP-161 |
| java-jvm-proxy-no-cidr | JVM 的代理例外列表不认 CIDR | 坑 | 实战 | web-reverse-proxy-and-gateway、spring-http-interface-clients | macOS 的 JDK 继承系统代理与例外列表；`nonProxyHosts` 只认通配符，网段写法全部失效 | 排查记录（v0.8 PAP-69 本机对照） | PAP-161 |
| java-ide-and-gradle-share-build-dir | IDE 和命令行 Gradle 同时写 build 目录 | 坑 | 实战 | java-build-with-gradle | 两个进程删写同一个输出目录，冒出说不通的编译错误；先查第二个编译进程 | 排查记录（IDEA 自动构建、构建脚本自动重新加载） | PAP-161 |
| java-gjf-breaks-markdown-doc-comments | 格式化工具把 /// 文档注释折成了 // | 坑 | 实战 | ops-commit-conventions | google-java-format 不认 `///`，超长行的续行补 `//`，文档注释断成两段且不报错 | 排查记录（v0.7 PAP-52，PR #192） | PAP-161 |
| spring-nacos-raft-hostname-changed | 容器重建后 Nacos 选不出 leader | 排查 | 实战 | spring-service-discovery、ops-docker-compose | Raft 成员按主机名认人；容器重建换了主机名，持久化的名单对不上；AP 与 CP 两套机制 | `patra-infra/docker/docker-compose.core.yaml` 的 `hostname: patra-nacos`；排查记录 | PAP-161 |

### 1.4 消息与事件（主路线 `messaging`）

| slug | 标题 | 类型 | 难度 | 前置 | 讲什么 | 例子 | 归属 |
|---|---|---|---|---|---|---|---|
| msg-why-async | 为什么要异步 | 概念 | 入门 | — | 同步调用一环等一环；有些事不必让用户等（发邮件、发提醒） | 注册后发验证邮件、评论后发提醒 | PAP-159 |
| msg-what-is-a-message-queue | 消息队列是什么 | 概念 | 基础 | msg-why-async | 生产者、队列、消费者；解耦与削峰；消息队列不是数据库 | Patra 的 RabbitMQ：`patra-infra/docker/docker-compose.core.yaml` | PAP-159 |
| msg-exchange-queue-binding | 交换机、队列、绑定与路由键 | 概念 | 基础 | msg-what-is-a-message-queue | direct / topic / fanout 三种交换机；一条消息怎么找到队列 | `patra-tool-events/.../consumer/EventQueue.java`；v0.16 决策 E「RabbitMQ 的结构」 | PAP-159 |
| msg-ack-and-redelivery | 确认（ack）与重投 | 概念 | 基础 | msg-exchange-queue-binding | 自动确认与手动确认；消费失败时消息去哪了 | `patra-notification-adapter/.../inbox/mq/InboxEventListener.java` | PAP-159 |
| msg-in-process-events | 进程内事件：Spring 的发布与监听 | 概念 | 基础 | msg-why-async、spring-dependency-injection | 同一个服务里用事件解耦；默认是同步执行的 | v0.14 决策 A「评论与提醒用进程内事件联动」；`HelpfulTargetDeletedListener.java` | PAP-159 |
| msg-after-commit-listeners | 提交后再处理：@TransactionalEventListener | 概念 | 进阶 | msg-in-process-events、db-transactions | 事务回滚了就不该发通知；提交后监听的时机与代价 | `patra-catalog-app/.../publication/listener/PmcOpenAccessListener.java`；v0.16 决策 I「事务提交后再推」 | PAP-159 |
| msg-dual-write-problem | 双写问题：存库和发消息怎么一起成功 | 概念 | 进阶 | msg-ack-and-redelivery、db-transactions | 先存后发、先发后存各会丢什么；为什么分布式事务不是答案 | v0.16 决策 C 的背景 | PAP-159 |
| msg-transactional-outbox | 发件箱：先记进本地表再发出去 | 概念 | 进阶 | msg-dual-write-problem | 消息和业务数据同一事务落库，再由外送把它发出去；没发出去的定时补投 | Modulith 事件发布登记表 `event_publication`；补投 `patra-tool-events/.../resubmit/EventPublicationResubmitter.java`；v0.16 决策 C | PAP-159 |
| msg-publisher-confirms | 发送确认：broker 真的收到了吗 | 概念 | 进阶 | msg-transactional-outbox | 发出去不等于收到；等 broker 确认后才把发件箱记录标完成 | `patra-tool-events/.../publisher/ConfirmingRabbitMessagingTemplate.java` | PAP-159 |
| msg-at-least-once-and-dedup | 至少一次投递与消费端去重 | 概念 | 进阶 | msg-publisher-confirms、msg-ack-and-redelivery | 最多一次、至少一次、恰好一次；按事件 ID 去重让重复消息无害 | `patra-tool-events/.../consumer/JdbcProcessedEventAdapter.java`、`ProcessedEventPurger.java` | PAP-159 |
| msg-dead-letter | 死信：处理不了的消息去哪 | 概念 | 进阶 | msg-ack-and-redelivery | 重试几次后转进死信队列；死信要有人看 | `EventQueue.java`；`SecurityMailDeadLetterIT.java` | PAP-159 |
| msg-ordering | 乱序：后发的消息先到了 | 概念 | 进阶 | msg-at-least-once-and-dedup | 为什么不能指望顺序；用版本号和墓碑判断新旧 | `patra-notification-app/.../inbox/usecase/ingest/RecordCommentEditedHandler.java`；v0.16 决策 D | PAP-159 |
| msg-why-rabbitmq | 为什么换成 RabbitMQ | 决策 | 实战 | msg-exchange-queue-binding、msg-publisher-confirms | 从 RocketMQ 换到 RabbitMQ 的背景、选项与代价 | v0.16 release spec 第 1 节现状（RocketMQ 实际零使用）、第 3 节 J、调研记录（Modulith 外送支持 AMQP 不支持 RocketMQ） | PAP-161 |
| msg-recipient-lookup-two-paths | 收件地址：随事件带，还是发送时现查 | 决策 | 实战 | msg-transactional-outbox | 安全邮件随事件带地址，提醒邮件发送时现查，各防什么 | v0.16 决策 F | PAP-161 |
| msg-mail-idempotency-key | 消息重投了，邮件别发两封 | 决策 | 实战 | msg-at-least-once-and-dedup | 对外部系统的幂等：发信时带幂等键 | v0.16 决策 G | PAP-161 |

### 1.5 DDD 与六边形（主路线 `ddd-hexagonal`）

| slug | 标题 | 类型 | 难度 | 前置 | 讲什么 | 例子 | 归属 |
|---|---|---|---|---|---|---|---|
| ddd-why-layer-code | 为什么代码要分层 | 概念 | 入门 | — | 所有代码写在一个文件里会怎样；分层是为了改一处不牵动全身 | 同一个「建收藏夹」功能，不分层与分层两种写法对比 | PAP-159 |
| ddd-rules-vs-plumbing | 业务规则和技术细节分开放 | 概念 | 入门 | ddd-why-layer-code | 「收藏夹最多几个」是业务规则，「存在哪张表」是技术细节 | `patra-interaction-domain/.../collection/policy/CollectionLimits.java` | PAP-159 |
| ddd-entities-and-value-objects | 实体与值对象 | 概念 | 基础 | ddd-rules-vs-plumbing、java-records-and-immutability | 有身份的是实体，只看值的是值对象；值对象自己守规则 | `patra-interaction-domain/.../collection/model/vo/CollectionName.java`、`CollectionId.java` | PAP-159 |
| ddd-aggregates | 聚合与聚合根 | 概念 | 基础 | ddd-entities-and-value-objects | 一致性边界；外面只能通过聚合根改里面的东西 | `patra-interaction-domain/.../discussion/model/aggregate/CommentAggregate.java`；基类 `patra-tool-core/.../domain/AggregateRoot.java` | PAP-159 |
| ddd-repositories | 仓库：聚合怎么存、怎么取 | 概念 | 基础 | ddd-aggregates | 接口定义在领域层，实现放在基础设施层 | `.claude/rules/tech/port-service.md` 的 Repository 约定；`AuthorRepositoryAdapter.java` | PAP-159 |
| ddd-domain-exceptions | 领域异常：错误也有业务含义 | 概念 | 基础 | ddd-aggregates、java-exceptions | 异常带语义特征，由 Web 层统一翻译成状态码 | `collection/exception/CollectionNameTakenException.java`、`CollectionErrorTrait.java` | PAP-159 |
| ddd-application-layer | 应用层：一个用例怎么编排 | 概念 | 基础 | ddd-repositories | 命令与处理器；事务边界放在这里；应用层不写业务规则 | `LogoutUserHandler.java`；`patra-tool-cqrs/.../SimpleCommandBus.java` | PAP-159 |
| ddd-ports-and-adapters | 端口与适配器（六边形） | 概念 | 进阶 | ddd-application-layer、spring-dependency-injection | 核心只认端口，外部世界通过适配器接进来；换数据库不动业务 | `EmailChangeThrottlePort.java` → `EmailChangeThrottleAdapter.java`；`.claude/rules/tech/port-service.md` | PAP-159 |
| ddd-patra-six-modules | Patra 一个服务的六个模块 | 概念 | 进阶 | ddd-ports-and-adapters | domain / app / infra / adapter / api / boot 各放什么、谁能依赖谁 | `patra-api/patra-interaction/` 目录结构 | PAP-159 |
| ddd-architecture-guards | 让构建替你守住架构边界 | 概念 | 进阶 | ddd-patra-six-modules | 依赖规则写进构建，违反就编译不过；ArchUnit 检查分包 | `build-logic/`（R1～R6）；`patra-tool-test/.../archunit/SubdomainArchitectureRules.java` | PAP-159 |
| ddd-cqrs-read-side | 读写分开：查询走另一条路 | 概念 | 进阶 | ddd-application-layer | 写走聚合，读直接查读模型；ReadPort 与 QueryService | `patra-identity-app/.../session/usecase/query/SessionQueryService.java` | PAP-159 |
| ddd-subdomains | 子域：一个服务里再切一刀 | 概念 | 进阶 | ddd-patra-six-modules | 核心子域与支撑子域；层内先按子域分包 | interaction 的 collection / discussion / reaction；`.claude/rules/tech/package-layout.md` | PAP-159 |
| ddd-bounded-contexts | 限界上下文与微服务边界 | 概念 | 进阶 | ddd-subdomains | 同一个词在不同上下文里意思不同；按上下文划服务 | catalog / identity / interaction / notification 四个服务 | PAP-159 |
| ddd-domain-vs-integration-events | 领域事件与集成事件 | 概念 | 进阶 | ddd-bounded-contexts、msg-in-process-events | 服务内的事件和跨服务的事件为什么分两层；集成事件是对外契约 | `discussion/event/CommentPublishedEvent.java`；v0.16 决策 B | PAP-159 |
| ddd-translate-remote-errors | 跨服务调用：把对方的错误翻译成自己的话 | 概念 | 进阶 | ddd-bounded-contexts、spring-http-interface-clients | 适配器捕获远程异常，按语义特征转成本服务的领域异常（防腐层） | `.claude/rules/tech/error-handling.md`「HTTP Interface 错误处理」；`RemoteCallException.java` | PAP-159 |
| ddd-username-only-in-identity | 用户名只存在 identity 一处 | 决策 | 实战 | ddd-bounded-contexts、db-keys-and-relations | 其他地方只存 user_id、展示时现查；改名只改一行，由构建扫描迁移脚本守住 | `build-logic/src/main/kotlin/MigrationColumnCheck.kt`（R6）；`.claude/rules/project-info.md` | PAP-161 |
| ddd-session-owned-by-identity | 会话归 identity 独占 | 决策 | 实战 | ddd-bounded-contexts、web-gateway-auth-by-session-lookup | 网关不连 Redis、只调 identity；边界换来的代价 | `docs/patra/specs/2026-10-23-session-introspection-design.md` | PAP-161 |
| ddd-extract-notification-service | 把提醒搬出 interaction，新建通知服务 | 决策 | 实战 | ddd-bounded-contexts、ddd-domain-vs-integration-events | interaction 只说「发生了什么」，提醒归通知服务；拆服务的时机与代价 | v0.16 决策 A | PAP-161 |
| ddd-subdomain-first-layout | 层内先按子域分包 | 决策 | 实战 | ddd-subdomains | 先按组件类型还是先按子域分包；五个服务怎么迁过去 | `.claude/rules/tech/package-layout.md`；PR #313 | PAP-161 |
| ddd-tool-modules-by-capability | 工具模块按技术能力分 | 决策 | 实战 | spring-boot-auto-configuration、ddd-architecture-guards | 「通用 / 专属」分法与「技术能力」分法；依赖规则由构建守 | v0.10 决策 A、C | PAP-161 |
| ddd-remove-unused-service | 删掉没在做的生意：移除 ingest 服务 | 决策 | 实战 | ddd-bounded-contexts | 代码对应的业务不存在时该不该留；删除的范围与验收 | PAP-136（PR #339） | PAP-161 |

### 1.6 测试（主路线 `testing`）

| slug | 标题 | 类型 | 难度 | 前置 | 讲什么 | 例子 | 归属 |
|---|---|---|---|---|---|---|---|
| test-why-write-tests | 为什么要写测试 | 概念 | 入门 | — | 手工点一遍的成本；测试是会自动重跑的说明书 | Patra 每个 PR 都要过的 CI（`.github/workflows/ci.yml`） | PAP-160 |
| test-unit-tests | 单元测试：JUnit 与 AssertJ | 概念 | 基础 | test-why-write-tests、java-classes-objects-interfaces | 准备、执行、断言；一个测试只测一件事；命名读得通 | `patra-tool-web/src/test/.../DefaultErrorResolutionEngineTest.java`；`.claude/rules/testing/conventions.md` | PAP-160 |
| test-doubles-and-mocks | 测试替身：Stub、Mock 与 Fake | 概念 | 基础 | test-unit-tests | 把依赖换成假的；什么时候该用 Mock，什么时候不该 | 应用层 Handler 测试里用 Mockito 替掉端口 | PAP-160 |
| test-tdd | TDD：红、绿、重构 | 概念 | 基础 | test-unit-tests | 先写失败的测试，再写刚好让它通过的代码，再整理 | Patra 根 `CLAUDE.md` 的强制 TDD 规则 | PAP-160 |
| test-controlling-time | 让时间可控：注入 Clock | 概念 | 基础 | test-doubles-and-mocks | 代码里直接取当前时间就没法测；注入时钟、测试里换成固定时钟 | `PortalCitationController.java` 等处的 `Clock clock` | PAP-160 |
| test-kinds-and-pyramid | 测试分几层：单元、集成、端到端 | 概念 | 基础 | test-unit-tests | 测试金字塔；每一层测什么、多快、多少 | Patra 的 `test` / `integrationTest` / `e2eTest` 三个 source set | PAP-160 |
| test-frontend-unit | 前端测试：Vitest 与 Testing Library | 概念 | 基础 | test-unit-tests、fe-react-components-and-state | 测组件看用户看到什么，不测实现细节 | `patra-portal/vitest.config.ts`、`patra-portal/tests/app/api/auth-me.test.ts` | PAP-160 |
| test-integration-testcontainers | 集成测试与 Testcontainers | 概念 | 进阶 | test-kinds-and-pyramid、db-transactions | 用真实的 PostgreSQL、RabbitMQ 容器跑测试；为什么不用内存库 | `CatalogE2EPostgreSQLContainerInitializer.java`；`.github/workflows/ci.yml` | PAP-160 |
| test-spring-slice-tests | 切片测试：只起需要的那一层 | 概念 | 进阶 | test-integration-testcontainers、spring-mvc-controller | `@WebMvcTest` 与 `@DataJpaTest`；切片缺 Bean 时怎么补 | `IdentityITWebMvcConfig.java`、`CatalogAdapterITWebMvcConfig.java` | PAP-160 |
| test-http-stubs-wiremock | 外部服务的替身：WireMock | 概念 | 进阶 | test-integration-testcontainers、spring-http-interface-clients | 假装成 PubMed、PMC 这样的外部接口，测超时、错误码 | `patra-catalog-boot/src/e2eTest/.../fulltext/FullTextPipelineE2E.java` | PAP-160 |
| test-e2e-playwright | 端到端测试：Playwright | 概念 | 进阶 | test-kinds-and-pyramid、fe-nextjs-pages-and-routing | 开真浏览器走真实流程；选择器、等待与截图 | `patra-portal/playwright.config.ts`、`patra-portal/tests/e2e/auth.spec.ts` | PAP-160 |
| test-coverage | 覆盖率说明了什么，没说明什么 | 概念 | 进阶 | test-kinds-and-pyramid | 行覆盖与分支覆盖；高覆盖率不等于测对了 | `.github/workflows/ci.yml` 的 coverage 任务 | PAP-160 |
| test-flaky-tests | 偶发失败：怎么判断、怎么治 | 概念 | 进阶 | test-integration-testcontainers | 时间、并发、共享状态、机器负载；重跑不是治法 | Patra 记录过的几类抖动测试（见本路线实战篇） | PAP-160 |
| test-hikari-too-many-clients-in-ci | 测试上下文一多，CI 报 too many clients | 坑 | 实战 | test-integration-testcontainers、db-connection-pool | 每个 Spring 测试上下文各开一个连接池，本地不报、CI 才报 | 计划缺陷清单 | PAP-161 |
| test-first-case-timeout | 第一个用例偶发超时 | 排查 | 实战 | test-flaky-tests | 首次初始化开销大，高负载时撞上超时；没改动就先看负载 | `JpaAutoConfigurationTest`；排查记录 | PAP-161 |
| test-high-load-timeouts | 机器负载高时前端测试随机超时 | 坑 | 实战 | test-flaky-tests、test-frontend-unit | 并行 worker 太多把负载推高；限 worker 数而不是放宽超时 | 排查记录（去掉本机同步软件细节） | PAP-161 |
| test-redis-ttl-real-time | Redis 过期走真实时间，注入的 Clock 管不到 | 坑 | 实战 | test-controlling-time、db-redis-basics | 应用里的时钟能换，Redis 服务端的时钟换不了 | 计划缺陷清单 | PAP-161 |
| test-resttestclient-retries-429 | 测试客户端自动重发了 429 | 坑 | 实战 | test-spring-slice-tests、web-rate-limiting | 客户端按 Retry-After 自动重试，限流测试断言不到 429 | 计划缺陷清单 | PAP-161 |
| test-e2e-wait-for-hydration | 端到端测试要等页面水合完再操作 | 坑 | 实战 | test-e2e-playwright、fe-hydration | 开发模式冷编译时，点击落在还没水合的按钮上，测试空过 | 排查记录 | PAP-161 |
| test-route-mocking-font-flash | 拦截网络后截图抓到了回退字体 | 坑 | 实战 | test-e2e-playwright | 开着请求拦截会关掉缓存，字体重新下载，截图时还没到 | 排查记录 | PAP-161 |

### 1.7 部署与 CI/CD（主路线 `deploy-cicd`）

| slug | 标题 | 类型 | 难度 | 前置 | 讲什么 | 例子 | 归属 |
|---|---|---|---|---|---|---|---|
| ops-code-to-production | 代码怎么变成线上服务 | 概念 | 入门 | — | 写代码 → 合并前检查 → 打包 → 部署 → 验货 → 巡检，整条路先走一遍 | 旧课程三条线（质检 / 上线 / 守夜）的全景 | PAP-160 |
| ops-git-basics | Git：提交、分支与远端 | 概念 | 入门 | ops-code-to-production | 提交是快照、分支是指针、推送是同步；一切从分支开始 | 旧站「写代码」 | PAP-160 |
| ops-commit-conventions | 提交规范与提交前检查 | 概念 | 基础 | ops-git-basics | 提交信息怎么写；提交前自动格式化、查密钥 | `commitlint.config.cjs`；pre-commit 里的 spotless 与 gitleaks | PAP-160 |
| ops-pull-requests | PR 与分支保护 | 概念 | 基础 | ops-git-basics | PR 是合并前的考场；必过检查、对话要处理完、squash 合并 | 旧站「开 PR」；`.github/workflows/ci.yml` 的 `required-check` | PAP-160 |
| ops-secrets-out-of-repo | 密钥不进仓库 | 概念 | 基础 | ops-git-basics | 公开仓库的一切都永久可读；密钥走环境变量与 CI 的 secrets，运行时注入 | 根 `CLAUDE.md`「安全红线」；v0.9 决策 E；旧术语「secrets」 | PAP-160 |
| ops-docker-images-and-containers | Docker 镜像与容器 | 概念 | 基础 | ops-code-to-production | 镜像是连运行环境一起打的包，容器是跑起来的镜像；镜像仓库 | `patra-learn/Dockerfile`；旧术语「Docker 镜像」「GHCR」 | PAP-160 |
| ops-docker-compose | 用 compose 拉起一整套服务 | 概念 | 基础 | ops-docker-images-and-containers | 一份文件声明每个服务的镜像、端口、依赖与健康标准 | `patra-infra/docker/docker-compose.core.yaml`、`docker-compose.apps.yaml`；旧术语「docker compose」 | PAP-160 |
| ops-cpu-architectures | 芯片架构：amd64 与 arm64 | 概念 | 基础 | ops-docker-images-and-containers | 镜像和芯片架构绑定；在一种机器上打另一种机器的包要做什么 | 云端 Intel 机器与 Apple 芯片的 mini | PAP-160 |
| ops-ci-pipeline | CI 流水线：workflow、job 与 runner | 概念 | 进阶 | ops-pull-requests | 仓库一有动静就按剧本开工；剧本、任务、执行机器各是什么 | `.github/workflows/ci.yml`；旧术语「GitHub Actions」「workflow」「runner」 | PAP-160 |
| ops-ci-changed-only | 只考改过的：按改动路由 | 概念 | 进阶 | ops-ci-pipeline | 改了哪个模块就测哪些受影响的模块；纯文档直接放行 | `patra-infra/cd/detect-changes.sh`、`module-graph.json`；旧站「只考改过的」 | PAP-160 |
| ops-ci-parallel-and-required-check | 并行开考与汇总放行 | 概念 | 进阶 | ops-ci-changed-only | 多个任务同时跑；用一个汇总任务当分支保护唯一认的检查 | `ci.yml` 的 backend 矩阵、portal、learn 与 `required-check`；旧站「并行考试」 | PAP-160 |
| ops-ci-security | 流水线安全：别让输入变成命令 | 概念 | 进阶 | ops-ci-pipeline | 把手动输入直接拼进 shell 会被注入；部署流水线只听 main 的推送 | `.github/workflows/cd.yml` 头部注释与 env 传参 | PAP-160 |
| ops-self-hosted-runner | 自托管 runner：家里的机器来干活 | 概念 | 进阶 | ops-ci-pipeline | 云端 runner 与自托管 runner；机器凭什么领到任务 | `runs-on: [self-hosted, macmini]`；`patra-infra/scripts/install-github-runner.sh`；旧站「mini 领任务」 | PAP-160 |
| ops-cd-deploy-loop | CD 部署闭环 | 概念 | 进阶 | ops-self-hosted-runner、ops-docker-compose | 镜像就位、按依赖顺序拉起、记住上一个好版本 | `.github/workflows/cd.yml`、`patra-infra/cd/deploy.sh`、`services.json`；旧站「部署闭环」 | PAP-160 |
| ops-health-checks | 健康检查：上线后要验货 | 概念 | 进阶 | ops-cd-deploy-loop | 健康接口、轮询与超时；容器健康与服务健康 | `deploy.sh` 的健康检查轮询；旧站「健康检查」 | PAP-160 |
| ops-rollback | 上线与回滚 | 概念 | 进阶 | ops-health-checks | 验货失败自动退回上一个好版本；手动回滚按服务名加旧版本号 | `deploy.sh` 的 `rollback()`、`cd.yml` 的 `workflow_dispatch`；旧站「上线与回滚」、旧操作卡「回滚」 | PAP-160 |
| ops-scheduled-watchdog | 定时巡检：cron、时区与四项体检 | 概念 | 进阶 | ops-self-hosted-runner | cron 用 UTC，北京时间要换算；查 runner 在不在、磁盘、容器健康、runner 版本 | `.github/workflows/runner-watchdog.yml`；旧站「每天 07:00」「查岗与钥匙」「四项体检」、旧操作卡「巡检」 | PAP-160 |
| ops-config-as-code | 配置也走流水线：从 git 推到 Nacos | 概念 | 进阶 | ops-cd-deploy-loop、spring-nacos-config-and-refresh | 非敏感配置以 git 为唯一来源，CD 自动推送，控制台只读 | `patra-infra/scripts/nacos-push.sh`；v0.9 决策 B | PAP-160 |
| ops-cloudflare-tunnel | 不开端口也能上公网：Cloudflare Tunnel | 概念 | 进阶 | ops-cd-deploy-loop、web-reverse-proxy-and-gateway、http-https-and-certificates | 家里的机器主动连出去，公网请求从隧道进来；只开放门户和学习站 | `patra-infra/docker/README.md`「门户的公网入口」（隧道配置不在仓库里）；v0.17 决策 A | PAP-160 |
| ops-native-arm64-build | 改在 mini 本机原生打包 | 决策 | 实战 | ops-cpu-architectures、ops-self-hosted-runner | 跨架构打包与本机原生打包的取舍；为什么镜像仓库只当备份 | `cd.yml` 构建任务的 `runs-on`；旧站「本机打包」 | PAP-160 |
| ops-wrong-arch-image | 坏了三个月：镜像芯片格式装错 | 排查 | 实战 | ops-cpu-architectures、ops-cd-deploy-loop | 云端打的镜像 mini 装不上，没人发现；部署前断言镜像架构 | 旧事故档案 #1 | PAP-160 |
| ops-runner-deregistered | runner 离线太久被除名 | 坑 | 实战 | ops-self-hosted-runner | 项目搁置期 runner 掉线无人察觉，被平台自动注销；每日查岗与安装脚本 | 旧事故档案 #2；`install-github-runner.sh` | PAP-160 |
| ops-timeout-shown-as-cancelled | 超时了，却显示「已取消」 | 坑 | 实战 | ops-ci-pipeline | 拉大镜像撞上时限被杀，界面显示成 cancelled；不再从外网拉镜像 | 旧事故档案 #3 | PAP-160 |
| ops-localhost-resolves-to-ipv6 | localhost 解析成 ::1，健康的服务被判不健康 | 坑 | 实战 | ops-health-checks | 服务只听 IPv4，检查却走了 IPv6；宿主机检查写死 127.0.0.1 | 旧事故档案 #4；`deploy.sh` 头部注释 | PAP-160 |
| ops-bash32-fullwidth-punctuation | 一个全角标点炸了整个脚本 | 坑 | 实战 | ops-scheduled-watchdog | macOS 自带 bash 3.2 把紧跟变量的全角字符并进变量名；变量一律加大括号 | 旧事故档案 #5 | PAP-160 |
| ops-typo-reports-success | 服务名拼错，流水线却报部署成功 | 坑 | 实战 | ops-rollback | 脚本静默跳过不认识的服务名再报成功；入参校验，不认识就拒绝 | 旧事故档案 #6 | PAP-160 |
| ops-solo-ops-restraint | 单人环境的运维克制：拆掉独立通知通道 | 决策 | 实战 | ops-scheduled-watchdog | 告警要有人看才有用；一个人的项目不套生产级运维模板 | 旧站「通知哲学」 | PAP-160 |
| ops-proxy-dial-timeout-as-tls-handshake | 代理拨号超时，被 JVM 报成 TLS 握手被断 | 排查 | 实战 | java-jvm-proxy-no-cidr、http-https-and-certificates | 代理先回 200 再拨上游，拨号超时关隧道；JVM 报握手被断，Gradle 不重试这种异常 | 排查记录（v0.13 后端 CD 失败） | PAP-161 |
| ops-ssh-controlmaster-forward-exits | 开了连接复用，ssh -N -L 转发立刻就退出了 | 坑 | 实战 | web-client-server-and-ports | ControlMaster 把转发交给主连接后退出；要独立转发就关掉这次的复用 | 排查记录（v0.8 收尾测线上容器） | PAP-161 |
| ops-precommit-stashes-unstaged | 提交时 pre-commit 会先把没暂存的改动藏起来 | 坑 | 实战 | ops-commit-conventions | 钩子运行期间没暂存的改动被撤掉再还原，别的进程同时写会冲突或丢失 | 排查记录（v0.8 PAP-63） | PAP-161 |
| ops-zsh-is-not-bash | zsh 不是 bash：三处按 bash 习惯写就错的地方 | 坑 | 实战 | ops-bash32-fullwidth-punctuation | 变量不分词、小写 path 即 PATH、变量后的冒号是修饰符 | 排查记录（v0.13 PAP-115、v0.16 PAP-128） | PAP-161 |

### 1.8 前端（主路线 `frontend`）

| slug | 标题 | 类型 | 难度 | 前置 | 讲什么 | 例子 | 归属 |
|---|---|---|---|---|---|---|---|
| fe-what-makes-a-web-page | 网页由什么组成：HTML、CSS 与 JavaScript | 概念 | 入门 | — | 结构、样式、行为三件套各管什么 | 门户任意一页的源代码 | PAP-160 |
| fe-dom-and-events | 浏览器里的 JavaScript：DOM 与事件 | 概念 | 入门 | fe-what-makes-a-web-page | 页面是一棵树，脚本改树、监听点击和输入 | 门户快速检索框 | PAP-160 |
| fe-typescript-basics | TypeScript：给 JavaScript 加上类型 | 概念 | 基础 | fe-dom-and-events | 类型在写代码时就抓错；严格模式 | `patra-portal/tsconfig.json`；`patra-portal/src/types/` | PAP-160 |
| fe-async-and-fetch | 异步与 fetch：前端怎么调接口 | 概念 | 基础 | fe-typescript-basics、http-request-and-response | Promise 与 async / await；请求失败怎么处理 | `patra-portal/src/lib/gateway.ts` | PAP-160 |
| fe-css-and-tailwind | 样式：CSS 布局与 Tailwind | 概念 | 基础 | fe-what-makes-a-web-page | 盒模型、Flex 与 Grid；工具类写法；设计 token | `patra-portal/src/styles/tokens.css` | PAP-160 |
| fe-react-components-and-state | React：组件、props 与状态 | 概念 | 基础 | fe-typescript-basics | 界面是状态的函数；数据往下传、事件往上报 | 门户的检索与浏览组件 `src/components/portal/` | PAP-160 |
| fe-react-hooks-and-effects | Hook 与副作用 | 概念 | 基础 | fe-react-components-and-state | useState、useEffect、useId；副作用什么时候跑、怎么清理 | `patra-portal/src/components/discussion/MentionSuggest.tsx` | PAP-160 |
| fe-forms-and-validation | 表单与校验 | 概念 | 基础 | fe-react-components-and-state | 前端校验为了体验，后端校验为了安全；字数怎么算 | `patra-portal/src/lib/grapheme.ts` | PAP-160 |
| fe-nextjs-pages-and-routing | Next.js：文件就是路由 | 概念 | 基础 | fe-react-components-and-state | App Router、动态段、布局与加载状态 | `patra-portal/src/app/u/[username]/page.tsx`、`src/app/collections/[id]/page.tsx` | PAP-160 |
| fe-server-and-client-components | 服务端组件与客户端组件 | 概念 | 进阶 | fe-nextjs-pages-and-routing、fe-react-hooks-and-effects | 哪些组件在服务器上渲染、哪些发到浏览器；边界怎么划 | 门户页面里的 `"use client"` 边界 | PAP-160 |
| fe-rendering-static-and-dynamic | 静态生成与动态渲染 | 概念 | 进阶 | fe-server-and-client-components | 构建时生成还是请求时生成；什么让一页变成动态的；预取 | 学习站本身是纯静态站（v0.17 决策 B）；门户的动态页面 | PAP-160 |
| fe-hydration | 水合：服务端 HTML 怎么「活」过来 | 概念 | 进阶 | fe-server-and-client-components | 服务端 HTML 与客户端首次渲染必须一致，不一致会报错 | `patra-portal/src/components/settings/profile/MeshInterestPicker.tsx`（拖拽排序） | PAP-160 |
| fe-server-actions | Server Action：表单直接调服务端 | 概念 | 进阶 | fe-server-and-client-components、fe-forms-and-validation | 不写接口也能提交表单；它适合和不适合做什么；门户在 `onSubmit` 里调用 Server Action，不用 `<form action>` | `patra-portal/src/lib/auth/actions.ts`、`src/components/auth/LoginForm.tsx` | PAP-160 |
| fe-route-handler-bff | Route Handler 透传：门户当中间层 | 概念 | 进阶 | fe-server-and-client-components、http-same-origin-and-cors | 浏览器只跟门户说话，门户带着会话转给网关 | `patra-portal/src/app/api/me/notifications/route.ts`、`src/lib/gateway.ts` | PAP-160 |
| fe-auth-state | 前端怎么管登录态 | 概念 | 进阶 | fe-route-handler-bff、http-cookie-and-session | 会话 Cookie 只在服务端读；入口 `proxy.ts` 只做乐观跳转，要登录的页面自己调 `requireUser` 守门 | `patra-portal/src/lib/auth/session-cookie.ts`、`src/proxy.ts`、`src/lib/auth/require-user.ts` | PAP-160 |
| fe-optimistic-updates | 乐观更新：先改界面再等结果 | 概念 | 进阶 | fe-react-hooks-and-effects、fe-async-and-fetch | 失败时怎么回滚；计数类交互为什么适合乐观更新 | v0.15 决策 D「有帮助乐观更新」；`components/portal/browse/BrowseQueryProvider.tsx` | PAP-160 |
| fe-realtime-eventsource | 前端接收推送：EventSource | 概念 | 进阶 | web-server-push-and-sse、fe-react-hooks-and-effects | 浏览器订阅事件流、断线重连 | `patra-portal/src/lib/notifications/stream.ts`、`src/app/api/me/notifications/stream/route.ts` | PAP-160 |
| fe-rsc-try-catch-swallows-dynamic | try/catch 吞掉了动态渲染信号 | 坑 | 实战 | fe-rendering-static-and-dynamic | 框架靠抛异常标记动态渲染，被 catch 住后只有构建时才暴露 | `unstable_rethrow`：`patra-portal/src/app/u/[username]/page.tsx`；计划缺陷清单 | PAP-161 |
| fe-reauth-needs-route-handler | 再认证必须走 Route Handler | 坑 | 实战 | fe-server-actions、fe-route-handler-bff | 敏感操作前的再认证为什么不能用 Server Action | `patra-portal/src/app/api/auth/reauth/route.ts`；v0.12 总结 | PAP-161 |
| fe-dev-logs-action-args | 开发终端打印出了明文密码 | 坑 | 实战 | fe-server-actions | 开发模式默认记录 Server Action 参数；关掉它 | `patra-portal/next.config.ts` 的 `serverFunctions`；排查记录 | PAP-161 |
| fe-grapheme-and-bytes | 字数要按字素算，还要卡字节 | 坑 | 实战 | fe-forms-and-validation | 一个表情可能是好几个码点；前端按字素数，后端按字节限 | `patra-portal/src/lib/grapheme.ts`；计划缺陷清单 | PAP-161 |
| fe-dnd-kit-needs-useid | 拖拽库不给 id 就水合不一致 | 坑 | 实战 | fe-hydration | 自动生成的 id 在服务端和客户端不同；用 useId | `MeshInterestPicker.tsx`；计划缺陷清单 | PAP-161 |
| fe-combobox-item-press | 组合框点选后没回填 | 坑 | 实战 | fe-react-hooks-and-effects | 无样式组件库的选中事件与输入框回填 | `patra-portal/src/components/settings/profile/MeshInterestPicker.tsx`、`OrganizationPicker.tsx`；计划缺陷清单 | PAP-161 |
| fe-extension-hydration-mismatch | 浏览器扩展改了 `<html>`，开发模式报水合不一致 | 坑 | 实战 | fe-hydration | 差异来自翻译类扩展加的属性，不是代码问题；怎么分辨 | 排查记录 | PAP-161 |
| fe-next15-build-clobbers-dev | Next 15 及以前，next build 会弄坏正在跑的 next dev | 坑 | 实战 | fe-nextjs-pages-and-routing | dev 与 build 共用 `.next`，build 覆盖 dev 正在用的文件；Next 16 起输出分开 | 排查记录（v0.8 PAP-59 升级前后实测） | PAP-161 |

## 2. 路线（按阅读顺序）

一条路线 = 按难度从低到高排好的一串篇目。每条路线下一行是转 YAML 用的 `order` 与 `summary`。跨路线的篇目标了 ↗（主路线另有）。

### 2.1 Web 与 HTTP `web-http`

order 1｜从「打开一个网页时发生了什么」读到网关、限流与服务器推送。

1. web-what-happens-opening-a-page（入门）
2. web-client-server-and-ports（入门）
3. web-url-anatomy（入门）
4. http-request-and-response（基础）
5. http-status-codes（基础）
6. http-json-and-content-type（基础）
7. http-rest-api-design（基础）
8. http-cookie-and-session（基础）
9. http-https-and-certificates（基础）
10. http-same-origin-and-cors（基础）
11. http-caching（基础）
12. web-reverse-proxy-and-gateway（进阶）
13. web-rate-limiting（进阶）
14. web-timeouts-and-downstream-failures（进阶）
15. web-server-push-and-sse（进阶）
16. web-gateway-auth-by-session-lookup（进阶）
17. web-trusted-forwarded-headers（进阶）
18. ops-cloudflare-tunnel（进阶）↗
19. web-gateway-sse-buffered（实战）
20. web-gateway-lb-invalid-scheme（实战）
21. web-security-adds-cache-control（实战）
22. web-no-proxy-matches-ip-literal-only（实战）

### 2.2 数据库与事务 `database`

order 2｜从「数据库和表」读到索引、事务、锁与迁移，再看 Patra 踩过的约束与连接坑。

1. db-what-is-a-database（入门）
2. db-keys-and-relations（入门）
3. db-sql-select（基础）
4. db-sql-join（基础）
5. db-sql-write（基础）
6. db-constraints-and-null（基础）
7. db-indexes（基础）
8. db-transactions（基础）
9. db-connection-pool（基础）
10. db-redis-basics（基础）
11. db-schema-migrations-flyway（进阶）
12. db-isolation-levels（进阶）
13. db-locks（进阶）
14. db-partial-unique-index（进阶）
15. db-jsonb-columns（进阶）
16. db-explain-query-plans（进阶）
17. db-trigram-search（进阶）
18. spring-jpa-basics（进阶）↗
19. spring-jpa-n-plus-one（进阶）↗
20. db-schema-per-subdomain（进阶）
21. db-orphan-connections（实战）
22. db-check-passes-null（实战）
23. db-jsonb-not-null-json-null（实战）
24. db-trigram-short-pattern-full-scan（实战）
25. spring-flush-order-insert-before-delete（实战）↗

### 2.3 Java 与 Spring `java-spring`

order 3｜从「Java 程序怎么跑起来」读到依赖注入、自动配置、JPA、批处理与配置中心。

1. java-how-a-program-runs（入门）
2. java-classes-objects-interfaces（入门）
3. java-build-with-gradle（入门）
4. java-records-and-immutability（基础）
5. java-exceptions（基础）
6. java-collections-and-streams（基础）
7. java-annotations（基础）
8. spring-dependency-injection（基础）
9. spring-boot-auto-configuration（基础）
10. spring-configuration-properties（基础）
11. spring-mvc-controller（基础）
12. spring-json-jackson（基础）
13. spring-proxies-and-transactional（进阶）
14. spring-jpa-basics（进阶）
15. spring-jpa-n-plus-one（进阶）
16. spring-http-interface-clients（进阶）
17. spring-service-discovery（进阶）
18. spring-nacos-config-and-refresh（进阶）
19. spring-security-filter-chain（进阶）
20. spring-batch-jobs（进阶）
21. spring-scheduled-jobs（进阶）
22. spring-nacos-revert-no-refresh（实战）
23. spring-refresh-strict-enum（实战）
24. spring-no-custom-refreshable-properties（实战）
25. spring-json-single-source（实战）
26. spring-batch-context-java-types-only（实战）
27. spring-stepscope-proxy-fields（实战）
28. spring-flush-order-insert-before-delete（实战）
29. java-jvm-proxy-no-cidr（实战）
30. java-ide-and-gradle-share-build-dir（实战）
31. java-gjf-breaks-markdown-doc-comments（实战）
32. spring-nacos-raft-hostname-changed（实战）

### 2.4 消息与事件 `messaging`

order 4｜从「为什么要异步」一直读到发件箱、去重、死信与乱序。

1. msg-why-async（入门）
2. msg-what-is-a-message-queue（基础）
3. msg-exchange-queue-binding（基础）
4. msg-ack-and-redelivery（基础）
5. msg-in-process-events（基础）
6. msg-after-commit-listeners（进阶）
7. msg-dual-write-problem（进阶）
8. msg-transactional-outbox（进阶）
9. msg-publisher-confirms（进阶）
10. msg-at-least-once-and-dedup（进阶）
11. msg-dead-letter（进阶）
12. msg-ordering（进阶）
13. ddd-domain-vs-integration-events（进阶）↗
14. msg-why-rabbitmq（实战）
15. msg-recipient-lookup-two-paths（实战）
16. msg-mail-idempotency-key（实战）
17. ddd-extract-notification-service（实战）↗

### 2.5 DDD 与六边形 `ddd-hexagonal`

order 5｜从「为什么代码要分层」读到聚合、端口与适配器、子域与限界上下文。

1. ddd-why-layer-code（入门）
2. ddd-rules-vs-plumbing（入门）
3. ddd-entities-and-value-objects（基础）
4. ddd-aggregates（基础）
5. ddd-repositories（基础）
6. ddd-domain-exceptions（基础）
7. ddd-application-layer（基础）
8. ddd-ports-and-adapters（进阶）
9. ddd-patra-six-modules（进阶）
10. ddd-architecture-guards（进阶）
11. ddd-cqrs-read-side（进阶）
12. ddd-subdomains（进阶）
13. ddd-bounded-contexts（进阶）
14. ddd-domain-vs-integration-events（进阶）
15. ddd-translate-remote-errors（进阶）
16. ddd-username-only-in-identity（实战）
17. ddd-session-owned-by-identity（实战）
18. ddd-extract-notification-service（实战）
19. ddd-subdomain-first-layout（实战）
20. ddd-tool-modules-by-capability（实战）
21. ddd-remove-unused-service（实战）

### 2.6 测试 `testing`

order 6｜从「为什么要写测试」读到 Testcontainers、端到端测试与偶发失败。

1. test-why-write-tests（入门）
2. test-unit-tests（基础）
3. test-doubles-and-mocks（基础）
4. test-tdd（基础）
5. test-controlling-time（基础）
6. test-kinds-and-pyramid（基础）
7. test-frontend-unit（基础）
8. test-integration-testcontainers（进阶）
9. test-spring-slice-tests（进阶）
10. test-http-stubs-wiremock（进阶）
11. test-e2e-playwright（进阶）
12. test-coverage（进阶）
13. test-flaky-tests（进阶）
14. test-hikari-too-many-clients-in-ci（实战）
15. test-first-case-timeout（实战）
16. test-high-load-timeouts（实战）
17. test-redis-ttl-real-time（实战）
18. test-resttestclient-retries-429（实战）
19. test-e2e-wait-for-hydration（实战）
20. test-route-mocking-font-flash（实战）

### 2.7 部署与 CI/CD `deploy-cicd`

order 7｜从「代码怎么变成线上服务」读到 CI 流水线、CD 部署、回滚与公网隧道。

1. ops-code-to-production（入门）
2. ops-git-basics（入门）
3. ops-commit-conventions（基础）
4. ops-pull-requests（基础）
5. ops-secrets-out-of-repo（基础）
6. ops-docker-images-and-containers（基础）
7. ops-docker-compose（基础）
8. ops-cpu-architectures（基础）
9. ops-ci-pipeline（进阶）
10. ops-ci-changed-only（进阶）
11. ops-ci-parallel-and-required-check（进阶）
12. ops-ci-security（进阶）
13. ops-self-hosted-runner（进阶）
14. ops-cd-deploy-loop（进阶）
15. ops-health-checks（进阶）
16. ops-rollback（进阶）
17. ops-scheduled-watchdog（进阶）
18. ops-config-as-code（进阶）
19. ops-cloudflare-tunnel（进阶）
20. ops-native-arm64-build（实战）
21. ops-wrong-arch-image（实战）
22. ops-runner-deregistered（实战）
23. ops-timeout-shown-as-cancelled（实战）
24. ops-localhost-resolves-to-ipv6（实战）
25. ops-bash32-fullwidth-punctuation（实战）
26. ops-typo-reports-success（实战）
27. ops-solo-ops-restraint（实战）
28. ops-proxy-dial-timeout-as-tls-handshake（实战）
29. ops-ssh-controlmaster-forward-exits（实战）
30. ops-precommit-stashes-unstaged（实战）
31. ops-zsh-is-not-bash（实战）

### 2.8 前端 `frontend`

order 8｜从「网页由什么组成」读到服务端组件、水合与 Route Handler 透传。

1. fe-what-makes-a-web-page（入门）
2. fe-dom-and-events（入门）
3. fe-typescript-basics（基础）
4. fe-async-and-fetch（基础）
5. fe-css-and-tailwind（基础）
6. fe-react-components-and-state（基础）
7. fe-react-hooks-and-effects（基础）
8. fe-forms-and-validation（基础）
9. fe-nextjs-pages-and-routing（基础）
10. test-frontend-unit（基础）↗
11. fe-server-and-client-components（进阶）
12. fe-rendering-static-and-dynamic（进阶）
13. fe-hydration（进阶）
14. fe-server-actions（进阶）
15. fe-route-handler-bff（进阶）
16. fe-auth-state（进阶）
17. fe-optimistic-updates（进阶）
18. fe-realtime-eventsource（进阶）
19. test-e2e-playwright（进阶）↗
20. fe-rsc-try-catch-swallows-dynamic（实战）
21. fe-reauth-needs-route-handler（实战）
22. fe-dev-logs-action-args（实战）
23. fe-grapheme-and-bytes（实战）
24. fe-dnd-kit-needs-useid（实战）
25. fe-combobox-item-press（实战）
26. fe-extension-hydration-mismatch（实战）
27. test-e2e-wait-for-hydration（实战）↗
28. fe-next15-build-clobbers-dev（实战）

### 2.9 主题

主题把同一件事在不同版本里的条目串成一条演进线（PAP-155 格式：`content/topics/<slug>.yaml` 只放标题与简介，成员由条目 frontmatter 的 `topics` 决定）。下表的「成员」是写作时要在条目里挂上的主题；挂了主题的条目必须写带版本的 `sources`，概念篇也一样。

| 主题 slug | 标题 | 成员（写作时在条目 `topics` 里挂上） | 演进线 |
|---|---|---|---|
| config-refresh | 配置中心与动态刷新 | spring-nacos-config-and-refresh、ops-config-as-code、spring-no-custom-refreshable-properties、spring-nacos-revert-no-refresh、spring-refresh-strict-enum | v0.9 建配置中心 → 自研刷新组件被取消 → 刷新失败的两个坑 |
| login-session | 登录态放在哪、谁来认人 | fe-auth-state、web-trusted-forwarded-headers、fe-reauth-needs-route-handler、web-gateway-auth-by-session-lookup、ddd-session-owned-by-identity | v0.8 账号 → v0.12 用户中心与再认证 → 会话查询改为 identity 独占 |
| service-boundaries | 服务和模块的边界怎么划 | ddd-tool-modules-by-capability、ddd-username-only-in-identity、db-schema-per-subdomain、ddd-subdomain-first-layout、ddd-extract-notification-service、ddd-remove-unused-service | v0.10 工具模块重组 → v0.12 用户名只存一处 → v0.13 新建 interaction → 子域优先分包 → v0.16 拆出通知服务、删除 ingest |
| event-delivery | 事件怎么可靠送到 | msg-in-process-events、msg-transactional-outbox、msg-at-least-once-and-dedup、msg-why-rabbitmq、msg-recipient-lookup-two-paths、msg-mail-idempotency-key | v0.14 进程内事件联动 → v0.16 发件箱、RabbitMQ、去重与幂等 |
| realtime-push | 从轮询到实时推送 | web-server-push-and-sse、web-gateway-sse-buffered、fe-realtime-eventsource | v0.14 轮询取未读数 → v0.16 SSE 推送与网关攒流排查 |

## 3. 不进路线的条目

按决策 E，「个人环境」条目（`personalEnv: true`）不进路线、不进首页推荐，只在标签页可见，也不受第 5 节的路线检查约束。判定标准按维护技能：读者要装上作者同款工具组合（代理软件、出站防火墙、OrbStack、Tailscale 与 TUN 共存）才会碰到的，标个人环境；机制本身通用的（如 JVM 的代理例外列表不认 CIDR、shell 的 no_proxy 只匹配 IP 字面量）写成普通条目进路线，见第 1 节各表归属 PAP-161 的行。

PAP-161 盘点 Agent 记忆里的排查记录后写了下面 5 篇（2026-10-31）：

| slug | 标题 | 类型 | 难度 | 前置 | 讲什么 | 例子 | 归属 |
|---|---|---|---|---|---|---|---|
| env-fake-ip-hijacks-tailnet-names | 代理软件把内网主机名解析成假地址，每次打开都卡 6 秒 | 排查 | 实战 | web-what-happens-opening-a-page、web-reverse-proxy-and-gateway | fake-ip 接管 DNS，VPN 主机名得到假地址，经代理等超时才回退真地址；三条 curl 对照 | 排查记录 | PAP-161 |
| env-firewall-blocks-app-dns | 按进程拦截的防火墙，让一个应用里的网页只剩 HTML | 排查 | 实战 | web-what-happens-opening-a-page | 出站防火墙对 Claude 进程全部拦截，内置浏览器的 DNS 预检被丢；终端正常不代表别的进程正常 | 排查记录 | PAP-161 |
| env-orbstack-background-no-docker-sock | SSH 上去启动 OrbStack，CD 却连不上 docker | 坑 | 实战 | ops-docker-images-and-containers、ops-self-hosted-runner | 后台模式启动不建 `/var/run/docker.sock`；自定义 `DOCKER_CONFIG` 没有 context，只能连默认 socket | 排查记录 | PAP-161 |
| env-proxy-stale-state-looks-like-port-block | 代理改完配置后的旧状态，伪装成了「按端口拦截」 | 排查 | 实战 | web-client-server-and-ports、env-fake-ip-hijacks-tailnet-names | 旧连接与假地址缓存要几小时才过期；三视角对照、新端口对照实验 | 排查记录 | PAP-161 |
| env-vpn-route-taken-by-proxy-tun | ping 只要 0.5 毫秒：VPN 的路由被代理的虚拟网卡抢走了 | 排查 | 实战 | web-client-server-and-ports、env-fake-ip-hijacks-tailnet-names | 绑定网卡的路由普通流量查不到，落进 TUN 的大网段；看延迟判断包出没出本机 | 排查记录 | PAP-161 |

## 4. 旧学习站内容的去处

旧站（`patra-learn/src/content/`）由 PAP-155 整体删除（决策 H），内容按下表改写进新篇目。

### 4.1 地铁图 13 站

| 旧站 | 去处 | 说明 |
|---|---|---|
| 1 号线 · 写代码 `l1/write-code` | ops-git-basics、ops-commit-conventions | 「main 不能直接改」「开分支」「push」进 Git 基础篇；「本地钩子是第一道考场」进提交规范篇 |
| 1 号线 · 开 PR `l1/open-pr` | ops-pull-requests | 「闸机的两把锁」「squash merge」照搬；「AI 评审员」一节按现状改写（Codex 已于 2026-10-20 断开，现为 CodeRabbit + Claude Action） |
| 1 号线 · 只考改过的 `l1/changed-only` | ops-ci-changed-only | 「三种开考方式」「分类器」「docs-only 免考」全部进这一篇，以现在的 `detect-changes.sh` 与 `module-graph.json` 为准 |
| 1 号线 · 并行考试 `l1/parallel-exams` | ops-ci-parallel-and-required-check | 「preflight」「backend 矩阵」「required-check 总闸灯」进这一篇；现在多了 learn 与 coverage 任务，按 `ci.yml` 现状改写 |
| 2 号线 · mini 领任务 `l2/runner-picks-up` | ops-self-hosted-runner、ops-ci-security | 「领活方式」「常驻环境：.env、.path 与禁自更新」进 runner 篇；「陌生代码上不了这台机器」进流水线安全篇 |
| 2 号线 · 本机打包 `l2/native-build` | ops-wrong-arch-image、ops-native-arm64-build、ops-docker-images-and-containers | 「三个月的芯片格式事故」进排查篇；「打包的机器就是上线的机器」「GHCR 降级为备份」进决策篇；「bootJar 装进分层镜像」「portal 整条流水线装进 Dockerfile」进 Docker 镜像篇；芯片架构的基础知识进 ops-cpu-architectures |
| 2 号线 · 部署闭环 `l2/deploy-loop` | ops-cd-deploy-loop、ops-typo-reports-success、ops-wrong-arch-image | 「按依赖顺序换新」「last-good 是服务级的」进部署闭环篇；「先验票：入参校验」进打错字的坑；「镜像就位顺手验格式」进芯片格式排查篇 |
| 2 号线 · 健康检查 `l2/health-check` | ops-health-checks、ops-localhost-resolves-to-ipv6 | 三道检查（容器体检表、deploy.sh 门外追问、对版验证防无声掉包）进健康检查篇；「localhost 的背叛」单独成坑 |
| 2 号线 · 上线与回滚 `l2/ship-and-rollback` | ops-rollback、ops-typo-reports-success | 「验货失败自动翻小本本」「手动回滚两分钟」进回滚篇；文中的事故档案 #6 进打错字的坑 |
| 3 号线 · 每天 07:00 `l3/daily-0700` | ops-scheduled-watchdog | 「这条线防的不是代码坏」「cron、UTC 与北京时间」「拆成两个 job：不能让病人给自己量体温」都进巡检篇 |
| 3 号线 · 查岗与钥匙 `l3/roll-call-and-key` | ops-scheduled-watchdog（查岗）、ops-secrets-out-of-repo（钥匙）、ops-runner-deregistered（档案 #2） | 钥匙只讲「巡检用一把只读权限的令牌、放在 secrets 里」，不写令牌名以外的细节 |
| 3 号线 · 四项体检 `l3/four-checks` | ops-scheduled-watchdog、ops-bash32-fullwidth-punctuation | 「金丝雀：在岗不等于能干活」「四连检」进巡检篇；文中的档案 #5 单独成坑 |
| 3 号线 · 通知哲学 `l3/notification-philosophy` | ops-solo-ops-restraint、ops-code-to-production | 「谁来喊你」「专线后来拆了」「运维克制」进决策篇；「终点站：回望三条线」的全景回顾并进入门总览篇 |
| 换乘节点「合并进 main」 | ops-pull-requests | 只是视觉连接，没有文章 |
| 4 号线 · 架构线（规划中：六边形架构、DDD 与限界上下文、微服务拓扑） | ddd-ports-and-adapters、ddd-bounded-contexts、ddd-patra-six-modules | 占位站没有正文，由 DDD 与六边形路线整条接替 |
| 5 号线 · 数据线（规划中：采集管道、存储与索引） | spring-batch-jobs（采集管道）、db-indexes 与 db-trigram-search（存储与索引） | 占位站没有正文；8 条路线里没有「数据采集」路线，见第 6 节问题 2 |

### 4.2 术语表（10 条）

术语表页不保留：每个术语在定义它的篇目里讲，站内由标签与搜索找到。

| 术语 | 去处 |
|---|---|
| GitHub Actions | ops-ci-pipeline |
| workflow | ops-ci-pipeline |
| runner | ops-ci-pipeline、ops-self-hosted-runner |
| Docker 镜像 | ops-docker-images-and-containers |
| GHCR | ops-docker-images-and-containers（镜像仓库）、ops-native-arm64-build（只当备份） |
| docker compose | ops-docker-compose |
| secrets | ops-secrets-out-of-repo |
| 分支保护 | ops-pull-requests |
| squash merge | ops-pull-requests |
| workflow_dispatch | ops-rollback、ops-ci-security |

### 4.3 事故档案（6 条）

| 事故 | 去处 |
|---|---|
| #1 芯片格式装错 | ops-wrong-arch-image |
| #2 演员失踪（runner 被除名） | ops-runner-deregistered |
| #3 20 分钟魔咒（超时显示已取消） | ops-timeout-shown-as-cancelled |
| #4 localhost 的背叛 | ops-localhost-resolves-to-ipv6 |
| #5 全角逗号刺客 | ops-bash32-fullwidth-punctuation |
| #6 打错字的假成功 | ops-typo-reports-success |

### 4.4 操作卡（3 张）与红线（2 条）

| 旧内容 | 去处 | 说明 |
|---|---|---|
| 操作卡「回滚」 | ops-rollback | 命令格式作为例子保留，旧版本号写成占位 |
| 操作卡「巡检」 | ops-scheduled-watchdog | 讲「可以手动触发巡检」这个机制 |
| 操作卡「升级」 | ops-runner-deregistered | 只讲安装脚本即运维手册；带令牌参数的具体命令只对作者有用，不保留 |
| 红线「有任务时不要重启 runner」 | ops-self-hosted-runner | 作为「重启会杀掉执行中的任务」的知识点，并在 ops-timeout-shown-as-cancelled 里呼应 |
| 红线「不要删巡检令牌」 | 不保留 | 只对仓库维护者本人有用的操作提醒；令牌的作用已在 ops-scheduled-watchdog 讲 |

## 5. 分工与自检

### 5.1 分工汇总

（由第 1 节统计，篇目数按难度分布；PAP-161 收尾时按记忆里的排查记录加了 10 篇路线条目，已计入。第 3 节的 5 篇个人环境条目不进路线，不计入下表）

| 写作 Issue | 负责路线 | 篇数 | 入门 | 基础 | 进阶 | 实战 |
|---|---|---|---|---|---|---|
| PAP-158 | Web 与 HTTP、数据库与事务、Java 与 Spring | 53 | 8 | 25 | 20 | 0 |
| PAP-159 | 消息与事件、DDD 与六边形 | 27 | 3 | 9 | 15 | 0 |
| PAP-160 | 测试、部署与 CI/CD（含旧课程改写）、前端 | 57 | 5 | 19 | 25 | 8 |
| PAP-161 | 由已有材料转写的决策 / 排查 / 坑（分布在 8 条路线） | 50 | 0 | 0 | 3 | 47 |
| **合计** | | **187** | 16 | 53 | 63 | 55 |

类型分布：概念 128、坑 34、决策 16、排查 9。PAP-160 的 8 篇实战全部来自旧课程（6 个旧事故 + 本机打包决策 + 通知哲学决策）。

| 路线 | 篇数 | 入门 | 基础 | 进阶 | 实战 |
|---|---|---|---|---|---|
| Web 与 HTTP | 22 | 3 | 8 | 7 | 4 |
| 数据库与事务 | 25 | 2 | 8 | 10 | 5 |
| Java 与 Spring | 32 | 3 | 9 | 9 | 11 |
| 消息与事件 | 17 | 1 | 4 | 8 | 4 |
| DDD 与六边形 | 21 | 2 | 5 | 8 | 6 |
| 测试 | 20 | 1 | 6 | 6 | 7 |
| 部署与 CI/CD | 31 | 2 | 6 | 11 | 12 |
| 前端 | 28 | 2 | 8 | 9 | 9 |

跨路线篇目 9 篇（第 2 节标 ↗），只由归属 Issue 写一次。

### 5.2 PAP-158 与 PAP-160 的写作分组

两个 Issue 按主路线切成 5 组，每组一个写作会话。PAP-159（27 篇）与 PAP-161（50 篇，另有个人环境 5 篇）不再分组。

| 组 | 组名 | 篇数 | 篇目（按总表顺序） |
|---|---|---|---|
| PAP-158-web | Web 与 HTTP | 15 | web-what-happens-opening-a-page、web-client-server-and-ports、web-url-anatomy、http-request-and-response、http-status-codes、http-json-and-content-type、http-rest-api-design、http-cookie-and-session、http-https-and-certificates、http-same-origin-and-cors、http-caching、web-reverse-proxy-and-gateway、web-rate-limiting、web-timeouts-and-downstream-failures、web-server-push-and-sse |
| PAP-158-db | 数据库与事务 | 17 | db-what-is-a-database、db-keys-and-relations、db-sql-select、db-sql-join、db-sql-write、db-constraints-and-null、db-indexes、db-transactions、db-connection-pool、db-redis-basics、db-schema-migrations-flyway、db-isolation-levels、db-locks、db-partial-unique-index、db-jsonb-columns、db-explain-query-plans、db-trigram-search |
| PAP-158-java | Java 与 Spring | 21 | java-how-a-program-runs、java-classes-objects-interfaces、java-build-with-gradle、java-records-and-immutability、java-exceptions、java-collections-and-streams、java-annotations、spring-dependency-injection、spring-boot-auto-configuration、spring-configuration-properties、spring-mvc-controller、spring-json-jackson、spring-proxies-and-transactional、spring-jpa-basics、spring-jpa-n-plus-one、spring-http-interface-clients、spring-service-discovery、spring-nacos-config-and-refresh、spring-security-filter-chain、spring-batch-jobs、spring-scheduled-jobs |
| PAP-160-ops | 部署与 CI/CD | 27 | ops-code-to-production、ops-git-basics、ops-commit-conventions、ops-pull-requests、ops-secrets-out-of-repo、ops-docker-images-and-containers、ops-docker-compose、ops-cpu-architectures、ops-ci-pipeline、ops-ci-changed-only、ops-ci-parallel-and-required-check、ops-ci-security、ops-self-hosted-runner、ops-cd-deploy-loop、ops-health-checks、ops-rollback、ops-scheduled-watchdog、ops-config-as-code、ops-cloudflare-tunnel、ops-native-arm64-build、ops-wrong-arch-image、ops-runner-deregistered、ops-timeout-shown-as-cancelled、ops-localhost-resolves-to-ipv6、ops-bash32-fullwidth-punctuation、ops-typo-reports-success、ops-solo-ops-restraint |
| PAP-160-fe-test | 前端与测试 | 30 | test-why-write-tests、test-unit-tests、test-doubles-and-mocks、test-tdd、test-controlling-time、test-kinds-and-pyramid、test-frontend-unit、test-integration-testcontainers、test-spring-slice-tests、test-http-stubs-wiremock、test-e2e-playwright、test-coverage、test-flaky-tests、fe-what-makes-a-web-page、fe-dom-and-events、fe-typescript-basics、fe-async-and-fetch、fe-css-and-tailwind、fe-react-components-and-state、fe-react-hooks-and-effects、fe-forms-and-validation、fe-nextjs-pages-and-routing、fe-server-and-client-components、fe-rendering-static-and-dynamic、fe-hydration、fe-server-actions、fe-route-handler-bff、fe-auth-state、fe-optimistic-updates、fe-realtime-eventsource |

组与组之间的前置（写的时候引用对方的 slug 即可，同一个 PR 里合入）：

- PAP-160 两组之间没有交叉前置：测试路线里依赖前端的两篇（test-frontend-unit、test-e2e-playwright）与前端放在同一组。
- PAP-158 三组之间有 8 处交叉前置，方向都是单向的：Java 与 Spring 组引用 Web 组的 http-rest-api-design、http-json-and-content-type、http-status-codes、http-cookie-and-session，引用数据库组的 db-transactions、db-keys-and-relations；Web 组的 web-rate-limiting 引用数据库组的 db-redis-basics。三组之间不成环，可以同时开写。

### 5.3 自检

用脚本对第 1、2 节逐项检查（2026-10-31，结果：全部通过；表中「177 篇」是当时的篇数。PAP-161 收尾时加的 10 篇路线条目与 5 篇个人环境条目，由 `pnpm test` 的跨篇规则检查，同日全绿）：

| 检查 | 结果 |
|---|---|
| slug 全局唯一；类型、难度、归属取值合法 | 通过（177 篇） |
| 前置全部存在，且前置的难度不高于本篇 | 通过 |
| 前置关系全局无环 | 通过 |
| 每条路线内难度单调不降（入门 → 基础 → 进阶 → 实战） | 通过（8 条） |
| 每条路线内，同路线的前置都排在本篇之前（顺着读不会碰到没读过的前置） | 通过 |
| 每条路线首篇没有前置 | 通过 |
| 每篇都在自己的主路线里；没有不进任何路线的篇目 | 通过 |
| 路线列表里标的难度与总表一致 | 通过 |
| 概念篇归主路线的写作 Issue；实战档没有概念篇 | 通过 |
| 每篇恰好归属一个写作 Issue | 通过 |
| 例子列里的仓库路径在 `cc2956951` 存在 | 通过 |
| 无 tailnet IP、tailnet 主机名、局域网地址 | 通过 |

跨路线的前置（如「限流」要先读数据库路线的「Redis」、「进程内事件」要先读 Java 路线的「依赖注入」）不要求出现在同一路线里，按第 0 节「挂多条路线」的规则由条目页的「先读这几篇」引导。

## 6. 已定事项（2026-10-31 用户确认，原话「都按建议」）

1. 写作归属按第 0.1 节：概念篇按主路线归 PAP-158 / 159 / 160，旧 CI/CD 课程改写的归 PAP-160，由已有材料转写的决策 / 排查 / 坑归 PAP-161。
2. 不加「数据采集」路线：采集并进 spring-batch-jobs，存储与索引并进数据库路线。
3. 接受 177 篇；PAP-158、PAP-160 按第 5.2 节分组并行写。
4. 标签在 PAP-155 初始清单之外加 `security`、`redis`、`git`、`docker`、`ci-cd`。
5. 本文件留在 `content/` 根目录，内容加载器不读它；路线以 `content/routes/*.yaml` 为准，本文件第 2 节与 YAML 不一致时改本文件。
