# 对象登记表与能力矩阵工程设计（PAP-112）

> 状态：待评审（用户本地让 Codex 评审后再写代码）
> 日期：2026-10-24
> Issue：PAP-112（GitHub #282），Linear Project P-PAP-10 v0.13 Library，Milestone BE Ready
> 依据：release spec `docs/patra/release-specs/v0.13-library.md` 决策 A、B、C、G，Done 判定 D2、D3、D7
> 前序：PAP-111 骨架（`docs/patra/specs/2026-10-24-interaction-skeleton-design.md`，子域边界见第 5 节）、PAP-109 catalog 解析接口（`patra-catalog-api` 的 `PublicationEndpoint`）、PAP-110 计划一（解析响应新增带精度的 `publishedDate`；已于 2026-10-24 快进到含它的 `1342c0b7c`）
> 后续：PAP-113（收藏与收藏夹）只经本文第 6 节的两个公开端口使用 subject

## 1. 要解决的问题

互动数据（本版是收藏）要指向一个外部对象（本版只有文献）。catalog 的文献内部 ID 在换库重建时会整表重发，dev 上也常清库重导，所以互动表不能存它（决策 C）。本 Issue 建 `subject` 子域：

1. 每个被互动过的对象在登记表里有一行，持有本地 `subject_id`；互动表只存 `subject_id`。
2. 对象的身份锚定在外部标识上：文献是 PMID，写成 `natural_key = pmid:<PMID>`。catalog 内部 ID 只是可以重新解析的缓存 `source_id`。
3. 快照存标题、期刊全称、带精度的出版日期：用于兜底和「已失效」展示，也是 collection 按出版日期 / 期刊排序的数据来源（用户 2026-10-24 选定，release spec 决策 C 已同步）。collection 把排序键抄进自己的表还是在内存里排，由 PAP-113 定。
4. 对象第一次被互动时，同步调 catalog 解析并入表，之后只查本地；catalog 不可用或对象不存在时拒绝。
5. 每天对账一次：按 PMID 回查 catalog，刷新 `source_id`、`state`、`snapshot`。catalog 怎么重导，都只改登记表，互动行不动。
6. 对象能做哪些互动写在代码枚举 `SubjectType` 的能力集里，本版只开「收藏文献」。

## 2. 范围

**做**：

- domain：`SubjectType`、`SubjectCapability`、`SubjectId`、`NaturalKey`、`SubjectSnapshot`、`SubjectState` 等值；`SubjectAggregate` 与对账规则；三个端口（仓储、来源端口、读端口）与驱动端口 `SubjectGateway`；四个领域异常。
- app：`SubjectGatewayImpl`（首次互动解析）、`SubjectSources`（按类型选来源策略）、对账命令与处理器。
- infra：迁移脚本 `subject.registry`；`SubjectRepositoryAdapter`、`SubjectReadAdapter`（原生 SQL）；`PublicationSubjectSourceAdapter`（调 catalog）。
- adapter：XXL-Job `subjectReconciliationJob`。
- boot：catalog 的 HTTP Interface 客户端、`application.yml` 的分组超时。
- 测试：各层单元测试；PG 与 WireMock 集成测试；boot 的端到端集成测试覆盖全部 AC（第 9 节）。
- 文档：服务 README（表命名、`set_updated_at()` 约定）、上线 runbook 第 6 步、模块图、共享契约笔记 `PAP-112-subject-contract.md`。

**不做**：

- 任何 REST 接口。`(type, key)` 是 `SubjectGateway` 的签名，第一个对外接口（收藏）随 PAP-113 来；本 Issue 的 AC 在网关端口这一层用集成测试验证（第 9 节）。
- 进程内缓存（用户 2026-10-24 确认）：登记表本身就是 `(type, key) → subject_id` 的本地缓存，按部分唯一索引查本地 PG。
- 登记行的回收：用户取消全部互动后登记行照留，本版不清理没人引用的行（行数 ≈ 被收藏过的不同文献数）。
- 批量首次解析：PAP-113 的首次互动都是单篇（复制收藏夹时条目已经登记过）。

## 3. 已定的前提

