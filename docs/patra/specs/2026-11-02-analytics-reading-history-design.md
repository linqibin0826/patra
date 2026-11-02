# 阅读历史设计

> **Issue**：PAP-169（v0.18 Analytics，Milestone Data Ready）
> **日期**：2026-11-02
> **依据**：release spec [`v0.18-analytics.md`](../release-specs/v0.18-analytics.md) 第 2.1 节「阅读历史」「两层数据」「保留期」、决策 B、J；[采集设计](2026-11-01-analytics-collector-design.md)；[入库设计](2026-11-01-analytics-ingest-design.md) §6（注销清理底座）；设计简报 `patra-portal/docs/design-briefs/v0.18/analytics.md` §3.7、§3.8、附录 A 第 8～11 条（PAP-166，分支 `claude/hungry-gagarin-2357af`）
> **前置**：PAP-167、168 已在 `feat/v0.18-analytics-api`

---

## 1. 要做什么

在 `patra-analytics` 里加 `history` 子域：

1. **写历史**：登录用户打开文献、读全文时，采集端点在同一次请求里把它记进个人阅读历史（PG）。同一篇只留一行，记最近一次打开的时间和是否读过全文。
2. **记录开关**：默认开。关闭后不再记录，已有的保留。
3. **接口**：我的历史（倒序、游标翻页，顺带返回开关状态）、单条删除、清空、读写开关。都要登录。
4. **清理**：每天删掉 12 个月前的历史；用户注销后清空他的历史与开关（接入 PAP-168 的注销清理底座）。

**两层数据**：个人历史和 ClickHouse 里的统计是两套数据。关闭记录、单条删除、清空只动历史，浏览量照常累计。这一点由数据流天然保证：写历史发生在事件写进 Kafka 之后，入库与历史互不读取。

**不做**：门户页面与设置卡片（PAP-174）；在历史里拼文献的标题、刊名（门户按 ID 调 catalog 的批量接口，§6.4）；按历史做个性化推荐（release spec 第 3 节 C）；设备 ID 产生的匿名浏览进历史（只记登录用户）。

## 2. 写历史的时机：采集时同步写（用户 11/02 定）

可选的位置有两个：

| 做法 | 是什么 | 问题 |
|---|---|---|
| **A. 采集端点里写（本版）** | 一批事件写进 Kafka 成功后，当场把这批里的文献浏览写进 PG | 采集端点多一次 PG 写入；写失败只是这次没记上 |
| B. 另起 Kafka 消费者 | 第二个消费组读 `events.raw`，写 PG | 消费有延迟。清空历史后，还在 Kafka 里排队的那次打开会被写回来，刚清空的历史又冒出一条；要防就得记下每个人的清空、删除时间并逐条比对 |

阅读历史是用户自己看、自己删的东西，「清空了就真的空了」比拆成两个消费者更重要，所以选 A（也是 release spec 决策 B）。

**代码上怎么接**：采集与历史是两个子域，采集用例不直接调用历史的代码。采集用例在 Kafka 写成之后发一个进程内事件 `ArticlesReadEvent`（`domain.collect.event`），历史子域用同步的 `@EventListener` 接住、写 PG。同步监听与发布方在同一个线程里执行，效果等同于直接调用，写完才返回 202。架构测试里声明这条订阅边：`history` 订阅 `collect`。

**为什么能信事件里的 `user_id`**：采集端点只在内网（tailscale）可达；公网的浏览器事件先到门户，门户按会话补上 `user_id`（采集设计 §4.1、埋点方案 §2：采集端点以信封里的 `user_id` 为准）。所以「谁读了什么」不会被伪造成别人的。这是本设计的前提，有两条约束：

- 公网只开放门户和学习站（`patra-infra/docker/README.md`「门户的公网入口」），网关与采集端点不对公网；采集端点以后要是对公网开放，写历史前必须先改成从可信来源取用户。
- **给 PAP-173**：门户的第一方采集路径只能按服务端会话填 `user_id`，浏览器批次里不带身份字段（`browser-event` schema 已经不允许），也不能透传浏览器给的任何用户标识。

## 3. 子域与分层

