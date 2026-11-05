# 流量模拟器与压测报告设计

> **Issue**：PAP-171（v0.18 Analytics）
> **日期**：2026-11-02
> **状态**：已定稿（2026-11-02，用户拍板见 §15）
> **依据**：release spec [`v0.18-analytics.md`](../release-specs/v0.18-analytics.md) 决策 E、G、H、I，验证场景 5，Done 判定 D8～D10；[埋点方案](2026-11-01-analytics-tracking-plan-design.md)（事件字典 §3、口径 §6）；[采集设计](2026-11-01-analytics-collector-design.md)（端点、富化、爬虫规则）；[入库设计](2026-11-01-analytics-ingest-design.md)（表结构、写入设置、§9 给模拟器的约束）；PAP-165 契约（ClickHouse 账号 `patra` 每条查询 1 GiB、剩余磁盘 < 22 GB 拒写；Kafka 保留 2 天、5 GB 上限）
> **前置**：PAP-167、168、169 都在 `feat/v0.18-analytics-api`

---

## 1. 要做什么

在 `patra-analytics` 下新建模块 `patra-analytics-simulator`：一个命令行程序，按真实网站的访问规律造合成行为事件，有三个命令：

| 命令 | 做什么 | 证明什么 |
|---|---|---|
| `backfill`（灌历史） | 造近 88 天的事件，**直接批量写进 ClickHouse 的 `events` 表**，物化视图随之生成汇总 | 存储：1 亿条压缩后占多少磁盘、分区与 TTL 是否正常、汇总查询在亿级数据上跑多快（D8） |
| `live`（实时压测） | 用虚拟线程模拟成千上万个同时在线的读者，按设定速率**经网关打采集端点** `POST /patra-analytics/events` | 整条链路：采集吞吐、Kafka 攒批、消费滞后、小批量写入会不会 `TOO_MANY_PARTS`、各容器内存（D9） |
| `reconcile` / `report` | 拿模拟器记下的真值和 ClickHouse 汇总表对账；把各项实测数字汇成 Markdown 报告 | 去重与「真人会话」口径算得对（D10）；压测报告 |

所有事件都带 `is_synthetic = true`；门户对外数字默认排除它们（决策 I）。

**不做**：计数、热读、共现的接口（PAP-170）；Grafana 业务面板（PAP-172，用本模拟器造的数据出图）；在 mini 上真正灌 1 亿条与 30 分钟压测——**执行前必须经编排会话征得用户同意**（§12）。

## 2. 先讲清几个概念

### 2.1 为什么要「按规律」造数据，而不是随便造

随机均匀地造 1 亿条，压不出真实问题：真实网站上**少数热门文献占了大部分访问**（长尾分布），白天多、夜里少，一个读者一次来会连着看好几页。这些规律决定了：汇总表里一篇文献有多少行、一个分区多大、热读榜是不是一眼看得出、某个时段写入有多集中。业界的流量模拟（如 Snowplow、PostHog 的压测脚本，学术界的 Web 负载生成器 SURGE）都用下面三个模型组合：

| 规律 | 模型 | 一句话 |
|---|---|---|
| 热门集中 | **Zipf 分布**（指数 s ≈ 1） | 排第 k 名的文献被访问的概率 ∝ 1/k：第 1 名是第 10 名的 10 倍 |
| 白天多夜里少、周末少 | **非齐次泊松过程** | 会话「到达」是随机的，但每小时的平均到达率按一条昼夜曲线 × 周内系数变化 |
| 一次访问连看几页 | **马尔可夫链** | 读者在「首页 → 检索 → 结果列表 → 文献页 → 全文 → 离开」之间按转移概率走，下一步只看当前在哪一页 |

### 2.2 两种模式为什么都要

- **灌历史**跳过采集与 Kafka，直接写 ClickHouse：要的是**量**（1 亿条、铺满 88 天），测存储和查询。经 HTTP 灌 1 亿条按每秒 1,000 条要 28 小时，没必要。
- **实时压测**走完整链路：要的是**速率**，测「每秒 1,000 条持续 30 分钟，管线扛不扛得住」。
- 两种模式共用同一个流量模型，只是输出形态不同：灌历史输出**富化事件**（③，采集端点处理过的样子，自己填好地区、浏览器、爬虫标记）；实时压测输出**采集信封**（②，带 IP 与 UA，交给采集端点去富化）。

### 2.3 开环压测

压测工具有两种发压方式：**闭环**（固定 N 个用户，每个发完一个请求、等到响应再发下一个）——服务变慢时工具自己也跟着变慢，压力自动降下来，掩盖了问题（业界叫 coordinated omission）；**开环**（按设定的速率产生新会话，不管服务响应快慢）——服务跟不上时请求堆积、延迟飙升，问题暴露出来。真实网站是开环的：读者不会因为你的服务慢就晚点来。本模拟器用开环：会话按泊松过程到达，每个会话一个虚拟线程。

### 2.4 真值与对账

模拟器知道自己造了什么：每篇文献每天被哪些「真人」看过。把这份**真值**和 ClickHouse 汇总表按同一口径算出的数比对，就能验证整条链路（去重、按北京时间切天、真人会话判定、爬虫识别）算得对不对。故意重发 1% 的事件（同一 `event_id`），对账仍要相等，才证明去重有效。

