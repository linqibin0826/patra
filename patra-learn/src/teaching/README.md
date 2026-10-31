# 教学组件库

条目正文里的交互和强调块都从这里来（决策 D）。**写内容只读这一份**：每个组件干什么、什么时候用、参数、MDX 示例、常见错误。参数由 `registry.ts` 的 zod schema 校验，写错了 `pnpm test` / `pnpm content:check-file <文件>` 会列出「`<组件>` 参数不合法：路径：原因」。

什么时候配图、什么时候插「你猜」，按维护技能的 `teaching.md`；本文只讲组件怎么写。

## 0. 通用写法

1. **组件前后空一行**。组件和段落挤在一起会被当成行内元素，开头 / 结尾判断就不认它。
2. **属性只写字面量**：字符串用 `"…"`；数字、布尔、数组、对象用 `{…}`，例如 `answer="B"`、`col={2}`、`code`（布尔 true 可以只写名字）、`options={["甲", "乙"]}`。不能写变量、表达式、展开 `{...x}`。
3. **开始标签里不要有空行**。参数多时可以一行一个、缩进对齐，但中间不能空行，否则 MDX 认为标签断了。
4. **字符串里的英文双引号**要么换成中文引号「」，要么整个属性改用 `{"…"}` 写 JS 字符串。字符串里只认一种格式：反引号 `` `代码` `` 会渲染成行内代码；其他 Markdown（加粗、链接）不生效。
5. **id** 只用小写字母、数字、横线，字母开头（`notify`、`event-row`）。id 在一个组件里唯一，别的组件里可以重名。
6. **列、行从 1 数**：`col={1}` 是最左一列，`row={1}` 是最上一行，`row` 不写就是第 1 行。
7. **图里的名字用真实代码里的**（`interaction.events`、`event_publication`），设置 `code` 让它用等宽字体。
8. 图示、动画只放结构，道理写在正文。一张图只讲一个关系。
9. 下面的示例取自画板，用来说明写法；里面的技术细节写进条目前照样要以仓库代码为准、用 `CodeRef` 钉住。

各组件一览：

| 组件 | 角色 | 子内容 | 一句话 |
|---|---|---|---|
| `Diagnose` | 开头（必须是第一块） | 不能写 | 单选诊断题，答错推荐前置 |
| `Recap` / `Transfer` | 结尾（必须是最后一块，二选一） | 可选 | 先写，再对照参考要点自评 |
| `Quiz` | — | 不能写 | 文中的「考考自己」，1～5 道单选 |
| `Structure` | — | 不能写 | 结构图：部件与它们之间的关系 |
| `Sequence` | — | 不能写 | 时序图：参与者之间按时间先后的消息 |
| `StateMachine` | — | 不能写 | 状态机：点事件推进状态 |
| `Stepper` | — | 不能写 | 逐步动画，可插「你猜会怎样」 |
| `Params` | — | 不能写 | 参数滑块与开关：参数变了结果怎么变 |
| `Compare` | — | 不能写 | 对比切换：几种写法并排比，可模拟崩溃 |
| `Callout` | — | 必须写 | 强调块：一句话 / 打个比方 / 顺带想通的 / 小心 / 代价 |
| `DebugTrail` | — | 不能写 | 排查路径：一步一个假设，点开看结果 |
| `DecisionOptions` | — | 不能写 | 决策选项：点开看好处与代价 |
| `CodeRef` | — | 不能写 | 代码引用，钉在提交上 |

掌握度（刚接触 / 理解中 / 吃透了）由条目页自带，正文不写。

选择题的共同字段（`Diagnose`、`Quiz` 的每道题、`Stepper` 的 `guesses`、`Params` 的 `guess`）：

| 参数 | 类型 | 说明 |
|---|---|---|
| `question` | 字符串 | 题干 |
| `options` | 字符串数组，2～4 个 | 依次显示为 A、B、C、D |
| `answer` | `"A"`～`"D"` | 正确项的字母，必须落在选项范围内 |
| `explain` | 字符串 | 作答后显示的解释（`Diagnose` 例外，见下） |

## 1. Diagnose：开头诊断题

**干什么**：正文第一块。一道凭直觉能作答的单选题，答案能看出读者缺不缺前置。答对给一句肯定，答错或点「我答不上来」时给解释，并推荐一篇本篇的前置（页面从 frontmatter 的 `prerequisites` 取标题与难度，你不用写链接）。读者可以「我直接往下读」，题目收成一行，能「重新答」。

**什么时候用**：每篇都要，且只能放第一块。