| 东西 | 位置 |
|---|---|
| 子域 | 新增 `history`（个人阅读历史）。订阅 `collect` 的事件 |
| 事件 `ArticlesReadEvent(List<ArticleRead> reads)`、`ArticleRead(long userId, long articleId, Instant readAt, boolean fulltext)` | `domain.collect.event`（collect 发布的契约，其他子域只能经这个包依赖 collect） |
| 仓储 `ReadingHistoryRepository`（写一批、翻页、删一条并写删除记录、清空、查一批里的删除记录、过期删除、找用户、按一批用户删） | `domain.history.port.repository` → `infra.history.persistence` |
| 仓储 `RecordingSettingRepository`（读开关、改开关、记清空时刻、一批用户的开关与清空时刻、按一批用户删） | 同上 |
| 写历史 `ReadingHistoryListener`（`@EventListener`） | `app.history.listener` |
| 接口用例：清空、删一条、改开关（Command + Handler）；翻页与读开关（`ReadingHistoryQueryService`） | `app.history.usecase.*` |
| 过期清理 `PurgeExpiredHistoryHandler` + `ReadingHistoryRetentionScheduleJob` | `app.history.usecase.purge`、`adapter.history.scheduler` |
| 注销清理 `HistoryUserDataCleanupGatewayImpl` | `app.history.service` |
| 接口 `MyReadingHistoryController` | `adapter.history.rest` |
| 表 | PG 库 `patra_analytics`，schema `history`，迁移在 `infra/src/main/resources/db/migration/history/` |

当前用户用 `patra-tool-security` 的 `CurrentUserPort`（同 notification）：网关对 `/patra-analytics/**` 放行，analytics 自己验网关签发的身份断言，接口里 `currentUser.require()`，没登录就是 401。网关不改。

## 4. 写历史

### 4.1 记哪些事件

只记**登录**（`user_id` 非空）、**非合成**（`is_synthetic = false`）、带 `article_id` 的这三种：

| 事件 | 记成 |
|---|---|
| `article_viewed`（服务端，打开文献详情） | 打开过 |
| `fulltext_read`（浏览器，全文区可见累计 ≥ 10 秒） | 打开过 + 读过全文 |
| `pdf_downloaded`（服务端） | 打开过 + 读过全文（埋点方案 §6.8：全文 = 全文阅读或下载 PDF） |

- 被判为爬虫的照记：那是这个账号自己的历史，爬虫标记只用在对外数字上。
- 合成事件不记：模拟器的用户 ID 在 identity 里不存在，注销清理会把它们当成「identity 不认识」而永远不删，表里会一直堆着。
- 不合格进 dlq 的事件不记；Kafka 写失败（503）的那一批也不记（门户会丢弃这批，模拟器会重试，重试那次再记）。
- `article_collected`、`citation_copied` 等不记：「阅读历史」是打开过的文献，不是操作记录。

`article_id` 来自事件属性。采集校验通过时（`NetworkntEventSchemaAdapter`）顺手把属性里的 `article_id` 解析成 `CollectedEnvelope.articleId`（`Long`，没有为 `null`），采集用例不解析 JSON。

### 4.2 流程

1. `CollectEventsHandler`：`publisher.publish(messages)` 成功后，把这批合格事件里符合 §4.1 的挑出来，`readAt` 用事件的 `derived_at`（校正后的事件时间），发一个 `ArticlesReadEvent`；没有符合的就不发。
2. `ReadingHistoryListener`：
   1. 在 Java 里按（用户，文献）合并：`readAt` 取最大、`fulltext` 取「或」。同一条 `INSERT ... ON CONFLICT DO UPDATE` 里出现两行相同的键时 PG 会直接报错（`cannot affect row a second time`），所以必须先合并。
   2. 查这批用户的设置与删除记录（各一条 `IN` 查询），去掉三种行：关了记录的人的；`readAt` 不晚于这个人清空时间的；`readAt` 不晚于这一篇被删时间的（§4.4）。
   3. 按（用户，文献）排序后一条语句写入：没有就插入；有就 `last_read_at = GREATEST(旧, 新)`、`fulltext_read = 旧 OR 新`。排序是为了两个并发的采集请求按同一顺序加行锁，不会死锁；`GREATEST` 让晚到的旧事件不会把时间往回改。
   4. 用 `TransactionOperations` 把第 2、3 步包进同一个事务（实施时调整：两次查询也要受下面的语句超时约束，只占一个连接）。