## 3. 模块与依赖

| 项 | 内容 |
|---|---|
| 位置 | `patra-api/patra-analytics/patra-analytics-simulator`，Gradle 路径 `:patra-api:patra-analytics:patra-analytics-simulator`，根包 `dev.patra.analytics.simulator` |
| 插件 | `patra.java-base`（Java 25、spotless、spotbugs、test / integrationTest 套件）+ Gradle 自带 `application`（主类 `dev.patra.analytics.simulator.SimulatorMain`，`installDist` / `distTar` 出可分发的目录） |
| 主依赖 | `patra-analytics-api`（JSON Schema、COUNTER-Robots 列表，作 classpath 资源读）；`clickhouse-client`（client-v2，与入库同版本）；Jackson 3 `JsonMapper`；命令行解析 **picocli**（新进版本目录） |
| 测试依赖 | `json-schema-validator`（networknt，生成的每条事件按 schema 校验）；`yauaa`、`ip2region`（钉住 UA 与 IP 表的富化结果）；集成测试另加 `patra-analytics-infra`（复用 `ClickHouseMigrator` 建表）与 `patra-tool-test`（ClickHouse 容器） |
| 不依赖 | Spring、`patra-analytics-domain/app/infra`（主代码）：模拟器是外部的「访客」，不应借服务内部代码；拉 infra 进主代码会把 Spring、Kafka、Redis、Yauaa 都带进命令行 |
| 不被依赖 | boot 与其他模块都不依赖它；不进 CD、不进 compose |

构建接入：`settings.gradle.kts` 加一行 `includeAt`；`gradle/libs.versions.toml` 加 picocli；重跑 `./gradlew dumpModuleGraph`（单元归 `analytics`，`unitOf` 按目录已能认出，无需改根构建脚本）。模块内分包按职责（不是六边形层模块，不受子域规则约束）：

```text
dev.patra.analytics.simulator
├── SimulatorMain / command/      # picocli 命令：backfill、live、reconcile、report
├── model/                        # 流量模型：参数、Zipf、到达曲线、人群、会话马尔可夫链、爬虫
├── event/                        # 中性事件 SimEvent、两种序列化（富化事件 / 采集信封）、UUIDv7、时间格式
├── truth/                        # 真值账本、真值文件读写、对账
├── backfill/                     # 按天分块、去重令牌、写 ClickHouse
├── live/                         # 到达调度、会话虚拟线程、HTTP 发送与重试、每秒统计、parts / 滞后采样
└── report/                       # 汇总 Markdown
```

可测性：所有随机都来自按 `(运行种子, 日期, 块号)` 派生的 `SplittableRandom`，时间来自注入的 `Clock`，同样参数两次运行产出逐字节相同。

## 4. 运行标识：让每次运行可隔离、可重跑

- 每次运行有一个运行号 `--run`（1～899 的整数）和种子 `--seed`。
- **设备 ID** = `sim` + 三位运行号 + 37 位随机 base64url 字符（共 43 位，符合 schema）；**合成用户 ID** = 9,000,000,000,000,000,000 + 运行号 × 10,000,000 + 序号（远离 identity 的雪花 ID，不会撞上真实用户）。
- 对账时按「设备 ID 前缀 + 用户 ID 区间」只取本次运行的人，灌历史、实时压测、测试运行互不干扰。
- 每次运行的输出目录 `--out/run-{运行号}/`：`params.json`（全部参数与种子）、`truth.csv.gz`、`summary.json`、实时压测另有 `live-stats.csv`、`parts.csv`、`lag.csv`。

## 5. 流量模型

全部参数放在模块资源 `simulator/traffic-model.json`，`--model` 可换一份；下表是默认值（我的取值，§15 第 3 条请过目）。

### 5.1 文献与热度

- **文献池必须是 catalog 里真实存在的文献 ID**（PAP-170 约束：热读与「看过还看了」会向 catalog 核对，不存在、软删、撤稿的会被去掉，用假 ID 灌满了榜单也是空的）。`--articles` 指定一个 CSV 文件，每行 `id,venue_id`，从 mini 的 catalog 库只读导出：

  ```sql
  \copy (SELECT id, coalesce(venue_id, 0) FROM cat_publication
         WHERE deleted_at IS NULL ORDER BY random() LIMIT 200000) TO 'articles.csv' CSV
  ```

  导出文件只在本机与 mini 之间传，不进仓库。默认取 20 万篇（不足就全用），按文件顺序（已随机）排名。
- **热度**：Zipf，s = 1.0。抽样用预先算好的累积分布 + 二分查找（20 万篇约 1.6 MB）。
- **主题聚集**：共现推荐要的是「常被一起读」，如果每次都从全局 Zipf 独立抽，一起读的只会是热门文献的随机组合，验证场景 3（「不是随机推荐」）就验不出来。所以按 `venue_id` 把文献分成主题（同刊近似同主题；无刊的归入一个大杂烩主题）：每个会话开头按主题总热度抽一个兴趣主题，之后检索结果与「看过还看了」里点进去的文献 70% 来自这个主题（主题内仍按 Zipf），30% 来自全局。测试钉住：同一会话里两篇同主题的比例显著高于全局抽样。
- 爬虫不按热度：线性遍历文献池（§5.5）。