| 参数 | 必填 | 说明 |
|---|---|---|
| `question` `options` `answer` | 是 | 见上表 |
| `ifRight` | 是 | 答对后显示：确认 + 一句「往下读会讲什么」 |
| `ifWrong` | 是 | 答错 / 答不上来时显示：正确答案是什么、这题考的是哪个前置知识 |
| `suggest` | 否 | 答错时推荐哪篇前置（写 slug，要是 frontmatter `prerequisites` 里的一篇）；不写推荐第一篇；没有前置时只显示 `ifWrong` |

```mdx
<Diagnose
  question="一个请求先往数据库写了一行（事务还没提交），接着往 RabbitMQ 发了一条消息。这时进程崩了，会怎样？"
  options={["那一行和消息都在", "那一行回滚了，消息已经发出去了", "那一行和消息都没有"]}
  answer="B"
  ifRight="没提交的那行会回滚，可消息已经发出去，收不回来。这正是这篇要解决的问题。"
  ifWrong="答案是 B：没提交的那行会回滚，消息却已经发出去了。这题考的是「事务提交前崩了会怎样」，是读懂这篇的前提。"
  suggest="msg-dual-write-problem"
/>
```

**常见错误**：问「你了解 X 吗」（只能答是或否，改成要用到 X 才能答对的判断题）；在标签之间写答案（`Diagnose` 不能写子内容，解释写进 `ifRight` / `ifWrong`）；`answer` 写成数字或选项原文。

## 2. Recap / Transfer：结尾复述题与迁移题

**干什么**：正文最后一块，二选一。读者先在文本框里写（不保存、不提交），点「写好了，对照参考」或「想不出来，直接看」后出现参考要点，读者逐条打勾自评，组件给出「想到了几条」。

- `Transfer` 迁移题：换个场景还成立吗、换个参数会怎样。**能出迁移题就出迁移题。**
- `Recap` 复述题：用自己的话说出 X 为什么会 Y。

| 参数 | 必填 | 说明 |
|---|---|---|
| `prompt` | 是 | 题目 |
| `points` | 是 | 参考要点，2～5 条，每条 `{ label?, text }`；`label` 是加粗的短标题（不带冒号） |
| `placeholder` | 否 | 文本框里的提示，默认「比如：我会先……」/「比如：它解决的是……」 |

子内容可选：写了就在参考要点下面多显示一段 Markdown 参考答案（可以有代码块）。

```mdx
<Transfer
  prompt="一个电商系统：用户下单后要给仓库系统发一条「备货」消息。订单存在 MySQL，消息走 Kafka。你会怎么保证「下单成功，就一定会发出备货消息」？仓库那边要注意什么？"
  points={[
    { label: "同一个事务", text: "订单和一行「待发的备货消息」一起写进 MySQL，同一个事务提交。" },
    { label: "提交后再发", text: "由单独的发送任务把待发行发给 Kafka，确认发成功再删掉或标记已发；没成功的下次再发。" },
    { label: "仓库要去重", text: "同一条备货消息可能到两次，仓库按消息 ID 判断处理过没有。" }
  ]}
/>
```

```mdx
<Recap
  prompt="不看上文，用两三句话说说：发件箱解决了什么问题，怎么做，代价是什么？"
  points={[
    { label: "解决什么", text: "业务数据和要发的消息在两个系统里，没法一起提交；进程崩在中间，消息就丢了。" },
    { label: "怎么做", text: "把消息先写成一行登记，和业务数据同一个事务提交；提交后再发，发成功才删。" },
    { label: "代价", text: "回执没到就崩，补投会再发一次，消费方必须按事件 ID 去重。" }
  ]}
  placeholder="比如：它解决的是……"
>

想得更细的话，可以再对照一下 Patra 的补投间隔：`PT1M`，也就是每分钟扫一次。

</Recap>
```

**常见错误**：结尾写要点总结（改成迁移题或复述题）；只写一条要点（自评至少要 2 条）；`Recap` 后面又写了一段正文（结尾组件必须是最后一块）。

## 3. Quiz：考考自己

**干什么**：正文中间的小测。读者先选，再点「对一下答案」；答错可以「再试一次」；多题有进度条，做完显示「第一次就答对几题」。

**什么时候用**：讲完一个机制、读者可能自以为懂了的时候。一篇最多一两处。

| 参数 | 必填 | 说明 |
|---|---|---|
| `questions` | 是 | 1～5 道，每道 `{ question, options, answer, explain }` |
| `title` | 否 | 标题，默认「考考自己」 |

