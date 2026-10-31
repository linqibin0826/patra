# interaction 发集成事件

- 日期：2026-10-31
- 分支：`claude/v016-pap141-e48ad7`（基线 `claude/v016-integration` @ `a1eedc4d9`）
- Issue：PAP-141（v0.16 决策 A、B、C、J）
- 依赖：PAP-137 事件模块（`patra-tool-events`）、PAP-128 正文转纯文本（`PlainTextPort`）
- 下游：PAP-143（站内提醒）、PAP-144（有帮助提醒）、PAP-146（提醒邮件）照本 spec 第 5 节的契约写消费端
- 范围：interaction 接入事件模块；五种事件外送到 `interaction.events`；领域事件补事件 ID、版本与纯文本；`reaction` 子域补发事件。interaction 现有的 `notification` 子域不动，由 PAP-143 删除
- 契约定稿：2026-10-31 编排会话代用户拍板（对象展示上下文读时现查、`helpful.unmarked` 不外送），见第 9 节

## 1. 为什么做

v0.16 把站内提醒从 interaction 搬到新服务 patra-notification（决策 A）。两个服务的库放不进一个事务，interaction 只能把「发生了什么」可靠地告诉通知服务，由通知服务自己算出提醒谁、写进信箱、发邮件。

现在这些事实只在 interaction 进程内流动：讨论子域发 `CommentPublishedEvent` 等三种进程内事件，提醒子域在同一事务里同步写提醒；投稿经验删除发 `PostDeletedEvent`；有帮助（v0.15 的 `reaction` 子域）点与取消什么都不发。本 Issue 把它们变成跨服务的**集成事件**，经事件模块写进 Outbox（`event_publication`）、提交后外送到 RabbitMQ。

通知服务**计算收件人**时不回头调 interaction（决策 A），所以算收件人、去重、判乱序要用的事实全部随事件带；提醒列表与邮件里「在哪」那一行（对象的 key、标题、期刊）由通知服务在读列表、发邮件时调 interaction 的内部接口「按 ID 取对象视图」现拼（PAP-143 写），不进事件（2.1）。

## 2. 先讲清几个概念

### 2.1 事件里带什么：事实随事件走，展示读时现查

- **两种做法**：
  - **事件携带状态转移**（Martin Fowler 的说法）：消费方要用的数据随事件送过去，消费方存一份，不再回头问发布方。
  - **事件通知 + 回调查询**：事件只说「评论 123 发表了」，消费方要详情时再调发布方的接口。
- **防什么**：带状态防的是两个服务在运行时互相依赖——发布方挂了，消费方照样能干活；回调查询防的是消费方存了一份**会过期**的数据。
- **Patra 的情况**：两类数据性质不同。
  - **算收件人、去重、判乱序要用的事实**（被回复评论的作者、经验作者、@ 到的人、是否匿名、版本）在事件发生那一刻就定了，之后不会变，也必须在消费时立刻可用。这类随事件带。
  - **对象的展示信息**（文献 key、标题、经验的期刊）会变：catalog 重导后文献的 key 会变，文献会下线。存一份快照，旧提醒的链接会 404、下线的文献照样显示摘录。interaction 自己的提醒列表正是因此一直读时现查登记表。这类不进事件，由通知服务读时调 interaction 的内部批量接口现拼，interaction 不可用时降级成不显示对象（门户已支持）。
- **我们选**：事实随事件带，展示读时现查（编排会话拍板，决策 A 的「不回头调」只管计算收件人）。
- **有帮助的例外**：被点那条内容的**纯文本**与经验的**结果**随 `helpful.marked` 带。它们不是「在哪」，而是条目第二行的摘录（设计简报 §3.2）：通知服务手里没有这条内容（上线前发的评论、没人回复的根评论都没有任何事件）。代价是内容事后被编辑，条目里的摘录不跟着变——摘录只用来认出「是哪一条」，可以接受。
- **代价**：事件稍大（评论事件带全文纯文本，约 1–3 KB）；提醒列表读时多一次对 interaction 的调用。

### 2.2 版本号：编辑事件晚到了怎么办