3. **失败不影响采集**：第 2 步抛出的任何异常在监听器里捕获，记 WARN 与指标 `analytics.history.writes{outcome="failed"}`，不往外抛，采集照样返回 202。代价是那次打开没进历史，用户再打开一次就补上了。
4. **慢也算失败**（审阅补，我的决定）：PG 卡住时写历史不能把采集拖过门户的 6 秒超时。照 Redis 频率规则的先例（500 ms 超时、超了就跳过）：
   - Hikari `connection-timeout` 从 30 秒改为 500 ms（整个服务统一改：PG 上都是短查询，历史接口在连接池用尽时快速失败、页面单独降级，也比卡 30 秒好）；
   - 这个事务开头 `SET LOCAL statement_timeout = '500ms'`、`SET LOCAL lock_timeout = '500ms'`，查询与写入都受约束。
   - 写进历史的时间截到毫秒（与 Kafka 里的 `derived_at` 一致；翻页游标只到毫秒，存微秒会在同一毫秒的行上漏翻）。
   超时按失败处理（第 3 条）。最坏情况下写历史占 1 秒多（拿连接 + 两次查询 + 写入），加上 Kafka 的整批截止 5 秒，只在 Kafka 与 PG 同时很慢时才会碰到门户的 6 秒；这种情况门户本来就会丢掉这一批。

### 4.3 开关

- 没有设置过 = 开。设置表里只有改过开关或清空过历史的人才有一行；关掉写 `recording_enabled = false`，重新打开更新为 `true`（不删行：行里还有 `cleared_at`）。
- 关掉只停后续，已有的保留（YouTube「暂停观看记录」的做法）。
- 关、开之间的并发：用户关掉的那一刻正好有一批事件在写，这一批可能还会写进去（开关在第 2.2 步读，写入在第 2.3 步）。窗口是毫秒级，不另加锁。

### 4.4 清空、删除之后晚到的事件（用户 11/02 定：堵上）

事件可能比用户的操作晚到：浏览器每隔几秒才把「读了全文」攒成一批发出；门户、模拟器失败重试。不处理的话，刚清空的历史、刚删掉的那一篇会被一条晚到的事件加回来。

- **清空**时记下清空时刻 `cleared_at`（服务端时钟）。之后写历史时，`readAt` 不晚于 `cleared_at` 的事件丢掉：清空之前发生的打开不再进历史，清空之后的照常记。
- **单条删除**时记一条删除记录（用户，文献，`deleted_at`）。之后这一篇 `readAt` 不晚于 `deleted_at` 的事件丢掉；之后再打开（`readAt` 更晚）照常记、重新出现在最上面。
- **删除记录留 2 天**：事件时间最多比收到时刻早 24 小时（`EventTimeCorrection.MAX_EARLY`，更早的按收到时刻算，那就是一次「新」的打开），所以 2 天后删除记录不再起作用，由过期清理任务删掉（§7.1）。清空时刻存在设置表里，不过期。
- 比较用的是事件的 `derived_at`。它最多能比收到时刻晚 5 分钟（`MAX_LATE`），客户端时钟严重偏差时极少数清空前的打开会漏网，不另处理。
- 被丢掉的计入 `analytics.history.writes{outcome="erased"}`。

## 5. 表结构

```sql
CREATE SCHEMA history;

-- 每人每篇一行：最近一次打开的时间、是否读过全文。只存用户 ID 与 catalog 的文献 ID，不建跨库外键
CREATE TABLE history.reading_history (
    user_id        BIGINT      NOT NULL,
    article_id     BIGINT      NOT NULL,
    last_read_at   TIMESTAMPTZ NOT NULL,
    fulltext_read  BOOLEAN     NOT NULL,
    PRIMARY KEY (user_id, article_id)
);
-- 翻页：按最近打开时间倒序、同一时间按文献 ID 倒序
CREATE INDEX idx_reading_history_user_time
    ON history.reading_history (user_id, last_read_at DESC, article_id DESC);
-- 过期清理：按时间扫
CREATE INDEX idx_reading_history_time ON history.reading_history (last_read_at);

-- 改过开关或清空过历史的人才有一行；没有行 = 开着、没清空过
CREATE TABLE history.recording_setting (
    user_id            BIGINT      PRIMARY KEY,
    recording_enabled  BOOLEAN     NOT NULL,
    cleared_at         TIMESTAMPTZ,              -- 最近一次清空的时刻；没清空过为 NULL
    updated_at         TIMESTAMPTZ NOT NULL
);

-- 单条删除的记录：挡住删除之前发生、删除之后才到的事件（§4.4）；2 天后由过期清理删掉
CREATE TABLE history.deleted_entry (
    user_id     BIGINT      NOT NULL,
    article_id  BIGINT      NOT NULL,
    deleted_at  TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, article_id)
);
CREATE INDEX idx_deleted_entry_time ON history.deleted_entry (deleted_at);
```