```mdx
<Quiz
  questions={[
    {
      question: "登记行最后是谁、在什么时候发出去的？",
      options: ["业务代码在事务提交前发", "提交后由 Modulith 外送；没发成功的，补投任务每分钟再发", "RabbitMQ 定时从数据库里读"],
      answer: "B",
      explain: "提交后 Spring Modulith 异步外送一次；没收到回执的留在表里，每分钟的补投任务再发。"
    },
    {
      question: "「取消有帮助」这个事件，为什么不外送？",
      options: ["它不重要，丢了也无所谓", "没有队列订阅它：发出去会被退回，登记行就永远补投", "RabbitMQ 不支持撤销类的消息"],
      answer: "B",
      explain: "路由不到任何队列的消息会被退回。外送的只有真有人订阅的事件。"
    }
  ]}
/>
```

**常见错误**：把开头诊断题写成 `Quiz`（开头必须是 `Diagnose`）；对象里的键写成 `"question":`（可以，但不必）或漏了逗号。

## 4. Structure：结构图

**干什么**：画几个部件和它们之间的一种关系。读者点方块或连线，其余变淡，下方说明换成它的职责。可选「发一条」按钮：一个消息块沿给定路径一跳一跳走过去，每跳一句说明。

**什么时候用**：几个部件之间的关系（谁发给谁、谁依赖谁）。纯名词解释不用。

| 参数 | 必填 | 说明 |
|---|---|---|
| `title` | 是 | 图的标题 |
| `intro` | 否 | 没选中任何东西时的说明，默认「点任意一块看它管什么」 |
| `nodes` | 是 | 2～12 个，每个 `{ id, label, sub?, code?, col, row?, desc }`：`sub` 是第二行小字，`code` 用等宽字体，`col` / `row` 是网格位置（从 1 数），`desc` 是点中后的说明 |
| `edges` | 否 | 连线，每条 `{ from, to, label?, desc?, dashed? }`，箭头从 `from` 指向 `to`；写了 `desc` 的连线可以点 |
| `traces` | 否 | 最多 3 个「发一条」按钮，每个 `{ button, packet, hops }`：`packet` 是消息块上的字，`hops` 是 2～8 跳 `{ at, caption, drop? }`，最后一跳 `drop` 表示消息在这里被丢掉 |

```mdx
<Structure
  title="一条消息怎么找到它的队列"
  nodes={[
    { id: "producer", label: "interaction", sub: "生产者", col: 1, desc: "只管把消息交给交换机、带上路由键。它不知道有哪些队列。" },
    { id: "exchange", label: "interaction.events", sub: "交换机 · topic", code: true, col: 2, desc: "不存消息，只按路由键转发给绑定了它的队列。" },
    { id: "inbox", label: "notification.inbox", sub: "队列", code: true, col: 3, desc: "真正把消息存下来，等消费者来取；消费者回 ack 后才删掉。" }
  ]}
  edges={[
    { from: "producer", to: "exchange" },
    { from: "exchange", to: "inbox", label: "绑定 · 5 个路由键", desc: "「这个队列要接这个交换机里、路由键是这些的消息」。" }
  ]}
  traces={[
    {
      button: "发一条 comment.published",
      packet: "comment",
      hops: [
        { at: "producer", caption: "interaction 把消息交给 interaction.events。" },
        { at: "exchange", caption: "交换机查绑定：notification.inbox 绑了这个键。" },
        { at: "inbox", caption: "消息进了 notification.inbox，等通知服务来取。" }
      ]
    },
    {
      button: "发一条没人订阅的",
      packet: "无订阅",
      hops: [
        { at: "producer", caption: "interaction 照样把它交给交换机。" },
        { at: "exchange", caption: "没有任何队列绑了这个路由键，消息被丢掉。", drop: true }
      ]
    }
  ]}
/>
```

**常见错误**：两个节点放在同一格；连线引用了没声明的 id；一张图里同时画调用链和数据流（拆成两张）。

## 5. Sequence：时序图

**干什么**：几个参与者（竖线）之间按时间先后的消息。读者点「下一条」一条条往下走，没走到的变淡；左侧显示累计耗时；点图上任意一条消息直接跳过去；走完显示总结。

**什么时候用**：一次请求经过谁、时间花在哪；排查条目的「根因」。