| 来源 | 内容 |
|---|---|
| 决策 C | 登记表字段 `subject_id`、`natural_key`、`source_id`、`state`（ACTIVE / UNAVAILABLE）、`snapshot`；首次互动时同步解析；catalog 不可用时拒绝，不先收下再补校验；不存在时拒绝且不建行；对外用 `(type, key)`，文献的 key 是门户 URL 里的 catalog 内部 ID；能力矩阵在代码枚举里 |
| 决策 B | schema `subject`；迁移版本号 `yyyyMMddHHmmss`；子域表不对 `subject` 建外键 |
| 决策 G | 每日对账任务 `subjectReconciliationJob`，`0 30 6 * * ?`（PAP-111 第 11.2 节已定名与时间） |
| PAP-111 第 5.2 节 | subject 对其他子域只公开 `domain.subject.port.read`、`model.read`、`port.gateway`、`model.vo`、`model.enums`；subject 不依赖其他子域 |
| PAP-111 第 6.4 节 | `set_updated_at()` 放哪、表命名由本 Issue 定 |
| 层规则 | `@Transactional` 只能在 app；`domain..port..` 下的类型名必须以 Port / Repository / Gateway 结尾；`*GatewayImpl` 在 app；端口实现（Gateway 除外）在 infra |
| 用户 2026-10-24 答复（排序） | 快照存标题、期刊全称、带精度的出版日期，经读端口给 collection；排序机制留给 PAP-113 |
| PAP-110 计划一 | `PublicationResolutionResp` 末尾新增 `String publishedDate`：`2019` / `2019-03` / `2019-03-15`，不补原文没有的月日；未知时为 null |
| 用户 2026-10-24 答复 | ① 首次互动遇到 catalog 软删（`state = DELETED`）按不存在拒绝，不建行；② 对账时 PMID 在 catalog 里整行消失：置 UNAVAILABLE、清空 `source_id`、保留快照；③ 不加进程内缓存；④ catalog 分组超时照 identity（连接 1s、读取 2s），对账共用；某批失败就跳过并计数，跑完有失败就让任务失败，次日自动重跑，任务内不重试 |
| catalog 契约（PAP-109） | `POST /_internal/publications/resolve/by-ids`、`/by-pmids`，一次 1 ~ 100 个；每个去重后的键一行；查不到 `exists = false`；软删 `state = DELETED` 且展示字段照样有值；`pmid` 可能为 null；格式错误 422、坏 JSON 400，不用 404 |

## 4. 领域模型（`dev.patra.interaction.domain.subject`）

### 4.1 能力矩阵（`model.enums`，公开）

```java
public enum SubjectCapability { COLLECT }

public enum SubjectType {
  PUBLICATION((short) 1, "publication", NaturalKeyScheme.PMID, EnumSet.of(SubjectCapability.COLLECT));
  // code：落库的 smallint，永不复用、永不改；slug：(type, key) 里的类型名（PAP-113 的 URL 用它）
}
```

| 成员 | 说明 |
|---|---|
| `code()` / `fromCode(short)` | 稳定编码，落库用；未知编码抛 `IllegalArgumentException`（库里出现未知编码是程序错误） |
| `slug()` / `fromSlug(String)` | 未知 slug 返回 `Optional.empty()`，由调用方翻译成 404（PAP-113 的接口） |
| `supports(SubjectCapability)` | 能力判断 |
| `parseKey(String)` | 把对外的 key 解析成来源 ID：文献是十进制正整数（`long`，1 ~ `Long.MAX_VALUE`，不接受前导 `+`、前导零、空白）；不合法抛 `InvalidSubjectKeyException` |
| `naturalKeyScheme()` | 文献是 `PMID`：`NaturalKey` 的前缀 `pmid`，值 `^[1-9][0-9]{0,14}$`（与 catalog 的 PMID 校验一致） |

`NaturalKeyScheme` 也放 `model.enums`。新增一种对象 = 加一个枚举常量 + 一个来源策略（4.4）+ 一条迁移改 CHECK 约束；`SubjectSources` 的启动校验（5.1）保证不会漏掉策略。

### 4.2 值对象（`model.vo`，公开）

| 类型 | 内容 |
|---|---|
| `SubjectId(long value)` | 本地 Snowflake ID；`of(long)` |
| `NaturalKey(NaturalKeyScheme scheme, String value)` | `of(scheme, value)` 校验格式；`toString()` / `parse(String)` 对应落库格式 `pmid:12345678` |
| `SubjectSnapshot(String title, String journal, String publishedDate)` | 三项都可空。`title` 是 catalog 原文（含内联标记）；`journal` 是期刊全称；`publishedDate` 是 ISO 8601 降精度字符串，紧凑构造器校验 `^[0-9]{4}(-[0-9]{2}(-[0-9]{2})?)?$`（月 01–12、日 01–31，不做历法校验，以 catalog 为准），不合法抛 `IllegalArgumentException`（来源给了坏数据是程序错误）。用途：兜底与「已失效」展示，以及 collection 的排序键。降精度字符串按字典序倒排，正好是 catalog 的检索排序规则（同一年里只到年的排在有月份的之后） |
| `SubjectState` | `ACTIVE`、`UNAVAILABLE`（放 `model.enums`） |

### 4.3 聚合与对账规则（`model.aggregate`，不公开）

`SubjectAggregate`：`subjectId`、`type`、`naturalKey`、`sourceId`（`Long`，可空）、`state`、`snapshot`、`snapshotAt`。不变量：`sourceId == null` 时 `state` 必须是 `UNAVAILABLE`（库里同一条 CHECK）。

对账是纯函数 `SubjectAggregate reconcile(Optional<SourceRecord> found, Instant now)`：

| catalog 的结果 | `source_id` | `state` | `snapshot` | `snapshot_at` |
|---|---|---|---|---|
| 存在，ACTIVE | 用 catalog 返回的 ID（可能变了） | ACTIVE（UNAVAILABLE 时即复活） | 用新值 | `now` |
| 存在，DELETED（软删） | 用 catalog 返回的 ID | UNAVAILABLE | 用新值（catalog 软删照样给展示字段） | `now` |
| 不存在（整行消失） | 置空 | UNAVAILABLE | 保留旧值 | 不变 |