- **是什么**：每条评论有一个整数版本，发表时是 1，每次正文真的变了加 1（发表 5 分钟内的原地修改也算）。编辑事件带上改完后的版本，消费方只在「事件版本 > 已存版本」时才用它覆盖摘录。
- **防什么**：乱序。RabbitMQ 重投、补投都不保证顺序：同一条评论连改两次，第二次的事件可能先到，第一次的晚到后不能把新摘录盖回旧的。
- **为什么不用编辑时间当版本**：编辑处理器在**拿行锁之前**取时间。同一个人在两个标签页里几乎同时改同一条：A 先取到时间 t1，B 后取到 t2，但 B 先拿到锁、先提交；A 后提交，库里留下的是 A 的正文，A 的事件却带着更早的 t1，消费方会保留 B 的摘录，和库里不一致。机器时钟被 NTP 往回拨也会出同样的事。整数版本在行锁里加一、和正文在同一条 UPDATE 里写入，提交顺序就是版本顺序。
- **为什么不用 `comment_revision.revision_no`**：发表 5 分钟内的修改原地替换、不记修订，修订号不变。
- **代价**：`discussion.comment` 加一列 `version`，聚合与持久化跟着改。

### 2.3 契约测试：发出去的 JSON 对方真能读

- **是什么**：用发布方实际发出的消息，按消费方的读法（`patra-interaction-api` 里的 record）反序列化一遍，字段一一对得上。
- **防什么**：两端各改各的。字段改名、类型变了、时间格式变了，编译期发现不了，要到线上通知服务报反序列化失败、消息进死信才知道。
- **我们选**：集成测试从真 RabbitMQ 的测试队列里收下原始消息，检查消息头 `__TypeId__` 是 api 里 record 的全类名、`messageId` 是事件 ID，再用普通 `JsonMapper` 把正文读成 api 里的 record，与期望值整体比较。

### 2.4 只外送有人订阅的事件

事件模块发送时带 `mandatory`：路由键没有任何队列绑定时，broker 把消息退回，模块当成发送失败，登记行留着、每分钟补投一次，永远发不出去（PAP-137 spec 2.3）。所以：

- 外送的五个路由键，上线前每个都必须有通知服务的队列绑定；测试里由测试队列绑定全部五个。
- 「有帮助已取消」通知服务没有动作（产品规则：取消不撤回、人数不减），没有队列会绑它，所以**不外送**。领域事件照发，只是不进外送配置；以后有了消费方再加进来。

## 3. 目标、非目标、反目标

**目标**

1. interaction 接入 `patra-tool-events`：Flyway 建 `event_publication`，boot 层声明交换机与外送配置。
2. 五种事件在业务事务提交后送达 `interaction.events`，路由键为事件类型；事务回滚时不外送。
3. 事件带齐第 5 节列出的事实：算收件人、去重、判乱序不需要回头调 interaction。
4. 事件 ID 在领域事件创建时生成，补投时不变。
5. 正文按 PAP-128 的规则转成纯文本，保留 `<@用户ID>`。

**非目标**

- 消费端（PAP-143 / PAP-144 / PAP-146），包括通知服务的队列声明。
- interaction 的内部接口「按 ID 取对象视图」（PAP-143 写）。
- 删除 interaction 的 `notification` 子域和它的同步监听器（PAP-143）。过渡期里同一个领域事件既被同步监听器写进旧提醒表，又被外送，这是有意的。
- 投稿经验的发表、编辑事件：没有消费方需要（v0.16 没有「关注」，release spec 第 3 节 E）。
- 用户资料、对象状态变化（文献下线、key 变化）的事件：release spec 第 3 节 K。

**反目标**（做到了也算失败）

- 领域层贴 `@Externalized`、依赖 Spring 或 AMQP 类型。
- 在映射里生成事件 ID（补投会换 ID，消费端去不了重）。
- 映射里查库或调转换器：映射在每次外送（含补投）时执行，查库会让补投结果随时间变化，转换器抛异常会让这条事件永远发不出去。映射只拷字段。
- 服务代码里出现 `@ApplicationModuleListener`（会进登记表、被补投）。
- 外送没有队列绑定的事件。

## 4. 设计

### 4.1 发布流程

```text
app Handler 的事务内
  ├─ 写业务数据（评论 / 删除 / 有帮助明细……）
  └─ events.publishEvent(领域事件)       ← 领域事件已带 eventId、版本、纯文本
        ├─ 同步 @EventListener（旧提醒子域、有帮助删除联动）照旧在事务里执行
        └─ 被外送配置选中的类型：Modulith 写一行 event_publication（只为外送器登记，PAP-137 4.4）
提交
  └─ 外送器（Modulith，异步）
        ├─ EventExternalizationConfiguration：选中、路由到 interaction.events / 路由键、映射成 api 里的 record
        └─ ConfirmingRabbitMessagingTemplate：带 mandatory 发送、等 publisher confirm
回滚 → 登记行一起回滚，不外送
```

### 4.2 事件一览

