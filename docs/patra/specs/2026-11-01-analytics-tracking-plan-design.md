# 埋点方案（Tracking Plan）设计

> **Issue**：PAP-164（v0.18 Analytics，release spec [`v0.18-analytics.md`](../release-specs/v0.18-analytics.md) 第 2.1 节、决策 A / F / H / J）
> **日期**：2026-11-01
> **产物**：本文（事件字典与口径）+ `patra-api/patra-analytics/patra-analytics-api/src/main/resources/analytics/schema/` 下的 JSON Schema
> **使用方**：PAP-167 采集端点（校验、补字段）、PAP-168 入库建模（列与口径）、PAP-170 计数与热读（口径）、PAP-171 模拟器（造事件）、PAP-172 面板（指标定义）、PAP-173 门户埋点（发什么、什么时候发）

---

## 1. 这份文档是什么、防什么

**埋点方案**是公司里做行为数据的第一份文档：在写任何采集代码之前，先把「记哪些事、每件事带哪些字段、数字怎么算」定成一份所有人共用的规格。产品看它确认指标口径，前端照它埋点，后端照它校验和建表，数据分析照它写查询。

它要防的是行为数据最常见的三种烂法：

1. **同一件事有好几个名字**：前端叫 `view_paper`，后端叫 `ArticleView`，面板里又写成 `pv_detail`，没人说得清哪个是哪个。
2. **字段随手加**：今天某个事件多带一个字段，明天又换个名字，半年后表里有 40 列，一半永远是空的。
3. **数字对不上**：门户显示 120 次浏览，面板算出来 87，因为两边对「一次浏览」的定义不同。

所以这份文档只做三件事：**定名字**（事件字典）、**定格式**（信封与属性，落成 JSON Schema，机器校验）、**定口径**（浏览、全文、会话、曝光、爬虫怎么算），每条口径都写成能直接拿来写测试的规则。

## 2. 三种形态：事件在链路上长什么样

一条事件从产生到入库，会经过三次「换形」。每一种形态都有自己的 schema：

```text
浏览器 ──① 浏览器批次──▶ 门户服务端 ──② 采集信封──▶ 网关 ─▶ patra-analytics 采集端点 ──③ 富化事件──▶ Kafka events.raw ─▶ ClickHouse
                         门户服务端自己产生的事件直接组装成 ②
```

| 形态 | 谁写 | 谁读 | 有什么 | 没有什么 |
|---|---|---|---|---|
| ① 浏览器批次 `browser-batch` | 门户前端 JS | 门户的第一方 Route Handler | 事件 ID、事件名、版本、浏览器时间、页面、事件属性 | 设备、会话、用户、IP、UA（浏览器拿不到 httpOnly Cookie，也不该自报身份） |
| ② 采集信封 `collect-envelope` | 门户服务端 | 采集端点 | ① 的全部 + 设备 ID、会话 ID、用户 ID、来源、IP、UA、是否合成 | — |
| ③ 富化事件 `enriched-event` | 采集端点 | 入库消费者、模拟器灌历史 | ② 去掉 IP 与原始 UA，加上服务端接收时间、校正时间、地区、UA 解析结果、爬虫标记 | IP、原始 UA、用户名 |

**为什么分三种**：每一跳的信任程度不同。浏览器发来的东西谁都能伪造，所以 ① 里不能有身份字段；身份由门户服务端从 Cookie 和会话里取，填进 ②。IP 和 UA 只用来算地区和判爬虫，算完就丢，所以 ③（要落盘的那份）里没有它们。这就是 release spec 决策 J「不存 IP」落到格式上的样子。

**谁能调采集端点**：采集端点只经网关、只给门户服务端和模拟器调；浏览器只能打门户自己的路径。所以采集端点信任 ② 里的 `user_id`（与 Segment、PostHog 服务端 SDK 直接上报 `userId` 一样）。网关上采集路由不做登录校验，靠它不对公网暴露（公网只开放了门户）。

## 3. 事件字典

### 3.1 命名规则

