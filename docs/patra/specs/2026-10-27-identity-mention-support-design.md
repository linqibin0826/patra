# identity 提及支撑工程设计（PAP-118）

> 状态：已定稿，直接实施（2026-10-24 起不再有 Codex 设计评审）。2026-10-27 用户拍板追加：联想加按昵称前缀匹配（第 4 节重写），评审的 Minor 1、2、4 一并修
> 日期：2026-10-27
> Issue：PAP-118（GitHub #295），Linear Project P-PAP-11 v0.14 Discussion
> 依据：release spec `docs/patra/release-specs/v0.14-discussion.md` 2.1.1、2.1.5、决策 C、E；PAP-108 工程 spec `2026-10-24-identity-external-contract-design.md`（批量用户摘要、`ProfileAccess`）；PAP-93 的用户名规则与旧名保留（identity README 第 3、4 节）

## 1. 要解决的问题

v0.14 的讨论区要支持 @ 同行（release spec 决策 C）。门户只发 `@用户名`，由 interaction 写入时解析成 `<@用户ID>`；@ 联想也由 interaction 对外提供：先列本讨论的参与者，再并上 identity 按前缀查到的全站结果。网关对 `/_internal/**` 一律 403，门户调不到 identity 的内部接口，所以 identity 要给 interaction 两个内部接口：

1. **前缀联想**：入参是前缀、访客 ID、条数上限；按用户名前缀**和昵称前缀**两路找人（输入「@张」能联想到昵称以「张」开头的人），合并去重；只返回对这位访客主页可见的 `ACTIVE` 账号，带用户 ID、用户名、昵称、头像地址。可见性规则只用 `ProfileAccess` 那一份。
2. **用户名批量解析**：用户名 → 用户 ID 与账号状态。当前用户名查不到时，再查 90 天保留期内的旧用户名；过期的旧名不解析。

现状：identity 只能按完整用户名查公开主页（`GET /users/{username}`），没有前缀查询，也没有按用户名批量查 ID；库里没有能做用户名或昵称前缀扫描的索引。

## 2. 范围

**做**（identity 内，另加网关一条测试）：

- `patra-identity-api`：`UserEndpoint` 新增两个方法，新增五个 DTO。
- domain：值对象 `UsernamePrefix`、`DisplayNamePrefix`；`UserReadPort` 新增三个读方法；`FormerUsernameRepository` 新增批量查保留旧名；`UserFieldViolations` 新增三个字段名与五个工厂方法。
- app：`UserSuggestionQueryService`、`UserResolutionQueryService`、结果记录 `ResolvedUser`。
- infra：`UserDao` 三个查询、`FormerUsernameDao` 一个查询、两个适配器的实现；Flyway `V2__add_mention_prefix_indexes.sql`（用户名、昵称两个前缀索引）。
- adapter：`UserEndpointImpl` 实现两个新方法。
- 网关：`GatewayBlockedPathsIT` 的拒绝名单加两条路径（只改测试，不改规则与代码）。
- 文档：identity README。

**不做**：

- interaction 侧的客户端调用、「参与者优先」的合并、`<@用户ID>` 的存取与回显：属于 PAP-121。本 Issue 只交付契约，第 10 节写清楚交接。
- 网关规则与代码：`/*/_internal/**` 已在拒绝名单里。

## 3. 已定的前提

| 来源 | 内容 |
|---|---|
| release spec 2.1.1 | 联想只列出主页对你可见的正常账号，本讨论参与者优先（参与者由 interaction 排）；完整输入用户名时任何正常账号都能 @ 到；90 天保留期内的旧用户名能 @ 到原用户；解析不到的保持纯文本 |
| release spec 决策 C | 客户端只发 `@用户名`；interaction 写入时调 identity 内部接口批量解析（含保留期内旧名），存成 `<@用户ID>`；解析不到或不是正常账号的保持纯文本；主页可见性由 identity 按访客执行 |
| Issue To-Do / AC | 契约加到 `UserEndpoint`；前缀查询要有索引；联想排除 `BANNED`、`PENDING_DELETION`、`DELETED` 与主页对访客不可见的账号；解析覆盖当前名、保留期内旧名、过期旧名、不存在、非正常账号（带状态）；不符合用户名语法的输入直接返回空、不查库；两个接口经网关都是 403 |
| 编排会话的说明 | 形状对齐 `POST /_internal/users/summaries`；可见性复用 `ProfileAccess`，不另写；前缀匹配用 btree，注意「pg_trgm 短模式在通用计划下 GIN 全扫」那条坑 |
| 用户拍板（2026-10-27） | 联想加按昵称前缀匹配，与用户名前缀的结果合并、去重，可见性与账号状态规则照旧只走 `ProfileAccess`；索引照用户名的做法（`COLLATE "C"`、显式 `>=` / `<` 边界），在临时 PG17 里验证通用计划下走索引；英文昵称大小写、两路的排序由工程 spec 定 |
| 仓库规则 | 用户名只存 identity，其他服务只存 `user_id`（`.claude/rules/project-info.md`） |
| 代码事实 | 用户名规范化 = 去首尾空白 + `toLowerCase(Locale.ROOT)`；语法 `^[a-z0-9]([a-z0-9]\|[_-][a-z0-9])*$`、3–30 字符、不能纯数字（`Username`，库里有同样的 CHECK）；保留字只在注册、改名时判 |
| 代码事实 | 旧名保留由 `idn_user_former_username.reserved_until` 判断，到期当刻不再保留；`findReserved` 在未到期的里取改名时间最近的一条；索引 `(username, reserved_until)` 已有 |
| 代码事实 | `patra_identity` 库按 postgres:17 镜像默认建库，排序规则是 `en_US.utf8`；`uk_idn_user_username` 按它排序，做不了前缀范围扫描 |
| 代码事实 | 昵称在 `idn_user_profile.display_name`，可空（空表示跟随用户名），存的是 `DisplayName.normalize` 之后的值（NFC、空格类字符折成一个空格、去首尾空白）；能出现在昵称里的字符由 `ProfileTextRules.hasRejectedCharacter` 管（拒绝控制字符、私用区、未分配码点、孤立代理项、格式字符）；≤ 30 个字素且 ≤ 120 字节；账号状态在 `idn_user.status`，不在资料表 |
| 代码事实 | 下游服务的安全链对 `/_internal/**` 不做认证；访客 ID 取自请求体，identity 信任内部调用方（PAP-108 第 5.4 节） |