| 路由键 | 领域事件（domain） | 集成事件（`patra-interaction-api`） | 谁发、什么时候 |
|---|---|---|---|
| `comment.published` | `discussion.event.CommentPublishedEvent` | `api.discussion.event.CommentPublished` | 发表根评论或回复 |
| `comment.edited` | `discussion.event.CommentEditedEvent` | `api.discussion.event.CommentEdited` | 正文确有变化的编辑（含发表 5 分钟内的） |
| `comment.deleted` | `discussion.event.CommentDeletedEvent` | `api.discussion.event.CommentDeleted` | 变占位或整条去掉；已是占位再删、级联去掉的占位不发 |
| `post.deleted` | `post.event.PostDeletedEvent` | `api.post.event.PostDeleted` | 投稿经验软删；已删除再删不发 |
| `helpful.marked` | `reaction.event.HelpfulMarkedEvent`（新） | `api.reaction.event.HelpfulMarked` | 真插入了一条有帮助明细；重复点不发 |
| （不外送） | `reaction.event.HelpfulUnmarkedEvent`（新） | — | 真删掉了一条明细；没点过就取消不发 |

包名都在 `dev.patra.interaction` 下。api 层按子域分包（`SubdomainArchitectureRules` 管 api 层），所以集成事件分在三个子域的 `event` 包，共用的 `SubjectContext` 与交换机名放底座 `api.subject.event`。

路由键用「对象.动作」：`post` 与 `SubjectType.POST`、门户的 `/posts/[id]` 一致；`helpful` 与接口路径一致。

交换机名与每个路由键都写成 api 里的常量（`InteractionExchange.NAME`、各 record 的 `ROUTING_KEY`），消费方声明绑定时直接引用，不抄字符串。

### 4.3 领域事件的改动

**所有领域事件**加 `UUID eventId`，在发布处 `UUID.randomUUID()` 生成，紧凑构造器 `requireNonNull`（不在构造器里给空值兜底生成：反序列化登记行时会走构造器，兜底会让补投换 ID）。登记表存的是领域事件的 JSON，补投时由 Modulith 读回再映射，所以外送的领域事件里的值对象都要能经 Jackson 往返（测试见第 6 节）。

| 领域事件 | 新增字段 |
|---|---|
| `CommentPublishedEvent` | `eventId`、`version`（恒为 1）、`plainText` |
| `CommentEditedEvent` | `eventId`、`version`（改完后的版本）、`plainText` |
| `CommentDeletedEvent` | `eventId`、`version`（删除时的版本） |
| `PostDeletedEvent` | `eventId`。参数变成 5 个，按代码规范改用 `@Builder`、删掉 `of()` |
| `HelpfulMarkedEvent`（新） | 见下 |
| `HelpfulUnmarkedEvent`（新） | 见下 |

已有字段（`subjectId`、`subjectType`、`subjectOwner` 等）不动。发表、编辑事件原来带的存储形式正文 `body` 只给旧提醒子域的同步监听器用，PAP-143 删掉提醒子域后已从两个事件里删除（2026-10-31，上线前删，mini 上还没有登记行，不受「改字段前登记表要空」的限制）；服务内外都只用纯文本 `plainText`。

新的两个事件放 `domain.reaction.event`（`reaction` 第一次有事件包）：

```java
@Builder
public record HelpfulMarkedEvent(
    UUID eventId,
    HelpfulTargetType targetType,     // COMMENT / POST
    long targetId,                    // 评论 ID 或帖子 ID
    long targetAuthorId,              // 被点内容的作者（匿名经验也是真实作者）
    boolean targetAnonymous,          // 匿名经验，或作者在自己匿名经验下的评论
    long userId,                      // 点的人
    Instant markedAt,                 // 点的那一刻（注入的 Clock，截到微秒）
    SubjectId subjectId,              // 评论所在对象；点经验时是经验自己的登记对象
    SubjectType subjectType,
    SubjectOwner subjectOwner,        // 文献为 null
    ExperienceOutline experience,     // 目标是投稿经验时它的结果；目标是评论为 null
    String targetPlainText) {}        // 被点内容的纯文本；经验没有正文时为 null

@Builder
public record HelpfulUnmarkedEvent(
    UUID eventId, HelpfulTargetType targetType, long targetId, long userId, Instant unmarkedAt) {}
```

`HelpfulUnmarkedEvent` 只带「谁取消了哪个目标」：现在没有订阅方，不为它多读目标；以后要外送时再按消费方的需要加字段（届时登记表里没有它的行，加字段不受 PAP-137「改字段前登记表要空」的限制）。

### 4.4 评论版本（`discussion.comment.version`）