- **对外的展示名**用「对象 + 动作过去式」（Object Action，Segment 的规范）：`Article Viewed`、`Search Performed`。
- **机器值**（`event_name`）是展示名的 snake_case：`article_viewed`、`search_performed`。代码、JSON、ClickHouse 里只用机器值。
- 一个事件名只表示一件事；会变的值放属性里，不放事件名里（不要 `article_viewed_from_search`，而是 `article_viewed` 加属性）。
- 对象一律叫 `article`（文献），与门户代码里的 `Paper` 无关：事件名是对外契约，按业界通用词起，和内部类名脱钩。

### 3.2 首批 10 个事件

| 展示名 | `event_name` | 来源 | 什么时候发 | 属性 |
|---|---|---|---|---|
| Article Viewed | `article_viewed` | 门户服务端 | 文献详情页服务端渲染完成、文献存在时；**预取请求不发**（§6.6） | `article_id` |
| Search Performed | `search_performed` | 门户服务端 | 检索页服务端拿到结果时（翻页、改筛选、改排序都算一次新检索；无任何条件的浏览态不发） | `search_id`、`mode`、`query`、`filters`、`sort`、`page`、`result_count` |
| PDF Downloaded | `pdf_downloaded` | 门户服务端 | PDF Route Handler 开始向浏览器回传文件时 | `article_id` |
| Export Completed | `export_completed` | 门户服务端 | 导出 Route Handler 成功拿到 catalog 的文件、开始回传时 | `format`、`scope`、`article_count` |
| Article Collected | `article_collected` | 门户服务端 | 收藏写入成功后（取消收藏不发） | `article_id` |
| Page Viewed | `page_viewed` | 浏览器 | 每次进入一个页面（首次加载与站内跳转都算），页面可见时 | （无，页面信息在信封里） |
| Article Impressed | `article_impressed` | 浏览器 | 文献卡片满足曝光规则（§6.4）时，每个列表实例里每张卡片最多一次 | `article_id`、`list_type`、`list_id`、`position` |
| Article Clicked | `article_clicked` | 浏览器 | 点击文献卡片进入详情时（在跳转前发） | `article_id`、`list_type`、`list_id`、`position` |
| Fulltext Read | `fulltext_read` | 浏览器 | 全文正文累计可见停留首次达到 10 秒时，每个页面实例最多一次（§6.3） | `article_id`、`visible_seconds` |
| Citation Copied | `citation_copied` | 浏览器 | 引用对话框里点「复制」成功后（打开对话框、切换格式时的预览请求不算） | `article_ids`、`style` |

**来源与事件名绑定**：前 5 个只能是 `source = server`，后 5 个只能是 `source = client`，写进 schema。从浏览器路径发来 `article_viewed` 这类服务端事件会被判为不合格（§5），浏览器无法伪造浏览数。

`article_viewed` 与 `page_viewed` 并存：前者是服务端看到的「真的取了这篇文献」，用来算看过人数；后者是浏览器看到的「人到了哪个页面」，用来算漏斗、跳出率、会话里有没有真人（§6.5）。

**引用为什么是浏览器事件**：门户的引用 Route Handler（`app/api/publications/citations/route.ts`）只生成预览，对话框打开时、每切一次格式都会请求一次；真正的复制由浏览器里的 `CopyButton` 完成，所以只有浏览器知道「复制了」。

### 3.3 属性定义