### 5.2 人群

| 参数 | 默认 | 说明 |
|---|---|---|
| 设备数 | 300,000 | 一个人一台设备；访问频度服从帕累托分布（少数常客天天来，多数人来一两次） |
| 其中注册用户 | 30,000（10%） | 注册用户的访问频度是访客的 3 倍，所以约 25% 的会话是登录状态。**控制在几万**：注销清理任务每天按 100 个一批把日活表里的用户拿去 identity 查状态，合成用户越多这项开销越大 |
| 地区 | 国内 93%（按省份加权，粤、京、沪、苏、浙、鲁、川、鄂居前），境外 5%（美、英、德、日、新），查不到 2% | 实时压测用每个地区一组真实 IP，测试钉住 ip2region 查出来的省份与预期一致 |
| 浏览器 | 约 20 个真实 UA：Chrome / Edge / Safari / Firefox × macOS / Windows / iOS / Android，另有微信内置浏览器 | 测试钉住：每个 UA 都不命中 COUNTER-Robots，Yauaa 也不归为 Robot 类；灌历史直接写这些 UA 预先解析好的浏览器族、系统族、设备类别 |

### 5.3 到达曲线

- 每小时到达率 = 日均 × 昼夜系数(北京时间的小时) × 周内系数(星期)。
- 昼夜：凌晨 2～6 点最低（0.15），上午 9～11 点、下午 2～5 点两个高峰（1.8），晚 8～10 点次高峰（1.4），曲线归一化到日均。
- 周内：周一至周五 1.0，周六 0.65，周日 0.6（学术站点的工作日特征）。
- 一小时内的到达时刻在小时内均匀撒（泊松过程的性质），实时压测则按速率逐个产生间隔服从指数分布。

### 5.4 会话：页面跳转链

每个会话从入口页开始，在下列页面间按转移概率走，到「离开」结束。每到一页，按埋点方案 §3.2 发对应事件：

| 页面 | 发的事件 | 下一步（默认概率） |
|---|---|---|
| 入口 | — | 首页 45%、检索 30%、文献页 25%（从外部搜索引擎直达，`referrer_host` 为搜索引擎域名） |
| 首页 | `page_viewed`；文献流 4～8 张卡片的 `article_impressed`（`list_type` 按「最新 / 高被引 / 热读」分配） | 点卡片进文献页 35%（先发 `article_clicked`）、去检索 30%、离开 35% |
| 检索 | 服务端 `search_performed`（检索词从内置的医学词表抽，8% 无结果）+ `page_viewed`；有结果时前 5～12 张的 `article_impressed`（读者真正看到、满足曝光规则的） | 点结果 55%、翻页或改条件再检 20%、离开 25% |
| 文献页 | 服务端 `article_viewed` + `page_viewed`；「看过还看了」6 张卡片的 `article_impressed`（一半读者滚到那里） | 读全文 35%、点相关文献 15%、回检索 20%、离开 30% |
| 读全文 | 浏览器 `fulltext_read`（`visible_seconds` 10～300）；其中 20% 再 `pdf_downloaded`，5% `citation_copied`，登录用户 5% `article_collected` | 回检索 25%、点相关文献 15%、离开 60% |
| 文献库（仅登录） | `page_viewed`；2% 的会话在这里 `export_completed` | 离开 |

- **点击**：凡是「点卡片 / 点结果 / 点相关文献」进入文献页，跳转前先发浏览器事件 `article_clicked`（与被点卡片的那条曝光同一个 `list_type`、`list_id`、`position`；检索结果的 `list_id` 是 `search_id`），PAP-172 的点击率面板靠它。只点曝光过的卡片。
- **两页之间的停留**：对数正态，中位 25 秒；读全文页中位 90 秒。
- **会话长度**：最多 25 页；跨过 30 分钟不活动视为新会话（模拟器不会造出这种间隔）。
- **硬约束**：一个人 5 分钟内的 `article_viewed` 远低于 30 条（采集端点的频率规则阈值），否则真人会被误判成爬虫，对账会偏。默认参数下单会话每分钟至多 2 篇；测试钉住「真人会话任意 5 分钟窗口 ≤ 15 条」。
- **时间字段**：服务端事件 `sent_at` = `created_at` + 5～50 毫秒；浏览器事件按门户的攒批规则，每 5 秒或满 50 条或离开页面时随一批发出，`sent_at` 是那一批的发出时刻。灌历史时 `received_at` = `sent_at` + 20 毫秒，`derived_at` 按采集端点的公式算（= `created_at` + 20 毫秒）。
- 默认参数下一个真人会话平均约 18 条事件（曝光占一半以上），启动时用固定种子抽 10,000 个会话实测平均值，供实时压测换算速率。

### 5.5 爬虫与无脚本访客