`SourceRecord`（`model.source`，不公开）：`long sourceId`、`NaturalKey naturalKey`、`SourceState state`（`ACTIVE` / `DELETED`）、`SubjectSnapshot snapshot`。

### 4.4 端口

| 端口 | 包 | 实现 | 方法 |
|---|---|---|---|
| `SubjectRepository` | `port.repository` | infra `SubjectRepositoryAdapter` | `Optional<SubjectAggregate> findBySourceId(SubjectType, long)`；`SubjectAggregate upsertResolved(SubjectType, SourceRecord, Instant now)`；`List<SubjectAggregate> findPage(SubjectType, long afterSubjectId, int limit)`（按 `subject_id` 升序）；`void saveReconciled(List<SubjectAggregate>)` |
| `SubjectSourcePort` | `port.source` | infra `PublicationSubjectSourceAdapter`（每种类型一个，即「按类型策略」） | `SubjectType type()`；`Optional<SourceRecord> resolveByKey(long sourceId)`；`Map<NaturalKey, SourceRecord> resolveByNaturalKeys(Collection<NaturalKey>)`（1 ~ 100 个；返回里没有的键 = 不存在） |
| `SubjectReadPort` | `port.read`（公开） | infra `SubjectReadAdapter` | 第 6.2 节 |
| `SubjectGateway` | `port.gateway`（公开） | app `SubjectGatewayImpl` | 第 6.1 节 |

`resolveByKey` 只返回「存在且有 PMID」的结果：`exists = false` → 空；`pmid = null` → 空并打 WARN（没有锚就不能登记）；软删照样返回，由调用方判断。`resolveByNaturalKeys` 同理，`pmid` 为 null 的行不会出现（按 PMID 查的结果必然有 PMID）。

### 4.5 领域异常（`domain.subject.exception`，不公开）

| 异常 | 特征 | HTTP | 消息 |
|---|---|---|---|
| `SubjectSourceUnavailableException` | `DEP_UNAVAILABLE` | 503 | 文献信息暂时无法获取，请稍后再试 |
| `SubjectNotFoundException` | `NOT_FOUND` | 404 | 文献不存在或已被删除 |
| `InvalidSubjectKeyException` | `RULE_VIOLATION` | 422 | 对象标识格式不正确 |
| `SubjectCapabilityNotSupportedException` | `RULE_VIOLATION` | 422 | 该对象不支持此操作 |

四个都继承 `DomainException`，由 `DefaultErrorResolutionEngine` 按特征映射，错误码形如 `INTERACTION-0503`。异常包不在公开包里：PAP-113 不 import、不捕获它们，让它们直接传播到 web 层（第 10 节契约笔记写明）。消息里写「文献」：本版只有文献这一种对象，多一种类型时按类型给消息。

## 5. 应用层（`dev.patra.interaction.app.subject`）

### 5.1 `SubjectSources`（`app.subject.service`）

把注入的 `List<SubjectSourcePort>` 按 `type()` 建成 `EnumMap`。构造时校验：每个 `SubjectType` 恰好一个策略，缺失或重复都抛 `IllegalStateException`，让应用启动失败。

### 5.2 首次互动解析：`SubjectGatewayImpl`（`app.subject.service`）

`SubjectId resolveForInteraction(SubjectType type, String key, SubjectCapability capability)`：

1. `type.supports(capability)` 为假 → `SubjectCapabilityNotSupportedException`。
2. `long sourceId = type.parseKey(key)`（不合法 → `InvalidSubjectKeyException`）。
3. **本地查**：`repository.findBySourceId(type, sourceId)`。
   - 命中且 ACTIVE → 返回 `subjectId`，**不调 catalog**。这一步保证 catalog 不可用时已登记对象不受影响。
   - 命中且 UNAVAILABLE（catalog 软删、尚未复活）→ `SubjectNotFoundException`。不同步回查 catalog：复活由每日对账完成。
4. **未命中才调 catalog**：`sources.of(type).resolveByKey(sourceId)`（事务外调用，不占连接）。
   - 抛 `SubjectSourceUnavailableException` → 原样传播（503），不写库。
   - 空（不存在、没有 PMID）→ `SubjectNotFoundException`，不写库。
   - `state = DELETED` → `SubjectNotFoundException`，不写库（用户答复 ①）。
5. **入表**：在事务里 `repository.upsertResolved(type, record, clock.instant())`，返回它的 `subjectId`。按 `natural_key` upsert，所以下面两种情况都落到同一行：
   - 新对象：新建一行（Snowflake ID）。
   - 换库后的老对象：同一 PMID 已有行，但 `source_id` 是旧 ID（或被对账清空），刷新它的 `source_id`、`state = ACTIVE`、`snapshot`，`subject_id` 不变。

事务用注入的 `TransactionOperations`（identity 的 `RequestPasswordResetHandler` 有先例），不在类上加 `@Transactional`：第 4 步的远程调用不能在事务里。

并发：两个人同时首次收藏同一篇，都走到第 5 步，`ON CONFLICT (target_type, natural_key)` 让两者拿到同一个 `subject_id`（第 7.3 节）。

