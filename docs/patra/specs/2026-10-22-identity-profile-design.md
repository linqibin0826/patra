# identity 资料与公开主页工程设计（PAP-95）

> **Issue**：[PAP-95](https://linear.app/papertrace/issue/PAP-95)（GitHub #261）
> **版本**：[v0.12 User Center](../release-specs/v0.12-user-center.md)（2.1.2 资料、2.1.4 公开主页、2.1.5 隐私设置、决策 A / F）
> **前置设计**：[identity 账号重建（PAP-93）](2026-10-20-identity-account-rebuild-design.md)、[catalog 机构与 MeSH 接口（PAP-94）](2026-10-21-catalog-org-mesh-suggest-design.md)
> **日期**：2026-10-22
> **状态**：已实施（2026-10-22，分支 `feat/v0.12-user-center-api`）；实施时的结论与回归结果见第 18 节

## 1. 要解决的问题

PAP-93 给每个用户建了一行只有昵称的资料（`idn_user_profile` 骨架）和一张只含用户名、昵称的公开身份卡（`GET /users/{username}`）。本 Issue 把资料补成最终形态——简介、链接、专业背景（身份大类、职称、机构、科室）、关注领域、三个隐私开关——给本人读写的接口，并把公开身份卡扩成公开主页：按隐私开关裁剪、按开关决定谁能看到。

机构和关注领域只存权威自然键（ROR ID、MeSH UI）加名称快照（决策 F）。identity 保存时经 PAP-94 的内部接口校验 ID 并取名称，不信前端传来的名称。

设计过程中定下的、影响别的 Issue 的事：

- 昵称在 `PUT /me/profile` 里改，传空就是清空（回到跟随用户名）；没有单独的改昵称接口（第 8.1 节）。
- 公开主页响应加 `indexable`（门户据此输出 `noindex`），这是响应里唯一一个由设置推出来的字段（第 8.3 节，已定）。
- 本人看自己的主页能看到「仅自己可见」的专业背景；链接在邮箱未验证时对谁都不出现，本人也一样（第 7.4 节）。
- catalog 不可用时保存资料返回 503，不降级成「保存了但没校验」（第 9 节）。

**完成标准**：PAP-95 的 Acceptance Criteria，逐条对应见第 14 节。

## 2. 范围

做：

- `V1` 原地改写：`idn_user_profile` 加简介、链接、身份大类、职称、机构、科室、三个隐私开关；新建 `idn_user_profile_link`、`idn_user_research_interest`。
- 领域：`UserProfile` 聚合补齐；值对象 `Bio`、`ProfileLink`、`Occupation`、`JobTitle`、`Affiliation`、`Department`、`RorId`、`MeshUi`、`ResearchInterest`、`PrivacySettings` 与两个可见性枚举；`ProfilePolicy`（字段规则）；`ProfileTextRules`（从 `DisplayNamePolicy` 抽出的拒绝字符判定，昵称与资料文本共用）；端口 `CatalogReferencePort`。
- identity 经 `patra-catalog-api` 的 `OrganizationEndpoint`、`MeshEndpoint` 调 catalog：infra 适配器 `CatalogReferenceAdapter`，boot 注册代理。
- 接口：`GET/PUT /me/profile`、`GET/PUT /me/privacy`；`GET /users/{username}` 扩成公开主页。
- 测试：单元、Testcontainers 集成、WireMock 桩 catalog。
- identity README。

不做（都有归属）：

- 头像（PAP-97）。`avatarUrl` 仍恒为 `null`。
- 门户的资料页、隐私页、公开主页（PAP-101）。
- 注销时资料的清理（PAP-100），本 Issue 只交代清理清单（第 15 节）。
- 给其他服务的用户摘要接口（边界 G）。
- MeSH 中文标签（边界 J）：只存英文名快照，不为中文预留列。
- 快照的回刷：catalog 改了机构名或主题词名，已存的快照照旧，用户重选才更新（决策 F 的「快照」含义）。
- 网关规则：不改，只补一条集成测试（第 10 节）。

## 3. 已定的前提

| 前提 | 来源 |
|---|---|
| 资料是账号旁边的独立聚合，与 `idn_user` 一对一；资料改得勤，不和账号共用乐观锁 | 决策 A |
| 字段与长度：简介纯文本 ≤ 200 字素、最多 3 行；链接 ≤ 5 条，标题 ≤ 30 字、URL ≤ 300 字符，只收 `http` / `https`；身份大类八个枚举值可空；职称 ≤ 50；机构 ROR ID + 名称快照或无 ROR 的自由文本，名称 ≤ 200；科室 ≤ 100；关注领域 MeSH ≤ 10 个，存 UI + 英文名快照，按用户排序；拒绝的字符同昵称；除用户名外都不强制 | release spec 2.1.2 |
| 链接邮箱未验证时不展示，渲染为 `rel="nofollow noopener noreferrer ugc"`（门户做） | release spec 2.1.2 |
| 自述的四个专业背景字段按原样展示，不带认证外观 | release spec 2.1.2、边界 B |
| 主页头部：头像、昵称、`@用户名`、简介、加入时间（到月）、链接；专业背景整块受开关控制；关注领域是 MeSH 标签 | release spec 2.1.4 |
| 404 矩阵：主页可见性不是「公开」（对匿名）或是「仅自己」（对登录用户）；邮箱未验证；冷静期或已注销；被封禁。本人看自己不受约束，`audience` 为 `SELF` | release spec 2.1.4、PAP-93 第 8.5 节 |
| 隐私：主页可见性 公开 / 仅登录 / 仅自己，默认公开；专业背景可见性 公开 / 仅自己，默认公开；搜索引擎收录 开 / 关，默认关；邮箱永不展示、没有开关 | release spec 2.1.5 |
| 存 ROR ID 与 MeSH UI，不存 catalog 内部 ID；保存时经 catalog 内部接口校验并取名称；不信前端名称；无 ROR 的自由文本兜底 | 决策 F |
| 只做英文 MeSH，不为中文预留 | 边界 J（2026-10-21 定） |
| 内部接口：`GET /_internal/organizations/{rorId}`（`^0[a-z0-9]{8}$`，非 active 也 404）、`GET /_internal/mesh/descriptors/{ui}`（`^D\d{6}(\d{3})?$`）；404 带 `NOT_FOUND` 特征；机构快照 `zhName ?? displayName`，MeSH 快照 `name`；只在 `rorId` 变化时校验机构；调用前本地校验格式；catalog 不可用不能降级成未校验 | PAP-94 第 8.3 ~ 8.6、14 节，快照中文优先已拍板 |
| `/me/**` 写处理器先 `findByIdForUpdate` 再 `requireActive()`；`PENDING_DELETION` 409、`BANNED` / `DELETED` 401 | PAP-93 第 7.2、16 节 |
| 昵称可空，空 = 跟随用户名；`DisplayNamePolicy` 已有；`/auth/me` 读昵称已完成 | PAP-93 第 7.4、16 节 |
| identity 库推倒重建，改 `V1` 不写 `ALTER` | 决策 J |
| 新表不得有 `username` / `user_name` 列（R6） | `.claude/rules/project-info.md` |
| 时间走注入的 `Clock`；Long 型 ID 在 JSON 里是字符串；OSIV 已关 | 测试约定、`patra-tool-web`、identity `application.yml` |

## 4. 业界参照

| 事 | 参照 | 本设计 |
|---|---|---|
| 简介长度 | Bluesky `app.bsky.actor.profile.description`：`maxGraphemes: 256`、`maxLength: 2560`；GitHub bio 160 字符；X 160 | 200 字素（release spec）+ UTF-8 800 字节（与昵称同为 4 字节 / 字素），挡住 ZWJ 堆叠 |
| 链接 | GitHub：最多 4 个社交链接，只有 URL；Mastodon：4 组「标签 + 值」，值可验证；ORCID：标题 + URL | 5 条「标题（可空）+ URL」；标题为空时门户显示域名 |
| 可见性档位 | GitHub 私密资料（只隐藏活动）；Mastodon「隐藏关注」「不被搜索引擎收录」默认关闭收录（`noindex`）；ResearchGate 资料对未登录可见 | 三档主页可见性 + 专业背景单独一档 + 收录开关默认关（release spec） |
| 机构 | ORCID 用 ROR / Ringgold 自然键 + 名称，允许无标识的自由文本 | 同 |
| 有序标签 | ORCID 关键词（有序、≤ 用户自定） | 子表带 `position` |

来源：[atproto actor profile lexicon](https://github.com/bluesky-social/atproto/blob/main/lexicons/app/bsky/actor/profile.json)、[Mastodon 资料字段](https://docs.joinmastodon.org/user/profile/)、[ORCID 机构标识符](https://info.orcid.org/documentation/integration-guide/working-with-organization-identifiers)。

## 5. 模块与改动面

没有新模块。

| 模块 | 改动 |
|---|---|
| `patra-identity-domain` | `UserProfile` 补齐；新值对象与枚举（第 7.1 节）；`ProfilePolicy`、`ProfileTextRules`；`DisplayNamePolicy` 改为调用 `ProfileTextRules`（行为不变）；读模型 `PublicUserReadModel` 扩展、新增 `PublicUserView`、`PublicAudience` 加 `SIGNED_IN`；端口 `CatalogReferencePort`；异常 `CatalogUnavailableException`；`UserFieldViolations` 加资料字段与原因码 |
| `patra-identity-app` | `UpdateProfileCommand` / `Handler`、`UpdatePrivacyCommand` / `Handler`；`UserQueryService` 加 `myProfile()`、`myPrivacy()`，`publicIdentity` 返回裁剪后的视图 |
| `patra-identity-infra` | `V1` 改写；`UserProfileEntity` 加列；新实体 `UserProfileLinkEntity`、`UserResearchInterestEntity` 与 Dao；`UserProfileRepositoryAdapter` 连带链接与关注领域一起存取；`UserReadAdapter` 读出公开主页；`CatalogReferenceAdapter`；依赖 `patra-catalog-api` |
| `patra-identity-adapter` | `MeProfileController`（`/me/profile`、`/me/privacy`）及请求 / 响应记录；`PublicUserResponse` 扩展 |
| `patra-identity-boot` | 依赖 `patra-tool-http-client`；新增 `CatalogClientConfiguration` 注册两个代理；`application.yml` 加 `catalog` 组；`IdentityConfiguration` 声明 `ProfilePolicy`、`ProfileDisplayPolicy`；测试配置打开 SimpleDiscoveryClient；README |
| `patra-gateway-boot` | 规则不改；`GatewayAuthenticationIT` 补一条用例（第 10 节） |
| `module-graph.json` | identity-infra 新增对 catalog-api 的依赖，重新生成 |

包位置按 `.claude/rules/tech/port-service.md`：`CatalogReferencePort` 在 `domain/port/catalog/`，实现在 `infra/adapter/catalog/`；处理器在 `app/usecase/profile/`；值对象在 `domain/model/vo/profile/`。

## 6. 表结构（改写 `V1`）

### 6.1 `idn_user_profile`（在骨架上加列）

| 列 | 类型 | 约束 |
|---|---|---|
| `display_name` | `VARCHAR(120)` | 不变（PAP-93） |
| `bio` | `VARCHAR(800)` | 可空；`ck_idn_user_profile_bio`：`bio IS NULL OR (octet_length(bio) BETWEEN 1 AND 800 AND bio = btrim(bio) AND bio !~ '\r')` |
| `occupation` | `VARCHAR(16)` | 可空；`ck_idn_user_profile_occupation`：`IN ('PHYSICIAN','NURSE','PHARMACIST','TECHNICIAN','RESEARCHER','STUDENT','INDUSTRY','OTHER')` |
| `job_title` | `VARCHAR(200)` | 可空；`octet_length BETWEEN 1 AND 200`，等于 `btrim` |
| `organization_ror_id` | `VARCHAR(9)` | 可空；`ck_idn_user_profile_ror_id`：`organization_ror_id ~ '^0[a-z0-9]{8}$'` |
| `organization_name` | `VARCHAR(800)` | 可空；`ck_idn_user_profile_organization`：`organization_ror_id IS NULL OR organization_name IS NOT NULL`（有 ID 必有快照）；`organization_ror_id IS NOT NULL OR organization_name IS NULL OR octet_length(organization_name) <= 800`（自由文本的字节上限）；非空时等于 `btrim` |
| `department` | `VARCHAR(400)` | 可空；`octet_length BETWEEN 1 AND 400`，等于 `btrim` |
| `profile_visibility` | `VARCHAR(16)` | 非空，默认 `'PUBLIC'`；`IN ('PUBLIC','SIGNED_IN','SELF')` |
| `professional_visibility` | `VARCHAR(16)` | 非空，默认 `'PUBLIC'`；`IN ('PUBLIC','SELF')` |
| `search_indexing` | `BOOLEAN` | 非空，默认 `false` |

- **字节上限**统一取「字素上限 × 4」，和昵称（30 字素 / 120 字节）同一比例：简介 800、标题 120、职称 200、科室 400、机构自由文本 800。字素数据库数不了，由代码守；库守字节与首尾空白。`VARCHAR(n)` 按字符计，字节 ≤ n 时字符必然 ≤ n，所以列长取字节上限即可。
- **机构名两种来源两个上限**：自由文本受 200 字素 / 800 字节约束；快照来自 catalog（`cat_organization.display_name` 是 `VARCHAR(500)`），不受用户输入的上限约束。列长取 800 个字符，两种来源都放得下：快照最多 500 个字符；自由文本 800 字节以内，字符数不会超过 800。2026-10-22 在 mini 的 `patra_catalog` 实测：英文显示名最长 196 字符、中文名最长 77 字符，现有数据全部在 200 以内，所以「快照不受 200 约束」目前只是兜底。这是对 release spec 2.1.2「名称 ≤ 200」的细化：200 约束用户手填的名字，快照以 catalog 为准（第 16 节同步改 release spec 与 AC 1 的措辞）。
- **快照入库前去首尾空白**：适配器对 catalog 返回的名称先 `strip()`，空白的 `zhName` 视为没有；这样快照也满足「等于 `btrim`」的约束，catalog 数据里偶有的首尾空格不会让保存 500。
- **空串**一律存 `NULL`：代码规范化后为空就是「没填」，库里的 `BETWEEN 1 AND …` 守住不出现空串。
- **链接**单独成表（6.2）：和关注领域同一种「有序、有上限、整组替换」的形状，用同一套存取写法；仓库里 jsonb 列要经 `JsonNode` 手工拼数组，比子表更绕。
- **隐私开关**放在资料表而不是单独一张表：它们和资料同生同灭、一对一，读公开主页时一次查询就要用到；PUT `/me/privacy` 与 PUT `/me/profile` 在同一行上，靠用户行锁串行（第 8 节），不会撞乐观锁。

### 6.2 `idn_user_profile_link`（新）

| 列 | 类型 | 约束 |
|---|---|---|
| `id` | `BIGINT` | 主键，雪花 ID |
| `user_id` | `BIGINT` | 非空；外键 `fk_idn_user_profile_link_user` → `idn_user(id)` |
| `position` | `SMALLINT` | 非空；`ck_idn_user_profile_link_position`：`BETWEEN 0 AND 4` |
| `title` | `VARCHAR(120)` | 可空；`octet_length BETWEEN 1 AND 120`，等于 `btrim` |
| `url` | `VARCHAR(300)` | 非空；`ck_idn_user_profile_link_url`：`url ~* '^https?://'` |

加审计列与触发器。约束：`uk_idn_user_profile_link_position (user_id, position)`；唯一索引 `uk_idn_user_profile_link_url ON (user_id, lower(url))`。`position` 上限 4 加唯一约束，库里就守住「≤ 5 条、不重复」。

### 6.3 `idn_user_research_interest`（新）

| 列 | 类型 | 约束 |
|---|---|---|
| `id` | `BIGINT` | 主键，雪花 ID |
| `user_id` | `BIGINT` | 非空；外键 `fk_idn_user_research_interest_user` → `idn_user(id)` |
| `position` | `SMALLINT` | 非空；`ck_idn_user_research_interest_position`：`BETWEEN 0 AND 9` |
| `mesh_ui` | `VARCHAR(10)` | 非空；`ck_idn_user_research_interest_mesh_ui`：`mesh_ui ~ '^D[0-9]{6}([0-9]{3})?$'` |
| `mesh_name` | `VARCHAR(255)` | 非空，英文名快照；长度与 `cat_mesh_descriptor.name` 一致（实测最长 104） |

加审计列与触发器（照 `V1` 写法）。约束：`uk_idn_user_research_interest_position (user_id, position)`、`uk_idn_user_research_interest_mesh_ui (user_id, mesh_ui)`。索引：`idx_idn_user_research_interest_mesh_ui (mesh_ui)`——「对某主题感兴趣的用户」是以后推荐要用的查询，这也是关注领域单独成表的另一个原因。

`position` 上限 9 加两条唯一约束，库里就守住了「≤ 10 个、不重复、顺序唯一」。

## 7. 领域模型

### 7.1 值对象与枚举（`domain/model/vo/profile/`）

| 名称 | 内容 |
|---|---|
| `Bio` | 规范化后的简介（7.2）。紧凑构造器只接受已规范化的非空值 |
| `ProfileLink` | `title`（可空，规范化后的单行文本）、`url`（原样去首尾空白后的字符串）；`of(title, url)` |
| `Occupation` | 枚举：`PHYSICIAN`、`NURSE`、`PHARMACIST`、`TECHNICIAN`、`RESEARCHER`、`STUDENT`、`INDUSTRY`、`OTHER` |
| `JobTitle` / `Department` | 规范化后的单行文本，各自的长度上限 |
| `RorId` | 9 位裸 ID，小写；`violation(raw)` / `of(raw)`：去首尾空白、转小写后按 `^0[a-z0-9]{8}$` 判。不接受 `https://ror.org/…` 形式：门户拿到的建议结果就是裸 ID |
| `MeshUi` | `^D\d{6}(\d{3})?$`；`of(raw)` 去首尾空白，不改大小写（UI 的 `D` 是大写） |
| `Affiliation` | 密封接口：`Registered(RorId rorId, String name)`（name 是快照）、`FreeText(String name)` |
| `ResearchInterest` | `(MeshUi ui, String name)`，name 是快照 |
| `ProfileVisibility` | 枚举：`PUBLIC`、`SIGNED_IN`、`SELF` |
| `ProfessionalVisibility` | 枚举：`PUBLIC`、`SELF` |
| `PrivacySettings` | `(ProfileVisibility profile, ProfessionalVisibility professional, boolean searchIndexing)`；`defaults()` = 公开 / 公开 / 关 |

可见性的取值和 `PublicAudience` 同名：`PublicAudience` 是「这个主页实际能被谁看到」，设置是「用户希望被谁看到」，账号正常且邮箱已验证时两者相等。

### 7.2 文本规范化与校验（`ProfileTextRules`、`ProfilePolicy`）

**拒绝字符**从 `DisplayNamePolicy.isRejectedCluster` 抽到 `ProfileTextRules.hasRejectedCharacter(text, allowLineFeed)`：昵称、链接标题、职称、科室、机构自由文本传 `false`（规则与昵称完全相同）；简介传 `true`，只多放行 U+000A。`DisplayNamePolicy` 改为调用它，原有单测不改照样绿。

**单行文本**（标题、职称、科室、机构自由文本）的规范化与昵称相同：`DisplayName.normalize`（NFC、空格类折叠、去首尾空白）。

**简介**的规范化：NFC → `\r\n` 与单独的 `\r` 换成 `\n` → 每一行内的空格类字符折叠成一个空格并去掉行首行尾空白 → 整体去首尾空行。只做到这里：中间的空行保留（算行数）。

**`ProfilePolicy.parse(ProfileDraft) → ParsedProfile`**：所有字段一起查、错误一次报全（`InvalidUserFieldsException`，422），每个字段只报第一条：

| 字段（`errors[].field`） | 规则，依次 | 原因码 |
|---|---|---|
| `displayName` | `null` 或规范化后为空：清空（不是错误）；否则走 `DisplayNamePolicy` | 同 PAP-93 |
| `bio` | 空：清空；拒绝字符（放行换行）；> 200 字素或 > 800 字节；> 3 行 | `INVALID_CHARACTER`、`TOO_LONG`、`TOO_MANY_LINES` |
| `links` | 列表 `null` 视为空；> 5 条 | `TOO_MANY` |
| `links[i].url` | 空；> 300 个码点；含空白或控制字符；`URI` 解析失败、scheme 不是 `http` / `https`（不分大小写）、没有 host、带 userinfo（`https://patra.dev@evil.example` 这类冒充）；与前面某条 URL 相同（大小写不敏感比较整串） | `REQUIRED`、`TOO_LONG`、`INVALID_FORMAT`、`DUPLICATE` |
| `links[i].title` | 空：没有标题（不是错误）；拒绝字符；> 30 字素或 > 120 字节 | `INVALID_CHARACTER`、`TOO_LONG` |
| `occupation` | 空：清空；不是八个值之一（区分大小写） | `INVALID_FORMAT` |
| `jobTitle` | 空：清空；拒绝字符；> 50 字素或 > 200 字节 | `INVALID_CHARACTER`、`TOO_LONG` |
| `organization.rorId` | 非空时：格式不对 | `INVALID_FORMAT` |
| `organization.name` | 只在 `rorId` 为空时看：空 → 没有机构；拒绝字符；> 200 字素或 > 800 字节。`rorId` 非空时 `name` 被忽略，不校验 | `INVALID_CHARACTER`、`TOO_LONG` |
| `department` | 空：清空；拒绝字符；> 100 字素或 > 400 字节 | `INVALID_CHARACTER`、`TOO_LONG` |
| `researchInterests` | 列表 `null` 视为空；> 10 个 | `TOO_MANY` |
| `researchInterests[i]` | 空；格式不对；与前面某个相同 | `REQUIRED`、`INVALID_FORMAT`、`DUPLICATE` |

catalog 校验后才知道的错误（第 9 节）：`organization.rorId` / `NOT_FOUND`、`researchInterests[i]` / `NOT_FOUND`，同样是 422、可以有多条。

`ParsedProfile` 带着规范化后的值对象，以及「要解析名称的」机构 ID 与 MeSH UI 列表；名称由处理器填（第 8.1 节）。

**和库里相同的值不重新校验**：`parse(draft, current)` 拿当前资料作对照，单行文本、简介、链接标题规范化后与库里存的完全相同，就直接沿用、不过规则。昵称拒绝词等清单在代码里维护、以后会收紧，否则存量昵称撞上新规则的人连只改简介都会一直 422。机构与关注领域的「已存的不重新校验」（第 8.1 节第 4 步）是同一条原则。URL 照常校验：它的规则是格式，不会变。

文本形态（网址、邮箱、手机号）只对昵称查，简介、职称、科室不查：release spec 对这几个字段只要求「拒绝的字符同昵称」，简介里写一个实验室网址是正常用途。

URL 只校验、不改写：不做规范化、不跟随、不探活。`javascript:`、`data:` 之类被 scheme 白名单挡住；门户渲染时加 `rel`（2.1.2）。

### 7.3 `UserProfile` 聚合

字段：ID、用户 ID、昵称（可空）、简介（可空）、链接（列表）、身份大类（可空）、职称（可空）、机构（可空）、科室（可空）、关注领域（有序列表）、隐私设置、版本、审计时间。

| 方法 | 行为 |
|---|---|
| `create(userId)` | 一切为空、链接和关注领域为空列表、隐私为 `PrivacySettings.defaults()`（PAP-93 已有，补默认隐私） |
| `restore(...)` | Lombok `@Builder(builderMethodName = "restore")` 放在私有全参构造器上（字段超过 10 个，照 `User.restore()`） |
| `edit(ProfileContent)` | 整体替换昵称、简介、链接、身份大类、职称、机构、科室、关注领域；返回昵称是否变了（处理器据此打一行昵称变更日志） |
| `changePrivacy(PrivacySettings)` | 替换隐私设置 |
| `rename(DisplayName)` | PAP-93 的方法，被 `edit` 取代后删掉 |

不变式在构造时守：链接 ≤ 5、关注领域 ≤ 10 且 UI 不重复。`ProfileContent` 是 `@Builder` 记录，集合在紧凑构造器里 `List.copyOf`。

### 7.4 公开主页读模型

`PublicUserReadModel`（`@Builder`）在 PAP-93 的 `userId`、`username`、`displayName`、`status`、`emailVerified` 之外加：`joinedAt`（`idn_user.created_at`）、`bio`、`links`、`occupation`、`jobTitle`、`affiliation`、`department`、`researchInterests`、`privacy`。

| 方法 | 规则 |
|---|---|
| `audience()` | 账号不是 `ACTIVE` 或邮箱未验证 → `SELF`；否则按主页可见性：`PUBLIC` → `PUBLIC`、`SIGNED_IN` → `SIGNED_IN`、`SELF` → `SELF` |
| `isVisibleTo(requester)` | 本人 → 真；`PUBLIC` → 真；`SIGNED_IN` → 请求者非空（任何带有效断言的人，含邮箱未验证、冷静期中的账号；2026-10-22 用户定）；`SELF` → 假 |
| `viewFor(requester)` → `PublicUserView` | 先要求 `isVisibleTo`；裁剪：专业背景在「本人或专业背景可见性为公开」时给出，否则为空；链接只在邮箱已验证时给出（对本人也一样）；`indexable = audience() == PUBLIC && privacy.searchIndexing()` |

`PublicUserView`：`username`、`displayName`、`audience`、`joinedAt`、`bio`、`links`、`professional`（可空：`occupation`、`jobTitle`、`affiliation`、`department`）、`researchInterests`、`indexable`。专业背景四项都为空时 `professional` 也为空，门户只按「有没有」决定渲染整块。

两条规则的依据：

- **专业背景对本人始终给出**：「仅自己可见」的字面意思就是自己能看；门户在本人视角下据 `/me/privacy` 标「仅你可见」。
- **链接在邮箱未验证时对本人也不给**：release spec 写的是「邮箱未验证时不展示」，不是「对别人不展示」。本人此时看到的就是验证之后别人会看到的样子减去链接，门户在本人视角下提示「验证邮箱后展示链接」。

加入时间按月给出：`ProfileDisplayPolicy(ZoneId zone)` 把 `joinedAt` 换成 `YearMonth`，时区沿用 `patra.identity.mail.zone`（`Asia/Shanghai`，identity 唯一的展示时区）。只给到月，是 release spec「加入时间（到月）」的最小必要。

### 7.5 端口

| 端口 | 实现（infra） | 方法 |
|---|---|---|
| `UserProfileRepository` | `UserProfileRepositoryAdapter` | `findByUserId`、`save` 改为连带链接与关注领域：两张子表都整组替换——`@Modifying @Query` 按 `user_id` 批量删 + `flush`，再按新顺序插入（派生 `deleteBy…` 会排在插入之后，撞 `(user_id, position)` 唯一约束，见计划常见坑） |
| `UserReadPort` | `UserReadAdapter` | `findPublicIdentity` / `findPublicIdentityByUserId` 返回扩展后的读模型：用户行 + 资料行 + 关注领域，三次查询 |
| `CatalogReferencePort` | `CatalogReferenceAdapter` | `Optional<String> organizationName(RorId)`：快照名，不存在为空；`Optional<String> meshName(MeshUi)`；都可能抛 `CatalogUnavailableException` |

## 8. 接口与流程

### 8.1 `PUT /me/profile`

请求体（整份替换；省略的字段等于传 `null`，即清空）：

```json
{
  "displayName": "陈宇",
  "bio": "心内科医生，关注心衰与房颤。\n业余写写科普。",
  "links": [
    { "title": "实验室主页", "url": "https://lab.example.edu/chen" },
    { "title": null, "url": "https://scholar.example.com/chen" }
  ],
  "occupation": "PHYSICIAN",
  "jobTitle": "主治医师",
  "organization": { "rorId": "04jztag35", "name": "前端传的名字会被忽略" },
  "department": "心内科",
  "researchInterests": ["D006333", "D001281"]
}
```

`organization` 为 `null`、或 `rorId` 与 `name` 都为空：没有机构。只有 `name`：自由文本。

`UpdateProfileHandler`（不加类级 `@Transactional`，事务用 `TransactionOperations` 只包写库那一段，照 `RegisterUserHandler`）：

1. `currentUserPort.require()`。
2. 读用户（不加锁）→ `requireActive()`（409 / 401）；读当前资料。不加锁读是为了第 4 步不占数据库连接。
3. `profilePolicy.parse(draft, 当前资料)`：字段错误一次报全（422）；与库里相同的文本不重新校验（7.2）。
4. 解析名称（事务外）：`rorId` 非空且与当前资料里的不同 → `catalog.organizationName(rorId)`；`researchInterests` 里当前资料没有的 UI → 逐个 `catalog.meshName(ui)`。不存在的收集成 `NOT_FOUND` 字段错误，全部查完一起抛 422。catalog 不可用：第一次失败即抛 `CatalogUnavailableException`（503），不继续调。已存在的机构和主题词不再调 catalog，直接沿用库里的快照：ROR 重导后用户已选的机构可能变成 inactive，内部接口对它回 404，不能挡住与机构无关的保存（PAP-94 第 14 节）。
5. 事务内：`users.findByIdForUpdate(userId)` → `requireActive()`（第 3 步之后状态可能变了）→ `profiles.findByUserId` → 组装 `ProfileContent`：要沿用的快照从**这次**读到的资料里取；如果第 3 步之后有并发保存把某个要沿用的机构或主题词删掉了，这里取不到快照，抛 `UserModifiedConcurrentlyException`（409，文案「用户正被其他操作修改，请重试」，现有异常）→ `profile.edit(content)` → `profiles.save`。
6. 返回保存后的资料（同 8.2 的响应）。

行锁是用户行上的，和改用户名、改密码等 `/me/**` 写操作同一把锁；资料行自己的乐观锁仍在，正常路径不会撞上。

日志：`log.info("资料已更新: userId={}, displayNameChanged={}, organizationChanged={}, interests={}")`，不打简介、链接等内容；昵称变了另打一行 `log.info("昵称已修改: userId={}, from={}, to={}")`（昵称是公开信息，可以进日志；`null` 表示跟随用户名），和改用户名的日志同一做法。

**昵称变更不建表**（2026-10-22 用户定）：release spec 2.1.1 的「修改记入审计」本版只落成上面这行日志。账号的审计记录（改昵称、改用户名、改邮箱、改密码、登录、封禁……）以后单独开一个版本做成一张通用的账号事件表，不按变更类型一张张建表；见第 16 节。

### 8.2 `GET /me/profile`

`UserQueryService.myProfile()`：状态判断同 `currentAccount()`（`BANNED` / `DELETED` 401，`PENDING_DELETION` 照常返回），再读资料聚合。

```json
{
  "displayName": null,
  "bio": "心内科医生，关注心衰与房颤。\n业余写写科普。",
  "links": [{ "title": "实验室主页", "url": "https://lab.example.edu/chen" }],
  "occupation": "PHYSICIAN",
  "jobTitle": "主治医师",
  "organization": { "rorId": "04jztag35", "name": "北京协和医院" },
  "department": "心内科",
  "researchInterests": [
    { "ui": "D006333", "name": "Heart Failure" },
    { "ui": "D001281", "name": "Atrial Fibrillation" }
  ]
}
```

`displayName` 是库里的原值，`null` 表示跟随用户名（设置页把用户名显示成占位提示）；回退后的昵称看 `/auth/me`。空字段输出 `null`，空列表输出 `[]`。

### 8.3 `GET /users/{username}`（扩展 PAP-93）

流程与 PAP-93 第 8.5 节相同（规范化 → 当前名 → 旧名 301 → 404），只是：可见性判定换成 7.4 的 `isVisibleTo`；`Found` 里装 `viewFor(requester)` 裁剪后的视图。

```json
{
  "username": "chen-yu",
  "displayName": "陈宇",
  "avatarUrl": null,
  "audience": "PUBLIC",
  "joinedAt": "2026-10",
  "bio": "心内科医生，关注心衰与房颤。\n业余写写科普。",
  "links": [{ "title": "实验室主页", "url": "https://lab.example.edu/chen" }],
  "professional": {
    "occupation": "PHYSICIAN",
    "jobTitle": "主治医师",
    "organization": { "rorId": "04jztag35", "name": "北京协和医院" },
    "department": "心内科"
  },
  "researchInterests": [{ "ui": "D006333", "name": "Heart Failure" }],
  "indexable": false
}
```

- 不含邮箱、用户 ID、状态、任何设置原值。`indexable` 是给门户输出 `<meta name="robots" content="noindex">` 用的渲染指令：只有「谁都能看且用户允许收录」时为 `true`；它能推出收录开关，但这个信息本来就写在页面的 `robots` 里（第 17 节第 18 条，已定）。
- `organization.rorId` 原样给出：ROR ID 是公开标识，门户以后可以链到机构页。
- `audience` 取值 `PUBLIC` / `SIGNED_IN` / `SELF`，门户在本人视角下据此提示「目前只有登录用户能看到」「目前只有你能看到」。
- 404 矩阵（对非本人）：

| 情况 | 匿名 | 已登录的别人 | 本人 |
|---|---|---|---|
| 正常、已验证、主页公开 | 200 `PUBLIC` | 200 `PUBLIC` | 200 `PUBLIC` |
| 主页仅登录用户 | 404 | 200 `SIGNED_IN` | 200 `SIGNED_IN` |
| 主页仅自己 | 404 | 404 | 200 `SELF` |
| 邮箱未验证 | 404 | 404 | 200 `SELF` |
| `PENDING_DELETION` | 404 | 404 | 200 `SELF` |
| `BANNED` / `DELETED` | 404 | 404 | —（登不进来；断言窗口内的请求按 PAP-93 现状为 200 `SELF`） |
| 保留期内的旧名 | 原主按上面各行可见时 301，否则 404 | 同左 | 301 |

> 修订（2026-10-24，PAP-108，v0.13 决策 E）：公开主页响应改为带 `userId`（门户凭它取该用户的公开收藏夹）；主页可见性规则抽到 `ProfileAccess`，已注销对本人也不可见。见 `2026-10-24-identity-external-contract-design.md` 第 4、7 节。

本人带着断言请求时，专业背景总在、链接看邮箱是否验证；旧名跳转的可见性判断和 PAP-93 一样作用在原主的当前读模型上。

### 8.4 `GET /me/privacy`、`PUT /me/privacy`

```json
{ "profileVisibility": "SIGNED_IN", "professionalVisibility": "SELF", "searchIndexing": false }
```

- PUT 三个字段都必填；`UpdatePrivacyHandler`（`@Transactional`）：字段在请求记录里收成 `String` / `Boolean`，处理器解析：缺失 `REQUIRED`、取值不认识 `INVALID_FORMAT`，一次报全 422 → `findByIdForUpdate` → `requireActive()` → 读资料 → `changePrivacy` → 保存。返回保存后的三项（同 GET）。
- 枚举不用 Jackson 直接反序列化：取值错了 Jackson 回 400 且没有字段原因码，和 `clientType` 的做法一致收成字符串。
- GET 的状态判断同 8.2。
- 改隐私不撤销会话、不发邮件、不要求再认证（2.1.7 的敏感操作里没有它）。

### 8.5 状态与错误汇总

| 接口 | 401 | 409 | 422 | 503 |
|---|---|---|---|---|
| `GET /me/profile`、`GET /me/privacy` | 没登录；`BANNED` / `DELETED` | — | — | — |
| `PUT /me/profile` | 同上 | `PENDING_DELETION`（`traits` 含 `PENDING_DELETION`）；并发删掉了要沿用的快照 | 字段错误；`NOT_FOUND` | catalog 不可用 |
| `PUT /me/privacy` | 同上 | `PENDING_DELETION` | 字段错误 | — |

## 9. 调 catalog

**客户端**（boot，`CatalogClientConfiguration`，照 catalog 的 `HttpClientConfiguration`）：`factory.createRestClient(loadBalancedBuilder, "catalog", "lb://patra-catalog")` → `createProxy` 出 `OrganizationEndpoint`、`MeshEndpoint`。`application.yml`：

```yaml
patra:
  http-client:
    http-interface:
      groups:
        catalog:
          base-url: lb://patra-catalog
          connect-timeout: 1s
          read-timeout: 2s
```

两个内部接口都是主键查询（PAP-94 实测毫秒级），超时压短：catalog 卡住时一次保存最坏是 1 次连接超时或 1 次读超时就 503（第一次失败即停），不会串行等满 11 次。identity 调 catalog 不经网关，`/_internal/**` 的拒绝名单不影响它。

**适配器**（infra，`CatalogReferenceAdapter`）：

| 下游表现 | 实际抛出（2026-10-22 核对源码） | 适配器 |
|---|---|---|
| 200 | — | 机构返回 `zhName` 去首尾空白后非空就用它，否则 `displayName` 去首尾空白；MeSH 返回 `name` 去首尾空白 |
| 404 ProblemDetail（`traits` 含 `NOT_FOUND`） | `RemoteCallException`，`getErrorTraits()` 含 `NOT_FOUND` | `Optional.empty()` |
| 其他 4xx / 5xx | `RemoteCallException` | `CatalogUnavailableException` |
| 连不上、超时 | `ResourceAccessException`（I/O 错误被 `RestClient` 包成它） | `CatalogUnavailableException` |
| 注册中心里没有 catalog 实例 | `IllegalStateException("No instances available for patra-catalog")`（`BlockingLoadBalancerClient`，Spring Cloud 5.0.3） | `CatalogUnavailableException` |

`.claude/rules/tech/error-handling.md` 要求「禁止直接捕获 `RestClientException`，必须用 `RemoteCallException`」，针对的是下游回了错误响应的情况；连不上和没有实例时根本没有响应，`RemoteCallException` 不会出现，所以这里只按上表捕获最窄的两个类型，而且 `try` 只包住那一次代理调用。

**`CatalogUnavailableException`**（domain）：`DEP_UNAVAILABLE`，503，`IDN-0503`，文案「机构与关注领域暂时无法校验，请稍后再试」，保留原因。日志 WARN 记下游状态码 / 异常类型，不记请求体。

**为什么 503 而不是先存下来以后再校验**：决策 F 要求快照来自 catalog，存一个没校验的 ID 就得存前端传来的名字，等于不校验；资料不是要立刻可用的东西，让用户稍后再点一次保存更简单。

**内部接口的认证**：`RestClientFactory` 给内部客户端挂了 `IdentityAssertionForwardingInterceptor`，会把当前请求的断言带给 catalog；catalog 的内部接口不看身份，无害。

## 10. 网关

不改规则：`/patra-identity/me/**` 已由 `IDENTITY_PATHS` 整体 `authenticated()`，`/patra-identity/users/**` 已公开且照样翻译令牌。

「仅登录用户可见」整个靠「公开路由上带令牌来的请求，下游拿得到断言」这一点，而 `GatewayAuthenticationIT` 现有的 `/patra-identity/users/chen-yu` 用例只测了匿名转发、不带 `Authorization`。补一条：带有效令牌 `GET /patra-identity/users/chen-yu` 被转发且 WireMock 收到的请求带身份断言头。`/me/**` 的需登录规则由现有 `/patra-identity/me/username` 用例覆盖，`/me/profile`、`/me/privacy` 不另加。

## 11. 配置

- `patra.http-client.http-interface.groups.catalog`（第 9 节），jar 里，Nacos 不用加键。
- `ProfileDisplayPolicy` 用 `patra.identity.mail.zone`，不加新键。
- 字段上限是 release spec 定的产品规则，写成 `ProfilePolicy` 的常量，不进配置。

## 12. 测试策略

时间走注入的 `Clock`。命名与位置按 `.claude/rules/testing/conventions.md`。

### 12.1 单元测试（不起 Spring）

- `ProfileTextRules`：昵称原有的拒绝字符用例迁一份；`allowLineFeed` 为真时 `\n` 放行、`\r`、U+2028、U+0085 仍拒绝。`DisplayNamePolicyTest` 不改。
- `ProfilePolicy`（AC 1）：每个字段的边界——简介 200 / 201 字素（汉字、ZWJ emoji 各一组）、800 / 801 字节、3 / 4 行、`\r\n` 归一后算行、首尾空行去掉、行尾空白去掉、空串清空；链接 5 / 6 条，URL 300 / 301、`ftp:`、`javascript:`、`HTTPS://` 大写通过、无 host、userinfo、含空格、重复（大小写不同也算），标题 30 / 31 字素与为空；身份大类八个值各一、小写被拒、空清空；职称 50 / 51；科室 100 / 101；机构 ROR ID 大写被折成小写后通过、`https://ror.org/…` 被拒、9 位以外被拒，有 ROR ID 时 `name` 超长也不报，无 ROR ID 时自由文本 200 / 201；关注领域 10 / 11、`d003920` 小写被拒、`D0039201` 8 位被拒、`D000072716` 10 位通过、重复；多个字段同时错时全部报出且字段路径正确（`links[1].url`、`researchInterests[3]`）。
- `ProfilePolicy` 的对照沿用：库里的昵称命中（测试里换一份更严的）拒绝词时，PUT 原样带回不报错、改成别的值才报；简介同理；URL 不沿用。
- `UserProfile`：`create` 默认隐私；`edit` 整体替换并返回昵称是否变化（`null → 值`、`值 → null`、相同值）；不变式（11 个关注领域、重复 UI 构造即失败）。
- `PublicUserReadModel`（AC 3、4 的规则层）：`audience` 的全部组合；`isVisibleTo` 按 8.3 矩阵逐格；`viewFor` 对本人 / 别人的专业背景裁剪、未验证时链接为空（含本人）、`indexable` 只在 `PUBLIC` 且开关开时为真、四项全空时 `professional` 为空。
- `UpdateProfileHandler`（AC 2）：有 ROR ID 时快照来自 `CatalogReferencePort`、请求里的 `name` 不进聚合；`rorId` 未变时不调 catalog、沿用库里的快照；无 ROR ID 时自由文本原样（规范化后）保存；新 UI 才调 catalog；机构与两个主题词同时不存在时 422 带三条 `NOT_FOUND`；catalog 不可用 503 且不进事务；`PENDING_DELETION` 409 且不调 catalog；事务内状态变了 409 / 401；并发删掉了要沿用的快照 409。
- `UpdatePrivacyHandler`：缺字段、错取值一次报全；保存；状态守卫。
- `UserQueryService`：`myProfile` / `myPrivacy` 的状态分支；`publicIdentity` 对三种可见性 × 匿名 / 别人 / 本人；旧名跳转在原主「仅登录」时对匿名 404、对登录用户 301。
- `CatalogReferenceAdapter`：用 mock 的 Endpoint 覆盖第 9 节表格的五行，其中 `IllegalStateException` 一行只能在这里测（集成测试里造不出「没有实例」又有其他实例的上下文）。

### 12.2 集成测试

infra（PG 容器）：

- `IdentitySchemaConstraintsIT` 加：资料表每条新 CHECK 各一个失败插入（字节上限、首尾空白、`\r`、身份大类非法、ROR ID 格式、有 ID 无快照、自由文本超 800 字节、可见性非法）；链接表的 position 5、非 http(s)、`(user_id, lower(url))` 唯一；关注领域表的 position 10、UI 格式、两条唯一约束、外键。每个用例只做一条失败插入（PG 在第一条失败后中止事务）。
- `UserProfileRepositoryAdapterIT` 扩：全字段往返；关注领域保存后按顺序读回；重排顺序（`[A,B] → [B,A]`）与删减后保存不撞唯一约束；链接往返（标题为 `null`、顺序、重排）；隐私默认值来自库默认与聚合默认一致。
- `UserReadAdapterIT` 扩：公开读模型的全部新字段、`joinedAt` 来自 `created_at`、关注领域顺序。
- `CatalogReferenceAdapterIT`（新，WireMock，照 catalog 的 `DictionaryResolverAdapterIT` 手工建 `RestClient` + `ProblemDetailErrorHandler`）：200 映射（`zhName` 为 `null` / 空白时取 `displayName`）、404 ProblemDetail 带 `NOT_FOUND` → 空、500 → 不可用、读超时 → 不可用、端口不通 → 不可用；请求路径就是 `/_internal/organizations/04jztag35`、`/_internal/mesh/descriptors/D003920`。

adapter 切片（`@WebMvcTest` + `@Import(Controller)` + `@MockitoBean`）：

- `MeProfileControllerIT`（新）：`GET/PUT /me/profile` 的请求映射（`organization` 为 `null` / 只有 `name` / 两者都有）、响应字段与 `null` / `[]` 的输出；422 的 `errors[]` 字段路径；409 `traits`；503；`GET/PUT /me/privacy` 的 200 与 422。
- `PublicUserControllerIT` 扩：新字段、`professional` 为 `null`、`joinedAt` 格式 `YYYY-MM`、`indexable`。

boot 端到端（PG + Redis + GreenMail 容器，`RANDOM_PORT`，`MutableClock`，catalog 由 WireMock 扮演）：

- 测试配置打开 `spring.cloud.discovery.enabled`（PAP-93 关掉了它，而 `LoadBalancerClientConfiguration` 挂在它下面，关着 `lb://` 没有实例来源），用 `spring.cloud.discovery.client.simple.instances.patra-catalog[0].uri` 指向 WireMock（照网关集成测试）；Nacos 发现仍关。`application-test.yml` 是所有 boot 集成测试共用的，打开发现对它们无影响（它们不调 catalog），实施时跑全部 boot 集成测试确认。
- `ProfileFlowIT`（新，AC 2、5）：注册 → `GET /me/privacy` 是公开 / 公开 / 关、`GET /me/profile` 全空 → `PUT /me/profile` 带 ROR ID 和前端名字，库里是 catalog 的中文快照 → 再 PUT 只改简介，WireMock 没收到机构请求 → 换成自由文本机构 → 不存在的 ROR ID 与 UI 422 → WireMock 回 500 时 503 且库里不变 → 昵称改两次再清空，变更表三行。
- `PublicIdentityFlowIT` 扩（AC 3、4、6）：在 PAP-93 的未验证 / 验证 / 封禁之外，加主页仅自己（匿名 404、别人 404、本人 200 `SELF`）、仅登录（匿名 404、别人 200 `SIGNED_IN`）、`PENDING_DELETION`（SQL 改状态，别人 404、本人 200）、`DELETED`（SQL 改状态并置空邮箱，404）、旧名在原主「仅登录」时匿名 404 而登录者 301；专业背景仅自己时别人看到 `professional` 为 `null`、本人看到完整；未验证时本人看到 `links` 为 `[]`、验证后出现；响应 JSON 的键集合恰好是 8.3 列出的那些（不含 `email`、`userId`、`status`、任何设置原值）。

> 修订（2026-10-24，PAP-108，v0.13 决策 E）：键集合加 `userId`，断言改在 `PublicProfileFlowIT`。

网关（`GatewayAuthenticationIT`）：带有效令牌访问 `/patra-identity/users/chen-yu` 被转发且下游收到身份断言头（第 10 节）。

### 12.3 门控

`./gradlew spotlessApply`、`./gradlew check`（含 `checkMigrationColumns` R6、`dumpModuleGraph` 一致性），identity 四个模块与 gateway 的 `integrationTest`。`check` 不含集成测试，分开跑。

### 12.4 库重建

本机 `patra_identity` 按 README 第 8 节重建后启动，Flyway 只跑 `V1`；mini 的重建随后端 PR 部署（PAP-93 第 13.3 节的运行手册）。

## 13. README

`patra-api/patra-identity/README.md`：

1. 开头的工程设计列表加本文件。
2. 第 1 节模块表：domain 的新值对象、`ProfilePolicy`、`CatalogReferencePort`；app 的两个处理器；infra 的 `CatalogReferenceAdapter`；adapter 的 `MeProfileController`。
3. 第 2 节接口表：`GET/PUT /me/profile`、`GET/PUT /me/privacy`；`GET /users/{username}` 的新结构与 404 矩阵。
4. 第 3 节账号模型：资料表新列、链接与关注领域两张子表；删掉「PAP-95 在这份骨架上加列」。
5. 第 7 节错误码表：503 catalog 不可用；原因码加 `TOO_MANY`、`TOO_MANY_LINES`、`DUPLICATE`、`NOT_FOUND`。
6. 新增「资料与公开主页」一节：字段规则、快照规则、可见性矩阵、调 catalog 的超时与错误语义。

## 14. AC 对照

| AC | 证据 |
|---|---|
| 1 每个字段的长度与格式边界有单元测试 | `ProfilePolicyTest`（12.1） |
| 2 带 ROR ID 时名称来自 catalog、前端名称被忽略；无 ROR ID 时自由文本原样；ROR ID / MeSH UI 不存在 422 | `UpdateProfileHandlerTest`、`ProfileFlowIT` |
| 3 公开读 404 矩阵的集成测试（仅自己、仅登录 + 匿名、未验证、`PENDING_DELETION`、`BANNED`、`DELETED`、旧名重定向） | `PublicIdentityFlowIT` |
| 4 专业背景仅自己时公开读不含四项；未验证时不含链接 | `PublicUserReadModelTest`、`PublicIdentityFlowIT` |
| 5 隐私默认公开 / 公开 / 关 | `UserProfileTest`、`UserProfileRepositoryAdapterIT`（库默认）、`ProfileFlowIT` |
| 6 公开读不含邮箱、不含任何设置 | `PublicIdentityFlowIT` 断言键集合；`indexable` 的例外见第 17 节第 18 条 |
| 7 `check` + `integrationTest` 全绿 | 12.3 |

> 修订（2026-10-24，PAP-108，v0.13 决策 E）：AC 6 不再要求「不含用户 ID」。

## 15. 交给其他 Issue 的约束

| 约束 | 交给 |
|---|---|
| 资料编辑：`PUT /me/profile` 整份替换（省略 = 清空），请求 / 响应见 8.1 / 8.2；`displayName` 为 `null` 表示跟随用户名；`organization` 传 `{ rorId }` 或 `{ name }`；`researchInterests` 只传 UI 数组、按用户排好的顺序；422 按 `errors[].field`（`links[1].url`、`researchInterests[3]`、`organization.rorId`）定位，原因码见 7.2；503 提示稍后再试，表单保留输入；隐私页 `GET/PUT /me/privacy` 三项必填 | PAP-101 |
| 公开主页：响应见 8.3；`professional` 为 `null` 不渲染整块；`links` 渲染为 `rel="nofollow noopener noreferrer ugc"`、标题为空显示域名；`indexable` 为假时输出 `noindex`；`audience` 为 `SIGNED_IN` / `SELF` 时在本人视角提示可见范围；本人视角下专业背景「仅自己」由 `/me/privacy` 判断后标注；未验证时本人视角提示「验证邮箱后展示链接」；`joinedAt` 是 `YYYY-MM` | PAP-101 |
| 资料表新增的列在 `DELETED` 时置空、隐私回默认；`idn_user_profile_link`、`idn_user_research_interest` 按 `user_id` 删行；资料行本身不删（PAP-93 第 16 节） | PAP-100 |
| 头像：`PublicUserView`、`/me/profile` 响应在本 Issue 不含头像字段之外的东西，`avatarUrl` 仍为 `null`；头像对象键加在 `idn_user_profile` 上 | PAP-97 |

## 16. 要同步改的文档和 Issue

- Linear PAP-95：状态推 BE。
- release spec 2.1.1 昵称一栏：「修改记入审计」注明「本版只记日志，账号审计记录另开版本」。
- Linear PAP-101：第 15 节的契约。
- Linear PAP-100：第 15 节的清理清单。
- release spec 2.1.2 机构一行：「名称 ≤ 200」改成「手填名称 ≤ 200，ROR 名称快照以 catalog 为准」；Linear PAP-95 AC 1 的「机构名 ≤ 200」同步注明「手填」。
- Linear PAP-95 AC 6 改成「不含邮箱、不含任何设置原值（`indexable` 渲染指令除外）」。
- PAP-93 第 16 节说的 `clearDisplayName()` 由 `edit` 取代，`rename` 删掉；调它的三处测试（`UserProfileTest`、`UserProfileRepositoryAdapterIT`、`UserReadAdapterIT`）改成 `edit`。
- identity README：第 13 节。

## 17. 设计中由我定的点与待拍板的点

**我定的（builder 可直接采用，用户看到不对就推翻）**：

1. 昵称在 `PUT /me/profile` 里改，没有单独接口；整份替换，省略即清空。
2. 链接、关注领域都存有序子表（后者以后要按主题查用户）；隐私开关存资料表三列。
3. 字节上限统一是字素上限 × 4（与昵称同比例）；机构快照列长 500 与 catalog 一致，自由文本另有 200 字素 / 800 字节。
4. 链接标题可空；URL 拒绝 userinfo；重复 URL 拒绝；只校验不改写。
5. 简介、职称、科室、机构自由文本只查拒绝字符，不查网址 / 手机号形态。
6. 本人看自己：专业背景始终给出；链接仍受邮箱验证约束。
7. 已存的机构 / 主题词不重新校验、沿用快照（PAP-94 的约束推广到关注领域）。
8. catalog 不可用 503，第一次失败即停；超时压到连接 1s / 读 2s。
9. 加入时间只给到月，时区沿用 `patra.identity.mail.zone`。
10. 隐私的枚举值用 `SELF` 而不是 `PRIVATE`，与 `PublicAudience` 同名。
11. 关注领域接受 catalog 能查到的任何主题词 UI，不在 identity 重复「只要 class 1」的产品规则（门户只从建议接口拿 UI，建议接口只给 class 1）。
12. `professional` 四项全空时为 `null`。
13. 与库里相同的文本不重新校验（规则收紧不连累存量）。
14. 快照入库前去首尾空白；机构名列 `VARCHAR(800)`，快照不受手填的 200 上限约束（细化 release spec 2.1.2，第 16 节同步）。
15. 昵称变更只打 INFO 日志、不建表（用户 2026-10-22 定；账号审计另开版本做通用事件表）。
16. 「仅登录用户」按字面：任何带有效会话的人都算，含邮箱未验证、冷静期中的账号（用户 2026-10-22 定）。这一档挡匿名访客和爬虫，要挡人用「仅自己」。
17. 网关补一条「公开路由带令牌时下游收到断言」的集成测试（builder check 指出现有用例只测了匿名）。
18. 公开主页响应保留 `indexable`（用户 2026-10-22 定）；PAP-95 AC 6 改成「不含邮箱、不含任何设置原值（`indexable` 渲染指令除外）」。

**待拍板**：无。三条都已由用户 2026-10-22 定下（上面第 15、16、18 条）。

## 18. 实施时实测与偏离

实施时核对或实测的结论（2026-10-22，JDK 25、Spring Cloud 5.0.3、Testcontainers PostgreSQL 17）：

| # | 结论 | 依据 |
|---|---|---|
| 1 | 注册中心里没有 catalog 实例时，LoadBalancer 抛 `IllegalStateException("No instances available for patra-catalog")`，不是 `RemoteCallException`；连不上、超时是 `ResourceAccessException`。适配器按这三类捕获 | `BlockingLoadBalancerClient` 源码；`CatalogReferenceAdapterTest`、`CatalogReferenceAdapterIT`（端口不通、读超时） |
| 2 | 手工建的 `JsonMapper` 不注册 `ProblemDetailJacksonMixin` 时，ProblemDetail 的扩展属性（`code`、`traits`）全部丢失，404 + `NOT_FOUND` 会被误判成「不可用」；Boot 的 Jackson 自动配置会注册它，测试要照样注册 | `CatalogReferenceAdapterIT` 首跑失败后定位 |
| 3 | 经 `lb://patra-catalog` + SimpleDiscoveryClient 的真实路径能拿到快照；共用的测试配置关着服务发现，只在 `ProfileFlowIT` 的上下文里用 `@DynamicPropertySource` 打开 | `ProfileFlowIT` |
| 4 | 子表整组替换（JPQL 批量删 + flush 再插）重排、删减不撞 `(user_id, position)` / `(user_id, mesh_ui)` 唯一约束 | `UserProfileRepositoryAdapterIT.should_reorder_children_without_unique_violation` |
| 5 | 请求记录的列表字段 SpotBugs 只认 `List.copyOf` 为防御性拷贝；`List.copyOf` 不收 `null` 元素，所以先把 `null` 元素换成空值，规则照样报 `REQUIRED` | `UpdateProfileRequest`，`spotbugsMain` |
| 6 | mini 的 `patra_catalog` 里机构英文名最长 196、中文名最长 77、MeSH 名最长 104 个字符，现有快照都在手填上限之内 | 2026-10-22 只读查询 |

偏离计划的裁决（全部记在 `.superpowers/sdd/2026-10-22-identity-profile/progress.md` 的 `Ruling:` 行）：资料存取的 Bean 与 catalog 客户端装配随第一次用到它的任务提前；约束与公开主页矩阵测试各放一个新类（`ProfileSchemaConstraintsIT`、`PublicProfileFlowIT`）；infra 依赖 `patra-tool-http-client` 而不是裸 `spring-web`；`PatraIdentityApplicationIT` 的表清单 6 → 8；本机没有起 jar 手工验收（本机没有 identity 库、mini 的库随后端 PR 部署才重建），端到端由两个流程测试覆盖，真 catalog 联调在 mini 部署时做。

回归（2026-10-22，本机 OrbStack）：`./gradlew spotlessApply && ./gradlew check` 全绿；集成测试 identity-session 24、identity-infra 134、identity-adapter 70、identity-boot 64、gateway-boot 54，全部通过。

整分支评审（opus 子代理，2026-10-22）：0 Critical；Important「昵称变更日志缺旧值」已修（`b37f19338`）；Minor「URL / ROR ID / MeSH UI 首尾的不换行空格」升为 Important 一并修。延后的 Minor：链接标题按下标对照库里旧值（只重排链接时旧标题会重新过规则）；catalog 回 200 但名称为空或响应体不是 JSON 时是 500 而非 503；改隐私也整组重写两张子表；`CatalogClientConfiguration` 受 `patra.http-client.http-interface.enabled` 控制而适配器无条件注册。