| 类型 | 份额（占会话） | 行为 | 预期判定 |
|---|---|---|---|
| 声明身份的爬虫 | 3% | UA 是 Googlebot、bingbot、Baiduspider 等（取自 COUNTER-Robots 列表能命中的真实 UA），不跑脚本（只有服务端事件），每 2～5 秒一篇、线性遍历文献池，一个会话 50～300 篇 | `ua_list` |
| 伪装成浏览器的爬虫 | 1% | 普通 Chrome UA，跑脚本（有 `page_viewed`），每 3～6 秒一篇、一个会话 60～200 篇 | 窗口超过 30 条后标 `rate`，整个会话不进对外数字 |
| 不跑脚本的访客 | 2% | 普通 UA，只有服务端事件，像真人一样浏览 1～3 页 | 不标爬虫，但不是真人会话（没有浏览器事件，埋点方案 §6.5） |

灌历史时由模拟器自己填 `is_bot` / `bot_reason`，**逐条复刻采集端点的实际规则**（`CollectEventsHandler.classify` 与 `device-rate.lua`），这样两种模式标出来的爬虫事件数、原因分布一致（报告与 PAP-172 的数据质量面板会用到）：

- 先 `ua_list`，再 `ua_class`（UA 表里预先标好），命中这两条的事件不进频率窗口；
- 频率窗口按 `device_id` 跨会话累计，只有 `article_viewed` 计入（成员是 `event_id`，重发不顶高），时间用 `received_at`；
- 窗口里超过 30 条时，这台设备此刻的**所有事件**（含 `page_viewed`、曝光、其他服务端事件）都标 `rate`，直到窗口回落。

实时压测时由采集端点判定，对账检验它判得对不对。

### 5.6 故意重发 1%

每条事件以 1% 的概率被再发一次，`event_id` 等内容完全不变：

- **灌历史**：重发的副本放进**同一天的下一次写入**（不同令牌），否则同一次写入里的重复由令牌之外的机制吸收，测不到 `event_id` 去重。当天最后一块的副本单独写一次。
- **实时压测**：成功送达后 2～10 秒，在**另一个请求**里再发。

## 6. 中性事件与两种输出

生成器只产出中性的 `SimEvent`（事件名、来源、时间、人、会话、页面、属性、真值标签），再由两个序列化器转成输出：

| 序列化器 | 输出 | 额外填的字段 |
|---|---|---|
| `EnrichedEventWriter`（灌历史） | 富化事件 ③ 的一行 JSON | `received_at`、`derived_at`、`country_code`、`province`、`browser_family`、`os_family`、`device_class`、`is_bot`、`bot_reason`；不含 `client_ip`、`user_agent` |
| `CollectEnvelopeWriter`（实时压测） | 采集信封 ② | `client_ip`、`user_agent`；富化交给采集端点 |

格式细节（测试逐条钉住）：时间恰好 3 位毫秒 + `Z`；UUID 小写，`event_id` 用 UUIDv7（时间有序，按 `created_at` 生成）；`user_id` 为十进制字符串或 `null`；`page_path` 不带查询串、与 `page_type` 对应（文献页 `/papers/{id}`、检索 `/papers`、首页 `/`）。

**第一个 TDD 锚点**：随机种子下生成 10,000 个会话，每条事件序列化后分别通过 api 模块的 `enriched-event` 与 `collect-envelope` schema 校验。

## 7. 真值与对账

### 7.1 口径（与 PAP-170 的「看过人数」查询完全一致）

真值 = 每个（北京时间日期，文献）的独立读者数，分两列：

- **看过**：有过查看类事件（`article_viewed`、`fulltext_read`、`pdf_downloaded`、`article_collected`）的人数。
- **读过全文**：有过 `fulltext_read` 或 `pdf_downloaded` 的人数。
- 「人」：登录按 `u:{user_id}`，访客按 `d:{device_id}`。
- 只数**真人会话**：会话里至少一条浏览器事件、且没有一条被判为爬虫。爬虫会话整个不算；不跑脚本的访客不算。「被判为爬虫」按采集端点的同一套规则看**实际送达的事件**（灌历史用写入时打的标记，实时压测由模拟器对送达的事件复刻一遍），不用模拟器私下知道的身份：没跨过频率阈值就结束的伪装爬虫，管线认不出，真值也照算。这样对账检验的是「管线按定好的规则算得对不对」，而不是规则本身的识别率（实施时改定）。
- 「日期」按每条事件 `derived_at` 的北京时间切：跨午夜的会话两边各算。
- 同一个人一天里看同一篇多少次、重发多少次都只算 1。

### 7.2 账本

- 只记**已确认送达**的事件：灌历史在那次写入成功后记；实时压测在收到 202 后记。最终放弃（重试用尽）的事件不进真值，并计入 `summary.json` 的失败数。
- 会话结束且其事件都已有结果后，按 §7.1 判定是否真人；是真人的，把（日期，文献，人）放进当天的缓冲。人和文献在运行内都有整数下标，一对打包成一个 `long`；一天结束时排序去重计数，写 `truth.csv.gz`（`day, article_id, viewers, fulltext_readers`）。灌历史一天约 100 万条事件，缓冲十几 MB。
- `summary.json` 另记：送达的事件总数（不含重发）、重发条数、按类型的会话数、爬虫事件数、失败数、每天的事件数。

### 7.3 对账命令