### 5.3 对账：`ReconcileSubjectsCommand` / `ReconcileSubjectsHandler`（`app.subject.usecase.reconcile`）

走 `CommandBus`，同 identity 的定时任务写法。对每个 `SubjectType`：

1. 按 `subject_id` 升序分页，每页 100 行（`findPage(type, afterId, 100)`），直到取到空页。
2. 每页把 `natural_key` 交给 `resolveByNaturalKeys`（事务外）。
   - 抛 `SubjectSourceUnavailableException`：本页计入 `failedBatches`，记 WARN，**跳过本页继续下一页**（用户答复 ④）。
3. 每行套 `reconcile(found, now)`，在事务里 `saveReconciled(本页)`。

返回 `ReconciliationResult(scanned, sourceIdChanged, becameUnavailable, revived, failedBatches)`。结束打一行 INFO 汇总。整次运行不加锁：调度中心「单机串行」已经保证同一时间只有一个实例在跑。

与首次解析并发：对账写一行的同时有人首次解析同一篇，两边写的都是 catalog 的当前事实，后写者覆盖，无害。

### 5.4 重导与对账之间的空窗

catalog 换库重导后、对账跑之前，登记表里的 `source_id` 还是旧 ID：`findByIds` 给出的 key 取不到卡片，`findIdsByKeys` 按新 ID 查不到。这个窗口不改代码处理，靠 runbook「重导后立即手动对账」压到几分钟。窗口内用户对某篇重新点收藏不会出错：新 ID 本地未命中 → 向 catalog 解析 → 同一 PMID 命中原行并刷新 `source_id` → 返回原 `subject_id`（收藏表的唯一约束让重复加入是幂等的，PAP-113 负责）。

## 6. 公开端口（给 PAP-113）

### 6.1 `SubjectGateway`（`domain.subject.port.gateway`）

```java
public interface SubjectGateway {
  /// 首次互动前调用：已登记就返回，未登记就向来源解析并登记。
  SubjectId resolveForInteraction(SubjectType type, String key, SubjectCapability capability);
}
```

只用于**新增**互动（加入文献库）。对已有互动（改备注、取消收藏、移入收藏夹）PAP-113 直接用自己表里的 `subject_id`，不经它，所以对象失效后用户仍能管理已有条目。

### 6.2 `SubjectReadPort`（`domain.subject.port.read`）与读模型（`domain.subject.model.read`）

```java
public interface SubjectReadPort {
  /// 按对外 key 查已登记对象，只查本地，不解析、不登记。未登记或 key 不合法的不在结果里。
  Map<String, SubjectId> findIdsByKeys(SubjectType type, Collection<String> keys);   // ≤ 100

  /// 按 subject_id 取展示所需的信息。查不到的 ID 不在结果里。
  Map<SubjectId, SubjectView> findByIds(Collection<SubjectId> ids);                   // ≤ 5000
}

public record SubjectView(SubjectId subjectId, SubjectType type, String key,
                          SubjectState state, SubjectSnapshot snapshot) {}
```

| 用途（PAP-113） | 用哪个 |
|---|---|
| 「我的状态」：一屏 20 篇文献是否收藏过 | `findIdsByKeys`，再按 `subject_id` 查收藏表；没登记过的必然没收藏 |
| 文献库列表、收藏夹内容、导出：从 `subject_id` 拿 catalog ID 去取卡片或导出 | `findByIds`；`key` 是 `source_id` 的十进制字符串，`state = UNAVAILABLE` 且 `key` 为空时只能用快照展示「已失效」 |
| 按出版日期 / 期刊排序 | `findByIds` 返回的 `snapshot.publishedDate`、`snapshot.journal`；每日对账刷新。PAP-113 若把它们抄进自己的表，抄本的刷新也归 PAP-113 |

上限：`findIdsByKeys` 与批量接口一致取 100；`findByIds` 取 5000（一次导出的上限）。超出抛 `IllegalArgumentException`（调用方的程序错误）。

## 7. 存储

### 7.1 迁移脚本

`patra-interaction-infra/src/main/resources/db/migration/subject/V20261024200000__create_subject_registry.sql`：

```sql
-- set_updated_at()：每个 schema 一份，schema 整体搬走时不依赖 public
CREATE OR REPLACE FUNCTION subject.set_updated_at() RETURNS TRIGGER AS $$
BEGIN
  IF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
    NEW.updated_at = now();
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE subject.registry
(
    subject_id   BIGINT          NOT NULL,
    target_type  SMALLINT        NOT NULL,
    natural_key  VARCHAR(64)     NOT NULL,
    source_id    BIGINT          NULL,
    state        VARCHAR(16)     NOT NULL,
    snapshot     jsonb           NOT NULL,
    snapshot_at  timestamptz(6)  NOT NULL,
    created_at   timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (subject_id),
    CONSTRAINT ck_registry_target_type CHECK (target_type IN (1)),
    CONSTRAINT ck_registry_state CHECK (state IN ('ACTIVE', 'UNAVAILABLE')),
    CONSTRAINT ck_registry_source_present CHECK (source_id IS NOT NULL OR state = 'UNAVAILABLE'),
    CONSTRAINT ck_registry_publication_key CHECK (target_type <> 1 OR natural_key ~ '^pmid:[1-9][0-9]{0,14}$')
);

CREATE UNIQUE INDEX uk_registry_natural_key ON subject.registry (target_type, natural_key);
CREATE UNIQUE INDEX uk_registry_source_id ON subject.registry (target_type, source_id) WHERE source_id IS NOT NULL;

CREATE TRIGGER trg_registry_updated_at
    BEFORE UPDATE ON subject.registry
    FOR EACH ROW EXECUTE FUNCTION subject.set_updated_at();
```