| 参数 | 必填 | 说明 |
|---|---|---|
| `title` | 是 | 标题 |
| `actors` | 是 | 2～5 个参与者 `{ id, label, sub? }`，从左到右 |
| `messages` | 是 | 1～12 条 `{ from, to, label, caption, time?, bad?, dashed? }`：`label` 写在箭头上，`caption` 是走到这一条时的说明，`time` 是到这一步累计的时间（如 `"6.0 s"`），`bad` 标成红色（卡住、出错的那条），`dashed` 画虚线（等待、超时） |
| `summary` | 否 | 走完后的总结 `{ time?, caption }` |

```mdx
<Sequence
  title="那 6 秒花在了哪"
  actors={[
    { id: "browser", label: "浏览器" },
    { id: "dns", label: "DNS", sub: "被代理软件接管" },
    { id: "proxy", label: "代理" },
    { id: "mini", label: "mini" }
  ]}
  messages={[
    { from: "browser", to: "dns", label: "mini 的地址是？", time: "0 ms", caption: "浏览器要先知道 mini 的地址，去问 DNS。" },
    { from: "dns", to: "browser", label: "假地址 + 真地址", time: "1 ms", caption: "代理软件开着 fake-ip：先编一个假地址给你，真地址排在后面。" },
    { from: "browser", to: "proxy", label: "先连假地址", time: "2 ms", caption: "浏览器拿第一个地址去连，连接交给了代理。" },
    { from: "proxy", to: "mini", label: "拨号，6 秒没通", time: "6.0 s", bad: true, dashed: true, caption: "这条路到不了 mini，代理一直等到 6 秒超时。" },
    { from: "proxy", to: "browser", label: "连接失败", time: "6.0 s", caption: "代理告诉浏览器：连不上。" },
    { from: "browser", to: "mini", label: "换真地址直连", time: "6.06 s", caption: "浏览器换下一个地址直连 mini，60 毫秒就回来了。" }
  ]}
  summary={{ time: "6.06 s", caption: "一共 6 秒：5.99 秒在等一条到不了的路。" }}
/>
```

**常见错误**：`from` 和 `to` 写成同一个参与者；把查问题的经过做成时序图（经过用 `DebugTrail` 或有序的节）。

## 6. StateMachine：状态机

**干什么**：一个东西的几种状态和让它变化的事件。读者点事件按钮推进，当前状态弹一下；点了当前状态下不会发生的事件，说明条抖动变红，并列出现在能发生的事件（这段说明组件自动生成，你不用写）。底部显示走过的路径，可「从头来」。

**什么时候用**：一行数据、一个连接、一个订单的生命周期。

| 参数 | 必填 | 说明 |
|---|---|---|
| `title` | 是 | 标题 |
| `subject` | 否 | 状态机描述的对象，显示在右上角（如 `"event_publication"`） |
| `states` | 是 | 2～8 个 `{ id, label, sub?, desc, col, row?, dashed? }`：`desc` 是处在这个状态时的说明，`dashed` 画虚线框（如「从未存在」） |
| `start` | 是 | 起始状态的 id |
| `events` | 是 | 1～8 个事件按钮 `{ id, label }` |
| `transitions` | 是 | `{ from, event, to, label? }`：在 `from` 状态发生 `event` 就到 `to`；`label` 是连线上的短字，不写用事件名 |

```mdx
<StateMachine
  title="一行登记的一生"
  subject="event_publication"
  states={[
    { id: "in-tx", label: "事务中", sub: "还没提交", col: 1, desc: "业务数据和登记行都写了，但还没提交。这时崩了，两样一起消失。" },
    { id: "pending", label: "待外送", sub: "登记行在表里", col: 2, desc: "登记行已经提交，消息还没发。崩了也不怕：补投任务会找到它。" },
    { id: "sending", label: "外送中", sub: "等回执", col: 3, desc: "消息交给了 RabbitMQ，正在等回执。回执到了才算数。" },
    { id: "deleted", label: "已删除", sub: "发成功了", col: 4, desc: "回执到了、也没被退回，登记行删掉。" },
    { id: "gone", label: "从未存在", col: 1, row: 2, dashed: true, desc: "事务回滚，登记行和业务数据一起没了。" }
  ]}
  start="in-tx"
  events={[
    { id: "commit", label: "提交事务" },
    { id: "rollback", label: "事务回滚" },
    { id: "send", label: "外送 / 补投任务到点" },
    { id: "ack", label: "收到 ack，没被退回" },
    { id: "back", label: "被退回，或回执没来" }
  ]}
  transitions={[
    { from: "in-tx", event: "commit", to: "pending", label: "提交" },
    { from: "in-tx", event: "rollback", to: "gone", label: "回滚" },
    { from: "pending", event: "send", to: "sending", label: "外送" },
    { from: "sending", event: "ack", to: "deleted", label: "ack" },
    { from: "sending", event: "back", to: "pending", label: "被退回" }
  ]}
/>
```