`reconcile --run N`：按天查 ClickHouse，与真值逐行比对。每天一条查询，避免对全部会话 `GROUP BY` 撞上 1 GiB 上限：

```sql
SELECT article_id,
       uniqExactIf(person_id, v = 1)  AS viewers,
       uniqExactIf(person_id, ft = 1) AS fulltext_readers
FROM (
    SELECT article_id, session_id, person_id, max(viewed) AS v, max(fulltext) AS ft
    FROM article_session_reads
    WHERE day = {d:Date} AND is_synthetic = 1 AND {本次运行的人}
    GROUP BY article_id, session_id, person_id)
WHERE session_id IN (
    SELECT session_id FROM session_quality
    WHERE first_day BETWEEN {d:Date} - 1 AND {d:Date} + 1
    GROUP BY session_id HAVING max(has_client) = 1 AND max(has_bot) = 0)
GROUP BY article_id
```

- **会话特征看前一天到次日**：`session_quality` 每次写入各产生一行、后台合并前不汇总。跨午夜的会话如果在次日那次写入里才被标为爬虫（伪装爬虫过了午夜才跨过频率阈值），只看到当天就会把它在前一天误算成真人；`d + 1` 把次日那行也纳入判定（PAP-171 全量对账 26 行不一致即此原因，实施后改定）。

- **误差** = Σ|ClickHouse − 真值| ÷ Σ真值，看过、读过全文各算一个；用绝对差之和，正负不会互相抵消。**≤ 1% 判通过**（D10）。
- 同时输出：每天的两边合计、不一致的行数与前 10 行；**写入了多少行 vs 去重后多少条**——写入行数（含 1% 重发）取模拟器自己记的已确认数，去重后条数按天各算 `uniqExact(event_id)` 再求和（每天一个分区，同一事件的重发落在同一天；对 1 亿个 UUID 一次性 `uniqExact` 要几 GB 内存，会撞上 1 GiB 上限）。不用 `count() − uniqExact`：`ReplacingMergeTree` 后台合并会把重复行合掉，差值随时间降到 0，不是稳定的证据。去重有效的证据是「写入行数比去重后多 1%，而对账误差仍 ≤ 1%」。
- 实时压测要等消费滞后归零后再对账（Grafana 的滞后面板到 0 后再等 1 分钟）。

## 8. 灌历史（`backfill`）

```text
backfill --run 1 --seed 42 --events 102000000 --days 88 --articles articles.txt \
         --clickhouse-url http://…:8123 --clickhouse-user patra  （密码读环境变量 CLICKHOUSE_PATRA_PASSWORD）
```

- **时间范围**：今天往前 88 天到昨天（北京时间），不写今天、不写未来。最早一天离 90 天的 TTL 还有两天余量，跑几个小时也不会边写边被删。各天的事件数按周内系数分配，合计为 `--events`（不含 1% 重发）。
- **分块**：入库设计 §9 的约束——**一次写入只放一天**、按顺序、写完一次再写下一次。一天拆成若干块，每块约 25 万行（一块就是一个 ClickHouse 数据块，令牌含义不含糊）；块由「固定的一组会话」组成，用 `(种子, 日期, 块号)` 派生的随机数生成，所以同一块重新生成逐字节一样。
- **令牌**：`backfill:{运行号}:{参数指纹}:{日期}:{块号}`；跨午夜的会话落在第二天的部分单独写一次，块号写 `spill`；重发副本见 §5.6。**参数指纹**是种子、事件总数、天数、模型文件、文献池文件的哈希前 8 位：同一运行号换了参数再跑，令牌就不同——否则 ClickHouse 会把内容不同的新块当成重复整块丢掉，数据静默缺失。
- **写入设置**：与入库完全相同（`ClickHouseInserts`：令牌、`deduplicate_blocks_in_dependent_materialized_views = 1`、`input_format_null_as_default = 1`、`date_time_input_format = 'best_effort'`、`input_format_json_read_objects_as_strings = 1`），格式 `JSONEachRow`，请求体边生成边写（不先拼成大字符串），开 HTTP 压缩。
- **失败**：连不上、超时、5xx 按指数退避重试同一块（同一令牌，ClickHouse 认出已写过的会丢弃）；`NOT_ENOUGH_SPACE` 立即停止并报告——那是磁盘红线，不能硬写。
- **只灌前几天**：`--only-days N` 只写最早的 N 天，其余照常生成真值但不写入；它不进参数指纹，所以之后去掉它再跑全量，前 N 天的块令牌相同、内容相同，被整块丢弃（试灌就这样做，§12）。
- **重跑与续跑**：同样参数整条命令重跑是安全的——每块令牌不变，在去重窗口（最近 1,000 次写入，88 天约 530 次）内整块被丢弃；万一窗口已被别的写入挤掉，`ReplacingMergeTree` 与 `uniqExact` 仍兜底。`--from-day` 跳过已完成的天（这些天只生成、不写入，用来重建真值与跨午夜部分）。
- **进度**：每块打印日期、行数、耗时、累计条数；每天结束时打印 `system.parts` 里 `events` 的活跃片段数。

## 9. 实时压测（`live`）