加上每列的 `COMMENT ON`（英文，同 identity）。

- 表名不带服务前缀，schema 已经说明归属；`registry` 即「对象登记表」。README 第 4 节补这条命名约定和「`set_updated_at()` 每个 schema 一份」。
- Issue 写的「两个部分唯一索引」落成：`natural_key` 全表唯一（它永远非空，没有可排除的行）；`source_id` 只对非空行唯一。
- 对账分页按主键 `subject_id` 走（单一类型时就是 PK 扫描），不另建索引。
- 不继承 `BaseJpaEntity`：它带 `created_by_name` 等用户名列，与 README「表里只存用户 ID，不存用户名」冲突；登记表也没有操作人。

### 7.2 持久化写法

全部原生 SQL（`EntityManager.createNativeQuery`，同 catalog 的 `PublicationFullTextRepositoryAdapter`），不建 JPA 实体：表只有一张、查询全是按索引的点查与批量更新，`snapshot` 以注入的 `JsonMapper` 序列化成 `jsonb`。

### 7.3 `upsertResolved` 与过期 `source_id`

同一事务里两条语句：

```sql
-- ① 别的行如果还占着这个 source_id（换库后 catalog 把同一个数字给了另一篇），它是过期缓存：清掉
UPDATE subject.registry SET source_id = NULL, state = 'UNAVAILABLE'
 WHERE target_type = :type AND source_id = :sourceId AND natural_key <> :naturalKey;

-- ② 按身份锚 upsert
INSERT INTO subject.registry (subject_id, target_type, natural_key, source_id, state, snapshot, snapshot_at)
VALUES (:newId, :type, :naturalKey, :sourceId, 'ACTIVE', CAST(:snapshot AS jsonb), :now)
ON CONFLICT (target_type, natural_key) DO UPDATE
   SET source_id = EXCLUDED.source_id, state = 'ACTIVE',
       snapshot = EXCLUDED.snapshot, snapshot_at = EXCLUDED.snapshot_at
RETURNING subject_id, target_type, natural_key, source_id, state, snapshot, snapshot_at;
```

被①清掉的那一行，下一次对账按它自己的 PMID 拿回正确的 ID（或确认已不存在）。catalog 的 ID 是 Snowflake，跨重导撞号几乎不会发生，但发生时这里不会因唯一索引报错。

`saveReconciled` 对每个 `source_id` 有变化的行先做同样的①，再 `UPDATE … WHERE subject_id = :id` 写入 `source_id`、`state`、`snapshot`、`snapshot_at`。一页 100 行在一个事务里。

## 8. infra 的 catalog 来源与 boot 的装配

### 8.1 `PublicationSubjectSourceAdapter`（`infra.subject.source`）

- `type()` 返回 `PUBLICATION`。
- `resolveByKey(id)` → `PublicationEndpoint.resolveByIds({ids: [id]})`；`resolveByNaturalKeys(keys)` → `resolveByPmids({pmids: […]})`，去掉 `pmid:` 前缀，按返回行的 `pmid` 对回 `NaturalKey`。
- 映射：`state` `ACTIVE` / `DELETED` → `SourceState`；`title`、`journal`、`publishedDate` → `SubjectSnapshot`（`year` 不再用，它可由 `publishedDate` 推出）。`publishedDate` 不合 4.2 的格式时按「无日期」写 null 并记 WARN（含 PMID），不让一行坏数据让整页对账或一次收藏失败。
- 失败翻译照 identity 的 `CatalogReferenceAdapter`：`RemoteCallException`、`ResourceAccessException`（连不上、超时）、`IllegalStateException`（注册中心里没有 catalog 实例）三类都翻成 `SubjectSourceUnavailableException`，记 WARN（只记状态码、错误码、异常类名）。catalog 的这两个接口不会回 404，所以没有「404 当不存在」的分支；422 / 400 是本服务的程序错误，同样当不可用处理并在日志里能看出来。
- `try` 只包住一次代理调用。

### 8.2 依赖

