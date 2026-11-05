# 运营分析面板设计（PAP-172）

> **状态**：已定稿（2026-11-02 用户「按建议定」，§11）
> **Issue**：PAP-172（GitHub #365）；依赖 PAP-168 的表、PAP-171 的合成数据与对账
> **前提**：应用指标进 Prometheus 的修复（七个服务走 Micrometer OTLP 注册表）另走 PR #375，本 spec 只在「数据质量」里用到它的结果

## 1. 要做什么、为什么

v0.18 的行为数据已经入库（mini 上 1.02 亿条合成历史事件，2026-08-06～11-01）。这一版在公司里最主要的用途，是给产品与运营看一块指标面板：每天有多少人来、来了还回不回来、从检索到读全文在哪一步流失、检索有没有搜不到的词、数据本身可不可信。

用户第一次接触数据分析，是为进公司做准备，所以**指标定义和面板结构都按业界做法来**，每个指标在 §4 写清口径和出处；面板上每块图的描述里也写一句口径，看图时不用回来翻文档。

**做**

1. 一块 Grafana 看板「运营分析」，六组：活跃、留存、漏斗与跳出、检索、文献使用（COUNTER）、数据质量（§5）。
2. 面板读的 ADS 层（应用数据层）汇总表：ClickHouse 的定时刷新物化视图每小时把明细汇总成按天的小表（§3）。
3. 模拟器对账结果写进 ClickHouse，面板据此画丢失率（§4.5）。
4. 面板变量：时间范围、含不含合成数据、含不含爬虫（§5.1）。
5. 埋点方案里写到面板的几处同步改（§9）。

**不做**

- 不新增常驻容器、不调高任何容器内存（mini 已超卖）。
- 不做告警规则（单人 dev 环境，见记忆「不给单人 dev 环境套生产级运维模板」）。
- 不做用户分群、自定义漏斗、任意维度下钻（release spec 第 3 节 I）。
- 不做门户里的运营页面：面板只在 Grafana 上，只给作者看。

## 2. 先讲清几个概念

- **DAU / WAU / MAU**：一天 / 近 7 天 / 近 30 天里来过的独立用户数，按「滚动窗口」算（今天的 MAU = 今天往前 30 天里来过的人），这是 Facebook、Amplitude 的默认做法；不用自然月，因为自然月的 MAU 月初总是偏低。
- **黏性（Stickiness）= 平均 DAU ÷ MAU**：一个月里平均每人来几天的另一种说法。社交产品 50% 算很好，工具类 10%～20% 常见。
- **新增访客**：这一天第一次出现的人（之前从没来过）。
- **N 日留存（经典留存 / Day-N retention）**：某天新来的一批人（同期群，cohort）里，**恰好**在第 N 天又来的比例。Amplitude、Mixpanel 默认就是这种；另一种「第 N 天及以后任一天回来」叫无界留存，数字更高、容易自我安慰，本版不用。
- **漏斗**：一个会话里，按顺序走过「访问 → 检索 → 点检索结果 → 文献页 → 读全文」各步的会话数。ClickHouse 的 `windowFunnel(窗口)` 对每个会话算出「按顺序最远走到第几步」，窗口取 30 分钟（与会话切分一致）。
- **跳出率**：只看了一个页面就离开的会话占比（Universal Analytics 的经典定义）。GA4 改用「互动会话」的反面，需要停留时长，本版没有这个事件，所以用经典定义。
- **检索无结果率**：结果数为 0 的检索占比。电商与站内检索（Algolia、Elastic App Search）把它当头号检索健康指标，配一张「无结果词排行」让人知道该补什么数据或同义词。
- **检索点击率（Search CTR）**：至少点了一个结果的检索占比（Algolia 的定义：有点击的检索 ÷ 检索数）。不用「点击数 ÷ 曝光数」：它衡量的是卡片，不是检索。
- **数据分层**：入库设计已把 `events` 叫 ODS + DWD、汇总表叫 DWS。面板直接扫明细在公司里是禁忌：一块看板十几个图同时扫亿级表，库会被拖垮。通行做法是再加一层 **ADS**（Application Data Service，给某个应用用的小表），面板只读它。

## 3. ADS 层：为什么要、怎么做

### 3.1 实测：直接扫明细不行

在 mini 上用 patra 只读账号实测 30 天范围（每天约 116 万条事件）：