- Flyway：`ALTER TABLE discussion.comment ADD COLUMN version INTEGER NOT NULL DEFAULT 1` 加 `CHECK (version > 0)`；已有的评论都算版本 1。
- `CommentAggregate` 加 `version`；`edit()` 在正文确有变化时返回 `version + 1` 的新聚合（5 分钟内原地替换也加一：摘录变了，消费方要能分出新旧）。
- 发表：`NewComment` 不带版本，插入用列默认值 1；事件写常量 1（`CommentAggregate.INITIAL_VERSION`）。
- 编辑：`updateBody` 把新版本和正文写在同一条 UPDATE 里；行锁（`findForEdit`）保证同一条评论的编辑排队，版本严格递增。事件取 `edit.updated().getVersion()`。
- 删除：版本不变，事件带锁内读到的当前版本。删除对消费方是终态（墓碑），版本只作记录。

### 4.5 正文纯文本（PAP-128）

在发布领域事件处转换，放进领域事件；boot 映射只拷字符串（反目标第 3 条）。转换在事务外做（纯计算），不拉长事务。

| 场景 | 调用 |
|---|---|
| 评论发表 / 编辑 | `plainText.toPlainText(resolved.body(), Set.copyOf(resolved.mentionedUserIds())).value()`，名单是这一版正文 @ 到的人 |
| 有帮助点评论 | `plainText.toPlainText(comment.body(), Set.copyOf(comment.mentionedUserIds()))`；`CommentRecord` 新增派生方法 `mentionedUserIds()`（`MentionSyntax.mentionedIds(body)`，`MentionSyntax` 不在讨论的公开包里，由读模型代为暴露） |
| 有帮助点投稿经验 | 有正文时 `plainText.toPlainText(body, Set.of())`（经验不解析 @）；没有正文为 `null` |

全文不截断；截多长由消费方按渠道定（PAP-128 交接笔记的截断规则）。

### 4.6 有帮助点投稿经验：经验的结果与正文

`helpful.marked` 的目标是投稿经验时，条目第二行要写这条经验的摘录，没有正文时写结果行 `大修 → 录用 · 审稿 6 周`（设计简报 §3.2）。正文与结果在 `post` 子域，`reaction` 现在只读登记表里经验那一行（作者、匿名、状态）。

`post` 的读端口新增

```java
/// 一条投稿经验的结果与正文，给别的子域带进事件。已删除的也返回。
Optional<ExperienceDigest> findDigest(long postId);
```

`ExperienceDigest(ExperienceOutline outline, String body)`、`ExperienceOutline(FirstDecision firstDecision, FinalOutcome finalOutcome, int firstRoundDays, Integer totalDays)` 放 `domain.post.model.read`。`reaction` 只把结果原样放进事件，不读里面的枚举（`post.model.enums` 不是公开包），枚举转字符串在 boot 映射里做。结果给天数而不是周数：周数是展示规则（门户满 28 天才写「约 N 周」、四舍五入），由消费方算。

**子域依赖**（`InteractionArchitectureTest`）：`reaction → post` 从 `allowingSubscription` 升为 `allowingDependency`。读对方公开的读端口与 `reaction → discussion` 已有的做法一致；`post` 不依赖 `reaction` 的 Java 类型（经验的有帮助数按 SQL 读统计表），不成环。

### 4.7 有帮助事件只在真写入时发

- 点：事务里 `helpful.insert(...)` 返回 `true` 之后发 `HelpfulMarkedEvent`。重复点（事务外判过已点直接返回、或事务里插入冲突）都不发。所在对象、经验结果与纯文本在事务外解析（只读本地库），由 `ReactionTargets.describe` 给出。
- 取消：事务里 `helpful.delete(...)` 返回有值之后发 `HelpfulUnmarkedEvent`，不外送（2.4）。

### 4.8 boot 装配

`dev.patra.interaction.config.InteractionEventsConfiguration`：

```java
@Bean
TopicExchange interactionEventsExchange() {
  return ExchangeBuilder.topicExchange(InteractionExchange.NAME).durable(true).build();
}

@Bean
EventExternalizationConfiguration eventExternalizationConfiguration() {
  return EventExternalizationConfiguration.externalizing()
      .selectByType(EXTERNALIZED::contains)               // 五个领域事件类
      .route(CommentPublishedEvent.class, e -> target(CommentPublished.ROUTING_KEY))
      .mapping(CommentPublishedEvent.class, IntegrationEventMapper::commentPublished)
      // …其余四种同样写
      .build();
}
```