## 4. 前缀联想 `POST /_internal/users/suggest`（用户名与昵称）

### 4.1 契约

```java
@PostExchange("/suggest")
UserSuggestionsResp suggest(@RequestBody UserSuggestReq request);

public record UserSuggestReq(String prefix, Long viewerUserId, Integer limit) { of(...) }
public record UserSuggestionsResp(List<UserSummaryResp> users) { of(...)；紧凑构造器 copyOf，null 视为空 }
```

```json
// 请求：prefix 是 @ 后面已输入的部分，可以是用户名的开头，也可以是昵称的开头
{ "prefix": "张", "viewerUserId": 7, "limit": 10 }
// 响应
{ "users": [ { "userId": "42", "username": "zhang-san", "displayName": "张三",
               "avatarUrl": "/users/42/avatar?v=0123456789abcdef", "avatarPalette": 5,
               "accountStatus": "ACTIVE", "emailVerified": true, "profileVisible": true } ] }
```

- 列表项直接用批量摘要的 `UserSummaryResp`：interaction 已有 `UserSummaryResp → UserCard` 的映射，参与者（经批量摘要取）和全站联想结果是同一种卡片，按 `userId` 合并去重即可。按 4.3 的筛选，结果里 `accountStatus` 恒为 `ACTIVE`、`emailVerified` 与 `profileVisible` 恒为真。
- 外层另起 `UserSuggestionsResp`，不复用 `UserSummariesResp`：后者的文档承诺「按请求顺序、不认识的省略」，对联想不成立。
- 除 Issue 列的四个字段外多给 `avatarPalette`：门户画默认头像要它（PAP-108 同理）。

### 4.2 请求规则

| 输入 | 结果 |
|---|---|
| `prefix` 为 `null` | 422，字段 `prefix`，原因 `REQUIRED` |
| `limit` 为 `null` | 422，字段 `limit`，原因 `REQUIRED` |
| `limit` 不在 1–20 | 422，字段 `limit`，原因 `INVALID_FORMAT` |
| `prefix` 既不可能是任何合法用户名的开头，也不可能是任何昵称的开头（4.4） | 200 `{ "users": [] }`，不查库 |
| 只有一路可能（如 `张` 只能是昵称开头、`-a` 只能是昵称开头） | 只查那一路 |
| `viewerUserId` 为 `null` | 按匿名访客算（与批量摘要一致） |

先查 `prefix`、`limit` 是否缺失与越界（两个都有问题时一起报），再看前缀语法。上限 20：interaction 的下拉框最多列 10 人，和参与者重叠时多要一些，20 够用。

### 4.3 结果规则

两路候选，各自按库里的顺序取，再合并：

1. **用户名一路**（前缀能是用户名的开头时）：用户名以该前缀开头的 `ACTIVE` 账号，按用户名字节序升序，最多 200 个（`CANDIDATE_CAP`）。
2. **昵称一路**（前缀能是昵称的开头时）：已设昵称、且昵称以该前缀开头（**不分大小写**）的 `ACTIVE` 账号，按小写昵称的码位序升序、同名按用户名字节序，最多 200 个。
3. **合并**：用户名一路在前、昵称一路在后，按用户 ID 去重（两路都命中的人留在用户名一路的位置）。
4. **逐个筛**：去掉访客本人；再用 `UserSummaryReadModel.access().isVisibleTo(访客)` 判主页是否可见，不可见的去掉。
5. 剩下的按顺序取前 `limit` 个，`viewFor(访客)` 出摘要（昵称没设时回退成用户名，头像按状态给）。用户名一路筛完已经够 `limit` 个时不查昵称一路：反正排在后面、会被截掉，结果一样。