**常见错误**：同一状态下同一事件写了两条转移；`start` 写成状态的名字而不是 id。

## 7. Stepper：逐步动画 + 你猜会怎样

**干什么**：一个机制拆成几步，读者点「下一步」才往前走，「上一步」倒回去（动画反向播）。每一步：说明换一句、点亮相关节点与连线、消息块（token）在节点间移动、节点下方的小框（note）显示计数或状态。在读者最可能猜错的那一步之前插「你猜会怎样」：选完才能继续，也可以「不猜，直接看」。不会自动连播。

**什么时候用**：一条流程的先后、会出意外的那一步。一步只变一个东西。

| 参数 | 必填 | 说明 |
|---|---|---|
| `title` | 是 | 标题 |
| `nodes` | 是 | 2～5 个节点 `{ id, label, sub? }`，从左到右排成一行 |
| `links` | 否 | 节点间的连线 `{ from, to, label? }` |
| `steps` | 是 | 2～10 步，每步 `{ caption, focus?, links?, tokens?, notes? }`，见下 |
| `guesses` | 否 | 最多 3 道「你猜」，每道是选择题 + `before`：在显示第几步之前问（从 1 数，至少 2） |

每一步里：

- `focus`：这一步点亮的节点 id；写了的点亮，其余变淡；不写都正常显示。
- `links`：这一步点亮的连线，写成 `"起点>终点"`，必须先在顶层 `links` 里声明。
- `tokens`：这一步各消息块在哪，`{ id, label, at, state? }`。同一个 `id` 在相邻两步里 `at` 不同，就会从前一个节点移动到后一个；`state` 写 `"faded"` 变淡，写 `"dropped"` 划掉（被丢弃）。某一步没写某个 token，它就淡出。
- `notes`：节点下方的小框 `{ at, text, tone? }`，`tone` 写 `"wait"`（琥珀）、`"ok"`（绿）、`"bad"`（红）。

```mdx
<Stepper
  title="一条回复提醒，怎么从发件箱送到 E"
  nodes={[
    { id: "interaction", label: "interaction", sub: "写评论" },
    { id: "db", label: "PostgreSQL", sub: "评论 + 登记行" },
    { id: "mq", label: "RabbitMQ", sub: "交换机" },
    { id: "notify", label: "notification", sub: "给 E 发提醒" }
  ]}
  links={[
    { from: "interaction", to: "db" },
    { from: "db", to: "mq" },
    { from: "mq", to: "notify" }
  ]}
  steps={[
    { caption: "陈宇回复了 E 的评论。interaction 准备把评论写进数据库。", focus: ["interaction"], tokens: [{ id: "first", label: "#7f3a", at: "interaction" }] },
    { caption: "同一个事务里：写评论，再写一行登记。提交之后两样一起生效。", focus: ["db"], links: ["interaction>db"], tokens: [{ id: "first", label: "#7f3a", at: "db" }], notes: [{ at: "db", text: "登记表 · 1 行", tone: "wait" }] },
    { caption: "提交后第一次外送：E 收到了提醒，可 RabbitMQ 的回执在路上丢了，登记行还留着。", focus: ["mq", "notify"], links: ["db>mq", "mq>notify"], tokens: [{ id: "first", label: "#7f3a", at: "notify" }], notes: [{ at: "db", text: "登记表 · 1 行", tone: "wait" }, { at: "notify", text: "E 的提醒 · 1 条", tone: "ok" }] },
    { caption: "补投任务每分钟扫一次登记表，发现这一行，又发了一次。这次回执到了，登记行删掉。", focus: ["mq", "notify"], links: ["db>mq", "mq>notify"], tokens: [{ id: "first", label: "#7f3a", at: "notify", state: "faded" }, { id: "second", label: "#7f3a", at: "notify" }], notes: [{ at: "db", text: "登记表 · 0 行" }, { at: "notify", text: "E 的提醒 · 1 条", tone: "ok" }] },
    { caption: "notification 记着处理过的事件 ID。第二次到的是同一个 ID，直接丢掉。E 只收到 1 条提醒。", focus: ["notify"], tokens: [{ id: "second", label: "#7f3a 重复", at: "notify", state: "dropped" }], notes: [{ at: "notify", text: "E 的提醒 · 1 条", tone: "ok" }] }
  ]}
  guesses={[
    { before: 3, question: "提交后消息送到了 notification，可回执在路上丢了。这时登记行会怎样？", options: ["被删掉，消息反正已经送到了", "留在表里，因为没收到回执", "被移进死信队列"], answer: "B", explain: "发件箱只认回执：没收到 ack，就当这次没发成功，登记行留着等补投。" },
    { before: 5, question: "同一条提醒先后到了 notification 两次。E 会收到几条？", options: ["1 条", "2 条", "要看 notification 有没有去重"], answer: "C", explain: "发件箱保证不丢，不保证不重。会不会重复，取决于消费方有没有按事件 ID 去重。" }
  ]}
/>
```