| 模块 | 新增 |
|---|---|
| domain | `api` 无新增（插件自带 patra-common、tool-core） |
| app | `api(project(":…:patra-interaction-domain"))`、`api(project(":patra-tool:patra-tool-cqrs"))`、`api("org.springframework:spring-tx")`（同 identity） |
| infra | `api(project(":…:patra-interaction-domain"))`、`implementation(project(":patra-api:patra-catalog:patra-catalog-api"))`、`implementation(project(":patra-tool:patra-tool-http-client"))`（同 identity-infra：带来 spring-web 的 `ResourceAccessException`，集成测试要用它的 `ProblemDetailErrorHandler`；它有自动配置登记，R5 放行） |
| adapter | `api(project(":…:patra-interaction-app"))`、`api(libs.xxl.job)` |
| boot | `implementation(project(":patra-tool:patra-tool-http-client"))`（`RestClientFactory`）、`implementation(project(":patra-api:patra-catalog:patra-catalog-api"))`（`CatalogClientConfiguration` 要 import `PublicationEndpoint`，infra 的 `implementation` 传不到 boot；同 identity-boot）；测试类路径上 domain、app 已由层间依赖带上，第 4.3 节的显式 `testImplementation` 去掉对应两行（api 仍留着） |

每个声明都有主代码 import 到（R5）。改依赖后重跑 `./gradlew dumpModuleGraph`：`patra-catalog-api` 的 `impacts` 会多出 `interaction`（catalog 契约变了要重部署 interaction），`detect-changes.test.sh` 里如有据此写死的期望一并更新。

### 8.3 boot

- `dev.patra.interaction.config.CatalogClientConfiguration`：照 identity，`factory.createRestClient(loadBalancedBuilder, "catalog", "lb://patra-catalog")` → `createProxy(PublicationEndpoint.class)`。
- `application.yml`：

  ```yaml
  patra:
    http-client:
      http-interface:
        groups:
          # 首次收藏时解析文献、每日对账（按主键 / PMID 批量查，毫秒级）：超时压短，catalog 卡住时很快 503
          catalog:
            base-url: lb://patra-catalog
            connect-timeout: 1s
            read-timeout: 2s
  ```

- `ApplicationConfigTest` 加一条：`catalog` 分组的 `base-url` 是 `lb://patra-catalog`。

### 8.4 XXL-Job（`adapter.subject.scheduler.SubjectReconciliationScheduleJob`）

handler `subjectReconciliationJob`，无参数。照 identity 的 `AccountDeletionScheduleJob`：`commandBus.handle(ReconcileSubjectsCommand.of())`；`failedBatches > 0` 时 `handleFail("已核对 N 个，失败 M 批")`，否则 `handleSuccess("已核对 N 个：换 ID a、失效 b、复活 c")`；`RuntimeException` 记 ERROR 并 `handleFail`。

调度中心设置（runbook 第 6 步）：Cron `0 30 6 * * ?`，路由「第一个」，阻塞「单机串行」，任务超时 600 秒，失败重试 0 次（次日自动重跑）。

## 9. 测试

### 9.1 单元测试

| 测试 | 位置 | 覆盖 |
|---|---|---|
| `SubjectTypeTest` | domain | 编码与 slug 稳定（`PUBLICATION` 是 1 / `publication`）；`fromCode` 未知抛错；`fromSlug` 未知为空；`supports`；`parseKey` 接受 `1`、`Long.MAX_VALUE`，拒绝 `0`、`-1`、`01`、`+1`、` 1`、`abc`、超出 long |
| `NaturalKeyTest` | domain | `pmid:123` 往返；拒绝 `pmid:0`、`pmid:01`、16 位、缺前缀、未知前缀 |
| `SubjectSnapshotTest` | domain | `publishedDate` 接受 `2019`、`2019-03`、`2019-03-15`、null，拒绝 `19`、`2019-3`、`2019-13`、`2019-03-32`、`2019/03` |
| `SubjectAggregateTest` | domain | 第 4.3 节表格的三行，外加 UNAVAILABLE → ACTIVE（复活）、ID 变化；不变量 |
| `SubjectSourcesTest` | app | 正常建表；某类型缺策略、重复策略都抛 `IllegalStateException` |
| `SubjectGatewayImplTest` | app | 第 5.2 节每一个分支：能力不支持、key 不合法、本地命中 ACTIVE（不调来源）、本地命中 UNAVAILABLE、来源不可用（不写库）、不存在（不写库）、软删（不写库）、正常入表 |
| `ReconcileSubjectsHandlerTest` | app | 分页到空页为止；一页失败计数并继续下一页；结果计数正确 |
| `PublicationSubjectSourceAdapterTest` | infra | 映射（含三种精度的 `publishedDate` 与 null）；`pmid` 为 null 视为不存在；三类失败的翻译 |
| `SubjectReconciliationScheduleJobTest` | adapter | 成功、有失败批次、运行异常三种回写（同 identity 的 Job 测试） |

### 9.2 集成测试

