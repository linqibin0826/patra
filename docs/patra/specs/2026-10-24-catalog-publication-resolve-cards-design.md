# catalog 文献批量解析与批量卡片接口工程设计（PAP-109）

> **Issue**：[PAP-109](https://linear.app/papertrace/issue/PAP-109)（GitHub #279）
> **版本**：[v0.13 Library](../release-specs/v0.13-library.md)（2.1.1 文献失效、决策 C、决策 H）
> **前置设计**：[网关鉴权（PAP-65）](2026-10-09-gateway-auth-design.md)（`/*/_internal/**` 拒绝名单）、[catalog 机构与 MeSH 建议（PAP-94）](2026-10-21-catalog-org-mesh-suggest-design.md)（内部接口与 `patra-catalog-api` 的写法）
> **下游**：PAP-112（interaction 对象登记表：首次收藏时解析、每日对账）、PAP-114（门户文献库页取卡片）
> **日期**：2026-10-24
> **状态**：已实施（2026-10-24，分支 `claude/v013-catalog-b41dff`，基于 `feat/v0.13-library-api`）；Codex 评审通过；第 5.6 节空元素状态码与第 9 节执行计划已按实测回填；部署后经网关的 curl 核对待合入后补做

## 1. 要解决的问题

v0.13 的 interaction 服务给每个被互动的文献建一行登记，身份锚是 `pmid:…`，catalog 内部 ID 只是可重新解析的缓存（决策 C）。它需要 catalog 回答两类问题：

1. 首次收藏时：门户 URL 里的 catalog 内部 ID 对应哪篇文献？存在吗？被软删了吗？PMID 是多少？标题、期刊、年份是什么（登记表的快照）？不存在就拒绝收藏。
2. 每日对账时：这一批 PMID 现在对应哪些内部 ID？状态如何？快照是否变了？

门户的文献库与收藏夹页要按一批文献 ID 显示卡片（决策 H：一屏只发一次批量请求），字段与首页、检索结果的卡片一致。

catalog 现在没有任何按 ID 批量查询的接口。另外门户响应里的 `bookmarks` 写死为 0，本版不显示任何收藏数（2.1.1「公开计数」），要删掉。

## 2. 范围

做：

- 内部接口 `POST /_internal/publications/resolve/by-ids`、`POST /_internal/publications/resolve/by-pmids`（给 interaction；网关拒绝外部访问），契约放进 `patra-catalog-api`。
- 门户接口 `GET /portal/publications/batch`（公开，经网关给门户）。
- 读端：读模型、读端口方法、查询服务、Dao native SQL、控制器与内部端点实现。
- 删除 `PortalPaperResponse.bookmarks` 与 `PortalPublicationDetailResponse.bookmarks`。
- 网关集成测试补一条拒绝路径与一条匿名放行路径（规则本身不改）。
- catalog README。

不做：

- 新索引与 Flyway 迁移：主键覆盖按 ID 查，`uk_pmid` 唯一约束覆盖按 PMID 查（第 9 节）。Issue To-Do「读侧 SQL 与索引」按此关闭。
- 门户的 TS 类型与「收藏」事实格：归 PAP-114。门户 `PaperFacts.tsx` 读的是 `paper.bookmarks ?? 0`，后端先删不会让页面报错。
- 出版日期精度、页码、ISO 缩写：归 PAP-110。本 Issue 两个接口的年份都取 `cat_publication.publication_year`，精度改造后仍然有效，不为此预留字段。门户文献库要按精度显示出版日期（PAP-107 设计简报：`2019` / `2019 年 3 月` / `2019 年 3 月 15 日`），所以 PAP-110 会给 `PortalPaperResponse` 加带精度的出版日期字段，本接口复用该响应，届时自动带上。
- interaction 侧的客户端装配、PMID 缺失时的处理：归 PAP-112（第 12 节交接）。

## 3. 已定的前提

| 前提 | 来源 |
|---|---|
| 互动数据只引用本地 `subject_id`；登记表的 `natural_key` 是 `pmid:…`，`source_id` 缓存 catalog 内部 ID；`state` 为 ACTIVE / UNAVAILABLE（catalog 软删，可复活）；`snapshot` 存标题、期刊、年份 | release spec 决策 C |
| 首次互动时同步调 catalog 解析；catalog 不可用或对象不存在则拒绝，不建登记行；每日对账按 PMID 批量回查 | 决策 C、决策 G |
| 文献被软删时文献库条目保留，显示「已失效」和收藏时记下的标题；恢复后正常显示 | 2.1.1 |
| 公共数据（文献卡片）不带会话；一屏一次批量请求 | 决策 H |
| 解析接口按内部 ID 或 PMID，≤ 100；返回 exists、state（ACTIVE / DELETED）、pmid、内部 ID、标题、期刊名、年份；软删返回 DELETED 而不是「不存在」 | PAP-109 To-Do |
| 卡片接口按 ID，≤ 100，字段与门户 `PaperCard` 一致 | PAP-109 To-Do |
| catalog 的软删是 `cat_publication.deleted_at`：`PubmedDeleteCitationTasklet` 按 PMID 置删除时间，同一 PMID 再次出现在更新文件里时 `PublicationSyncRepositoryAdapter` 把它清空（复活）。内部 ID 在复活时不变 | 现状代码 |
| `/*/_internal/**` 在网关第一条过滤器链上对任何人 403；catalog 其余路径（含 `/portal/**`）匿名放行 | `GatewaySecurityConfiguration.BLOCKED_PATHS` |
| 服务间调用走 `lb://patra-catalog` + `@HttpExchange` 代理，不经网关 | PAP-94 第 14 节 |
| MVC 响应里的包装类型 `Long` 以字符串输出（`LongToStringHttpMessageConvertersCustomizer`）；服务间请求体与客户端容器 `JsonMapper` 里的 `Long` 仍是数字 | `patra-tool-web` |
| 适配器入口用 Bean Validation，非法参数 422，ProblemDetail 由 `patra-tool-web` 输出 | PAP-94 第 8.5 节 |

## 4. 模块与改动面

| 层 | 新增 / 修改 |
|---|---|
| `patra-catalog-api` | 新增 `PublicationEndpoint`（`@HttpExchange`）、`PublicationResolveByIdsReq`、`PublicationResolveByPmidsReq`、`PublicationResolveResp`、`PublicationResolutionResp`、枚举 `PublicationStateResp` |
| domain | 新增读模型 `PublicationResolveReadModel`（`model/read/publication/`）、读端口 `PublicationResolveReadPort`（`port/read/`）；`PublicationSearchReadPort` 加 `findCardsByIds` |
| app | 新增 `PublicationResolveQueryService`（`usecase/publication/query/`）与结果 `PublicationResolution`（`…/query/dto/`）；新增 `PortalPublicationCardQueryService`（`usecase/portal/query/`） |
| infra | 新增 `PublicationResolveReadAdapter`（`adapter/read/`）、投影 `PublicationResolveRow`、Dao `PublicationResolveDao`；`PublicationSearchDao` 加 `findCardsByIds`，`PublicationSearchReadAdapter` 实现 `findCardsByIds` |
| adapter | 新增 `PublicationEndpointImpl`（`rest/internal/`）、`PortalPublicationBatchRequest`、`PortalPaperBatchResponse`；`PortalPublicationController` 加 `batch`；`PortalApiConverter` 去掉两处 `bookmarks`；两个响应 record 删字段 |
| gateway | `GatewayBlockedPathsIT`、`GatewayRoutingIT` 各补一条 |
| 文档 | catalog README |

命名照 `.claude/rules/tech/port-service.md`：CQRS 读端 `{Entity}ReadPort` / `{Entity}ReadAdapter`，查询服务无接口。

## 5. 内部批量解析接口

### 5.1 为什么拆成两个端点

interaction 的两种调用彼此独立：首次收藏只有内部 ID，对账只有 PMID，从不混在一次请求里。拆成两个端点，每个请求体只有一个列表，校验与客户端调用都最简单；不用在一个请求里处理「两个列表都空 / 都非空 / 同一篇既按 ID 又按 PMID 出现」这些组合。

### 5.2 契约

```
POST /_internal/publications/resolve/by-ids
Content-Type: application/json

{ "ids": [38123456789012345, 38123456789012999, 1] }
```

```
POST /_internal/publications/resolve/by-pmids
Content-Type: application/json

{ "pmids": ["38001234", "12345"] }
```

响应（两个端点同形）：

```json
{
  "items": [
    {
      "key": "38123456789012345",
      "exists": true,
      "state": "ACTIVE",
      "id": "38123456789012345",
      "pmid": "38001234",
      "title": "Empagliflozin in heart failure with a preserved ejection fraction",
      "journal": "The New England journal of medicine",
      "year": 2021
    },
    {
      "key": "38123456789012999",
      "exists": true,
      "state": "DELETED",
      "id": "38123456789012999",
      "pmid": "37000001",
      "title": "Retracted duplicate record",
      "journal": "Lancet",
      "year": 2019
    },
    {
      "key": "1",
      "exists": false,
      "state": null,
      "id": null,
      "pmid": null,
      "title": null,
      "journal": null,
      "year": null
    }
  ]
}
```

规则：

| 项 | 规则 |
|---|---|
| 请求规模 | 列表 1 ~ 100 个元素；空、缺失或超过 100 → 422 |
| 元素校验 | `ids` 每个为正整数；`pmids` 每个匹配 `^[1-9][0-9]{0,14}$`（`cat_publication.pmid` 是 `VARCHAR(15)`）。任一元素不合规 → 整个请求 422，不做部分成功 |
| 去重与顺序 | 按请求顺序输出，重复的键只输出第一次出现的那一行 |
| 每个键一行 | 查得到 → `exists: true`，`state` 由 `deleted_at` 决定；查不到 → `exists: false`，其余字段全为 null |
| `key` | 请求里的键原样回传，统一为字符串（按 ID 时是 ID 的十进制字符串） |
| `state` | `ACTIVE`（`deleted_at IS NULL`）/ `DELETED`（`deleted_at IS NOT NULL`）。软删的文献照样带标题、期刊、年份，interaction 用它们刷新「已失效」快照 |
| `id` | catalog 内部 ID。按 PMID 解析时，interaction 用它刷新 `source_id`（换库重导后变化） |
| `pmid` | 库里的值；为 null 时照实返回 null，不推断。当前 catalog 只有 PubMed 来源，实际都非空 |
| `title` | `cat_publication.title` 原文，含内联标记（`<i>`、`<sub>` 等白名单标签），与门户其他接口的 `title` 一致；纯文本化由展示方用同一套规则处理 |
| `journal` | `cat_venue.title`（期刊全称），没有载体时 null。与门户卡片的 `journal` 同源 |
| `year` | `cat_publication.publication_year`，可空 |

`id` 字段在 Java 契约里是 `Long`，经 catalog 的 MVC 转换器输出为 JSON 字符串；interaction 用 `RestClient` 反序列化时 Jackson 默认允许字符串转数字（`MapperFeature.ALLOW_COERCION_OF_SCALARS` 默认开），拿回 `Long`。仓库里已有同样的先例在 mini 上运行：identity 的 `SessionIntrospectionResp.userId` / `sessionId` 是 `Long`，经同一个转换器输出为字符串，网关用 `RestClient` 反序列化回 `Long`（PAP-106）。`key` 统一用 `String`，避免按 ID / 按 PMID 两种类型。第 10 节有一条往返测试守住这个约定。

### 5.3 `patra-catalog-api`

```java
@HttpExchange(
    url = "/_internal/publications",
    accept = "application/json",
    contentType = "application/json")
public interface PublicationEndpoint {

  @PostExchange("/resolve/by-ids")
  PublicationResolveResp resolveByIds(@Valid @RequestBody PublicationResolveByIdsReq request);

  @PostExchange("/resolve/by-pmids")
  PublicationResolveResp resolveByPmids(@Valid @RequestBody PublicationResolveByPmidsReq request);
}

/// 1 个字段，of()
public record PublicationResolveByIdsReq(
    @NotEmpty @Size(max = 100) List<@Positive Long> ids) {}

/// 1 个字段，of()
public record PublicationResolveByPmidsReq(
    @NotEmpty @Size(max = 100) List<@Pattern(regexp = "^[1-9][0-9]{0,14}$") String> pmids) {}

/// 1 个字段，of()
public record PublicationResolveResp(List<PublicationResolutionResp> items) {}

/// 8 个字段，@Builder；另有静态 missing(String key)
public record PublicationResolutionResp(
    String key, boolean exists, PublicationStateResp state,
    Long id, String pmid, String title, String journal, Integer year) {}

public enum PublicationStateResp { ACTIVE, DELETED }
```

防御性拷贝：三个带列表的 record 都在紧凑构造器里 `List.copyOf`（SpotBugs 认得它是不可变副本，`Stream.toList()` 它不认，见 `PortalPublicationSearchRequest` 的注释）；请求 record 的列表为 `null` 时原样保留，交给 `@NotEmpty` 报 422。代价：`[1, null]` 这类带 `null` 元素的请求在反序列化时就失败（`List.copyOf` 抛 NPE，被包成消息不可读），与请求体不是合法 JSON 同一种错误，而不是 422。对内部契约这可以接受，元素上因此不写 `@NotNull`。

### 5.4 分层

```
PublicationEndpointImpl（adapter/rest/internal）
  → PublicationResolveQueryService（app，无接口）
      resolveByIds(List<Long>) / resolveByPmids(List<String>) → List<PublicationResolution>
  → PublicationResolveReadPort（domain）
      findByIds(Collection<Long>) / findByPmids(Collection<String>) → List<PublicationResolveReadModel>
  → PublicationResolveReadAdapter（infra）→ PublicationResolveDao（native SQL）
```

- `PublicationResolveReadModel(id, pmid, title, venueName, publicationYear, deleted)`：一篇库里存在的文献（含软删）。
- `PublicationResolution(key, PublicationResolveReadModel found)`：`found` 为 null 表示不存在。查询服务负责去重、按请求顺序排列、为查不到的键补 null 行。端点实现只做 DTO 转换。

### 5.5 SQL

不复用 `PublicationSearchDao.WHERE`：它写死 `deleted_at IS NULL`，而解析要连软删的一起返回。

```sql
-- findByIds
SELECT p.id               AS "id",
       p.pmid             AS "pmid",
       p.title            AS "title",
       v.title            AS "venueName",
       p.publication_year AS "publicationYear",
       (p.deleted_at IS NOT NULL) AS "deleted"
FROM cat_publication p
LEFT JOIN cat_venue v ON v.id = p.venue_id
WHERE p.id = ANY(CAST(:ids AS bigint[]))

-- findByPmids：WHERE p.pmid = ANY(CAST(:pmids AS text[]))
```

一个请求一条 SQL。`cat_publication.pmid` 是 `VARCHAR(15)`，与 `text[]` 比较时 PG 会把列隐式转成 `text`，`uk_pmid` 的 B-tree 索引仍然可用（`varchar` 到 `text` 是二进制兼容转换，不影响索引选择）；实施时在 Testcontainers 里 `EXPLAIN` 确认一次走索引（第 9 节）。

### 5.6 错误契约

| 情况 | 状态 |
|---|---|
| 列表为空、缺失、超过 100；元素非正整数或 PMID 格式不对 | 422（Bean Validation） |
| 请求体不是合法 JSON、元素为 `null`、`ids` 元素不是数字 | 400（`patra-tool-web` 错误引擎对消息不可读的现有映射；`PublicationEndpointIT` 实测 `[1, null]` → 400） |
| 部分或全部键查不到 | 200，对应行 `exists: false` |
| 数据库不可用 | 500（错误引擎默认）；interaction 收到 `RemoteCallException` 时按决策 C 拒绝首次收藏 |

不用 404：批量接口里「查不到」是逐行的业务结果，不是请求失败。

## 6. 门户批量卡片接口

### 6.1 契约

```
GET /portal/publications/batch?id=38123456789012345&id=38123456789012999&id=1
```

```json
{
  "items": [
    {
      "id": "38123456789012345",
      "title": "…",
      "journal": "The New England journal of medicine",
      "year": 2021,
      "authors": ["Anker S. D.", "Butler J."],
      "cites": 1203,
      "doi": "10.1056/nejmoa2107038",
      "pmid": "34449189",
      "source": "PubMed",
      "aiSummary": null,
      "estimatedReadMin": null,
      "kind": "Journal Article",
      "minutesAgo": 1440,
      "venueId": "9100000000000001",
      "evidenceLevel": { "level": "RANDOMIZED_CONTROLLED_TRIAL", "rank": 4, "label": "…", "derived": true },
      "abstractSnippet": "…"
    }
  ]
}
```

| 项 | 规则 |
|---|---|
| 参数 | 重复参数 `id`，1 ~ 100 个，每个为正整数；缺失、超过 100、非正整数 → 422。不拆逗号（控制器已注册的 `RepeatedParamListEditor`） |
| 元素 | 与 `PortalPaperResponse` 同形（删掉 `bookmarks` 之后的形状），即首页 feed 与检索结果的列表项；门户直接复用 `Paper` 类型 |
| 不存在与软删 | **不返回**（2026-10-24 用户确认）。门户对缺的 ID 用 interaction 登记表里的快照显示「已失效」 |
| 顺序 | 按请求里 `id` 的顺序；重复的 ID 只出现一次（第一次出现的位置） |
| 包一层 `items` | 本接口是我定的：与分页接口的 `items` 字段同名，以后要加字段（例如缺失 ID 列表）不破坏形状 |
| 鉴权 | 匿名可访问；网关放行（`anyRequest().permitAll()`） |

路径用 `/batch` 而不是给 `GET /portal/publications` 加参数：后者已经是首页 feed。`/batch` 是字面路径，Spring 的路径匹配优先于 `/{id}` 模板，不会被 `@Positive long id` 截走。

### 6.2 分层

```
PortalPublicationController.batch(PortalPublicationBatchRequest)
  → PortalPublicationCardQueryService.findByIds(List<Long>)   // 去重、按请求顺序排列
  → PublicationSearchReadPort.findCardsByIds(Collection<Long>) // 只返回未软删的
  → PublicationSearchReadAdapter → PublicationSearchDao.findCardsByIds
  → PortalApiConverter.toResponse(PortalPaperReadModel)       // 与 feed / 检索同一个转换
```

卡片放进现有的 `PublicationSearchReadPort`，不另开端口：行到 `PortalPaperReadModel` 的组装（类型拆分、证据等级、摘要片段）就在 `PublicationSearchReadAdapter.toReadModel` 里，按 ID 取卡片只是同一个投影换个 WHERE。

### 6.3 SQL

```sql
-- PublicationSearchDao.findCardsByIds
SELECT_LIST
WHERE p.deleted_at IS NULL
  AND p.id = ANY(CAST(:ids AS bigint[]))
```

`SELECT_LIST` 是现有常量：载体与摘要 LEFT JOIN，作者与类型用相关子查询聚合，整批一条 SQL。顺序在查询服务里按请求重排，SQL 不排序。

## 7. 删除 `bookmarks`

| 位置 | 改动 |
|---|---|
| `PortalPaperResponse` | 删字段与 JavaDoc `@param` |
| `PortalPublicationDetailResponse` | 删字段与 JavaDoc `@param` |
| `PortalApiConverter.toResponse` | 删 `0, // bookmarks…` 实参 |
| `PortalApiConverter.toPublicationDetailResponse` | 删 `.bookmarks(0)` |
| `PortalPublicationControllerIT` | 两条 `jsonPath("$…bookmarks")` 断言改为断言该字段不存在（`doesNotExist()`），守住「不再输出」 |

其他引用 `bookmarks` 的测试按编译错误逐个删除（实施时 `rg bookmarks patra-api/patra-catalog` 确认清零）。

## 8. 网关

不改规则。补两条集成测试：

- `GatewayBlockedPathsIT`：路径表加 `/patra-catalog/_internal/publications/resolve/by-ids`；并加一条 `POST` 同路径带 JSON 体的断言（现有用例只用 `GET`，而本接口是 `POST`），匿名与登录都 403、WireMock 收不到请求。
- `GatewayRoutingIT`：匿名 `GET /patra-catalog/portal/publications/batch?id=1&id=2` 原样转发到下游（含重复参数），不带 `Authorization`。

interaction 调内部接口走 `lb://patra-catalog`，不经网关。

## 9. 索引

| 查询 | 走的索引 |
|---|---|
| 解析按 ID、卡片按 ID | `cat_publication` 主键 |
| 解析按 PMID | `uk_pmid`（唯一约束自带 B-tree，覆盖软删行） |
| 载体名 | `cat_venue` 主键 |
| 作者 / 类型子查询 | `uk_pub_author (publication_id, author_id)` 的前缀、`idx_pub_type_publication`（现有，feed 已在用） |
| 摘要 | `uk_pub_abstract (publication_id)` |

不新增索引、不写迁移。2026-10-24 在 mini 的真实数据（18,807 篇）上 `EXPLAIN` 按 PMID 的 SQL：`Index Scan using uk_pmid on cat_publication p`，条件 `(pmid)::text = ANY ('{…}'::text[])`，`varchar` 列与 `text[]` 比较不影响索引选择。不写成测试断言（计划形状随统计信息变化，断言会脆）。

## 10. 测试策略

### 10.1 单元测试

- `PublicationResolveQueryServiceTest`（mock 读端口）：按请求顺序输出；重复键只出现一次；查不到的键补 `found = null`；按 PMID 同理；端口只被调一次且入参已去重。
- `PortalPublicationCardQueryServiceTest`（mock 读端口）：按请求顺序重排；重复 ID 去重；端口没返回的 ID（不存在 / 软删）不出现。
- `PublicationResolutionRespTest` 或放进端点测试：`missing(key)` 的形状。
- `PortalApiConverterTest`：现有用例去掉 `bookmarks`。

### 10.2 集成测试

- infra（`@DataJpaTest` + `CatalogITPostgreSQLContainerInitializer`）：
  - `PublicationResolveReadAdapterIT`：按 ID 返回存在、软删两种行（`deleted` 正确，软删行带标题 / 期刊 / 年份）；不存在的 ID 不出现在结果里；按 PMID 同理；无载体时 `venueName` 为 null；`EXPLAIN` 记录（第 9 节）。
  - `PublicationCardReadIT`（新类，`@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")`）：造 100 篇文献，每篇带载体、2 个作者、2 个类型、摘要，再造 1 篇软删；`findCardsByIds` 传 101 个 ID，断言返回 100 条、软删的不在、组装字段正确，且 Hibernate `Statistics.getPrepareStatementCount()` 在调用前后只增加 1（AC「无 N+1」）。单独成类是因为统计开关改变了 Spring 测试上下文，放进现有 IT 会让其他用例换一个上下文。
- adapter（`@WebMvcTest` + `CatalogAdapterITWebMvcConfig`，mock 查询服务）：
  - `PublicationEndpointIT`：两个端点 200 与 JSON 结构（`id` 为字符串、`exists: false` 行全 null）；再用切片上下文里的 `JsonMapper` 把响应体反序列化回 `PublicationResolveResp`，断言 `id` 拿回 `Long`，守住第 5.2 节的往返约定（`patra-catalog-api` 没有测试源集与 Jackson 依赖，不为一条用例去加）；空列表、101 个、非正整数、PMID 含字母 → 422 且不触达服务。
  - `PortalPublicationControllerIT`：`/batch` 200 与 `items` 结构、重复参数绑定为多个 ID、`?id=` 空值被丢弃；缺失 / 101 个 / 非正整数 → 422；列表与详情不再有 `bookmarks`。
- gateway：第 8 节两条。

### 10.3 门控

`./gradlew spotlessApply` → `./gradlew check` → `:patra-api:patra-catalog:patra-catalog-infra:integrationTest`、`:patra-api:patra-catalog:patra-catalog-adapter:integrationTest`、`:patra-api:patra-gateway-boot:integrationTest`。

## 11. README

`patra-api/patra-catalog/README.md`：「已实现功能」加一条（文献批量解析与批量卡片）；「内部接口」一节加两个 `_internal/publications/resolve/*` 路径与契约位置；变更日志加一行。

## 12. 交给其他 Issue 的约束

**PAP-112（interaction 对象登记表）**：

- `patra-interaction-infra` 依赖 `:patra-api:patra-catalog:patra-catalog-api`；boot 用 `RestClientFactory.createRestClient(builder, "catalog", "lb://patra-catalog")` 建 `RestClient` 再 `createProxy(PublicationEndpoint.class)`，照 identity 调 catalog 的写法（PAP-94 第 14 节）。
- 首次收藏：`resolveByIds([id])`；`exists: false` → 拒绝，不建登记行；`state: DELETED` 的文献能不能被首次收藏由 PAP-112 定（catalog 只如实返回）；`pmid` 为 null 时无法建 `pmid:` 锚，应拒绝。
- 对账：`resolveByPmids`，每批 ≤ 100；用返回的 `id` 刷新 `source_id`、`state` 映射为 ACTIVE / UNAVAILABLE、用 `title` / `journal` / `year` 刷新快照；`exists: false`（PMID 在 catalog 里整行消失，例如清库后还没重导）的处理由 PAP-112 定，catalog 不区分「从未有过」与「被清掉」。
- 收到 `RemoteCallException`（含超时、5xx）：首次收藏按决策 C 失败并提示重试；对账任务跳过本批、下次再对。

**PAP-114（门户文献库）**：

- 文献库与收藏夹页先从 interaction 拿到文献 ID（及快照），再一次 `GET /patra-catalog/portal/publications/batch?id=…` 取卡片；响应里缺的 ID 用快照显示「已失效」。
- `Paper` 类型删 `bookmarks`，详情页事实条删「收藏」格。
- 文献库按精度显示的出版日期等 PAP-110 给卡片加上日期字段后再接；本接口在 PAP-109 合入时只有年份。

## 13. AC 对照

| AC | 怎么证明 |
|---|---|
| 解析接口对已存在、软删、不存在三种情况返回正确的 exists / state | `PublicationResolveReadAdapterIT`（真实 SQL 的 `deleted`）+ `PublicationResolveQueryServiceTest`（不存在补行）+ `PublicationEndpointIT`（JSON 形状） |
| 卡片接口一次 100 个 ID 时没有 N+1（集成测试断言查询次数） | `PublicationCardReadIT`：100 个 ID、`getPrepareStatementCount()` 增量为 1 |
| 经网关：门户接口可访问，`/_internal/**` 返回 403 | `GatewayRoutingIT` 的 `/batch` 转发用例；`GatewayBlockedPathsIT` 的 `GET` / `POST` 两条 403 且下游收不到；部署后在 mini 上用 curl 各打一次（实施记录） |

## 14. 设计中由我定的点

1. 解析接口拆成 `by-ids`、`by-pmids` 两个 `POST` 端点，而不是一个端点带两个列表（第 5.1 节）。
2. `key` 统一为字符串并原样回传；查不到的键也输出一行（`exists: false`），按请求顺序、去重。
3. 任一元素不合规整个请求 422，不做部分成功。
4. `title` 返回含内联标记的原文，与门户其他接口一致。
5. `pmid` 为 null 照实返回，由 PAP-112 决定拒绝。
6. 卡片接口路径 `/portal/publications/batch`，响应包一层 `items`。
7. 不加索引、不写迁移（第 9 节）。
8. N+1 用 Hibernate `Statistics` 断言（零新依赖；仓库里第一次统计查询次数），单独成一个 IT 类。
9. 请求列表里的 `null` 元素按消息不可读处理，不单独做成 422（第 5.3 节）。

用户已定：卡片接口对不存在与软删的 ID 不返回（2026-10-24）。