- **只认 `ACTIVE`**：写在两条查询里。冷静期账号的主页本来只对本人可见，加上第 4 步排除本人，结果一样；单独写在查询里是让「正常账号」这条规则不依赖可见性规则的细节。
- **排除本人**：`ProfileAccess.isVisibleTo` 对主人恒为真，不排除的话，邮箱未验证或主页设为仅自己的访客会在自己的联想里看到自己；而且 @ 自己不产生提醒（release spec 2.1.4）。interaction 的参与者列表最好同样排除访客本人（第 10 节）。
- **候选上限每路 200**：前缀很短（如 `a`、`张`）时，每路最多扫 200 行，过滤后不足 `limit` 就返回不足的结果，不再往后翻。联想只是输入辅助；完整输入用户名时走解析，任何正常账号都能 @ 到。
- **两路的排序**：用户名一路在前。@ 的语法是 `@用户名`，按用户名命中是最强的信号；前缀本身就是一个用户名时排第一（它是所有以它开头的字符串里最短的）。中文前缀只可能命中昵称，结果全来自昵称一路；拉丁字母前缀先出用户名命中的，再补昵称命中的。
- **昵称不分大小写**：输入 `chen` 能联想到昵称 `Chen Yu`；中文没有大小写，不受影响。两边都转小写：Java 侧 `toLowerCase(Locale.ROOT)`，库里 `lower(display_name)`（按库的 `en_US.utf8`）。两者在极少数字符上可能不一致（如 `İ`），只影响含这些字符的昵称能否按小写输入联想到。
- **没设昵称的人**：昵称跟随用户名，已经被用户名一路覆盖，昵称一路只看 `display_name` 不为空的行。
- **只匹配开头**：昵称中间的词不匹配（`Yu` 联想不到 `Chen Yu`），与用户名一致。

### 4.4 前缀语法：`UsernamePrefix` 与 `DisplayNamePrefix`

同一个 `prefix` 原文分别解析成两种前缀，哪一种不成立就跳过那一路。

新值对象 `domain/model/vo/UsernamePrefix`（record，`value` 为规范化之后的前缀）：

- `parse(String raw)` 返回 `Optional<UsernamePrefix>`：`raw` 为 `null` 返回空（缺失由服务先报 422）；先按 `Username.normalize` 规范化，再要求：长度 1–30，且整串匹配 `^[a-z0-9]([_-]?[a-z0-9])*[_-]?$`（首字符是字母或数字、`_` 与 `-` 不连续、末尾可以是一个 `_` 或 `-`）。不满足返回空。
- 比用户名语法宽：`a`、`ab`（不够 3 个字符）、`chen-`（末尾是连接符）、`123`（纯数字，后面还能接字母）都是合法前缀。
- 不再细分边界情形（如 30 个字符却以 `-` 结尾、30 个数字）：这些前缀会去查库，查不到任何人，结果一样。
- 判定只看语法，不看保留字。

新值对象 `domain/model/vo/DisplayNamePrefix`（record，`value` 为规范化并转小写之后的前缀）：

- `parse(String raw)` 返回 `Optional<DisplayNamePrefix>`：`raw` 为 `null` 返回空；先按 `DisplayName.normalize` 规范化（与存库的昵称同一套：NFC、空格类字符折成一个空格、去首尾空白），再要求：非空、不超过 120 字节（`DisplayNamePolicy.MAX_BYTES`，存库的昵称不会更长）、不含 `ProfileTextRules.hasRejectedCharacter` 拒绝的字符（这些字符不可能出现在昵称里）。满足时 `value` 是规范化之后再 `toLowerCase(Locale.ROOT)` 的值。
- 只看能不能是昵称的开头：不判最短字素数、联系方式形态、拒绝词（`张` 一个字也是合法前缀）。
- 被拒字符包括孤立代理项、未分配码点（含 U+10FFFF）和私用区，所以前缀的末码点加一总是合法码点，4.5 的上界不会越界。

### 4.5 查询与索引

#### 用户名一路

**查询**（`UserDao.findActiveByUsernameRange`，原生 SQL）：

```sql
SELECT * FROM idn_user
WHERE status = 'ACTIVE'
  AND username COLLATE "C" >= :from
  AND username COLLATE "C" <  :to
ORDER BY username COLLATE "C"
LIMIT :limit
```

`from` 是前缀本身，`to` 是把前缀末字符的码位加一（`chen` → `cheo`；`chen-` → `chen.`；`chen_` → `` chen` ``）。用户名只有 ASCII 字符，按字节序时 `[from, to)` 恰好是所有以前缀开头的字符串。资料行用现有的 `profileDao.findByUserIdIn` 一次取齐，拼成 `UserSummaryReadModel`（与 `findSummaries` 同一个 `toSummary`）。

**索引**（`V2__add_mention_prefix_indexes.sql` 的第一个）：

```sql
CREATE INDEX idx_idn_user_username_prefix
    ON idn_user (username COLLATE "C")
    WHERE status = 'ACTIVE';