| 查询 | 耗时 | 内存 |
|---|---|---|
| 漏斗（`windowFunnel`，按会话） | 6.1 s | 403 MiB |
| 跳出率（按会话） | 4.2 s | 172 MiB |
| 滚动 DAU / WAU / MAU（日活表） | 2.5 s | 520 MiB |
| 日活（日活表） | 1.0 s | 194 MiB |
| 留存 30 个同期群（日活表） | 0.6 s | 167 MiB |
| 迟到分位数（明细） | 2.3 s | 9 MiB |
| 无结果词（明细） | 0.7 s | 20 MiB |

ClickHouse 容器只有 2 GiB，每条查询上限 1 GiB。Grafana 打开看板时所有图**同时**发查询：光漏斗、跳出、MAU 三块就要 1.1 GiB，范围拉到 90 天还要翻三倍。所以面板不直接扫 `events` 与日活表做按会话 / 按人的聚合。

### 3.2 做法：定时刷新物化视图

ClickHouse 的**定时刷新物化视图**（refreshable materialized view，`REFRESH EVERY 1 HOUR`）每小时跑一次 SELECT，把结果写进目标表。和入库设计里那几个「写入时触发」的物化视图不同：它适合跨会话、跨天的聚合（漏斗、MAU 都要看整个会话或 30 天）。

- 每次刷新只重算**昨天和今天**（北京时间），`APPEND` 写进 `ReplacingMergeTree(computed_at)`，键是「天 + 维度」。面板读每张表的 `<表>_latest` 视图：**每天只取 `computed_at` 最大的那一版**。不能只靠 `FINAL`：上一版有、这一版没有的键（例如会话后来多看了一页，从「单页」挪到「多页」）`FINAL` 去不掉，数字会翻倍（终审 C1）。同一次刷新里 `now64(3)` 是常量，同一天的行共用一个 `computed_at`。
  - 为什么连昨天也重算：事件会迟到（§4.5 迟到分布），跨午夜的会话也要等会话结束；重算前一天把这些补上。比昨天更早到的事件不再补进 ADS（量见迟到分布，预期远小于 0.1%）。
  - 留存要等第 30 天，所以留存表重算的是「最近 32 个同期群」（§4.2）。
- **口径只写一份**：每张 ADS 表的计算写成一个带参数的视图 `<表名>_calc(from, to)`（ClickHouse 参数化视图，按北京日期区间算），刷新视图、回填脚本、集成测试都调它：刷新视图传 `today() - 1` 到 `today()`，回填传某一天，测试传固定日期。已在 26.8.36.3 上实测刷新视图里可以用 `today() - 1` 这样的表达式传参。
- **新鲜度**：面板数字最多晚 1 小时，图的描述里写明。实时状态看「Analytics 管线」看板（Prometheus）。
- **历史回填**：刷新视图只算最近两天，2026-08-06 以来的历史用一个脚本按天逐天 `INSERT … SELECT`（每天一条，内存与上表 1 天的量相当），在 mini 上跑一次（§8）。
- 迁移脚本照入库设计 §5 的规矩放 `patra-analytics-infra/src/main/resources/clickhouse/`，接着编号 `V6__…`，由 analytics 服务启动时执行。

### 3.3 维度：合成数据与爬虫两个开关怎么落到表上

每张 ADS 表都带这两列维度（例外见下），面板变量直接过滤它们：

- `is_synthetic`（0 / 1）：合成与真实的人、会话不重叠（合成设备 ID 以 `sim` 开头），可以相加。「含合成数据」= 两行相加，「不含」= 只取 0。
- `traffic`：`human` 或 `all`。`human` 是对外数字用的真人口径（埋点方案 §6.5：按会话 = 有浏览器事件且无爬虫事件；按人 × 天 = 当天有浏览器事件且无爬虫事件，入库设计 §5.5）；`all` 是全部流量（含爬虫与不跑脚本的访客）。两行分别算好再存，不用相减：按人去重的数字（MAU 等）不能拆成几类相加。
- 例外：会话表 `ads_daily_sessions` 存互斥的 `session_kind`（`human` / `bot` / `no_script`），会话数可以相加，「含爬虫」= 三类都算，会话构成图也直接用它；数据质量的表只有 `is_synthetic` 或什么都没有（§4.5）。

## 4. 指标口径