**常见错误**：把整个机制塞进两三步（一步只变一个东西）；`before={1}`（第一步之前没有东西可猜，从 2 起）；步骤里引用了没声明的连线；「你猜」的答案已经写在上一步的 `caption` 里。

## 8. Params：参数滑块与开关

**干什么**：几个开关和滑块，右侧实时给出结果表和一句结论。可选先问读者预测（`guess`）：答完或「不猜，直接试」后控件才能动。

**什么时候用**：「参数变了结果怎么变」，尤其是某个组合会踩坑的时候。

| 参数 | 必填 | 说明 |
|---|---|---|
| `title` | 是 | 标题 |
| `guess` | 否 | 动手前的预测题（选择题字段 + `explain`） |
| `controls` | 是 | 1～5 个控件：开关 `{ type: "switch", id, label, default }`（`default` 是 true / false）；滑块 `{ type: "slider", id, label, min, max, step, default, unit? }` |
| `cases` | 是 | 结果表，**从上往下第一个匹配的生效**，每个 `{ when?, results?, verdict }`；最后一个不写 `when`，作为其余组合的兜底 |

- `when`：`{ 开关id: true/false, 滑块id: [下限, 上限] }`，写了的都满足才算匹配；没写的控件不管。
- `results`：结果表的行 `{ label, value, tone? }`。`value` 是一个字符串，或按先后的一串徽章 `[{ text, tone }]`（显示成「A 然后 B」）。
- `verdict`：一句结论 `{ tone: "ok" | "bad", head?, text }`。
- 文字里写 `{滑块id}` 会替换成当前值加单位，例如 `"每 {interval} 补投一次"`。

```mdx
<Params
  title="动手试试：生产者到底收到了什么"
  guess={{ question: "没有队列绑定这个路由键、发送时带了 mandatory，生产者会收到什么？", options: ["只有 ack", "先退回，再 ack", "只有退回"], answer: "B", explain: "路由不到的消息先被退回，broker 随后照样回 ack。" }}
  controls={[
    { type: "switch", id: "bound", label: "有队列绑定了这个路由键", default: false },
    { type: "switch", id: "mandatory", label: "发送时带 `mandatory`", default: true },
    { type: "switch", id: "check-return", label: "代码先看退回，再看 ack", default: false },
    { type: "slider", id: "interval", label: "补投间隔", min: 10, max: 300, step: 10, default: 60, unit: "秒" }
  ]}
  cases={[
    { when: { bound: true }, results: [{ label: "生产者依次收到", value: "ack", tone: "ok" }, { label: "登记行", value: "删除", tone: "ok" }], verdict: { tone: "ok", head: "正常。", text: "有队列接，broker 回 ack，登记行删掉。" } },
    { when: { mandatory: false }, results: [{ label: "生产者依次收到", value: "ack", tone: "ok" }, { label: "消息去哪了", value: "悄悄丢掉", tone: "bad" }], verdict: { tone: "bad", head: "连退回都没有。", text: "不带 mandatory，路由不到队列的消息直接丢掉，照样回 ack。" } },
    { when: { "check-return": false }, results: [{ label: "生产者依次收到", value: [{ text: "退回 basic.return", tone: "wait" }, { text: "ack", tone: "ok" }] }, { label: "登记行", value: "删除", tone: "bad" }], verdict: { tone: "bad", head: "这就是坑。", text: "代码只看 ack，以为发成功了，登记行被删，提醒就这么没了。" } },
    { results: [{ label: "生产者依次收到", value: [{ text: "退回 basic.return", tone: "wait" }, { text: "ack", tone: "ok" }] }, { label: "登记行", value: "留着，每 {interval} 补投", tone: "wait" }], verdict: { tone: "ok", head: "没丢，但发不出去。", text: "先看到退回，登记行留着；可没有队列，它每 {interval} 就被重发一次。" } }
  ]}
/>
```