```

- **为什么 `COLLATE "C"`**：库的排序规则是 `en_US.utf8`，它按语言规则排序（连字符、下划线在第一层被忽略），前缀范围在这个顺序下不连续，`uk_idn_user_username` 用不上。用户名只有 `[a-z0-9_-]`，按字节排序正是字典序。只给这个索引和查询指定 `"C"`，不改列的排序规则：改列会重建唯一约束的索引，也会悄悄改变其他按用户名排序的地方。
- **为什么不用 `LIKE`**：① `_` 既是用户名字符又是 `LIKE` 的单字符通配符，前缀 `chen_` 会匹配到 `chena`，要转义；② pgjdbc 第 5 次执行起用服务端预编译，PG 可能改用通用计划，而通用计划里参数化的 `LIKE` 推不出索引范围（只有常量模式才能），只能顺序扫或整索引扫。显式的范围谓词带参数照样能用 btree 索引的范围。这正是计划坑清单里 pg_trgm 那条的同类问题。
- **为什么不用 pg_trgm**：要的只是前缀，btree 范围扫描已经精确；trigram 对 1–2 个字符的短前缀凑不出三元组，在通用计划下会退化成整索引扫描（坑清单原话）。
- **为什么是部分索引**：查询只要 `ACTIVE`，谓词写成字面量 `status = 'ACTIVE'`，通用计划下也能匹配上部分索引；索引里不放封禁、冷静期、已注销的行。
- **为什么新增 `V2` 而不是改 `V1`**：mini 上的 `patra_identity` 已有账号，这次不推倒重建（v0.12 决策 J 是那一版一次性的做法）。`V2` 只加两个索引，不动数据；两个索引在同一个未合入的改动里，放进同一个 `V2`，不另起 `V3`。
- **实施时的验证**：对迁移后的库执行

  ```sql
  SET plan_cache_mode = force_generic_plan;
  SET enable_seqscan = off;   -- 表很小时规划器本来就会选顺序扫，关掉它才看得出索引能不能用
  PREPARE suggest(text, text, int) AS
    SELECT * FROM idn_user WHERE status = 'ACTIVE'
      AND username COLLATE "C" >= $1 AND username COLLATE "C" < $2
      ORDER BY username COLLATE "C" LIMIT $3;
  EXPLAIN EXECUTE suggest('chen', 'cheo', 10);
  ```

  期望出现 `Index Scan using idx_idn_user_username_prefix`，`Index Cond` 里有两条范围条件，且没有 `Sort` 节点。

  **实测（2026-10-27，postgres:17 一次性容器，`datcollate = en_US.utf8`，V1 + 本索引，2007 行）**：

  - 默认排序规则下 `chen%` 的顺序是 `chen < chen9 < chena < chen-banned < chen_li < chen-yu`（连接符被忽略），按 `"C"` 是 `chen < chen-banned < chen-yu < chen9 < chen_li < chena`：前者里前缀范围不连续，证实不能用默认排序规则的索引。
  - 范围谓词在 `force_generic_plan` 下：`Index Scan using idx_idn_user_username_prefix`，`Index Cond: (((username)::text >= $1) AND ((username)::text < $2))`，没有 `Sort`；参数声明成 `varchar`（Hibernate 的绑定类型）结果相同。`('chen_', 'chen`')` 只命中 `chen_li`，`('chen-', 'chen.')` 只命中 `chen-yu`（`chen-banned` 被状态排除）。
  - 对照：同样设置下参数化的 `username COLLATE "C" LIKE $1` 只能整索引扫描，条件落在 `Filter` 里，没有 `Index Cond`。
  - 不改任何设置（自定义计划）时同样走该索引的范围扫描，执行 0.019 ms。

#### 昵称一路

**查询**（`UserDao.findActiveByDisplayNameRange`，原生 SQL）：

```sql
SELECT u.* FROM idn_user u JOIN idn_user_profile p ON p.user_id = u.id
WHERE u.status = 'ACTIVE' AND p.display_name IS NOT NULL
  AND lower(p.display_name) COLLATE "C" >= :from
  AND lower(p.display_name) COLLATE "C" <  :to
ORDER BY lower(p.display_name) COLLATE "C", u.username COLLATE "C"
LIMIT :limit
```

`from` 是 `DisplayNamePrefix.value`（已转小写），`to` 是把它的末**码点**加一（`张` U+5F20 → `弡` U+5F21；`🍎` U+1F34E → U+1F34F，代理对照样按码点算）。按 `"C"` 比较即 UTF-8 字节序，与码点序一致，所以 `[from, to)` 恰好是所有以前缀开头的字符串。用户名一路的上界算法同一个，只是用户名全是 ASCII。资料行照样用 `profileDao.findByUserIdIn` 一次取齐。

**索引**（`V2__add_mention_prefix_indexes.sql` 的第二个）：

```sql
CREATE INDEX idx_idn_user_profile_display_name_prefix
    ON idn_user_profile ((lower(display_name)) COLLATE "C")
    WHERE display_name IS NOT NULL;