| 属性 | 类型 | 规则 |
|---|---|---|
| `article_id` | string | catalog 文献 ID 的十进制字符串（门户 `Paper.id`），`^[1-9][0-9]{0,18}$` |
| `article_ids` | string[] | 1～100 个 `article_id`（引用对话框一次最多 100 篇，与 Route Handler 的上限一致） |
| `search_id` | string | UUID，门户为每次检索生成；同一次检索的曝光、点击用它关联 |
| `mode` | string | 枚举：`keyword`（普通检索）、`exact_pmid`、`exact_doi`（门户的精确定位模式，`isExactLookup`） |
| `query` | string | 普通检索是用户输入的检索词（`q`）；精确定位时是 PMID 或 DOI。去掉首尾空白，最长 200 个字符；只用筛选、没写检索词时为 `""` |
| `filters` | object | 与门户 `PaperSearchQuery` 对齐，只允许这些键，没用到的键省略：`author`（string，≤ 200 字符）、`year_from`、`year_to`（integer）、`type`、`evidence`、`lang`（string[]，各 ≤ 20 个）、`venue`（string[]，期刊 ID，≤ 20 个）、`oa`（boolean，只在为 `true` 时出现）。门户新增筛选时先改 schema |
| `sort` | string | 枚举：`latest`、`year`（与门户 `PaperSortId` 一致） |
| `page` | integer | 从 1 开始 |
| `result_count` | integer | ≥ 0；0 即「检索无结果」 |
| `format` | string | 枚举：`ris`、`bibtex`、`csv`、`csl-json`（与导出 Route Handler 一致） |
| `scope` | string | 枚举：`items`（勾选的若干篇或单篇）、`collection`（自己的收藏夹）、`public_collection`（别人的公开收藏夹）、`library_all`、`library_unfiled`（文献库全部 / 未归类）。由 `ExportDialog` 随导出请求传给 Route Handler（PAP-173 加这个参数） |
| `article_count` | integer | 1～5000，导出了几篇 |
| `style` | string | 枚举：`gb-t-7714-2015`、`vancouver`、`apa-7`、`ama-11`（与引用 Route Handler 一致） |
| `page_type` | string | 枚举：`home`、`search`、`article`、`journal`、`library`、`collection`、`profile`、`settings`、`notifications`、`post`、`history`、`privacy`、`auth`、`other` |
| `list_type` | string | 枚举：`feed_latest`、`feed_cited`、`feed_hot`、`search`、`related`（看过还看了）、`related_topic`（同主题补位）、`collection`、`library` |
| `list_id` | string | 一个列表实例的 UUID，每次渲染出一个列表就生成一个；检索结果列表用 `search_id` |
| `position` | integer | 卡片在列表里的位置，从 1 开始，跨页连续（第 2 页第 1 张是 21） |
| `visible_seconds` | integer | 发出时全文区的累计可见秒数（≥ 10） |

**长度与取值上限**（PAP-164 写 schema 时定，防止异常大的值进库；门户正常使用不会碰到）：`filters.type` 每项 ≤ 100 字符、`evidence` 每项 ≤ 32、`lang` 每项 ≤ 16；`year_from` / `year_to` 在 1000～9999；`page_path` ≤ 512 字符；`article_ids`、`filters` 里的数组都不允许重复项。

## 4. 信封字段

### 4.1 ① 浏览器批次（`browser-batch`）

```json
{
  "sent_at": "2026-11-01T08:30:12.345Z",
  "events": [
    {
      "event_id": "0193e1a2-7c4b-7d2e-9f10-3b5c6d7e8f90",
      "event_name": "article_impressed",
      "schema_version": "1-0-0",
      "created_at": "2026-11-01T08:30:10.120Z",
      "page_path": "/papers",
      "page_type": "search",
      "properties": { "article_id": "123456", "list_type": "search", "list_id": "…", "position": 3 }
    }
  ]
}
```

- 一批最多 50 条、序列化后不超过 48 KiB（`sendBeacon` 的在途上限约 64 KiB，留余量）。
- `page_path` 只放路径，不放查询串（检索词由 `search_performed` 从服务端记，不经浏览器）。

### 4.2 ② 采集信封（`collect-envelope`）

