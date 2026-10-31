# 提醒邮件：离站逐条与每日汇总、退订头（PAP-146）工程设计

> **Issue**：[PAP-146](https://linear.app/papertrace/issue/PAP-146)（GitHub #333）
> **依据**：release spec `docs/patra/release-specs/v0.16-notification.md` 2.1、决策 D / F / G / H / J、Done D4 / D5；设计简报 `patra-portal/docs/design-briefs/v0.16/notification.md` §3.7–§3.11、§4.4、附录 A 第 13–16 条；画布「提醒邮件 · 每日汇总」页（快照 `docs/patra/design/snapshots/2026-10-30-notification-hifi.zip` 里的 `Mail.dc.html`、`Digest.dc.html`）
> **契约来源**：PAP-143 信箱（`2026-10-31-notification-inbox-design.md` 第 10 节）、PAP-142 偏好与退订（`2026-10-31-notification-preference-unsubscribe-design.md` 第 7、9 节）、PAP-128 纯文本（`2026-10-30-plaintext-conversion-design.md`）、PAP-105 邮件渠道（发信端口，见第 13 节）
> **日期**：2026-10-31
> **状态**：设计定稿，代码等 PAP-143、PAP-105 汇入集成分支后再写

---

## 1. 要解决的问题

「回复我的」「@ 我的」两类提醒，现在只在站内。本 Issue 把它们送进邮箱，有两种送法，由用户在设置里按类选（PAP-142 已存好档位）：

- **离站时逐条**（默认）：提醒生成后等 10 分钟。到点时已读，或者这 10 分钟里在站上，就不发；否则发一封。同一类里一起等着的几条合成一封：回复一封、@ 一封，不混发。
- **每日汇总**：每天北京时间 8:00，把过去 24 小时里仍未读的回复和 @ 合成一封；一条都没有就不发。

每封都要带：摘录（按渠道截断）、`<@用户ID>` 换成名字、「查看并回复」直达那一楼、RFC 8058 的两个退订头和底部的退订链接。偏好是关、邮箱未验证、账号封禁或注销时不发。identity 挂了要推迟重试，恢复后补发，不能丢。

---

## 2. 先讲清几个概念

### 2.1 延迟任务：等一会儿再决定做不做

**是什么**：「10 分钟后检查一下，再决定发不发」就是一个延迟任务。它要记住两件事：什么时候到点、到点后做什么。

**防什么**：提醒一生成就发邮件，用户正开着页面时会被打扰，站内和邮箱也会重复提醒。等 10 分钟，给用户留出自己在站内看到的时间。

**业界**：

- Discourse、Mastodon 用 Sidekiq 这类任务队列（任务存进 Redis，带一个执行时刻）。
- 没有任务队列的系统，最常见的做法是**排队表 + 定时扫**：要做的事写进数据库一张表，带上到点时刻，定时任务每隔一会儿取出到点的行来做。
- RabbitMQ 的延迟消息要装插件，或者用「消息过期后转进另一个队列」的绕法；而且合并多条要把消息攒起来，不好做。
- 在内存里设定时器最简单，但进程一重启就全没了。

**我们**：排队表 `mail.reminder_queue` + XXL-Job 每分钟扫一次（第 6、7 节）。CD 每次合并都会重部署通知服务，内存定时器会丢；排队表重启后还在。

**代价**：到点后最多再等一分钟才发，所以是「10～11 分钟」，落在 AC 的 10～12 分钟内；每分钟多一次很便宜的空查询。

### 2.2 和提醒写在同一个事务里入队

**是什么**：信箱写入提醒时，会在同一个事务里发出领域事件 `NotificationsCreatedEvent`（PAP-143）。mail 用**同步**的 `@EventListener` 接住它，在**同一个事务**里往排队表插行。

**防什么**：如果等事务提交之后再入队（`AFTER_COMMIT`），提交和入队之间进程一挂，这条提醒就永远不会发邮件。同一个事务里写，就是要么提醒和排队一起成功，要么一起回滚。回滚了，RabbitMQ 消息不会被确认，会重投，重新再来一遍。

**为什么推送用提交后、邮件用同步**：推送（PAP-145）只推数字、不写库，必须等数据提交后才能推，所以用 `AFTER_COMMIT`；我们要排队，是写库的订阅方，必须和提醒同生共死，所以用同步监听。PAP-143 契约（10/31 02:14 版）第 4 节也是这样约定的；`.claude/rules/tech/package-layout.md` 同样规定：跨子域写不调对方仓储，而是发本子域事件，对方用同步 `@EventListener` 在同一事务里处理。

**代价**：排队表一旦写不进去（比如表不存在），会连带信箱写入一起回滚。这条消息按消费端的规则重试 3 次（1、2、4 秒，PAP-137），仍失败就进死信队列，站内提醒和邮件要等有人重放死信才会出现。这正是想要的效果：两者始终一致，不会出现「站内有提醒、邮件悄悄丢了」。

### 2.3 合并窗口：第一条到点时，把同类里还在等的一起带上

**是什么**：从一类提醒里的第一条开始计时 10 分钟，这段时间里同类再来的提醒都攒进同一封，到点后一起发。

**防什么**：每条各等 10 分钟、各发一封的话，15:02、15:05、15:09 的三条回复会变成 15:12、15:15、15:19 三封邮件。

**业界**：Jira 的邮件合并就是这样的窗口：第一条变更开始计时，窗口里的变更合成一封。release spec 2.1 写的是「多条合成一封学 Jira、Slack」。画布的「到点 3 条回复合成一封」也是三条分别在 15:02、15:05、15:09，合在一封里发出。

**我们**：一类里最早那条到点时，把这一类当前仍在排队、仍未读、这段时间没在站上的提醒全部带上，发一封（第 5.1 节）。

**代价**：窗口里后来的几条，等待时间不满 10 分钟。这可以接受：用户从第一条起已经 10 分钟没上站了，后来的那几条他同样没看见。

### 2.4 投递记录与幂等键：两层防重

**是什么**：每封提醒邮件先在库里记一条**投递记录**（`mail.reminder_delivery`），它的 ID 是一个 UUID。之后每次尝试发送，都带着同一个 `Resend-Idempotency-Key: {投递 ID}`（决策 G；PAP-105 的发信端口用它当幂等键，也用它生成固定的 `Message-ID`）。

**防什么**：信已经发出去了，但「已发送」还没写回库里，进程就挂了，重试时会再发一次。同一个 ID、同一个幂等键，Resend 在 24 小时内认出是同一封，不再重复送达。决策 D 写过：去重表防的是「同一个事件处理两次」，幂等键防的是「发信成功、记账失败」，两层都要。

**重试时内容变了怎么办**：每次尝试都按当时的数据重新装配（已读的去掉、名字现查），内容可能和上一次不同。Resend 遇到同一个键带不同的内容，会回 `invalid_idempotent_request`。这只会发生在「上一次其实已经被接受」的时候，PAP-105 的端口把它报成 `ALREADY_ACCEPTED`，我们记为已发（`SENT`）。这正是想要的结果：信已经到了，不再发第二封。上一次没被接受时，这个键还没用过，新内容照常发出。所以不需要把渲染好的邮件冻结进库，也就不用在库里存邮箱地址、退订令牌链接和正文摘录。

**业界**：Stripe、Resend 都推荐「先生成并保存一个幂等键，重试时复用它」。把键随请求临时现算，内容一变键就跟着变，起不到作用。

**我们**：

- 离站逐条的投递记录在合并窗口到点时建，每日汇总的在 8:00 建（第 6、7 节）。
- 投递记录上记下**第一次调用发信端口的时刻**（`first_sent_at`）。一旦调用过，结果就可能不确定（比如超时，其实对方已经收下），之后只在 24 小时内重试，超过就记为过期（`EXPIRED`）：幂等键过了 24 小时就失效，再发可能重复送达。还没调用过发信端口（identity、interaction 挂着）的投递，可以一直推迟，不会过期。
- 每日汇总再加一道唯一约束（收件人 × 汇总窗口），8 点那一小时里补跑名单也不会多发。

**代价**：多一张表，要定期清理（结束后只留 7 天）。SMTP 连续 24 小时发不出去时，这封邮件就放弃了。

### 2.5 指数退避：依赖挂了就隔一阵再试，间隔越来越长

**是什么**：第一次失败等 1 分钟再试，再失败等 2 分钟，然后 4、8、16 分钟，最长等 30 分钟，一直试到成功或者确定不该发为止。

**防什么**：identity、interaction 或 SMTP 挂掉时，每分钟都去打一遍，既浪费，也会在它刚恢复时一下子涌进去；而直接放弃又会丢信（AC 要求「恢复后补发、不丢」）。

**业界**：AWS、Google 的重试指南都推荐指数退避加上限。

**我们**：投递记录上记 `attempts` 和 `next_attempt_at`。还没调用过发信端口时，依赖不可用只推迟，不放弃；调用过以后，最多重试到第一次调用后 24 小时（2.4）。只有「确定不该发」时（偏好关了、全都已读、这段时间上过站、账号不正常、收件地址被拒），才提前结束这条投递（5.3、5.4）。

**代价**：故障持续很久时，邮件会晚到，最多比恢复时刻晚 30 分钟。装配前 identity 挂了几个小时，离站逐条的邮件也照发：装配时会重新查一遍未读和在站，读过的、上站看过的都不会出现在邮件里。

---

## 3. 目标、非目标、反目标

**目标**

1. 离站逐条：入队、到点复查（已读、在站）、同一类合并、10～11 分钟内发出。
2. 每日汇总：北京时间 8:00，按类分节，只列装配时仍未读的；没有就不发；正常情况下一天一封。
3. 邮件内容与版式照简报 §3.7–§3.11 与画布，三种邮件（单条、多条合一、汇总）各有 HTML 和纯文本两部分。
4. 退订：`List-Unsubscribe`、`List-Unsubscribe-Post` 两个头，加上正文底部的退订链接与通知设置链接，格式照 PAP-142 第 7 节。
5. 不发的情形：偏好不是对应档位、邮箱未验证、账号不是 `ACTIVE`、identity 不认识这个人。
6. 可靠：入队和提醒同一个事务；依赖不可用时退避重试，不丢；重试复用幂等键，不重复送达。

**非目标**（release spec 第 3 节）

- 有帮助的邮件、安全邮件的发送（后者归 PAP-105）、退信与投诉回调、打开与点击追踪、自选汇总时间、按讨论静音。
- 多实例：通知服务只有一个实例，调度中心「单机串行」，不加分布式锁。

**反目标**（这样做就算失败，即使功能看起来能用）

- 用户开着门户，或者已经在站内看过，却收到了这一条的邮件。
- 档位不变的情况下，一条提醒出现在两封邮件里，或者正常情况下一天收到两封汇总。
- identity 抖一下，就有提醒邮件永远不再发。
- 邮件里出现原始的 `<@123>`、未转义的 HTML，或者截出半个 `@名字`。
- 退订头指向网关（公网打不到），或者链接里带着追踪参数。

---

## 4. 已定的前提

| 前提 | 来源 |
|---|---|
| 档位 `OFF` / `INSTANT` / `DIGEST`，两类默认 `INSTANT`，有帮助恒 `OFF` | PAP-142 `EmailSubscription.modeOf` |
| 退订地址两个，都在门户；令牌是 base64url，放进地址不用转义；两个地址是长期契约 | PAP-142 第 7 节 |
| 收件信息发信前现查，不存副本 | 决策 F，PAP-142 `RecipientPort` |
| 在站 = 推送连接在线，或者轮询拉过未读数；都记在 `kernel.presence` | 决策 H，PAP-143 `PresenceGateway` |
| 摘录：单条 300 字素、保留换行；列表每条 140 字素、压成一行；一封最多列 10 条 | 简报 §10 第 10 条、PAP-128 |
| `<@ID>` 按 `MentionNames` 换名字（主页可见写用户名，否则写昵称，查不到写「已注销用户」），和站内列表同一条规则 | PAP-128、PAP-143 10.4 |
| 逐条邮件不跨类合并；每日汇总自成一个退订类别 | 简报 §10 第 1、2 条 |
| 邮件里的颜色、骨架照 identity 的 `layout.html`，不另做一套 | 简报 §3.7、§7 |
| 发信走 SMTP，带 `Resend-Idempotency-Key`；提醒邮件带两个退订头，安全邮件不带 | 决策 G |

---

## 5. 规则

### 5.1 离站逐条

**入队**（同步监听 `NotificationsCreatedEvent`，和信箱写入同一个事务）

- 每条 `category.hasEmail()` 为真的提醒（回复与 @）插一行：`queued_at` 取事件里的 `createdAt`（写入信箱的时刻，和入队同一个事务，PAP-143 契约第 4 节），`due_at = queued_at + 10 分钟`。
- 按 `notification_id` 主键 `ON CONFLICT DO NOTHING`：同一条提醒不会排两次。
- 入队时**不查**偏好和收件人：
  - 偏好要按到点那一刻的为准（用户可能在这 10 分钟里改了档位）；
  - 读偏好的 `subscriptionOf` 会插行，不该塞进信箱的事务里；
  - 查收件人要调 identity，不能放在消费事件的事务里。
- **到点和在站都按 `createdAt` 算，不按 `occurredAt` 算**：RabbitMQ 停过之后补投到达的事件，`occurredAt` 可能是几小时前，按它算会让提醒一到就「到点」，用户没有等待的机会；`createdAt` 才是用户第一次能看到这条提醒的时刻。

**到点**（每分钟一次，按收件人处理）

取出「至少有一行 `due_at ≤ now`」的收件人。对每个人，在一个事务里：

1. 取出他排队的全部行（两类都取，没到点的也取）。
2. 经 `InboxReadPort.findUnread(收件人, 这些 ID)` 复查：已读的、提醒已被删除的（评论或经验删了，PAP-143 会一起删掉提醒），**丢弃**。
3. 经 `PresenceGateway.lastSeen` 取他最后一次在站的时刻 `lastSeen`：`queued_at ≤ lastSeen` 的行**丢弃**，因为这条提醒出现之后他上过站，铃铛上已经看到过了。
4. 剩下的行按类别分组。最早一行已经到点的那一类，**这一类剩下的全部行**（包括还没到点的）合成一条投递记录（2.3），排队行随之删除；最早一行还没到点的那一类，原样留着。
5. 被丢弃的行也一起删除。

这样，每一条发进邮件的提醒都满足两点：出现之后用户没上过站；它所在的那一组，最早一条已经等满 10 分钟。

**例子**（「回复我的」，逐条）：15:02、15:05、15:09 各来一条回复，用户一直没上站。15:12 最早那条到点，三条合成一封。如果用户 15:07 上过站：15:02、15:05 两条在 15:12 被丢弃；15:09 那条留着，它自己 15:19 到点。

### 5.2 每日汇总

- **窗口**：`[当天北京时间 08:00 − 24 小时, 当天北京时间 08:00)`，按提醒写入信箱的 `createdAt`（`inbox.notification.created_at`）算，不按 `occurredAt`。原因：broker 停过时，一条 07:30 的评论可能 08:30 才写进信箱。按 `occurredAt` 算，它既不在当天 8:00 的窗口里（那时还没有这条提醒），也不在第二天的窗口里（时间早于 08:00），就永远进不了汇总；按 `createdAt` 算，它进第二天的汇总。每一条显示的时间仍然是 `occurredAt`。
- **名单**：08:00 时偏好里至少一类是 `DIGEST` 的人，每人建一条汇总投递记录，同一个窗口只有一条（唯一约束）。名单从 preference 取（新加读端口方法 `digestSubscribers`，第 9 节）：汇总的对象由偏好决定，人数也少；反过来从信箱「有未读的人」里筛，就要对每个人调一次 `subscriptionOf`，顺带给他们插一行偏好。
- **建名单的任务**每 10 分钟跑一次，只在 8 点那一小时里跑（`0 0/10 8 * * ?`）：08:00 那次如果赶上通知服务重部署而没跑成，08:10 会补上；唯一约束保证同一个人不会多建。8:00～8:50 之间才改成每日汇总的人，会被补跑带上，当天就收到一封；8:50 之后改的，从第二天开始收。
- **装配时**（和逐条邮件用同一个投递流程，第 7.3 节）：
  1. 重新读偏好。这一刻是 `DIGEST` 的类别才进汇总；一类都没有了就跳过。
  2. 每一类取窗口内**此刻仍未读**的总数，以及最新的 10 条。
  3. 两类合计 0 条就跳过（「没有就不发」）。
  4. 不看在站：汇总本来就是「每天一封、列出还没读的」，和用户这会儿在不在站上无关。
- **分节**：「回复我的」在前、「@ 我的」在后，每节里新的在前。节标题写这一节的总数（`回复我的 · 9 条`）。
- **10 条上限怎么分**：两节都有时，回复先拿 `ceil(10 × 回复总数 ÷ 两类总数)` 条，但至少给 @ 留 1 条；@ 拿剩下的；哪一节不够用，余下的名额给另一节。例如 9 条回复、5 条 @，列 7 + 3 条，其余写 `还有 4 条没有列出（回复 2 条、@ 2 条），到提醒页查看。`（画布「超过 10 条」场景的数字）。只有一节时直接列前 10 条，其余写 `还有 N 条没有列出，到提醒页查看。`
- **手动触发**（D4 验收用）：任务参数写 `users=123,456`（必须列出用户 ID）。只给列出的、此刻偏好里有 `DIGEST` 类别的人建投递，窗口的右端是「此刻，截到分钟」，左端随之是 24 小时前。限定到具体用户，是因为门户已经对公网开放，手动跑不能给真实用户多发一封。窗口右端不同，就不会挡住第二天 8:00 的正式汇总；同一分钟里重复触发，会被唯一约束挡住；隔一分钟再触发，就是一个新窗口，会再发一封（只发给列出的测试账号）。

### 5.3 不发的情形（每次装配时都查一遍）

按下表从上到下检查：只查库的在前，调外部服务的在后，这样档位已经关掉的人不必去调 identity。汇总投递的前三行顺序不同：先读档位（不知道哪些类别在汇总，就没法按类查窗口），再按窗口查未读，也不看在站；三者都只查库，原则不变。

| 情形 | 判断 | 结果 |
|---|---|---|
| 要列的提醒全都已读或已删除 | `findUnread` / 窗口查询 | 跳过，原因 `NOTHING_UNREAD` |
| 逐条：剩下的提醒出现之后，用户上过站 | `lastSeen ≥ 条目的 createdAt`，这样的条目去掉；去掉后为空就跳过 | 跳过，原因 `NOTHING_UNREAD` |
| 这一类的档位不是逐条 / 汇总里没有 `DIGEST` 的类别 | preference 新加的只读方法 `findSubscription`（不建行；没有行就是默认值，第 9 节） | 跳过，原因 `MODE` |
| identity 不认识这个人 | `RecipientPort.find` 为空 | 跳过，原因 `RECIPIENT` |
| 账号状态不是 `ACTIVE`（封禁、注销冷静期、已注销） | `Recipient.state` | 跳过，原因 `RECIPIENT` |
| 邮箱未验证或为空 | `emailVerified`、`email` | 跳过，原因 `RECIPIENT` |
| 能列的提醒都没有落点（对象查不到、`available` 为假或没有 `key`） | `SubjectViewPort` | 跳过，原因 `NOTHING_UNREAD` |

- **收件人通过检查之后，才调 `subscriptionOf` 取退订令牌**：它在没有行时会建一行（PAP-142 第 9 节），不能给已注销的人重新建出偏好和令牌。
- **注销冷静期也不发**：用户已经申请离开，再发「有人回复了你」形同挽留。账号安全类的邮件（注销提醒等）照常由 PAP-105 发。release spec 写的是「封禁或注销时不发」，只有 `ACTIVE` 才发是最稳妥的读法。
- **单条与多条**：由装配时还能列出几条决定。逐条投递原本有 3 条、到装配时只剩 1 条未读，就按单条的版式发。
- **改了档位之后**：一条提醒已经逐条发过邮件、一直没读，用户把档位改成每日汇总，第二天的汇总还会列出它。这可以接受：它确实还没读，而汇总就是「列出还没读的」。

### 5.4 失败与重试

每次尝试都从头装配（7.3），然后调用发信端口。各种结果：

| 结果 | 处理 |
|---|---|
| 5.3 的某一条成立 | `SKIPPED`，记下原因 |
| identity 不可用（`IdentityUnavailableException`） | 推迟：`attempts + 1`，`next_attempt_at = now + min(2^(attempts−1), 30) 分钟`；本轮停下（别人也一样会失败） |
| interaction 不可用（`InteractionUnavailableException`） | 同上。「查看并回复」的链接要靠对象视图里的 `key`，没有链接的提醒邮件没有意义；站内列表遇到这种情况会降级显示，邮件不降级 |
| 发信端口正常返回 | `SENT` |
| `MailDeliveryFailedException`，`ALREADY_ACCEPTED` | 之前那次已经被接受：`SENT`，记 WARN |
| `MailDeliveryFailedException`，`REJECTED` | 对这个收件人永久失败（地址被拒、太大、本地组不出来）：`SKIPPED`，原因 `REJECTED`；继续处理下一条 |
| `MailDeliveryFailedException`，`TRANSIENT` | 推迟，本轮停下（连不上、超时、4xx、鉴权失败，别人也一样会失败） |
| 其他意外异常 | 推迟，记 ERROR，**继续**处理下一条（问题只出在这一条上） |

**过期**：推迟时，如果这条投递已经调用过发信端口，而且 `now ≥ first_sent_at + 24 小时`，就不再推迟，改记 `EXPIRED`，记 ERROR（2.4）。

**结局**：一条投递只有三种结局：发出去了（`SENT`）；确定不该发（`SKIPPED`）；调用过发信端口之后 24 小时仍没成功（`EXPIRED`）。identity、interaction 不可用，只会让它停在 `PENDING`，等到恢复。

**一个已知的边角**：identity 连续挂了一天以上时，前一天的汇总要等它恢复才发。恢复之后，前一天和当天的两封汇总可能在同一天到达。两封里的条目不会重复（窗口不重叠），宁可晚到，也不丢。

---

## 6. 存储

脚本放 `patra-notification-infra/src/main/resources/db/migration/mail/`，schema `mail` 用 `CREATE SCHEMA IF NOT EXISTS mail` 建（PAP-105 的发送记录表也在 `mail` 下，两边都写 `IF NOT EXISTS`，谁先谁后都行）。只存用户 ID（R6）。时间列都由代码显式给值，取注入的 `Clock`。

### 6.1 `mail.reminder_queue`：在等 10 分钟的提醒

| 列 | 类型 | 说明 |
|---|---|---|
| `notification_id` | BIGINT PK | 信箱里的提醒 |
| `recipient_id` | BIGINT NOT NULL | 收件人 |
| `category` | VARCHAR(16) NOT NULL | `REPLY` / `MENTION`（CHECK） |
| `queued_at` | timestamptz(6) NOT NULL | 入队时刻，判断在站用 |
| `due_at` | timestamptz(6) NOT NULL | `queued_at + 10 分钟` |

索引：`(due_at)`（找到点的收件人）、`(recipient_id)`（取一个人的全部行）。

### 6.2 `mail.reminder_delivery`：一封提醒邮件的投递记录

| 列 | 类型 | 说明 |
|---|---|---|
| `delivery_id` | UUID PK | 投递 ID，也是 `Resend-Idempotency-Key` 与 `Message-ID` 的来源 |
| `recipient_id` | BIGINT NOT NULL | 收件人 |
| `kind` | VARCHAR(16) NOT NULL | `INSTANT` / `DIGEST`（CHECK） |
| `category` | VARCHAR(16) NULL | 逐条：`REPLY` / `MENTION`；汇总为空（CHECK 与 `kind` 联动） |
| `notification_ids` | BIGINT[] NULL | 逐条：合进这封的提醒，按入队先后；邮件里的顺序在装配时按 `occurredAt` 重排。汇总为空 |
| `window_end` | timestamptz(6) NULL | 汇总：窗口右端；逐条为空 |
| `status` | VARCHAR(16) NOT NULL | `PENDING` / `SENT` / `SKIPPED` / `EXPIRED`（CHECK） |
| `skip_reason` | VARCHAR(32) NULL | `MODE` / `NOTHING_UNREAD` / `RECIPIENT` / `REJECTED`；只有 `SKIPPED` 时有值 |
| `attempts` | INTEGER NOT NULL | 已失败的次数 |
| `next_attempt_at` | timestamptz(6) NOT NULL | 下一次可以尝试的时刻 |
| `created_at` | timestamptz(6) NOT NULL | 建记录的时刻 |
| `first_sent_at` | timestamptz(6) NULL | 第一次调用发信端口的时刻；24 小时过期从这里算 |
| `finished_at` | timestamptz(6) NULL | 结束（`SENT` / `SKIPPED` / `EXPIRED`）的时刻 |

约束与索引：

- `uk_reminder_delivery_digest (recipient_id, window_end) WHERE kind = 'DIGEST'`：一个人一个窗口只有一封汇总（部分唯一索引）。
- `idx_reminder_delivery_pending (next_attempt_at) WHERE status = 'PENDING'`：取到点的投递。
- `idx_reminder_delivery_finished (finished_at) WHERE finished_at IS NOT NULL`：清理。
- CHECK：
  - `kind = 'INSTANT'` 时 `category`、`notification_ids` 非空、`window_end` 为空；`kind = 'DIGEST'` 反过来。
  - `status = 'PENDING'` 当且仅当 `finished_at IS NULL`。
  - 只有 `SKIPPED` 时 `skip_reason` 非空。

**保留**：结束的记录留 7 天，由汇总任务顺带删除（第 7.4 节）。唯一约束要靠留下的 `SENT` 行起作用：8:10 补跑名单时，8:00 那批已经发出的不会再建。

**为什么不接注销清理**（PAP-142 的 `UserDataCleanupGateway`）：两张表都只有用户 ID，不存地址和正文；排队行几分钟内就消费掉，投递记录最多留 7 天；已注销的人在装配时就会被跳过。

---

## 7. 流程

### 7.1 入队

```
inbox 写入提醒的事务
  └─ 发布 NotificationsCreatedEvent
       └─ app.mail.listener.ReminderQueueListener（@EventListener，事务 MANDATORY）
            └─ ReminderQueueRepository.enqueue(…)   同一事务
```

### 7.2 每分钟：`reminderMailJob`

```
ReminderMailScheduleJob（XXL，0 * * * * ?）
  ├─ FlushDueRemindersHandler：到点的收件人 → 每人一个事务做 5.1 的 1～5 步 → 新建 INSTANT 投递
  └─ SendPendingDeliveriesHandler：PENDING 且 next_attempt_at ≤ now 的投递，按 next_attempt_at 先后，每次取 50 条，直到取完
        每一条：装配（7.3）→ 调发信端口（事务外）→ 记结果（一个短事务）
        identity / interaction 不可用或 TRANSIENT：本轮停下；意外异常：记下、继续
```

先到点、再发送：同一轮里刚建好的投递，马上就会发出。

### 7.3 一条投递怎么装配、怎么发

**逐条**（`INSTANT`）：

1. `InboxReadPort.findUnread(收件人, notification_ids)`：取仍未读的条目，带纯文本全文、触发人、对象、评论、类型、`occurredAt`、`createdAt`。
2. `PresenceGateway.lastSeen(收件人)`：去掉 `createdAt ≤ lastSeen` 的条目。
3. `findSubscription(收件人)`：档位（不建行）。
4. `RecipientPort.find(收件人)`：地址与状态（5.3）。
5. `subscriptionOf(收件人)`：退订令牌（这时才可能建行）。
6. `SubjectViewPort.findViews(条目的对象)`：对象视图，用来写「在哪」行和链接。
7. `UserCardPort.findCards(触发人 ∪ 正文里 <@ID> 的人, viewer = 收件人)`：名字。
8. 交给领域里的拼装规则（`ReminderMailComposer`）：得到 `ReminderMailContent`（主题、预览、小标、标题、条目、按钮、页脚、两个退订地址）。
9. 组装 `OutgoingMail` 交给 PAP-105 的 `MailDeliveryPort.deliver`，渲染在端口里做（第 8.5 节）：
   - `deliveryId`：投递 ID；
   - `to`：收件地址；
   - `template`：`notification-single` / `notification-batch` / `notification-digest`；
   - `subject`：内容里的主题；
   - `model`：`mail` → `ReminderMailContent`，外加 layout 读的三个顶层变量 `preheader`、`footnote`、`asideLinks`（第 8.5 节；值不能为 null，所以没有值就不放）。`subject`、`siteName` 由 PAP-105 的渲染器每封覆盖，`notice` 留给安全邮件，这三个键都不用；
   - `headers`：`List-Unsubscribe: <{一键退订地址}>`、`List-Unsubscribe-Post: List-Unsubscribe=One-Click`。
10. 第一次调用前写下 `first_sent_at`，和「准备发送」放在同一个短事务里；然后调用端口，再按 5.4 记结果。

**汇总**（`DIGEST`）：第 1、2 步换成「每一类按窗口取未读总数与最新 10 条」（`InboxReadPort.unreadBetween`），不看在站；第 3 步提到最前（先知道哪些类别在汇总）。其余相同。

### 7.4 每日：`dailyDigestJob`

```
DailyDigestScheduleJob（XXL，0 0/10 8 * * ?；手动时参数 users=…）
  └─ ScheduleDailyDigestHandler
       ├─ 算出窗口右端（5.2）
       ├─ 按用户 ID 升序分页（每页 500 人）取 digestSubscribers（手动时只取参数里的人），逐页 INSERT … ON CONFLICT DO NOTHING 建 DIGEST 投递（next_attempt_at = now）
       └─ 删除 finished_at 早于 7 天前的投递记录
```

建好的汇总投递由下一分钟的 `reminderMailJob` 发出，所以邮件在 08:01 前后到。

---

## 8. 邮件内容

时间一律写北京时间（`Asia/Shanghai`，配置项见第 10 节）。字数一律按扩展字素簇计（PAP-128），截掉内容时末尾加 `…`（U+2026）。

### 8.1 名字

| 谁 | 写法 |
|---|---|
| 触发人，`actorAnonymous` 为真 | `匿名作者` |
| 触发人，名片查不到或状态是已注销 | `已注销用户` |
| 触发人，其余情况 | 名片的 `displayName`（identity 已用用户名兜底） |
| 摘录里的 `<@ID>` | `MentionNames.of(名片)`，规则同站内列表 |

Issue 写的是「邮件里显示显示名，为空时用用户名兜底」，这一条说的是触发人。正文里的 `@名字` 沿用站内的规则：同一条评论，在站内和邮件里读起来应该一样。

### 8.2 摘录

- 用底座的 `ExcerptRenderer` 按 PAP-128 规则截断：单条 300 字素、保留换行；列表 140 字素、压成一行。
- 简报要求摘录里的 `@名字` 用 `clay-700`、字重 500 显示，所以要知道摘录中每个 `@名字` 的位置。为此给 `ExcerptRenderer` 加一个返回**分段**的方法：文本段与提及段交替，另带一个「是否截断」。原来的 `render` 改成把分段拼起来，行为不变。所有真提及都加强调，不只是收件人自己。
- 纯文本里可能有字面的 `<b>`、`<https://…>`（PAP-128）：HTML 部分一律用 `th:text` 转义输出；纯文本部分原样输出。
- 预览文字（preheader）：单条邮件是摘录的前 60 字素；多条与汇总是 `第一条的名字：` 加上第一条摘录的前 50 字素，压成一行。截掉了内容就加 `…`。

### 8.3 「在哪」与链接

| 对象 | 单条邮件：小标 / 内容 | 列表里的一行 |
|---|---|---|
| 文献 | `文献讨论` / 标题，最多 200 字素 | `文献 · ` + 标题，最多 64 字素 |
| 自己的投稿经验（`ownerId` = 收件人） | `你的投稿经验` / `期刊 · 2026 年 3 月投稿 · 大修 → 录用` | `你的投稿经验 · 期刊 · 2026 年 3 月投稿` |
| 别人的投稿经验 | `投稿经验讨论` / `期刊 · 2026 年 3 月投稿 · 大修 → 录用` | `期刊 的一条投稿经验` |

- 期刊名用 `abbreviation`，没有时用 `journal`。投稿月份取 `publishedDate`（经验的投稿月份 `yyyy-MM`）。
- 结果段：首轮决定是直接拒稿、审后拒稿、首轮录用时，只写首轮决定（`直接拒稿`）；否则写 `首轮决定 → 最终结果`（`大修 → 录用`）。中文名照门户 `lib/experience/labels.ts`：
  - 首轮决定：`DESK_REJECT` 直接拒稿、`REJECT_AFTER_REVIEW` 审后拒稿、`MAJOR_REVISION` 大修、`MINOR_REVISION` 小修、`ACCEPT` 录用；
  - 最终结果：`ACCEPTED` 录用、`REJECTED` 拒稿、`WITHDRAWN` 撤稿、`IN_PROGRESS` 进行中；
  - 不认识的枚举值整段省略。
- 文献标题来自 PubMed，可能带 `<i>`、`<sub>` 和 MathML 这类内联标记：去掉门户白名单里的标签（排版组 `i b sub sup u`、`math` 及其子元素，`annotation` 连内容一起去掉），解码常见实体（`&amp; &lt; &gt; &quot; &apos; &nbsp;` 和数字实体），其余字符原样保留，最后照常转义输出。标题为空时写 `一篇文献的讨论`（同门户）。
- 每一条的「查看并回复」：文献 `{门户}/papers/{key}?comment={commentId}`，经验 `{门户}/posts/{key}?comment={commentId}`（同门户 `notificationHref`）。
- 主按钮：单条是 `查看并回复 →`，链到这一条；多条合一与汇总是 `打开提醒页 →`，回复链到 `{门户}/notifications`，@ 链到 `{门户}/notifications/mentions`，汇总链到 `{门户}/notifications`。
- 通知设置：`{门户}/settings/notifications`。
- 退订：确认页 `{门户}/unsubscribe#token={令牌}&category={REPLY|MENTION|DIGEST}`；一键退订 `{门户}/unsubscribe/one-click?token={令牌}&category={…}`。
- 门户地址末尾的 `/` 去掉。链接不改写、不加追踪参数。

### 8.4 文案

**刊头类别**一律 `讨论提醒`。**版权页第二行**：`此邮件由 Patra 系统自动发出，直接回复不会出现在讨论里。`，后面照常接 `Patra 不会在邮件里向你索要密码。`。

| | 单条 | 多条合一 | 汇总 |
|---|---|---|---|
| 栏目小标 | `回复 · REPLY` / `@ 提及 · MENTION` | 同左 | `每日汇总 · 10 月 31 日`（窗口右端的日期） |
| 主题 | 见下表 | `3 条新回复：王立、周敏、林知远` / `2 条评论 @ 了你：林知远、周敏` | `每日汇总：5 条未读的回复和 @（10 月 31 日）`；只有 @：`每日汇总：2 条未读的 @（10 月 31 日）`；只有回复：`每日汇总：3 条未读的回复（10 月 31 日）` |
| h1 | 见下表 | `3 条新回复` / `2 条评论 @ 了你` | `过去 24 小时有 5 条未读`；只有 @：`过去 24 小时有 2 条 @ 没看`；只有回复：`过去 24 小时有 3 条回复没看` |
| 引导句 | — | `你不在 Patra 上的这段时间，有 3 条回复还没看。` / `…有 2 条 @ 你的评论还没看。` | `10 月 30 日 08:00 到 10 月 31 日 08:00（北京时间）之间，下面这些提醒你还没看过；在站内看过的不会列在这里。` |
| 时间 | `10 月 30 日 15:02` | `15:02`；有任一条不在发送当天时，全部写 `10 月 30 日 15:02` | `10 月 30 日 15:02` |
| 超过 10 条 | — | `还有 N 条没有列出，到提醒页查看。` | 见 5.2 |
| 卡片外一句 | `你收到这封邮件，是因为你把「回复我的」设为离站时逐条发送。` + `退订这类邮件` · `通知设置` | 同左 | `你收到这封邮件，是因为你把「回复我的」「@ 我的」设为每日汇总。`（只有一类在汇总时只写那一类）+ `退订每日汇总` · `通知设置` |

单条的主题与 h1：

| 类型 | 主题 | h1 |
|---|---|---|
| `COMMENT_REPLY` | `王立 回复了你的评论` | `王立 回复了你` |
| `POST_COMMENT` | `林知远 评论了你的投稿经验` | 同主题 |
| `MENTION` | `林知远 在评论里 @ 了你` | 同主题 |

- **列表里的动作**：`回复了你` / `评论了你的投稿经验` / `@ 了你`（同门户 `actionLabel`）。
- **主题里的名字**：按条目顺序去重，3 个以内全写；多于 3 个写前两个加 `等 N 人`（N 是去重后的人数），如 `王立、周敏等 5 人`。
- **条数**：主题、h1、引导句里的 N 都是装配时还能列出的条数（多条合一）或这一类的未读总数（汇总）。

### 8.5 模板

模板放在 `patra-notification-infra/src/main/resources/mail/` 下（PAP-105 搬来的目录），共三个 HTML（`html/notification-single.html`、`html/notification-batch.html`、`html/notification-digest.html`）加三个纯文本（`text/*.txt`），名字照简报 §5。由 PAP-105 的发信端口按模板名渲染：同一份模型渲染两次逐字节相同。模型里 `mail` 是 `ReminderMailContent`，另有 layout 用的 `preheader`、`footnote`、`asideLinks` 三个顶层变量（安全邮件不放，layout 里 `th:if` 判空）；模板里用 `${mail.heading}` 这样读 record 的字段，同 identity 模板读 `SecurityNoticeView` 的写法。时间都在拼装时写成文字（`10 月 30 日 15:02`），不传 `Instant`：端口会把 `Instant` 渲染成安全邮件的写法 `2026-11-04 08:00 (UTC+8)`。

HTML 部分按画布 `Mail.dc.html`、`Digest.dc.html` 的 table 加行内样式写：

- 摘录块是 `p-quote` 类（深色模式下补 `.p-quote { background: #292524 !important; }`）；
- 列表块上边是 1px `#1c1917` 实线，条目之间是 1px `#e7e1d4` 分隔；
- `@名字` 用 `span.p-link`（`#97441e`、字重 500）；
- 窄屏的样式沿用 layout 里 `@media (max-width: 520px)` 那一组。

纯文本部分照简报 §3.11。

共用骨架 `html/fragments/layout.html`（PAP-105 原样搬来）加三个**可选**的模型变量，安全邮件不设，行为不变：

| 变量 | 作用 |
|---|---|
| `preheader` | 有值时在正文最前面放一个隐藏的 `div`（同画布写法） |
| `footnote` | 有值时替换版权页的「请勿回复」那一句 |
| `asideLinks` | 有值时，卡片外那一句后面接上这些链接（`p-link`、下划线），以 ` · ` 隔开 |

纯文本部分不用共用的 `text/fragments/footer.txt`：简报 §3.11 的页脚（为什么收到、退订链接、通知设置、「直接回复不会出现在讨论里」）和安全邮件的页脚不同，三个纯文本模板各自写一份。13 个安全邮件模板与 `footer.txt` 一行不改。

### 8.6 邮件头

| 头 | 值 |
|---|---|
| `List-Unsubscribe` | `<{一键退订地址}>` |
| `List-Unsubscribe-Post` | `List-Unsubscribe=One-Click` |
| `Resend-Idempotency-Key` | 投递 ID（UUID），由 PAP-105 的端口写入 |

`Auto-Submitted: auto-generated`、`X-Auto-Response-Suppress: All`，以及 `Message-ID: <{投递 ID}@{发件人域名}>`（同一个投递每次重试都相同），都由 PAP-105 的端口在写完我们的头之后统一加上，不会被覆盖。头的值里带 `\r` 或 `\n` 时，构造 `OutgoingMail` 会直接抛异常，退订地址里不会出现这两个字符。

---

## 9. 端口与子域

**子域边**（`NotificationArchitectureTest` 里加 `allowingDependency`）：`mail → inbox`、`mail → preference`。mail 用底座 kernel 不用声明。

**要往别的子域加的方法**（只增不改；PAP-143、PAP-142 合入后由本 Issue 加）：

| 端口 | 新方法 | 说明 |
|---|---|---|
| `InboxEntry`（inbox） | 加字段 `Instant createdAt` | 写入信箱的时刻；装配时复查在站要用 |
| `InboxReadPort`（inbox） | `List<InboxEntry> findUnread(long recipientId, Collection<Long> notificationIds)` | 给定 ID 里仍存在、仍未读的条目，按 `occurredAt` 升序 |
| `InboxReadPort`（inbox） | `UnreadWindow unreadBetween(long recipientId, NotificationCategory category, Instant from, Instant to, int limit)` | `created_at ∈ [from, to)` 的这一类未读总数，以及按 `occurredAt` 降序的前 `limit` 条；`UnreadWindow` 放在 `domain.inbox.model.read`。不加索引：走 PAP-143 的部分索引 `idx_notification_unread (recipient_id, category) WHERE read_at IS NULL`，一个人的未读条数很少，在其上再按 `created_at` 过滤足够 |
| `EmailSubscriptionReadPort`（preference） | `List<EmailSubscription> digestSubscribers(long afterUserId, int limit)` | 至少一类是 `DIGEST` 的人，按用户 ID 升序分页；不建行（没有行就是默认值，不可能是 `DIGEST`） |
| `EmailSubscriptionReadPort`（preference） | `EmailModes findSubscription(long userId)` | 只读两类的档位，不建行；没有行时返回默认值（两类都是 `INSTANT`）。`EmailModes(EmailMode reply, EmailMode mention)` 放 `model.read`，带 `modeOf(NotificationCategory)` |
| `ExcerptRenderer`（kernel） | `SegmentedExcerpt renderSegments(String plainText, Map<Long, String> names, int maxGraphemes, boolean singleLine)` | 分段结果（8.2）；`render` 改成基于它实现 |

**mail 自己的**：

| 层 | 包 | 类 |
|---|---|---|
| domain | `mail.model.aggregate` | `ReminderDelivery`：状态流转（`PENDING` → `SENT` / `SKIPPED` / `EXPIRED`）、退避、记下第一次发送时刻、24 小时过期 |
| domain | `mail.model.vo` | `QueuedReminder`、`DigestWindow` |
| domain | `mail.model.enums` | `DeliveryKind`、`DeliveryStatus`、`SkipReason` |
| domain | `mail.model.vo.content` | 拼好的邮件 `ReminderMailContent`（单条 / 多条 / 汇总三种，加上条目、分节、链接） |
| domain | `mail.policy` | `ReminderWindowPolicy`（5.1 的丢弃与合并）、`DigestSlotPolicy`（10 条怎么分）、`ReminderMailComposer`（文案与链接）、`ReminderLinks`、`PublicationTitleText`、`ExperienceOutcomeText`、`ReminderTimeText` |
| domain | `mail.port.repository` | `ReminderQueueRepository`、`ReminderDeliveryRepository` |
| domain（PAP-105 的） | `mail.port.delivery` | `MailDeliveryPort#deliver(OutgoingMail)`：正常返回即已接受；失败抛 `MailDeliveryFailedException`，`failure()` 为 `ALREADY_ACCEPTED` / `REJECTED` / `TRANSIENT`（5.4） |
| app | `mail.listener` | `ReminderQueueListener` |
| app | `mail.usecase.flush` | `FlushDueRemindersCommand` / `Handler` / `Result` |
| app | `mail.usecase.send` | `SendPendingDeliveriesCommand` / `Handler` / `Result`；`ReminderDeliveryAssembler` 放 `mail.service` |
| app | `mail.usecase.digest` | `ScheduleDailyDigestCommand` / `Handler` / `Result` |
| infra | `mail.persistence` | 两个 `*RepositoryAdapter`（JdbcClient + 原生 SQL，同 preference） |
| adapter | `mail.scheduler` | `ReminderMailScheduleJob`（`reminderMailJob`）、`DailyDigestScheduleJob`（`dailyDigestJob`） |

发信用 PAP-105 在 `domain.mail.port.<协作者>` 里提供的端口，按它的类名与结果类型接（第 13 节）。

---

## 10. 配置

| 键 | 值 | 放哪 |
|---|---|---|
| `patra.notification.mail.links.base-url` | `${PATRA_PORTAL_BASE_URL:http://linqibins-mac-mini:4000}` | Nacos `patra-infra/nacos/dev/patra-notification.yaml`（和 identity 同一个环境变量） |
| `patra.notification.mail.zone` | `Asia/Shanghai` | 若 PAP-105 已为安全邮件定了显示时区，就复用它；否则加在 jar 的 `application.yml` |

用 `@ConfigurationProperties` + `@Validated` 绑定，`base-url` 必须是绝对的 http(s) 地址，否则启动失败。域名只来自配置，不来自请求的 `Host` 头（同 identity）。

---

## 11. 测试

**单元测试**（domain 不依赖 Spring，固定 `Clock`）

- `ReminderWindowPolicyTest`：已读丢弃；`queued_at ≤ lastSeen` 丢弃；最早一行到点时整类合并（含未到点的行）；两类同时到点分成两组；一类到点、一类没到点；`lastSeen` 为空。
- `ReminderDeliveryTest`：退避序列 1、2、4、8、16、30、30 分钟；第一次发送时刻只记一次；没发送过时一直推迟不过期；发送过、满 24 小时后推迟变成 `EXPIRED`；结束后不能再改。
- `DigestSlotPolicyTest`：9 / 5 → 7 / 3；13 / 1 → 9 / 1；1 / 13 → 1 / 9；只有一节；总数 ≤ 10；能列的条数少于名额。
- `ReminderMailComposerTest`：三种邮件的主题、h1、小标、引导句、卡片外一句、时间写法（含跨天）、名字去重与「等 N 人」、超过 10 条的说明、预览文字、匿名与已注销的名字。
- `ReminderLinksTest`：门户末尾带 `/`；两个退订地址与三种类别；文献与经验的永久链接。
- `PublicationTitleTextTest`、`ExperienceOutcomeTextTest`：标签与实体、MathML 的 `annotation`、首轮即终局、不认识的枚举值。
- `ExcerptRendererTest`（kernel）：补分段的用例，原有用例不变。

**app 层**（Mockito）：`FlushDueRemindersHandlerTest`、`ReminderDeliveryAssemblerTest`（5.3 每一行一个用例；检查顺序：只查库的在调外部服务之前，`subscriptionOf` 在收件人通过之后）、`SendPendingDeliveriesHandlerTest`（依赖不可用与 `TRANSIENT` 只推迟、本轮停下；`ALREADY_ACCEPTED` 记 `SENT`；`REJECTED` 跳过并继续；重试复用同一个投递 ID；发送过且满 24 小时过期；意外异常继续下一条）、`ScheduleDailyDigestHandlerTest`（窗口右端：8:00 正点、8:10 补跑；参数 `users=` 只给列出的人建）。

**集成测试**（infra：Testcontainers PG；boot：PG + GreenMail + WireMock 桩 identity 与 interaction，可变 `Clock`）

| AC | 测试 |
|---|---|
| 不在站上时，回复后 10～12 分钟内发出 | `ReminderMailFlowIT.instant_reply_is_sent_after_ten_minutes`：写入一条回复提醒 → 拨到 9 分 59 秒，跑任务，没有邮件 → 拨到 10 分钟，跑任务，GreenMail 收到一封，有摘录与 `?comment=` 链接 |
| 到点时已读或在站，不发；同一时刻到点的回复与 @ 分成两封 | `…read_before_due_is_not_sent`、`…seen_during_wait_is_not_sent`、`…reply_and_mention_due_together_are_two_mails`、`…later_reply_in_window_joins_the_first` |
| 邮箱未验证时不发 | `…unverified_email_is_skipped`（另有封禁、冷静期、偏好为关） |
| 每日汇总只列未读的回复和 @，没有时不发 | `DailyDigestFlowIT.lists_only_unread_in_window`、`…nothing_unread_sends_nothing`、`…rerun_in_eight_oclock_hour_does_not_send_twice`、`…manual_run_only_targets_listed_users` |
| 邮件头带两个退订头 | `ReminderMailFlowIT.headers_carry_one_click_unsubscribe`：断言 `List-Unsubscribe` 的地址与类别、`List-Unsubscribe-Post`、幂等键，以及正文底部的确认页链接 |
| identity 不可用时推迟重试，恢复后补发、不丢 | `…identity_down_defers_then_sends`：WireMock 返回 503 → 投递仍是 PENDING、`attempts = 1` → 恢复，拨过退避时间 → 发出，只发一封 |
| （2.4）重试复用幂等键、只发一封 | `…transient_failure_retries_with_same_delivery_id`：发信端口先抛 `TRANSIENT`（测试替身包一层，失败 N 次；GreenMail 各上下文共用，不停它）→ `PENDING`、`attempts = 1`、`first_sent_at` 已记 → 恢复后发出一封，`Resend-Idempotency-Key` 与 `Message-ID` 和第一次尝试相同 |

另有 `ReminderQueueRepositoryAdapterIT`、`ReminderDeliveryRepositoryAdapterIT`（唯一约束、CHECK、按到点分页、清理）；`InboxReadAdapterIT` 补两个新方法，`EmailSubscriptionReadAdapterIT` 补 `digestSubscribers`。

AC 写的是「Mailpit 集成测试」。仓库惯例（PAP-105 同）是用 GreenMail 做 SMTP 桩，它能直接断言 `MimeMessage` 的头，CI 不用另拉镜像。Mailpit 留给本机与 mini 手测（D4、D5）。

一封单条、一封多条、一封汇总的 HTML，用 Playwright 离线截图，和画布在 600px、375px 两种宽度下对照，自查后附进验收汇报，不进测试套件。

---

## 12. 上线步骤（追加到上线手册第 7 步）

1. 合并前：推送 Nacos `patra-notification.yaml`（新增 `patra.notification.mail.links.base-url`）。`PATRA_PORTAL_BASE_URL=https://patra.linqibin.dev` 由 PAP-105 写进 `patra-infra/docker/.env.common`（编排会话 10/31 定；mini 的 `mail.env` 里原本没有这个变量，identity 一直在用 tailnet 默认地址，公网打不开，Gmail 一键退订也要求 https 公网地址）。部署后用 `docker exec patra-notification printenv PATRA_PORTAL_BASE_URL` 核对。
2. 合并后：`\dt mail.*` 能看到 `reminder_queue`、`reminder_delivery`。
3. 调度中心在执行器 `patra-notification` 下登记两个任务：
   - `reminderMailJob`：Cron `0 * * * * ?`；路由第一个；阻塞处理选「丢弃后续调度」；超时 300 秒；失败重试 0 次。
   - `dailyDigestJob`：Cron `0 0/10 8 * * ?`；路由第一个；阻塞处理选「丢弃后续调度」；超时 300 秒；失败重试 0 次。
4. 验收（D4、D5）：用测试账号 E 收真实邮件，经 Resend 发出。
   - B 回复 E、E 不在站上：10～12 分钟内收到；
   - E 开着门户，或者先在站内读了：收不到；
   - 把「@ 我的」改成每日汇总，以参数 `users={E 的用户 ID}` 手动触发 `dailyDigestJob`：只收到一封、只列未读的 @；E 读完之后再触发一次（隔一分钟以上）：不发；
   - 第二天 8:00 之后，看调度日志与 `mail.reminder_delivery`：8:00 建一次，8:10～8:50 补跑时没有新建；
   - Gmail 原始邮件里有两个退订头，DKIM 为 PASS。
   - 收尾：把 E 改回逐条。

---

## 13. 与并行 Issue 的接缝

| 对方 | 接缝 | 现状 |
|---|---|---|
| PAP-105 | `MailDeliveryPort#deliver(OutgoingMail)`（`deliveryId` UUID、`to`、`template`、`subject`、`model`、`headers`）；失败分 `ALREADY_ACCEPTED` / `REJECTED` / `TRANSIENT`；端口写固定的 `Message-ID` 与通用头；发送记录不由端口写，本 Issue 以 `reminder_delivery` 为准 | 10/31 已和 PAP-105 对齐；它在 SMTP 下靠错误文字匹配 `idempotent` 判断 `ALREADY_ACCEPTED`，上线后看 mini 日志核实 |
| PAP-105 | `mail` schema 谁建 | 两边都写 `IF NOT EXISTS` |
| PAP-105 | `layout.html` 原样搬过来，本 Issue 加三个可选变量（8.5） | 已请编排会话转达 |
| PAP-105 | 显示时区的配置项 | 有就复用（第 10 节） |
| PAP-143 | `NotificationsCreatedEvent` 在写入事务里发布、条目带 `createdAt`；`NotificationType` 在 `domain.inbox.model.read` | 编排会话已让 PAP-143 挪包（10/31）；合入后核对 |
| PAP-143 | `InboxEntry` 加 `createdAt`；`InboxReadPort`、`ExcerptRenderer` 加方法（第 9 节） | 合入后加 |
| PAP-142 | `EmailSubscriptionReadPort` 加 `digestSubscribers`、`findSubscription` | 已在集成分支，直接加 |
| PAP-148 | 邮件里的链接就是 PAP-142 第 7 节的两个地址，以及 `/settings/notifications`、`/notifications`、`/notifications/mentions` | 无新增契约 |

---

## 14. 考虑过、没选的做法

| 做法 | 为什么不选 |
|---|---|
| 提交后（`AFTER_COMMIT`）再入队 | 提交和入队之间进程一挂，就丢一封（2.2） |
| 内存定时器或 `@Scheduled` 加内存队列 | 每次 CD 重部署都会清空 |
| RabbitMQ 延迟消息（插件或 TTL + 死信） | mini 要装插件；合并多条要攒消息；而事件本来就是进程内的领域事件 |
| 每条各等 10 分钟，只合并同一分钟到点的 | 几分钟里来的几条回复会变成几封邮件；画布与 release spec 都是 Jira 式的合并窗口（2.3） |
| 发信后才写投递记录，幂等键按内容现算 | 重试时窗口里多了一条，键就变了，会重复送达；或者键不变，新来的那条被 Resend 吞掉 |
| 第一次装配就冻结渲染好的邮件，重试只发冻结的 | 要在库里存地址、退订令牌链接和正文；而「上一次其实已被接受、这次内容不同」的情形端口会报 `ALREADY_ACCEPTED`，记为已发即可，冻结换不来更多正确性（2.4） |
| 调用过发信端口之后也无限重试 | 幂等键只保留 24 小时，之后再发可能重复送达；宁可放弃这一封（`EXPIRED`），也不重复 |
| 手动触发给所有设了汇总的人 | 门户已对公网开放，测试会给真实用户多发一封 |
| 入队时就查偏好 | 用户可能在这 10 分钟里改档位；`subscriptionOf` 会插行，不该放进信箱的事务里 |
| 汇总用滚动窗口、没有投递记录 | 8:00 前后跑两次就会重复发；8:00 那次没跑成，这一天的汇总就丢了 |
| 汇总排除已经逐条发过的提醒 | 只在用户把逐条改成汇总时才会碰上，那一条也确实还没读；为此要查已发的投递，不值得 |
| 汇总名单从信箱里「有未读的人」里筛 | 要对每个人调一次 `subscriptionOf`，还会顺带建一行偏好 |
| interaction 挂了也发，只是不带「在哪」和链接 | 「查看并回复」是这封邮件的意义所在 |
| 注销冷静期也发 | 用户已经申请离开（5.3） |
| 摘录里只强调收件人自己的 `@名字` | 站内与门户不区分谁被 @；全部强调更一致，实现也更简单 |

---

## 15. 本 spec 自己定的事（请重点看）

1. 合并窗口按 Jira 的做法：一类里最早那条到点时，带上同类里仍在等的所有提醒（2.3、5.1）。
2. 到点、在站与汇总窗口都按提醒写入信箱的 `createdAt` 算，不按 `occurredAt`（5.1、5.2；和 PAP-143 契约 02:14 版一致）。
3. 偏好、收件人、对象视图、在站都在装配时查，不在入队时查；每次重试都从头装配（5.3、7.3）。
4. 先建投递记录再发信，幂等键用投递 UUID；重试时内容可能变，上一次其实已被接受时端口报 `ALREADY_ACCEPTED`，记为已发，所以不冻结渲染结果，库里不存地址与正文；每日汇总靠唯一约束保证一个窗口一封（2.4、6.2）。
5. 依赖不可用时指数退避（上限 30 分钟）；还没调用过发信端口就一直推迟、不丢；调用过之后最多重试到第一次调用后 24 小时，之后记为 `EXPIRED`；interaction 挂了也推迟，不降级；`REJECTED` 跳过（5.4）。
6. 只给 `ACTIVE` 的账号发，注销冷静期不发（5.3）。
7. 汇总名单在 8 点这一小时里每 10 分钟补建一次；手动验收用参数 `users=…`，只给列出的人建（5.2）。
8. 汇总的 10 条按比例分，回复先拿，但至少给 @ 留 1 条（5.2）。
9. 正文里的 `@名字` 沿用站内的 `MentionNames`，触发人写显示名（8.1）。
10. 文献标题去掉白名单里的标签，转义输出；经验结果段用短写法 `大修 → 录用`，同画布（8.3）。
11. 多条合一时有跨天的条目，时间就全部写上日期（8.4）。
12. 不接注销清理：两张表都只有用户 ID，不存地址与正文（6.2）。
13. 邮件头集成测试用 GreenMail，Mailpit 留给手测（11）。
14. 先查收件人、再调 `subscriptionOf` 取令牌；之前用 preference 新加的不建行读法 `findSubscription` 判断档位（5.3）。
15. 用户把逐条改成汇总时，已经逐条发过、仍未读的提醒会再出现在汇总里，接受（5.3）。

---

## 16. 实施记录（2026-10-31）

### 16.1 与本设计、计划的偏离

1. **内容字段 `settingsUrl` 改名 `preferencesUrl`**：PAP-105 的渲染器用 Thymeleaf 标准方言（OGNL 3.3.4），OGNL 读属性时跳过以 `set` 开头的方法（实测：record 的 `settingsUrl()`、`settings()` 都读不到，其余字段正常）。模板读不到这个字段，所以改名，并在字段注释里写明原因。
2. **门户地址用独立的属性类** `PortalLinkProperties`（`patra.notification.mail.links.base-url`），没有并进 PAP-105 的 `MailDeliveryProperties`：
   - 不改 PAP-105 的类和它的测试；
   - 时区复用 `MailDeliveryProperties.zone`；
   - 地址缺失，或者不是绝对的 http(s) 地址时，启动失败。
3. **摘录分段直接用底座的 `SegmentedExcerpt.Segment`**，没有另建 `ExcerptPart`：同一个概念只留一个类型。
4. **列表里的一条抽成片段** `html/fragments/reminder-item.html`，多条合一与每日汇总共用，上下内边距作为参数（18px / 16px，同画布）。
5. **模板变量在 app 层组装**：`ReminderMailModel` 与 `AsideLink` 放在 `app.mail.service`。模型里有：
   - `mail`：`ReminderMailContent`；
   - 三个顶层变量 `preheader`、`footnote`、`asideLinks`；
   - 不用渲染器保留的 `subject`、`siteName`、`notice` 三个键。
6. **投递记录的写回不包 `TransactionOperations`**：每次都是一条 `UPDATE`，自动提交本身就是短事务，调用发信端口天然在事务外。
7. **失败分类照 PAP-105 的实际契约**：发信端口正常返回就是已接受；失败抛 `MailDeliveryFailedException`，由 `failure()` 区分 `ALREADY_ACCEPTED` / `REJECTED` / `TRANSIENT`。按 PAP-105 的约定，交给 SMTP 之前就失败的（模板不存在、地址不合法）也算 `REJECTED`。
8. **`InboxEntry` 加了 `createdAt`**（PAP-143 的读模型，只增不改）。`NotificationType` 已由 PAP-143 挪到 `domain.inbox.model.read`。

### 16.2 自查

三封邮件的 HTML，用 portal 的 Playwright（Chromium，禁网）在 760px 与 375px 两种宽度下截图，和画布 `Mail.dc.html`、`Digest.dc.html` 对照：

- 版式与画布一致：引用块、`@名字` 强调、分节、超过 10 条的说明、版权页、卡片外的两个链接；
- 窄屏下按钮占满一行。
