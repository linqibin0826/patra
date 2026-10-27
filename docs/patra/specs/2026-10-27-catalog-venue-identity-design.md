# catalog 期刊身份与批量接口工程设计（PAP-117）

> **Issue**：[PAP-117](https://linear.app/papertrace/issue/PAP-117)（GitHub #294）
> **版本**：[v0.14 Discussion](../release-specs/v0.14-discussion.md)（决策 B）
> **前置设计**：[文献批量解析与批量卡片（PAP-109）](2026-10-24-catalog-publication-resolve-cards-design.md)（内部解析接口与门户批量卡片的写法）、[网关鉴权（PAP-65）](2026-10-09-gateway-auth-design.md)（`/*/_internal/**` 拒绝名单）
> **下游**：PAP-120（interaction 期刊对象：首次登记时解析、每日对账）、PAP-125（门户按期刊 ID 取卡片）
> **日期**：2026-10-27
> **状态**：已实施（2026-10-27，分支 `claude/v014-catalog-e0526e`，基于 `feat/v0.14-discussion-api`）；与设计不同处已按实测回填（第 6.3 节同批第二条记录、第 10.1 节转换器测试、第 11.4 节回滚、第 15 节观察、第 16 节已知限制）

## 1. 要解决的问题

v0.14 要把期刊登记成 interaction 的对象（`JOURNAL`）。身份锚是 `nlm:{NLM Unique ID}`；catalog 的期刊内部 ID 只是可以重新解析的缓存，换库重导后会变（决策 B）。interaction 需要 catalog 回答两类问题：

1. 首次登记时：门户 URL 里的期刊 ID 对应哪个期刊？存在吗？软删了吗？NLM ID 是多少？刊名是什么（登记表的快照）？
2. 每日对账时：这一批 NLM ID 现在对应哪些期刊 ID？状态如何？刊名变了吗？

门户的用户主页「投稿经验」标签等列表要按一批期刊 ID 取期刊卡片（PAP-125）。

锚要成立，必须满足两点：一个 NLM ID 只指向一个期刊；同一个期刊的 NLM ID 不会变。现状两点都没有保证：

- `cat_venue.nlm_id` 没有唯一约束，也没有索引。
- 导入按 ISSN-L → NLM → ISSN 的顺序匹配。如果一条记录的 ISSN-L 对上期刊 X、NLM 对上期刊 Y，它会被并进 X，它的 NLM 也会挂到 X 上。这样同一个 NLM 就挂在了两个期刊上。
- `nlm_id` 列冗余存「标识符列表里排第一的 NLM」，而列表从库里读出来时不排序。一个期刊挂着两个 NLM 时，每次导入都可能换值。
- `VenueRepositoryAdapter.findByNlmIds` 查的是 `cat_venue_identifier`，那里同样没有唯一约束，同一个 NLM 挂在两个期刊上时结果不确定。另外它按期刊 ID 建映射、后写覆盖前写，同一期刊的两个 NLM 一起查时只返回其中一个。

## 2. 范围

做：

- 迁移 V12：`cat_venue.nlm_id` 全局唯一；`cat_venue_identifier` 里 NLM 类型的值全表唯一。
- 期刊聚合显式记录主 NLM：第一次写入后不再改。
- 导入改为 NLM → ISSN-L → ISSN 匹配；NLM 属于已软删期刊的记录跳过；`findByNlmIds` 主、副 NLM 都能命中。
- 内部接口 `POST /_internal/venues/resolve/by-ids`、`POST /_internal/venues/resolve/by-nlm-ids`，契约放进 `patra-catalog-api`。
- 门户接口 `GET /portal/venues/batch`；期刊浏览卡片加 `nlmId`。
- 网关集成测试补拒绝路径与放行路径（规则不改）。
- 上线步骤（第 11 节）、catalog README。

不做：

- 拆分已合并的期刊。mini 上有 15 个期刊由同一份刊物改名前后的两条 NLM 记录合并而成，保持现状，刊名仍是先导入那条记录的。
- interaction 侧的客户端、`nlm:` 锚和期刊对象（PAP-120，交接见第 13 节）。
- 门户的 TS 类型与页面（PAP-125）。
- 期刊详情接口（PAP-119 会在详情里加审稿周期）。
- `findByIssnLs` 的同类映射问题：一个期刊有两个 ISSN-L 时只返回其中一个。ISSN-L 匹配不到时会退到按 ISSN 匹配，结果相同，本版不改。
- 期刊浏览接口的列表参数按逗号拆分的问题（第 8.1 节）。

## 3. 已定的前提

| 前提 | 来源 |
|---|---|
| 期刊对外 key 是 catalog 期刊内部 ID（门户 URL 已在用）；`natural_key` 是 `nlm:{NLM Unique ID}`，按字符串存（NLM ID 有前导零）；catalog 先在 mini 核对没有重复，再加全局唯一约束，并提供内部批量解析接口；解析与每日对账的做法和文献相同 | release spec 决策 B |
| 导入匹配顺序改为 NLM → ISSN-L → ISSN | 用户 2026-10-27 |
| 主 NLM 在期刊聚合上明确记录，第一次写入后不再改 | 用户 2026-10-27 |
| 按 NLM 批量解析时，副 NLM 也对到同一个期刊，返回的 `nlmId` 仍是主 NLM | 用户 2026-10-27 |
| 标识符表加部分唯一索引，保证 NLM 类型的值全表唯一（超出 Issue 字面范围，用户已同意） | 用户 2026-10-27 |
| 期刊只有 `venuePubmedImportJob` 一个创建来源。mini 上的 XXL-Job 任务 11 没有定时（`schedule_type = NONE`、已停用），只会被手动触发；每日 05:00 的 `pubmedImportJob` 只按 NLM / ISSN 查期刊，不建期刊 | 现状代码 + mini 2026-10-27 |
| `VenueEntity` 继承 `SoftDeletableJpaEntity`（Hibernate `@SoftDelete`）：JPA 查询与 `EntityManager.find` 自动排除软删行，native SQL 不排除。catalog 里没有任何代码软删期刊 | 现状代码 |
| `cat_venue.abbreviated_title` 来自 LSIOU 的 `MedlineTA`（MEDLINE 刊名缩写），门户卡片的 `abbr` 取的就是它 | `VenuePubmedImportHandler` |
| 内部接口的写法照 PAP-109：契约在 `patra-catalog-api`（`@HttpExchange`），批量 1 ~ 100 个键，每个键一行，元素不合规时整个请求 422；`Long` 经 MVC 转换器输出为 JSON 字符串，interaction 的 `RestClient` 反序列化回 `Long` | PAP-109 第 5 节 |
| `/*/_internal/**` 在网关第一条过滤器链上对任何人 403；catalog 其余路径（含 `/portal/**`）匿名放行 | `GatewaySecurityConfiguration.BLOCKED_PATHS` |

## 4. mini 核对结论（2026-10-27，只读）

用 `BEGIN READ ONLY` 加只读会话参数在 mini 的 `patra_catalog` 上查询，SQL 见第 11.1 节。

| 项 | 结果 |
|---|---|
| 期刊总数 | 15,434，全部是 `JOURNAL`，没有软删的 |
| `nlm_id` 为空 | 0 |
| `nlm_id` 列重复（含软删行） | **0**，可以直接加唯一约束 |
| 标识符表的 NLM 行 | 15,449 行，15,449 个不同的值 |
| 同一个 NLM 挂在多个期刊上（标识符表） | **0** |
| 一个期刊挂两个 NLM | 15 个，都是同一份刊物改名前后的两条 NLM 记录，导入时因为共用 ISSN 被合并。例：`0404401` / `100970884`（Schweizerische medizinische Wochenschrift / Swiss medical weekly）、`0413657` / `101194770`（Bibliotheca nutritio et dieta / Forum of nutrition） |
| 主 NLM 不在该期刊的标识符里 | 0 |
| 有 NLM 标识符但 `nlm_id` 为空 | 0 |
| NLM ID 的长度 | 7 位 10,425 个、8 位 246 个、9 位 4,714 个、16 位 49 个 |
| NLM ID 的写法 | 纯数字 13,243 个，其中 3,652 个以 0 开头；末尾带 `R` 2,190 个；末尾带 `A` 1 个（`9300367A`） |
| ISSN-L 列重复 | 0 |
| 现有索引 | `cat_venue` 只有主键与 `idx_venue_type` / `idx_title` / `idx_provenance` / `idx_cited_by_count`；`cat_venue_identifier` 有 `uk_venue_type_value (venue_id, identifier_type, identifier_value)` 与 `idx_type_value (identifier_type, identifier_value)` |
| Flyway 最新版本 | V11，本 Issue 用 V12 |
| 文献 | 18,807 篇，全部关联了期刊，36 篇软删 |

由此有两个推论。约束可以直接上线，不需要先处理数据。那 15 个「主 NLM + 副 NLM」的期刊是唯一需要照顾的情况：它们决定了主 NLM 必须固定（第 6.2 节），副 NLM 也要能解析（第 7 节）。

## 5. 模块与改动面

| 层 | 新增 / 修改 |
|---|---|
| `patra-catalog-api` | 新增 `VenueEndpoint`（`@HttpExchange`）、`VenueResolveByIdsReq`、`VenueResolveByNlmIdsReq`、`VenueResolveResp`、`VenueResolutionResp`、枚举 `VenueStateResp` |
| domain | `VenueAggregate` 加主 NLM（`nlmId`），改 `fromPubMed` / `restore` / `addIdentifier` / `removeIdentifier`；`VenueRepository` 加 `findSoftDeletedNlmIds`；新增读模型 `VenueResolveReadModel`（`model/read/venue/`）、读端口 `VenueResolveReadPort`（`port/read/`）；`VenueBrowseReadModel` 加 `nlmId`；`VenueBrowseReadPort` 加 `findCardsByIds` |
| app | `VenuePubmedImportHandler` 改匹配顺序、跳过软删期刊的 NLM；新增 `VenueResolveQueryService`（`usecase/venue/query/`）与结果 `VenueResolution`（`…/query/dto/`）、`PortalVenueCardQueryService`（`usecase/portal/query/`） |
| infra | 迁移 `V12__add_venue_nlm_unique.sql`；`VenueJpaMapper` 读写主 NLM；`VenueRepositoryAdapter.findByNlmIds` 改为按标识符行建映射，并实现 `findSoftDeletedNlmIds`；新增 `VenueResolveReadAdapter`（`adapter/read/`）、投影 `VenueResolveRow`、Dao `VenueResolveDao`；`VenueDao` 浏览查询加 `nlm_id` 并新增 `findPortalVenueCardsByIds`；`VenueBrowseRow` 加 `getNlmId`；`VenueBrowseReadAdapter` 实现 `findCardsByIds`；`VenueEntity` 的索引注解改成唯一约束注解 |
| adapter | 新增 `VenueEndpointImpl`（`rest/internal/`）、`PortalVenueBatchRequest`、`PortalVenueBatchResponse`；`PortalVenueController` 加 `batch`；`PortalVenueBrowseResponse` 加 `nlmId`；`PortalApiConverter` 带上 `nlmId` |
| boot | e2eTest 新增 `VenuePubmedImportE2E` 与 LSIOU 夹具 |
| gateway | `GatewayBlockedPathsIT`、`GatewayRoutingIT` 各补用例 |
| 文档 | catalog README |

命名照 `.claude/rules/tech/port-service.md`：CQRS 读端是 `{Entity}ReadPort` / `{Entity}ReadAdapter`，查询服务不定义接口。

## 6. 期刊身份

### 6.1 迁移 V12

```sql
-- V12__add_venue_nlm_unique.sql
ALTER TABLE cat_venue
    ADD CONSTRAINT uk_venue_nlm_id UNIQUE (nlm_id);

CREATE UNIQUE INDEX uk_venue_identifier_nlm
    ON cat_venue_identifier (identifier_value)
    WHERE identifier_type = 'NLM';
```

- `uk_venue_nlm_id` 覆盖软删行（与文献的 `uk_pmid` 一致），所以是「全局」唯一。`nlm_id` 允许为空，PG 默认把多个 NULL 视为互不相同，不冲突。
- `uk_venue_identifier_nlm` 是部分唯一索引：无论主 NLM 还是副 NLM，一个 NLM ID 只属于一个期刊。`findByNlmIds`、按 NLM 解析、导入匹配都查这张表，结果因此确定。它也是按 NLM 解析时走的索引（查询条件带 `identifier_type = 'NLM'`，与索引谓词一致）。
- 两条在 mini 上都是秒级（15,434 行与 15,449 行）。V12 在 catalog 容器启动时由 Flyway 执行，库里一旦有重复，迁移会失败、catalog 起不来，所以第 11 节要求在合并前核对。
- `VenueEntity` 的 `@Table` 里声明了 `@Index(name = "idx_nlm_id")`，但 V1 从没建过这个索引（库结构只由 Flyway 管，注解不建库）。把它换成 `@UniqueConstraint(name = "uk_venue_nlm_id", columnNames = "nlm_id")`，让注解和真实结构一致。其余几个同样不存在的索引注解（`idx_issn_l`、`idx_openalex_id`）与本 Issue 无关，不动。

### 6.2 主 NLM

`VenueAggregate` 新增字段 `nlmId`，表示主 NLM，也就是期刊的身份锚：

| 方法 | 行为 |
|---|---|
| `fromPubMed(title, nlmId, issnL)` | `nlmId` 非空时成为主 NLM，同时进入标识符列表（行为同现在） |
| `restore(id, venueType, title, imageObjectKey, nlmId, version)` | 新增参数 `nlmId`，从 `cat_venue.nlm_id` 恢复主 NLM |
| `addIdentifier(NLM 值)` | 期刊还没有主 NLM 时，第一次加入的 NLM 成为主 NLM；已经有主 NLM 时只进标识符列表，成为副 NLM。其他类型的行为不变 |
| `removeIdentifier(NLM, 主 NLM)` | 拒绝，抛 `IllegalStateException`：主 NLM 必须留在标识符里 |
| `getNlmId()` | 主 NLM，可空（Lombok `@Getter`） |

`VenueJpaMapper` 的 `toAggregate` 把 `entity.getNlmId()` 传给 `restore`；`populateQuickAccessFields` 写 `aggregate.getNlmId()`，不再取 `getIdentifier(NLM)`。`VenueAggregate.toString` 也改用主 NLM。

由此有两个不变式：

1. 主 NLM 一经写入不再改：恢复时以库里的 `nlm_id` 为准，导入只会追加副 NLM。
2. 主 NLM 一定在该期刊的 NLM 标识符里：主 NLM 只能经 `fromPubMed` 或 `addIdentifier` 产生，二者都同时写进标识符列表；删除又被拒绝。mini 上目前 0 违反（第 4 节）。

只有 ISSN-L 没有 NLM 的期刊（mini 上没有），会在第一次带 NLM 的记录经 ISSN-L 或 ISSN 匹配上时拿到主 NLM。

### 6.3 导入匹配

`VenuePubmedImportHandler` 对每条 LSIOU 记录按下面的顺序处理：

| 顺序 | 条件 | 处理 |
|---|---|---|
| 1 | LSIOU 标记删除（`deletedTimestamp` 非空） | 跳过（不变） |
| 2 | 记录的 NLM 属于已软删的期刊（主或副） | **跳过**，计入 `skipped`，打 debug 日志 |
| 3 | 记录的 NLM 命中未软删的期刊（主或副） | 覆盖更新该期刊 |
| 4 | ISSN-L 命中 | 覆盖更新；记录的 NLM 挂到该期刊上 |
| 5 | ISSN 命中（Print 先于 Electronic） | 同 4 |
| 6 | 都没命中 | 新建；批内按 NLM、ISSN-L 去重 |

同一期刊在一批里只按第一条命中它的记录覆盖资料（不变）。同一份刊物的另一条记录落在同一批时，有两种情况：命中同一个已有期刊，或按 ISSN-L 与本批新建的期刊重复。这两种情况下，第二条记录都不覆盖资料，但它的 NLM 只要无主（库里和本批都没用过），就挂成那个期刊的副 NLM。NLM 要挂到本批要保存的那个实例上：三种查找各自重建聚合，同一期刊在内存里可能有多个实例。否则同批的两条记录里第二条的 NLM 永远进不了库，第 7.2 节「副 NLM 也能查到」的保证就只在两条记录分属不同批时成立。本批已挂到某个期刊上的 NLM 不会再新建期刊。

第 4、5 步里，记录的 NLM 不属于任何期刊（否则第 2、3 步已经处理了），挂上去不会冲突：期刊没有主 NLM 时它成为主 NLM，否则成为副 NLM。

**为什么 NLM 先于 ISSN-L**：Issue 的验收要求「导入遇到同一 NLM ID 时合并到已有期刊」，NLM 必须在冲突时胜出。mini 上目前没有 ISSN-L 与 NLM 指向不同期刊的情况（标识符表里没有挂在两个期刊上的 NLM），改顺序不改变现有数据。

**代价**：NLM 与 ISSN-L 指向不同期刊时，记录的 ISSN-L 会挂到 NLM 那个期刊上（标识符行与 `issn_l` 列），同一个 ISSN-L 于是出现在两个期刊上，按 ISSN-L / ISSN 的查找（`findByIssnLs`、`VenueLookupPort.findByIssn`）对这一对期刊不再确定。旧顺序扩散的是 NLM，新顺序扩散的是 ISSN-L；ISSN-L 没有唯一约束，身份锚是 NLM，这是有意的取舍。mini 上目前没有这样的期刊对；文献导入按 NLM 优先找期刊（`findByPriority`），不受影响。

**为什么跳过软删期刊的 NLM，而不是复活**：catalog 没有任何代码软删期刊，软删只可能来自人工下架，导入不该推翻这个决定；也不能把这个 NLM 并进别的期刊，那样等于把身份转给了别的期刊。另外，`findByNlmIds` 经 JPA 重建聚合时看不到软删行，若不先查出来，记录会落到第 4 ~ 6 步：要么把这个 NLM 挂到别的期刊上，要么新建一个同 NLM 的期刊，两种都会撞上 V12 的约束，导致整批回滚。

仓储改动：

- `findByNlmIds`：按标识符行逐条建映射，每个命中的 NLM 映射到它的期刊。主、副 NLM 都能命中；同一期刊的两个 NLM 一起查时两个都返回。仍只返回未软删的期刊（经 JPA 重建）。
- 新增 `Set<String> findSoftDeletedNlmIds(Collection<String> nlmIds)`：返回入参里属于已软删期刊的 NLM（主或副）。用 native SQL，绕过 `@SoftDelete` 过滤：

```sql
SELECT i.identifier_value
FROM cat_venue_identifier i
JOIN cat_venue v ON v.id = i.venue_id
WHERE i.identifier_type = 'NLM'
  AND i.identifier_value = ANY((:nlmIds)::text[])
  AND v.deleted_at IS NOT NULL
```

`processBatch` 每批多这一条查询。处理器类 JavaDoc 与 README 里的「ISSN-L → NLM ID → ISSN」改成新顺序。

**两条约束为什么在导入中守得住**：

- 只有第 6 步新建期刊。走到第 6 步的记录，NLM 既不属于未软删的期刊（第 3 步），也不属于软删的期刊（第 2 步），而 LSIOU 里一个 NLM 只有一条记录，批内又按 NLM 去重。所以新期刊的主 NLM 不会和任何已有的 NLM 重复。
- 只有第 4、5 步给已有期刊追加 NLM。理由同上，追加的 NLM 不属于任何期刊。
- 主 NLM 恢复时以库里为准，不会被改写成别的值。

## 7. 内部批量解析接口

### 7.1 契约

```
POST /_internal/venues/resolve/by-ids
Content-Type: application/json

{ "ids": [319059608675758187, 1] }
```

```
POST /_internal/venues/resolve/by-nlm-ids
Content-Type: application/json

{ "nlmIds": ["0404401", "100970884", "2985213R", "9999999"] }
```

响应（两个端点同形）：

```json
{
  "items": [
    {
      "key": "0404401",
      "exists": true,
      "state": "ACTIVE",
      "id": "319059608675758187",
      "nlmId": "0404401",
      "title": "Schweizerische medizinische Wochenschrift",
      "abbreviatedTitle": "Swiss Med Wkly"
    },
    {
      "key": "100970884",
      "exists": true,
      "state": "ACTIVE",
      "id": "319059608675758187",
      "nlmId": "0404401",
      "title": "Schweizerische medizinische Wochenschrift",
      "abbreviatedTitle": "Swiss Med Wkly"
    },
    {
      "key": "2985213R",
      "exists": true,
      "state": "ACTIVE",
      "id": "319054106608742644",
      "nlmId": "2985213R",
      "title": "Lancet (London, England)",
      "abbreviatedTitle": "Lancet"
    },
    {
      "key": "9999999",
      "exists": false,
      "state": null,
      "id": null,
      "nlmId": null,
      "title": null,
      "abbreviatedTitle": null
    }
  ]
}
```

（示例取自 mini 2026-10-27 的数据。前两行是同一个合并期刊：刊名是先导入的旧刊名，缩写来自后导入的新刊名记录，这是第 2 节「不做」里提到的现状。）

| 项 | 规则 |
|---|---|
| 请求规模 | 列表 1 ~ 100 个元素；为空、缺失或超过 100 → 422 |
| 元素校验 | `ids` 每个都是正整数；`nlmIds` 每个都匹配 `^[0-9]{1,19}[A-Z]?$`（第 7.2 节）。任一元素不合规，整个请求 422，不做部分成功 |
| 去重与顺序 | 按请求顺序输出；重复的键只输出第一次出现的那一行 |
| 每个键一行 | 查得到 → `exists: true`，`state` 由 `deleted_at` 决定；查不到 → `exists: false`，其余字段全为 null |
| `key` | 请求里的键原样回传，统一为字符串（按 ID 时是十进制字符串） |
| 只认期刊 | 只认 `venue_type = 'JOURNAL'`，其他类型按查不到处理，与门户期刊详情同口径（mini 上全部是期刊） |
| `state` | `ACTIVE`（`deleted_at IS NULL`）/ `DELETED`（`deleted_at IS NOT NULL`）。软删的期刊照样带刊名，interaction 用它刷新「已失效」快照 |
| `id` | catalog 期刊内部 ID。按 NLM 解析时，interaction 用它刷新 `source_id`（换库重导后会变） |
| `nlmId` | **主 NLM**。按 NLM 解析时，如果请求的是副 NLM，`nlmId` 与 `key` 不同（上例第 2 行）；库里为空时为 null（mini 上没有） |
| `title` | `cat_venue.title`，期刊全称 |
| `abbreviatedTitle` | `cat_venue.abbreviated_title`，MEDLINE 刊名缩写（`MedlineTA`），可空；与门户卡片的 `abbr` 同源 |

`id` 在 Java 契约里是 `Long`，与 PAP-109 同样经 MVC 转换器输出为 JSON 字符串，interaction 用 `RestClient` 反序列化时拿回 `Long`。第 10 节有一条往返测试守住这个约定。

### 7.2 NLM ID 的写法（契约说明）

- **一律按字符串处理，原样保留前导零。** mini 上有 3,652 个以 0 开头，如 `0404401`。
- 在 mini 上看到的写法（2026-10-27）：7 位、8 位、9 位数字，以及 49 个 16 位的 NLM 新式 ID（如 `9918523075306676`）；末尾可以带一个大写字母，`R` 2,190 个（如 Lancet 的 `2985213R`），`A` 1 个（`9300367A`）。
- 校验写法 `^[0-9]{1,19}[A-Z]?$`：总长不超过 20，对齐 `VARCHAR(20)`。区分大小写，不做归一（NLM 只用大写；小写的 `r` 不合规，422）。
- 一个期刊可以有多个 NLM：一个主 NLM，若干副 NLM。所有 NLM 全表唯一（V12），无论主副，一个 NLM 只对应一个期刊。
- 按 NLM 解析时，主、副 NLM 都能查到期刊，返回的 `nlmId` 总是主 NLM。将来整库重导时，同一份刊物的两条记录谁先导入、谁就成为主 NLM，主副可能互换；副 NLM 也能查到，锚才不会失效。

### 7.3 `patra-catalog-api`

```java
@HttpExchange(
    url = "/_internal/venues",
    accept = "application/json",
    contentType = "application/json")
public interface VenueEndpoint {

  @PostExchange("/resolve/by-ids")
  VenueResolveResp resolveByIds(@Valid @RequestBody VenueResolveByIdsReq request);

  @PostExchange("/resolve/by-nlm-ids")
  VenueResolveResp resolveByNlmIds(@Valid @RequestBody VenueResolveByNlmIdsReq request);
}

/// 1 个字段，of()
public record VenueResolveByIdsReq(
    @NotEmpty @Size(max = 100) List<@Positive Long> ids) {}

/// 1 个字段，of()
public record VenueResolveByNlmIdsReq(
    @NotEmpty @Size(max = 100) List<@Pattern(regexp = "^[0-9]{1,19}[A-Z]?$") String> nlmIds) {}

/// 1 个字段，of()
public record VenueResolveResp(List<VenueResolutionResp> items) {}

/// 7 个字段，@Builder；另有静态 missing(String key)
public record VenueResolutionResp(
    String key, boolean exists, VenueStateResp state,
    Long id, String nlmId, String title, String abbreviatedTitle) {}

public enum VenueStateResp { ACTIVE, DELETED }
```

防御性拷贝与 `null` 元素的处理照 PAP-109 第 5.3 节：三个带列表的 record 都在紧凑构造器里 `List.copyOf`；请求的列表为 `null` 时原样保留，交给 `@NotEmpty` 报 422；带 `null` 元素的请求在反序列化时就失败，按「消息不可读」返回 400。

### 7.4 分层

```
VenueEndpointImpl（adapter/rest/internal）
  → VenueResolveQueryService（app，无接口）
      resolveByIds(List<Long>) / resolveByNlmIds(List<String>) → List<VenueResolution>
  → VenueResolveReadPort（domain）
      findByIds(Collection<Long>) → List<VenueResolveReadModel>
      findByNlmIds(Collection<String>) → Map<String, VenueResolveReadModel>   // 命中的 NLM → 期刊
  → VenueResolveReadAdapter（infra）→ VenueResolveDao（native SQL）
```

- `VenueResolveReadModel(id, nlmId, title, abbreviatedTitle, deleted)`：库里存在的一个期刊（含软删）。
- `VenueResolution(key, VenueResolveReadModel found)`：`found` 为 null 表示查不到。查询服务负责去重、按请求顺序排列、为查不到的键补行。按 NLM 时用端口返回的映射按 `key` 取，主、副 NLM 各占一行。端点实现只做 DTO 转换。
- 按 NLM 的端口方法返回映射，而不是像文献那样返回列表：命中的键（可能是副 NLM）不等于读模型里的 `nlmId`，查询服务要按键对回。

### 7.5 SQL

```sql
-- findByIds
SELECT v.id                 AS "id",
       v.nlm_id             AS "nlmId",
       v.title              AS "title",
       v.abbreviated_title  AS "abbreviatedTitle",
       (v.deleted_at IS NOT NULL) AS "deleted"
FROM cat_venue v
WHERE v.id = ANY((:ids)::bigint[])
  AND v.venue_type = 'JOURNAL'

-- findByNlmIds：从标识符表出发，主、副 NLM 都命中；命中的值作为键
SELECT i.identifier_value   AS "matchedNlmId",
       v.id                 AS "id",
       v.nlm_id             AS "nlmId",
       v.title              AS "title",
       v.abbreviated_title  AS "abbreviatedTitle",
       (v.deleted_at IS NOT NULL) AS "deleted"
FROM cat_venue_identifier i
JOIN cat_venue v ON v.id = i.venue_id
WHERE i.identifier_type = 'NLM'
  AND i.identifier_value = ANY((:nlmIds)::text[])
  AND v.venue_type = 'JOURNAL'
```

一个请求一条 SQL，native 查询不过滤软删。按 ID 走 `cat_venue` 主键；按 NLM 走 `uk_venue_identifier_nlm`（第 9 节）。主 NLM 一定在标识符表里（第 6.2 节不变式 2），所以只查标识符表就够了。

### 7.6 错误契约

| 情况 | 状态 |
|---|---|
| 列表为空、缺失、超过 100；元素不是正整数，或 NLM ID 写法不对 | 422（Bean Validation） |
| 请求体不是合法 JSON、元素为 `null`、`ids` 的元素不是数字 | 400（`patra-tool-web` 错误引擎对「消息不可读」的现有映射） |
| 部分或全部键查不到 | 200，对应行 `exists: false` |
| 数据库不可用 | 500（错误引擎默认）；interaction 收到 `RemoteCallException` 时按决策 B 的文献做法处理 |

不用 404：批量接口里「查不到」是逐行的业务结果，不是请求失败。

## 8. 门户批量期刊卡片

### 8.1 契约

```
GET /portal/venues/batch?id=319059608675758187&id=1
```

```json
{
  "items": [
    {
      "id": "319059608675758187",
      "name": "Schweizerische medizinische Wochenschrift",
      "abbr": "Swiss Med Wkly",
      "coverObjectKey": null,
      "impactFactor": null,
      "jcrQuartile": null,
      "jcrSubject": null,
      "casMajorCategory": null,
      "casMajorQuartile": null,
      "casIsTop": null,
      "countryCode": "CH",
      "citedByCount": 1983,
      "foundedYear": 2001,
      "isOpenAccess": true,
      "isInDoaj": false,
      "issnL": "0036-7672",
      "nlmId": "0404401"
    }
  ]
}
```

| 项 | 规则 |
|---|---|
| 参数 | 重复参数 `id`，1 ~ 100 个，每个都是正整数；缺失、超过 100、非正整数 → 422 |
| 元素 | 与期刊浏览列表项（`PortalVenueBrowseResponse`）同形，并加上 `nlmId`（主 NLM，字符串，保留前导零）。浏览列表项随之也带上 `nlmId`，同一种卡片只有一个形状 |
| 不存在、软删、非期刊 | 不返回（与 `/portal/publications/batch` 一致）。门户对缺的 ID 用 interaction 登记表里的快照显示 |
| 顺序 | 按请求里 `id` 的顺序；重复的 ID 只出现一次（第一次出现的位置） |
| 包一层 `items` | 与 `/portal/publications/batch` 一致 |
| 鉴权 | 匿名可访问；网关放行 |

路径用字面量 `/batch`，Spring 的路径匹配优先于 `/{id}` 模板，不会被详情接口的 `@Positive long id` 截走。

`PortalVenueController` 不注册 `RepeatedParamListEditor`。注册了会改变浏览接口 `subject` 等列表参数现在的逗号行为，那是另一个问题（本 Issue 不改，见第 15 节观察）。期刊 ID 是纯数字，`?id=1,2` 按逗号拆成两个 ID 也没有歧义。

### 8.2 分层

```
PortalVenueController.batch(PortalVenueBatchRequest)
  → PortalVenueCardQueryService.findByIds(List<Long>)   // 去重、按请求顺序排列
  → VenueBrowseReadPort.findCardsByIds(Collection<Long>) // 只返回未软删的期刊
  → VenueBrowseReadAdapter → VenueDao.findPortalVenueCardsByIds
  → PortalApiConverter.toVenueBrowseResponse(VenueBrowseReadModel) // 与浏览同一个转换
```

`PortalVenueBatchRequest` 照 `PortalPublicationBatchRequest` 写：紧凑构造器丢掉 `null` 元素后 `List.copyOf`，空列表交给 `@NotEmpty`。`PortalVenueBatchResponse(List<PortalVenueBrowseResponse> items)` 照 `PortalPaperBatchResponse` 写。

### 8.3 SQL

浏览查询 `findPortalVenueBrowsePage` 的投影列抽成常量，加上 `v.nlm_id AS "nlmId"`；批量卡片复用同一份投影：

```sql
-- VenueDao.findPortalVenueCardsByIds
SELECT <浏览卡片投影，含 nlm_id>
FROM cat_venue v
LEFT JOIN LATERAL (… 最新年 JCR …) jcr ON TRUE
LEFT JOIN LATERAL (… 最新年 CAS …) cas ON TRUE
WHERE v.venue_type = 'JOURNAL'
  AND v.deleted_at IS NULL
  AND v.id = ANY((:ids)::bigint[])
```

整批一条 SQL，顺序在查询服务里按请求重排，SQL 不排序。

## 9. 网关与索引

网关规则不改，补两类集成测试：

- `GatewayBlockedPathsIT`：路径表加 `/patra-catalog/_internal/venues/resolve/by-ids`、`/patra-catalog/_internal/venues/resolve/by-nlm-ids`；`POST` 带 JSON 体的用例也覆盖期刊路径。匿名与登录都是 403，WireMock 收不到请求。
- `GatewayRoutingIT`：匿名 `GET /patra-catalog/portal/venues/batch?id=1&id=2` 原样转发到下游（含重复参数），不带 `Authorization`。

interaction 调内部接口走 `lb://patra-catalog`，不经过网关。

| 查询 | 走的索引 |
|---|---|
| 解析按 ID、卡片按 ID | `cat_venue` 主键 |
| 解析按 NLM、导入的 `findByNlmIds`、`findSoftDeletedNlmIds` | `uk_venue_identifier_nlm`（V12 新建）或现有的 `idx_type_value (identifier_type, identifier_value)`：同一谓词两者都能用；`findByNlmIds` 是 JPQL 派生查询，类型走绑定参数，通用计划用不上部分索引，会用 `idx_type_value` |
| 卡片的最新年 JCR / CAS | `idx_jcr_venue_id`、`idx_cas_venue_id`（现有，浏览已在用） |

除 V12 的两条唯一约束外，不新增索引。Testcontainers 里只有几行数据，规划器总会选顺序扫描，看不出索引选择；部署后在 mini 的真实数据上对按 NLM 的 SQL `EXPLAIN` 一次（第 11.3 节），确认走 `uk_venue_identifier_nlm`。不写成断言（计划形状随统计信息变化）。

## 10. 测试策略

### 10.1 单元测试

- `VenueAggregateTest`：`fromPubMed` 写入主 NLM；`restore` 恢复传入的主 NLM；没有主 NLM 时第一次 `addIdentifier(NLM)` 成为主 NLM；已有主 NLM 时再加的 NLM 只进标识符、主 NLM 不变；删除主 NLM 被拒绝，删除副 NLM 可以；`addIdentifier` 其他类型不影响主 NLM。
- `VenuePubmedImportHandlerTest`（mock 仓储）：ISSN-L 指向 X、NLM 指向 Y 时更新 Y、不碰 X；副 NLM 命中它所在的期刊；NLM 属于软删期刊的记录跳过（不新建、不并入经 ISSN-L 命中的期刊，计入 skipped）；ISSN-L 命中且 NLM 无主时把 NLM 加到该期刊；现有批内去重用例保持通过。
- `VenueResolveQueryServiceTest`（mock 读端口）：按请求顺序输出；重复键只出现一次；查不到的键补 `found = null`；副 NLM 的键拿到主 NLM 的期刊；端口只被调一次，入参已去重。
- `PortalVenueCardQueryServiceTest`：按请求顺序重排；重复 ID 去重；端口没返回的 ID 不出现。
- 浏览卡片转换带 `nlmId`：catalog 没有覆盖期刊浏览转换的 `PortalApiConverterTest`（只有 `PortalApiConverterSuggestTest`），改由 `PortalVenueControllerIT` 对浏览与批量响应的 `nlmId` 断言覆盖。

### 10.2 集成测试

- infra（`@DataJpaTest` + `CatalogITPostgreSQLContainerInitializer`）：
  - `VenueRepositoryAdapterIT`：主 NLM 与已有期刊相同的期刊插入被 `uk_venue_nlm_id` 拒绝；把别的期刊已有的 NLM 加成副 NLM 被 `uk_venue_identifier_nlm` 拒绝；多个 `nlm_id` 为空的期刊可以共存；一个挂着两个 NLM 的期刊反复「读出 → 更新 → 刷新」后 `nlm_id` 不变；`findByNlmIds` 同时传主、副 NLM 时两个都返回同一期刊；`findSoftDeletedNlmIds` 只返回软删期刊的主、副 NLM。
  - `VenueResolveReadAdapterIT`：按 ID 返回正常与软删两种行（软删行带刊名、`deleted` 为真），非期刊类型与不存在的 ID 不返回；按 NLM 时主 NLM、副 NLM 都命中且 `nlmId` 为主 NLM，`0404401` 这类前导零与 `2985213R` 这类后缀原样往返。
  - `VenueBrowseReadAdapterIT`：`findCardsByIds` 返回存在且未软删的期刊，软删与非期刊不返回，`nlmId` 有值；浏览结果带 `nlmId`。
- adapter（`@WebMvcTest` + `CatalogAdapterITWebMvcConfig`，mock 查询服务）：
  - `VenueEndpointIT`：两个端点 200 与 JSON 结构（`id` 为字符串、`nlmId` 保留前导零、`exists: false` 行全为 null）；用切片上下文里的 `JsonMapper` 把响应体反序列化回 `VenueResolveResp`，断言 `id` 拿回 `Long`；空列表、101 个、非正整数、NLM 写法不对（小写后缀、超长、中间夹字母）→ 422 且不触达服务；`[1, null]` → 400。
  - `PortalVenueControllerIT`：`/batch` 200 与 `items` 结构、重复参数绑定为多个 ID；缺失、101 个、非正整数 → 422；浏览列表项带 `nlmId`。
- boot（e2eTest，`@SpringBootTest` + `CatalogE2EPostgreSQLContainerInitializer`）：
  - `VenuePubmedImportE2E`：mock `FileDownloadPort`（把 LSIOU 夹具复制成临时文件返回）、`DictionaryResolverPort`、`OpenAlexEnrichmentQueryPort`，经 `CommandBus` 跑真实的 `VenuePubmedImportHandler`。先用 SQL 预置期刊 X（主 NLM `0000001`、ISSN-L `1000-0001`）、Y（主 NLM `0000002`）、软删期刊 Z（主 NLM `0000003R`）。夹具含四条记录：NLM `0000002` + ISSN-L `1000-0001`（应更新 Y、不碰 X）；NLM `0000003R`（应跳过）；NLM `0000004` + ISSN-L `1000-0001`（应并进 X 成为副 NLM，X 的主 NLM 仍是 `0000001`）；NLM `9918523075306676`（应新建）。断言导入结果计数、每个 NLM 的归属、期刊总数；再导入一次，结果相同，主 NLM 不变，没有违反约束。这条用例就是 AC「导入遇到同一 NLM ID 时合并到已有期刊」的集成测试。
- gateway：第 9 节两类。

### 10.3 门控

`./gradlew spotlessApply` → `./gradlew check` → `:patra-api:patra-catalog:patra-catalog-infra:integrationTest`、`:patra-api:patra-catalog:patra-catalog-adapter:integrationTest`、`:patra-api:patra-catalog:patra-catalog-boot:e2eTest`、`:patra-api:patra-gateway-boot:integrationTest`。

## 11. 上线步骤

本节会被 PAP-120 汇总进 `docs/patra/runbooks/v0.14-discussion-go-live-runbook.md`。PAP-117 不需要回填数据，也不需要重跑任何导入。

### 11.1 合并后端 PR 之前：核对

后端 PR 合入 main 后，CD 会部署 catalog，Flyway 在启动时执行 V12。库里一旦有重复，迁移失败、catalog 起不来。所以**在合并前**，在 mini 上跑一次下面的只读核对（2026-10-27 的结果全部为 0）：

```bash
ssh mini 'export PATH="/opt/homebrew/bin:/usr/local/bin:$PATH";
  docker exec -i -e PGOPTIONS="-c default_transaction_read_only=on" patra-postgres \
  psql -U postgres -d patra_catalog -v ON_ERROR_STOP=1' <<'SQL'
BEGIN READ ONLY;
-- Q1 主 NLM 重复（含软删行），期望 0 行
SELECT nlm_id, array_agg(id ORDER BY id) FROM cat_venue
WHERE nlm_id IS NOT NULL GROUP BY nlm_id HAVING count(*) > 1;
-- Q2 同一个 NLM 挂在多个期刊上，期望 0 行
SELECT identifier_value, array_agg(venue_id ORDER BY venue_id) FROM cat_venue_identifier
WHERE identifier_type = 'NLM' GROUP BY identifier_value HAVING count(DISTINCT venue_id) > 1;
-- Q3 主 NLM 不在该期刊的标识符里，期望 0
SELECT count(*) FROM cat_venue v WHERE v.nlm_id IS NOT NULL AND NOT EXISTS (
  SELECT 1 FROM cat_venue_identifier i WHERE i.venue_id = v.id
    AND i.identifier_type = 'NLM' AND i.identifier_value = v.nlm_id);
-- Q4 有 NLM 标识符但主 NLM 为空，期望 0
SELECT count(*) FROM cat_venue v WHERE v.nlm_id IS NULL AND EXISTS (
  SELECT 1 FROM cat_venue_identifier i WHERE i.venue_id = v.id AND i.identifier_type = 'NLM');
ROLLBACK;
SQL
```

从核对到合并之间，不要手动触发 `venuePubmedImportJob`（XXL-Job 任务 11）：旧代码按 ISSN-L 优先匹配，可能把一个 NLM 挂到第二个期刊上。

### 11.2 核对不为 0 时

先不合并，把核对结果交给用户。下面任何一步都是写操作，逐条经用户同意后再执行：

- **Q2 不为 0**（副 NLM 挂错了期刊）：对每个这样的 NLM，保留「主 NLM 等于它」的那个期刊上的行，删掉其他期刊上的这一行。副 NLM 行只用于导入匹配与解析，删除不影响文献关联。如果它不是任何期刊的主 NLM，保留在关联文献最多的期刊上。
- **Q1 不为 0**（两个期刊的主 NLM 相同，即同一份刊物导入成了两个期刊）：合并成一个。保留关联文献多的那个（一样多时取 ID 小的），把 `cat_publication.venue_id`、`cat_venue_instance.venue_id`、`cat_venue_relation.related_venue_id` 改指向保留者；被合并期刊的标识符里，保留者没有的行改挂到保留者名下，其余删除；被合并期刊的 MeSH、关联、索引历史、年度统计和 JCR / CAS / Scopus / 预警评级直接删除（重跑期刊导入与富化任务可以再生）；最后删除被合并的期刊行。
- **Q3 不为 0**：给该期刊补一行 `NLM = 主 NLM` 的标识符。
- **Q4 不为 0**：该期刊只有一个 NLM 标识符时，把 `nlm_id` 设为它；有多个时交给用户选一个作主 NLM。

处理完重跑 11.1，全为 0 后再合并。

### 11.3 部署后核对

```bash
# 1. V12 已执行，约束与索引都在
ssh mini 'export PATH="/opt/homebrew/bin:/usr/local/bin:$PATH";
  docker exec patra-postgres psql -U postgres -d patra_catalog -At -c "
    SELECT max(version::int) FROM flyway_schema_history WHERE version IS NOT NULL;
    SELECT conname FROM pg_constraint WHERE conname = '"'"'uk_venue_nlm_id'"'"';
    SELECT indexname FROM pg_indexes WHERE indexname = '"'"'uk_venue_identifier_nlm'"'"';"'
# 期望：12 / uk_venue_nlm_id / uk_venue_identifier_nlm

# 2. 经网关访问内部接口是 403
curl -s -o /dev/null -w '%{http_code}\n' -X POST \
  http://100.103.73.27:9528/patra-catalog/_internal/venues/resolve/by-nlm-ids \
  -H 'Content-Type: application/json' -d '{"nlmIds":["0404401"]}'
# 期望：403

# 3. 在 mini 本机直连 catalog：主 NLM 与副 NLM 都解析到同一个期刊
ssh mini "curl -s -X POST http://127.0.0.1:6300/_internal/venues/resolve/by-nlm-ids \
  -H 'Content-Type: application/json' -d '{\"nlmIds\":[\"0404401\",\"100970884\"]}'"
# 期望：两行 exists=true、id 相同（核对时是 319059608675758187）、nlmId 都是 "0404401"

# 4. 门户批量卡片经网关可访问，带 nlmId
curl -s 'http://100.103.73.27:9528/patra-catalog/portal/venues/batch?id=319059608675758187'
# 期望：items[0].nlmId = "0404401"

# 5. 按 NLM 解析走新索引（只读）
ssh mini 'export PATH="/opt/homebrew/bin:/usr/local/bin:$PATH";
  docker exec patra-postgres psql -U postgres -d patra_catalog -c "EXPLAIN
    SELECT i.identifier_value, v.id FROM cat_venue_identifier i JOIN cat_venue v ON v.id = i.venue_id
    WHERE i.identifier_type = '"'"'NLM'"'"' AND i.identifier_value = ANY('"'"'{0404401,2985213R}'"'"'::text[])
      AND v.venue_type = '"'"'JOURNAL'"'"'"'
# 期望：Index Scan using uk_venue_identifier_nlm 或 idx_type_value（只记录，两者都够快，不作为上线门槛）
```

### 11.4 回滚

- **V12 在部署时失败**：PG 的 DDL 在事务里，Flyway 整体回滚，`flyway_schema_history` 里没有失败记录，catalog 起不来（健康检查不过）。按 11.2 处理掉重复后重启 catalog 即可；要先恢复服务，就回滚到上一个镜像（库结构没变）。
- **V12 上线后回滚到旧镜像**：两条约束留在库里，旧代码的读接口照常工作。但旧的期刊导入是 ISSN-L 优先，并且把标识符列表里第一个 NLM 写进 `nlm_id`：在新约束下可能让整批回滚，也可能把合并期刊的主 NLM 改成另一个。**回滚期间不要触发 `venuePubmedImportJob`（XXL-Job 任务 11）**；回到新镜像后再用。不需要撤销 V12。

## 12. README

`patra-api/patra-catalog/README.md`：

- 「PubMed Venue 数据导入」一条：匹配策略改为 NLM ID → ISSN-L → ISSN；补一句主 NLM 写入后不变、NLM 全表唯一、NLM 属于软删期刊的记录跳过。
- 「已实现功能」加一条「期刊身份与批量接口」（v0.14，PAP-117）：两个内部解析端点、门户 `/portal/venues/batch`、契约位置、NLM ID 写法，链接本 spec。
- 「架构位置」的调用方一句：interaction 也按期刊 ID 或 NLM ID 批量解析期刊。

## 13. 交给其他 Issue 的约束

**PAP-120（interaction 期刊对象）**：

- `patra-interaction-infra` 已依赖 `patra-catalog-api`；在 `CatalogClientConfiguration` 里照 `PublicationEndpoint` 用同一个 `RestClient` 再建一个 `VenueEndpoint` 代理。
- `nlm:` 锚的值校验用 `[0-9]{1,19}[A-Z]?`，与 catalog 一致。照搬 PMID 的 `[1-9][0-9]{0,14}` 会拒掉 3,652 个以 0 开头的真实 NLM ID，以及所有带 `R` / `A` 后缀的。
- 首次登记：`resolveByIds([期刊 ID])`。`exists: false`（不存在或不是期刊）→ 拒绝，不建登记行；`nlmId` 为 null → 没有锚，拒绝；`state: DELETED` 的期刊能不能首次登记由 PAP-120 定（catalog 只如实返回）。锚取返回的 `nlmId`（主 NLM）。
- 对账：`resolveByNlmIds`，每批不超过 100 个。**按响应的 `key` 对回登记行，不要按 `nlmId`**：请求的若是副 NLM（整库重导后主副互换），`nlmId` 与 `key` 不同。锚保持登记时的值不变；用返回的 `id` 刷新 `source_id`，`state` 映射为 ACTIVE / UNAVAILABLE，用 `title`（与 `abbreviatedTitle`）刷新快照。`exists: false` 的处理由 PAP-120 定。
- 收到 `RemoteCallException`（含超时、5xx）、连不上或注册中心没有实例：处理与文献相同。

**PAP-125（门户期刊投稿经验）**：

- 用户主页「投稿经验」等列表先从 interaction 拿到期刊 ID（及快照），再一次 `GET /patra-catalog/portal/venues/batch?id=…` 取卡片；响应里缺的 ID 用快照显示。
- 期刊卡片类型加 `nlmId: string`（浏览列表项同样带）。

## 14. AC 对照

| AC | 怎么证明 |
|---|---|
| mini 上核对无重复后约束上线；导入遇到同一 NLM ID 时合并到已有期刊（集成测试） | 第 4 节核对结论 + 第 11 节上线步骤（合并前重跑核对，部署后核对约束存在）；`VenuePubmedImportE2E`（真实处理器 + PG）+ `VenuePubmedImportHandlerTest` |
| 按 NLM ID 批量解析结果确定，前导零保留 | V12 的 `uk_venue_identifier_nlm`；`VenueResolveReadAdapterIT`（`0404401`、`2985213R`、副 NLM）；`VenueEndpointIT`（JSON 里的 `nlmId` 为原样字符串） |
| 一次批量不超过 100 个键；不存在的键返回「不存在」，软删期刊返回对应状态（集成测试） | `VenueEndpointIT`（101 个 → 422）；`VenueResolveReadAdapterIT`（软删行 `deleted`、不存在不返回）+ `VenueResolveQueryServiceTest`（补 `exists: false` 行）+ `VenueEndpointIT`（JSON 形状） |
| 内部接口经网关访问为 403 | `GatewayBlockedPathsIT`（`GET` / `POST`，匿名与登录）；部署后 curl（第 11.3 节） |

## 15. 设计中的决定

用户已定（2026-10-27）：

1. 导入匹配顺序改为 NLM → ISSN-L → ISSN。
2. 主 NLM 在期刊聚合上明确记录，第一次写入后不再改。
3. 按 NLM 批量解析时副 NLM 也对到同一个期刊，返回的 `nlmId` 仍是主 NLM。
4. 标识符表加部分唯一索引，NLM 类型的值全表唯一。

由我定：

1. 迁移版本 V12；约束名 `uk_venue_nlm_id`、`uk_venue_identifier_nlm`。
2. 契约叫 `VenueEndpoint`（沿用 catalog 的 Venue 词汇），路径 `/_internal/venues/resolve/by-ids`、`by-nlm-ids`，请求字段 `ids`、`nlmIds`；按 NLM 的读端口返回「命中的 NLM → 期刊」映射。
3. 展示字段只给期刊全称与 MEDLINE 缩写，不带 ISSN、封面。
4. 只认 `JOURNAL`：非期刊与不存在都是 `exists: false`；软删是 `DELETED`。
5. NLM ID 校验写法 `^[0-9]{1,19}[A-Z]?$`，区分大小写，不归一。
6. NLM 属于软删期刊的 LSIOU 记录：导入跳过，不复活，也不并入别的期刊。
7. 主 NLM 在聚合上显式建模；删除主 NLM 标识符被拒绝。
8. `findByNlmIds` 改为按标识符行建映射，主、副 NLM 都返回。
9. 门户 `/portal/venues/batch` 复用浏览卡片形状并加 `nlmId`，浏览列表随之多一个字段；`PortalVenueController` 不注册 `RepeatedParamListEditor`。
10. 「导入合并」的集成测试放在 boot 的 `VenuePubmedImportE2E`。
11. 不拆分 15 个合并期刊；`findByIssnLs` 的同类问题不改；`VenueEntity` 只改 `nlm_id` 那一条索引注解。

观察（不在本 Issue 内，2026-10-27 已核实）：期刊浏览接口按逗号拆分 `subject` 等列表参数（`PortalVenueListRequest` 有意如此，门户 `venue-browse.ts` 也用 `join(",")` 拼接），但 JCR 学科名本身可能带逗号：mini 上 113 个学科里有 30 个，如 `MATERIALS SCIENCE, MULTIDISCIPLINARY`。选中这类学科时，后端把它拆成两个值，筛不出任何期刊。修法要前后端一起改（例如改成重复参数并注册 `RepeatedParamListEditor`），已作为后续修复报给用户。

## 16. 已知限制

都是本版有意不处理的，记在这里，免得以后误以为是遗漏：

1. **ISSN-L 与 OpenAlex 富化数据会跟着挂到两个期刊上。** NLM 与 ISSN-L 指向不同期刊时按 NLM 匹配（第 6.3 节「代价」）。这条记录的 ISSN-L 会写进 NLM 那个期刊的标识符和 `issn_l` 列；OpenAlex 富化也按记录的 ISSN-L 取数，OPENALEX 标识符、`openalex_id`、引用指标、OA 信息也会写到这个期刊上。于是同一个 ISSN-L、同一个 OpenAlex ID 出现在两个期刊上，按 ISSN-L / ISSN 的查找对这一对期刊不再确定；文献导入在 NLM 查不到、退到 ISSN 兜底时也一样。导入会对这种期刊对打 WARN（「NLM ID 与 ISSN-L 指向不同期刊」），便于发现。mini 上目前没有这样的期刊对。
2. **假设 LSIOU 里一个 NlmUniqueID 只有一条记录。** NLM Unique ID 是 NLM 目录记录的主键，按理不会重复。新建路径已用本批已挂上的 NLM（`claimedNlmIds`）挡住重复；但如果重复的两条一条按 ISSN-L 命中期刊 X、另一条命中期刊 Y，更新路径仍会把同一个 NLM 挂到第二个期刊上，撞 `uk_venue_identifier_nlm`，整批（500 条）回滚、导入失败，不会写坏数据。真遇到时再按数据处理。
3. **`cat_venue_identifier.is_primary` 与主 NLM 是两套概念。** 「哪个 NLM 是主 NLM」只看 `cat_venue.nlm_id`；标识符行的 `is_primary` 现在一律是 false（导入从不设置），门户期刊详情按它排序标识符列表时也就没有区分。本版不改 `is_primary`，也不让 NLM 依赖它。

测试补充（2026-10-27 提交前）：`VenuePubmedImportHandlerTest` 加了 WARN 的两条用例（指向不同期刊时打、指向同一期刊时不打）；`VenueRepositoryAdapterIT` 加了「没有主 NLM 的期刊第一次拿到 NLM 后写进 `nlm_id`」，并用变异验证过：去掉 mapper 写主 NLM 的那一行，这条测试会失败。