门户把 ① 的每条事件展开，或把自己产生的服务端事件组装成下面的样子，按批发给采集端点：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `event_id` | string（UUID） | 是 | 事件产生方生成；建议 UUIDv7（按时间有序，ClickHouse 排序更友好）。重试时不变，是去重的依据 |
| `event_name` | string（枚举） | 是 | §3.2 的 10 个之一 |
| `schema_version` | string | 是 | 该事件 schema 的版本，`MODEL-REVISION-ADDITION`（§7） |
| `source` | string | 是 | `client`（浏览器）或 `server`（门户服务端） |
| `created_at` | string（RFC 3339，毫秒，UTC） | 是 | 事件发生时产生方的时钟 |
| `sent_at` | string（同上） | 是 | 产生方发出这一批的时刻；浏览器事件取 ① 的 `sent_at` |
| `device_id` | string | 是 | 设备 Cookie 的值，`^[A-Za-z0-9_-]{43}$`；门户保证每个请求都有（§6.1） |
| `session_id` | string（UUID） | 是 | 会话 Cookie 的值（§6.2） |
| `user_id` | string \| null | 是 | 登录用户的 ID（十进制字符串）；未登录为 `null`。不放用户名 |
| `page_path` | string \| null | 是 | 路径，不含查询串；服务端事件取请求路径 |
| `page_type` | string（枚举） | 是 | 同 §3.3 |
| `referrer_host` | string \| null | 是 | 来源网页的主机名；站内跳转、无来源为 `null` |
| `client_ip` | string | 是 | 访客 IP，只用于查地区与判爬虫，采集端点用完即丢 |
| `user_agent` | string | 是 | 原始 UA，只用于解析与判爬虫，不落盘 |
| `is_synthetic` | boolean | 是 | 模拟器造的为 `true`；门户发的一律 `false`（门户转发浏览器事件时强制覆盖） |
| `properties` | object | 是 | 该事件的属性，由该事件自己的 schema 约束 |

一批最多 500 条。字段名全部 snake_case。

### 4.3 ③ 富化事件（`enriched-event`）

采集端点在 ② 的基础上：

- **去掉**：`client_ip`、`user_agent`。
- **加上**：

| 字段 | 类型 | 说明 |
|---|---|---|
| `received_at` | string（RFC 3339，毫秒，UTC） | 采集端点收到这一批的时刻 |
| `derived_at` | string（同上） | 校正后的发生时间：`received_at − (sent_at − created_at)`（Snowplow 的 `derived_tstamp`）。客户端时钟快了慢了都不影响；分析一律用它 |
| `country_code` | string \| null | ISO 3166-1 两位国家码，ip2region 查不到为 `null` |
| `province` | string \| null | 中国境内的省级行政区名（ip2region 的结果），境外为 `null` |
| `browser_family` | string \| null | Yauaa 解析的浏览器名 |
| `os_family` | string \| null | Yauaa 解析的操作系统名 |
| `device_class` | string | Yauaa 的设备类别（`Desktop`、`Phone`、`Tablet`、`Robot` 等） |
| `is_bot` | boolean | §6.7 |
| `bot_reason` | string \| null | `ua_list`、`ua_class`、`rate`，不是爬虫为 `null` |

字段间的约束（schema 用 `if / then / else` 写死，模拟器直接写 ③ 时也守得住）：`is_bot` 为 `true` 时 `bot_reason` 必须是三个原因之一，为 `false` 时必须是 `null`；`country_code` 不是 `CN` 时 `province` 必须是 `null`。长度上限：`province` ≤ 32 字符，`browser_family`、`os_family` ≤ 64，`device_class` ≤ 32。

③ 是 Kafka `events.raw` 里的消息格式，也是 ClickHouse 明细表的一行（列名、类型由 PAP-168 按它定）。`derived_at` 早于 `received_at` 超过 24 小时，或晚于 `received_at` 超过 5 分钟（时钟严重错乱）时，采集端点把 `derived_at` 置为 `received_at`，不丢事件。

## 5. 不合格的事件

- 任一事件不符合 schema（缺字段、类型不对、枚举外的值、未知 `event_name`、`source` 与事件名不匹配、版本不被接受（§7）、属性里有 schema 没列的键），这一条进 `events.dlq`；同批其余合格事件照常进 `events.raw`。
- 整批不是合法 JSON、超过条数或大小上限，整批拒收（采集端点返回 400），不进任何 topic。
- **批次外壳与单条事件分开校验**：门户第一方路径只按 `browser-batch` 校验浏览器批次的外壳（`sent_at`、1～50 条），不逐条校验；它把每条都组装成采集信封转发，采集端点按 `collect-batch` 校验请求体外壳（1～500 条），再按 `collect-envelope` 逐条校验。所以一条坏事件只会让它自己进 dlq，不会拖累同批的其他事件。`browser-event` 是浏览器单条事件的 schema，供门户生成 TypeScript 类型与写测试用。
- 事件 schema 一律 `additionalProperties: false`：多一个字段也算不合格。这是「字段不能随手加」在机器上的落点。