**常见错误**：最后一个 case 也写了 `when`（没有兜底，某些组合会显示「这个组合没有写结果」）；`when` 里开关写成 `"true"` 字符串；id 带横线时在 `when` 里没加引号（`{ "check-return": false }`）。

## 9. Compare：对比切换

**干什么**：分段切换几种写法，每种写法列出执行顺序与结果；可选一个开关模拟「崩在第 N 步之后」，崩溃点之后的步骤划掉，结果换成崩溃时的样子。

**什么时候用**：两三种做法并排比，尤其是「只看顺利时都一样，出意外才看出差别」。

| 参数 | 必填 | 说明 |
|---|---|---|
| `title` | 是 | 标题 |
| `variants` | 是 | 2～4 种写法，每种 `{ label, steps, results?, verdict, failure? }`：`steps` 是 2～8 步执行顺序，`results` / `verdict` 是顺利时的结果（形状同 `Params`） |
| `failureLabel` | 否 | 开关上的字（如「在第 2、3 步之间崩溃」）；写了它，每种写法都要写 `failure` |

`failure` 是 `{ after, results?, verdict }`：崩在第 `after` 步之后（`after` 小于步骤数）。

```mdx
<Compare
  title="三种写法，崩在中间会怎样"
  failureLabel="在第 2、3 步之间崩溃"
  variants={[
    { label: "先提交，再发", steps: ["写评论", "提交事务", "发提醒消息到 RabbitMQ"], results: [{ label: "PostgreSQL · 评论", value: "在", tone: "ok" }, { label: "RabbitMQ · 提醒", value: "发出了", tone: "ok" }], verdict: { tone: "ok", head: "两样都成功。", text: "只看顺利的时候，三种写法没有区别。" }, failure: { after: 2, results: [{ label: "PostgreSQL · 评论", value: "在", tone: "ok" }, { label: "RabbitMQ · 提醒", value: "没有", tone: "bad" }], verdict: { tone: "bad", head: "提醒丢了。", text: "评论好好地在，可没有任何地方记得还欠一条提醒。" } } },
    { label: "先发，再提交", steps: ["写评论", "发提醒消息到 RabbitMQ", "提交事务"], results: [{ label: "PostgreSQL · 评论", value: "在", tone: "ok" }, { label: "RabbitMQ · 提醒", value: "发出了", tone: "ok" }], verdict: { tone: "ok", head: "两样都成功。", text: "只看顺利的时候，三种写法没有区别。" }, failure: { after: 2, results: [{ label: "PostgreSQL · 评论", value: "回滚了", tone: "bad" }, { label: "RabbitMQ · 提醒", value: "发出了", tone: "ok" }], verdict: { tone: "bad", head: "提醒指向空气。", text: "E 收到「有人回复了你」，那条评论却跟着事务回滚了。" } } },
    { label: "发件箱", steps: ["写评论，同时写一行登记（同一个事务）", "提交事务", "把登记行发到 RabbitMQ，成功后删掉"], results: [{ label: "PostgreSQL · 登记行", value: "已删除", tone: "ok" }, { label: "RabbitMQ · 提醒", value: "发出了", tone: "ok" }], verdict: { tone: "ok", head: "两样都成功。", text: "只看顺利的时候，三种写法没有区别。" }, failure: { after: 2, results: [{ label: "PostgreSQL · 登记行", value: "还在", tone: "wait" }, { label: "RabbitMQ · 提醒", value: "等补投", tone: "wait" }], verdict: { tone: "ok", head: "只是晚到一会儿。", text: "登记行和评论一起提交了；补投任务每分钟扫一次，把它再发出去。" } } }
  ]}
/>
```

**常见错误**：只给一部分写法写 `failure`；`after` 等于步骤数（崩在最后一步之后等于没崩）。

## 10. Callout：强调块

**干什么**：正文里的几种强调面板，内容写 Markdown（可以有段落、列表、行内代码、链接）。

| `kind` | 默认标题 | 用在 |
|---|---|---|
| `lead` | 一句话 | 概念篇开头的一句话定义；排查篇的「结论先说」、坑篇的「坑在哪」、决策篇的「选了什么」（用 `label` 改标题） |
| `analogy` | 打个比方 | 用日常例子类比 |
| `insight` | 顺带想通的 | 排查中顺带解释的现象；坑篇的「怎么认出它」 |
| `warn` | 小心 | 容易出错的地方 |
| `cost` | 代价 | 这样做要付出什么 |