| 测试 | 位置 | 覆盖 |
|---|---|---|
| `SubjectRegistryMigrationIT`（或并入现有 `InteractionMigrationIT`） | infra | 表、两个唯一索引（含部分条件）、四条 CHECK、触发器存在；迁移版本号是时间戳 |
| `SubjectRepositoryAdapterIT` | infra，PG | 新建；同 PMID 再次 upsert 返回同一 `subject_id` 并刷新 `source_id`；过期 `source_id` 被另一篇抢到时旧行被清空且不报错；并发两个线程 upsert 同一 PMID 得到同一 `subject_id`、表里只有一行；`findPage` 按序分页；`saveReconciled` 写回；`updated_at` 由触发器推进 |
| `SubjectReadAdapterIT` | infra，PG | `findIdsByKeys` 只返回已登记且有 `source_id` 的、跳过不合法 key；`findByIds` 读回快照与状态；上限 |
| `PublicationSubjectSourceAdapterIT` | infra，WireMock | 真实 HTTP 下的请求体与路径；`id` 以字符串返回时读成 `long`；503、连接拒绝都翻成不可用（客户端照 identity 的 `CatalogReferenceAdapterIT` 手工建） |
| `SubjectRegistryFlowIT` | boot，PG + WireMock，经 `SimpleDiscoveryClient` 走 `lb://patra-catalog`（照 identity 的 `ProfileFlowIT`） | **全部 AC**，见下 |

`SubjectRegistryFlowIT` 的用例，逐条对应 Issue 的 AC：

1. **ID 变化后对账指向新 ID，互动数据不变**：catalog 桩让 ID 100 / PMID 1 存在 → `resolveForInteraction(PUBLICATION, "100", COLLECT)` 得到 S；改桩为 PMID 1 的 ID 变成 200（ID 100 不存在）→ 执行对账命令 → 登记表里 S 的 `source_id = 200`、`subject_id` 仍是 S；`findIdsByKeys(["200"])` 返回 S，`findIdsByKeys(["100"])` 为空；再用 `"200"` 首次解析直接返回 S 且没有请求 catalog。「互动数据不变」在本 Issue 的含义是 `subject_id` 不变：互动表只存它（PAP-113 的收藏在此之上无须任何改动）。
2. **软删变 UNAVAILABLE，复活变回 ACTIVE**：已登记的 S，桩改为 `DELETED` → 对账后 `state = UNAVAILABLE`、`source_id` 保留、快照更新；此时用该 key 首次解析抛 `SubjectNotFoundException`；桩改回 `ACTIVE` → 对账后 `state = ACTIVE`。
3. **catalog 不可用**：桩返回 503（另一例：停掉 WireMock，连接被拒）→ 未登记的 key 抛 `SubjectSourceUnavailableException`，其错误特征是 `DEP_UNAVAILABLE`，登记表无新行；已登记的 key 照常返回 `subject_id`，WireMock 没收到请求。
4. **不存在拒绝且不建行**：桩返回 `exists = false` → 抛 `SubjectNotFoundException`，登记表行数为 0；`pmid = null`、`DELETED` 两种同样不建行。
5. 对账遇到 PMID 整行消失：`state = UNAVAILABLE`、`source_id` 为空、快照保留；之后重导出现新 ID，用新 ID 首次解析能复用 S 并复活。
6. 对账一批失败：两页数据，第一页的请求返回 503 → 结果 `failedBatches = 1`，第二页照常刷新。

HTTP 状态（503 / 404 / 422）由领域异常的特征决定，本 Issue 没有接口可以端到端打到；PAP-113 的收藏接口集成测试会覆盖「首次收藏返回 503」。

### 9.3 架构测试

`InteractionArchitectureTest` 不改代码：新增的类都落在第 4 ~ 8 节写明的包里，它会自动检查子域公开包、层规则（`@Transactional` 只在 app、端口命名、`GatewayImpl` 在 app）。实施时确认它对新类确实生效（临时让 collection 引用 `domain.subject.port.repository` 看它红）。

### 9.4 门禁

`./gradlew spotlessApply`；`./gradlew check`；显式跑 `:patra-interaction-infra:integrationTest`、`:patra-interaction-boot:integrationTest`（PAP-111 实施记录：`check` 只编译集成测试）；`bash patra-infra/cd/detect-changes.test.sh`。

## 10. 文档与交接

| 文件 | 改动 |
|---|---|
| `patra-api/patra-interaction/README.md` | 第 1 节 subject 一行补「登记表 `subject.registry`、首次互动解析、每日对账」；第 4 节加表命名（不带前缀）与 `set_updated_at()` 每 schema 一份；第 5 节定时任务一行写明超时 600 秒、不重试 |
| `docs/patra/runbooks/v0.13-library-go-live-runbook.md` | 第 6 步对账任务一行补超时与重试；预期日志改为具体的汇总行格式；补「D7：mini 手动触发一次，日志有处理条数且无 ERROR」；新增一条通用规则：**catalog 任何换库重建 / 重跑导入（含 PAP-110 的回填）之后，立即手动触发一次 `subjectReconciliationJob`**，否则到次日 06:30 之前文献库按旧 `source_id` 取不到卡片、显示「已失效」，「我的状态」显示没收藏（第 5.4 节） |
| `patra-infra/cd/module-graph.json` | `./gradlew dumpModuleGraph` 重新生成 |
| 共享笔记 `PAP-112-subject-contract.md` | 第 6 节两个端口的签名与语义、四个异常与 HTTP 状态、「只用于新增互动」「异常不 import 直接传播」「`key` 为空时只能用快照」 |
| Linear PAP-112 | 开写代码时推到「BE」；完成后在描述里勾 To-Do 与 AC |

## 11. 我的决定（请评审时重点看）