**`events.dlq` 的消息格式**（`dlq-record.schema.json`，PAP-167 写、PAP-172 的数据质量面板读）：

| 字段 | 说明 |
|---|---|
| `received_at` | 采集端点收到的时刻 |
| `reason_code` | 枚举：`schema_invalid`、`unknown_event`、`source_mismatch`、`version_rejected` |
| `reason_detail` | 校验器给出的第一条错误（JSON 指针 + 说明），最长 1,000 字符 |
| `event_name` | 原事件里的 `event_name`，读不出时为 `null` |
| `raw` | 原事件的 JSON 文本，**先删掉 `client_ip`、`user_agent` 两个键**再存，最长 16 KiB（16,384 字符） |
| `raw_truncated` | `raw` 是否因超长被截断 |

dlq 也落盘（Kafka 保留 2 天），所以同样不存 IP。

## 6. 口径

每条规则后面都给出可直接写成测试的例子。

### 6.1 访客与设备

- 每个访客第一次请求门户的任意页面时，门户下发设备 Cookie（400 天，httpOnly），已有合法值就沿用。登录、登出都不换。名字沿用现有约定：开着 Secure 时叫 `__Host-patra_device`，关着时叫 `patra_device`（`lib/auth/session-cookie.ts`）。
- 「一个人」：登录用户按 `user_id`；未登录按 `device_id`。同一台设备登录前后的事件，靠 identity 登录记录里设备与账号的对应关系认回同一个人（release spec 2.1「访客标识」），本版只在面板的「新增 / 留存」里用到。

### 6.2 会话

- 会话 Cookie（httpOnly，不设过期时间、随浏览器关闭失效；与设备 Cookie 同一套命名：开着 Secure 叫 `__Host-patra_sid`，关着叫 `patra_sid`）里存会话 ID 与最后活动时间。
- 门户每处理一个页面请求或浏览器批次：距最后活动超过 **30 分钟**，或没有会话 Cookie，就生成新的会话 ID；否则沿用，并把最后活动时间更新为现在。
- 例：10:00 打开首页（会话 S1）→ 10:20 打开文献 → 10:55 再打开文献，距上次 35 分钟，换成 S2。

### 6.3 全文阅读

- 「全文正文」指 `FullTextBody` 渲染出的正文容器。只有 PDF、全文不可用这类说明面板不算正文，不计时。
- **正文在视口里占的高度 ≥ 视口高度的 50%**（正文很长时，滚到正文中间就满足），或正文本身比半个视口还矮且整段可见，并且标签页在前台时计时；不满足就暂停，满足再继续累计。不用「正文元素 50% 可见」：长文超过两屏时它永远达不到。
- 累计到 **10 秒**发一条 `fulltext_read`，同一个页面实例只发一次。离开页面时不足 10 秒不发。
- 下载 PDF（`pdf_downloaded`）也算一次全文阅读，不要求停留。
- 例：正文占视口 70% 停 6 秒 → 切到别的标签页 1 分钟 → 回来再停 4 秒，此时发出，`visible_seconds = 10`；一直停在摘要区、正文只露出视口底部 20%，不计时。

### 6.4 曝光

- 卡片至少 **50%** 在视口内、**连续 1 秒**、标签页在前台，算一次曝光（MRC / IAB 的可见曝光标准）；中途低于 50% 或切到后台就重新计时。
- 同一个列表实例（`list_id`）里同一篇文献只算一次；同一篇出现在两个列表里各算各的。
- 例：卡片 60% 可见 0.8 秒后滚走 → 不算；再滚回来连续 1.2 秒 → 算一次；之后再滚出滚入 → 不再算。

### 6.5 真人会话