```text
live --run 2 --seed 7 --rate 1200 --duration 30m --articles articles.txt \
     --collector-url http://100.103.73.27:9528/patra-analytics/events \
     [--clickhouse-url … 采样 parts] [--prometheus-url … 导出滞后曲线]
```

- **到达**：速率 λ（会话/秒）= 目标事件速率 ÷ 每会话平均事件数（§5.4 实测）；按指数分布的间隔产生会话，每个会话一个虚拟线程，按真实停留时间（`Thread.sleep`）走跳转链。默认 1,200 条/秒，留出 20% 余量保证 ≥ 1,000；30 分钟内同时在线约 3 万个虚拟线程。
- **预热**（实施时补）：开环从零在线起步，要等最长的会话（声明爬虫最长约 25 分钟）走完才到稳态，前几分钟速率明显不够（本机实测不预热时头几秒只有目标的约 15%）。所以开跑前按第一级速率，在过去 30 分钟里按泊松过程补上「已经开始」的会话，各自只发还没到期的请求，一开始在线会话的数量与进度就和稳态一样；已过去的请求不发、不进真值。`--no-warm-up` 可关。
- **同一个人不同时开两个会话**：抽到正在线的人就重抽，避免同一设备并发把频率规则顶穿。
- **发送**：
  - 服务端事件在「页面请求」时立即发（一批 1～2 条，模拟门户服务端）；浏览器事件按 5 秒 / 50 条 / 离开页面攒批发出（模拟门户转发浏览器批次）。
  - 共享一个 JDK `HttpClient`；同时在途的请求数有上限（默认 512），满了就排队——排队时间计入延迟，开环的压力不会被吞掉。
  - 503、超时、连接错误按指数退避重试（同一请求体、同一 `event_id`），最多 5 次；400 / 413 立即记为失败（说明模拟器有 bug）。
  - 202 响应里的 `rejected` 必须为 0，不为 0 记错误日志与计数（合成事件不该进 dlq）。
- **到达窗口**：开始与停止产生会话的时刻写进 `latency.json`，报告的速率只在这个窗口里算，不把停发后的收尾算进去（实施时补）。
- **每秒统计** `live-stats.csv`：发出 / 确认 / 失败的事件数，各状态码请求数，延迟 p50 / p95 / p99（对数分桶直方图），在线会话数，在途请求数。控制台每 10 秒打一行。
- **parts 采样**（给了 `--clickhouse-url` 时）每 30 秒记 `events` 等四张表的活跃片段数、最大单分区片段数、这 30 秒里完成的合并次数 → `parts.csv`。合并次数取自 `system.part_log`（`patra` 账号读不了 `system.merges`），单独查：查不到时只空这一列，片段数与新鲜度照记（实施后改定）。
- **滞后**：入库的 `analytics.ingest.lag` 是「落后多少**条**」，D9 要的是「落后多少**秒**」。所以 parts 采样同时测**新鲜度**：`now() − 本运行最新一条事件的 received_at`（采集端点收到它的时刻），即「收到之后多久能在 ClickHouse 里查到」，每 30 秒一个点，D9 的「峰值 ≤ 10 秒、不持续上涨」按它判。给了 `--prometheus-url` 时，结束后再按运行时间段导出 `analytics.ingest.lag`（Prometheus 里带 Collector 前缀）→ `lag.csv`，作条数维度的佐证。
- **结束**：到时长后停止产生新会话，等在线会话走完（上限 5 分钟）；到上限还没走完的会话就地截断、不再发送，已送达的部分照常进真值，再写真值与汇总——否则它们会在汇总写完后继续往管线里送事件，对账对不上（实施时补）。`--ramp` 可分级加压（如 `500,1000,1500,2000` 每级 5 分钟），用来找瓶颈。

## 10. 报告（`report`）

`report --run 1 --run 2 --out docs/patra/reports/…` 汇总成 Markdown，模板：

```markdown
# v0.18 行为数据压测报告（{日期}）

## 环境
mini 配置；各容器内存上限；ClickHouse / Kafka 版本；模拟器参数与种子（params.json）

## 1. 灌历史（D8）
- 写入：{N} 条（另重发 {n} 条），{天} 天，用时 {t}，平均 {r} 行/秒
- 存储（system.parts，active）：| 表 | 行数 | 压缩后 | 未压缩 | 压缩比 | 每行字节 | 分区数 |
- 磁盘：灌前剩余 {a} GB → 灌后 {b} GB（要求 ≥ 20 GB）
- 片段：每天写完后 events 活跃片段数的最大值

## 2. 实时压测（D9）
- 速率：目标 {x}/秒，实际 {y}/秒（每分钟曲线）；请求 {k} 个，状态码分布；延迟 p50/p95/p99
- 消费滞后：峰值 {s} 秒；曲线（lag.csv）；是否持续上涨
- 每批大小：analytics.ingest.batch.size 的分布
- 片段与合并：parts.csv 的最大值；有没有 TOO_MANY_PARTS（system.errors）
- 容器内存：各容器峰值 / 上限（mem.csv）
- 一次真实浏览多久反映到计数上：{秒}

## 3. 对账（D10）
| 运行 | 看过：真值 / ClickHouse / 误差 | 读过全文：真值 / ClickHouse / 误差 | count() − uniqExact(event_id) |

## 4. 发现的瓶颈与结论
```