- 映射写在 `IntegrationEventMapper`（boot，纯静态方法，只拷字段：值对象拆成基本类型，枚举转 `name()`），单测逐字段核对。
- 交换机由发布方与消费方各声明一次（同名同参数是幂等的），谁先部署都行。
- 不配消息头（`headers(...)` 在 Modulith 2.1.2 默认外送路径上会被丢掉，PAP-137 README）。
- 完成模式用模块默认的 `DELETE`。
- boot `build.gradle.kts`：加 `patra-tool-events`；`patra-interaction-api` 从 `testImplementation` 改为 `implementation`（映射要用）。改了构建脚本，重跑 `./gradlew dumpModuleGraph`。
- RabbitMQ 连接与密码已由 PAP-138 写进 Nacos（`patra-common.yaml`、`patra-interaction.yaml`）和 apps compose，本 Issue 不动。

### 4.9 Flyway

- `db/migration/events/V20261031<时分秒>__create_event_publication.sql`：照抄 PAP-137 的模板（Modulith JDBC 第二版表结构，表名与列名不能改），放 `public` schema。interaction 的 schema 白名单测试只看 schema，不受影响。
- `db/migration/discussion/V20261031<时分秒>__add_comment_version.sql`：4.4 的列。
- 版本号取写脚本时的当前时间（14 位时间戳）。

## 5. 事件契约（给 PAP-143 / PAP-144 / PAP-146）

本节同步写进共享笔记 `PAP-141-events-contract.md`。

### 5.1 通用

- 交换机 `interaction.events`（topic，持久化），常量 `InteractionExchange.NAME`。
- 路由键 = 事件类型，常量是各 record 的 `ROUTING_KEY`。外送的只有五个：`comment.published`、`comment.edited`、`comment.deleted`、`post.deleted`、`helpful.marked`。
- 消息体是 JSON，`__TypeId__` 是 api record 的全类名，AMQP `messageId` = `eventId`。时间是 ISO-8601 字符串（UTC，带 `Z`），ID 是 JSON 数字（AMQP 不走 MVC 的 Long 转字符串）。
- **`eventId`**：事件在 interaction 里产生时生成，补投、重投都不变，是消费端去重的键（`ProcessedEventPort.markProcessed(队列, eventId)`）。
- **至少一次**：同一个事件可能到两次；不保证顺序。
- **trusted-packages**：一个监听类用多个 `@RabbitHandler` 时，`patra.events.consumer.trusted-packages` 要写这三个包（按包名精确匹配）：`dev.patra.interaction.api.discussion.event`、`dev.patra.interaction.api.post.event`、`dev.patra.interaction.api.reaction.event`。嵌套的 `SubjectContext`、`ExperienceOutcome` 按字段类型反序列化，不用列。
- **五个路由键都必须有队列绑定**才能上线（2.4）。
- 枚举一律是字符串（枚举常量名），消费方按字符串比较，遇到不认识的值别抛异常。
- 对象的展示信息（key、标题、期刊、是否可用）不在事件里：按 `subjectId` 调 interaction 的内部接口「按 ID 取对象视图」现查（PAP-143 写）。

### 5.2 共用的嵌套 record

`api.subject.event.SubjectContext`：讨论所在的对象（有帮助事件里是被点内容所在的对象）。

| 字段 | 类型 | 含义 |
|---|---|---|
| `subjectId` | long | 登记表本地 ID，永不变；`post.deleted` 按它清理，读时现查展示信息也用它 |
| `type` | String | `PUBLICATION`（文献）/ `POST`（投稿经验） |
| `ownerId` | Long，可空 | 投稿经验的作者；文献为空 |
| `ownerAnonymous` | boolean | 投稿经验是匿名发布的；文献为 `false` |

`api.reaction.event.ExperienceOutcome`：投稿经验的结果，只出现在 `helpful.marked`（目标是经验时）。

| 字段 | 类型 | 含义 |
|---|---|---|
| `firstDecision` | String | `DESK_REJECT`、`REJECT_AFTER_REVIEW`、`MAJOR_REVISION`、`MINOR_REVISION`、`ACCEPT` |
| `finalOutcome` | String | `ACCEPTED`、`REJECTED`、`WITHDRAWN`、`IN_PROGRESS` |
| `firstRoundDays` | int | 首轮用时（天） |
| `totalDays` | Integer，可空 | 投稿到最终决定的天数；首轮即终局时等于首轮用时；修改类没填最终日期为空 |

首轮决定是 `DESK_REJECT`、`REJECT_AFTER_REVIEW`、`ACCEPT` 时首轮就是终局，门户的结果行不写箭头（`formatOutcomeLine`）；「审稿 N 周」由消费方按天数算（门户满 28 天才写、四舍五入）。

### 5.3 `comment.published` → `CommentPublished`