不保存 Cookie 的爬虫每次请求都会拿到一个新设备 ID、新会话，靠设备去重拦不住它。所以对外数字只数「真人会话」里的事件：

- **真人会话**：这个会话里至少有一条 `source = client` 的事件（浏览器执行了门户的脚本），并且**会话里没有任何一条事件被标为爬虫**（§6.7）。只要有一条被标，整个会话（包括被标之前的事件）都不进对外数字。
- 只有服务端事件、没有任何浏览器事件的会话，不进浏览数、全文数、热读、共现；面板里单独显示这类会话的量。
- 代价：关掉 JavaScript 的真人不会被计入；浏览器事件被拦截器拦掉的访客也不会（门户用第一方路径，正常不会被拦）。业界用前端统计（如 Google Analytics）时本来就是这个口径。

### 6.6 不算事件的请求

- **预取**：Next.js 站内链接预取会带 `Next-Router-Prefetch` 请求头。带这个头的请求不发任何服务端事件。
- **Server Action 之后的重新渲染**：页面里的操作（收藏、发评论）触发的服务端重新渲染不再发 `article_viewed`；门户在 PAP-173 里区分「页面请求」与「Action 回渲染」。
- **门户自己的健康检查、e2e 冒烟**：e2e 测试账号的事件照常记录（它们本来就是 B / C / E 这些「真人」），健康检查不经页面渲染，不产生事件。

### 6.7 爬虫

三条规则，命中任一条 `is_bot = true`，按先后记 `bot_reason`：

1. `ua_list`：UA 匹配 COUNTER-Robots 列表里的任一模式。列表原样放进仓库（`analytics/robots/counter-robots.json`，记下来源提交与日期），更新靠手动重新拉取并提交，不在运行时联网。
2. `ua_class`：Yauaa 把 UA 归为 `Robot`、`Robot Mobile`、`Robot Imitator`、`Spy`、`Hacker` 等非人类类别。
3. `rate`：同一个 `device_id` 在 5 分钟内的 `article_viewed` 超过 **30 条**，之后的事件都标为爬虫，直到这个窗口内的数量降回阈值以下（采集端点用 Redis 计数）。

按 IP 的频率规则本版不做：现在所有访客的 IP 都是同一个内网地址（release spec 第 3 节 L）。

### 6.8 看过人数与读过全文人数

门户上写「1,284 人看过 · 312 人读过全文」，两个数字都**按人长期去重**，只数真人会话（§6.5）里的事件，排除爬虫与合成事件（2026-11-01 用户确认）：

- **人**：登录用户按 `user_id`，未登录按 `device_id`（§6.1）。
- **看过人数**：从有数据起，对这篇文献有过任一「查看类」事件的人数。查看类事件：`article_viewed`、`fulltext_read`、`pdf_downloaded`、`article_collected`。引用与导出一次可涉及多篇、不一定打开过文献，不算查看。
- **读过全文人数**：对这篇文献有过 `fulltext_read` 或 `pdf_downloaded` 的人数。
- 同一个人看多少次、隔多少天再看，都只算 1；刷新刷不上去。
- 例：B 打开文献 X、刷新 3 次、读了全文 → 看过 +1、读过全文 +1；第二天 B 又打开 X → 两个数字都不变；C 第一次打开 X → 看过 +1。
- 已知偏差：同一个人先以访客身份看、再登录后看，会算成两个人（设备一次、账号一次）；同一个人在两台设备上未登录看，也算两个人。量很小，本版接受。

**面板用的 COUNTER 口径**（对照学术出版界的统计）：

- **Unique Item Investigations / Requests**：按会话去重——一个会话里同一篇只算 1，同一个人隔天再看是新会话，再算 1。
- **Total Item Investigations**：按 COUNTER 的双击过滤——同一个人对同一篇文献，两条查看类事件相隔 ≤ **30 秒**只算一条（保留后一条）。例：10:01:00 与 10:01:29 算一次；10:01:00 与 10:01:35 算两次。

热读与共现的口径在 release spec 2.1，它们用的「每人每天每篇一次」中的「人」按 §6.1，也只数真人会话。

### 6.9 去重