不存用户名（R6）。不用 JPA 实体基类的审计列：两张表都是按键覆盖写，审计列没有意义；按 `patra-jpa` 的约定，用 `JdbcClient` 写原生 upsert（照 interaction 的 `HelpfulRepositoryAdapter`）。

## 6. 接口

都在 `adapter.history.rest.MyReadingHistoryController`，前缀 `/me/reading-history`（经网关是 `/patra-analytics/me/reading-history`），都要登录，未登录 401。ID 按项目约定序列化成字符串。

### 6.1 我的历史

`GET /me/reading-history?cursor=…`

```json
{
  "recordingEnabled": true,
  "items": [
    { "articleId": "39871245", "lastReadAt": "2026-11-02T07:42:10.177Z", "fulltextRead": true }
  ],
  "nextCursor": "MTc2MjA2OTMzMDE3Nzo0MDAwMDE"
}
```

- 按 `last_read_at` 倒序、同一时间按 `article_id` 倒序；一页固定 30 条（简报 §3.7 第 6 条）。
- 游标是上一页最后一行的（时间毫秒，文献 ID），Base64URL 编码、对门户不透明；`nextCursor` 为 `null` 表示到底了。游标解析不了返回 422（项目约定：校验失败一律 422）。
- 只返回 12 个月以内的（清理任务每天跑一次，这里再按时间过滤一次，跑之前的那一天也不会多出来）。
- `recordingEnabled` 顺带返回（附录 A 第 11 条），门户用它和「列表是不是空」选提示条与空状态。
- 只给文献 ID；标题、刊名、年份由门户调 catalog 的 `GET /portal/publications/batch` 取（文献库页现在就是这么做的）。catalog 里已经没有的文献，批量接口不返回，门户显示「这篇文献已不在 Patra 上」，仍可删除（附录 A 第 8 条）。

### 6.2 删一条、清空

- `DELETE /me/reading-history/{articleId}` → 204。一个事务里删掉这一行、写（或更新）删除记录 `deleted_at = 现在`。这一篇本来就不在也返回 204，照样写删除记录（附录 A 第 9 条：重复删除按成功处理）。`articleId` 不是正整数返回 422。
- `DELETE /me/reading-history` → 204。一个事务里删掉这个人的全部历史与删除记录、写 `cleared_at = 现在`（设置表没有这一行就插入一行 `recording_enabled = true`）。没有记录也是 204。开关不变。

### 6.3 开关

- `GET /me/reading-history/recording` → `{ "enabled": true }`（没设过是 `true`）。
- `PUT /me/reading-history/recording`，请求体 `{ "enabled": false }` → 返回保存后的 `{ "enabled": false }`。`enabled` 缺失返回 422。

开关单独一个接口：设置页的「阅读记录」卡片单独保存、单独降级（简报 §3.8），不跟 identity 的隐私设置一起提交。

### 6.4 为什么不在后端拼文献信息

后端拼要么 analytics 调 catalog（多一个服务间依赖，catalog 挂了历史页整页打不开），要么在 analytics 冗余文献标题（catalog 改了标题要回刷）。门户已经有按 ID 批量取卡片的现成做法，失败时也能单独降级。

## 7. 清理

### 7.1 12 个月自动清理

- XXL-Job 任务 `readingHistoryRetentionJob`，执行器 `patra-analytics`，每天北京时间 06:40（`0 40 6 * * ?`，错开注销清理的 06:20）。
- 删 `last_read_at` 早于「现在减 12 个月」的行（按 UTC 日历减 12 个月，时间来自注入的 `Clock`）。一次删 10,000 行（`DELETE ... WHERE ctid IN (SELECT ctid ... LIMIT 10000)`），删到没有为止，每批一个短事务，不会长时间锁表。
- 同一个任务顺带删掉 `deleted_at` 早于 2 天前的删除记录（§4.4）。
- 开关表不过期。
- 任务回写「已删除 N 条」；失败时标红，次日重跑（删除是幂等的）。

### 7.2 注销清理

接入 PAP-168 的底座流程（`CleanupDeletedUsersHandler`，入库设计 §6）：

- `HistoryUserDataCleanupGatewayImpl.subdomain()` = `history`。
- 找用户：`reading_history`、`recording_setting`、`deleted_entry` 三张表里游标之后的用户 ID，合并去重、升序取前 N 个。
- 删：一个事务里删掉这批用户在三张表里的行（各一条 `WHERE user_id IN (...)`），返回清理的用户数。