| 字段 | 类型 | 含义 |
|---|---|---|
| `eventId` | UUID | 见 5.1 |
| `commentId` | long | 新评论 |
| `version` | int | 恒为 1 |
| `subject` | SubjectContext | 所在对象 |
| `rootCommentId` | long | 所在楼的根评论（根评论是它自己） |
| `parentCommentId` | Long，可空 | 回复的哪一条；根评论为空 |
| `parentAuthorId` | Long，可空 | 被回复评论的作者；根评论、或回复的是已成占位的根评论时为空 |
| `authorId` | long | 作者 |
| `authorAnonymous` | boolean | 作者是对象的匿名所有者：提醒里触发人显示「匿名作者」 |
| `mentionedUserIds` | List<Long> | 正文 @ 到的正常账号，去重、按第一次出现；可能含作者本人 |
| `plainText` | String | 全文纯文本（PAP-128），`<@ID>` 是真提及；正文只有 Markdown 结构（如单独一个 `-`）没有文字时是空串 `""`，消费方按 isBlank 判 |
| `publishedAt` | Instant | 发表时刻 |

收件人规则（搬自 `NotificationRules`，PAP-143）：先列 `parentAuthorId`（回复），再列 `subject.ownerId`（只在根评论且 `subject.type = POST` 时，评论了你的投稿经验），最后列 `mentionedUserIds`（@）；同一人只取第一次出现的类型；去掉 `authorId`。

### 5.4 `comment.edited` → `CommentEdited`

字段同 5.3，区别：

| 字段 | 类型 | 含义 |
|---|---|---|
| `version` | int | 改完后的版本，≥ 2，同一条评论严格递增 |
| `mentionedUserIds` | List<Long> | 改完后正文 @ 到的人 |
| `addedMentionedUserIds` | List<Long> | 这条评论**第一次** @ 到的人（以前任何一版都没 @ 过）：只给他们发 @ 提醒；去掉作者本人由消费方做 |
| `plainText` | String | 改完后的全文纯文本 |
| `editedAt` | Instant | 编辑时刻（只作展示，比新旧用 `version`），代替 `publishedAt` |

`parentAuthorId` 是编辑时父评论的作者（父评论已成占位时为空）。

### 5.5 `comment.deleted` → `CommentDeleted`

| 字段 | 类型 | 含义 |
|---|---|---|
| `eventId` | UUID | |
| `commentId` | long | 被删的评论 |
| `version` | int | 删除时的版本 |
| `subjectId` | long | 所在对象 |
| `authorId` | long | 作者 |
| `placeholder` | boolean | 留下了占位（有回复）还是整条去掉 |
| `deletedAt` | Instant | |

已是占位再删不发；整条去掉时沿父链级联去掉的占位不发（它们变占位时已经发过）。

### 5.6 `post.deleted` → `PostDeleted`

| 字段 | 类型 | 含义 |
|---|---|---|
| `eventId` | UUID | |
| `postId` | long | 帖子 ID |
| `subjectId` | long | 这条经验**自己**的登记对象（它下面评论的 `subject.subjectId`、点它的 `helpful.marked` 的 `subject.subjectId`），不是所属期刊 |
| `authorId` | long | 作者 |
| `deletedAt` | Instant | |

经验下的评论**不会**随之发 `comment.deleted`（评论没被删）。旧提醒子域的做法是删掉该对象下的全部提醒，PAP-143 / PAP-144 按 `subjectId` 照做。

### 5.7 `helpful.marked` → `HelpfulMarked`

| 字段 | 类型 | 含义 |
|---|---|---|
| `eventId` | UUID | |
| `targetType` | String | `COMMENT` / `POST` |
| `targetId` | long | 评论 ID 或帖子 ID |
| `targetAuthorId` | long | 被点内容的作者 = 收件人（匿名经验也是真实作者） |
| `targetAnonymous` | boolean | 匿名经验，或作者在自己匿名经验下发的评论 |
| `userId` | long | 点的人 |
| `markedAt` | Instant | 点的那一刻（interaction 写库时的时刻，补投不变）；按北京时间切天用它，不用消费时间 |
| `subject` | SubjectContext | 被点内容所在的对象；点经验时是经验自己 |
| `experience` | ExperienceOutcome，可空 | 目标是投稿经验时它的结果；目标是评论为空；目标是经验、但投稿经验表里查不到这条（数据异常）时也为空，消费方不能在 `targetType = POST` 时直接解引用 |
| `targetPlainText` | String，可空 | 被点内容的全文纯文本；没有文字时为空（经验没写正文、正文只有 Markdown 结构、或查不到），摘录行改写结果（设计简报 §3.2）；两个都为空就只写「你的投稿经验」 |