除特别说明，「天」按北京时间，「人」按埋点方案 §6.1（登录按 `user_id`，未登录按 `device_id`）。

### 4.1 活跃 —— `ads_daily_active`

| 列 | 口径 |
|---|---|
| `dau` / `wau` / `mau` | 当天 / 含当天往前 7 天 / 往前 30 天里出现过的独立「人」，数据源日活表 `daily_person_activity` |
| 新增访客 | 当天第一次出现的人（往前在日活表里找不到），按 §4.6 的「认回」后的人判断，登录前用设备、登录后用账号的同一个人不算两次新增。它就是 §4.2 同期群的人数 `cohort_size`，不另存一列 |

面板：DAU / WAU / MAU 三条线；黏性（DAU ÷ MAU，取时间范围内的平均）一个数字和一条线；新增访客柱状图；范围末日的 DAU、MAU、黏性三个数字卡。

### 4.2 留存 —— `ads_cohort_retention`

| 列 | 口径 |
|---|---|
| `cohort_day` | 同期群：这批人的首日（新增那天，§4.1 同一套首日） |
| `cohort_size` | 这一天的新增人数 |
| `d1` / `d7` / `d30` | 首日之后恰好第 1 / 7 / 30 天又出现过的人数（ClickHouse `retention`） |

- 还没到第 N 天的同期群，dN 存 `NULL`，面板上显示空白，不显示 0（显示 0 会被读成「全流失」）。
- 面板：同期群表（行 = 首日，列 = 人数、次日、7 日、30 日留存率，按百分比上色），加一条「按首日的次日 / 7 日留存率」折线。

### 4.3 漏斗与跳出 —— `ads_daily_sessions`

一个会话一行聚合出来，再按天汇总。会话的「天」= 会话第一条事件的北京日期。

| 列 | 口径 |
|---|---|
| `funnel_level` | 0～5。第 1 步「访问」是会话级门槛：会话里有 `page_viewed` 就算到了，不参与排序。第 2～5 步用 `windowFunnel(1800)` 排顺序：`search_performed`；`article_clicked` 且 `list_type = 'search'`；`article_viewed`；`fulltext_read` 或 `pdf_downloaded`（埋点方案 §6.3：下载 PDF 也算读全文）。没过门槛的会话记 0，过了门槛的记 `1 + windowFunnel 的步数` |
| `page_views` 分桶 | `1` / `2+`：会话里 `page_viewed` 的条数 |
| `sessions` | 会话数 |

- 为什么第 1 步不排顺序：`search_performed` 是服务端渲染时发的，`page_viewed` 要等浏览器渲染完才发，直接落在检索页的会话里检索总比访问早（模拟器里早 300 ms，真实门户也一样）。照字面五步排序，这类会话的第一次检索进不了第 2 步，漏斗会少算（我的决定）。
- 漏斗面板：五步的会话数横条，旁边标「占第 1 步」与「占上一步」两种转化率（Mixpanel、Amplitude 都这么摆）。每一步的数 = `funnel_level ≥ 该步` 的会话数。
- 跳出率 = `page_views = 1` 的会话 ÷ 有 `page_viewed` 的会话，按天一条线。
- 不跑脚本的访客、声明身份的爬虫没有 `page_viewed`，进不了漏斗第 1 步，也不进跳出率分母；「含爬虫」打开后，伪装成浏览器的爬虫会进来。另加一块「会话构成」柱状图：每天真人 / 爬虫 / 不跑脚本三类会话数（埋点方案 §6.5 承诺「面板里单独显示这类会话的量」），这块不受爬虫开关影响。

### 4.4 检索 —— `ads_daily_search`、`ads_daily_zero_result_terms`

| 表 / 列 | 口径 |
|---|---|
| `ads_daily_search` 键 | 天 + `mode`（`keyword` / `exact_pmid` / `exact_doi`） |
| `searches` | `search_performed` 条数（按 `event_id` 去重；翻页、改筛选都算一次新检索，埋点方案 §3.2） |
| `zero_result_searches` | 其中 `result_count = 0` 的条数 |
| `clicked_searches` | 至少有一条 `article_clicked`（`list_type = 'search'`，`list_id = search_id`）的检索数 |
| `ads_daily_zero_result_terms` 键 | 天 + 规范化后的检索词 |
| 检索词规范化 | 去首尾空白、转小写、连续空白压成一个；只取 `mode = 'keyword'` 且 `query != ''` 的检索 |
| `searches`、`people` | 这个词无结果的次数；搜过它的独立人数（`uniqExact` 中间状态，跨天合并时不重复算人） |

