# interaction 底座扩展工程设计（PAP-120）

> **Issue**：[PAP-120](https://linear.app/papertrace/issue/PAP-120)（GitHub #297），Linear Project P-PAP-11 v0.14 Discussion，Milestone BE Ready
> **版本**：[v0.14 Discussion](../release-specs/v0.14-discussion.md)（决策 A、B、E、H；2.1.5 账号状态与数据）
> **前序**：[对象登记表（PAP-112）](2026-10-24-subject-registry-design.md)、[collection（PAP-113）](2026-10-24-interaction-collection-design.md)、[服务骨架与子域规则（PAP-111）](2026-10-24-interaction-skeleton-design.md)、[catalog 期刊身份与批量接口（PAP-117）](2026-10-27-catalog-venue-identity-design.md) 第 7、11、13 节、[identity 提及支撑（PAP-118）](2026-10-27-identity-mention-support-design.md)
> **下游**：PAP-121（讨论）、PAP-122（投稿经验）、PAP-123（提醒）只经本文第 12 节的扩展点使用底座
> **日期**：2026-10-27
> **状态**：已定稿（2026-10-27），实施中；两处歧义按设计简报定下，依据见第 15 节

## 1. 要解决的问题

v0.14 要在 interaction 里新增讨论、投稿经验、提醒三个子域。它们都建在底座 `subject` 上，现在的底座缺这些东西：

1. `SubjectType` 只有文献。期刊页要挂投稿经验，所以期刊要登记成对象（`JOURNAL`）。期刊的身份锚是 NLM ID，catalog 换库重导会换内部 ID，这一点和文献相同（决策 B）。
2. 投稿经验按「帖子」结构存，它本身也是可以被讨论的对象（`POST`）。它是本地对象：没有外部来源，由投稿经验子域自己登记、自己改状态，不进每日对账。讨论区要给所有者标「作者」，提醒要给所有者发「回复我的」，匿名所有者要遮蔽。这两个子域都不能依赖投稿经验子域（决策 A），所以所有者和「是否匿名」要记在登记表上。
3. 能力矩阵只有「收藏文献」。
4. 讨论、投稿经验、提醒都要用户名片，可名片在 collection 的私有包里，别的子域用不了。
5. 发评论、写经验都要检查发表资格（邮箱已验证、账号 `ACTIVE`），还要限频率（决策 E）。这段逻辑由底座提供，两边共用。
6. 注销清理写死在 collection 里。按决策 H，提醒和收藏要删，讨论和经验保留，所以清理要改成底座的公共流程：枚举用户、回查 identity 都由底座做一次，各子域只登记自己的清理动作。
7. ArchUnit 的子域清单写死为 `subject`、`collection`，没有事件契约这一类公开包，也管不住决策 A 规定的依赖方向。

此外要汇总本版的上线 runbook。

## 2. 范围

**做**：

- domain：`SubjectType` 加来源属性、`JOURNAL`、`POST`；`NaturalKeyScheme` 加 `NLM`、`POST`；能力加 `DISCUSS`、`SUBMISSION_EXPERIENCE`；快照加期刊缩写；所有者值对象 `SubjectOwner`；名片三件套与它的异常挪进底座；底座的原因码枚举 `SubjectErrorTrait`（含「对象已失效」）；发表守卫的端口、规则、异常；注销清理的扩展点。
- app：本地对象的登记与更新；启动校验与对账改为只管外部类型；发表守卫；注销清理的公共流程；collection 的清理动作。
- infra：两条迁移；登记表读写带上所有者；期刊来源策略（`VenueEndpoint`）；发表计数表的持久化；名片适配器挪包。
- adapter：注销清理任务挪进底座（handler 名不变）。
- boot：`CatalogClientConfiguration` 加 `VenueEndpoint` 代理。
- `patra-tool-test`：`SubdomainArchitectureRules` 的公开包加事件契约与底座的几类包，新增「跨子域依赖必须声明」规则。
- 测试：各层单元测试；infra 与 boot 的集成测试覆盖全部 AC（第 13 节）；为了避免 PAP-121 / 122 / 123 并行时改到同一行，几处集成测试的写法要调整，集成测试的共用底座也一并抽出（第 13.4 节）。
- 文档：服务 README、v0.14 上线 runbook、共享交接笔记 `interaction-PAP-120-handoff.md`。

**不做**：

- 讨论、投稿经验、提醒三个子域的任何代码、表和 schema（PAP-121 / 122 / 123）。本 Issue 只把它们写进子域清单，并定好它们怎样用底座。
- 任何 REST 接口。发表守卫的错误码在错误引擎这一层验证（第 13.3 节），第一个经 HTTP 打到它的是 PAP-121。
- 进程内事件本身：事件类和发布、订阅都归 PAP-121 / 123。本 Issue 只定包、写法和 ArchUnit 规则（第 11.3 节）。
- collection 设公开的资格检查不改为调发表守卫：两者原因码同名，规则也相同，但设公开不限频率，现有代码不动。
- 一个用户在冷静期能否编辑已发表的内容：由 PAP-121 / 122 决定是否在编辑时也调 `requireEligible`。

## 3. 已定的前提

| 来源 | 内容 |
|---|---|
| 决策 A | 新增子域 `discussion`、`post`、`notification`，每个子域一个 schema，共用一份 Flyway 历史；评论写入时同事务发布进程内领域事件，提醒订阅后写入；依赖方向：提醒 → 讨论（只依赖事件契约）→ 底座；投稿经验 → 底座；无环；子域 ArchUnit 规则的公开包加「事件契约」 |
| 决策 B | `JOURNAL`：对外 key 是 catalog 期刊内部 ID，`natural_key` 是 `nlm:{NLM ID}`（字符串，有前导零），解析与对账照文献；`POST`：本地对象，`post:{ID}`，投稿经验子域经底座网关登记、删除时改状态，不经外部解析、不对账，启动校验对本地类型豁免；登记表加所有者用户 ID 与「所有者是否匿名」（外部对象为空，登记后不变）；能力矩阵：文献 {收藏、讨论}，期刊 {挂投稿经验}，投稿经验 {讨论} |
| 决策 E | 资格：邮箱已验证、`ACTIVE`，每次写入经 identity 内部接口回查；频率：PG 里按作者计数，评论间隔 ≥ 10 秒、每天 ≤ 100 条，经验每天 ≤ 10 条，逻辑放底座的「发表守卫」；不用 Redis |
| 决策 H | 注销后评论与经验保留，提醒与收藏删除；清理改为底座公共流程，各子域登记自己的动作 |
| 设计简报附录 A | 写接口的原因码分开：频率（间隔 10 秒）、每日上限、邮箱未验证、账号状态异常；提醒上下文里，投稿经验要显示「期刊缩写 + 投稿月份」 |
| PAP-117 交接（第 13 节） | `VenueEndpoint` 的两个解析端点，1 ~ 100 个键；NLM 校验 `[0-9]{1,19}[A-Z]?`（区分大小写）；首次登记用 by-ids，`exists: false` 或 `nlmId` 为 null 拒绝，锚取返回的主 `nlmId`；对账用 by-nlm-ids，**按响应的 `key` 对回登记行**，锚保持登记时的值；软删期刊能否首次登记、对账时 `exists: false` 的处理由本 Issue 定；客户端在 `CatalogClientConfiguration` 里用同一个 `RestClient` 再 `createProxy` |
| 编排会话 | 三个新子域由本 Issue 一次性加进子域清单；讨论发布、提醒订阅的事件放哪个包、规则怎么写由本 Issue 定；发表计数放哪由本 Issue 定（PAP-121 / 122 的表还不存在）；`CollectionFlowIT` 不改断言通过 |
| PAP-112 第 11 节第 8 条 | 当时底座的 `exception` 包不公开，异常直接传播。本 Issue **有意推翻**，理由见第 11.1 节 |

## 4. 对象类型与能力矩阵（`domain.subject.model.enums`，公开）

### 4.1 `SubjectType`

```java
public enum SubjectType {
  PUBLICATION((short) 1, "publication", "文献", SubjectOrigin.EXTERNAL, NaturalKeyScheme.PMID,
      EnumSet.of(SubjectCapability.COLLECT, SubjectCapability.DISCUSS)),
  JOURNAL((short) 2, "journal", "期刊", SubjectOrigin.EXTERNAL, NaturalKeyScheme.NLM,
      EnumSet.of(SubjectCapability.SUBMISSION_EXPERIENCE)),
  POST((short) 3, "post", "投稿经验", SubjectOrigin.LOCAL, NaturalKeyScheme.POST,
      EnumSet.of(SubjectCapability.DISCUSS));
}
```

| 新成员 | 说明 |
|---|---|
| `SubjectOrigin { EXTERNAL, LOCAL }`（新枚举，`model.enums`） | 外部对象有来源策略、进每日对账；本地对象由所属子域登记和改状态 |
| `origin()` / `isLocal()` | 来源属性 |
| `displayName()` | 消息里的名词：文献、期刊、投稿经验。PAP-112 当时就说「多一种类型时按类型给消息」，现在兑现：`SubjectNotFoundException` 的消息改成「{名词}不存在或已被删除」，`SubjectSourceUnavailableException` 改成「{名词}信息暂时无法获取，请稍后再试」，两者都多带一个 `SubjectType` 参数 |
| `externalTypes()` | 全部外部类型，给启动校验和对账用 |

- `code`、`slug` 照旧，永不复用、永不改。`POST` 的 `displayName` 本版是「投稿经验」，因为本版的帖子只有这一种，界面上也不出现「帖子」。以后加求助帖时再改这个名词。
- `parseKey` 对三种类型都一样：规范的十进制正整数。期刊的 key 是 catalog 期刊 ID，投稿经验的 key 是帖子 ID，两者都是 Snowflake。
- 「新增一种对象」的步骤改成：加常量；外部对象再加一个来源策略；写一条迁移改 `ck_registry_target_type` 和格式约束；本地对象还要改 `ck_registry_owner_local`（第 10.1 节）。

### 4.2 `NaturalKeyScheme`

| 常量 | 前缀 | 值的格式 | 说明 |
|---|---|---|---|
| `PMID` | `pmid` | `[1-9][0-9]{0,14}` | 不变 |
| `NLM` | `nlm` | `[0-9]{1,19}[A-Z]?` | 与 catalog 一致，区分大小写。mini 上有前导零、`R` / `A` 后缀和 16 位的新式 ID，照搬 PMID 的写法会把它们拒掉 |
| `POST` | `post` | `[1-9][0-9]{0,18}` | 帖子 ID，Snowflake |

`natural_key` 列是 `VARCHAR(64)`，`nlm:` 加 20 个字符一共 24 个，够用。

### 4.3 `SubjectCapability`

`COLLECT`（收藏）、`DISCUSS`（讨论）、`SUBMISSION_EXPERIENCE`（挂投稿经验）。矩阵见 4.1。PAP-122 在期刊下写经验前，调 `resolveForInteraction(JOURNAL, 期刊 ID, SUBMISSION_EXPERIENCE)`，期刊第一次被挂经验时就在这一步登记。

### 4.4 快照 `SubjectSnapshot`（`model.vo`，公开）

快照是 jsonb，新增一个可空字段 `abbreviation`。旧行没有这个键，读回来就是 null，不用迁移。为什么扩快照而不加列，见第 15 节第 1 条。

| 类型 | `title` | `journal` | `publishedDate` | `abbreviation` |
|---|---|---|---|---|
| `PUBLICATION` | 文献标题 | 期刊全称 | 出版日期（降精度） | null |
| `JOURNAL` | 期刊全称（catalog `title`） | null | null | MEDLINE 刊名缩写（catalog `abbreviatedTitle`，可空） |
| `POST` | 由 PAP-122 定 | 由 PAP-122 定 | 由 PAP-122 定（格式仍须是降精度日期） | 由 PAP-122 定 |

`POST` 的快照由投稿经验子域在登记和编辑时写入（第 6.2 节），底座不解释它的含义。PAP-123 要在提醒上下文里显示「期刊缩写 + 投稿月份」，只能从登记表拿，因为它不能依赖投稿经验子域。建议 PAP-122 这样填：`journal` 放期刊全称，`abbreviation` 放期刊缩写，`publishedDate` 放投稿月份（`yyyy-MM`）。期刊的全称和缩写可以从该期刊登记行的快照里取。catalog 的 `abbreviatedTitle` 可能为空，`abbreviation` 为 null 时上下文行退回用 `journal`（全称）。最后由 PAP-122 与 PAP-123 商定。

`of()` 改成四个参数（符合「≤ 4 个参数用 `of()`」）。

## 5. 期刊：解析与对账

### 5.1 来源策略 `JournalSubjectSourceAdapter`（`infra.subject.source`）

- `type()` 返回 `JOURNAL`。
- `resolveByKey(id)`：调 `VenueEndpoint.resolveByIds([id])`，取唯一一行，**锚用返回的 `nlmId`**（主 NLM）。
- `resolveByNaturalKeys(keys)`：调 `resolveByNlmIds`，传锚的值，最多 100 个。每行 `exists: true` 的结果，**锚用这一行的 `key`**（也就是请求时的值），不用 `nlmId`。返回的映射以这个锚为键，`SourceRecord.naturalKey` 也是它。
- 行 → `SourceRecord`：`exists: false` 时没有记录；锚不合 `NLM` 的格式时没有记录并记 WARN（只会在首次登记、`nlmId` 为 null 时出现）；`state` 映射 `ACTIVE` / `DELETED` → `SourceState`；快照按 4.4。
- 失败翻译与文献相同：`RemoteCallException`、`ResourceAccessException`、`IllegalStateException` 都翻成 `SubjectSourceUnavailableException(JOURNAL, cause)`。两个来源策略把这段 try/catch 抽成包内的 `CatalogSourceCalls`，`try` 只包住一次代理调用，日志带上类型。

### 5.2 首次登记

走现有的 `SubjectGatewayImpl.resolveForInteraction`，步骤不变：先查本地；查不到才调来源；不存在、没有锚、**软删**都拒绝（404），不写库。来源返回软删时，异常额外带原因码 `SUBJECT_UNAVAILABLE`（第 6.3 节）。

软删期刊不能首次登记（由我定，与文献一致）：catalog 的期刊详情对软删期刊返回 404，用户进不了期刊页，自然也写不了经验；已经登记的期刊被软删后，由对账置为 `UNAVAILABLE`。

### 5.3 对账

`ReconcileSubjectsHandler` 现在只遍历外部类型（第 6.4 节），`JOURNAL` 和文献一样每页 100 行，交给 `resolveByNaturalKeys`，再套用 `SubjectAggregate.reconcile` 的三行规则：

| catalog 结果 | `source_id` | `state` | 快照 |
|---|---|---|---|
| 存在，ACTIVE | 返回的 `id`（可能变了） | ACTIVE | 用新值 |
| 存在，DELETED | 返回的 `id` | UNAVAILABLE | 用新值 |
| 不存在（`exists: false`） | 置空 | UNAVAILABLE | 保留 |

**锚永远不变**：对账按请求的 `key` 对回，副 NLM 解析出来的主 NLM 不会写进 `natural_key`。比如整库重导后，原来的主 NLM `0404401` 变成了副 NLM，`resolveByNlmIds(["0404401"])` 返回 `key = "0404401"`、`nlmId = "100970884"`、`id = 新 ID`，登记行仍是 `nlm:0404401`，`source_id` 换成新 ID，`subject_id` 不变。

### 5.4 已知限制：合并期刊在重导窗口里被首次登记

mini 上有 15 个期刊是同一份刊物改名前后的两条 NLM 记录合并成的（主 NLM + 副 NLM）。整库重导后主副可能互换。如果某个合并期刊早就登记过（锚是原主 NLM `A`），而重导后、对账前有人按新的期刊 ID 首次解析它，会出现下面的情况：本地按新 ID 查不到，于是调 by-ids，拿到的主 NLM 是 `B`，按 `nlm:B` 新建一行；之后每次对账，`A`、`B` 两行都解析到同一个期刊 ID，`saveReconciled` 页内后写的一方胜出，`A` 行一直是 `UNAVAILABLE`，挂在它上面的经验显示「已失效」。

这个问题不在代码里处理：要处理，就得在首次解析时知道该期刊的全部 NLM，而 PAP-117 的契约只返回主 NLM。三个条件同时满足才会触发：合并期刊、主副互换、窗口内首次登记。runbook 沿用 v0.13 的规则：**catalog 任何换库重建或重跑导入之后，立即手动触发一次 `subjectReconciliationJob`**，把窗口压到几分钟，并写明这条规则现在也保护期刊。

## 6. 本地对象 `POST`

### 6.1 登记表新增两列

| 列 | 类型 | 外部对象 | 本地对象 |
|---|---|---|---|
| `owner_user_id` | `BIGINT` | NULL | 必填，所有者用户 ID |
| `owner_anonymous` | `BOOLEAN` | NULL | 必填 |

- 这两列登记后不变：仓储里没有任何语句改它们（第 10.2 节），集成测试会核对改状态后所有者不变。不加触发器。
- 本地对象的 `source_id` 就是所属子域里的 ID，帖子就是帖子 ID，「来源」就是所属子域。所以 `uk_registry_source_id`、`findIdsByKeys`、`SubjectView.key` 对本地对象照常可用，`ck_registry_source_present` 也成立：本地对象永远有 `source_id`。
- 约束见第 10.1 节：`POST` 的 `source_id` 非空且 `natural_key` 等于 `'post:' || source_id`；本地类型当且仅当 `owner_user_id` 非空；两列同为空或同为非空。

### 6.2 网关：`SubjectGateway` 新增两个方法（`port.gateway`，公开）

```java
/// 登记一个本地对象。须在调用方的事务里调用，与对象本身的写入同一事务。
SubjectId registerLocal(SubjectType type, long localId, SubjectOwner owner, SubjectSnapshot snapshot);

/// 把本地对象的状态和快照改成给定值（删除：UNAVAILABLE；编辑：刷新快照）。须在调用方的事务里调用。
void updateLocal(SubjectType type, long localId, SubjectState state, SubjectSnapshot snapshot);
```

- `SubjectOwner(long userId, boolean anonymous)`：新值对象，放 `model.vo`（公开），用 `of()` 创建。
- 两个方法都在 `SubjectGatewayImpl` 上标 `@Transactional(propagation = Propagation.MANDATORY)`：调用方没有开事务时直接抛 `IllegalTransactionStateException`，因为登记必须和经验写入一起提交或一起回滚。这是 interaction 第一次用 `@Transactional`（之前都用 `TransactionOperations`），规则 15 允许它出现在 app 层。
- `type` 不是本地类型时，抛 `IllegalArgumentException`（程序错误）。
- `registerLocal` 直接插入一行：Snowflake 的 `subject_id`；`natural_key = {前缀}:{localId}`；`source_id = localId`；`ACTIVE`；快照；`snapshot_at = now`；所有者两列。重复登记会撞 `uk_registry_natural_key`，按程序错误处理，不做幂等。
- `updateLocal` 改 `state`、`snapshot`、`snapshot_at`。行不存在时抛 `IllegalStateException`（程序错误）。删除经验的调用方传入当前快照，编辑经验的调用方传入新快照。
- 「状态只由所属子域更新」：本地对象只有这两个方法会写；对账不碰它们（第 6.4 节）。

### 6.3 `resolveForInteraction` 对本地类型

讨论区在经验下发评论时调 `resolveForInteraction(POST, 帖子 ID, DISCUSS)`：

- 本地命中且 ACTIVE：返回 `subjectId`。
- 本地命中但 UNAVAILABLE（经验已删除）：`SubjectNotFoundException.unavailable(type, key)`，消息「投稿经验不存在或已被删除」，`traits[]` 带 `NOT_FOUND` 和原因码 `SUBJECT_UNAVAILABLE`。PAP-121 的 `composer.error.object-gone` 就按这个原因码出文案，直接传播即可，不用先查读端口。
- **本地没有**：本地类型没有来源可以解析，直接 `SubjectNotFoundException.notFound(type, key)`（只带 `NOT_FOUND`），不调任何来源。

这条规则对所有类型都一样：已登记但 UNAVAILABLE、首次解析时来源返回软删，都用 `unavailable`；从未登记且来源说不存在、没有锚、本地类型未登记，都用 `notFound`。`SubjectNotFoundException` 的构造器改为私有，只留这两个工厂方法。

### 6.4 启动校验与对账

- `SubjectSources`：外部类型必须恰好有一个来源策略，缺失或重复都让启动失败；本地类型**不能有**来源策略，有就启动失败（防止有人误给 `POST` 写来源）。
- `ReconcileSubjectsHandler`：只遍历 `SubjectType.externalTypes()`。`SubjectAggregate.reconcile` 遇到本地对象时抛 `IllegalStateException`：要是对本地对象对账，它的 `source_id` 会被清空。

### 6.5 读端口

`SubjectView` 新增 `SubjectOwner owner`，外部对象为 null。`findByIds` 读回所有者和匿名标记，PAP-121 / 123 只靠它判断「作者」标记、发「回复我的」提醒、做匿名遮蔽。`findIdsByKeys(POST, 帖子 ID)` 按 `source_id` 查，照常可用。

## 7. 用户名片挪进底座

| 原位置（collection 私有） | 新位置（底座公开） |
|---|---|
| `domain.collection.port.user.UserDirectoryPort` | `domain.subject.port.user.UserDirectoryPort` |
| `domain.collection.model.user.UserCard` | `domain.subject.model.user.UserCard` |
| `domain.collection.model.user.AccountState` | `domain.subject.model.user.AccountState` |
| `domain.collection.exception.UserDirectoryUnavailableException` | `domain.subject.exception.UserDirectoryUnavailableException` |
| `infra.collection.user.UserDirectoryAdapter`（及其单元测试） | `infra.subject.user.UserDirectoryAdapter` |

- 类名、方法签名、行为都不变，collection 只改 import。
- 端口放 `port.user` 而不是 `port.read`：`port.read` 在这个服务里是「查本地数据的 CQRS 读端」（`SubjectReadPort` 写明「只查本地」），而名片端口调的是 identity，按端口命名规范属于「外部能力 `{Function}Port`」。所以底座的公开包里加上 `port.user`、`model.user`（第 11.1 节）。
- 异常要公开，是因为 `CollectionAccess` 在本人读自己的收藏夹时要捕获它来降级，讨论区和提醒以后也有同样的需要。

## 8. 发表守卫

### 8.1 端口 `PublishGuardGateway`（`port.gateway`，公开；实现 `PublishGuardGatewayImpl` 在 `app.subject.service`）

```java
public interface PublishGuardGateway {
  /// 资格：事务外调用，回查 identity。返回自己的名片（viewer = 自己），调用方可以直接拿来拼响应。
  UserCard requireEligible(long userId);

  /// 频率：在写入内容的同一事务里、第一个写操作之前调用。放行时记一笔，事务回滚时这笔也一起回滚。
  void admit(long userId, PublishKind kind);
}
```

调用顺序（给 PAP-121 / 122）：

```java
UserCard self = publishGuard.requireEligible(author);   // 事务外：远程调用不占连接
// …其他事务外的远程调用（解析 @、首次登记对象）…
transactions.execute(status -> {
  publishGuard.admit(author, PublishKind.COMMENT);       // 事务里的第一个写操作之前
  // …写评论、发布事件…
});
```

### 8.2 资格

`users.findCards([userId], userId)` 取自己的名片，然后：

| identity 返回 | 结果 |
|---|---|
| 省略这个人，或 `state != ACTIVE`（BANNED、PENDING_DELETION、DELETED） | `PublishIneligibleException.accountRestricted()` |
| `ACTIVE` 且邮箱未验证 | `PublishIneligibleException.emailUnverified()` |
| `ACTIVE` 且已验证 | 返回名片 |
| identity 不可用 | `UserDirectoryUnavailableException`（503），原样传播 |

先判账号状态、再判邮箱，与 collection 设公开的顺序相同：被封禁又没验证邮箱的人，门户该给的是「账号异常」，而不是「去验证邮箱」。冷静期（PENDING_DELETION）不能发表（spec 2.1.5）。

### 8.3 频率

**种类** `PublishKind`（`model.enums`，公开）：

| 常量 | 名词 | 每日上限 | 最小间隔 |
|---|---|---|---|
| `COMMENT` | 评论 | 100 | 10 秒 |
| `POST` | 投稿经验 | 10 | 无 |

评论包括根评论和回复，文献下的和经验下的都算。编辑不算发表。删除不退还额度：计数记的是发表次数，不随内容删除而减少，否则先删后发就能绕过上限。

**「每天」是北京时间的自然日**（`Asia/Shanghai`），对应文案「今天已经发表了 100 条评论，明天再来吧」。时区是产品规则，不随部署变，所以写成规则类里的常量，不做配置项；规则是纯函数，`Instant` 与时区都作参数传入，单元测试可以拨到日界两侧。

**存储**：底座自己记一张计数表 `subject.publish_quota`，每个作者每种一行：

| 列 | 说明 |
|---|---|
| `user_id`、`kind` | 主键 |
| `quota_day` | 计数所属的自然日 |
| `day_count` | 当天已发表条数，> 0 |
| `last_published_at` | 上次发表的时刻 |

选这个形状，而不是每发表一次记一行、也不由调用方提供计数，有三个原因：讨论和经验的表还不存在，计数由底座自己掌握，PAP-121 / 122 不用为它建索引；每人每种只有一行，不需要清理过期数据；删除内容不会退还额度。

**规则** `PublishQuota`（`domain.subject.policy`，不公开）：

```
today = now 所在的北京时间日期
count = (记录存在且 quota_day == today) ? day_count : 0
count >= 每日上限          → PublishRateLimitedException.dailyLimitReached(kind, 到明天 0 点的时长)
now < last + 最小间隔      → PublishRateLimitedException.tooFrequent(kind, last + 间隔 - now)
否则                       → 新记录 (today, count + 1, now)
```

先判每日上限、再判间隔：已经到上限的人，等 10 秒也没用，告诉他「明天再来」更准确。

**并发**：`admit` 先拿事务级 advisory lock，键为 `subject.publish:{userId}`，然后读、判、写回（upsert）。同一作者的并发发表在锁上排队，后一个读到的是前一个提交后的计数；锁随调用方的事务释放。不同作者之间互不影响。

### 8.4 错误与原因码

| 情况 | 异常 | HTTP | `traits[]` | 默认文案 |
|---|---|---|---|---|
| 账号状态异常（含 identity 省略） | `PublishIneligibleException` | 409 | `CONFLICT`、`ACCOUNT_RESTRICTED` | 账号状态异常，暂时不能发表 |
| 邮箱未验证 | `PublishIneligibleException` | 409 | `CONFLICT`、`EMAIL_UNVERIFIED` | 验证邮箱后才能发表 |
| 间隔不足 | `PublishRateLimitedException` | 429 | `QUOTA_EXCEEDED`、`TOO_FREQUENT` | 发得太快了，请隔 10 秒再发 |
| 超过每日上限 | `PublishRateLimitedException` | 429 | `QUOTA_EXCEEDED`、`DAILY_LIMIT_REACHED` | 今天已经发表了 100 条评论，明天再来吧 / 今天已经发表了 10 条投稿经验，明天再来吧 |
| identity 不可用 | `UserDirectoryUnavailableException` | 503 | `DEP_UNAVAILABLE` | 用户信息暂时无法获取，请稍后再试 |

- 原因码放在底座唯一的原因码枚举 `SubjectErrorTrait`（`domain.subject.exception`），写法照 `CollectionErrorTrait`：错误引擎映射状态码时跳过它，响应的 `traits[]` 里带上它。它有五个值：上表四个，加上第 6.3 节的 `SUBJECT_UNAVAILABLE`。错误码前缀是 `INTERACTION`（`INTERACTION-0409`、`INTERACTION-0429`）。
- `ACCOUNT_RESTRICTED`、`EMAIL_UNVERIFIED` 与 collection 设公开的原因码同名、HTTP 状态相同，门户可以用同一套判断。
- `PublishRateLimitedException` 实现 `HasRetryAfter`（同 identity 的限流异常），响应带 `Retry-After` 头和 `retryAfterSeconds`。门户本版不做倒计时，但这是平台限流的统一写法。

## 9. 注销清理的公共流程

### 9.1 扩展点 `UserDataCleanupGateway`（`port.gateway`，公开）

```java
/// 一个子域里属于某个用户、要在注销后删除的数据。各子域实现，底座的注销清理调用。
public interface UserDataCleanupGateway {
  String subdomain();                                   // 日志用，如 "collection"
  List<Long> findUsersAfter(long afterUserId, int limit); // 有数据的用户，按 ID 升序
  int deleteAllOf(long userId);                         // 删他的全部数据，返回行数；在底座开的事务里调用
}
```

这是「底座调子域」的扩展点。它放在 `port.gateway`，实现类必须叫 `*GatewayImpl` 并放在各子域的 app 层，原因是两条层规则夹住了它：

- `repositoryImplsShouldResideInInfra`：实现了 `..domain..port..` 下的接口、名字不以 `GatewayImpl` 结尾的类，必须在 infra。
- `gatewayImplsShouldResideInApp`：`*GatewayImpl` 必须在 app。

各子域的清理要用自己的仓储（collection 要拿用户锁再删三张表），这些只能在 app 里编排，所以实现类命名为 `{子域}UserDataCleanupGatewayImpl`。读的时候注意：这个接口由别的子域实现，不是底座自己的驱动端口。

### 9.2 流程 `CleanupDeletedUsersHandler`（`app.subject.usecase.cleanup`）

```
cursor = 0
循环：
  候选 = 每个参与方 findUsersAfter(cursor, 100) 的并集，去重、升序，取最小的 100 个
  空 → 结束
  cursor = 候选里最大的那个
  identity 批量摘要（viewer = null），一批一次
    不可用 → failedBatches + 1，记 WARN，继续下一批
  对候选里的每个人：
    identity 省略 → 记 WARN，不删（省略不等于已注销）
    不是 DELETED → 跳过
    DELETED → 对每个参与方各开一个事务：deleteAllOf(userId)，累计行数
```

- **合并游标是对的**：设某个参与方 P 在 `cursor` 之后有用户 u，且 u ≤ 新游标，却不在 P 返回的列表里。那 P 返回的就是 100 个比 u 小的用户，它们都在并集里，于是最小的 100 个都比 u 小，新游标也就比 u 小，与假设矛盾。
- 每个（用户，参与方）一个事务：参与方之间互不影响，与以后按子域拆服务的方向一致。清理是幂等的，中途失败了次日重跑即可。
- 参与方抛出的意外异常不吞：任务失败、调度中心标红，与现在 collection 的行为一致。
- 结果 `CleanupResult(scannedUsers, deletedUsers, deletedRows, failedBatches)`；INFO 汇总行里按子域列出删除的行数。

### 9.3 参与方

| 参与方 | 位置 | 数据 |
|---|---|---|
| `CollectionUserDataCleanupGatewayImpl` | `app.collection.service` | `findUsersAfter` → `LibraryReadPort.findOwnersAfter`（文献库条目与收藏夹两表的并集，现成）；`deleteAllOf` → `lockOwner` + `LibraryRepository.deleteAllOf`，三项行数相加 |
| `SubjectUserDataCleanupGatewayImpl` | `app.subject.service` | 发表计数 `subject.publish_quota` 里他的行（第 8.3 节） |
| （PAP-123）提醒 | `app.notification…` | 他收到的提醒 |

讨论和投稿经验不登记参与方：内容保留，作者显示「已注销用户」由 identity 的占位名片负责（决策 H）。

collection 原来的 `app.collection.usecase.cleanup` 下三个类（命令、处理器、结果）删除，它们的测试改写成底座处理器的测试和 collection 参与方的测试。`LibraryReadPort.findOwnersAfter`、`LibraryRepository.deleteAllOf`、`lockOwner`、`DeletedUserData` 保留，由参与方调用。

### 9.4 任务

`DeletedUserCleanupScheduleJob` 从 `adapter.collection.scheduler` 挪到 `adapter.subject.scheduler`，**handler 名 `deletedUserCleanupJob`、Cron、超时都不变**，mini 的调度中心不用改。成功回写改成「已核对 N 人：清理 a 人，删除 b 行」；有失败批次时仍是「已核对 N 人，失败 M 批」。

`CollectionFlowIT` 里的 `import …app.collection.usecase.cleanup.CleanupDeletedUsersCommand` 必须改成底座的命令（底座的处理器不能依赖 collection 的类，规则 26）。这是该文件唯一的改动，断言一行不动。

## 10. 存储

### 10.1 迁移 `V20261027120000__extend_registry_for_journal_and_post.sql`（`db/migration/subject/`）

```sql
-- ============================================================
-- subject.registry 扩到期刊与本地对象（PAP-120）
-- 类型 2 = JOURNAL（外部，nlm: 锚）、3 = POST（本地对象，post: 锚，source_id 是帖子 ID）；
-- 所有者两列：本地对象必填，外部对象为空，登记后不变。
-- ============================================================

ALTER TABLE subject.registry
    ADD COLUMN owner_user_id   BIGINT   NULL,
    ADD COLUMN owner_anonymous BOOLEAN  NULL;

ALTER TABLE subject.registry DROP CONSTRAINT ck_registry_target_type;
ALTER TABLE subject.registry
    ADD CONSTRAINT ck_registry_target_type CHECK (target_type IN (1, 2, 3)),
    ADD CONSTRAINT ck_registry_journal_key CHECK (target_type <> 2 OR natural_key ~ '^nlm:[0-9]{1,19}[A-Z]?$'),
    ADD CONSTRAINT ck_registry_post_key CHECK (target_type <> 3 OR (source_id IS NOT NULL AND natural_key ~ '^post:[1-9][0-9]{0,18}$' AND natural_key = 'post:' || source_id)),
    ADD CONSTRAINT ck_registry_owner_pair CHECK ((owner_user_id IS NULL) = (owner_anonymous IS NULL)),
    ADD CONSTRAINT ck_registry_owner_local CHECK ((target_type = 3) = (owner_user_id IS NOT NULL));

COMMENT ON COLUMN subject.registry.target_type IS 'SubjectType code: 1 = PUBLICATION, 2 = JOURNAL, 3 = POST (local); never reused';
COMMENT ON COLUMN subject.registry.natural_key IS 'Identity anchor: pmid:…, nlm:… (string, leading zeros kept), post:{post id}';
COMMENT ON COLUMN subject.registry.source_id IS 'External: cached catalog internal ID, null when it vanished; local: the owning subdomain''s ID';
COMMENT ON COLUMN subject.registry.owner_user_id IS 'Owner user ID for local objects (POST); null for external objects; never changes';
COMMENT ON COLUMN subject.registry.owner_anonymous IS 'Whether the owner chose to be anonymous; null for external objects; never changes';
```

- `ck_registry_post_key` 必须显式写 `source_id IS NOT NULL`：`source_id` 为 NULL 时 `'post:' || source_id` 是 NULL，整个表达式也是 NULL，CHECK 会放行；失效行又满足 `ck_registry_source_present`，两条都拦不住（收尾评审发现，初稿漏了这一句）。
- mini 上现有的行都是类型 1、两列为空，新约束全部通过。加可空列不重写表。
- 回滚到旧镜像也安全：旧代码插入时不写两个新列（为空），类型 1 满足全部约束。

### 10.2 迁移 `V20261027120100__create_publish_quota.sql`

```sql
-- ============================================================
-- subject.publish_quota：发表守卫的按作者计数（PAP-120，决策 E）
-- 每个作者每种发表一行：当天（北京时间自然日）已发条数与上次发表时刻；删除内容不退还额度。
-- ============================================================
CREATE TABLE subject.publish_quota
(
    user_id            BIGINT          NOT NULL,
    kind               VARCHAR(16)     NOT NULL,
    quota_day          DATE            NOT NULL,
    day_count          INTEGER         NOT NULL,
    last_published_at  timestamptz(6)  NOT NULL,
    PRIMARY KEY (user_id, kind),
    CONSTRAINT ck_publish_quota_kind CHECK (kind IN ('COMMENT', 'POST')),
    CONSTRAINT ck_publish_quota_day_count CHECK (day_count > 0)
);
-- 每列 COMMENT ON（英文，同 registry）
```

- 不加 `created_at` / `updated_at`：`last_published_at` 就是这一行最后一次改动的时刻。
- 注销清理按主键 `(user_id, kind)` 的前导列找「游标之后有计数的用户」。

### 10.3 持久化写法

继续用 `JdbcClient` 写原生 SQL（同 PAP-112 的实施偏离 #1）。

- `SubjectRows.COLUMNS` 加 `owner_user_id, owner_anonymous`，读回 `SubjectOwner`（`owner_user_id` 为空时 owner 为 null）。
- `SubjectRepository` 新增 `SubjectAggregate insertLocal(SubjectType, long localId, SubjectOwner, SubjectSnapshot, Instant now)`（`INSERT … RETURNING`）和 `boolean updateLocal(SubjectType, long localId, SubjectState, SubjectSnapshot, Instant now)`（按 `target_type` + `source_id` 更新，只写 `state`、`snapshot`、`snapshot_at`）。现有的 `upsertResolved`、`saveReconciled` 不写所有者两列。
- `PublishQuotaRepository`（`port.repository`）由 `PublishQuotaRepositoryAdapter`（`infra.subject.persistence`）实现：`lock(userId)`、`find(userId, kind)`、`save(userId, kind, quota)`（`ON CONFLICT (user_id, kind) DO UPDATE`）、`findUsersAfter(after, limit)`、`deleteAllOf(userId)`。

## 11. 子域边界（ArchUnit）

### 11.1 公开包

| 子域 | 公开包（相对 `domain.{子域}`） |
|---|---|
| 任意子域 | `port.read`、`model.read`、**`event`（事件契约，新增）** |
| 底座另加 | `port.gateway`、`model.vo`、`model.enums`，**`port.user`、`model.user`（名片，新增）**，**`exception`（新增）** |

- 底座公开 `exception`，是**有意推翻** PAP-112 第 11 节第 8 条（当时让异常不公开、直接传播）。原因有二：底座端口的调用方有时要捕获异常来降级（`CollectionAccess` 捕获 `UserDirectoryUnavailableException`）；底座的异常本来就是它端口契约的一部分。默认做法不变：不捕获，直接传播到 web 层。
- 这几项都写进 `SubdomainArchitectureRules` 的两个常量。这个规则类目前只有 interaction 在用，加的都是通用的类别名，别的服务没有这些包时规则照样成立。

### 11.2 新规则：跨子域依赖必须声明

决策 A 的方向光靠「无环」和「只经公开包」管不住：讨论读投稿经验的读端口（决策 B 明确否决），提醒读讨论的读端口（只许依赖事件契约），这两种现有规则都放行。所以加一条规则，并给规则类加两个声明边的方法：

```java
new SubdomainArchitectureRules("dev.patra.interaction", "subject",
        Set.of("subject", "collection", "discussion", "post", "notification"))
    .allowingSubscription("notification", "discussion");
```

| 方法 / 规则 | 内容 |
|---|---|
| `allowingDependency(from, to)` | 声明 `from` 可以经 `to` 的全部公开包依赖它（本服务暂时不用；让「非底座子域的读端口」这一能力继续可用） |
| `allowingSubscription(from, to)` | 声明 `from` 只能依赖 `to` 的事件契约 `domain.{to}.event..` |
| `crossSubdomainDependenciesAreDeclared()` | 子域 A 依赖子域 B（B ≠ A）的类时：B 是底座，放行；声明了依赖边，放行（是否在公开包里由规则 25 管）；声明了订阅边，目标必须在 B 的事件契约里；否则违规 |

- 两个方法都返回新实例，原实例不变。以下情况都抛 `IllegalArgumentException`：名字不在子域清单里、`from == to`、`from` 是底座（底座不依赖任何子域，规则 26）、`to` 是底座（依赖底座无需声明）。
- `allRules()` 由四条变成五条。

### 11.3 事件契约

| 项 | 规定 |
|---|---|
| 包 | 发布方子域的 `domain.{发布方}.event`，如 `domain.discussion.event`。层规则 14 已要求 `*Event` 放在 `..domain..event..` |
| 类 | record，名字以 `Event` 结尾，纯 Java |
| 字段类型 | 只能是 JDK 类型（`Long`、`Instant`、`List<Long>`…）、底座公开包里的类型（`SubjectId`、`SubjectType`…）、同一个 `event` 包里的类型。**不能用发布方 `model.vo` 等不公开包里的类型**：订阅方读到这个字段，就直接依赖了那个类型，会违反规则 25 |
| 发布 | 发布方的 app 层在写入事务里调 Spring 的 `ApplicationEventPublisher.publishEvent(event)`（ingest 已有先例） |
| 订阅 | 订阅方的 app 层用 `@EventListener`：同步、同一线程、同一事务。**不用 `@TransactionalEventListener`**，它默认在提交后才执行，提醒会丢（决策 A「同事务写入，不会丢」）。订阅方抛出异常就传回发布方，整个事务回滚（D2「一方失败，两边都不落库」）。建议放 `app.notification.eventhandler` |
| 依赖声明 | interaction 只声明一条订阅边：`notification` → `discussion` |

以后迁到通知中心时，把订阅换成 Outbox 中继，发布方不用改。

### 11.4 interaction 的架构测试

- 子域清单一次写满五个，再加上一条订阅边（11.2）。
- 规则 22b（「每层每个子域都被导入」）改成自推导，**不建空包**（README「不建没有代码的空包和空 schema」仍然成立）：
  - 底座在 domain、app、infra、adapter 四层都必须被导入。导入条件写错时，这一条先红，其余规则才不会对空集合判通过。
  - 清单里其余子域，只要有一层出现了它的包，四层就都要有。PAP-121 / 122 / 123 谁先加代码，谁就得把四层补齐，谁也不用改这条测试。
- 新增规则 27：跨子域依赖必须声明。
- README「新增子域时同时改两处」改成：子域清单已含五个；以后新增子域才改清单；子域的各层包随第一批代码建。

## 12. 给 PAP-121 / 122 / 123 的扩展点（同步写进共享笔记 `interaction-PAP-120-handoff.md`）

| 需要 | 用什么 |
|---|---|
| 在文献 / 经验下发评论前确认对象 | `SubjectGateway.resolveForInteraction(type, key, DISCUSS)`，在事务外调用（文献首次登记要调 catalog） |
| 在期刊下写经验前确认期刊 | `resolveForInteraction(JOURNAL, 期刊 ID, SUBMISSION_EXPERIENCE)` |
| 登记经验 / 删除经验 / 编辑经验 | `registerLocal(POST, 帖子 ID, SubjectOwner.of(作者, 匿名), 快照)`；`updateLocal(POST, 帖子 ID, UNAVAILABLE, 当前快照)`；`updateLocal(POST, 帖子 ID, ACTIVE, 新快照)`。都必须在经验写入的同一事务里 |
| 判断「作者」标记、匿名遮蔽、「评论了你的经验」 | `SubjectReadPort.findByIds(...)` → `SubjectView.owner()`（外部对象为 null） |
| 用户名片、可见性 | `UserDirectoryPort.findCards(ids, viewer)`（`domain.subject.port.user`）；不可用时抛 `UserDirectoryUnavailableException`，需要降级时可以捕获 |
| 发表资格与频率 | `PublishGuardGateway.requireEligible(作者)`（事务外，返回自己的名片）+ `admit(作者, COMMENT / POST)`（事务内、第一个写操作之前）；异常直接传播 |
| 「对象已删除」（`composer.error.object-gone`） | `resolveForInteraction` 对已删除的经验抛 `SubjectNotFoundException`，`traits[]` 带 `SUBJECT_UNAVAILABLE`；直接传播即可 |
| 注销后删除个人数据 | 实现 `UserDataCleanupGateway`，命名 `{子域}UserDataCleanupGatewayImpl`，放各子域 app 层（PAP-123：提醒） |
| 讨论发布、提醒订阅 | 事件 record 放 `domain.discussion.event`，字段只用 JDK 与底座公开类型；发布用 `ApplicationEventPublisher`，订阅用同步 `@EventListener` |
| 新增 schema 与迁移 | `db/migration/{子域}/V{时间戳}__…sql`；`InteractionMigrationIT` 的 schema 与版本断言已改成按规律判断，不用再改那几行 |
| 流程集成测试 | 继承 `InteractionFlowITSupport`（第 13.4 节），它每例清空全部子域的表；假 identity、假 catalog 在 `dev.patra.interaction.support` 包，按需扩展 |

## 13. 测试

### 13.1 单元测试

| 测试 | 位置 | 覆盖 |
|---|---|---|
| `SubjectTypeTest` | domain | 三种类型的编码、slug、来源属性、能力矩阵；`externalTypes()` 只有前两种；`parseKey` 对三种都一样 |
| `NaturalKeyTest` | domain | `nlm:0404401`、`nlm:2985213R`、`nlm:9300367A`、`nlm:9918523075306676` 往返；拒绝 `nlm:2985213r`、`nlm:R`、20 位数字；`post:1` 往返，拒绝 `post:0`、`post:01` |
| `SubjectSnapshotTest` | domain | 四参数 `of()`；`abbreviation` 可空 |
| `SubjectAggregateTest` | domain | 本地类型必须有所有者、外部类型不能有；本地对象对账抛错 |
| `SubjectExceptionsTest` | domain | 消息按类型给名词；`SubjectNotFoundException.notFound` 只带 `NOT_FOUND`，`unavailable` 另带 `SUBJECT_UNAVAILABLE`；`PublishIneligibleException` / `PublishRateLimitedException` 的特征、原因码、`Retry-After`；`UserDirectoryUnavailableException`（从 collection 挪来） |
| `PublishQuotaTest` | domain | 没有记录时放行；当天第 100 条放行、第 101 条拒绝；跨过北京时间 0 点重新计数（UTC 16:00 两侧）；间隔 9.999 秒拒绝、10 秒放行；经验没有间隔；先判上限再判间隔；两种 `retryAfter` 的值 |
| `SubjectSourcesTest` | app | 外部类型缺来源、重复来源都启动失败；给 `POST` 配来源也启动失败；`POST` 不需要来源 |
| `SubjectGatewayImplTest` | app | 原有分支，外加：本地类型未登记时直接 404 且不调来源；`registerLocal` / `updateLocal` 写对参数；对外部类型调用时抛 `IllegalArgumentException`；`updateLocal` 找不到行时抛 `IllegalStateException` |
| `ReconcileSubjectsHandlerTest` | app | 只遍历外部类型（`POST` 不查也不写） |
| `PublishGuardGatewayImplTest` | app | 资格表格的四行 + identity 不可用；`admit` 先拿锁再读写、拒绝时不写 |
| `CleanupDeletedUsersHandlerTest` | app（`app.subject…`） | 两个参与方的候选重叠和交错时合并正确；一批只调一次 identity；只删 DELETED，省略的不删；identity 不可用时跳过这一批，游标照常前进；每个（用户，参与方）一个事务；行数按参与方累计 |
| `CollectionUserDataCleanupGatewayImplTest`、`SubjectUserDataCleanupGatewayImplTest` | app | 委托与先拿锁的顺序 |
| `JournalSubjectSourceAdapterTest` | infra | 首次登记的锚取 `nlmId`；对账的锚取 `key`（`key ≠ nlmId` 的副 NLM 行）；`exists: false`、`nlmId` 为 null 都没有记录；快照取 `title` 与 `abbreviatedTitle`；三类失败的翻译 |
| `DeletedUserCleanupScheduleJobTest` | adapter（挪包） | 三种回写，新的成功文案 |
| `SubdomainArchitectureRulesTest` | `patra-tool-test` | 见 13.5 |

### 13.2 infra 集成测试

| 测试 | 覆盖 |
|---|---|
| `InteractionMigrationIT` | 新约束：类型 2 的 `nlm:` 格式（含前导零、`R` 后缀，拒绝小写 `r`）；类型 3 的 `natural_key` 必须等于 `'post:' || source_id`，`source_id` 为空被拒；所有者两列成对；本地类型必须有所有者，外部类型不能有；`publish_quota` 的种类与计数约束。`should_enforce_registry_checks` 里当作「未知编码」的 `2` 改成 `99`：2 现在是 `JOURNAL`，它被拒的原因变成了格式不对，断言还绿，意义却错了 |
| `SubjectRepositoryAdapterIT` | `insertLocal` 读回所有者；`updateLocal` 只改状态与快照、所有者不变、找不到行时返回 false；`upsertResolved`、`saveReconciled` 不碰所有者 |
| `SubjectReadAdapterIT` | `findByIds` 带出所有者；`findIdsByKeys(POST, …)` 可用；快照里没有 `abbreviation` 键的旧行读回为 null |
| `JournalSubjectSourceAdapterIT` | 真实 HTTP（照 `PublicationSubjectSourceAdapterIT` 手工建客户端）：路径与请求体；字符串形式的 `id` 读回 `Long`；`0404401` 原样；503 与连接拒绝都翻成不可用 |
| `PublishQuotaRepositoryAdapterIT` | 读写往返；两个线程同时 `admit` 一个只差一条到上限的作者，只有一个放行（锁有效；测试关闭测试事务，见坑清单「advisory lock IT 关事务」）；`findUsersAfter`、`deleteAllOf` |

`InteractionITBootstrap` 加一个不会被调用的 mock `VenueEndpoint`。

### 13.3 boot 集成测试（AC 对应）

| 测试 | AC | 覆盖 |
|---|---|---|
| `SubjectRegistryFlowIT`（加期刊用例） | 期刊登记与对账 | 按期刊 ID 首次登记，锚是 `nlm:0404401`；改桩模拟重导：按 NLM 解析返回新 `id`、`nlmId` 是副 NLM → 对账后锚不变、`source_id` 是新 ID、`subject_id` 不变；按新 ID 首次解析直接命中、不请求 catalog；软删、`exists: false`、`nlmId` 为 null 都拒绝且不建行；对账时 `exists: false` → UNAVAILABLE、`source_id` 清空 |
| `LocalSubjectFlowIT` | 本地对象不进对账，状态只由所属子域更新 | 在事务里登记一个 `POST`；跑对账（catalog 有桩）→ catalog 没收到任何带 `post:` 的请求，`POST` 行原样不变；`updateLocal` 置为 UNAVAILABLE → `resolveForInteraction(POST, …, DISCUSS)` 拒绝；`findByIds` 读到所有者与匿名标记；事务外调用 `registerLocal` 抛 `IllegalTransactionStateException` 且不写库 |
| `PublishGuardFlowIT` | 发表守卫的四种拒绝 | 假 identity：未验证邮箱、`BANNED` / `PENDING_DELETION` / `DELETED`、省略各自拒绝；连发两条评论，第二条 `TOO_FREQUENT`；当天第 101 条评论、第 11 条经验 `DAILY_LIMIT_REACHED`（预先写好计数行）；四种异常交给容器里的 `ErrorResolutionEngine`，得到 `INTERACTION-0409` / `INTERACTION-0429`，`traits` 里带各自的原因码 |
| `DeletedUserCleanupFlowIT` | 公共流程 | 用户 A 有收藏和发表计数，用户 B 只有发表计数，A、B 都是 DELETED，C 是 ACTIVE → 跑命令后 A、B 的数据全删，C 不动；identity 摘要接口只被调用一次 |
| `CollectionFlowIT` | 注销清理迁到公共流程后不改断言通过 | 只改清理命令的 import |

### 13.4 为并行的 PAP-121 / 122 / 123 预先调整的测试底座

| 位置 | 现在 | 改成 | 为什么 |
|---|---|---|---|
| `InteractionMigrationIT.should_create_one_schema_per_subdomain` | `containsExactlyInAnyOrder("public", "subject", "collection")` | 每个非系统 schema 都是 `public` 或子域清单里的名字；`public`、`subject`、`collection` 必须在 | 三个子域各自建 schema 时不用再改这一行 |
| `InteractionMigrationIT.should_record_migrations_with_timestamp_versions` | 写死四个版本号 | 全部成功；版本号都是 14 位时间戳；按安装顺序严格递增；包含现有六个 | 同上，每加一个脚本不用改 |
| `FakeCatalog`、`FakeIdentity` | `collection` 包里的包私有类 | 挪到 `dev.patra.interaction.support`，改为 `public` | 三个子域的流程测试都要用 |
| 流程测试底座 | `CollectionFlowITSupport` 自带 WireMock 与清表 | 抽出 `InteractionFlowITSupport`（`dev.patra.interaction.support`）：真实 PG、两个 WireMock、`lb://` 发现、`RestTestClient`、`call` / `json` / `count`；每例前清空 `pg_tables` 里全部非 `public` 的表（新子域的表自动纳入）。`CollectionFlowITSupport` 继承它，只留收藏相关的辅助方法 | 不用逐个子域改清表列表；所有流程测试共用同一个 Spring 上下文 |

### 13.5 规则的测试（`patra-tool-test`）

新增夹具场景，沿用 `subject`、`collection`、`note` 三个子域：

| 场景 | 夹具 | 期望 |
|---|---|---|
| 订阅事件 | `note` 的 app 类用 `domain.collection.event.ItemCollectedEvent` | 声明 `allowingSubscription("note", "collection")` 后，规则 25 与新规则都通过；不声明时新规则违规 |
| 订阅边越界 | `note` 用 `domain.collection.port.read.CollectionReadPort`，只声明了订阅 | 新规则违规，点名 `CollectionReadPort` |
| 依赖边 | `note` 用 collection 的读端口 | 声明 `allowingDependency` 后通过；不声明时违规 |
| 底座的新公开包 | `collection` 用 `domain.subject.port.user.*`、`model.user.*`、`exception.*` | 规则 25 通过 |

外加：两个声明方法的参数校验（未知名字、自环、`from` 为底座、`to` 为底座都抛错）；`allRules()` 是五条；合规场景不声明任何边也通过新规则。

### 13.6 门禁

`./gradlew spotlessApply`；`./gradlew check`；单独跑 `:patra-api:patra-interaction:patra-interaction-infra:integrationTest` 和 `:patra-api:patra-interaction:patra-interaction-boot:integrationTest`（`check` 不含集成测试）；`:patra-tool:patra-tool-test:check`；`bash patra-infra/cd/detect-changes.test.sh`。`./gradlew dumpModuleGraph` 后看 `module-graph.json` 有没有变化：没有新增模块间依赖，预计不变，有变化就一起提交。

## 14. 上线与文档

### 14.1 runbook `docs/patra/runbooks/v0.14-discussion-go-live-runbook.md`

结构照 v0.13：

1. 环境与约定、资源基线（D11）。
2. **合并前：catalog `nlm_id` 只读核对**（PAP-117 第 11.1 节的 Q1–Q4，期望全为 0）；不为 0 时按其第 11.2 节交给用户逐条决定；**核对到合并之间不要触发 `venuePubmedImportJob`（XXL-Job 任务 11）**。
3. 合并、CD 结束后核对部署：catalog V12 的约束与索引、经网关 403、mini 本机按主副 NLM 解析（PAP-117 第 11.3 节）；identity `V2__add_mention_prefix_indexes` 已执行、两个前缀索引都在；interaction 的两条新迁移已执行、`subject.registry` 有新约束、`subject.publish_quota` 已建。
4. 手动触发一次 `subjectReconciliationJob`：日志里有 `type=PUBLICATION` 和 `type=JOURNAL` 两行汇总，`failedBatches=0`；手动触发一次 `deletedUserCleanupJob`：新的回写文案，无报错。
5. 占位：PAP-121 / 122 / 123 的三个 schema 迁移核对；PAP-123 的提醒清理任务登记，等它们完成后补。（PAP-119 已于 2026-10-27 撤出本版，runbook 不含它的步骤。）
6. 回滚：catalog V12 回滚期间不要触发任务 11（PAP-117 第 11.4 节）；interaction 两条迁移是加列、加约束、加表，旧镜像照常工作，不需要撤销。
7. 通用规则：catalog 换库重建或重跑导入后，立即手动触发 `subjectReconciliationJob`，现在它也保护期刊（第 5.4 节）。

### 14.2 README（`patra-api/patra-interaction/README.md`）

- 第 1 节子域表：加 `discussion`、`post`、`notification` 三行（职责一句话，本版由 PAP-121 / 122 / 123 实现）；`subject` 一行补期刊、本地对象、发表守卫、注销清理流程。
- 第 3 节公开包表：按第 11.1 节更新，补「跨子域依赖必须声明」和事件契约的写法。
- 第 1 节末尾「新增子域时同时改两处」改成第 11.4 节的说法。
- 第 4 节：登记表的所有者两列、`publish_quota`。
- 第 5 节定时任务：注销清理改为底座的公共流程。

### 14.3 交接

共享笔记 `interaction-PAP-120-handoff.md`：第 12 节的表格加上调用示例。Linear PAP-120 开工时已推到「BE」；用户同意提交后在描述里勾选 To-Do 和 AC，不手动改 Done。

## 15. 按设计简报定下的两处歧义

1. **提醒上下文里的投稿经验信息从哪来：扩快照，不加列。** 提醒不能依赖投稿经验子域（决策 A、B），只能从登记表拿。设计简报里有两种说法：附录 A「PAP-119 / PAP-117」一条笼统地说「提醒的上下文要期刊卡片」；但提醒列表页的权威版式 §3.19 把上下文行定为一行文字：`你的投稿经验 · <期刊缩写> · 2026 年 3 月投稿`、`<期刊缩写> 的一条投稿经验`、`一条已删除的投稿经验`，没有卡片。附录 A「PAP-123」第 2 条也只要「期刊缩写 + 投稿月份 + 是否本人的 + 是否已删除」。详细版式比笼统概括更准，所以只需要这几个字符串：快照加一个可空的 `abbreviation`；期刊登记行存 MEDLINE 缩写；`POST` 的快照由 PAP-122 在登记和编辑时写入，PAP-123 直接读。不加「挂在哪个对象下」这一列：它要多一条迁移和一组约束，`registerLocal` 也要多一个参数，换来的期刊卡片在提醒列表里用不上。
2. **「对象已删除」的原因码由底座给。** 附录 A 第 8 条要求评论写接口把「对象已删除」单列。只有底座知道一个对象是「已登记但失效」还是「从未存在」：如果底座不分，PAP-121 就得在每次写入前多查一次读端口。所以底座在这两种情况下给 `SubjectNotFoundException` 带上 `SUBJECT_UNAVAILABLE`：已登记但 UNAVAILABLE；首次解析时来源返回软删（第 5.2、6.3 节）。文献的 404 也会带上这个码，collection 的门户不认识它，照样只看状态码。

## 16. 由我定的点

1. 软删期刊不能首次登记（404，与文献一致）；对账时 `exists: false` 按文献处理（UNAVAILABLE、清空 `source_id`、保留快照）。
2. `SubjectType` 加 `SubjectOrigin` 与 `displayName`，两个异常的消息按类型给名词。
3. 本地对象的 `source_id` 就是所属子域的 ID；`POST` 的锚必须等于 `post:{source_id}`。
4. `registerLocal` / `updateLocal` 用 `@Transactional(MANDATORY)` 强制和调用方同事务；重复登记按程序错误处理，不做幂等。
5. 所有者两列靠「仓储里没有改它们的语句」保证不变，不加触发器。
6. 名片放 `port.user` / `model.user` 并公开；底座的 `exception` 包公开（推翻 PAP-112 第 11 节第 8 条）。
7. 发表计数是底座自己的一张表，每个作者每种一行；「每天」是北京时间的自然日，写成常量；删除不退还额度；先判每日上限、再判间隔。
8. 资格不符返回 409（与 collection 设公开同名同码），限流返回 429 并带 `Retry-After`；底座的原因码只用一个枚举 `SubjectErrorTrait`。
9. 注销清理的扩展点叫 `UserDataCleanupGateway`，各子域在 app 层用 `*GatewayImpl` 实现；底座自己的发表计数也是一个参与方；每个（用户，参与方）一个事务。
10. 任务挪进底座，handler 名、Cron 不变。
11. ArchUnit：每个子域的公开包都加 `event`；新增「跨子域依赖必须声明」规则与两个声明方法；22b 改成自推导，不建空包。
12. 事件用 Spring 的 `ApplicationEventPublisher` + 同步 `@EventListener`，不用 `@TransactionalEventListener`。
13. 为并行开发预先调整：`InteractionMigrationIT` 的两条列表断言改成按规律判断；抽出 `InteractionFlowITSupport`；`FakeCatalog`、`FakeIdentity` 改为公开的共用类。
14. 两个来源策略的失败翻译抽成包内的 `CatalogSourceCalls`。

## 17. 已知限制

1. 合并期刊在重导窗口里被首次登记，会多出一行，原来的行一直「已失效」（第 5.4 节）；靠 runbook 的「重导后立即对账」规避。
2. 发表计数按自然日计，北京时间 0 点重置，所以 23:59 与 00:00 之间可以连着发两个上限的量（每日上限的本义如此）。
3. 本地对象的所有者两列不可改，账号注销后照样保留用户 ID（决策 H：内容保留，identity 返回「已注销用户」的占位）。
4. `PublishGuardFlowIT` 预写计数时用真实时钟算「今天」：如果测试恰好在北京时间 0 点前后几毫秒内运行，预写的那行会变成「昨天」，每日上限的用例偶发失败。重跑即可；流程测试共用一个 Spring 上下文，换成固定时钟会影响收藏的时间排序用例，所以不改。

## 18. 实施记录（2026-10-27）

计划 `docs/patra/plans/2026-10-27-interaction-foundation-extension.md`，本会话内联 TDD 执行（22 个任务）。按编排约定全程未提交。

### 18.1 与本设计的偏离

| # | 偏离 | 原因 |
|---|---|---|
| 1 | 「同一作者卡在上限边上并发发表只放行一个」放在 boot 的 `PublishGuardFlowIT`，infra 的 `PublishQuotaRepositoryAdapterIT` 只证明作者锁会阻塞（第 13.2 节写的是 infra） | 放行规则在 app 层的网关里，infra 的测试上下文里没有它；boot 里用真实网关、两个线程各开一个事务，测的才是完整路径 |
| 2 | `SubjectSources.of(本地类型)` 抛 `IllegalArgumentException` | 本地类型没有来源策略；`resolveForInteraction` 先判本地类型，不会走到这里。初版返回 `null`，收尾评审后改为直接抛异常，免得以后的调用方忘了判空 |
| 3 | `InteractionFlowITSupport` 照搬原 `CollectionFlowITSupport` 的 WireMock 用法，没有额外 `resetAll` | WireMock 的 JUnit 扩展本来就在每例前重置，加了反而和重构前的行为不一致 |

### 18.2 收尾评审后的修复

评审（opus，全支）结论 With fixes，没有 Critical。两处 Important 与能顺手修的 Minor 都按 TDD 修了（先写失败的测试）：

| 发现 | 修复 | 测试 |
|---|---|---|
| `ck_registry_post_key` 拦不住 `source_id` 为空的失效 POST 行（第 10.1 节初稿的推理不成立） | 约束显式加 `source_id IS NOT NULL`；迁移还没上 mini，直接改原脚本；顺带更新表与 `snapshot` 列的注释 | `InteractionMigrationIT.should_reject_post_without_source_id_even_when_unavailable` |
| 本地对象接口不查空：快照传 null 会写成 JSON null，之后批量读整页失败 | `registerLocal` / `updateLocal` 对快照、状态、所有者 `requireNonNull` | `SubjectGatewayImplTest.should_reject_null_arguments_for_local_methods` |
| 经验没有间隔却仍判间隔：时钟回拨时会报「请隔 0 秒再发」 | 间隔为零的种类不判间隔 | `PublishQuotaTest.should_not_check_interval_when_kind_has_none` |
| `SubjectSources.of(本地类型)` 返回 null | 改为抛异常 | `SubjectSourcesTest.should_not_require_port_for_local_type` |
| 测试缺口：带后缀与 16 位的 NLM 没走过写库再读回；事务外 `updateLocal` 只在空表上测过；「先判账号状态再判邮箱」没被锁住 | 补测试（行为本来就对） | `SubjectRepositoryAdapterIT.should_round_trip_nlm_anchors_as_written`、`LocalSubjectFlowIT.should_leave_existing_row_untouched_when_updated_outside_transaction`、`PublishGuardGatewayImplTest.should_report_account_restriction_before_unverified_email` |
| 文字：`FakeIdentity` 注释被格式化折断、`AccountState` 与 README 的旧说法、runbook 的回滚前提过严（旧代码读不到期刊与经验行）、四个空目录 | 改掉 | — |

没修的一条写进第 17 节第 4 条（测试在北京时间 0 点前后偶发失败）。

### 18.3 验证

- 每个任务先看测试失败、再实现、再看通过；另外对五处关键行为做了变异检查（改坏实现，确认测试会红，再改回）：事件契约包与底座新公开包两条规则、期刊对账按请求 key 对回（AC）、`registerLocal` 的 `MANDATORY`、发表守卫的作者锁。
- `InteractionArchitectureTest`：临时放入违规代码，规则 25（collection 用底座仓储）、规则 27（notification 用 collection 读端口）、规则 22b（notification 只有一层）都报红，删掉后恢复。
- 门禁：`./gradlew spotlessApply`；`./gradlew check` BUILD SUCCESSFUL；`:patra-interaction-infra:integrationTest`、`:patra-interaction-boot:integrationTest --rerun` 全部通过（99 例）；`patra-infra/cd/detect-changes.test.sh` 通过 66 / 失败 0；`./gradlew dumpModuleGraph` 后 `module-graph.json` 无变化（没有新增模块间依赖）。