能从文件和 ClickHouse 查到的（存储、parts、对账、吞吐、延迟、滞后）由命令填好；容器内存来自 §12 的采样脚本；「真实浏览多久反映到计数上」和「瓶颈与结论」人工填写。报告存 `docs/patra/reports/v0.18-analytics-load-test.md`，随后端 PR 提交。

## 11. 本地验证（完成标准，不碰 mini）

本机 catalog 库是空的，本地运行与测试用测试资源里的一份小文献池（几千个 `id,venue_id`，ID 是随便取的正整数）：本地只验管线与对账，不看门户。

1. **集成测试**（Testcontainers ClickHouse，§13）：灌历史小规模跑通、对账误差为 0。
2. **本机端到端**：本机 OrbStack 起 pipeline 栈（Kafka + ClickHouse，同一份 compose）与 analytics 服务（指向本机的 Kafka / ClickHouse），`live --rate 200 --duration 2m` 跑通，等滞后归零后 `reconcile` 通过；再 `backfill --events 1000000 --days 3` 跑通并对账。结果记进 plan 的验证记录。

## 12. 在 mini 上执行（每一步都先经编排会话征得用户同意）

**运行位置（§15 第 2 条）**：建议灌历史在 mini 上跑（用 `eclipse-temurin:25-jre` 临时容器挂载 `distTar` 解开的目录，接入 pipeline 网络直连 `patra-clickhouse:8123`）——经 Tailscale 从 MacBook 灌，要传几十 GB 的 JSON（压缩后也有数 GB），且笔记本休眠会中断；实时压测在 MacBook 上跑——业界做法是压测机与被测系统分开，否则模拟器自己抢 mini 的 CPU 与内存，测出的瓶颈不可信。MacBook 上起 JVM 前按本机惯例 `export JAVA_TOOL_OPTIONS` 关掉系统代理，地址用 `100.103.73.27` 而不是 MagicDNS 名。

**前提**：PAP-167～169（以及要验「真实浏览反映到计数」的 PAP-170、PAP-173）已部署到 mini；pipeline 栈在跑。

**步骤**：

1. **基线**：`df -h`、`docker stats --no-stream`、`system.parts` 合计，记进报告。
2. **文献池**：在 mini 的 PG 上执行 §5.1 的只读导出（`id,venue_id`）。
3. **试灌**：`backfill --run 1 --events 102000000 --days 88 --only-days 2`（正式运行的前 2 天，约 230 万条）→ 读 `system.parts` 的 `bytes_on_disk`（明细 + 四张汇总表）算出每条事件的字节数 → **乘到 1.02 亿条，报预计占用给用户**（另算合并时的临时空间约为最大分区的 2 倍）。灌完剩余预计 < 25 GB 就停下来商量。
4. **正式灌**：同意后去掉 `--only-days` 再跑同一条命令（前 2 天被令牌去重，不会重复）。期间每 15 秒采样 `docker stats` 到 `mem.csv`。灌完立即由 `reconcile` 输出按天求和的去重条数 ≥ 1 亿（D8；最早那天两天后会被 TTL 删掉，过几天再查会低于 1 亿，这是正常的）。
5. **对账**：`reconcile --run 1`（D10）。
6. **实时压测**：同意后在 MacBook 上 `live --run 2 --rate 1200 --duration 30m`，同时在 mini 上继续采样内存；中途让 B 在门户打开一篇文献，记下多久反映到计数上（D9 的最后一条）。
7. **压测后**：等滞后归零 → `reconcile --run 2` → `report --run 1 --run 2`。

**权限核对**：`patra` 账号显式授权了 `system.parts`、`part_log`、`query_log`；parts 采样还要读 `system.merges`、报告查 `TOO_MANY_PARTS` 要读 `system.errors`。ClickHouse 默认不要求单独授权读 system 库，仓库也没改这项，第 1 步基线时用 `patra` 账号各查一次确认；读不了就改用 `part_log`（合并记录）与 `query_log`（报错记录）取证，不去改 infra。
8. **清理**：合成数据保留（PAP-172 面板要用）；Kafka 的压测数据按 2 天保留期自然过期。

## 13. 测试