- 面板：无结果率、检索点击率两条线（按 `mode` 可选，默认只看 `keyword`：精确定位查不到通常是用户输错号，不是缺数据）；无结果词排行表（时间范围内次数前 50，带人数）。
- 检索词原样保存 90 天（埋点方案决策 6）：`ads_daily_zero_result_terms` 也设 90 天 TTL，与明细一致。
- 已知取舍：`people` 存的是 `person_id` 的 `uniqExact` 中间状态（128 位哈希），账号 ID 空间小，理论上可穷举还原；它不进注销清理，只靠 90 天 TTL 删掉。

### 4.5 数据质量 —— `ads_daily_quality`、`reconcile_results`

| 指标 | 口径 | 数据源 |
|---|---|---|
| **丢失率**（对照模拟器真值） | 每次运行、每天：`1 − 入库的去重事件数 ÷ 模拟器确认送达的事件数`；另画「看过人数」「读过全文人数」的对账误差（模拟器 spec §7.3 的 Σ\|差\| ÷ Σ真值）。只对模拟器运行有意义，真实流量没有真值 | `reconcile_results`（模拟器的 `reconcile` 命令写入，§6） |
| **重复率**（不受任何开关影响） | 每天：`1 − 去重后事件数 ÷ 写入行数`，单独一张小表 `ads_daily_duplicates`。写入行数取 ClickHouse `system.part_log` 里这一天分区新写入的行数（含重发），去重后取 `uniqExact(event_id)`。不用 `count() − uniqExact`：后台合并会把重复合掉，差值随时间降到 0（模拟器 spec §7.3） | `ads_daily_duplicates` |
| **迟到分布** | `received_at − created_at`（服务端收到时刻减客户端产生时刻）。分 6 档：< 1 秒、1～10 秒、10～60 秒、1～10 分钟、10～60 分钟、≥ 1 小时；另给 p50 / p95 / p99。服务端事件与浏览器事件分开（浏览器时钟可能不准，负值单列一档「客户端时钟超前」） | `ads_daily_quality` |
| **坏数据量** | `events.dlq` 每天的条数，按 `reason_code` 分色 | `dlq_events`（量小，直接查） |
| 采集拒收、入库滞后 | 引用「Analytics 管线」看板的 Prometheus 指标（`analytics_ingest_lag` 等），本看板放一个链接 | Prometheus |

- 重复率不按合成数据拆：`part_log` 只知道每个数据片段写了多少行，不知道是哪种数据，入库服务和模拟器又都用 `patra` 账号写，分不开（我的决定）。
- 重复率的已知限制：`system.part_log` 只保留 7 天，所以 `ads_daily_duplicates` 在每次刷新时把当天与前一天的「写入行数」快照存下来，之后就不依赖 `part_log`；回填的历史（`part_log` 已过期的天）重复率为空。
- 迟到分布在合成历史上也有分布：模拟器灌历史时按真实延迟造了 `received_at`（实测 10-15 浏览器事件 p50 约 4 秒，服务端约 50 毫秒），不会全挤在「< 1 秒」一档。
- 「写入行数」按分区（事件的北京日期）而不是按写入时间统计，与「去重后事件数」同一口径。

### 4.6 认回：登录前后的同一个人（只用于新增与留存）

埋点方案 §6.1 要求新增、留存把「同一台设备登录前后」认成一个人。做法照神策、PostHog 的匿名 ID 关联：**登录后的事件本身同时带着 `device_id` 和 `user_id`**，所以不用去 identity 读登录记录，ClickHouse 里就能建对应关系。