```

- **偏离「只覆盖 ACTIVE」（有意，表结构所限）**：编排会话要求照用户名的做法只覆盖 `ACTIVE`。昵称在 `idn_user_profile`，账号状态在 `idn_user`，PG 的部分索引不能引用别的表，所以这个索引做不到只覆盖 `ACTIVE`；改为只覆盖已设昵称的行，`ACTIVE` 由查询里联用户表、按主键回表时过滤（实测计划见下）。不为此把状态冗余进资料表。
- **大小写**：表达式 `lower(display_name)` 进索引，查询写同一个表达式；`lower()` 用列的排序规则（`en_US.utf8`）做大小写映射，再按 `"C"` 比较。
- **同名按用户名**：同一个小写昵称可以有多人，加用户名做第二排序键让结果确定；通用计划下是索引范围扫描加增量排序（按已排好的第一键分组排），不是全量排序。
- **实测（2026-10-27，postgres:17 一次性容器，V1 + 两个索引，3000 个用户、2000 个设了昵称、每 10 个一个封禁）**：
  - `force_generic_plan` + `enable_seqscan = off`：`Index Scan using idx_idn_user_profile_display_name_prefix`，`Index Cond: (((lower((display_name)::text))::text >= ($1)::text) AND ((lower((display_name)::text))::text < ($2)::text))`，外层 `Nested Loop` 按 `idn_user_pkey` 回表，`Filter: ((status)::text = 'ACTIVE'::text)`；加用户名第二排序键后多一个 `Incremental Sort`（`Presorted Key` 是小写昵称），仍走索引范围。
  - 不改任何设置（自定义计划）时走同一个索引的位图扫描加 top-N 排序，`('张', '弡', 200)` 命中 1000 行、执行 1.35 ms。
  - 抽查：`chen`（小写）命中 900 个 `Chen …` 昵称（1000 个减去 100 个封禁），全部 `ACTIVE`。
  - 实施后用最终的 `V2__add_mention_prefix_indexes.sql` 在全新库上复验，两条查询都用 `UserDao` 里的原句、参数声明成 `varchar`：用户名一路 `Index Scan using idx_idn_user_username_prefix`，昵称一路 `Index Scan using idx_idn_user_profile_display_name_prefix` 加 `Incremental Sort`，与上面一致。
  - 附带发现：同名的几行如果不加用户名第二排序键，按堆顺序出（倒着写入时是 e…d…a）；加了才稳定按用户名。

## 5. 用户名批量解析 `POST /_internal/users/resolve/by-usernames`

### 5.1 契约

```java
@PostExchange("/resolve/by-usernames")
UserResolveResp resolveByUsernames(@RequestBody UserResolveByUsernamesReq request);

public record UserResolveByUsernamesReq(List<String> usernames) { of(...)；不做防御性拷贝 }
public record UserResolveResp(List<UserResolutionResp> users) { of(...)；紧凑构造器 copyOf，null 视为空 }
public record UserResolutionResp(String username, Long userId, AccountStatus accountStatus) { of(...) }
```

```json
// 请求
{ "usernames": ["Chen-Yu", "old-name", "nobody", "bad name", "chen-yu"] }
// 响应：nobody 不存在、bad name 不合语法，都不出现
{ "users": [ { "username": "Chen-Yu",  "userId": "42", "accountStatus": "ACTIVE" },
             { "username": "old-name", "userId": "43", "accountStatus": "BANNED" },
             { "username": "chen-yu",  "userId": "42", "accountStatus": "ACTIVE" } ] }