`label` 可选，改掉默认标题。子内容必须写，前后各空一行。

```mdx
<Callout kind="lead" label="结论先说">

代理软件的 fake-ip 把 mini 的主机名解析成了假地址，浏览器拿着假地址走代理，等 6 秒超时才换回真地址。

- **怎么修**：把 tailnet 的域名加进 fake-ip 过滤名单，再加一条直连规则兜底
- **值得记住**：假地址在查地址那一步就产生了，只改路由规则不够

</Callout>

<Callout kind="analogy">

发件箱像寄挂号信前先在登记簿上记一笔：信寄丢了，登记簿还在，第二天照着再寄一次。

</Callout>

<Callout kind="warn">

回执还没到时进程崩了，补投会再发一次。消费方必须按事件 ID 去重。

</Callout>
```

**常见错误**：把整节正文都塞进 `Callout`（它只放一两段要强调的话）；标签和内容挤在一行（内容要单独成段）。

## 11. DebugTrail：排查路径

**干什么**：排查条目「一路怎么查过来的」。一步一个假设，读者先读假设、想想自己会怎么测，再点「测一下，看结果」展开结果；被排除的假设划掉，方向对的标琥珀色，找到根因的标水青色。

**什么时候用**：排查条目的经过。经过不做成动画，用它或有序的节。

| 参数 | 必填 | 说明 |
|---|---|---|
| `steps` | 是 | 2～8 步，每步 `{ hypothesis, result, verdict }`；`verdict` 是 `"ruled-out"`（排除）、`"closer"`（方向对）、`"found"`（找到根因），最后一步必须是 `"found"` |

```mdx
<DebugTrail
  steps={[
    { hypothesis: "是 mini 上的服务慢？", result: "在 mini 本机用 localhost 访问，每次都在 100 毫秒内。不是服务。", verdict: "ruled-out" },
    { hypothesis: "是网络链路慢？", result: "ping mini 的真实地址只要 0.5 毫秒，链路本身很快。", verdict: "ruled-out" },
    { hypothesis: "是代理在中间绕了路？", result: "`curl --noproxy '*'` 62 毫秒，走代理 6.2 秒。慢在走代理的那条路上。", verdict: "closer" },
    { hypothesis: "主机名被解析成了什么？", result: "解析结果第一个是 198.18 段的假地址：代理软件的 fake-ip 接管了 DNS。", verdict: "found" }
  ]}
/>
```

**常见错误**：只写找到根因那一步（排除的假设同样有教学价值）；`result` 里直接写出下一步的假设。

## 12. DecisionOptions：决策选项

**干什么**：决策条目里的 2～4 个选项卡片，点开看好处与代价；选中的那个标「选了这个」。默认展开选中的那个。

| 参数 | 必填 | 说明 |
|---|---|---|
| `options` | 是 | 2～4 个 `{ name, summary, pros, cons, chosen? }`：`summary` 一句话说是什么，`pros` 好处，`cons` 代价；有且只有一个 `chosen` |

```mdx
<DecisionOptions
  options={[
    { name: "签发方独占", summary: "只有 identity 读写会话，网关每次调接口查。", pros: "会话的规则只在一个地方，改起来不牵连网关。", cons: "每个请求多一次服务间调用。", chosen: true },
    { name: "网关自持", summary: "网关自己存会话，签一份断言给下游。", pros: "少一次调用。", cons: "登录、注销的规则散在两个服务里。" },
    { name: "共享存储", summary: "identity 与网关都直接连同一个 Redis。", pros: "实现最快。", cons: "两个服务绑在同一份数据结构上，谁改都要两边一起改。" }
  ]}
/>
```

**常见错误**：没有或有两个 `chosen`；把「选了什么、为什么」写进选项（写在后面的正文或 `Callout kind="lead"`）。

## 13. CodeRef：代码引用

**干什么**：钉住一段真实代码的出处，页面链到 GitHub 上那次提交的永久地址。代码本身用 Markdown 代码块贴在它下面。

| 参数 | 必填 | 说明 |
|---|---|---|
| `at` | 是 | `路径@40位提交SHA`，路径相对仓库根；代码还没进 main 写 `路径@pending` |
| `lines` | 否 | 行号范围，如 `"40-72"` |

```mdx
<CodeRef at="patra-learn/package.json@pending" lines="1-10" />
```

**常见错误**：SHA 只写 7 位；路径写成相对 `patra-learn/` 或绝对路径（一律相对仓库根，如 `patra-api/patra-tool/...`）。