- 每人首日表 `ads_person_first_day` 按原始身份存，算留存时再认回，设备后来登录时首日跟着变。已知偏差：某人某天先被算作真人、当天晚些时候才被标为爬虫时，真人口径的首日不撤回。它存 `u:<账号>`，进注销清理。
- 新表 `device_user_links`：每对（设备，用户）一行，记第一次一起出现的时刻（`min(derived_at)`），由写入时触发的物化视图从 `events` 中 `user_id != 0` 的行维护；历史由 §8 的回填脚本补。查询时每台设备取时刻最早的那个用户（`argMin(user_id, first_at)`）。按（设备，用户）存而不是每台设备一行，是为了注销清理能直接 `WHERE user_id IN (…)` 删掉（入库设计 §6），这张表要进注销清理。
- 算新增与留存时，「人」= 有 `user_id` 用 `u:{user_id}`；没有的，设备在 `device_user_links` 里有对应就用 `u:{对应的用户}`，否则 `d:{device_id}`。
- 一台设备先后登录过两个账号，归第一个（已知偏差，量很小，与埋点方案 §6.8 的已知偏差同一类）。
- 日活表的 `person_id` 已经是「登录按账号」，`d:` 开头的才需要查对应关系。
- 认回会让过去的数字变：一台设备后来登录了，它当初那天的「新增」会变成账号首次出现的那天。新增就是同期群人数，随留存表每次刷新重算最近 32 个同期群；32 天以前的冻结（我的决定）。
- DAU / WAU / MAU 不做认回：与门户对外的「人」保持同一个口径（埋点方案 §6.1 只要求新增与留存）。

### 4.7 文献使用（COUNTER 5.1）—— `ads_daily_counter_items`

学术出版界统计文献使用的标准是 COUNTER 5.1（出版社给图书馆的使用报告都按它出）。release spec 2.1 与埋点方案 §6.8 写了面板要按它统计，这一组兑现。

| COUNTER 指标 | 口径（埋点方案 §6.8） |
|---|---|
| **Investigations**（查看） | 查看类事件：`article_viewed`、`fulltext_read`、`pdf_downloaded`、`article_collected` |
| **Requests**（取全文） | `fulltext_read`、`pdf_downloaded` |
| **Unique Item …** | 按会话去重：一个会话里同一篇只算 1 |
| **Total Item …** | 双击过滤：同一个人对同一篇，两条事件相隔 ≤ 30 秒只算一条（保留后一条） |

- 表的键：天 + `article_id` + `is_synthetic` + `traffic`；列 `unique_investigations`、`unique_requests`、`total_investigations`、`total_requests`。
- Unique 两列从 `article_session_reads` 算（长期保留），Total 两列要做双击过滤，只能扫 `events`（90 天）。会话的真人判定与 §3.3 相同；COUNTER 本身要求排除机器人，`traffic = 'all'` 只为和其他组的开关一致。
- 双击过滤的做法：按（人，文献）排序后，用窗口函数看下一条事件的时刻，相隔 > 30 秒或没有下一条的才计数。
- 规模（mini 实测 10-28）：一天约 1.9 万篇被看过，表一天最多约 7.5 万行（两个开关的组合），一个月约 230 万行；面板查一个月的前 20 名是按 `article_id` 汇总这些行，与 §3.1 的无结果词同一量级。刷新时的双击过滤在一天的明细上实测 0.2 秒、7 MiB。
- 面板：四个指标按天的折线；时间范围内 Unique Item Requests 前 20 篇的表（文献 ID，带到门户 `/papers/{id}` 的链接；Grafana 拿不到标题，不跨库查 catalog）。

## 5. 看板

### 5.1 变量

| 变量 | 取值 | 默认 | 落到 SQL |
|---|---|---|---|
| 时间范围 | Grafana 自带 | 近 30 天 | `day BETWEEN toDate($__fromTime, 'Asia/Shanghai') AND toDate($__toTime, 'Asia/Shanghai')` |
| 含合成数据 | 含 / 不含 | **含**（真实流量几乎为零，Issue 原话「主要用合成数据出图」） | `is_synthetic IN (${synthetic:csv})`，含 = `0,1`，不含 = `0` |
| 含爬虫 | 不含 / 含 | **不含** | `traffic = '${traffic}'`，不含 = `human`，含 = `all` |
| 检索模式 | keyword / exact_pmid / exact_doi，多选 | keyword | 只作用于检索组 |

### 5.2 布局（从上到下，每组一行可折叠）