```

- 路径与命名对齐 catalog 的 `/_internal/publications/resolve/by-ids`；结果形态对齐 identity 自己的批量摘要：解析不到的省略，不返回占位行。
- `username` 是**请求里的原文**（不带 `@`，未规范化），不是规范化之后的用户名：interaction 没有 `Username` 值对象，要按用户写的原文把结果配回正文里的每个 `@xxx`。
- 请求 DTO 不做防御性拷贝：`null` 与含 `null` 的列表要原样到达服务端（与 `UserSummariesReq` 同理）。

### 5.2 请求规则

| 输入 | 结果 |
|---|---|
| `usernames` 为 `null` | 422，字段 `usernames`，原因 `REQUIRED` |
| `usernames` 长度 > 100（按原始长度算，去重之前） | 422，字段 `usernames`，原因 `TOO_MANY` |
| 元素为 `null`，或规范化后不符合用户名语法（`Username.violation`） | 跳过，不出现在结果里 |
| 全部元素都被跳过（含空列表） | 200 `{ "users": [] }`，不查库 |
| 原文完全相同的重复元素 | 保留第一次出现 |
| 原文不同、规范化后相同（`Chen-Yu` 与 `chen-yu`） | 各占一行，`userId` 相同；调用方按 `userId` 去重 |

元素不合语法时跳过而不是 422：AC 要求「直接返回空」；正文里的 `@xxx` 本来就可能不是用户名，调用方不必先自己校验一遍。

### 5.3 解析规则

对每个合法的元素（按规范化之后的名字查）：

1. **当前用户名**：`idn_user` 里有这个用户名 → 该用户的 ID 与状态。四种状态都返回，状态原样带上（已注销的用户名永不释放，也会解析到，带 `DELETED`）。
2. **保留期内的旧名**：第 1 步查不到时，查 `idn_user_former_username` 里此刻未到期（`reserved_until > now`）的记录，多条取改名时间最近的一条 → 原主的 ID 与原主的**当前**状态。
3. **都没有**（不存在、旧名已过期）→ 不出现在结果里。

- 当前名优先于旧名：保留期内别人不能占用旧名，两者同时命中只会发生在原主改回旧名之后，这时两步得到的是同一个人。
- 「是不是正常账号」由 interaction 判：identity 原样给状态，不替调用方过滤（release spec 决策 C 的「不是正常账号的保持纯文本」在 interaction 里做）。
- 不看主页可见性：release spec 2.1.1「完整输入用户名时，任何正常账号都能 @ 到」。
- 「此刻」取注入的 `Clock`（IT 用可拨动的测试时钟验证过期）。
- 解析不到属正常情况（手误、正文里的 `@` 不是提及），不打日志。

### 5.4 查询

最多两条查询，加上取原主状态共三条，都是 `IN`：

1. 当前名：`userDao.findByUsernameIn(规范化后的名字)` + `profileDao.findByUserIdIn`，拼成 `UserSummaryReadModel`（`UserReadPort.findSummariesByUsernames`，复用摘要读模型，只用它的 `userId`、`username`、`status`）。
2. 只对第 1 步没查到的名字：`formerUsernameDao.findByUsernameInAndReservedUntilAfter(名字, now)`，适配器里每个名字取 `replacedAt` 最大的一条（`FormerUsernameRepository.findAllReserved(Collection<Username>, Instant)`）。走已有索引 `(username, reserved_until)`。
3. 第 2 步命中的原主：`UserReadPort.findSummaries(原主 ID)`（PAP-108 已有）取当前状态。

第 2 步没有要查的名字时不发第 2、3 条。

## 6. 分层

| 层 | 类 | 职责 |
|---|---|---|
| api | `UserEndpoint` | 新增 `suggest`、`resolveByUsernames` |
| api | `UserSuggestReq`、`UserSuggestionsResp`、`UserResolveByUsernamesReq`、`UserResolveResp`、`UserResolutionResp` | 第 4.1、5.1 节 |
| domain | `UsernamePrefix`、`DisplayNamePrefix`（`vo/`） | 第 4.4 节 |
| domain | `UserReadPort` | 新增 `findActiveSummariesByUsernamePrefix(UsernamePrefix prefix, int limit)`（只含 `ACTIVE`，按用户名字节序升序，最多 `limit` 个）、`findActiveSummariesByDisplayNamePrefix(DisplayNamePrefix prefix, int limit)`（只含 `ACTIVE` 且已设昵称，按小写昵称码点序、同名按用户名，最多 `limit` 个）、`findSummariesByUsernames(Collection<Username> usernames)`（存在的那些，不保证顺序；空集合返回空） |
| domain | `FormerUsernameRepository` | 新增 `findAllReserved(Collection<Username> usernames, Instant now)`：此刻未到期的记录，每个名字最多一条（改名时间最近的），不保证顺序 |
| domain | `UserFieldViolations` | 字段名 `PREFIX = "prefix"`、`LIMIT = "limit"`、`USERNAMES = "usernames"`；`prefixRequired()`、`limitRequired()`、`limitOutOfRange()`（`INVALID_FORMAT`）、`usernamesRequired()`、`usernamesTooMany()`；原因码都已存在 |
| app | `UserSuggestionQueryService.suggest(String prefix, Optional<Long> viewerId, Integer limit)` → `List<UserSummaryView>`（`app/usecase/user/query/`） | 第 4.2、4.3 节；常量 `MAX_LIMIT = 20`、`CANDIDATE_CAP = 200` |
| app | `UserResolutionQueryService.resolveByUsernames(List<String> usernames)` → `List<ResolvedUser>` | 第 5.2、5.3 节；常量 `MAX_USERNAMES = 100`；注入 `Clock` |
| app | `ResolvedUser(String username, long userId, UserStatus status)` | 一个元素的解析结果，`username` 是请求原文 |
| infra | `UserDao.findActiveByUsernameRange(String from, String to, int limit)`、`findActiveByDisplayNameRange(String from, String to, int limit)`（原生，第 4.5 节）、`findByUsernameIn(Collection<String>)`（派生） | |
| infra | `UserReadAdapter` | 三个新读方法；`to` 的计算（末码点加一）是这里的私有方法，两路共用 |
| infra | `FormerUsernameDao.findByUsernameInAndReservedUntilAfter(Collection<String>, Instant)`、`FormerUsernameRepositoryAdapter.findAllReserved(Collection, Instant)` | 按名字分组取 `replacedAt` 最大的 |
| infra | `db/migration/V2__add_mention_prefix_indexes.sql` | 第 4.5 节，两个索引 |
| adapter | `UserEndpointImpl` | 两个新方法：请求 → 服务 → 响应；`UserSummaryView → UserSummaryResp` 与现有 `summaries` 共用 `toResp`；`UserStatus → AccountStatus` 按名字映射 |

两个服务都只读，不经 CommandBus，没有事务，与 `UserSummaryQueryService` 同一做法。错误走现有的 `InvalidUserFieldsException`（422，`IDN-0422`，`errors[]`）。

## 7. 迁移

`patra-identity-infra/src/main/resources/db/migration/V2__add_mention_prefix_indexes.sql`，只有第 4.5 节那两个 `CREATE INDEX` 和注释。R6（`checkMigrationColumns`）只查建表与加列里的用户名列，加索引不受影响。mini 上随 identity 新镜像启动时由 Flyway 执行，不用手工操作；两张表都只有几十行，建索引不锁业务。

## 8. 测试

### 8.1 单元测试

| 测试 | 覆盖 |
|---|---|
| `UsernamePrefixTest`（新） | 合法：`a`、`ab`、`chen-`、`chen_y`、`123`、30 个字符的合法名；规范化：` Chen ` → `chen`；不合法：`null`、空串、空白、`-a`、`_a`、`a--`、`a-_`、`a b`、`a%`、`陈`、31 个字符 |
| `DisplayNamePrefixTest`（新） | 合法：`张`、`Chen`（转成 `chen`）、` Chen  Yu `（折成 `chen yu`）、`🍎`、`-a`；不合法：`null`、空串、空白、控制字符、私用区字符、孤立代理项、超过 120 字节 |
| `UserSuggestionQueryServiceTest`（新） | `prefix` 为 `null`、`limit` 为 `null` / 0 / 21 → 422 且字段与原因码对（两个都错时一起报）；两路都不可能的前缀 → 空结果且端口零调用；端口收到规范化后的前缀与 `CANDIDATE_CAP`；去掉访客本人；去掉不可见的（他人主页 `SELF`、邮箱未验证、匿名看 `SIGNED_IN`）；保持端口的顺序；截到 `limit`；`张`、` chen yu` 只查昵称一路；`chen` 两路都查，用户名一路在前、按用户 ID 去重；用户名一路已够 `limit` 时不查昵称一路 |
| `UserResolutionQueryServiceTest`（新） | `null` → 422 `REQUIRED`；101 个 → 422 `TOO_MANY`；全是 `null` / 不合语法 → 空结果且两个端口零调用；当前名命中（含非 `ACTIVE`，带状态）；当前名没有、保留旧名命中 → 原主 ID 与原主状态；旧名仓储只收到第 1 步没查到的名字，且收到的时刻是时钟的此刻；都没有 → 省略；结果按请求顺序，原文相同的去重、大小写不同的各占一行 |

### 8.2 集成测试

| 测试 | 覆盖 |
|---|---|
| `UserReadAdapterIT`（改） | `findActiveSummariesByUsernamePrefix`：前缀边界（前缀 `chen_` 不匹配 `chen-yu`、`chena`；`chen-` 不匹配 `chen0`、`chen_li`；`chen` 匹配 `chen`、`chen-yu`、`chen_li`、`chen9`、`chena`），只含 `ACTIVE`，按字节序（`chen` < `chen-yu` < `chen9` < `chen_li` < `chena`），`limit` 截断；单字符前缀（`c` 的上界是 `d`）；`findActiveSummariesByDisplayNamePrefix`：`张` 按码点序命中 `张三`、`张三丰`、`张伟`，不含 `章子`；`chen` 不分大小写命中 `Chen Yu`；只含 `ACTIVE`、不含没设昵称的；同名按用户名；前缀 U+1F3FF（低位代理是 `\uDFFF`，按 char 加一会拆坏代理对）命中以它开头的昵称、不含以 U+1F400（上界）开头的；`limit` 截断；两个索引的定义；`findSummariesByUsernames`：多个一次读出，不存在的不返回 |
| `FormerUsernameRepositoryAdapterIT`（改） | 批量 `findAllReserved`：多个名字一次查出；同名多条取改名时间最近的；到期当刻不再保留；不在保留期的名字不返回 |
| `UserEndpointImplIT`（改，adapter 切片） | 两个新端点的请求体 / 响应体 JSON 形状（`userId` 是字符串、`accountStatus` 是名字、`username` 是原文）；`viewerUserId` 缺省时按匿名；422 的 `errors[]` |
| `UserSuggestionsFlowIT`（新，boot，`RANDOM_PORT`，PG17 + Redis，继承 `IdentityFlowITSupport`） | ① **可见性矩阵**：五种账号情形（`ACTIVE` 已验证、`ACTIVE` 未验证、`PENDING_DELETION`、`BANNED`、`DELETED`）× 三档主页可见性 × 三种访客（匿名、登录的他人、本人），每格断言「主人出现在以其用户名为前缀的联想里」当且仅当「状态是 `ACTIVE`、访客不是主人、且该访客 `GET /users/{username}` 是 200」；② 响应项字段齐全、按用户名字节序、`limit` 截断；③ 不合法前缀 200 空结果；④ **昵称**：经 `PUT /me/profile` 设昵称后，`张` 联想到昵称以「张」开头的人、`chen` 不分大小写联想到 `Chen Yu`，用户名命中排在昵称命中之前、两路都命中的只出一次，昵称命中的人同样受可见性与状态规则约束 |
| `UserResolutionFlowIT`（新，boot，同上） | 当前名（大小写不同的原文也能解析）；`PATCH /me/username` 改名后旧名解析到原用户、新名也解析到原用户；把测试时钟拨到改名后第 90 天（到期当刻）旧名不再解析；不存在的名字省略；`BANNED`、`PENDING_DELETION`、`DELETED` 带状态返回；不合语法的元素省略、全部不合法时空结果；101 个 → 422 |
| `GatewayBlockedPathsIT`（改，`patra-gateway-boot`） | 拒绝名单加 `/patra-identity/_internal/users/suggest` 与 `/patra-identity/_internal/users/resolve/by-usernames`：匿名与带令牌都 403、下游收不到请求（AC 4） |

「本人看自己」在矩阵里恒为不出现（排除本人），照样逐格跑，钉住这条规则。

### 8.3 门控

`./gradlew spotlessApply` → identity 各模块 `check`（含 SpotBugs、R6 `checkMigrationColumns`、`dumpModuleGraph` 一致性；本 Issue 不新增模块与依赖）→ identity 各模块 `integrationTest` → `:patra-api:patra-gateway-boot:integrationTest`。第 4.5 节两个索引的通用计划验证各手工做一次并记录。PR 前的全量 `./gradlew check` 在后端统一 PR 时跑。

## 9. 文档

| 文件 | 改动 |
|---|---|
| `patra-api/patra-identity/README.md` | 开头的工程设计列表加本 spec；第 1 节模块表的 domain 行加 `UsernamePrefix`、`DisplayNamePrefix`，app 行加两个服务；第 2 节接口表加两行；新增第 17 节「提及支撑（内部接口）」：用途、请求、结果（含昵称一路）、读法与两个索引、调用方的义务 |

## 10. 交接给 interaction（PAP-121）

- 两个方法都在 `patra-identity-api` 的 `UserEndpoint` 上，现有的 identity HTTP Interface 客户端（`IdentityClientConfiguration`）直接能调，不用新建分组。失败处理照 `UserDirectoryAdapter` 的三种异常。
- **联想**：`prefix` 传 `@` 后面已输入的原文（可以是中文昵称的开头，不用自己判是不是用户名）；`viewerUserId` 填当前请求断言里的用户 ID。identity 的结果已排除访客本人、用户名命中在前、昵称命中在后，最多 `limit` 个；interaction 把参与者排在前面、按 `userId` 去重后再截断，所以 `limit` 可以要得比展示的多（上限 20）。参与者列表如要和全站结果一致，也排除访客本人和匿名作者（后者是 release spec 的要求）。
- **解析**：传 `@` 后面的原文（不带 `@`），一次最多 100 个；结果按原文配回。只把 `accountStatus == ACTIVE` 的换成 `<@userId>`，其余保持纯文本；「一条最多 @ 10 人」按解析出的不同 `userId` 计数（大小写不同的原文会解析到同一个人）。
- 解析不看主页可见性：主页不可见的正常账号也能被 @ 到（release spec 2.1.1）。渲染 `<@userId>` 时取名片走批量摘要，`profileVisible` 为假的人不要放出用户名与头像地址（PAP-108 第 5.5 节的义务不变）。

## 11. 验收对照

| AC | 证据 |
|---|---|
| 联想不返回 `BANNED`、`PENDING_DELETION`、`DELETED`，也不返回主页对访客不可见的账号（集成测试覆盖三档可见性） | `UserSuggestionsFlowIT` ①；`UserReadAdapterIT`；`UserSuggestionQueryServiceTest` |
| 批量解析覆盖当前名、保留期内旧名、过期旧名（不解析）、不存在、非正常账号（带状态） | `UserResolutionFlowIT`；`UserResolutionQueryServiceTest`；`FormerUsernameRepositoryAdapterIT` |
| 不符合用户名语法的输入直接返回空，不查库 | `UserResolutionQueryServiceTest`、`UserSuggestionQueryServiceTest`（端口零调用）；两个 FlowIT 的空结果 |
| 两个接口经网关访问都是 403 | `GatewayBlockedPathsIT` |
| 前缀查询有合适的索引（To-Do） | `V2` 两个索引；第 4.5 节的通用计划实测 |
| 联想按昵称前缀（2026-10-27 用户拍板） | `UserSuggestionsFlowIT` ④；`UserReadAdapterIT` 昵称用例；`UserSuggestionQueryServiceTest`；`DisplayNamePrefixTest` |

## 12. 由我定的点与待拍板的点

**由我定的**：

1. **昵称索引做不到「只覆盖 ACTIVE」（有意偏离编排会话的说明）**：状态在 `idn_user`、昵称在 `idn_user_profile`，部分索引不能引用别的表；改为只覆盖已设昵称的行，`ACTIVE` 由查询联表过滤，实测通用计划下仍是索引范围扫描（4.5）。
1a. **昵称匹配不分大小写**：两边都转小写，`chen` 能联想到 `Chen Yu`；`Locale.ROOT` 与 PG `lower()` 在极少数字符上可能不一致（4.3）。
1b. **两路的排序**：用户名命中在前（按用户名字节序），昵称命中在后（按小写昵称码点序、同名按用户名），按用户 ID 去重；用户名一路已够 `limit` 时不查昵称一路（4.3）。
1c. **昵称一路只看已设昵称的行**：没设昵称的人昵称跟随用户名，已由用户名一路覆盖；只匹配开头（4.3、4.5）。
1d. **昵称前缀的规则**：与存库的昵称同一套规范化，非空、≤ 120 字节、不含昵称里不可能出现的字符；不判最短字素数与拒绝词（4.4）。
2. **联想排除访客本人**（4.3）。
3. **候选每路最多扫 200 个**，过滤后不足 `limit` 就返回不足的（4.3）。
4. **`limit` 必填、1–20**（4.2）。
5. **联想项复用 `UserSummaryResp`**，外层另起 `UserSuggestionsResp`；多给 `avatarPalette`（4.1）。
6. **只认 `ACTIVE` 写在查询里**，与部分索引的谓词一致（4.3、4.5）。
7. **解析回显请求原文**；原文相同去重，规范化后相同的各占一行（5.1、5.2）。
8. **解析里 `null` 元素与不合语法的元素跳过，不报 422**；只有列表为 `null` 或超过 100 才 422（5.2）。
9. **解析返回全部四种状态**（含 `DELETED`），是否当成提及由 interaction 判（5.3）。
10. **新增 `V2`，不改 `V1`**；两个索引都用 `COLLATE "C"` 的部分 btree，查询用范围谓词而不是 `LIKE`；同一个未合入的改动，两个索引放同一个 `V2`（4.5）。
11. **网关只改测试**：拒绝名单 IT 加两条路径（8.2）；终审 Minor 3（这两条路径只发了 GET）按用户决定不改：规则按路径拦截，带 JSON 体的 POST 已有用例。

**待拍板**：无。