底座流程本身不变：identity 返回 `DELETED` 的才删、identity 没返回的不删、identity 不可用跳过这一批。

## 8. 指标

| 指标 | 说明 |
|---|---|
| `analytics.history.writes`（计数，标签 `outcome`：`recorded` 写入、`disabled` 因开关关着跳过、`erased` 发生在清空或删除之前而丢掉、`failed` 写入失败或超时） | 按（用户，文献）合并后的行数 |
| `analytics.history.purged`（计数，标签 `table`：`reading_history`、`deleted_entry`） | 过期清理删掉的行数 |

注销清理沿用 `analytics.cleanup.users`。

## 9. 测试

| 层 | 测什么 |
|---|---|
| 单元 | 挑事件：只要登录、非合成、三种事件之一；`pdf_downloaded` 算全文；Kafka 写失败时不发事件；合并：同一批里同一篇的时间取最大、全文取「或」；监听器写入失败时不抛出、记 `failed`；关着的用户不写、记 `disabled`；清空前、删除前发生的不写、记 `erased`（正好等于清空时刻的也丢）；游标编码与解析（坏游标 422）；过期清理的时间边界（固定 `Clock`：正好 12 个月前的那一刻删不删，写明取舍） |
| PG 集成（Testcontainers） | 清空写 `cleared_at` 并删掉删除记录；单条删除写删除记录、重复删除照样成功；语句超时：在另一个连接里锁住目标行，写入在 500 ms 左右以超时失败（不等满 30 秒）；upsert：再打开同一篇只留一行、时间更新、全文标记不会被后来的「只打开」抹掉、晚到的旧事件不把时间往回改；翻页：30 条一页、游标连续翻不重不漏、同一毫秒多行按文献 ID 稳定排序、12 个月前的不返回；删一条与清空只删自己的；过期清理分批删完；注销清理按一批用户删两张表、找用户合并两张表 |
| Web 切片 | 四个接口的路径、状态码（204 / 401 / 422）、ID 是字符串、未设置开关时返回 `true` |
| 端到端（boot） | 采集一批：登录用户的文献浏览出现在历史里；关掉记录后再采集，历史里没有这条，而 Kafka `events.raw` 里照样有这条事件（浏览量照常，对应 release spec D7 的后端部分）；清空后 `events.raw` 不受影响；清空后再采集一条清空之前发生的（`created_at` 早于清空时刻）不出现、清空之后发生的出现 |
| 架构 | 新子域 `history` 加进架构测试，声明 `history` 订阅 `collect` |

## 10. 上线

写进 v0.18 runbook 第 8 节新的一小节：

- 无新密钥、无新容器；Flyway 在 analytics 启动时建 `history` schema。
- 网关不改（`/patra-analytics/**` 已有路由，登录由 analytics 自己判断）。
- XXL-Job 在执行器 `patra-analytics` 下登记 `readingHistoryRetentionJob`（每天 06:40）。
- 验收：用测试账号登录，打开一篇文献，`GET /patra-analytics/me/reading-history` 里出现这篇；关掉记录再打开另一篇，历史里没有、Kafka 里有。

## 11. 我的决定（等你审）

1. **采集时同步写历史**（§2，用户 11/02 选 A）：Kafka 写成后发进程内事件，历史子域同步监听写 PG；写失败不影响采集。
2. **只记登录、非合成的文献浏览、全文阅读、PDF 下载**；爬虫标记的照记（§4.1）。
3. **每人每篇一行**，时间取最近、全文取「或」，写入前在 Java 里合并、排序（§4.2）。
4. **开关没设过 = 开**，只存改过开关或清空过的人；关掉只停后续（§4.3）。
5. **接口只给文献 ID**，标题、刊名由门户调 catalog 批量接口（§6.4）。
6. **一页固定 30 条，不透明游标**；列表顺带返回开关状态（§6.1）。
7. **登录由 analytics 自己判断**（`patra-tool-security`），网关不改（§3）。
8. **过期清理每天 06:40、按 12 个日历月、每批 1 万行**；开关表不过期（§7.1）。
9. **注销清理接入 PAP-168 的底座**，`history` 一批用户一个事务删三张表（§7.2）。
10. **清空、删除之后晚到的事件挡掉**（§4.4，用户 11/02 定）：清空记 `cleared_at`，单条删除记删除记录（留 2 天），事件时间不晚于它们的不写。
11. **写历史慢也算失败**（§4.2 第 4 条，审阅补）：Hikari 拿连接 500 ms、写入语句与锁等待各 500 ms，超时跳过。