| 层 | 测什么 |
|---|---|
| 单元：格式 | 时间 3 位毫秒 + `Z`；UUIDv7 小写且随时间递增；设备 ID 43 位且带运行前缀；用户 ID 区间；`page_path` 与 `page_type` 对应 |
| 单元：schema | 10,000 个会话的每条事件分别通过 `enriched-event` 与 `collect-envelope` 校验（§6） |
| 单元：Zipf | 固定种子抽 100 万次：第 1 名占比 ≈ 1 / H(N)（误差 5% 内），前 1% 的文献占约一半访问 |
| 单元：到达曲线 | 凌晨 3 点 / 上午 10 点的到达率之比、周日 / 周三之比与参数一致；一天的会话总数期望等于日均 |
| 单元：会话链 | 全文事件之前一定有同一篇的文献页；`fulltext_read` 每页最多一次；`article_collected` 只出现在登录会话；真人会话任意 5 分钟内 `article_viewed` ≤ 15；会话至少一条浏览器事件 |
| 单元：爬虫 | 声明身份的 UA 命中 COUNTER-Robots；伪装爬虫在 5 分钟内超过 30 篇，灌历史时第 31 条起标 `rate`；真人 UA 不命中列表、Yauaa 不归为 Robot 类 |
| 单元：地区 | IP 表里每个 IP 经 ip2region 查出的国家 / 省份与表中声明一致（用 infra 同样的数据文件下载方式） |
| 单元：重发 | 重发比例 ≈ 1%（±0.1%）；灌历史的副本与原件不在同一块 |
| 单元：真值 | 手工构造的会话：同人同篇同日两次算 1；跨午夜两天各算；爬虫会话、无浏览器事件会话不算；重发不算；失败事件不算 |
| 单元：确定性 | 同参数生成同一块两次逐字节相同；令牌格式 |
| 单元：实时发送 | 用 JDK 自带 `HttpServer` 起假端点：503 后重试且 `event_id` 不变；400 不重试；在途上限生效；每秒统计正确；按速率 λ 跑 10 秒的事件数在期望的 ±10% |
| 集成（Testcontainers ClickHouse） | 迁移建表 → 对 `events` 执行 `SYSTEM STOP MERGES`（不让后台合并吃掉重复行，断言才稳定）→ 灌历史 3 天 × 5 万条 → `reconcile` 误差 0；`count()` 等于模拟器记的写入行数、`count() − uniqExact(event_id)` 等于重发数；同参数再跑一遍，`count()` 不变（令牌去重）；先用 `--only-days 1` 跑、再跑全量，结果与一次跑完相同；每天一个分区 |

## 14. 不做的事

- 不做按 IP 的频率规则相关的流量（采集端点本版不做）。
- 不模拟「访客登录后认回账号」：本版只有面板的新增 / 留存用到，PAP-172 需要时再说。
- 不提供删除合成数据的命令：合成数据要留给面板；需要清空时按 runbook 删表数据。

## 15. 用户拍板（2026-11-02 定稿）

1. **文献池**：用 catalog 真实文献 ID（PAP-170 的约束，§5.1）；在 mini 上跑只读导出 SQL，执行前仍按 §12 先征得同意。
2. **运行位置**：灌历史在 mini 上用临时 Java 容器直连 ClickHouse，实时压测在 MacBook 上跑（§12）。
3. **合成用户与注销清理**：在注销清理找用户的查询（`ClickHouseEventStoreAdapter` 的找用户 SQL）里排除合成用户 ID 区间（`user_id < 9000000000000000000`），一行 SQL 加一条集成测试，由本 Issue 一并提交。原因：清理任务每天把日活表（400 天）与文献 × 会话汇总（永久）里出现过的用户按 100 个一批拿去 identity 查状态，不排除的话 6 万个合成用户每天多约 600 次调用、6 万条 WARN。
4. **流量模型**：先用 §5 的默认数字。

## 16. 我的决定（已随定稿确认）

1. 模拟器是**不依赖服务内部代码的独立命令行**，只共用 api 模块的 schema 与爬虫列表；服务代码只在集成测试里用来建表（§3）。
2. 命令行解析用 **picocli**（Java 命令行的主流库），不用 Spring Boot（§3）。
3. **一个流量模型、两种输出**：灌历史自己做富化并按采集端点的同一套规则标爬虫；实时压测交给采集端点富化（§6）。
4. **运行号进设备 ID 与用户 ID**，对账只看本次运行的人，重跑同一运行号是幂等的（§4）。
5. 灌历史铺**昨天往前 88 天**，不写今天，给 TTL 留两天余量；目标 1.02 亿条，留 2% 余量（§8）。
6. 灌历史**一天拆成 25 万行一块**、确定性生成、令牌 `backfill:{运行号}:{参数指纹}:{日期}:{块号}`；重发副本放进下一块（§8、§5.6）。
7. 实时压测**开环**发压：会话按泊松过程到达、一个会话一个虚拟线程；在途请求设上限；默认 1,200 条/秒留 20% 余量（§9）。
8. 真值**只记已确认送达的事件**，按 PAP-170 查询的同一口径算；误差用绝对差之和（§7）。
9. 对账**按天查询**，避开 1 GiB 查询内存上限（§7.3）。
10. mini 上**先试灌 2 天、实测每条字节数、报预计占用**，再正式灌（§12）。
11. 每次运行的合成用户控制在 3 万，mini 上只做两次运行（试灌是正式灌历史的前两天，不另起运行号）；清理任务排除合成用户区间（§15 第 3 条）。
12. 报告单独成文 `docs/patra/reports/v0.18-analytics-load-test.md`；mini 执行步骤写在本 spec §12，不改 runbook（PAP-170 的会话也在改 runbook，避免冲突）。
13. 灌历史**由模拟器逐条复刻采集端点的爬虫规则**（含跨会话的设备频率窗口、超限后同设备所有事件标 `rate`），两种模式的爬虫标记一致（§5.5）。
14. D8 的条数按天 `uniqExact` 再求和；「去重有效」用「写入行数 vs 去重后条数 + 对账误差」证明，不用会随合并漂移的 `count() − uniqExact`（§7.3）。