只在真插入了明细时发：同一人对同一目标点、取消、再点，会发两次 `helpful.marked`（两个不同的 `eventId`），按人去重由 PAP-144 做。去处：评论 = 读时现查到的对象 key + `targetId`（`?comment=`）；经验 = `/posts/{targetId}`。

### 5.8 有帮助已取消：不外送

`HelpfulUnmarkedEvent` 只在 interaction 内发布，不进外送配置，消费方不用绑定。产品规则是取消不撤回提醒、人数不减。

### 5.9 乱序与终态（给消费端的判断规则）

| 情形 | 怎么判 |
|---|---|
| 同一事件到两次 | 按 `eventId` 去重 |
| 编辑晚到 | 已存版本 ≥ 事件 `version` 就丢弃（摘录不覆盖）；`addedMentionedUserIds` 的 @ 提醒照常补（新增的 @ 只会在那一次编辑里出现） |
| 编辑先于发表到达 | 发表的 `version` 是 1，比已存的小：照常建收件人的提醒，摘录用已存的较新版本 |
| 删除先于发表 / 编辑到达 | `comment.deleted` 是终态：按 `commentId` 记墓碑，之后到的该评论的发表、编辑都丢弃 |
| 经验删除后，它下面的评论事件晚到 | `post.deleted` 对 `subjectId` 是终态：之后到的 `subject.subjectId` 相同的评论事件不再生成提醒 |
| 有帮助的目标已删除后，`helpful.marked` 晚到 | 目标评论已有墓碑、或 `subject.subjectId` 已有经验墓碑就丢弃 |

## 6. 测试

| 验收（PAP-141） | 测试 |
|---|---|
| 每种事件在事务提交后送达 RabbitMQ | boot IT `InteractionEventsFlowIT`（真 PG + 真 RabbitMQ）：经 HTTP 发表、编辑、删除评论，删除经验，点有帮助，测试队列各收到一条，路由键、`__TypeId__`、`messageId` = `eventId` 都对；登记表随后为空。取消有帮助不产生消息、不留登记行 |
| 业务事务回滚时不外送 | 同 IT：同步订阅方抛异常让发表回滚（`DiscussionEventRecorder.FAIL_MARKER`），登记表为空、测试队列等一段时间也没收到 |
| 发出的 JSON 能反序列化成 api 里的 record | 同 IT：每条原始消息用普通 `JsonMapper` 读成 api record，与期望值整体比较（含嵌套 record、时间、可空字段） |
| 正文转纯文本、保留 `<@ID>` | 同 IT：正文含 @ 与链接，`plainText` 与 PAP-128 规则一致、`<@ID>` 保留；app 单测核对调用参数 |
| 事件 ID 随领域事件生成、补投不变 | 同 IT：`rabbitmqctl stop_app` 后发表，登记行留着；`start_app` 后跑补投，收到的 `eventId` 与登记行里领域事件的 `eventId` 相同；EventSerializer 往返测试（五个外送的领域事件经 Modulith 实际用的序列化器写进、读回，与原值相等） |

单测（TDD 的红绿步骤落在这些上）：

- `CommentAggregateTest`：编辑加版本，5 分钟内原地替换也加；正文没变不加。
- `PublishCommentHandlerTest` / `EditCommentHandlerTest`：事件带 `eventId`、版本、纯文本。
- `DeleteCommentHandlerTest`、`DeleteExperienceHandlerTest`：事件带 `eventId`（评论删除还带版本）。
- `MarkHelpfulHandlerTest`：插入成功才发，重复点不发；评论目标带评论纯文本，经验目标带经验正文纯文本与结果，没正文时纯文本为空。
- `UnmarkHelpfulHandlerTest`：删到才发。
- `ReactionTargetsTest`：新的 `describe`。
- `IntegrationEventMapperTest`（boot）：五种映射逐字段，`eventId` 原样，枚举转名字，可空字段。
- `InteractionEventsConfigurationTest`（boot）：五个事件被选中、路由键对；`HelpfulUnmarkedEvent` 与无关对象不选中。

infra IT：`CommentRepositoryAdapterIT`（版本落库与递增）、`PostReadAdapterIT`（`findDigest`）、`DiscussionMigrationIT`（列与约束）、`InteractionMigrationIT`（`event_publication` 在 public）。

架构测试：`InteractionArchitectureTest` 的依赖边（4.6）。

IT 底座：`InteractionFlowITSupport` 加 `RabbitMQContainerInitializer`，导入一个测试配置声明测试队列 `interaction.it.events` 绑定五个路由键（不绑定的话，其他流程测试发的事件都会被退回、在登记表里积着）；每例前清空测试队列与 `public.event_publication`（`TRUNCATE` 只覆盖子域 schema）。