1. 不出 REST 接口，AC 在 `SubjectGateway` 端口层用集成测试验证（9.2），HTTP 层的 503 由 PAP-113 的接口覆盖。
2. 首次解析先按 `source_id` 查本地、命中就不调 catalog（5.2 第 3 步）；命中但 UNAVAILABLE 时直接拒绝，不同步回查 catalog。
3. 按 `natural_key` upsert，换库后同一 PMID 复用原 `subject_id`；过期 `source_id` 被别的行抢到时先清掉旧行（7.3）。
4. `natural_key` 全表唯一，`source_id` 非空时唯一。
5. 表名 `subject.registry`；`set_updated_at()` 每个 schema 一份；全部原生 SQL，不建 JPA 实体，不继承 `BaseJpaEntity`。
6. 「按类型策略」= 每种 `SubjectType` 一个 `SubjectSourcePort` 实现，`SubjectSources` 启动时校验全覆盖。
7. `SubjectGateway` 只用于新增互动；已有互动直接用 `subject_id`。
8. 四个领域异常放不公开的 `domain.subject.exception`，PAP-113 让它们直接传播。
9. 首次解析的事务用 `TransactionOperations`，远程调用在事务外。
10. 对账按 `subject_id` 升序 100 行一页，一页失败跳过继续，任务内不重试；调度中心超时 600 秒。
11. 读端口上限：`findIdsByKeys` 100、`findByIds` 5000。
12. key 只接受规范的十进制正整数（无前导零、无符号、无空白）。
13. 快照用 `publishedDate`（降精度字符串）代替 `year`，在 domain 里校验格式；排序键随 `findByIds` 给出，排序机制归 PAP-113。不用 PAP-110 新增的 `dev.patra.common.PartialDate`：它没有 ISO 字符串的解析与输出、带着与快照无关的 `season` / `text`；快照存的就是 catalog 线上给出的字符串，原样落库、按字典序即可排序。

## 12. 实施记录（2026-10-24）

计划 `docs/patra/plans/2026-10-24-subject-registry.md`，本会话内联 TDD 执行。

### 12.1 与本设计的偏离

| # | 偏离 | 原因 |
|---|---|---|
| 1 | 持久化用 Spring `JdbcClient`，不用 `EntityManager.createNativeQuery`（7.2） | `INSERT … RETURNING`、`IN` 列表展开、可空列读取都更直接；`JpaTransactionManager` 经 `DataSourceUtils` 暴露同一连接，事务语义不变 |
| 2 | `saveReconciled` 对每个有来源 ID 的行都先执行「释放占用」（7.3 写的是只对有变化的行） | 聚合不带旧值；按部分唯一索引的空更新代价可以忽略 |
| 3 | `SubjectReadPort` 暴露常量 `MAX_KEYS = 100`、`MAX_IDS = 5000` | 调用方（PAP-113）按它分批 |
| 4 | infra 集成测试加 `spring-boot-starter-validation`；`InteractionITBootstrap` 提供一个 mock `PublicationEndpoint`；持久化集成测试用只扫 `infra.subject.persistence` 的 `SubjectRegistryITBootstrap` | http-client 的属性类带校验约束（identity-infra 同样加了）；catalog 代理在 boot 里装配 |
| 5 | `patra-tool-cqrs` 在首次解析那一步就加进 app | app 的 `@Component` 要 spring-context，identity 也经 cqrs 拿到 |
| 6 | `SubjectSources` 声明为 `final` | SpotBugs `CT_CONSTRUCTOR_THROW`，PAP-111 同样处理 |
| 7 | 计数 `sourceIdChanged` 含 null ↔ ID 的变化（复活补回、整行消失清空） | 与 `ReconciliationResult` 的说明一致 |
| 8 | 文本块 `RETURNING """` 写成 `RETURNING\s"""` | Java 文本块吞行尾空格，列名会粘在 `RETURNING` 上 |
| 9 | `upsertResolved` 先拿事务级 advisory lock `pg_advisory_xact_lock(hashtextextended('subject.registry:{类型编码}:{身份锚}', 0))`，再执行 7.3 的两条语句（改 5.2「并发」一段的做法） | 终审指出：只靠 `ON CONFLICT (target_type, natural_key)`，两个事务都通过预检查后，非判定索引 `uk_registry_source_id` 会直接报唯一冲突，走不到 `DO UPDATE`。锁只串行化同一 PMID 的首次解析 |
| 10 | catalog 返回不合格式的 PMID 时与 `pmid = null` 一样跳过并记 WARN（4.4） | 终审指出：catalog 的 PMID 列没有格式约束，坏值会在 `NaturalKey` 里抛错，让整次对账中止 |

### 12.2 门禁

- `./gradlew check`：BUILD SUCCESSFUL。
- `:patra-interaction-infra:integrationTest --rerun`、`:patra-interaction-boot:integrationTest --rerun`：全部通过（`SubjectRegistryFlowIT` 7 例覆盖 Issue 的 4 条 AC）。
- `detect-changes.test.sh`：通过 66 / 失败 0。
- 架构测试：临时在 `app.collection` 引用 `SubjectRepository`，规则 25「跨子域只经对方的公开包」报红，删掉后恢复。
