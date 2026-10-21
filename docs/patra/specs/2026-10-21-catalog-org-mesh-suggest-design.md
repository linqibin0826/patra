# catalog 机构（ROR）与 MeSH 建议接口工程设计（PAP-94）

> **Issue**：[PAP-94](https://linear.app/papertrace/issue/PAP-94)（GitHub #260）
> **版本**：[v0.12 User Center](../release-specs/v0.12-user-center.md)（2.1.2 资料字段、决策 F、边界 J）
> **前置设计**：[网关鉴权（PAP-65）](2026-10-09-gateway-auth-design.md)（`/*/_internal/**` 拒绝名单）、[identity 账号重建（PAP-93）](2026-10-20-identity-account-rebuild-design.md)（404 与错误契约的写法）
> **日期**：2026-10-21
> **状态**：已实施（2026-10-21，分支 `feat/v0.12-user-center-api`），四个待拍板项都按建议定下（第 17 节）；实测结果见第 11 节；别名种子第一版待用户审阅（第 16 节），经网关的 p95 待部署后再量

## 1. 要解决的问题

v0.12 的个人资料要让用户从 ROR 里选机构、从 MeSH 里选关注领域（release spec 2.1.2、决策 F）。catalog 已经导入了 ROR 全量机构（120,445 家）和 MeSH 全量主题词（31,110 条），但没有任何对外读接口。本 Issue 给门户加两个建议接口（输入框边打边搜），给 identity 加两个内部查询接口（保存时按 ID 校验并取名称快照），并把这两个内部接口的契约放进 `patra-catalog-api`。

用户对机构搜索的期望（2026-10-21 原话）：填机构时「一般都会直接打 协和」，全名、简称、拼音「都有可能」，希望第一条就命中。所以机构匹配的核心是**中文简称 / 全名 / 别名 / 英文名都能命中，且中国机构排前面**。

## 2. 范围

做：

- `GET /portal/organizations/suggest`、`GET /portal/mesh/suggest`（公开，经网关给门户）。
- `GET /_internal/organizations/{rorId}`、`GET /_internal/mesh/descriptors/{ui}`（内部，给 identity；网关拒绝外部访问）。
- `patra-catalog-api`：两个 `@HttpExchange` 接口与两个响应 DTO。
- catalog 的读端：读模型、读端口、查询服务、读适配器（native SQL）、控制器与内部端点实现。
- Flyway `V9`：`pg_trgm` 扩展 + 三个 trigram GIN 索引。
- 网关集成测试补两条拒绝路径与一条匿名放行路径（规则本身不用改）。
- catalog README。

不做：

- MeSH 中文标签层（release spec 边界 J）：只按英文主题词与入口词匹配，不为中文预留字段。
- 拼音搜索：ROR 数据里没有拼音，本版不做（2026-10-21 定，代价见第 16 节）。
- 模糊匹配 / 拼写纠错（trigram 相似度排序）：先按 LIKE 命中规则做，排序可解释；相似度排序对 2 字中文查询也无效（第 7.4 节）。
- 批量校验接口（一次传多个 MeSH UI）：Issue 定的是单个 GET，identity 保存最多 10 个关注领域，循环调用在服务内网里是毫秒级。
- 机构详情页、按 ID 查机构给门户：本版门户只有搜索选择，没有机构页。
- 按 MeSH UI 过滤文献检索：release spec 2.1.4 写了公开主页的关注领域「点标签进文献检索」，而文献检索现在只按标题子串匹配 `q`，没有 MeSH 过滤。2026-10-21 定：本版跳转到 `/papers?q=<英文主题词>`，不动文献检索的后端；按 MeSH 过滤另立 Issue。

做（2026-10-21 拍板后加入）：

- Patra 自维护的机构别名表 `cat_organization_alias` 与种子第一版（第 16 节方案 A）：ROR 中文名覆盖不全（第 4 节），由它补。

## 3. 已定的前提

| 前提 | 来源 |
|---|---|
| 资料里存 ROR ID + 名称快照、MeSH UI + 英文名快照，不存 catalog 内部 ID；门户的搜索选择调 catalog 建议接口；identity 保存时经 catalog 内部接口校验 ID 并取名称，不信前端传来的名称 | release spec 决策 F |
| 机构允许无 ROR 的自由文本兜底；名称 ≤ 200 | release spec 2.1.2 |
| 关注领域 ≤ 10 个，只显示英文 | release spec 2.1.2、边界 J |
| 内部接口按 ID 返回名称，不存在 404；identity 把 404 翻译成 422 | PAP-94 / PAP-95 的 To-Do |
| `/*/_internal/**` 在网关第一条过滤器链上对任何人 403；catalog 的其余路径（含 `/portal/**`）匿名放行 | PAP-65 设计第 6.1 节、`GatewaySecurityConfiguration.BLOCKED_PATHS` |
| 服务间调用走 Nacos 发现（`lb://patra-catalog`）+ `@HttpExchange` 代理，不经网关；下游 ProblemDetail 由 `patra-tool-http-client` 翻译成 `RemoteCallException` | catalog 调 registry 的现状（`HttpClientConfiguration`、`DictionaryResolverAdapter`） |
| 领域异常带 `StandardErrorTrait.NOT_FOUND` 即由错误引擎映射为 404，错误码 `<前缀>-0404`（catalog 运行时前缀 `CAT`，切片测试里设成 `CATALOG`） | `.claude/rules/tech/error-handling.md`、`VenueNotFoundException` |
| 适配器入口用 Bean Validation 校验请求记录，非法参数 422；Long 型 ID 不出现在本设计的响应里（只有 ROR ID 与 MeSH UI 两个自然键） | `PortalPublicationSearchRequest` 的做法 |
| 读端 native SQL 放 Dao，接口投影 `*Row`，读适配器转读模型；LIKE 转义符 `!` | `VenueBrowseReadAdapter` / `VenueDao` |
| catalog 的迁移是追加式（v0.11 新增 `V8`），库里有 PubMed 全量数据不重建 | catalog 现状 |
| 机器身份、服务间鉴权仍是 v0.8 边界 F 的事；内部接口靠网关拒绝名单 + tailnet 内网保护，与 registry / object-storage 相同 | 现状 |

版本：Java 25、Spring Boot 4.0.8、PostgreSQL 17.10（mini）/ `postgres:17`（Testcontainers）、`pg_trgm` 1.6（trusted 扩展，随官方镜像自带）。

## 4. 数据现状（2026-10-21 在 mini 的 `patra_catalog` 核实）

ROR 数据版本 `v2.0-2025-12-16`。

| 项 | 数 |
|---|---|
| 机构 | 120,445（active 118,084 / inactive 1,056 / withdrawn 1,305） |
| 名称行 | 259,560；`lang`：en 102,137、空 90,573（多为缩写与别名）、zh 5,166 |
| 名称类型 | `ror_display`+`label` 120,198、`acronym` 53,735、`alias` 48,397、`label` 37,035 |
| 中国机构（location 国家 CN） | 5,139，其中有 `zh` 名称的 4,247（83%） |
| 中国医疗机构（types 含 healthcare） | 1,049，其中有 `zh` 名称的 708（67%） |
| 中文名所在字段 | 一律是 `label`（官方名）或 `alias`（别名），`ror_display` 全是英文；缩写与英文别名的 `lang` 为空 |
| 中国机构中缺 zh 名但有 Wikidata ID 的 | 199 |

release spec 决策 F 写的「7,823 家中国机构（医疗类 1,079 家）」是 ROR 官网的口径，按 location 国家统计是上面的数。

「协和」在现有数据里的命中（全部 CN）：北京协和医院（`04jztag35`，label zh）、武汉协和医院（`0371fqr87`）、中国医学科学院北京协和医学院（`02drdmm93`，另有 alias「北京协和医学院」）、山东协和学院、福建师范大学协和学院。北京协和医院这条记录有 4 个名称：英文显示名、中文 label、英文别名「Beijing Xiehe Hospital」、缩写「PUMCH」。

**覆盖不足的证据**——按英文名找 40 家常见三甲医院，缺中文名的有：华西医院（`007mrxy13`，只有「四川大学华西医学中心」这条关联记录有中文）、齐鲁医院、同济医院、北京大学第一医院、华山医院、南方医院、南京鼓楼医院、武汉大学中南医院、湘雅二医院、上海胸科医院、郑大一附院、浙大一院 / 二院、哈医大一院、安医大一院、武大人民医院、青大附院、协和医院（福建）。另有若干中文名质量问题：北京安贞医院的 zh 是「北京市心肺血管疾病研究所」，湘雅三医院的 zh 是「中南大学湘雅三医院-门急诊楼」，北京大学首钢医院写成「首鋼」。结论：**「协和」「北京大学」「Mayo」这三条 AC 用现有数据就能过，但「华西」「齐鲁」这类同样常见的简称搜不到医院本身**，第 16 节给补救方案。

MeSH（2026 版）：主题词 31,110（class 1 主题 30,512、class 2 出版类型 194、class 3 核对词 2、class 4 地理 402）；入口词 995,917，其中主题词的非排列入口词 134,900（覆盖全部 31,110 个主题词）、排列入口词 132,112、SCR 入口词 728,905；树号 65,360。入口词表里包含主题词自己的首选词（`is_concept_preferred`）。没有任何主题词的名称等于别的主题词的入口词。

## 5. 业界参照

| 事 | 参照 | 本设计 |
|---|---|---|
| 边打边搜的子串 / 前缀匹配 | PostgreSQL `pg_trgm`：GIN + `gin_trgm_ops` 支持 `LIKE` / `ILIKE` 任意位置子串；模式里凑不出一个三元组（如 2 个汉字夹在两个 `%` 之间）时退化成全表扫 | 用 trigram GIN；2 字中文查询接受全表扫（实测 30 ms，第 11 节），不引入 `pg_bigm` |
| 机构搜索 | ROR API v2 的 `query` 按 `names.value`（含 alias、acronym）匹配，`affiliation` 端点返回按分数排序的候选 | 所有名称类型都参与匹配；排序改成可解释的规则（命中方式 → 中国优先 → 医疗优先 → 名称类型 → 短名优先） |
| 入口词映射 | PubMed 的 MeSH 数据库与 NLM MeSH Browser：输入「Cancer」命中入口词，返回主题词 Neoplasms | 入口词命中返回主题词，响应里带 `matchedTerm` 说明是经哪个入口词命中的 |

## 6. 模块与改动面

没有新模块，没有新依赖。

| 模块 | 改动 |
|---|---|
| `patra-catalog-api` | 新增 `endpoint/OrganizationEndpoint`、`endpoint/MeshEndpoint`（`@HttpExchange`）；`dto/organization/OrganizationResp`、`dto/mesh/MeshDescriptorResp` |
| `patra-catalog-domain` | 查询对象 `query/SuggestQuery`（含 `MatchMode`）；读模型 `model/read/organization/{OrganizationSummary, OrganizationSuggestion}`、`model/read/mesh/{MeshDescriptorSummary, MeshDescriptorSuggestion}`；读端口 `port/read/OrganizationReadPort`、`port/read/MeshDescriptorReadPort`；异常 `OrganizationNotFoundException`、`MeshDescriptorNotFoundException`（`NOT_FOUND`） |
| `patra-catalog-app` | 查询服务 `usecase/organization/query/OrganizationQueryService`、`usecase/mesh/query/MeshDescriptorQueryService`（`@Transactional(readOnly = true)`） |
| `patra-catalog-infra` | `V9__add_suggest_trigram_indexes.sql`（扩展、索引、别名表）与 `R__cat_organization_alias_seed.sql`（种子）；`OrganizationNameDao`、`OrganizationDao`、`MeshDescriptorDao` 各加 native 查询与投影 `adapter/read/*Row`；读适配器 `adapter/read/OrganizationReadAdapter`、`adapter/read/MeshDescriptorReadAdapter` |
| `patra-catalog-adapter` | 控制器 `rest/portal/PortalOrganizationController`、`rest/portal/PortalMeshController`；请求记录 `rest/portal/request/PortalSuggestRequest`；响应 `rest/portal/response/PortalSuggestionsResponse<T>`、`PortalOrganizationSuggestionResponse`、`PortalMeshSuggestionResponse`；`PortalApiConverter` 加两个转换；内部端点 `rest/internal/OrganizationEndpointImpl`、`rest/internal/MeshEndpointImpl` |
| `patra-gateway-boot` | 只加集成测试：`GatewayBlockedPathsIT` 的拒绝名单加两条 catalog 内部路径；`GatewayRoutingIT` 加一条匿名、带中文查询串的建议请求原样转发 |
| `patra-catalog` README | 功能 14、内部接口一节、数据模型加索引说明 |

包位置按 `.claude/rules/tech/port-service.md`：`ReadPort` 在 `domain/port/read/`，`ReadAdapter` 在 `infra/adapter/read/`，`QueryService` 在 `app/usecase/{domain}/query/`。内部端点实现放 `adapter/rest/internal/`（object-storage 的做法）。

## 7. 匹配与排序规则

### 7.1 查询归一化（`SuggestQuery`）

`SuggestQuery.of(String raw, int limit)`，两个建议接口共用：

1. Unicode NFKC 归一（全角字母数字、全角空格变半角）→ 去首尾空白 → 连续空白折叠成一个空格。
2. 归一后按码点计长：**2 ~ 100**，否则 `IllegalArgumentException`（适配器层先用注解拦成 422，值对象只是守卫）。
3. `MatchMode`：含任一 `UnicodeScript` 为 HAN / HIRAGANA / KATAKANA / HANGUL 的字符 → `SUBSTRING`（中日韩没有词边界，任意位置子串）；否则 → `WORD_PREFIX`（整串前缀，或某个词的开头，不匹配词中间）。
4. `limit`：1 ~ 20，控制器缺省 10。

LIKE 模式由读适配器拼：转义 `!`、`%`、`_`（转义符 `!`，SQL 里 `ESCAPE '!'`），三种模式 `q%`（前缀）、`% q%`（词首）、`%q%`（子串）。精确比较用 `lower(列) = lower(:q)`，不走 LIKE。大小写：`ILIKE`，对中日韩无影响。

### 7.2 机构

**参与匹配的名称**：`cat_organization_name` 全部行（`ror_display`、`label`、`alias`、`acronym`，任何语言）。只返回 `status = 'active'` 且未软删的机构——`withdrawn` 是 ROR 标记的错误记录，`inactive` 是已关闭或并入他处的机构，不该成为一个人的当前单位；选不到的用自由文本兜底。

**候选与去重**：先在名称表里按模式取候选，给每条名称打分后只保留每家机构最好的一条（`DISTINCT ON (org_id)`），再关联机构主表、中文名、地点，最后排序取 `limit` 条。候选上限 500（按下面的命中方式、名称类型、长度排好再截断）：泛词如「University」「医院」有几万条命中，截断后再做机构级关联与排序，耗时才可控；500 条里再按中国优先排，足够把中国机构顶上来。

**排序键**（依次）：

1. 命中方式：精确（0）> 前缀（1）> 词首（2）> 子串（3）。
2. 中国优先：机构第一条地点的 `country_code = 'CN'`。
3. 医疗优先：`types` 含 `healthcare`。Patra 的用户是医生与医学科研人员，「协和」同时命中北京协和医院、武汉协和医院、山东协和学院时医院排前面。
4. 名称类型：`ror_display` / `label`（0）> `alias`（1）> `acronym`（2）。
5. 命中名称更短的在前（短名通常是更常用的名）。
6. 英文显示名字典序（稳定）。

**返回的字段**：`rorId`（9 位裸 ID）、`displayName`（ROR 显示名，英文）、`zhName`（`lang = 'zh'` 的名称，优先 `label`，其次 `alias`，同类取最短；没有则 `null`）、`matchedName`（命中的那条名称原文，门户可高亮或标注「经别名 PUMCH 命中」）、`countryCode`、`cityName`（同名机构靠城市区分：ROR 里有三家「Union Hospital」、两家「Mayo Hospital」）、`types`（ROR 的类型码，如 `healthcare`、`education`）。

在 mini 全量数据上的结果（第 11 节同一批探针）：「协和」→ 北京协和医院、武汉协和医院、山东协和学院、福建师范大学协和学院、中国医学科学院北京协和医学院；「北京大学」→ 北京大学、北京大学肿瘤医院、北京大学人民医院…（7 家）；「Mayo」→ Mayo Clinic 第一；「PUMCH」→ 北京协和医院（经缩写命中）；「union hospital」→ 福建医科大学附属协和医院（精确）、武汉协和医院（英文别名命中）…；「Cancer」→ 中国的肿瘤医院排在美国的 CancerCare 前面。

### 7.3 MeSH

**参与匹配**：`cat_mesh_descriptor.name`，以及 `cat_mesh_entry_term` 里 `record_type = 'DESCRIPTOR'` 且 `is_permuted_term = false` 的入口词（134,900 条）。排列入口词（「Mellitus, Diabetes」这种换序形式）不要：词首匹配已经覆盖「mellitus → Diabetes Mellitus」，少索引 13 万行。SCR（补充概念）不参与：关注领域只选主题词。

**只给 class 1（主题类）且 `active_status` 的主题词**：出版类型（「Review」）、核对词、地理词不是「研究领域」。

**匹配模式**：前缀或词首（`q%` / `% q%`）；MeSH 全是英文，不做词中子串。查询含中日韩字符时照样执行，自然返回空（边界 J）。

**排序键**：命中方式（主题词名精确 0 > 入口词精确 1 > 主题词名前缀 2 > 入口词前缀 3 > 主题词名词首 4 > 入口词词首 5）→ 主题词名更短优先 → 名称字典序。候选同样先截 500 再按主题词去重。精确命中放在所有前缀命中之前，所以「Cancer」第一条是 Neoplasms（入口词精确），「Cancer Pain」「Cancer Vaccines」排其后；「Diabetes」第一条是 Diabetes Mellitus（最短的前缀命中）；「Neoplasms」精确第一。

**返回的字段**：`ui`、`name`（主题词英文名）、`matchedTerm`（经入口词命中时的入口词原文；命中的是主题词名本身、或入口词与名称只差大小写时为 `null`）、`treeNumbers`（全部树号，`is_primary` 的在前，其余按字典序；门户用第一个树号的首字母判断大类，如 `C` 疾病、`D` 化学物质）。

### 7.4 明确不做的匹配

- 词中子串（「ayo」不命中 Mayo）：中日韩以外的查询只做前缀与词首，避免泛匹配。
- 拼写容错 / 相似度排序：`pg_trgm` 的 `similarity` 对 2 字中文（三元组几乎全是补白）没有意义；英文的拼写纠错不是本版问题。
- 2 字中文查询的毫秒级优化：`%协和%` 凑不出三元组，走全表扫，mini 上 30 ms。可以用「字间插空格的生成列」让 2 字查询也命中索引（`北 京 协 和 医 院` 里有三元组 `协 和`），本版不做：200 ms 的目标有六倍余量，边打边搜本身带 200 ms 以上的防抖，这 30 ms 用户感受不到。

## 8. 接口契约

### 8.1 `GET /portal/organizations/suggest`

查询参数：`q`（必填，归一后 2 ~ 100 个字符）、`limit`（可选，1 ~ 20，缺省 10）。

```json
{
  "items": [
    {
      "rorId": "04jztag35",
      "displayName": "Peking Union Medical College Hospital",
      "zhName": "北京协和医院",
      "matchedName": "北京协和医院",
      "countryCode": "CN",
      "cityName": "Beijing",
      "types": ["funder", "healthcare"]
    }
  ]
}
```

没有命中返回 `{ "items": [] }`，不 404。

### 8.2 `GET /portal/mesh/suggest`

参数同上。

```json
{
  "items": [
    { "ui": "D009369", "name": "Neoplasms", "matchedTerm": "Cancer", "treeNumbers": ["C04"] },
    { "ui": "D000072716", "name": "Cancer Pain", "matchedTerm": null,
      "treeNumbers": ["C23.888.592.612.212", "F02.830.816.444.416", "G11.561.790.444.432"] }
  ]
}
```

### 8.3 `GET /_internal/organizations/{rorId}`

`rorId` 是 9 位裸 ID（`^0[a-z0-9]{8}$`，小写）。格式不对直接 404，不查库。只返回 `active` 且未软删的机构，其余 404。

```json
{
  "rorId": "04jztag35",
  "displayName": "Peking Union Medical College Hospital",
  "zhName": "北京协和医院",
  "countryCode": "CN",
  "types": ["funder", "healthcare"]
}
```

### 8.4 `GET /_internal/mesh/descriptors/{ui}`

`ui`：`^D\d{6}(\d{3})?$`（`D003920`、`D000072716`，与领域里 `MeshUI` 的主题词正则一致）。格式不对直接 404。按 UI 精确查，**不限 class 与 `active_status`**（这是通用的主题词查询，将来 interaction 的标签也能用）；identity 从建议接口拿到的 UI 一定是 class 1，这里不再重复产品规则。

```json
{ "ui": "D003920", "name": "Diabetes Mellitus", "treeNumbers": ["C18.452.394.750", "C19.246"] }
```

### 8.5 错误契约

| 情况 | 状态 | 说明 |
|---|---|---|
| `q` 缺失 / 归一后不足 2 字 / 超 100 字；`limit` 不在 1 ~ 20 | 422 | Bean Validation 在适配器层拦下，不触达应用层；ProblemDetail 由 `patra-tool-web` 统一输出 |
| 内部接口 ID 不存在、格式不对、机构非 active | 404 | `OrganizationNotFoundException` / `MeshDescriptorNotFoundException` 带 `NOT_FOUND`，错误码 `CAT-0404` |
| 建议接口没有命中 | 200 | 空列表 |

identity（PAP-95）收到 `RemoteCallException` 且特征含 `NOT_FOUND` 时按它自己的 Issue 回 422。

### 8.6 `patra-catalog-api`

```java
@HttpExchange(url = "/_internal/organizations", accept = "application/json")
public interface OrganizationEndpoint {
  @GetExchange("/{rorId}")
  OrganizationResp getOrganization(@PathVariable("rorId") String rorId);
}

@HttpExchange(url = "/_internal/mesh", accept = "application/json")
public interface MeshEndpoint {
  @GetExchange("/descriptors/{ui}")
  MeshDescriptorResp getDescriptor(@PathVariable("ui") String ui);
}

/// 5 个字段，@Builder
public record OrganizationResp(
    String rorId, String displayName, String zhName, String countryCode, List<String> types) {}

/// 3 个字段，of()
public record MeshDescriptorResp(String ui, String name, List<String> treeNumbers) {}
```

两个 record 的 `List` 字段在紧凑构造器里 `List.copyOf`。服务端 `OrganizationEndpointImpl implements OrganizationEndpoint`（`@RestController`，Spring MVC 认 `@HttpExchange`），客户端由 identity 用 `RestClientFactory.createProxy` 建代理（第 14 节）。`patra.hexagonal-api` 插件已经给了 `spring-web`（compileOnly）与 `jakarta.validation`，api 模块的构建脚本不用改。

## 9. 网关

不改规则：`/patra-catalog/_internal/**` 命中第一条过滤器链的 `/*/_internal/**`，匿名与登录都 403 且下游收不到请求；`/patra-catalog/portal/**` 走第二条链的 `anyRequest().permitAll()`，匿名放行、已登录的照样写入身份断言。补三条集成测试：`GatewayBlockedPathsIT` 的路径表加 `/patra-catalog/_internal/organizations/04jztag35` 与 `/patra-catalog/_internal/mesh/descriptors/D003920`；`GatewayRoutingIT` 加「匿名 `GET /patra-catalog/portal/organizations/suggest?q=协和&limit=5` 原样转发到下游、不带 `Authorization`」。

identity 调 catalog 内部接口不经网关（`lb://patra-catalog`），所以拒绝名单不影响它。

## 10. 索引与 SQL

### 10.1 `V9__add_suggest_trigram_indexes.sql`

```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_org_name_value_trgm
    ON cat_organization_name USING gin (name_value gin_trgm_ops);

CREATE INDEX idx_mesh_desc_name_trgm
    ON cat_mesh_descriptor USING gin (name gin_trgm_ops)
    WHERE descriptor_class = '1' AND active_status;

CREATE INDEX idx_entry_term_term_trgm
    ON cat_mesh_entry_term USING gin (term gin_trgm_ops)
    WHERE record_type = 'DESCRIPTOR' AND is_permuted_term = false;
```

- `pg_trgm` 是 trusted 扩展，mini 上 catalog 用 `postgres` 账号，Testcontainers 的 `postgres:17` 镜像自带 contrib；`CREATE EXTENSION` 在 `patra_catalog` 库内生效。
- 两个局部索引的谓词与查询的 `WHERE` 逐字相同，规划器才会选它。实测大小：机构名 22 MB、主题词名 2 MB、入口词 8 MB；建索引在 mini 上几秒内完成，服务启动时由 Flyway 执行。
- 不动 `cat_organization_name` 等表的列，JPA 实体不变。

### 10.2 机构建议（`OrganizationNameDao`）

两个 native 查询（`SUBSTRING` / `WORD_PREFIX` 各一个，只有候选的 `WHERE` 不同），共享的骨架：

```sql
WITH cand AS (
  SELECT n.org_id, n.name_value,
         CASE WHEN lower(n.name_value) = lower(:q) THEN 0
              WHEN n.name_value ILIKE :prefix ESCAPE '!' THEN 1
              WHEN n.name_value ILIKE :wordStart ESCAPE '!' THEN 2 ELSE 3 END AS match_rank,
         CASE WHEN jsonb_exists(n.types, 'ror_display') OR jsonb_exists(n.types, 'label') THEN 0
              WHEN jsonb_exists(n.types, 'alias') THEN 1 ELSE 2 END AS type_rank
  FROM cat_organization_name n
  WHERE n.name_value ILIKE :contains ESCAPE '!'                      -- SUBSTRING 模式
     -- WORD_PREFIX 模式：(n.name_value ILIKE :prefix ESCAPE '!' OR n.name_value ILIKE :wordStart ESCAPE '!')
  ORDER BY match_rank, type_rank, length(n.name_value)
  LIMIT 500),
best AS (
  SELECT DISTINCT ON (c.org_id) c.*
  FROM cand c ORDER BY c.org_id, c.match_rank, c.type_rank, length(c.name_value))
SELECT o.ror_id AS "rorId", o.display_name AS "displayName", b.name_value AS "matchedName",
       zh.name_value AS "zhName", l.country_code AS "countryCode", l.city_name AS "cityName",
       array_to_string(ARRAY(SELECT jsonb_array_elements_text(o.types)), ' ') AS "types"
FROM best b
JOIN cat_organization o ON o.id = b.org_id AND o.status = 'active' AND o.deleted_at IS NULL
LEFT JOIN LATERAL (SELECT z.name_value FROM cat_organization_name z
                   WHERE z.org_id = o.id AND z.lang = 'zh'
                   ORDER BY CASE WHEN jsonb_exists(z.types, 'label') THEN 0 ELSE 1 END,
                            length(z.name_value), z.name_value LIMIT 1) zh ON TRUE
LEFT JOIN LATERAL (SELECT loc.country_code, loc.city_name FROM cat_organization_location loc
                   WHERE loc.org_id = o.id ORDER BY loc.id LIMIT 1) l ON TRUE
ORDER BY b.match_rank, (l.country_code = 'CN') DESC NULLS LAST,
         jsonb_exists(o.types, 'healthcare') DESC, b.type_rank, length(b.name_value), o.display_name
LIMIT :limit
```

注意：Spring Data 的 native 查询里不能写 jsonb 的 `?` 操作符（会被当成位置参数），所以用 `jsonb_exists()`；`types` 以空格拼成一个字符串返回，读适配器再拆成列表（PG 数组到 `String[]` 的投影不稳）。`cat_organization.types` 的 DDL 是 JSONB，实体上的 `columnDefinition = "JSON"` 只是注解文字。

**实施时加的第三条查询（评审修正）**：`SUBSTRING` 模式且归一后只有 2 个码点（「协和」「华西」）的查询走 `suggestByShortSubstring`，候选条件写成 `lower(n.name_value) LIKE lower(:contains) ESCAPE '!'`（别名表同样）。原因：`%协和%` 凑不出三元组，psql 里用字面量规划会选顺序扫描（30 ms），但 pgjdbc 默认第 5 次起改用服务端预编译语句，PG 之后可能改用通用计划，通用计划对参数化的 `ILIKE` 会选 trigram 索引，运行时变成扫完整个 22 MB 索引再对 259,560 行 recheck——mini 上 `PREPARE` + `plan_cache_mode = force_generic_plan` 实测 220 ~ 380 ms。列上套 `lower()` 后规划器用不了索引，两种计划都是顺序扫描（实测 34 ms）。3 个码点起的子串模式照旧用 `ILIKE`，有三元组时通用计划选索引是对的。

别名表里 `alias_type = 'LABEL'` 的候选 `type_rank` 取 0（视同官方名），不是第 16 节原文的「视同 alias（1）」：LABEL 本来就是可作显示名的全名，排在 ROR 的 alias 前合理。

### 10.3 机构查询（`OrganizationDao`）

同样的 `SELECT` 列（去掉 `matchedName`），`WHERE o.ror_id = :rorId AND o.status = 'active' AND o.deleted_at IS NULL`，走 `uk_ror_id`。

### 10.4 MeSH 建议（`MeshDescriptorDao`）

```sql
WITH cand AS (
  SELECT d.ui, d.name, NULL::varchar AS matched_term,
         CASE WHEN lower(d.name) = lower(:q) THEN 0
              WHEN d.name ILIKE :prefix ESCAPE '!' THEN 2 ELSE 4 END AS match_rank
  FROM cat_mesh_descriptor d
  WHERE d.descriptor_class = '1' AND d.active_status
    AND (d.name ILIKE :prefix ESCAPE '!' OR d.name ILIKE :wordStart ESCAPE '!')
  UNION ALL
  SELECT t.owner_ui, d.name, t.term,
         CASE WHEN lower(t.term) = lower(:q) THEN 1
              WHEN t.term ILIKE :prefix ESCAPE '!' THEN 3 ELSE 5 END
  FROM cat_mesh_entry_term t
  JOIN cat_mesh_descriptor d ON d.ui = t.owner_ui
  WHERE t.record_type = 'DESCRIPTOR' AND t.is_permuted_term = false
    AND d.descriptor_class = '1' AND d.active_status
    AND (t.term ILIKE :prefix ESCAPE '!' OR t.term ILIKE :wordStart ESCAPE '!')),
capped AS (SELECT * FROM cand ORDER BY match_rank, length(coalesce(matched_term, name)) LIMIT 500),
best AS (SELECT DISTINCT ON (ui) * FROM capped
         ORDER BY ui, match_rank, length(coalesce(matched_term, name)))
SELECT b.ui AS "ui", b.name AS "name",
       CASE WHEN b.matched_term IS NULL OR lower(b.matched_term) = lower(b.name)
            THEN NULL ELSE b.matched_term END AS "matchedTerm",
       (SELECT string_agg(tn.tree_number, ' ' ORDER BY tn.is_primary DESC, tn.tree_number)
        FROM cat_mesh_tree_number tn WHERE tn.descriptor_ui = b.ui) AS "treeNumbers"
FROM best b
ORDER BY b.match_rank, length(b.name), b.name
LIMIT :limit
```

### 10.5 MeSH 查询

`SELECT d.ui, d.name, (树号聚合) FROM cat_mesh_descriptor d WHERE d.ui = :ui`，走 `uk_mesh_ui`。

## 11. 性能验证

**目标**：建议接口在 mini 的全量数据上 p95 < 200 ms（AC 4）。

**已做（2026-10-21，SQL 级）**：在 mini 的 `patra_catalog` 里开事务、按第 10.1 节建索引、跑第 10.2 / 10.4 节的最终 SQL、`ROLLBACK`，库里没留痕迹。每条查询 `EXPLAIN (ANALYZE)` 跑 3 次取第 3 次（热缓存）：

| 机构查询 | ms | 机构查询 | ms | MeSH 查询 | ms |
|---|---|---|---|---|---|
| 协和 | 34.9 | University | 34.6 | Diabetes | 0.6 |
| 华西 | 30.6 | Hospital | 21.8 | Cancer | 1.9 |
| 北京大学 | 0.1 | union hospital | 1.7 | Neoplasms | 2.5 |
| 人民医院 | 1.6 | Peking University | 1.5 | mellitus | 0.4 |
| 医院 | 32.6 | ab | 4.7 | ca | 17.3 |
| 大学 | 35.5 | Un | 28.4 | disease | 9.5 |
| 中山 | 31.1 | Med | 16.7 | He / Pa | 13.2 / 10.2 |
| 医学院 | 0.4 | Johns Hopkins | 0.5 | Hypertension | 4.0 |
| Mayo / Harvard / PUMCH | 0.5 / 0.4 / 0.2 | General Hospital | 5.2 | cell / protein | 9.1 / 16.6 |

38 条查询最差 35.5 ms，p95 ≈ 35 ms。慢的两类都是全表扫：2 字中文（凑不出三元组）与 2 ~ 3 字母的英文泛前缀（候选几万条）。冷缓存首跑最高 85 ms（「大学」）。

**实施阶段要做（HTTP 级）**：本机起 catalog 连 mini 的库（日常开发方式，Flyway 顺便把 `V9` 应用到 mini），用 curl 对上表 38 条查询各打 10 次、取 `time_total` 的 p95，写回本节「实际：」行。经网关的数字要等版本 PR 合并、CD 部署后再量一次，记在 release spec 的 Done 判定里（收尾待办）。

**实际（2026-10-21，实施后，本机起 catalog 连 mini 的库）**：

- Flyway 在 mini 的 `patra_catalog` 上应用 `V9`（三个 trigram GIN 索引 + `cat_organization_alias`）与别名种子，共 3.9 s。`EXPLAIN` 核实：`%北京大学%` 走 `idx_org_name_value_trgm`（0.17 ms）；MeSH 名与入口词走两个局部索引（0.24 ms / 2.5 ms）；别名表只有 600 多行，规划器选 Seq Scan（0.18 ms），合理。
- 全量数据上的结果：「协和」→ 北京协和医院、武汉协和医院、福建医科大学附属协和医院（中文名来自种子）、山东协和学院、福建师范大学协和学院；「华西」→ 四川大学华西医院（经别名「华西」命中）、四川大学华西第二医院、四川大学华西医学中心；「Cancer」→ Neoplasms（经入口词 Cancer）、Cancer Pain、Cancer Vaccines；`/_internal/organizations/04jztag35` 与 `/_internal/mesh/descriptors/D003920` 返回摘要，`0zzzzzzz9` 404 + `CAT-0404`；`02h2j1586` 的 `zhName` 是种子压过的「北京安贞医院」。
- HTTP 级（本机 → tailscale 隧道 → mini 的库，ping RTT 25 ms）：机构建议 20 条查询 × 10 次 p50 114 ms / p95 248 ms / max 365 ms；MeSH 18 条 × 10 次 p50 101 / p95 265 / max 697 ms；内部按 ID 查（单行主键查询）40 次 p50 92 / p95 254 ms；`/actuator/health` p50 107 ms。单行主键查询也要 90 ms，说明本机量到的是每个请求 3 ~ 4 次 JDBC 往返 × 25 ms 的网络开销，不是查询本身（SQL 级最差 35 ms）。**服务与库同在 mini 时这部分是亚毫秒级，所以 AC 4 的 HTTP 级数字要在部署到 mini 后经网关再量**（收尾待办）；查询本身的上限是上表的 35 ms。
- 评审发现的通用计划问题（第 10.2 节）：原 `ILIKE :contains` 的 SQL 在 mini 上 `PREPARE` 后 `force_generic_plan` 执行，「协和」「医院」走 trigram 索引全扫 + 全表 recheck，220 ~ 380 ms（`force_custom_plan` 32 ~ 37 ms）；改成 `lower()` 条件的短子串查询后两种计划都是 Parallel Seq Scan，34 ms（首跑 62 ms）。部署后经网关量的时候，每条语句要先打 10 次以上让 pgjdbc 切到服务端预编译，并带上 2 字中文查询。

## 12. 测试策略

### 12.1 单元测试（不起 Spring）

- `SuggestQueryTest`：NFKC、去空白折叠、长度边界（1 字拒、2 字收、100 收、101 拒）、`MatchMode` 判定（纯英文 → `WORD_PREFIX`，含汉字 / 假名 / 谚文 → `SUBSTRING`，中英混合 → `SUBSTRING`）、`limit` 边界。
- `OrganizationReadAdapterTest` / `MeshDescriptorReadAdapterTest`：mock Dao，断言拼出的三个 LIKE 模式与转义（`100%` → `100!%%`）、`types` 与 `treeNumbers` 字符串拆成列表、空字符串拆成空列表。
- `OrganizationQueryServiceTest` / `MeshDescriptorQueryServiceTest`：委托读端口；`findByRorId` 空 → `OrganizationNotFoundException`。
- `PortalSuggestRequestTest`：紧凑构造器的去空白折叠。
- `PortalApiConverterTest`：两个读模型到响应的转换。

### 12.2 集成测试

- infra（`@DataJpaTest` + `CatalogITPostgreSQLContainerInitializer`，Flyway 跑到 `V9`）：
  - `OrganizationReadAdapterIT`：用 `TestEntityManager` 造机构 + 名称 + 地点。用例：「协和」命中两家医院且医院排在学院前、CN 排在非 CN 前；经缩写命中返回 `matchedName`；精确 > 前缀 > 词首；`WORD_PREFIX` 模式不命中词中（「ayo」空）；一家机构两条名称都命中只出现一次且取最好的一条；`inactive` / `withdrawn` / 软删不出现；`limit`；`%`、`_`、`!` 当字面量；`zhName` 取 label 不取 alias；`findByRorId` 命中 / 不存在 / 非 active 为空。
  - `MeshDescriptorReadAdapterIT`：名称精确第一；入口词精确返回主题词并带 `matchedTerm`；入口词等于名称时 `matchedTerm` 为空；排列入口词不参与；class 2 不出现；词首命中；树号 primary 在前；`findByUi` 命中 / 不存在 / class 2 也能查到。
  - 这两个 IT 同时证明 `V9` 的扩展与索引在干净库上能建（索引是否被选用在 mini 上用 `EXPLAIN` 看，第 11 节）。
- adapter（`@WebMvcTest` + `CatalogAdapterITWebMvcConfig`，mock 查询服务）：
  - `PortalOrganizationControllerIT` / `PortalMeshControllerIT`：200 与 JSON 结构；缺省 `limit = 10`；`q` 缺失、1 字、101 字、`limit = 0` / `21` → 422 且不触达服务；`q` 前后空白被折叠后传给服务（`ArgumentCaptor`）。
  - `OrganizationEndpointIT` / `MeshEndpointIT`：200 JSON；不存在 → 404 + `CATALOG-0404`。「格式不对（`abc`、`D12`）→ 404 且不查库」由两个查询服务的单测覆盖（切片里服务是 mock）。
- gateway（`@SpringBootTest` + Redis 容器 + WireMock，已有基座）：第 9 节的三条。

### 12.3 门控

`./gradlew spotlessApply` → `./gradlew check` → `:patra-api:patra-catalog:patra-catalog-infra:integrationTest`、`:patra-api:patra-catalog:patra-catalog-adapter:integrationTest`、`:patra-api:patra-gateway-boot:integrationTest`（`check` 不含 `integrationTest`）。

## 13. README 与文档

- `patra-api/patra-catalog/README.md`：「已实现功能」加第 14 条（机构与 MeSH 建议 + 内部查询）；新增「内部接口」一节列两个 `_internal` 路径与契约位置；「数据模型」里机构与 MeSH 表下注明 trigram 索引；变更日志加一行。
- 本文档第 11 节写回实测数字；release spec 的 Done 判定与 Linear 由版本收尾统一处理，本 Issue 不改 release spec。

## 14. 交给其他 Issue 的约束

**PAP-95（identity 资料）**：

- `patra-identity-infra` 依赖 `:patra-api:patra-catalog:patra-catalog-api`；`patra-identity-boot` 加 `patra-tool-http-client`，在 `HttpClientConfiguration` 里用 `RestClientFactory.createRestClient(builder, "catalog", "lb://patra-catalog")` 建 `RestClient`，再 `createProxy` 出 `OrganizationEndpoint`、`MeshEndpoint`；`application.yml` 加 `patra.http-client.http-interface.groups.catalog.base-url: lb://patra-catalog`（照 catalog 调 registry 的写法）。
- 捕获 `RemoteCallException`，`getErrorTraits()` 含 `NOT_FOUND` → 该字段 422 带原因码；其他远程错误按意外异常处理，不要把 catalog 不可用降级成「保存成功但没校验」。
- 名称快照规则（待拍板 3，建议）：机构快照 = `zhName`，没有才用 `displayName`；MeSH 快照 = `name`。`rorId` 存裸 ID。
- 关注领域最多 10 个，逐个调 `GET /_internal/mesh/descriptors/{ui}`。
- 机构只在 `rorId` 与库里已存的不同（新选或改选）时才调 `GET /_internal/organizations/{rorId}` 校验，只改简介不重校验。ROR 重导后用户已选的机构可能变成 `inactive`，内部接口对它回 404，不能让它挡住与机构无关的保存；已存的快照照旧显示。
- 调内部接口前先在本地按 `^0[a-z0-9]{8}$` / `^D\d{6}(\d{3})?$` 校验 ID，不合规直接回 422：URL 形式的 ROR ID（`https://ror.org/…`）作为路径段会被编码成 `%2F`，Tomcat 直接 400 且不是 ProblemDetail，到不了 catalog 的 404。

**PAP-101（门户）**：

- 机构与关注领域的搜索框经门户 Route Handler 调 `GET /patra-catalog/portal/organizations/suggest` / `/mesh/suggest`，防抖 ≥ 200 ms，归一后不足 2 字不发请求。
- 机构候选显示：有 `zhName` 用它当主行、`displayName` 当副行；没有就 `displayName` 主行；副行带城市与国家（`countryCode` 用 `Intl.DisplayNames` 转中文）；`matchedName` 与主副行都不同时标「经 PUMCH 命中」。选不到走自由文本。
- MeSH 候选显示：`name` 主行；`matchedTerm` 非空时副行「via Cancer」；大类由第一个树号的首字母映射（16 个大类的中文标签在门户里维护）。
- 公开主页的关注领域标签点击跳 `/papers?q=<英文主题词>`（待拍板 4 另定的话以那为准）。

## 15. AC 对照

| AC | 怎么证明 |
|---|---|
| 用「协和」「北京大学」「Mayo」各能搜到对应 ROR 机构，中文别名命中 | 第 7.2 节的 mini 结果；`OrganizationReadAdapterIT` 的三个用例；HTTP 级实测（第 11 节） |
| 用「Diabetes」「Neoplasms」能搜到主题词，入口词命中时返回的是主题词 | 第 7.3 节的 mini 结果；`MeshDescriptorReadAdapterIT` 的「Cancer → Neoplasms」用例 |
| 内部接口按 ID 返回名称；不存在返回 404；经网关从外部访问被拒 | `OrganizationEndpointIT` / `MeshEndpointIT` 的 200 与 404；`GatewayBlockedPathsIT` 的两条路径 403 且 WireMock 收不到请求 |
| 建议接口在 mini 的全量数据上 p95 < 200 ms（实测记录） | 第 11 节：SQL 级 p95 ≈ 35 ms（已测）；HTTP 级本机连 mini 库实测（实施时）；经网关的数字在部署后补 |
| `./gradlew check` + `integrationTest` 全绿 | 第 12.3 节 |

## 16. 覆盖不足的补救：Patra 自维护的机构别名表（待拍板）

第 4 节已证明 ROR 的中文名覆盖不全（中国医疗机构 33% 没有中文名，华西、齐鲁、同济、北大一院这种常见简称搜不到医院本身），而且 ROR 的 curation 周期不由我们控制。可选方案：

**方案 A：别名表（建议）**。`cat_organization_alias(id, ror_id, alias, lang, alias_type, source, 审计列)`，唯一键 `(ror_id, alias)`，GIN trigram 索引；用 `ror_id` 而不是内部 ID 关联，ROR 重导后内部 ID 变了也不失效。建议查询的候选 `cand` 多一个 `UNION ALL` 读这张表（命中方式同样打分，`type_rank` 视同 `alias`）。种子数据放 Flyway 可重复迁移 `R__cat_organization_alias_seed.sql`（每次内容变了就重跑：先删 `source = 'SEED'` 的行再插入），来源两路：

1. Wikidata 的 zh 标签与别名（CC0）：ROR 里 199 家缺中文名的中国机构有 Wikidata ID，可以一次性脚本取回；release spec 边界 J 已经为 MeSH 中文层选了 Wikidata，同一依据。
2. 手工：按常见三甲医院清单（第 4 节那 18 家起步，目标百家左右）补中文全名与简称（「四川大学华西医院」「华西医院」「华西」）。由 Claude 起草、用户审一遍。

选 A 时还定下三点：

- `zhName`（建议接口与内部接口共用）的取值顺序改为：别名表里 `alias_type = 'LABEL'` 且 `lang = 'zh'` 的行 > ROR `label` > ROR `alias`，同级取最短。这样「华西」命中的华西医院主行与 identity 的快照都是中文；第 4 节那三条错的中文名也能被种子压过（给安贞医院一条 LABEL「北京安贞医院」即可）。
- 别名表的 `alias_type` 只有 `LABEL`（可作显示名的中文全名）与 `ALIAS`（简称、别名、旧名，只参与匹配）；`source` 为 `SEED`（手工种子）或 `WIKIDATA`。
- 交付程度：本 Issue 交付机制 + 种子第一版——Wikidata 能取到的 199 家自动生成，再加手工百家左右（从第 4 节那 18 家起，按常见三甲清单补），由 Claude 起草；用户审完种子文件是 PR 转 ready 前的一个阻塞点。
- 第一版（2026-10-21，`R__cat_organization_alias_seed.sql`）：手工 374 行（305 条 LABEL + 69 条 ALIAS，覆盖三条压名与 300 多家缺中文名的医院）+ Wikidata 247 行（199 家里 107 家有 zh 标签；Wikidata 的 SPARQL 端点当天限流到每分钟 1 次，改用 `wbgetentities` 动作 API），共 621 行、374 条 LABEL。
- **一家机构只能有一条 zh LABEL**（评审修正，`V10` 的局部唯一索引 `uk_org_alias_zh_label`）：`ON CONFLICT (ror_id, alias)` 只能去掉文字相同的重复，手工 LABEL 与 Wikidata LABEL 文字不同时两条都入库、最短者当 `zhName`，审过的名字会被压掉（第一版里有 7 家，如徐州医科大学附属医院被旧名「徐州医学院附属医院」压过）。所以种子生成规则：某机构已有手工 LABEL 时，Wikidata 的 LABEL 降为 ALIAS；三条繁体标签（微創醫療、鞍鋼股份、中國中材）只作 ALIAS；误标成 zh 的英文「Coolpad」删掉。`V10` 先删种子行再建索引，已应用过 `V9` 的库（mini）靠种子文件校验和变化重跑 `R__` 重新插入。
- 审阅时留意：`042jtt364` 的 LABEL 是 2018 年前的旧名「空军总医院」，现名「空军特色医学中心」只作 ALIAS，快照该用哪个由用户定；手工 305 条 LABEL 与 ROR ID 是否逐条对应同一家机构，建议在 mini 上把 `cat_organization_alias` 与 `cat_organization` 按 `ror_id` 连起来导一张审阅表（英文名、城市、状态、LABEL、来源）核对。

代价：一张表 + 一个 `UNION ALL` + 一份种子文件（本 Issue 多出约四分之一的工作量：建表、查询、IT 各加一组用例，种子起草半天）；之后每次有人报「搜不到」就改种子文件、随下一次部署生效，没有后台界面。不做的代价：这些医院的用户只能用英文名搜，或填自由文本（没有 ROR ID，公开主页上只是一段文字）。

**方案 B：本版只用 ROR 数据 + 自由文本兜底**。零额外工作；建议查询的 `cand` 结构已经为将来加 `UNION ALL` 留好位置，以后再做是纯增量。

**拼音（待拍板 2）**：ROR 数据没有拼音。要做的话是在方案 A 的别名表里由系统生成（ROR 导入时对每条中文名生成全拼与首字母两条 `alias_type = PINYIN` 的行，如「beijingxiehe yiyuan」「bjxhyy」），依赖一个拼音库（倾向 TinyPinyin：带城市词典，多音字比 pinyin4j 准，是第 6 节「没有新依赖」的例外）。ROR 导入只往空表写（表里有数据就拒绝），所以不是「重跑导入」，而是另写一个按名称表生成拼音行的回填任务并在 mini 跑一次。代价约一天；多音字仍会错一部分（「重庆」「长沙」靠词典兜住，机构名里的其它多音字兜不住）。我的建议是本版不做：用户用中文输入法时搜索框收到的就是汉字，拼音只对「刻意不切输入法」的场景有用，而且拼音只对有中文名的机构有效，覆盖缺口比拼音更要紧。

## 17. 设计中由我定的点与待拍板的点

**由我定的（评审时核对）**：

1. 建议与内部查询都只认 `active` 且未软删的机构；`withdrawn` / `inactive` 一律不出现。
2. 排序里加了「医疗机构优先」这一键（排在中国优先之后、名称类型之前）。
3. 中日韩以外的查询只做前缀与词首，不做词中子串；不做模糊匹配。
4. 2 字中文查询接受全表扫（30 ms），不加「字间插空格」的生成列。
5. 查询归一做 NFKC；长度 2 ~ 100；`limit` 1 ~ 20、缺省 10；候选截 500。
6. MeSH 建议只给 class 1 且 active 的主题词、排除排列入口词与 SCR；内部查询不限 class。
7. 内部接口的 ID 格式不对直接 404，不查库、不 422。
8. 建议响应用 `{ "items": [...] }` 信封，不是裸数组；响应里不带缓存头；不做批量查询接口。
9. 响应字段：机构给 `zhName` + `displayName` + `matchedName` + `countryCode` + `cityName` + `types`；MeSH 给 `matchedTerm` + 全部 `treeNumbers`。
10. `V9` 是追加迁移，不重写 `V4` / `V5`（catalog 库有 PubMed 全量数据，不重建）。
11. 网关不改代码，只补测试。
12. 用户已存的机构在 ROR 重导后变成 `inactive` 时，内部接口照样 404；由 PAP-95 只在 `rorId` 变化时才校验机构，避免只改简介也被挡（第 14 节）。不给 `OrganizationResp` 加 `status` 字段。
13. 别名表 LABEL 的 `type_rank` 取 0（视同官方名），不是第 16 节原文的「视同 alias」（第 10.2 节）。
14. 2 码点的子串查询走 `lower()` 条件的第三条查询，绕开预编译语句通用计划下的 trigram 全索引扫描（第 10.2 节）；不用全局的 `prepareThreshold=0` 或 `plan_cache_mode`，不碰别的查询。
15. 一家机构只能有一条 zh LABEL，用 `V10` 的局部唯一索引守住，种子按此生成（第 16 节）。

**已拍板（2026-10-21，用户按建议定）**：

1. **机构别名表**：做方案 A，纳入本 Issue；种子第一版由 Claude 起草、用户审（PR 转 ready 前的阻塞点）。
2. **拼音**：本版不做。
3. **名称快照取中文优先**：是。PAP-95 的快照 = `zhName`，为空才用 `displayName`；PAP-101 的候选主行同理。
4. **「点标签进文献检索」**：本版只跳转 `/papers?q=<英文主题词>`；按 MeSH UI 过滤文献另立 Issue，不进 PAP-94。