1. **概览**：范围末日的 DAU、MAU、黏性、次日留存（最近一个满 1 天的同期群）、无结果率、跳出率六个数字卡。
2. **活跃**：DAU / WAU / MAU 折线；黏性折线；新增访客柱状图。
3. **留存**：同期群表；次日 / 7 日留存率折线。
4. **漏斗与跳出**：漏斗横条（范围内合计）；跳出率折线；会话构成柱状图。
5. **检索**：无结果率与检索点击率折线；无结果词排行表。
6. **文献使用（COUNTER）**：四个指标折线；前 20 篇表。
7. **数据质量**：丢失率与对账误差（按运行号）；重复率折线；迟到分布堆叠柱状图与 p50 / p95 / p99；dlq 条数按原因堆叠；到「Analytics 管线」看板的链接。这一组**不受「含爬虫」开关影响**（爬虫事件同样要完整入库，质量要看全部）；迟到受「含合成数据」影响，所以 `ads_daily_quality` 的迟到部分只有 `is_synthetic` 维度、没有 `traffic`；重复率（§4.5）、丢失率（只有合成数据才有）、dlq（读不出合成标记）三块不受任何开关影响，图标题里写明。

- 每块图的描述（Grafana 的 `description`，鼠标悬停可见）写一句口径和「数字最多晚 1 小时」。
- 看板 uid `patra-analytics-ops`，放 `patra-infra/docker/grafana/provisioning/dashboards/patra/analytics-ops.json`，进现有的 Patra 文件夹；`allowUiUpdates` 沿用现有 provider 的 `true`，但以仓库文件为准，界面里改了要导出回文件。

## 6. 模拟器写对账结果

模拟器的 `reconcile --run N` 现在只打印结果。改为同时写入 `patra_analytics.reconcile_results`（迁移 `V6` 起一同建）：

| 列 | 说明 |
|---|---|
| `run`、`day` | 运行号、北京日期 |
| `delivered` | 模拟器确认送达的事件数（不含重发，`summary.json` 的 `eventsByDay`） |
| `distinct_events` | ClickHouse 里本次运行这一天的 `uniqExact(event_id)` |
| `viewers_truth` / `viewers_ch`、`fulltext_truth` / `fulltext_ch` | 两边的「看过」「读过全文」合计 |
| `viewers_abs_diff`、`fulltext_abs_diff` | 逐行绝对差之和（算误差用） |
| `checked_at` | 对账时刻；同一运行、同一天重跑时以最新一次为准（`ReplacingMergeTree(checked_at)`） |

上线时在 mini 上对 run 1（灌历史）与 run 4（最近一次实时压测）各重跑一次 `reconcile`，面板就有数据。`events` 里这一天的分区已被 90 天 TTL 删掉，或已到 TTL 前一天（今天 ≥ 那天 + 89 天：分区里的数据片段会在一天里陆续被删）时，`reconcile` 跳过这一天、不写行：否则去重条数偏低会算出假的丢失率，并覆盖掉之前的正确结果。

## 7. 内存与性能约束

- 面板查询只读 ADS 表、`dlq_events`、`reconcile_results` 与 Prometheus，单条查询目标 < 50 MiB、< 1 秒；不读 `events`、`daily_person_activity`、`article_session_reads`。
- 刷新视图错开分钟（`REFRESH EVERY 1 HOUR OFFSET n MINUTE`），同一时刻只跑一个，避开 PAP-170 热读每小时重算的整点。
- 每次刷新的输入量（按 mini 实测换算）：
  - `ads_daily_sessions`、`ads_daily_search`、`ads_daily_zero_result_terms`、`ads_daily_quality`：只读昨天和今天的 `events`，约 2～4 百万行，每条刷新预计 < 100 MiB。
  - `ads_daily_active`：两天的 DAU / WAU / MAU 要读往前 31 天的日活表，约 120 万行，实测 30 天日活 194 MiB。
  - `ads_cohort_retention`：首日不再扫日活表的全部历史（不加限制时 mini 上 90 天历史已 621 MiB，攒满 400 天会超过 1 GiB；只看窗口里出现过的人也不行，窗口里有 52.5 万人、历史共 71.9 万人，实测 1.01 GiB 超限）。改为增量的每人首日表 `ads_person_first_day`（每个原始身份、每种口径一行，`min(first_day)`；刷新视图每小时用最近两天的日活补进来），算留存时先从它挑出这批同期群的人，再只读这些人首日之后 30 天的日活。上线时在 mini 上实测峰值内存。
  - 都在 1 GiB 上限内；超过 512 MiB 会按现有配置落盘而不是失败。
- 回填脚本按天串行，不并发。

## 8. 上线（mini，需另行同意）