- 同一个 `event_id` 不论到达几次，入库后只算一次（PAP-168 用批次去重令牌 + 汇总按 `event_id` 去重实现）。
- 门户重试发送时保持 `event_id` 不变；模拟器故意重发时也保持不变。

## 7. 版本与改动规则

- 每个事件一个 schema 文件，带自己的版本号，格式 `MODEL-REVISION-ADDITION`（Snowplow 的 SchemaVer）：
  - 加了一个**可选**属性 → `ADDITION` +1（`1-0-0` → `1-0-1`）；
  - 改了属性含义或取值范围、旧数据还能读 → `REVISION` +1；
  - 删属性、改类型、加必填属性（旧事件会不合格）→ `MODEL` +1。
- 三个信封 schema（①②③）与 dlq 记录也各带版本号，规则相同。
- 仓库里每个事件只保留当前版本的 schema 文件（项目规则：不留多版本并存）；已入库的旧数据靠每行的 `schema_version` 追溯。
- **采集端点接受与当前版本 `MODEL` 相同的任意版本**：门户与 `patra-analytics` 是分开部署的容器，用户已打开的标签页也还跑着旧脚本，所以总有一段时间会收到旧版本事件。`ADDITION` / `REVISION` 的改动按定义兼容旧事件，照收；`MODEL` 变了的旧事件进 dlq（`version_rejected`）——这种改动本来就是旧数据读不了，代价是部署前后几分钟里旧标签页的这类事件丢掉。
- 改 schema 的顺序：先改 schema 与本文 → 再改采集端点 → 再改产生方（门户 / 模拟器）；同一个 PR 合入、按这个顺序部署。CI 检查门户生成的 TypeScript 类型与 schema 一致（PAP-173）。

## 8. 保留期

| 数据 | 保留 | 在哪执行 |
|---|---|---|
| Kafka `events.raw`、`events.dlq` | 2 天，且有磁盘上限 | Kafka topic 配置（PAP-165） |
| ClickHouse 明细（③） | 90 天 | 表 TTL（PAP-168） |
| ClickHouse 汇总 | 长期 | — |
| 个人阅读历史 | 12 个月 | XXL-Job 清理（PAP-169） |
| 注销用户的明细事件与历史 | 注销清理时删除 | PAP-168、PAP-169 |

## 9. 文件布局

```text
patra-api/patra-analytics/patra-analytics-api/src/main/resources/analytics/
├── schema/
│   ├── defs.schema.json                 # 公共类型
│   ├── event-catalog.schema.json        # 事件名、来源、版本、属性的绑定
│   ├── browser-batch.schema.json        # ① 的外壳
│   ├── browser-event.schema.json        # ① 的单条事件
│   ├── collect-batch.schema.json        # 采集端点请求体的外壳
│   ├── collect-envelope.schema.json     # ②（properties 按 event_name 分派到 events/）
│   ├── enriched-event.schema.json       # ③
│   ├── dlq-record.schema.json           # events.dlq 的消息（§5）
│   └── events/
│       ├── article_viewed.schema.json   # 每个事件的属性 schema，文件内带 schema_version
│       └── …（共 10 个）
├── examples/
│   ├── valid/                           # 每个事件至少一条合格样例，三种形态各一批
│   └── invalid/                         # 每条不合格规则至少一条反例，文件名写明违反了哪条
└── robots/
    └── counter-robots.json              # COUNTER-Robots 列表的原样副本
```