## 7. 上线步骤（追加到 `docs/patra/runbooks/v0.16-notification-go-live-runbook.md`）

1. 部署顺序：通知服务（声明队列、绑定**五个**路由键）先于 interaction。interaction 先上也不丢事件，但没有绑定的路由键会让登记行每分钟补投、日志里持续报「没有队列可投」。
2. interaction 部署时 Flyway 建 `public.event_publication`、给 `discussion.comment` 加 `version` 列（已有评论为 1）。
3. xxl-job-admin 给 interaction 执行器登记 `eventPublicationResubmitJob`：CRON `0 * * * * ?`，路由「第一个」，阻塞处理「丢弃后续调度」，超时 60 秒，失败重试 0 次。
4. 验证：发一条回复后 interaction 库 `SELECT count(*) FROM event_publication;` 为 0；RabbitMQ 管理界面 `interaction.events` 的 Message rates 有发布。

## 8. 给后续 Issue 的约束

- 上线后要改外送领域事件的类名或字段（包括本 Issue 新增的），先确认 interaction 的 `event_publication` 是空的（PAP-137 README）。
- 集成事件 record 的字段只增不改名；要删字段先确认消费端不再读。
- 要外送 `HelpfulUnmarkedEvent` 时：先有消费方绑定，再按它的需要补字段、加进外送配置。
- PAP-143 删 `notification` 子域时：同步监听器 `CommentNotificationListener`、`PostNotificationListener` 一起删；`allowingSubscription("notification", …)` 两条随之删。（已完成；领域事件里只为旧监听器留的 `body` 也已删除，见 4.3。）
- 新增外送事件：照 4.8 在 `InteractionEventsConfiguration` 里选中、路由、映射，api 里加 record 与 `ROUTING_KEY`，通知服务先绑定。

## 9. 拍板记录

**编排会话代用户拍板（2026-10-31）**

1. 事件不带对象展示快照；`SubjectContext` 只留 `subjectId`、`type`、`ownerId`、`ownerAnonymous`。展示信息由通知服务读时调 interaction 内部接口现拼（PAP-143 写）。决策 A 的「不回头调 interaction」只针对计算收件人。
2. 评论加单调递增的整数版本（发表 = 1，每次编辑 + 1，含 5 分钟内），发表、编辑、删除事件都带。
3. 收件人规则要的字段一个不裁；评论删除、经验删除都带 `subjectId`。
4. `helpful.marked` 带发生时刻、目标纯文本全文、经验结果、对象 ID 与类型；`helpful.unmarked` 不外送，领域事件照留。

**本 spec 自己定的（理由见各节）**

1. 评论版本用新增的整数列，不用编辑时间、不用修订号（2.2、4.4）。
2. 经验结果只放 `helpful.marked`：它在那里是摘录的替身（内容），不是「在哪」；评论事件不带，与拍板 1 同一原则，邮件「在哪」行要用结果时同样读时现查（4.6）。
3. `reaction → post` 升为依赖，读 `PostReadPort.findDigest`（4.6）。
4. 路由键用 `post.deleted`（不叫 `experience.deleted`），与 `SubjectType.POST` 和门户路径一致（4.2）。
5. 集成事件里的枚举一律用字符串（5.1）。
6. 结果给天数不给周数，周数是展示规则（4.6）。
7. `event_publication` 放 `public`，迁移脚本放 `db/migration/events/`（4.9）。
8. `HelpfulUnmarkedEvent` 只带目标与取消的人，不为没有订阅方的事件多读目标（4.3）。
9. `ExperienceOutcome` 放 `api.reaction.event`（不放 `api.post.event`）：api 层的子域包互不公开（`SubdomainArchitectureRules`），只有 `HelpfulMarked` 用它。
10. 集成事件放 `api.{子域}.event`：包模板原来只认 `endpoint`、`dto`，编排会话在 `d3644fde4` 统一放开（PAP-141 与 PAP-104 共用）。
11. 被点内容转出来没有文字（空白）记为 `null`，消费方据「没有正文」退回写结果行；评论事件的 `plainText` 保持原样（可能是空串），契约写明按 isBlank 判（独立评审 M3）。
12. 外送映射要解引用的字段（对象、对象种类、目标种类、经验结果的两个枚举）在领域事件与 `ExperienceOutline` 的紧凑构造器里必填：哪个发布点漏传，在发布时就让事务回滚，而不是留下一条永远发不出去的登记行（独立评审 M2，反目标第 3 条）。