1. 后端 PR 合并 → CD 部署 analytics → 服务启动时执行 `V6…` 建表与刷新视图。
2. 回填脚本 `patra-infra/scripts/backfill-ads.sh`（patra 账号，按天逐天）：`device_user_links` → `ads_daily_active` → `ads_person_first_day` → `ads_cohort_retention` → `ads_daily_sessions` → `ads_daily_search` / `ads_daily_zero_result_terms` → `ads_daily_counter_items` → `ads_daily_quality` → `ads_daily_duplicates`。读 `events` 的几张从回填那天 `events` 里还剩的最早一天开始（§11 第 0 条）；`daily_person_activity` 与 `article_session_reads` 的部分从 08-06 开始。
3. 对 run 1、run 4 重跑 `reconcile`。
4. `dlq_events` 现在 0 行，「坏数据量」画不出图：验收时往采集端点发一小批故意不合格的事件（各种 `reason_code` 各几条，`is_synthetic` 无从标记，量小不影响其他图），确认能出图。
5. Grafana provisioning 每 30 秒读文件，看板自动出现；按 AC 重建一次 Grafana 容器，确认看板还在。

## 9. 埋点方案同步

- **§6.1**：「靠 identity 登录记录里设备与账号的对应关系认回」改为「靠登录后事件里同时带的设备 ID 与用户 ID 认回（本 spec §4.6）」。
- **§6.5**：「面板里单独显示这类会话的量」补上位置：运营分析看板「漏斗与跳出」组的「会话构成」图。
- **§6.8**：「面板用的 COUNTER 口径」补上位置与实现：运营分析看板「文献使用（COUNTER）」组；Unique 从 `article_session_reads` 算，Total 的双击过滤在 `ads_daily_counter_items` 刷新时做（本 spec §4.7）。

## 10. 测试

- **ADS 口径的集成测试**（`patra-analytics-infra` 的 `integrationTest`，Testcontainers 起 ClickHouse，跑全部迁移）：写一组手工构造的事件（几个人、跨午夜的会话、爬虫会话、不跑脚本的会话、重发、无结果检索、登录前后同一台设备），`SYSTEM REFRESH VIEW` 后断言每张 ADS 表的数字。一个口径一个测试，先红后绿。
- **看板 SQL 的集成测试**：读仓库里的 `analytics-ops.json`，把 Grafana 宏和变量替换成固定值，逐个面板在同一个测试库上执行，断言不报错、关键面板的数字与上面一致。这样改了表结构而看板没跟上，测试就红。
- **回填脚本**：在测试库上对某一天跑一次，结果与刷新视图算出的同一天一致。
- **模拟器**：`reconcile` 写表的集成测试（模拟器已有 `ReconcilerIT`，在其上加断言）。

## 11. 我的决定与用户已定

**我的决定**（你看时可以推翻）：

1. 加 ADS 层（定时刷新物化视图），面板不扫明细（§3）。
2. 留存用经典 N 日留存，不用无界留存；跳出率用单页会话的经典定义（§2）。
3. 检索点击率用「有点击的检索 ÷ 检索数」（Algolia 定义）；无结果相关默认只看 `keyword` 模式（§4.4）。
4. 认回用事件里的设备与账号对应，不读 identity（§4.6）；DAU / WAU / MAU 不认回。
5. 重复率从 `system.part_log` 取写入行数，每天快照（§4.5）。
6. 「含合成数据」默认打开，「含爬虫」默认关闭（§5.1）。
7. 漏斗第 1 步「访问」是会话级门槛，只给第 2～5 步排顺序（§4.3）。
8. 重复率不按合成数据拆（§4.5）；新增与留存都重算最近 32 天（§4.6）。

**用户已定**（2026-11-02，「按建议定」）：

0. ADS 历史从回填那天 `events` 里还剩的最早一天开始，不抢在 TTL 删除前单独去 mini 建表。（`events` 按 90 天 TTL 整天删分区，合成历史最早的 08-06 约在 11-04 晚上被删；活跃与留存读 400 天的日活表，不受影响。）
1. 迁移、模拟器改动、看板 JSON 放一个后端 PR，从合并了指标 PR（#375）的 main 另切分支。
2. 本版加「文献使用（COUNTER）」一组（§4.7），PAP-172 的 To-Do 已同步补上。
3. 埋点方案要同步的是 §6.1、§6.5、§6.8（§9）。
