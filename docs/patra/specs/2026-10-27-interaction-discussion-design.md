# interaction 讨论子域工程设计（PAP-121）

> **Issue**：[PAP-121](https://linear.app/papertrace/issue/PAP-121)（GitHub #298），Linear Project P-PAP-11 v0.14 Discussion，Milestone BE Ready
> **版本**：[v0.14 Discussion](../release-specs/v0.14-discussion.md)（2.1.1 文献讨论、2.1.4 提醒规则、2.1.5 账号状态；决策 A、C、E、F）
> **前序**：[底座扩展（PAP-120）](2026-10-27-interaction-foundation-extension-design.md) 第 12 节的扩展点、[identity 提及支撑（PAP-118）](2026-10-27-identity-mention-support-design.md) 第 10 节、[identity 对外契约（PAP-108）](2026-10-24-identity-external-contract-design.md) 第 5.5 节、设计简报 `patra-portal/docs/design-briefs/v0.14/discussion.md`（§3.1 ~ §3.12、§3.20、§4.1 ~ §4.2、附录 A「PAP-121」）
> **下游**：PAP-123（提醒）只经第 7 节的事件契约订阅；PAP-124（讨论区前端）、PAP-125（经验页回复区）用第 6 节的接口
> **日期**：2026-10-27
> **状态**：已定稿（2026-10-27）：第 12 节四项均由用户拍板，第 11 节由我定的点经用户认可

## 1. 要解决的问题

文献页要能讨论一篇文献，投稿经验下要能追问和回复（spec 2.1.1、2.1.2「回复」）。两处是同一套规则，都放在 interaction 的新子域 `discussion`：

1. 评论挂在对象上：文献（`PUBLICATION`）和投稿经验（`POST`）。能力矩阵允许 `DISCUSS` 的对象才能评。
2. 数据按树存（父评论 + 根评论），界面只显示两层：每楼根评论下，整楼回复按时间平铺；回复的不是根评论时标「回复 @X」。
3. 正文是 Markdown 原文，`@用户名` 由服务端解析成 `<@用户ID>`；修改历史公开；删除时有回复的留占位。
4. 投稿经验下，所有者的评论标「作者」；匿名所有者对别人显示「匿名作者」，接口里不出现他的用户 ID。
5. 写入评论的同一个事务里发布进程内事件，提醒子域（PAP-123）订阅后算收件人、写提醒。

## 2. 范围

**做**：

- `discussion` schema：评论表、修改历史表（第 4 节）。
- 发表根评论与回复、编辑、删除、取编辑原文（第 5、6 节）。
- 读：讨论列表（每页 20 楼，每楼带前 3 条回复）、楼内更多回复、单楼视图、对话链、修改历史（第 6.1 节）。
- @ 解析与联想（第 5.4 节、第 6.3 节）。
- 「作者」标记、匿名所有者遮蔽、「这条是我发的」、名片可见性（第 5.7、5.8 节）。
- 三个进程内事件：评论已发表、评论已编辑、评论已删除（第 7 节）。
- 测试、README、v0.14 上线 runbook 中本子域的一节、共享交接笔记 `interaction-PAP-121-handoff.md`。

**不做**：

- 提醒本身（PAP-123）。本 Issue 只发布事件，并用一个测试用的订阅方证明「订阅方失败时评论回滚」。
- 投稿经验子域（PAP-122）。经验下的讨论只靠登记表的 `POST` 行（所有者、匿名标记、状态），测试里经底座网关直接登记。
- Markdown 渲染、PMID / DOI 自动链接：由门户按白名单渲染（决策 C），后端只校验字数和 @ 人数。
- 点赞、计数、热度排序、审核、举报（spec 第 3 节 A、D）。
- 注销清理：讨论内容保留（决策 H），不登记 `UserDataCleanupGateway`。

## 3. 已定的前提

| 来源 | 内容 |
|---|---|
| spec 2.1.1 | 树存、两层展示、「回复 @X」、「查看对话」；根评论正序 / 最新，楼内正序；Markdown 子集，≤ 5,000 字素且限字节；一条最多 @ 10 人，完整用户名能 @ 到任何正常账号，90 天内旧名能 @ 到原用户，解析不到的保持纯文本；作者可编辑，修改历史公开，发布后 5 分钟内的修改不记版本；有回复的删除留占位，没有回复的整条去掉；「作者」标记；资格：登录、邮箱已验证、状态正常；频率：10 秒、每天 100 条；永久链接与单楼视图；匿名访客可读；不显示计数 |
| spec 2.1.2 | 匿名经验：接口不向他人返回作者 ID；作者在这条经验下的回复也显示「匿名作者」；匿名作者不进 @ 联想的参与者 |
| spec 2.1.4 | 提醒：回复我的、@ 我的；不提醒自己；同一条里既回复又 @ 同一人只发一条；编辑后只对新增的 @ 发提醒；匿名作者触发的提醒显示「匿名作者」 |
| spec 2.1.5 | 注销后内容保留、作者显示「已注销用户」、@ 他的地方显示占位不暴露用户名；冷静期、封禁不能发表；任何表只存用户 ID，正文里的 @ 存 `<@用户ID>` |
| 决策 A | 子域 `discussion`、schema `discussion`；评论写入时同事务发布进程内事件；提醒只依赖事件契约；评论、修改历史表不分区 |
| 决策 C | 存 Markdown 原文；客户端只发 `@用户名`，interaction 写入时调 identity 批量解析，只把正常账号换成 `<@用户ID>`，超过 10 人拒绝；编辑时由服务端把标记换回当前用户名；联想由 interaction 提供（要求登录）：参与者在前（不含匿名作者），再并上 identity 前缀联想 |
| 决策 E | 资格与频率走底座的发表守卫 |
| 决策 F | 首屏由服务端取，匿名访客可读，已登录带会话拿「这条是我发的」与主页可见性；作者和被 @ 的人一次批量向 identity 取齐；永久链接打开单楼视图 |
| PAP-120 交接 | `resolveForInteraction`（事务外）、`requireEligible`（事务外）、`admit`（事务内第一个写操作之前）的用法；`SubjectView.owner()`；事件 record 放 `domain.discussion.event`，字段只用 JDK 与底座公开类型；同步 `@EventListener`；新迁移时间戳晚于 `20261027120100`；规则 22b：四层都要有包 |
| PAP-118 交接 | `UserEndpoint.suggest`（前缀原文原样传、带访客 ID、`limit` 1 ~ 20、已排除访客本人）与 `resolveByUsernames`（≤ 100 个原文、回显原文、四种状态都返回）；@ 人数按解析出的不同用户 ID 计 |
| PAP-108 第 5.5 节 | `profileVisible` 为假时，不得把这个人的 `username`、`avatarUrl` 放进给这位访客的响应 |
| 用户拍板（2026-10-27，与 PAP-122 统一） | ① 评论作者的主页对访客不可见时，不返回 `@用户名` 和头像，沿用 PAP-108 的名片规则；设计简报 §3.20「封禁用户 `@用户名` 照常」与此冲突，以本条为准。② 冷静期、被封禁、邮箱未验证的账号不能编辑已发表的评论；删除不查资格，随时能删 |
| 设计简报附录 A「PAP-121」 | 每页 20 楼，每楼根评论 + 前 3 条回复 + 「还有更多回复」布尔值（不给条数）；楼内回复每次 ≤ 50、游标；单楼按评论 ID 取整楼并带目标，目标不存在可区分；对话链含占位；修改历史最新在前；联想参与者 ≤ 6、合计 ≤ 8；写接口原因码分开；成功返回完整新条目 |

## 4. 数据模型

迁移放 `patra-interaction-infra/src/main/resources/db/migration/discussion/`：

- `V20261027161210__create_discussion_schema.sql`：`CREATE SCHEMA discussion;`
- `V20261027161220__create_discussion_tables.sql`：下面两张表。（版本号避开 PAP-122 的 `post` 迁移，写进交接笔记）

### 4.1 `discussion.comment`

| 列 | 类型 | 说明 |
|---|---|---|
| `comment_id` | `BIGINT` PK | Snowflake |
| `subject_id` | `BIGINT NOT NULL` | `subject.registry` 的本地 ID；不建跨 schema 外键（README 第 4 节） |
| `root_id` | `BIGINT NOT NULL` | 所在楼的根评论；根评论等于自己 |
| `parent_id` | `BIGINT NULL` | 回复的是哪一条；根评论为空 |
| `author_id` | `BIGINT NOT NULL` | 作者用户 ID |
| `state` | `VARCHAR(16) NOT NULL` | `ACTIVE` / `DELETED`（占位） |
| `body` | `TEXT NULL` | Markdown 原文，@ 存 `<@用户ID>`；占位为空 |
| `mentioned_user_ids` | `BIGINT[] NOT NULL DEFAULT '{}'` | 这条评论**曾经** @ 到过的全部用户 ID（只增不减），用来算编辑时「新增的 @」（第 5.4.6 节）；占位清空 |
| `created_at` | `timestamptz(6) NOT NULL` | 发表时刻 |
| `revised_at` | `timestamptz(6) NULL` | 当前版本由一次「记版本的编辑」产生的时刻；为空 = 没有修改记录 |
| `deleted_at` | `timestamptz(6) NULL` | 变成占位的时刻 |

约束（名字要在测试里断言，见坑清单「PG CHECK 遇 NULL 放行」）：

```sql
CONSTRAINT ck_comment_state      CHECK (state IN ('ACTIVE', 'DELETED')),
CONSTRAINT ck_comment_tree       CHECK ((parent_id IS NULL) = (root_id = comment_id)),
CONSTRAINT ck_comment_body       CHECK ((state = 'ACTIVE') = (body IS NOT NULL)),
CONSTRAINT ck_comment_deleted_at CHECK ((state = 'DELETED') = (deleted_at IS NOT NULL)),
CONSTRAINT fk_comment_root   FOREIGN KEY (root_id)   REFERENCES discussion.comment (comment_id),
CONSTRAINT fk_comment_parent FOREIGN KEY (parent_id) REFERENCES discussion.comment (comment_id)
```

- 两个自引用外键是树完整性的兜底：还有回复的评论删不掉（只能变占位），根评论在楼里还有回复时删不掉。删除按「先子后父」进行（第 5.6 节），不会撞上。根评论引用自己，外键在语句结束时检查，插入时成立。
- 三个 CHECK 的每一项两边都不会是 NULL（`root_id`、`comment_id`、`state` 非空），不会被 NULL 放行。

索引：

```sql
CREATE INDEX idx_comment_roots        ON discussion.comment (subject_id, created_at, comment_id) WHERE parent_id IS NULL;
CREATE INDEX idx_comment_thread       ON discussion.comment (root_id, created_at, comment_id);
CREATE INDEX idx_comment_parent       ON discussion.comment (parent_id) WHERE parent_id IS NOT NULL;
CREATE INDEX idx_comment_participants ON discussion.comment (subject_id, author_id, created_at) WHERE state = 'ACTIVE';
```

| 索引 | 服务 |
|---|---|
| `idx_comment_roots` | 讨论列表的根评论分页（正序正扫、最新倒扫） |
| `idx_comment_thread` | 每楼前 3 条回复、楼内更多回复、单楼整楼 |
| `idx_comment_parent` | 删除时「有没有直接回复」、外键检查 |
| `idx_comment_participants` | @ 联想的「本讨论参与者」（最近发言在前） |

### 4.2 `discussion.comment_revision`

| 列 | 类型 | 说明 |
|---|---|---|
| `comment_id` | `BIGINT NOT NULL` | 外键 → `discussion.comment`，`ON DELETE CASCADE` |
| `revision_no` | `INTEGER NOT NULL` | 1 是最初版本，依次递增；`CHECK (revision_no > 0)` |
| `body` | `TEXT NOT NULL` | 这一版的原文（存储形式，含 `<@用户ID>`） |
| `version_at` | `timestamptz(6) NOT NULL` | 这一版成为当前版本的时刻：第 1 版是发表时刻，之后是那次编辑的时刻 |

主键 `(comment_id, revision_no)`。表里只存**被替换下来的旧版本**，当前版本在 `comment.body`。

### 4.3 为什么这样切

- **`root_id` 存自己**：取整楼就是 `root_id = ?`，根与回复一条索引；`ck_comment_tree` 让「根评论」只有一种写法。
- **不分区**（决策 A）：按对象读，量很小。
- **当前版本放主表、旧版本放历史表**：列表只读主表；`revised_at` 一列同时回答「有没有修改记录」和「当前版本是什么时候产生的」，不用数历史表。
- **`mentioned_user_ids` 只增不减**：编辑时「新增的 @」= 这次正文里的 @ 减去这条评论曾经 @ 过的人。先删掉 @A、再加回来，A 不会再收到一次提醒；反复编辑不能拿来骚扰别人（编辑不限频率）。
- 不存用户名、昵称、头像（R6）；不对 `subject.registry` 建外键（README 第 4 节）。

## 5. 规则

### 5.1 对象

- 对象由 `(type, key)` 指定：`type` 是 `SubjectType.slug()`，本版能讨论的是 `publication`（key 是 catalog 文献 ID）和 `post`（key 是帖子 ID）。`journal` 不能讨论，返回 422（底座的 `SubjectCapabilityNotSupportedException`）。
- **写**（发表、回复）经 `SubjectGateway.resolveForInteraction(type, key, DISCUSS)`，事务外调用：文献第一次被评论时在这一步登记；经验已删除时它抛 `SubjectNotFoundException.unavailable`（404，`SUBJECT_UNAVAILABLE`），直接传播，门户据此显示 `composer.error.object-gone`。
- **读**不触发登记：`SubjectReadPort.findIdsByKeys` 查本地，查不到就是还没人讨论过，回空列表；查到了再 `findByIds` 取视图（状态、所有者）。对象 `UNAVAILABLE`（经验已删除、文献已下线）时读接口回 404 `SUBJECT_UNAVAILABLE`：spec 2.1.2「删除后……下面的回复不再展示」。
- 评论级的接口（按评论 ID）由评论的 `subject_id` 取对象视图，规则同上。
- 读写都先在本地校验 `type`、能力、key 的格式（`type.parseKey`），格式不对 422，不发远程调用。

### 5.2 树与两层

- 根评论：`parent_id` 为空，`root_id = comment_id`。回复：`parent_id` 是被回复的那条，`root_id` 取父评论的 `root_id`。三层以上的回复都在同一楼里。
- 列表里楼内回复一律按 `(created_at, comment_id)` 正序平铺。
- 「回复 @X」：只在父评论**不是**根评论时给出（第 6.4 节 `replyTo`）。
- 父评论必须属于同一个对象，否则 422（客户端的程序错误）。
- **能不能回复**：父评论是正常评论，或者是本楼的根评论（即使根评论已是占位）。根评论变占位后，这一楼仍能「回复这一楼」（简报 §4.2 `composer.error.target-gone` 退回的就是这个）；父评论是楼里一条已删除的回复（占位或已整条去掉）→ 409 `REPLY_TARGET_GONE`。

### 5.3 正文

对客户端发来的原文（含 `@用户名`）：

1. 换行统一：`\r\n`、`\r` → `\n`。
2. NFC 规范化，去掉首尾的空白与空格类字符（同 collection 的 `TextRules`）。去完为空 → 422 `body` / `REQUIRED`。
3. 拒绝控制字符（放行 `\n` 与制表符 `\t`）以及 U+2028、U+2029 → 422 `body` / `INVALID_CHARACTER`。
4. 长度：扩展字素簇（`BreakIterator`）≤ 5,000，且 UTF-8 ≤ 20,000 字节（字素上限 × 4，同 collection）→ 否则 422 `body` / `TOO_LONG`。

长度按**原文**算（@ 写成 `@用户名`），与门户编辑器的计数一致；换成 `<@用户ID>` 之后不再算。后端不做 Markdown 校验（图片、标题、HTML 由门户按原文显示，决策 C）。

讨论子域的文本规则是自己的一份（`domain.discussion.policy.CommentText`）：collection 的 `TextRules` 在它的私有包里，跨子域不能用。

### 5.4 @ 提及

#### 5.4.1 识别（`domain.discussion.policy.MentionSyntax`，纯函数）

从左到右扫描规范化后的原文，近似 CommonMark 的行内规则：

| 片段 | 处理 |
|---|---|
| 反斜杠转义：`\` 后跟一个 ASCII 标点 | 两个字符原样保留，不参与识别（`\@chen` 不是提及） |
| 行内代码：N 个反引号开始，到下一串恰好 N 个反引号结束（不跨空行） | 原样保留；里面的 `@` 不是提及，`<@数字>` 不处理（门户不在代码里渲染提及） |
| 链接 `[文字](目标)`、网址 `https?://` 开头到空白或 `<`、`>` 为止 | 里面的 `@` 不是提及（不破坏网址，也不在链接里套链接） |
| 标记 `<@数字>`（规范十进制正整数） | 见 5.4.3 |
| 其余（普通文本）里的 `@名字` | 见下 |

`@名字` 是候选，当且仅当：

- `@` 前面是开头，或者一个既不是字母、数字（Unicode），也不是 `_`、`@` 的字符（空白、标点、符号都算）。邮箱 `a@b.com`、`你好@chen` 都不是提及，与简报 §3.6「`@` 前面是行首、空白或标点」一致。
- `@` 后面贪婪取 `[A-Za-z0-9_-]+`，去掉末尾的 `_`、`-`（它们留在正文里），剩下的部分符合用户名语法：3 ~ 30 个字符、`[A-Za-z0-9]([A-Za-z0-9]|[_-][A-Za-z0-9])*`、不全是数字（identity `Username` 的规则，不分大小写）。不符合的原样保留，不发给 identity。

#### 5.4.2 解析

1. 候选按原文去重（`Chen-Yu` 与 `chen-yu` 是两个原文）。超过 100 个 → 422 `body` / `TOO_MANY_MENTIONS`（不调 identity；正常文字里几乎不可能出现）。
2. 有候选时调一次 `resolveByUsernames`（事务外）。identity 已处理当前名与 90 天保留期内的旧名。
3. 结果里 `accountStatus == ACTIVE` 的，把每一处 `@名字` 整段（含 `@`）换成 `<@用户ID>`；其余（不存在、旧名已过期、封禁、冷静期、已注销、不合语法）原样保留。
   - **编辑时的承接**：解析到的用户 ID 已在**当前版本**的提及里时，不论他现在是什么状态都换成标记。作者没动的提及不会因为对方后来被封禁、进了冷静期而在一次无关的编辑后变成纯文本（变成纯文本还会把用户名原样露给所有人）。
4. 最终正文里不同的用户 ID 超过 10 个 → 422 `body` / `TOO_MANY_MENTIONS`。@ 自己也算一个人（提醒子域不提醒自己）。

#### 5.4.3 标记 `<@数字>` 的防伪造

客户端只发 `@用户名`，发表和编辑都一样（编辑原文里也没有标记，见 5.4.5）。客户端发来的正文里出现 `<@数字>` 时，一律在 `<` 前加 `\`，变成字面文字 `\<@数字>`，门户按 Markdown 转义显示成 `<@数字>`，不渲染成提及。行内代码里的不处理（门户在代码里本来就不渲染提及）。

所以库里没被转义、不在行内代码里的 `<@数字>`，都是服务端解析出来的，门户可以放心把它们渲染成提及。

#### 5.4.4 存储与读

- `body` 存替换后的原文；读接口原样返回 `body`，并附上「提及实体」（第 6.4 节 `mentions`），门户按实体把标记渲染成 `@用户名`（链主页）、`@昵称`（主页对访客不可见时，不链接）或 `@已注销用户`。
- 实体由 identity 批量摘要取，访客 ID 透传，按第 5.8 节置空用户名。

#### 5.4.5 编辑时把标记换回用户名（决策 C）

`GET /comments/{id}/source`（作者本人）返回编辑原文，标记按决策 C 换回：

- 被 @ 的人有用户名（没注销）→ `@当前用户名`（改过名就是新名字），不看他的主页对作者是否可见：这是作者自己写下的提及，原文里本来就是这个人的用户名。
- 已注销（或 identity 省略）→ 纯文本 `@已注销用户`。保存后它就是普通文字（不合用户名语法，不会被识别），显示上与提及占位相同。

编辑器里因此永远没有 `<@数字>`。保存时按 5.4.2 重新解析，第 3 步的承接保证没改动的提及不会丢。

#### 5.4.6 「新增的 @」

编辑事件里的 `addedMentionedUserIds` = 新正文里的提及 − `mentioned_user_ids`（这条评论曾经 @ 过的人）。之后 `mentioned_user_ids` 并上新正文里的提及。发表时 `mentioned_user_ids` 就是正文里的提及。

### 5.5 编辑与修改历史

- 只有作者能编辑（403）；占位不能编辑（404 `COMMENT_NOT_FOUND`）；对象已失效（经验已删除）不能编辑（404 `SUBJECT_UNAVAILABLE`）。
- 编辑要发表资格（`requireEligible`，用户拍板 ②）：冷静期、被封禁、邮箱未验证的账号不能编辑。编辑会加 @、发出提醒，账号受限的人不该还能改已发表的内容。编辑不调 `admit`（底座：编辑不算发表）。
- 新正文按 5.3、5.4 处理成存储形式后：
  1. 与当前 `body` 逐字相同 → 什么都不写、不发事件，返回当前条目。
  2. `now − created_at ≤ 5 分钟`（含整 5 分钟）→ 原地替换 `body`，不记版本，`revised_at` 不变。
  3. 超过 5 分钟 → 把当前 `body` 存成历史表的下一版（`version_at = revised_at ?? created_at`），`body` 换成新的，`revised_at = now`。
- `edited`（条目上的「已编辑」）= `revised_at` 非空。5 分钟内改过、之后没再改的评论没有「已编辑」。
- 修改历史 = 当前版本（`at = revised_at`）+ 历史表按 `revision_no` 倒序；最后一个是最初版本，`at` 是发表时刻。没有修改记录时只有一个版本（当前即最初，`at = created_at`）。历史公开，匿名访客可读；占位与已整条去掉的评论没有历史（404）。

例：21:03 发表，21:05 改（不记）→ 09:12 改（存第 1 版 = 21:05 改后的正文，`version_at` 21:03）→ 18:40 改（存第 2 版，`version_at` 09:12）。历史：当前（18:40 修改）、09:12 修改、最初版本（21:03 发表）。

### 5.6 删除与占位

- 只有作者能删（403）。不查发表资格（用户拍板 ②）：账号受限的人也能随时删自己的内容。对象已失效也能删。
- 在楼锁里（5.10）：
  1. 这条有直接回复（`parent_id` 是它）→ 变占位：`state = DELETED`、`body = NULL`、`mentioned_user_ids = '{}'`、`deleted_at = now`，删掉它的全部历史版本（内容已撤回，历史也不再公开）。
  2. 没有直接回复 → 整行删除（历史版本随外键级联删除）。
  3. 级联：整行删除后看它的父评论，父评论是占位且已没有直接回复 → 也整行删除，再看上一层，直到遇到正常评论、仍有回复的占位，或者到了根评论之上。根评论占位的最后一条回复被删时，整楼消失。
- 返回 `{ placeholder, removedIds }`：变占位时 `placeholder = true`、`removedIds = []`；整条去掉时 `removedIds` 是这条和级联去掉的占位（门户据此移除，简报 §3.9「由后端判断，界面只按返回显示」）。
- 已经是占位的再删一次 → 200 `{ placeholder: true, removedIds: [] }`（幂等）；已经不存在 → 404 `COMMENT_NOT_FOUND`。
- 删除不退还额度（底座规则）。

### 5.7 「作者」标记、匿名遮蔽、「这条是我发的」

设对象视图的所有者为 `owner`（文献没有所有者，为空）：

| 条目字段 | 规则 |
|---|---|
| `isOwner`（「作者」标记） | `owner != null && author_id == owner.userId` |
| `anonymous` | `owner != null && owner.anonymous && author_id == owner.userId`：所有者在这个对象下的**全部**评论都是匿名的 |
| `author` | `anonymous` 且访客不是作者时为 `null`（不给用户 ID、昵称、头像）；匿名所有者本人看自己的评论时是**他自己的名片**（与 PAP-122 的作者对象一致）；否则是名片（5.8）；identity 省略了这个人时为 `null`（`anonymous: false`，门户按「已注销用户」显示） |
| `mine` | 已登录且 `author_id == 访客`（匿名所有者本人看自己的评论也是 `true`，门户显示「匿名作者（你）」） |
| `replyTo` | 父评论不是根评论时给：父评论是占位 → `{ deleted: true }`；父评论是匿名所有者发的 → `{ anonymous: true }`（访客是所有者本人时也一样：只是告诉他回复的是匿名作者）；否则 `{ author: 名片 }`（identity 省略时 `author` 为 `null`）。父评论是根评论时为 `null` |
| `hasReplies` | 有没有直接回复（`parent_id` 是它的评论）：删除确认的两种文案要它（简报 §3.9）；楼折叠时门户自己推断不出楼里某条回复下面有没有回复 |

- 占位条目只给 `commentId`、`rootId`、`parentId`、`deleted: true`、`hasReplies`、`createdAt`，其余为空、`false` 或空列表。
- 作者对象与 PAP-122 的同形（字段、遮蔽、本人看匿名、identity 省略），经验页上经验署名与回复区由门户的同一套组件渲染。
- 匿名所有者的用户 ID 不出现在任何对他人的响应里：不在 `author`、不在 `replyTo`、不在联想的参与者里（5.9）。他被别人用真实用户名 @ 到时，提及实体是他的真实账号，与匿名经验没有关联（简报 §3.20），不在此列。

### 5.8 名片与可见性

- 作者、`replyTo` 的作者、被 @ 的人，一次批量经 `UserDirectoryPort.findCards(ids, 访客)` 取齐；去重后一次最多 100 个，超过分批（一页 20 楼通常一次就够）。匿名所有者只在访客是他本人时取（就是访客自己的名片）。
- 名片：`userId`、`username`、`displayName`、`avatarUrl`、`avatarPalette`、`state`（`ACTIVE` / `BANNED` / `PENDING_DELETION` / `DELETED`）、`profileVisible`，与 PAP-122 同形。
- **`profileVisible` 为假时 `username`、`avatarUrl` 置空**（PAP-108 第 5.5 节，用户拍板 ①）。主页设为仅自己、封禁、冷静期、邮箱未验证的人，别人看到的是昵称 + 默认头像（色号），没有 `@用户名`、不链主页；被 @ 时显示 `@昵称`、不链接；「回复 @X」写昵称。本人看自己永远可见。设计简报 §3.20「封禁用户 `@用户名` 照常」以这条为准，由 PAP-124 同步。
- **昵称可能就是用户名**：identity 的摘要里，没设昵称的人 `displayName` 回退成用户名，且不看主页是否可见（`UserSummaryReadModel.viewFor`；注册只收用户名，所以大多数人没有昵称）。interaction 原样使用 identity 给的 `displayName`，不另行遮蔽：「不可见」在讨论区的含义是不链主页、不给 `@用户名` 与头像地址，名字本身照常显示。见第 12 节第 3 条。
- 已注销：identity 返回的占位（`username`、`displayName`、`avatarUrl` 为空，`state = DELETED`），门户显示「已注销用户」。identity 省略的 ID（不应发生）：作者与 `replyTo.author` 为 `null`，提及实体 `deleted: true`，门户都按「已注销用户」显示。
- identity 不可用 → 整个读接口 503（`UserDirectoryUnavailableException` 直接传播），门户把讨论区降级为「暂不可用」（简报 §3.21）。

### 5.9 资格与频率

| 操作 | 资格 `requireEligible`（事务外） | 频率 `admit(COMMENT)`（事务内第一个写操作之前） |
|---|---|---|
| 发表根评论、回复 | 要 | 要 |
| 编辑 | 要 | 不要（编辑不算发表） |
| 删除 | 不要 | 不要 |

异常都直接传播（底座第 8.4 节：409 `ACCOUNT_RESTRICTED` / `EMAIL_UNVERIFIED`，429 `TOO_FREQUENT` / `DAILY_LIMIT_REACHED` 带 `Retry-After`，503）。

### 5.10 并发

- **楼锁**：发表回复、删除都先拿事务级 advisory lock `discussion.thread:{rootId}`，在锁里读父评论 / 子评论、判断、写。同一楼的结构变化排队：回复与删除父评论同时发生时，要么回复先提交、删除看到它而变占位，要么删除先提交、回复看到父评论没了而 409。发表根评论不拿楼锁（新楼）。
- **编辑**：`SELECT … FOR NO KEY UPDATE` 锁这一行（不阻塞往这一行下面插回复时外键要的 KEY SHARE 锁），锁里判状态、算版本、写。
- **加锁顺序**：作者锁（`admit`）→ 楼锁 → 行锁。删除只拿楼锁和行锁，编辑只拿行锁，不会成环。
- 外键兜底：并发出错时数据库拒绝孤儿回复，而不是悄悄写进去。

## 6. 接口

网关前缀 `/patra-interaction`，下表省略。ID 在 JSON 里是字符串（`patra-tool-web` 的 Long → String），请求里字符串或数字都收。时间是 UTC ISO 8601。游标是不透明字符串。

### 6.1 读（登录可选；已登录时带会话）

| 方法 | 路径 | 参数 | 响应 |
|---|---|---|---|
| GET | `/discussions/{type}/{key}/threads` | `sort=asc\|latest`（默认 `asc`）、`cursor`、`size` 1 ~ 50（默认 20） | 200 `{ items: [Thread], nextCursor }`；对象还没人讨论过时 `items: []` |
| GET | `/discussions/{type}/{key}/comments/{commentId}/thread` | — | 200 `{ targetId, thread: Thread }`：目标所在的整楼，回复全部给出；目标不在这个对象下或已整条去掉 → 404 `COMMENT_NOT_FOUND`（门户显示 `disc.single.missing`）；目标是占位照常返回 |
| GET | `/comments/{rootId}/replies` | `cursor`、`size` 1 ~ 50（默认 50） | 200 `{ items: [Comment], nextCursor }`，楼内正序；`rootId` 不存在或不是根评论 → 404 `COMMENT_NOT_FOUND` |
| GET | `/comments/{commentId}/conversation` | — | 200 `{ targetId, items: [Comment] }`：从根评论到它的祖先链（含占位），按时间正序 |
| GET | `/comments/{commentId}/revisions` | — | 200 `{ versions: [{ body, at }], mentions: [Mention] }`：最新在前，最后一个是最初版本；占位 → 404 `COMMENT_NOT_FOUND` |

`Thread`：`{ root: Comment, replies: [Comment], hasMoreReplies, repliesCursor }`。列表里 `replies` 是前 3 条回复，`hasMoreReplies` 为真时 `repliesCursor` 是第 3 条之后的游标，交给 `/comments/{rootId}/replies` 取剩下的；单楼视图里 `replies` 是全部，`hasMoreReplies = false`。

排序：根评论按 `(created_at, comment_id)`，`asc` 正序、`latest` 倒序；游标记下一页从哪一条之后开始，删除不会让翻页重复或漏条。

### 6.2 写（需登录，未登录 401）

| 方法 | 路径 | 请求 | 响应 |
|---|---|---|---|
| POST | `/discussions/{type}/{key}/comments` | `{ body, parentId }`（`parentId` 为空 = 根评论） | 201 `Comment`（完整条目，门户直接插入） |
| GET | `/comments/{commentId}/source` | — | 200 `{ body }`：编辑原文（5.4.5）；不是作者 403，占位 404 |
| PUT | `/comments/{commentId}` | `{ body }` | 200 `Comment` |
| DELETE | `/comments/{commentId}` | — | 200 `{ placeholder, removedIds: [] }`（5.6） |

**发表的顺序**（先做本地校验，远程调用都在事务外）：

1. 登录（401）；`type`、能力、key 格式、`parentId` 为正数（422）；正文 5.3（422）；@ 候选 ≤ 100（422）。
2. `requireEligible(作者)` → 自己的名片（409 / 503）。
3. `resolveForInteraction(type, key, DISCUSS)` → `subjectId`（404 / 422 / 503）；`findByIds` 取对象视图（所有者，给事件和响应用）。
4. 有 `parentId` 时预读父评论：不存在 → 409 `REPLY_TARGET_GONE`；不属于这个对象 → 422。
5. 有候选时 `resolveByUsernames`，换成存储形式；提及 ≤ 10 人（422）。
6. 事务：`admit(作者, COMMENT)` → 回复时拿楼锁、重读父评论、按 5.2 判能不能回复（409）→ 插入（`RETURNING`）→ 发布 `CommentPublishedEvent` → 提交。
7. 拼响应：作者名片用第 2 步的；父评论作者与被 @ 的人再批量取一次名片（没有就不取）。

identity 调用：写入前的检查是资格 1 次 + @ 解析 ≤ 1 次（决策 E「每次写入经 identity 回查，和解析 @ 合起来最多两次」）；写完拼响应时，父评论作者与被 @ 的人的名字按决策 F「一次批量取齐」再取 ≤ 1 次（根评论、回复根评论且没有 @ 时不取）。决策 E 管的是写入前的检查，不包括拼响应，见第 12 节第 4 条。

**编辑的顺序**：登录、正文（422）→ 预读评论：不存在或占位 404、不是作者 403 → 对象视图（失效 404）→ `requireEligible` → @ 解析（5.4.2 第 3 步承接当前版本的提及）→ 事务：锁行、重判、5.5 → 发布 `CommentEditedEvent`（正文没变时不发）→ 拼响应。

**删除的顺序**：登录 → 预读：不存在 404、不是作者 403 → 事务：楼锁、锁行、重判、5.6 → 发布 `CommentDeletedEvent`（已经是占位时不发）。

### 6.3 @ 联想（需登录）

| 方法 | 路径 | 参数 | 响应 |
|---|---|---|---|
| GET | `/discussions/{type}/{key}/mention-suggestions` | `q`：`@` 后面已输入的原文，可空 | 200 `{ items: [{ userId, username, displayName, avatarUrl, avatarPalette, group }] }`，`group` 是 `PARTICIPANT` 或 `OTHER` |

1. **参与者**（对象已登记时）：本讨论正常评论的作者，按各自最近一条评论倒序，取最近 50 位；排除访客本人和匿名所有者。对象是**非匿名**所有者的投稿经验时，所有者排在最前面（他不一定评论过，简报 §3.6「经验页另加非匿名的作者本人」；在经验页最常要 @ 的就是作者），同样排除访客本人。
2. 参与者批量取名片（访客 = 当前用户），只留 `state == ACTIVE` 且 `profileVisible` 的（不可见的人不能放出用户名）。
3. `q` 为空：前 6 位参与者，结束。
4. `q` 不为空：参与者里用户名以 `q` 开头、或昵称以 `q` 开头（都按 `Locale.ROOT` 转小写比较）的前 6 位；再调 identity `suggest(q, 访客, min(20, 8 + 已有参与者数))`，按 `userId` 去掉已在参与者里的，补到合计 8 个。
5. 对象 `UNAVAILABLE` → 404 `SUBJECT_UNAVAILABLE`；identity 不可用 → 503（门户显示 `composer.mention.error`）。

identity 的联想结果已排除访客本人、只含主页对访客可见的正常账号（PAP-118）。

匿名所有者只是不进**参与者**（spec 2.1.2）：带查询词时，identity 的全站联想（`OTHER`）照常可能列出他的真实账号。那是全站搜索，与这条匿名经验无关；如果只在他自己的匿名经验下把他从全站结果里剔掉，「缺席」本身反而会暴露谁是匿名作者（实施时由流程测试发现，见第 14 节）。

### 6.4 条目 `Comment`

```json
{
  "commentId": "…", "rootId": "…", "parentId": "…" | null,
  "deleted": false,
  "author": { "userId": "…", "username": "chen-yu" | null, "displayName": "陈宇" | null,
              "avatarUrl": "/users/…/avatar?v=…" | null, "avatarPalette": 5,
              "state": "ACTIVE", "profileVisible": true } | null,
  "anonymous": false,
  "isOwner": false,
  "mine": false,
  "replyTo": { "deleted": false, "anonymous": false, "author": { …名片… } | null } | null,
  "body": "同意 <@42> 的看法……" | null,
  "mentions": [ { "userId": "42", "username": "zhou-min" | null, "displayName": "周敏" | null, "deleted": false } ],
  "hasReplies": false,
  "createdAt": "2026-10-24T06:20:00Z",
  "edited": false
}
```

`author`、`anonymous`、`mine` 三个字段与 PAP-122 的作者对象同形（5.7）。

- `mentions`：`body` 里每个标记一项，按第一次出现的顺序；`username` 按 5.8 置空，`deleted` = 已注销（这时 `displayName` 也为空）。
- 修改历史的 `mentions` 是全部版本里标记的并集。

### 6.5 错误语义

错误码前缀 `INTERACTION`。门户按 `traits[]` 里的原因码与 `errors[]` 里的字段码选文案，不解析 `message`。

| 情况 | HTTP | 原因码（`traits[]`）/ 字段码（`errors[]`） | 默认文案 |
|---|---|---|---|
| 未登录调需登录的接口 | 401 | （`patra-tool-security`） | — |
| `type` 未知、`sort` 未知、`size` 越界、游标不合法、`parentId` 不是正数、父评论不在这个对象下 | 422 | — | 请求参数不合法 |
| 对象不支持讨论（期刊）、key 格式不对 | 422 | — | 底座异常的文案 |
| 正文为空 / 含不允许的字符 / 超长（字素或字节） | 422 | `errors[]`：`body` × `REQUIRED` / `INVALID_CHARACTER` / `TOO_LONG` | 写点什么再发表吧 / 含有不允许的字符 / 最多 5,000 字 |
| @ 超过 10 人（或候选超过 100 个） | 422 | `errors[]`：`body` × `TOO_MANY_MENTIONS` | 一条评论最多 @ 10 人 |
| 账号状态异常 / 邮箱未验证 | 409 | `ACCOUNT_RESTRICTED` / `EMAIL_UNVERIFIED`（底座） | 底座文案 |
| 10 秒内第二条 / 当天第 101 条 | 429 + `Retry-After` | `TOO_FREQUENT` / `DAILY_LIMIT_REACHED`（底座） | 底座文案 |
| 回复的评论已删除（占位、已整条去掉） | 409 | `REPLY_TARGET_GONE` | 要回复的评论已被删除 |
| 对象已删除 / 已失效（发表、编辑、读） | 404 | `NOT_FOUND`、`SUBJECT_UNAVAILABLE`（底座） | 投稿经验不存在或已被删除 |
| 对象不存在（发表时，底座解析） | 404 | `NOT_FOUND` | 文献不存在或已被删除 |
| 评论不存在、已整条去掉、不在这个对象下（单楼）、占位（编辑、取原文、历史） | 404 | `NOT_FOUND`、`COMMENT_NOT_FOUND` | 这条评论已删除，或者不在这里的讨论中 |
| 编辑、删除、取原文别人的评论 | 403 | `FORBIDDEN` | 只能修改自己的评论 |
| catalog 不可用（文献首次被评论） | 503 | —（底座） | 文献信息暂时无法获取，请稍后再试 |
| identity 不可用 | 503 | `DEP_UNAVAILABLE`（底座） | 用户信息暂时无法获取，请稍后再试 |

- 讨论自己的原因码放 `domain.discussion.exception.DiscussionErrorTrait`：`COMMENT_NOT_FOUND`、`REPLY_TARGET_GONE`（写法同 `CollectionErrorTrait`：错误引擎映射状态码时跳过它，`traits[]` 里带上）。
- 「回复目标已删除」用 409 而不是 404：请求的讨论还在，是客户端看到的楼已经过时；门户据此把回复框改成「回复这一楼」（简报 §4.2）。
- 不需要 `ErrorMappingContributor`：全部是带标准特征的领域异常，由 `DefaultErrorResolutionEngine` 自动映射。

## 7. 进程内事件契约（给 PAP-123）

### 7.1 包、写法、发布

- 包 `dev.patra.interaction.domain.discussion.event`；record，名字以 `Event` 结尾，纯 Java；参数 ≥ 5 个用 `@Builder`，列表字段在紧凑构造器里 `List.copyOf`（null 视为空）。
- 字段只用 JDK 类型与底座公开类型（`SubjectId`、`SubjectType`、`SubjectOwner`）。
- discussion 的 app 层在写入事务里、写完库之后调 `ApplicationEventPublisher.publishEvent(event)`。订阅方用同步 `@EventListener`（同一线程、同一事务），抛出异常时整个事务回滚：评论、发表计数、订阅方自己的写入都不落库。**不要用 `@TransactionalEventListener`**（提交后才执行，提醒会丢）。

### 7.2 `CommentPublishedEvent`（发表根评论或回复）

| 字段 | 类型 | 说明 |
|---|---|---|
| `commentId` | `long` | 新评论 |
| `subjectId` | `SubjectId` | 对象（永久链接要的 key 由订阅方读时经 `SubjectReadPort` 取：文献的 key 在 catalog 重导后会变） |
| `subjectType` | `SubjectType` | `PUBLICATION` / `POST` |
| `subjectOwner` | `SubjectOwner` | 对象所有者与是否匿名；文献为 `null` |
| `rootCommentId` | `long` | 所在楼的根评论（根评论是它自己） |
| `parentCommentId` | `Long` | 回复的是哪一条；根评论为 `null` |
| `parentAuthorId` | `Long` | 被回复评论的作者；根评论、或回复的是已成占位的根评论（「回复这一楼」）时为 `null` |
| `authorId` | `long` | 作者 |
| `authorAnonymous` | `boolean` | 作者是对象的匿名所有者：提醒里的触发人要显示「匿名作者」 |
| `mentionedUserIds` | `List<Long>` | 正文 @ 到的正常账号，去重，按第一次出现的顺序；可能含作者本人 |
| `body` | `String` | 存储形式的正文（Markdown，含 `<@用户ID>`），摘录由订阅方自己截 |
| `publishedAt` | `Instant` | 发表时刻（与库里的 `created_at` 相同） |

### 7.3 `CommentEditedEvent`（编辑，正文确有变化时）

| 字段 | 类型 | 说明 |
|---|---|---|
| `commentId`、`subjectId`、`subjectType`、`subjectOwner`、`rootCommentId`、`parentCommentId`、`parentAuthorId`、`authorId`、`authorAnonymous` | 同 7.2 | `parentAuthorId` 取编辑时父评论的作者（父评论已成占位时为 `null`），给「同一条里既回复又 @ 同一人只发一条」去重用 |
| `mentionedUserIds` | `List<Long>` | 编辑后正文 @ 到的人 |
| `addedMentionedUserIds` | `List<Long>` | 这条评论第一次 @ 到的人（5.4.6）：提醒只发给他们 |
| `body` | `String` | 编辑后的正文 |
| `editedAt` | `Instant` | 编辑时刻 |

5 分钟内的编辑也发（不记版本，但可能加了 @）。

### 7.4 `CommentDeletedEvent`（删除：变占位或整条去掉；已是占位再删不发）

| 字段 | 类型 | 说明 |
|---|---|---|
| `commentId` | `long` | 被删的评论 |
| `subjectId` | `SubjectId` | 对象 |
| `authorId` | `long` | 作者 |
| `placeholder` | `boolean` | 留下了占位（有回复）还是整条去掉 |
| `deletedAt` | `Instant` | 删除时刻 |

提醒列表要显示「评论是否已删除」和摘录，而提醒子域只能依赖事件契约、不能读讨论子域，所以删除也要发事件（设计简报附录 A「PAP-123」第 2 条）。级联去掉的占位当初变占位时已经发过，不再发。

### 7.5 给 PAP-123 的提示（不是本子域的规则）

- 「回复我的」：`parentAuthorId`（≠ `authorId`）；投稿经验下 `subjectOwner.userId`（≠ `authorId`），根评论一定提醒，楼里的回复要不要提醒所有者由 PAP-123 定（简报 §10 缺口 4）。
- 「@ 我的」：`mentionedUserIds`（发表）/ `addedMentionedUserIds`（编辑），去掉作者本人；与「回复我的」是同一人时只发一条、归「回复我的」。
- 触发人：`authorAnonymous` 为真时显示「匿名作者」，不要把 `authorId` 放进给收件人的响应。

## 8. 模块落点

| 层 | 包（`dev.patra.interaction.…discussion`） | 内容 |
|---|---|---|
| domain | `model.aggregate` | `CommentAggregate`：发表、能否被回复、编辑（5 分钟规则、新增的 @）、删除（占位或去掉） |
| | `model.vo` | `ThreadCursor`（游标的解析与格式化）、`NewComment`（要插入的新评论）、`CommentEdit`（一次编辑的结果）；评论 ID 直接用 `long`，不另设值对象 |
| | `model.enums` | `CommentState`、`ThreadSort` |
| | `model.read` | `CommentRecord`（一条评论 + 父评论的作者与状态 + 有没有直接回复）、`RevisionRecord`、`MentionTarget`（解析结果：用户 ID + 账号状态） |
| | `policy` | `CommentText`（5.3）、`MentionSyntax`（5.4）、`DiscussionLimits`（常量） |
| | `port.repository` | `CommentRepository`：楼锁、按 ID 取（含加锁）、插入、改正文、存历史版本、变占位、删除、是否有直接回复 |
| | `port.read` | `DiscussionReadPort`：根评论分页、每楼前 N 条回复、楼内回复分页、整楼、祖先链、历史版本、最近参与者 |
| | `port.mention` | `MentionDirectoryPort`：`resolve(原文列表)`、`suggest(前缀, 访客, 条数)`（identity 的 @ 提及支撑） |
| | `exception` | `DiscussionErrorTrait`、`CommentNotFoundException`、`ReplyTargetGoneException`、`CommentNotOwnedException`、`InvalidCommentException`（字段错误）、`InvalidDiscussionRequestException` |
| | `event` | 第 7 节三个事件 |
| app | `usecase.comment` | `PublishComment` / `EditComment` / `DeleteComment` 的 Command + Handler（`CommandBus`）；事务用 `TransactionOperations` 显式包 |
| | `usecase.query` | `DiscussionQueryService`（列表、更多回复、单楼、对话、历史、编辑原文）、`MentionSuggestionQueryService`；视图 record |
| | `service` | `CommentViews`（批量取名片、遮蔽、拼视图，读写共用）、`MentionResolution`（识别 + 解析 + 换成存储形式）、`DiscussionSubjects`（对象的校验、查找与状态判断） |
| infra | `persistence` | `CommentRepositoryAdapter`、`DiscussionReadAdapter`、`CommentRows`（`JdbcClient` 原生 SQL） |
| | `mention` | `MentionDirectoryAdapter`：调 `UserEndpoint.resolveByUsernames` / `suggest`，失败翻成 `UserDirectoryUnavailableException`（503，与 `UserDirectoryAdapter` 相同的三类失败） |
| | `resources/db/migration/discussion` | 两条迁移 |
| adapter | `rest` | `DiscussionController`（`/discussions/…`）、`CommentController`（`/comments/…`），请求 / 响应 DTO |
| boot | — | 不改（`UserEndpoint` 代理已有；Flyway 递归扫描新目录）；`IdentityClientConfiguration` 的注释补一句讨论的用途 |

- 四层各建 `package-info.java`（规则 22b）。
- 讨论只 import 底座的公开包：`SubjectGateway`、`SubjectReadPort`、`SubjectView`、`SubjectId`、`SubjectOwner`、`SubjectType`、`SubjectCapability`、`SubjectState`、`PublishGuardGateway`、`PublishKind`、`UserDirectoryPort`、`UserCard`、`AccountState`、底座异常。不依赖 collection、post、notification。
- `MentionDirectoryPort` 放讨论自己的 `port.mention`，不放底座：只有讨论解析 @（经验正文不解析 @，spec 2.1.2）。

## 9. 测试

### 9.1 单元测试

| 测试 | 层 | 覆盖 |
|---|---|---|
| `CommentTextTest` | domain | 换行统一、NFC、首尾空白；空 → `REQUIRED`；制表符与换行放行，其他控制字符、U+2028 / U+2029 拒绝；5,000 字素放行、5,001 拒绝；字素不超但字节超 20,000 拒绝 |
| `MentionSyntaxTest` | domain | 前一个字符的边界（行首、空白、中英文标点、字母、数字、`_`、`@`、邮箱、`你好@x`）；名字贪婪取、去尾部 `_` `-`、长度 3 ~ 30、全数字、连续连接符；转义 `\@x`；行内代码（单、多反引号、未闭合、跨空行）；链接与网址里不识别；同一原文多处一起替换；伪造标记一律转义（含链接里）、代码里的不动、`\\<@1>`；标记 ID 抽取去重保序；换回编辑原文（有用户名的换成 `@当前用户名`，已注销与省略的换成 `@已注销用户`） |
| `CommentAggregateTest` | domain | 根评论与回复的 `root_id`；能否被回复（正常、占位根、占位回复）；编辑：非作者、占位、正文相同、5 分钟整与 5 分钟 + 1 微秒、第二次记版本的 `version_at`、新增的 @；删除：非作者、有回复变占位、没回复去掉、占位再删幂等 |
| `ThreadCursorTest` | domain | 往返；格式不对 422 |
| `DiscussionExceptionsTest` | domain | 特征、原因码、字段码 |
| 事件 record 测试 | domain | 列表 `copyOf`、null 视为空 |
| `PublishCommentHandlerTest` | app | 顺序：本地校验先于任何远程调用；资格先于对象解析；`admit` 在事务里、先于插入；回复拿楼锁、占位根可回复、占位回复 409、父评论不在对象下 422；事件字段（含 `parentAuthorId` 为空的两种情况、`authorAnonymous`） |
| `EditCommentHandlerTest` | app | 403、404、对象失效 404、要资格不调 `admit`；已在当前版本里的人现在被封禁，提及照样保留；正文不变不写不发事件；事件的 `addedMentionedUserIds` |
| `DeleteCommentHandlerTest` | app | 403、404；占位 / 去掉 / 级联；幂等；不要资格；事件 |
| `CommentViewsTest` | app | 遮蔽矩阵：匿名所有者（`author`、`replyTo`）、`isOwner`、`mine`、占位；`profileVisible` 为假置空用户名与头像；已注销与 identity 省略；批量去重分块 |
| `DiscussionQueryServiceTest` | app | 未登记 → 空；失效 → 404；前 3 条与 `hasMoreReplies`、游标；单楼目标不在对象下 404；历史版本的顺序 |
| `MentionSuggestionQueryServiceTest` | app | 空查询只有参与者 ≤ 6；排除本人与匿名所有者；非匿名所有者排第一；不可见、非正常的参与者去掉；参与者前缀匹配（用户名、昵称不分大小写）；与 identity 结果去重、合计 ≤ 8 |
| `MentionDirectoryAdapterTest` | infra | 两个方法的映射；三类失败翻成 503 |

### 9.2 infra 集成测试

| 测试 | 覆盖 |
|---|---|
| `DiscussionMigrationIT` | schema 与两张表在；四个 CHECK、三个外键各自拦住违规行（断言约束名）；R6 不报 |
| `CommentRepositoryAdapterIT` | 插入 `RETURNING`；改正文与 `mentioned_user_ids`；历史版本编号；变占位清正文与历史；删除级联历史；有回复的根删不掉（外键）；楼锁阻塞同楼另一个事务（测试关事务，见坑清单） |
| `DiscussionReadAdapterIT` | 根评论正序 / 倒序与游标（同一时刻按 ID）；每楼前 4 条（判断「更多」）；楼内分页；整楼；祖先链含占位；父评论的作者与状态；`hasReplies`；历史倒序；最近参与者（只算正常评论、排除给定 ID、按最近评论倒序） |

`InteractionMigrationIT` 已按规律判断 schema 与版本号，不用改。

### 9.3 boot 流程集成测试（AC）

继承 `InteractionFlowITSupport`。`FakeIdentity` 扩两个接口：`/resolve/by-usernames`（用户名默认 `user-{id}`，可设旧名、可改状态）、`/suggest`（按前缀返回可见的正常账号，排除访客）；按请求路径分派，现有批量摘要不变。

| 测试 | AC | 覆盖 |
|---|---|---|
| `DiscussionFlowIT` | 两层结构与单楼视图 | A 发根评论；B 回复根；C 回复 B；D 回复 C → 列表里一楼三条回复平铺，B 无 `replyTo`，C 的 `replyTo` 是 B、D 的是 C；`hasMoreReplies` 与「更多回复」；单楼按 D 取到整楼与 `targetId`；按别的对象取 404 `COMMENT_NOT_FOUND`；对话链根 → B → C → D |
| 同上 | 删除 | 删有回复的根 → 占位、楼保留；删没回复的回复 → 去掉；占位的最后一条回复被删 → 占位一并去掉、`removedIds` 两条；回复占位回复 409 `REPLY_TARGET_GONE`；回复占位根成功 |
| `DiscussionAnonymityFlowIT` | 匿名经验 | 经底座网关登记一条匿名所有者的 `POST`；所有者评论、被回复、被单楼与对话链读到；他人与匿名访客的每个响应体里都没有所有者的用户 ID（按字符串断言）；`anonymous`、`isOwner`、`replyTo.anonymous`；所有者本人 `mine = true` 且 `author` 是自己的名片；联想的参与者里没有他；非匿名经验的所有者排在参与者第一 |
| `DiscussionMentionFlowIT` | @ 解析 | 当前名、保留期内旧名换成标记；封禁、不存在、过期旧名保持纯文本；11 个不同的人 422；代码与网址里的不换；伪造标记被转义；编辑原文换回 `@当前用户名`（改名后是新名、已注销是 `@已注销用户`）；被 @ 的人之后被封禁，原样保存后提及不丢 |
| `DiscussionRevisionFlowIT` | 修改历史 | 发表后立即编辑 → 无「已编辑」、历史一版；把 `created_at` 改到 6 分钟前再编辑两次 → 「已编辑」、历史三版、顺序与时间对；匿名访客能读历史；删除变占位后历史 404 |
| `DiscussionEventRollbackFlowIT` | 订阅方失败时评论回滚 | `@Import` 一个测试用的 `@EventListener`，正文含约定标记时抛异常 → 500，`discussion.comment` 与 `subject.publish_quota` 都没有行；不含标记时三种事件都按字段送到（顺带验事件内容） |
| 同上 | 资格与频率 | 邮箱未验证 409、10 秒内第二条 429、对象已删除 404 `SUBJECT_UNAVAILABLE`：经 HTTP 的原因码（底座第一次被 HTTP 打到） |

`DiscussionEventRollbackFlowIT` 多导入一个配置会多一个 Spring 上下文；只此一个，不影响其余流程测试共用的上下文。

### 9.4 门禁

`./gradlew spotlessApply`；`./gradlew check`；单独跑 `:patra-api:patra-interaction:patra-interaction-infra:integrationTest` 和 `:patra-api:patra-interaction:patra-interaction-boot:integrationTest`（`check` 不含集成测试）；`InteractionArchitectureTest`（规则 22b、25、27 自动管到新子域）；`./gradlew dumpModuleGraph` 后 `module-graph.json` 应无变化（没有新模块、新依赖）。

## 10. 上线与文档

- **runbook**（`docs/patra/runbooks/v0.14-discussion-go-live-runbook.md` 第 6 节「三个新子域」占位里本子域的一条）：合并、CD 后核对 `discussion` schema、两张表、四个 CHECK 与三个外键、四个索引，Flyway 历史里两条新版本成功；经网关匿名 `GET /patra-interaction/discussions/publication/{某个文献 ID}/threads` 回 200 空列表。没有新的定时任务、没有 Nacos 配置。
- **README**（`patra-api/patra-interaction/README.md`）：第 1 节 `discussion` 一行补全；第 4 节加「4.2 discussion 的存储与并发」（两张表、楼锁、外键、只存用户 ID）；事件契约写法第 3 节已有，不重复。
- **交接笔记** `interaction-PAP-121-handoff.md`：第 7 节事件契约（给 PAP-123）、第 6 节接口与条目形状（给 PAP-124 / 125）。
- **Linear**：开工时已改为 BE；用户同意提交后勾选 To-Do 与 AC，不手动改 Done。

## 11. 由我定的点

1. 接口路径与形状：对象级 `/discussions/{type}/{key}/…`、评论级 `/comments/{id}/…`；列表与楼内回复用游标；`sort=asc|latest`；楼内回复每次默认 50。
2. 回复已成占位的**根评论**可以（「回复这一楼」照常能用）；回复已删除的**回复**是 409 `REPLY_TARGET_GONE`；父评论不在同一对象是 422。
3. 删除：内容与历史版本一起清掉；没有回复的整条去掉，并级联去掉已经没有回复的占位祖先；已是占位再删幂等；不要求资格；对象失效也能删。
4. 编辑不计频率（资格见第 12 节第 2 条）；对象失效时不能编辑；正文不变时不写不发事件；5 分钟含整 5 分钟。
5. 「新增的 @」按这条评论曾经 @ 过的人算（`mentioned_user_ids` 只增不减），反复增删同一个 @ 只提醒一次。
6. @ 的识别规则（5.4.1）：近似 CommonMark，跳过转义、行内代码、链接、网址；候选按用户名语法预筛，超过 100 个直接 422。
7. 客户端发来的 `<@数字>` 一律转义成字面文字（编辑原文里也没有标记）。
8. 编辑原文按决策 C 把标记换回 `@当前用户名`，不看被 @ 的人主页对作者是否可见（作者自己写下的提及）；已注销的换成纯文本 `@已注销用户`。保存时，已在当前版本里的人不论现在什么状态都保留提及（5.4.2 第 3 步）。
9. 正文放行制表符；长度按客户端原文（`@用户名` 形式）算，字节上限 = 字素上限 × 4。
10. 发布三个事件（加了「评论已删除」，事件带正文）：提醒列表要摘录与「是否已删除」，而提醒只能依赖事件契约。
11. 作者对象与 PAP-122 对齐：匿名所有者本人看自己的评论时 `author` 是自己的名片；identity 省略的 `author` 为 `null`、`anonymous: false`。`profileVisible` 为假时置空用户名与头像地址，`displayName` 原样用 identity 给的。
11a. 条目带 `hasReplies`（删除确认的两种文案要它）。
12. 联想：参与者取最近 50 位再筛；非匿名的经验所有者排第一；只留正常且主页可见的参与者；identity 联想请求 `min(20, 8 + 已有参与者数)` 条。
13. 读：对象未登记回空列表；对象失效回 404 `SUBJECT_UNAVAILABLE`。
14. 别人的评论：编辑、删除、取原文一律 403（评论是公开的，不需要用 404 隐藏它的存在）。
15. 并发用楼锁 + 行锁 + 两个自引用外键兜底。
16. 单楼视图一次返回整楼，不设上限（第 13 节）。
17. 拼发表、编辑的响应时，父评论作者与被 @ 的人另取一次名片（第 12 节第 4 条）。

## 12. 已拍板

1. **主页对访客不可见的人的署名**（2026-10-27，用户「都按建议」）：不返回 `@用户名` 和头像，沿用 PAP-108 的名片规则（5.8）；设计简报 §3.20 与此冲突的地方以这条为准。
2. **编辑与删除的资格**（同日，与 PAP-122 统一）：冷静期、被封禁、邮箱未验证的账号不能编辑已发表的评论；删除不查资格（5.5、5.6）。

**审阅后补拍的两项**（2026-10-27，用户「都按建议」）：

3. **「不返回用户名」挡不住没设昵称的人（接受）**：identity 摘要的 `displayName` 在没设昵称时回退成用户名，且不看主页是否可见；注册只收用户名，所以多数人没有昵称。第 1 条在讨论区的实际效果是：不链主页、不给 `@用户名` 与头像地址，名字照常显示，而这个名字常常就是用户名。用户接受这个效果（5.8），本 Issue 不改 identity。
4. **决策 E「最多两次」的范围（只管写入前的检查）**：写入前的检查（资格 + @ 解析）合起来最多两次；写完后拼响应时，按决策 F 再批量取一次被回复者与被 @ 的人的名片，单次写入最多三次 identity 调用（用户确认）。

## 13. 已知限制

1. 单楼视图返回整楼，回复很多的楼响应会大；按现在的量（PubMed Commons 只有约 0.02% 的文献有评论，且只在 tailnet 内开放）不加上限。
2. @ 的识别是 CommonMark 的近似（代码跨行、嵌套括号的链接等边角情形），与门户渲染器的判断可能有出入：最坏情况是某个 `@名字` 没被当成提及（保持纯文本），或者显示成字面的标记文字，不会把标记插进网址里。唯一的例外是刻意构造的写法：识别时链接先于行内代码（与 CommonMark 相反），`` [`a](x) @bob ` `` 里的 `@bob` 会被换成标记，而门户把整段当代码、显示出字面的 `<@用户ID>`。
3. catalog 换库重导后、对账之前，文献的新 key 查不到登记行，讨论区暂时显示为空（与 collection 的「我的状态」相同，靠 runbook「重导后立即对账」规避）。
4. 错误引擎（`patra-tool-web`）把 `IllegalStateException` 一律映射成 422：本子域里的数据完整性错误（如评论引用的对象不在登记表里、级联删除时父评论不存在），以及以后 PAP-123 订阅方的程序错误，在用户那边都会显示成「请求参数不合法」。全局映射不在本 Issue 范围（用户拍板，2026-10-27）。
5. 修改历史没有上限：编辑不限频率，发布 5 分钟后每次编辑都存一版，`GET /comments/{id}/revisions` 一次返回全部、不分页。只在 tailnet 内开放时可以接受，本版不改（用户拍板，2026-10-27）。
6. 极窄的竞态下读接口返回 500 而不是 404：单楼视图取到目标之后、取整楼之前整楼被删；发表提交之后、读回响应之前，作者在另一个页面把它删了。
7. 编辑时「承接的提及」取自事务外预读的正文，不是锁内那份：同一作者在两个页面并发编辑同一条评论时，承接的集合可能是旧的。
8. 读评论时 `mentioned_user_ids` 用 `Set.of` 转换，数组里有重复元素会抛异常；现有两处写入方都已去重，触发不了。
9. 父评论已是占位时，拼「回复 @X」仍会多取一次它作者的名片（响应里只给 `{ deleted: true }`，不泄露）。
10. 代码小节：`CommentSourceResponse` 没有 `of()` 工厂方法，控制器直接 `new`；`MentionSyntaxTest` 的病态输入用例没有覆盖「长度两两不同、都配不上的反引号串」这种最坏情况（算法本身有上界）。

## 14. 实施记录（2026-10-27）

计划 `docs/patra/plans/2026-10-27-interaction-discussion.md`，本会话内联 TDD 执行（21 个任务，每个先看测试失败再实现）。按编排约定全程未提交。

### 14.1 与本设计、计划的偏离

| # | 偏离 | 原因 |
|---|---|---|
| 1 | @ 识别多一条规则：`@` 后面紧跟 `http://` / `https://` 时网址优先，`@` 不当提及 | 否则用户名恰好是 `https` 时会把网址改坏 |
| 2 | 带查询词的 @ 联想里，identity 全站结果（`OTHER`）照常可能列出匿名所有者的真实账号（6.3 已补说明） | spec 只要求他不进参与者；只在他自己的匿名经验下剔除，「缺席」反而暴露谁是匿名作者。由流程测试发现 |
| 3 | `InvalidCommentException` 的消息用具体文案（与字段错误同文），不是「请求参数不合法」 | 只有一条字段错误，ProblemDetail 的 `detail` 直接可读；门户只看字段码 |
| 4 | 流程测试共用底座 `InteractionFlowITSupport` 改为每个 JVM 只启动一次的静态 `WireMockServer`；测试用的事件订阅方 `DiscussionEventRecorder` 由共用底座导入，全部流程测试只有一个 Spring 上下文（9.3 节原写「`DiscussionEventRollbackFlowIT` 多一个上下文」） | WireMock 的 JUnit 扩展每个测试类换端口，共用上下文在第一个类启动时记下了下游地址；测试类变多后同一 fork 里后面的类连不上（原有的 `PublishGuardFlowIT` 也会中招） |
| 5 | 订阅方失败的回滚用例不断言 500 | 错误引擎把订阅方抛的 `IllegalStateException` 映射成 422；AC 只要求回滚 |

### 14.2 验证

- 每个任务先看测试失败再实现；对两处关键行为做了变异检查（改坏实现确认测试会红，再改回）：匿名遮蔽（`CommentViews`）→ `should_never_expose_anonymous_owner_to_others` 变红；事件挪到事务外 → `should_roll_back_comment_and_quota_when_subscriber_fails` 变红。
- 门禁：`./gradlew spotlessApply`；`./gradlew check` BUILD SUCCESSFUL（第一次挂在 SpotBugs `BX_UNBOXING_IMMEDIATELY_REBOXED`，已修）；`:patra-interaction-infra:integrationTest` 87 例、`:patra-interaction-boot:integrationTest --rerun` 59 例全部通过；`./gradlew dumpModuleGraph` 后 `module-graph.json` 无变化。