- 实际文件（2026-11-01）：`schema/` 下 `defs`、`event-catalog`、`browser-batch`、`browser-event`、`collect-batch`、`collect-envelope`、`enriched-event`、`dlq-record` 共 8 个，`schema/events/` 下 10 个；样例合格 30 条、不合格 46 条；`robots/` 下列表、`LICENSE`、`README.md`。
- 正则的结尾不用 `$`：Python 与 JDK 的 `$` 会放过末尾的换行（`"123\n"` 会被当成合法 ID），一律写成各引擎都认的 `(?![\s\S])`；定长的值（时间、UUID、设备 ID、国家码）另加 `minLength` / `maxLength`。
- JSON Schema 用 2020-12 草案，`$id` 以 `https://patra.linqibin.dev/schemas/analytics/` 开头，相互引用用相对路径。
- 不依赖 `format` 关键字（2020-12 默认不校验它）：时间、UUID 都用 `pattern`。时间必须是 UTC、带毫秒（`2026-11-01T08:30:12.345Z`）；UUID 必须小写。
- 每个 schema 用注解 `x-patra-version` 记当前版本；事件目录里 `schema_version` 的约束是「MODEL 为 1」（§7）。校验器若对未知关键字告警，在校验器配置里把它当注解（PAP-167）。
- 浏览器批次里的 `page_path` 必须是字符串；采集信封与富化事件里允许 `null`（服务端事件未必有页面）。
- 本 Issue 只放这些资源文件；`patra-analytics-api` 的 Gradle 模块由 PAP-167 建，届时这个目录直接成为它的资源目录。样例在 PAP-167 里变成采集端点校验的测试数据（合格的都收、不合格的都进 dlq），在 PAP-173 里变成门户类型生成的测试数据。
- **给 PAP-167 的 Java 侧注意点**（PAP-164 终审时整理）：用 networknt json-schema-validator 1.5.x；把 `https://patra.linqibin.dev/schemas/analytics/` 映射到 classpath（否则会去联网取 schema）；把 `x-patra-version` 注册为注解关键字；`oneOf` 失败只会报「没有分支匹配」，所以 `unknown_event`、`source_mismatch`、`version_rejected` 要先按「事件名在目录里 → 来源与事件名匹配 → 版本 MODEL」的顺序预检，再跑 schema；`--extra-top-level`、`--has-device-id` 这类靠 `unevaluatedProperties` 拦的反例要写成 Java 测试。
- 本 Issue 交付前，用 JSON Schema 校验器在本机核对：所有 schema 本身合法，`valid/` 全部通过，`invalid/` 全部不通过。

## 10. 本 Issue 不做

- 采集端点、校验代码、Kafka 写入（PAP-167）。
- ClickHouse 表结构与列类型（PAP-168；它按 ③ 定）。
- 门户的埋点代码与类型生成（PAP-173）。
- 创作者数据、关注、帖子相关事件（release spec 第 3 节）。

## 11. 我的决定（规划后定的，等你审）

以下是 release spec 没写死、我在本文里定下的做法：

1. **三种形态分开定义**，身份字段只由门户服务端填，采集端点信任门户上报的 `user_id`（§2）。
2. **「真人会话」规则**：只有执行了门户脚本的会话才进对外数字，拦住不存 Cookie 的爬虫（§6.5）。
3. ~~浏览数按会话去重~~ → 已由用户改定：门户上的数字**按人长期去重**，写「N 人看过」；COUNTER 的按会话口径只用于面板（§6.8）。
4. **爬虫的频率阈值**：同一设备 5 分钟内超过 30 次文献浏览（§6.7）。
5. **预取与 Action 回渲染不算浏览**（§6.6）。
6. **检索词原样保存 90 天**，用于「检索无结果词」面板；隐私政策里写明（§3.3、§8）。
7. **schema 只留当前版本**，旧数据靠 `schema_version` 追溯；采集端点接受同一 `MODEL` 的任意版本（§7）。
8. **引用复制改为浏览器事件**，带 `article_ids`；引用与导出都不算「查看」（§3.2、§6.8）。
9. **导出的 `scope` 由 `ExportDialog` 传给 Route Handler**，PAP-173 给导出请求加这个参数（§3.3）。
10. **检索的筛选与排序照搬门户 `PaperSearchQuery`**，精确定位模式也记一次检索，用 `mode` 区分（§3.3）。
11. **全文计时按「正文占视口一半」**，不按「正文一半可见」（§6.3）。
12. **来源与事件名在 schema 里绑定**，浏览器发不了服务端事件（§3.2）。
13. **会话里有一条被标爬虫，整个会话不计**（§6.5）。
14. **dlq 记录有自己的 schema，原文先删 IP 与 UA 再存**（§5）。
