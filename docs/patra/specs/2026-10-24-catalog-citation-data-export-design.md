# catalog 引用数据、可重放导入与引用导出工程设计（PAP-110）

> **Issue**：[PAP-110](https://linear.app/papertrace/issue/PAP-110)（GitHub #280）
> **版本**：[v0.13 Library](../release-specs/v0.13-library.md)（2.1.3 导出与复制引用、决策 I）
> **相关设计**：[catalog 文献批量解析与批量卡片（PAP-109）](2026-10-24-catalog-publication-resolve-cards-design.md)、v0.11 增量同步（`PubmedUpdateSyncHandler` 一系）
> **下游**：PAP-113（interaction 导出编排、收藏夹按出版日期排序）、PAP-114（门户日期展示、导出与复制引用）、PAP-115（复制引用）
> **日期**：2026-10-24
> **状态**：已实施（2026-10-24：计划一 `2791bdf91`，计划二随后提交）；release spec D6 的手工导出核对与第 9 节 mini 回填待执行

## 1. 要解决的问题

v0.13 要让用户把文献导出成 RIS / BibTeX / CSV / CSL-JSON，并复制四种格式的规范引用（release spec 2.1.3、决策 I）。catalog 现在缺引用要用的数据（2026-10-24 代码核对）：

- 页码：解析器读了 `<MedlinePgn>`，但处理器从不取用，库里没有列。
- 文章号（ELocationID）：完全不解析。
- 期刊 ISO 缩写：不解析；`cat_venue.abbreviated_title` 存的是期刊目录导入的 MedlineTA。
- 出版日期：缺月 / 日时补成 1 月 1 日，`cat_publication_date.date_precision` 恒为 `day`；PMC 发布日期同样补 1。

补数据之后还要把库里已有的文献按新规则重算一遍。核对发现现有导入做不到（2026-10-24 与用户确认）：

- baseline 导入遇到库里已有的 PMID 一律跳过，Job 照报成功；
- update 同步会覆盖已有记录，但只能下载 updatefiles，读不到 baseline；
- 两条链路都没有「新旧版本」判断，重放旧文件会把后来修订过的记录退回去；
- 一条坏记录（非法日期、缺年份）会让整个文件失败；
- 原始 XML 用完即删，每次重算都要从 NCBI 重新下载。mini 到 NCBI 的网络传大文件会截断——2026-10-24 凌晨 `pubmed26n1379` 就因为下载不完整、XML 在第 875 万行解析失败。

用户 2026-10-24 拍板：这是绿地项目，导入流水线按「天然支持重处理」重做，作为本 Issue 的一部分；这次回填就是它的第一次正常使用。

## 2. 范围

本设计分四部分，各自列「做 / 不做」。

### A. 引用数据（第 4 节）

做：解析并存储页码、文章号、期刊 ISO 缩写；出版日期按实际精度存（年 / 年月 / 年月日）；按出版日期的检索排序规则；门户卡片、详情、PAP-109 解析接口带上按精度的出版日期。

不做：解析收稿 / 接收 / 修回日期与 ArticleDate（枚举里有类型但解析器从未写过，要的话另开 Issue）；`cat_venue_instance` 重新设计（只停止往月 / 日补 1）；`cat_publication_history`（没有任何代码引用的空表，不动）；`<DateRevised>` 落库（守卫改用文件位置，第 5.2 节）。

### B. 可重放的导入流水线（第 5 节）

做：baseline 与 update 共用一条写入路径；按「来源文件位置」挡住旧版本，软删也记位置；逐篇保留原始 XML 并提供本地重算任务；统一的「处理文件」入口，可显式重处理；下载后校验 NCBI 的 md5；坏记录只跳过这一篇。

不做：`parser_version` 列与「只重算过期记录」（18,807 篇全量重算很快，第 5.5 节）；全 PubMed 规模的原始数据存储（第 5.4 节的前提）；关注 / 新文献信号相关的同步范围调整（release spec 边界 A）。

### C. 导出与复制引用（第 6、7 节）

做：`POST /portal/publications/export`（≤ 5,000，流式，RIS / BibTeX / CSV / CSL-JSON）；`POST /portal/publications/citations`（≤ 100，GB/T 7714-2015 顺序编码制、Vancouver、APA 7、AMA 11），citeproc-java + 官方 CSL 样式。

不做：GB/T 7714-2025（2026-10-24 用户定：本版用 2015 版，界面写明版本；理由见第 7.3 节）；HTML / 富文本引用输出；导出时附带摘要；按收藏夹 ID 导出（收藏夹到文献 ID 的展开在 interaction，决策 I）。

### D. 回填 runbook（第 9 节）

做：在 mini 上把现有范围（baseline `pubmed26n1333`、update `1335`–`1379`）按新流水线重处理一遍。每一步执行前等用户点头。

不做：把 `update-scope` 改成 `ALL`（绝不）；扩大镜像范围。

## 3. 已定的前提

| 前提 | 来源 |
|---|---|
| 引用数据与导出归 catalog，用 CSL 标准；收藏夹导出时门户从 interaction 拿文献 ID 再调 catalog | release spec 决策 I |
| 导出范围 单篇 / 多篇 / 收藏夹 / 整库；格式 RIS、BibTeX、CSV、CSL-JSON；字段：作者全名保持顺序、标题、期刊全称与 ISO 缩写、卷、期、页码或文章号、出版日期按实际精度、DOI、PMID、PMCID；导出 ≤ 5,000 同步流式，复制引用 ≤ 100 | release spec 2.1.3 |
| 不用 citation-js、不手写引用模板、不在运行时调 NCBI | 决策 I |
| GB/T 7714 用 2015 版 | 用户 2026-10-24 |
| 导入流水线按可重放重做，三层 + 原始 XML 保留都进本 Issue | 用户 2026-10-24 |
| dev 数据可丢弃；mini 上任何重导、重跑 Job 先问用户；`update-scope` 绝不改 `ALL` | 记忆、任务说明 |
| mini 的 Nacos：`patra.catalog.pubmed.update-scope: EXISTING_ONLY`（`patra-infra/nacos/dev/patra-catalog.yaml`）；代码默认值是 `ALL` | 仓库核对 |
| mini 现状（2026-10-24 只读查询）：文献 18,807 篇（18 篇软删），全部来自 baseline `pubmed26n1333`，经 update `1335`–`1378` 覆盖过（12,015 篇至少被更新一次），`1379` 失败；catalog 库 1.95 GB；磁盘剩余 37 GB；runner 看门狗要求 ≥ 20 GB | 查询结果 |
| baseline 与 update 文件同一年共用一个编号序列（`pubmed26n0001` 起，update 接着 baseline 往下编）；NLM 规则：后出现的文件取代先出现的 | NLM 文档、现有 `PubmedUpdateFileRef` |
| NCBI 每个文件有 `.md5` 旁文件，内容形如 `MD5(pubmed26n1335.xml.gz)= 35b5eddf88f6772fc99fc00cfec173c9` | 2026-10-24 实取 |
| 门户 `PaperCard` 只显示年；详情页与文献库按精度写「2019 / 2019 年 3 月 / 2019 年 3 月 15 日」；详情页「文献档案」用 ISO 截断到精度 | PAP-107 设计会话已定 |
| 复制引用按请求顺序、GB/T 带序号；导出文件名 `patra-<范围名>-<YYYY-MM-DD>.<ext>`，中文名用 RFC 5987 `filename*` | PAP-107 门户需求 |

## 4. A：引用数据

### 4.1 解析（`CanonicalPublicationParsingStrategy`）

| 元素 | 规则 | 去向 |
|---|---|---|
| `Article/Pagination` | 读 `StartPage`、`EndPage`、`MedlinePgn`（三个都可能出现，也可能只有 `MedlinePgn`） | `CanonicalPublication.pagination`（已有 `startPage` / `endPage` / `medlinePgn` 字段，补赋值） |
| `Article/ELocationID[@EIdType='pii']` | 取 `ValidYN != 'N'` 的第一个 | 标识符 `PII`（`cat_publication_identifier.type = 'pii'`，`source = 'PubMed'`）。不新增列；详情响应现有的 `pii` 字段从此有值 |
| `Article/ELocationID[@EIdType='doi']` | 只在 `ArticleIdList` 没有 DOI 时用作 DOI | `cat_publication.doi` |
| `Journal/ISOAbbreviation` | 原文 | `CanonicalPublication.journal.isoAbbreviation`（已有字段，补赋值） |
| `MedlineJournalInfo/MedlineTA` | 原文 | `journal.medlineAbbreviation`（已有字段，补赋值）；本版只作缩写的兜底 |
| `JournalIssue/PubDate` | `Year`、`Month`（数字或英文缩写）、`Day`、`Season`、`MedlineDate` | 见第 4.2 节 |
| `PubmedData/History/PubMedPubDate[@PubStatus='pmc-release']` | 同第 4.2 节的精度规则，不再补 1 | PMC 发布日期 |

### 4.2 出版日期精度

解析结果是「部件 + 精度」，不再构造 `LocalDate`：

| XML | year | month | day | precision | season / date_string |
|---|---|---|---|---|---|
| `Year Month Day` 且合法 | ✓ | ✓ | ✓ | `day` | — |
| `Year Month`；或 `Year Month Day` 但日不合法（2 月 30 日） | ✓ | ✓ | — | `month` | — |
| `Year` | ✓ | — | — | `year` | — |
| `Year Season`（`Spring`） | ✓ | — | — | `year` | `season = Spring` |
| `Year Month` 但月是区间（`Jun-Jul`） | ✓ | — | — | `year` | `date_string = Jun-Jul`（原文） |
| `Year Month` 但月不在 1 ~ 12（`13`、`0`，或认不出的文字） | ✓ | — | — | `year` | `date_string` = 原文月份 |
| 年份不在 1800 ~ 2100 | 记录无效，按第 5.6 节跳过 | | | | |
| `MedlineDate`（`1998 Dec-1999 Jan`） | 第一个四位数 | — | — | `year` | `date_string` = 原文 |
| 没有任何年份 | 记录无效，按第 5.6 节跳过 | | | | |

「日不合法」用 `YearMonth.isValidDay` 判定，降为 `month` 精度，不抛 `DateTimeException`（现在这条会让整个文件失败）。

写入链：`PublicationImportResult` 不再硬编码 `"day"`，按部件写 `year / month / day / date_precision / season / date_string`；`date_value` 只在 `day` 精度时有值，否则为 NULL（不存编造的日期）。领域的 `PublicationDate` 已能按部件推断精度，写入链改为走它。

`cat_venue_instance` 的 `publication_month / day` 改为照传部件（缺就 NULL），不再补 1；表结构不动。`FullTextCandidateReadAdapter` 已经用 `COALESCE(d.date_value, make_date(d.year, COALESCE(d.month,1), COALESCE(d.day,1)))` 读 PMC 发布日期，只用于内部排序挑候选，不对外展示，保持不变。

### 4.3 存储（Flyway `V10`）

```sql
ALTER TABLE cat_publication
    ADD COLUMN publication_month        SMALLINT     NULL,
    ADD COLUMN publication_day          SMALLINT     NULL,
    ADD COLUMN medline_pgn              VARCHAR(200) NULL,
    ADD COLUMN start_page               VARCHAR(50)  NULL,
    ADD COLUMN end_page                 VARCHAR(50)  NULL,
    ADD COLUMN journal_iso_abbreviation VARCHAR(200) NULL,
    ADD CONSTRAINT ck_pub_month CHECK (publication_month BETWEEN 1 AND 12),
    ADD CONSTRAINT ck_pub_day   CHECK (publication_day BETWEEN 1 AND 31),
    ADD CONSTRAINT ck_pub_day_needs_month CHECK (publication_day IS NULL OR publication_month IS NOT NULL);
```

- `publication_month / day` 与已有的 `publication_year` 一样，是主出版日期（`date_type = 'published'`、`is_primary`）的冗余，供卡片、排序、导出直接读，不用 JOIN 日期表。精度由「哪几个为 NULL」表达。
- ISO 缩写放在文献行上，不放 `cat_venue`：它来自每篇文献 XML 的 `Journal/ISOAbbreviation`，期刊目录（LSIOU）没有这个字段；放文献行上不用在文献批处理里跨聚合写期刊。代价是同刊文献重复存同一个字符串，可以接受。
- 页码存三列原文：`MedlinePgn` 是 NLM 缩写形式（`1451-61`），`StartPage` / `EndPage` 只在较新的记录里有。
- 这些列都是导入拥有的列，加进 `PublicationSyncRepositoryAdapter.updatePublicationColumns` 的显式列清单，覆盖时一并更新。
- `PublicationAggregate`、`PublicationEntity`、`PublicationJpaMapper` 同步加字段。领域新增值对象 `Pagination(medlinePgn, startPage, endPage)`（第 4.5 节）。

### 4.4 对外的日期表示

所有接口与导出统一用 **ISO 8601 降精度字符串**：`2019`、`2019-03`、`2019-03-15`。字符串长度本身就是精度，不另加精度字段。

| 位置 | 改动 |
|---|---|
| `PortalPaperResponse`（卡片，PAP-109 批量卡片复用） | 新增 `publishedDate`（可空） |
| `PortalPublicationDetailResponse` | 新增 `publishedDate`；`dates[].date` 从 `LocalDate` 改为降精度字符串（门户类型本来就是 `string`） |
| `PublicationResolutionResp`（PAP-109 内部解析） | 新增 `publishedDate`，供 PAP-113 收藏夹按出版日期排序 |
| 导出与引用 | 第 6、7 节 |

`PublicationDetailDao.findDatesByPublicationId` 改读 `year, month, day, date_precision`，由领域 `PublicationDate` 生成字符串。季节、`MedlineDate` 原文本版不对外输出（年精度已足够；要显示「2000 Spring」时再加）。

### 4.5 页码与文章号

`Pagination.firstRange()` 给 RIS / CSV 用（CSL 直接拿 `MedlinePgn` 原文，由样式的 `page-range-format` 展开）：

1. 有 `StartPage`：首页 = `StartPage`，尾页 = `EndPage`（可空）。
2. 否则取 `MedlinePgn` 第一个以 `;` 或 `,` 分隔的片段，匹配 `前缀字母* 数字+ 后缀字母* - 前缀字母* 数字+ 后缀字母*`：尾页数字比首页短时按首页补齐高位。
3. 都不匹配：首页 = 整个片段，尾页为空。

| 输入（MedlinePgn） | 首页 | 尾页 |
|---|---|---|
| `1451-61` | `1451` | `1461` |
| `S1-S10` | `S1` | `S10` |
| `1451-61; discussion 1462-3` | `1451` | `1461` |
| `e0123456` | `e0123456` | — |
| `123` | `123` | — |

**文章号**（`ArticleNumber.of(pii, pagination)`，领域）：只在没有页码时，把 PII 当文章号；形如 Elsevier 的 PII（含括号，`S0140-6736(21)01624-4`）不算文章号。

| PII | 有页码 | 文章号 |
|---|---|---|
| `e0123456`（PLoS） | 否 | `e0123456` |
| `12345`（Sci Rep） | 否 | `12345` |
| `S0140-6736(21)01624-4` | 否 | 无（只靠 DOI） |
| `e0123456` | 是 | 无（以页码为准） |

### 4.6 检索排序

`sort=year` 的新规则（`PublicationSearchDao.ORDER_BY`）：

```
publication_year DESC NULLS LAST,
publication_month DESC NULLS LAST,
publication_day DESC NULLS LAST,
last_synced_at DESC NULLS LAST,
id DESC
```

同一年里，只到年的文献排在有月份的之后；同一月里，只到月的排在有日的之后。理由：只到年的文献实际日期未知，排在已知日期的后面，不会让一篇「2021」看起来比「2021-12-30」更新。年份 facet 与年份区间筛选不变。不加索引：排序跟在筛选之后，现有的 `publication_year` 排序也没有复合索引。

## 5. B：可重放的导入流水线

### 5.1 一条链路

```
文件读取器（下载 + md5 + 逐篇切出原始 XML + 收集 DeleteCitation）
   │  PubmedSourceRecord(pmid, rawXml, position)
本地重算读取器（读 cat_publication_source）
   ↓
PubmedArticleSyncProcessor：解析 rawXml → CanonicalPublication → 守卫 → 委托 PubmedArticleItemProcessor 组装
   ↓  PublicationSyncItem(result, target, rawXml, position)
PublicationSyncItemWriter：新增 / 覆盖文献 + upsert cat_publication_source
   ↓（仅文件作业）
PubmedDeleteCitationTasklet：软删 + 记位置
```

- Spring Batch 作业两个：`pubmedFileImportJob`（文件读取器 + 文章 Step + 删除 Step）、`pubmedReparseJob`（本地重算读取器 + 同一个处理器与写入器）。
- **删除**（不是废弃）：`PubmedBaselineJobConfig`、`PubmedArticleItemReader`、`PubmedUpdateItemReader`、`PubmedUpdateJobConfig`（合并进新配置）、`PublicationBaselineImportHandler` 及其命令 / 结果、XXL 任务 `pubmedBaselineImportJob`、`PubmedUpdateRoutingProcessor`（逻辑并入新处理器）、`PublicationBatchPort.launchBaselineImport`。
- 保留并复用：`PubmedArticleItemProcessor`（`CanonicalPublication` → `PublicationImportResult` 的组装）、`PublicationItemWriter`（新增文献的写入，含 chunk 内去重）、`PublicationSyncRepository` 的覆盖写、DOI 冲突处理。

### 5.2 守卫：按来源文件位置

**这是对 2026-10-24 讨论的一处修改**：讨论时说的是「用修订日期 `<DateRevised>` 挡住旧版本」，设计时改为用**来源文件位置**。理由：

1. 这就是 NLM 的规则本身——同一个 PMID，后出现的文件取代先出现的；
2. `<DateRevised>` 在 DTD 里是可选的，解析器目前也不读；NLM 每年的 MeSH 维护会改记录却不一定改修订日期；
3. 位置能同样覆盖软删（`DeleteCitation` 没有修订日期）。

位置 `SourcePosition`（领域值对象）：从文件名 `pubmed{yy}n{seq}` 解析，`rank = yy × 10000 + seq`（`pubmed26n1333` → `261333`）。跨年新 baseline（`pubmed27n0001` → `270001`）自然大于上一年所有文件。

| 情况 | 结果 |
|---|---|
| 库里没有该 PMID | `ALL` 范围新增；`EXISTING_ONLY` 跳过（计 `skipped_absent`） |
| 进来的 rank < 库里的 rank | 跳过，计新计数 `skipped_stale` |
| 进来的 rank = 库里的 rank | 覆盖（同一份数据按新解析器重算） |
| 进来的 rank > 库里的 rank | 覆盖 |
| 库里还没有位置（现有 18,807 行） | 视为最旧，覆盖 |
| 已软删、进来的 rank > 库里 | 覆盖并复活（现有行为） |
| 已软删、进来的 rank ≤ 库里 | 跳过（软删时记过位置，第 5.3 节） |
| 本地重算 | 要求 rank 相等且未软删；软删的文献不进入重算读取器 |
| `DeleteCitation`：库里有该 PMID，删除文件的 rank ≥ 来源表里的 rank（或来源表没有） | 软删（已软删则保持），来源行的 `source_file / source_rank` 改成删除文件 |
| `DeleteCitation`：删除文件的 rank < 来源表里的 rank | 不动（后面的文件已经复活或改写过它） |
| `DeleteCitation`：库里没有该 PMID，范围 `ALL` | 写一条「墓碑」来源行（`raw_xml` 为 NULL、没有对应文献行），之后重放更早的文件时按 rank 跳过，不会把它新增回来 |
| `DeleteCitation`：库里没有该 PMID，范围 `EXISTING_ONLY` | 不动（这个范围不会新增它） |
| 墓碑比进来的记录旧 | 视为库里没有（见下） |

由此，任何文件以任何顺序重放都收敛到同一结果；runbook 不必按顺序跑。守卫查询用 `cat_publication_source s FULL OUTER JOIN cat_publication p ON s.pmid = p.pmid` 按 PMID 查出 `source_rank`、文献 ID、软删状态、当前 DOI：两边任一有就返回。只有文献没有来源行（现有 18,807 行）→ `sourceRank = null`，按「视为最旧」覆盖；只有来源行没有文献（墓碑）→ `publicationId = null`，按 rank 挡住更早的新增。不能只从来源表 LEFT JOIN，否则旧数据全被当成「库里没有」，`EXISTING_ONLY` 回填会一篇不改。

**墓碑比进来的记录旧**（墓碑 rank < 进来的 rank）：处理器把它当作「库里没有」（`target = null`）——`ALL` 新增、`EXISTING_ONLY` 跳过；写入器的语义保持「`target` 为空即新增」。写入器插入后回查「真正插入了哪些」时，只算 `publicationId` 非空的目标（墓碑也会被查出来）。

**`last_synced_at` 的规则**：它是首页「最新」排序与 `minutesAgo` 的依据，表示「镜像里这篇最近一次拿到新版本的时间」。只在新增、或进来的 rank 大于库里**非空**的 rank 时刷新；同 rank 覆盖（重处理、本地重算）和库里 rank 为空的覆盖（这次回填）都保留原值。否则回填会让约 6,800 篇只来自 baseline 的旧文献冲到「最新」的顶部，每次重算还会再打乱一遍。

### 5.3 来源表 `cat_publication_source`（V11）

```sql
CREATE TABLE cat_publication_source (
    pmid            VARCHAR(15)    NOT NULL,
    source_file     VARCHAR(40)    NOT NULL,   -- 最后一次写入或软删它的文件名
    source_rank     INTEGER        NOT NULL,
    raw_xml         TEXT           NULL,       -- 该文件里这篇的 <PubmedArticle>；只被软删、从没写过原文时为 NULL
    parsed_at       TIMESTAMPTZ(6) NULL,       -- 最近一次按 raw_xml 解析写入的时间
    -- 审计列按仓库表惯例（version、created_at、updated_at 等）
    PRIMARY KEY (pmid)
);
ALTER TABLE cat_publication_source ALTER COLUMN raw_xml SET COMPRESSION lz4;
```

- 按 PMID 而不是内部 ID 做主键：原始数据与身份锚一致，换库重导内部 ID 时不受影响。
- 写入：`PublicationSyncItemWriter` 在同一个 chunk 事务里 upsert（`ON CONFLICT (pmid) DO UPDATE`），覆盖的全部写；新增的只为**实际插入成功**的 PMID 写（插入写入器会静默丢掉 DOI 已存在或 chunk 内重复的项，沿用现有「插入后按 PMID 回查」的做法确定哪些真的进了库），不留没有文献的原文。
- 软删：`PubmedDeleteCitationTasklet` 按第 5.2 节的删除规则执行，条件写在 SQL 里（`WHERE source_rank IS NULL OR source_rank <= :rank`）；`raw_xml` 保持原值（没有就 NULL）。墓碑行只有来源行、没有文献行。
- 守卫读取：`findSyncTargetsByPmids` 改为 `FULL OUTER JOIN`（第 5.2 节），`PublicationSyncTarget` 加 `sourceRank`，`publicationId` 改为可空。
- `raw_xml` 是「从文件里逐篇切出、重新序列化的子树」，与原文件语义等价，不保证逐字节相同（StAX 事件复制会规范化空白与转义）。
- lz4 由 PG17 官方镜像支持，infra 集成测试在 Testcontainers 里实际建表验证。
- 容量：按每篇 5 ~ 15 KB 原文、lz4 约 3 倍压缩估，18,807 篇约 30 ~ 100 MB。**前提**：镜像范围维持在现在的量级；如果以后镜像整个 PubMed（约 3,700 万篇），原文会到上百 GB，要换存储位置（对象存储），本版不做。

### 5.4 逐篇切出原始 XML

文件读取器用 StAX 的 `XMLEventReader` 读 gzip 流：

- 遇到 `<PubmedArticle>` 开始，用 `XMLEventWriter` 把整个子树（按深度配对到结束标签）写进 `StringWriter`，得到这篇的 `rawXml`；
- 遇到 `<DeleteCitation>`，收集其中的 PMID（与现有读取器一样放进 Step 上下文，删除 Step 读取）；
- 断点续传沿用「按已读条数跳过」。

解析器新增 `PubmedXmlParserPort.parseArticle(String rawXml)`：在一个独立的 `XMLStreamReader` 上调用现有的 `CanonicalPublicationParsingStrategy.parseRecord`。文件读取器与本地重算读取器产出同一种 `PubmedSourceRecord`，之后的处理完全一样。现有的 `parse` / `parseUpdateFile` 删除。

### 5.5 本地重算任务 `pubmedReparseJob`

- 读取器：按 `pmid` 分页读 `cat_publication_source JOIN cat_publication`，条件 `raw_xml IS NOT NULL AND p.deleted_at IS NULL`；`position` 取表里的 `source_file / source_rank`。
- 处理器与写入器同文件作业；守卫在这里恒为「rank 相等 → 覆盖」。
- 每次全量重算，不记解析器版本：18,807 篇在本机量级几分钟内完成。
- 用途：解析器升级后（例如以后加收稿日期），不用再从 NCBI 下载。

### 5.6 坏记录只跳过这一篇

| 情况 | 处理 |
|---|---|
| 单篇 `rawXml` 解析异常 | 跳过，计 `skipped_invalid`，`WARN` 日志带 PMID 与文件名 |
| 缺 PMID / 缺年份 / 缺标题 | 处理器返回 `null`，计 `skipped_invalid`，`WARN` 日志 |
| 非法日期 | 不再是错误（第 4.2 节降精度） |
| 文件级：下载失败、md5 不符、gzip 损坏、XML 不完整 | 整个文件失败（按 md5 重试后仍失败才算失败） |

逐篇切出之后，单篇的解析异常被隔离在 `parseArticle` 里。处理器把「解析 + 组装」包在一起，捕获 `XmlParseException`、`IllegalArgumentException`、`IllegalStateException`、`DateTimeException`（领域断言与值对象校验抛的都是这几类，例如 `PublicationDate` 的年份范围、`VenueInstanceAggregate` 的月份范围），计 `skipped_invalid` 后返回 `null`。数据库异常等基础设施错误不捕获，照常让文件失败。年份为空、月份非法在解析阶段就已处理（第 4.2 节），不会走到这些断言。

### 5.7 下载与 md5

新增 infra 组件 `PubmedFileFetcher`（包装现有 `FileDownloadPort`）：

1. 下载 `<文件>.xml.gz` 到临时文件；
2. 下载 `<文件>.xml.gz.md5`，解析 `MD5(<文件名>)= <32 位十六进制>`；
3. 计算临时文件的 MD5 比对；不符或任一步失败，删临时文件后重试，最多 3 次；
4. 仍失败抛出，文件标记失败。

这也是 `pubmed26n1379` 那类截断下载的根治：截断的文件不会进入解析。

### 5.8 统一入口

| 层 | 新 |
|---|---|
| XXL 任务 | `pubmedImportJob`：参数 `kind=BASELINE\|UPDATE`（默认 `UPDATE`）、`from=N`、`to=M`、`maxFiles=K`、`scope=EXISTING_ONLY\|ALL`（默认取配置）、`reprocess=true\|false`（默认 `false`）。无参数 = 每日增量（UPDATE、从进度续跑、到最新）。`pubmedReparseJob`：无参数 |
| app | `PubmedFileImportHandler`（由 `PubmedUpdateSyncHandler` 改造）+ `PubmedFileImportCommand`；`PubmedReparseHandler` |
| 规则 | `kind=BASELINE` 必须给 `from`（`to` 缺省 = `from`）；`kind=UPDATE` 不给 `from` 时从进度续跑、`to` 缺省 = 最新。`reprocess=false` 跳过进度表里已 COMPLETED 的文件；`reprocess=true` 不跳过，并为每个文件换新的作业 generation（沿用现有 `replay-<millis>` 机制）。`fromFile` 参数取消（它隐含的「重放」改由 `reprocess` 显式表达）。UPDATE 的续跑起点只看 `kind = UPDATE` 的进度行（baseline 与 update 共用编号，不过滤的话一个失败的 baseline 文件会让每日增量去 `updatefiles/` 下载不存在的文件、永远卡住） |
| 互斥 | `pubmedImportJob` 与 `pubmedReparseJob` 共用一把任务锁（现有 `ExclusiveRunPort.PUBMED_UPDATE_SYNC` 改名 `PUBMED_IMPORT`），拿不到锁时跳过并在结果里说明 |
| 进度表 | `cat_pubmed_update_file` 改名 `cat_pubmed_file`（V11 `ALTER TABLE … RENAME`，约束与索引同步改名），加 `kind VARCHAR(10) NOT NULL`（现有行 `UPDATE`）与 `skipped_stale_count INTEGER NOT NULL DEFAULT 0`；领域 `PubmedUpdateFile*` 一系改名 `PubmedFile*` |
| 配置 | `patra.catalog.pubmed.update-scope` 改名 `scope`（含义不再只是「update 的范围」），默认值改为 `EXISTING_ONLY`（更安全的默认；mini 的 Nacos 同步改键名）；`baseline-url`、`update-url`、`file-prefix` 不变 |

## 6. C1：导出

### 6.1 接口

```
POST /portal/publications/export
Content-Type: application/json

{ "ids": ["38123456789012345", "..."], "format": "ris", "name": "组会" }
```

| 项 | 规则 |
|---|---|
| `ids` | 1 ~ 5,000 个正整数（JSON 里字符串或数字都接受）；缺失、空、超过 5,000 → 422 |
| `format` | `ris` / `bibtex` / `csv` / `csl-json`；其他 → 422 |
| `name` | 可选，≤ 100 字；用于文件名 |
| 顺序与去重 | 按请求顺序，重复 ID 只输出一次 |
| 不存在与软删 | 跳过（interaction 已经把「已失效」的条目滤掉并计数，决策 I / PAP-113） |
| 响应 | 200，`Content-Type` 见下表，`Content-Disposition: attachment`，正文流式写出 |

| 格式 | Content-Type | 扩展名 |
|---|---|---|
| RIS | `application/x-research-info-systems; charset=UTF-8` | `.ris` |
| BibTeX | `application/x-bibtex; charset=UTF-8` | `.bib` |
| CSV | `text/csv; charset=UTF-8` | `.csv` |
| CSL-JSON | `application/vnd.citationstyles.csl+json` | `.json` |

**文件名**：`patra-<name>-<YYYY-MM-DD>.<ext>`，日期取注入的 `Clock` 在 `Asia/Shanghai` 的当天；`name` 去掉 `\ / : * ? " < > |` 与控制字符、空白折叠成 `-`，为空时用 `export`。用 Spring `ContentDisposition.attachment().filename(name, UTF_8)` 生成（含 RFC 5987 `filename*`）。

### 6.2 流式与内存

- 控制器返回 `StreamingResponseBody`；导出服务按 500 个 ID 一批：读一批 → 写一批 → 丢弃。任一时刻内存里最多 500 条。
- 每批两条 SQL：主行（文献 + 期刊 + 期次 + PMCID / PII 子查询）、作者（`publication_id = ANY(...)`，按 `author_order`）。没有 N+1。
- 每批一个只读事务（查询服务方法级 `@Transactional(readOnly = true)`）：响应体在控制器返回之后才执行，不能依赖请求级的会话。
- 门户经网关调用：网关对下游有读超时（PAP-106 一系）。5,000 条约 10 批、两条 SQL 一批，预计数秒；release spec D6 的手工导出必须经网关走一次，确认不触发超时。网关下游读超时为 60 秒空闲超时（`patra-gateway-boot` 的 `spring.http.clients.imperative.read-timeout`，连续 60 秒收不到字节才断、不限总时长）；导出每批写完即刷出，批间隔远小于 60 秒。
- 端口只有一个方法 `CitationExportPort.export(format, Iterator<List<CitationRecord>> batches, out)`：导出服务交出懒加载的批次迭代器（每次 `next()` 在一个只读事务里读一批），infra 适配器逐批拉取；写出器有状态（CSV 表头、JSON 数组括号与逗号），是 infra 内部类型（`begin` / `write` / `flush` / `end`），不越过端口边界。每批写完即刷出。
- 异步请求总时长：catalog 配 `spring.mvc.async.request-timeout: 10m`（不配时 Tomcat 默认 30 秒、按总时长计，慢速客户端会被中途掐断）。
- 已知限制：响应头发出后若中途失败（数据库报错、超时），`patra-tool-web` 的全局异常处理器在响应已提交时放弃渲染、请求正常结束，客户端拿到的是被截断的 200 文件而不是下载失败。要让连接中断需要改 `patra-tool-web`，不在本 Issue 范围。

### 6.3 引用记录 `CitationRecord`（领域读模型）

`id, pmid, pmcid, doi, title（纯文本）, authors（List<CitationAuthor>）, journalTitle, journalAbbreviation, volume, issue, pagination（Pagination）, articleNumber, publishedDate（PublicationDate）, language`

- `title`：`InlineMarkup.toPlainText(title)`（`CO<sub>2</sub>` → `CO2`）。
- `CitationAuthor(family, given, initials, collective)`：取 `cat_publication_author` 的 `last_name / fore_name / initials / collective_name`，按 `author_order`。
- `journalTitle`：`cat_venue.title`；`journalAbbreviation`：`journal_iso_abbreviation`，为空用 `cat_venue.abbreviated_title`（MedlineTA），再为空用 `journalTitle`。
- `volume / issue`：`cat_venue_instance`。

### 6.4 各格式字段

**RIS**（行尾 CRLF，每条以 `TY  - JOUR` 开始、`ER  - ` 结束）：

| 标签 | 值 |
|---|---|
| `AU` | 每位作者一行：`Family, Given`（无名用缩写）；团体作者原文 |
| `TI` / `T2` / `J2` | 标题 / 期刊全称 / 期刊缩写 |
| `VL` / `IS` | 卷 / 期 |
| `SP` / `EP` | `Pagination.firstRange()`；没有页码有文章号时 `SP` = 文章号 |
| `C7` | 文章号 |
| `PY` | 年 |
| `DA` | `YYYY/MM/DD/`，缺的部分留空：`2021/10/14/`、`2022/10//`、`2023///` |
| `DO` | DOI |
| `AN` | PMID（EndNote / Mendeley 惯例） |
| `M2` | `PMID: <pmid>`（Zotero 把 `M2` 导入到 Extra，这是 Zotero 识别 PMID 的原生写法） |
| `C2` | PMCID |
| `UR` | `https://pubmed.ncbi.nlm.nih.gov/<pmid>/` |

Zotero 的 RIS 导入里没有任何标签直接映射到 PMID（2026-10-24 读 `zotero/translators` 的 `RIS.js`）：`AN` 进「档案位置」，`M2` 进 Extra。`M2` 这个选择由 AC 的手工导入验证，不在这里断言；手工验证不通过时改用它给出的结果，并回写本节。

**BibTeX**（UTF-8，不转 TeX 重音）：条目 `@article{pmid<PMID>, …}`（没有 PMID 用 `patra<id>`）；`author = {Family, Given and …}`（团体作者整体加一层花括号）；`title = {{…}}`（双花括号保大小写）；`journal`、`year`、`month`（只在精度到月时，用 `jan`…`dec` 宏）、`volume`、`number`、`pages = {1451--1461}`、`eid`（文章号）、`doi`、`pmid`、`pmcid`。值里的 `\ { } % & $ # _ ~ ^` 转义。

**CSV**（RFC 4180，UTF-8 带 BOM 以便 Excel 打开中文，CRLF）：列 `id, pmid, pmcid, doi, title, authors, journal, journal_abbreviation, published_date, year, volume, issue, pages, article_number`。`authors` 用 `; ` 连接「Family Given」；`published_date` 是降精度 ISO 字符串；`pages` 为 `首页-尾页`。

**CSL-JSON**：一个 JSON 数组，每项由 `CslItemMapper`（第 7.2 节，与引用共用）生成 `CSLItemData` 再用 citeproc-java 的 `toJson` 输出；`id` = 文献 ID 字符串；`issued.date-parts` 按精度给 `[[2023]]` / `[[2022, 10]]` / `[[2021, 10, 14]]`。

## 7. C2：复制引用

### 7.1 接口

```
POST /portal/publications/citations
Content-Type: application/json

{ "ids": ["…", "…"], "style": "gb-t-7714-2015" }
```

```json
{
  "style": "gb-t-7714-2015",
  "entries": [
    { "label": "[1]", "text": "ANKER S D, BUTLER J, FILIPPATOS G, et al. Empagliflozin in Heart Failure with a Preserved Ejection Fraction[J/OL]. The New England journal of medicine, 2021, 385(16): 1451-1461. DOI:10.1056/NEJMoa2107038." }
  ]
}
```

| 项 | 规则 |
|---|---|
| `ids` | 1 ~ 100；按请求顺序去重；不存在与软删跳过 |
| `style` | `gb-t-7714-2015` / `vancouver` / `apa-7` / `ama-11`；其他 → 422 |
| `entries` 顺序 | 编号样式（GB/T、Vancouver、AMA）= 请求顺序，编号从 1 起；**APA = 样式自己的顺序（按作者字母）**，这是 APA 参考文献表的规则 |
| `label` | 编号样式的序号原样（`[1]`、`1.`）；APA 为 `null` |
| `text` | 不含序号的纯文本条目；门户自己拼 `label + " " + text` |

把序号与正文分开返回，原因：citeproc-java 的纯文本输出在「序号对齐」样式里把序号和正文直接相连（`[1]ANKER…`），而 GB/T 7714 与 NLM 的写法中间有空格；单篇复制时门户也可以不要序号。

### 7.2 生成

- 依赖：`de.undercouch:citeproc-java:3.5.2`（纯 Java，Apache-2.0）、`org.citationstyles:styles:26.8`、`org.citationstyles:locales:26.8`，版本只进 `gradle/libs.versions.toml`（`PatraDependencyManagement.kt` 只登记 BOM 与安全覆盖，普通库不在其中），只加在 `patra-catalog-infra`。它们会带进 `jbibtex`、`antlr4-runtime`、`commons-lang3`、`commons-text`，不要再单独声明。样式库是 CC BY-SA 3.0：catalog README 写明出处与许可（公开仓库）。
- `CslItemMapper`（infra）：`CitationRecord` → `CSLItemData`：`type = article-journal`；`author`（`family` / `given`，团体作者用 `literal`）；`title`；`container-title` = 期刊全称；`container-title-short` = 期刊缩写；`volume`、`issue`；`page` = `MedlinePgn` 原文（没有则 `首页-尾页`，再没有则文章号）；`number` = 文章号；`issued` 按精度；`DOI`、`PMID`、`PMCID`。
- `CiteprocCitationFormatterAdapter implements CitationFormatterPort`：每个请求新建一个 `CSL` 实例（它有状态、不是线程安全的，不共享），`ListItemDataProvider` 按请求顺序注册全部条目，渲染两遍：`html` 取 `csl-left-margin` 作为 `label`，`text` 去掉开头的 `label` 后作为 `text`。
- citeproc-java 3.5.2 的 `Bibliography.getEntryIds()` 返回 `null`（spike 实测），拿不到条目与 ID 的对应，所以 APA 只能按样式顺序返回，不做 ID 映射。

| `style` | CSL 样式文件 | locale |
|---|---|---|
| `gb-t-7714-2015` | `china-national-standard-gb-t-7714-2015-numeric` | `en-US` |
| `vancouver` | `nlm-citation-sequence`（官方 `vancouver-nlm` 的父样式） | `en-US` |
| `apa-7` | `apa` | `en-US` |
| `ama-11` | `american-medical-association`（AMA Manual of Style 11th） | `en-US` |

### 7.3 GB/T 7714 的两个决定

1. **用 `en-US` locale 渲染**。官方样式默认 `zh-CN`，作者超过 3 个时一律写「等」；GB/T 7714-2015 §8.1.2 规定西文文献用相应的词（et al.）。catalog 的文献都是 PubMed 记录，著录语言是英文，所以用 `en-US`，输出「et al.」。按文献语言切换要用 CSL-M 扩展，citeproc-java 不支持。被否：默认 `zh-CN`（英文文献印「等」，不合标准）。
2. **用 2015 版**（用户 2026-10-24 定）。GB/T 7714-2025 已于 2026-07-01 实施并替代 2015 版，但官方 CSL 的 2025 样式（2026-05 发布、标为「中文」）对英文文献也输出全角标点（`Anker S D，Butler J，… 2021，385（16）：1451-1461`），这是样式本身的行为。界面写明「GB/T 7714-2015」。

### 7.4 夹具

三篇样例（spike 用过的）：

- 全字段：EMPEROR-Preserved，NEJM 2021-10-14，385(16):1451-61，8 位作者含团体作者，DOI，PMID。
- 只到年 + 文章号：Sci Rep 2023，卷 13，文章号 12345，DOI。
- 只到年月：Lancet 2022-10，400(10360):1200-10。

四种样式的期望文本取 2026-10-24 spike 的输出，人工对照各样式指南核对后写进测试（例如 GB/T 第一条 `ANKER S D, BUTLER J, FILIPPATOS G, et al. …[J/OL]. The New England journal of medicine, 2021, 385(16): 1451-1461. DOI:10.1056/NEJMoa2107038.`）。所以「与夹具一致」证明的是「不回退」，不是由权威机构证明格式正确；正确性由这次人工核对和 D6 的手工检查保证。只到年、只到年月两篇守住「不出现编造的月日」。

## 8. 模块与改动面

| 层 | 新增 / 修改 |
|---|---|
| `patra-common` | 新增 `PartialDate(year, month, day, season, text)`（record，按部件推断精度）；`CanonicalPublication.PublicationDates` 的各字段从 `LocalDate` 改为 `PartialDate`（catalog 只写 `published`、`pmcRelease`；ingest 的 `PubmedPublicationConverter` 也构造 `PublicationDates`，改为 `PartialDate.ofDay(localDate)` 包一层，语义不变，其测试同步改；ingest 会把 `CanonicalPublication` 序列化进对象存储，JSON 形状随之变化——ingest 从未在 mini 上运行，没有存量数据）；`pagination`、`journal.isoAbbreviation / medlineAbbreviation` 补赋值，类型不变 |
| domain | 值对象 `Pagination`、`SourcePosition`、`ArticleNumber`；`PublicationDate` 加 `toIsoString()`；聚合加字段；读模型 `CitationRecord`、`CitationAuthor`、`CitationEntry`；端口 `CitationRecordReadPort`、`CitationFormatterPort`、`CitationExportPort`（写出器是 infra 内部类型）；`PubmedXmlParserPort.parseArticle`；`PublicationSyncRepository` 的来源表方法；进度仓储改名；枚举 `ExportFormat`、`CitationStyle` |
| app | `PubmedFileImportHandler` / `Command`、`PubmedReparseHandler`；`PortalPublicationExportService`、`PortalCitationQueryService` |
| infra | 解析策略改造；`PubmedFileFetcher`；`PubmedFileItemReader`、`PubmedSourceRecordReader`；`PubmedArticleSyncProcessor`；写入器与删除 Tasklet 改造；作业配置合并；`CitationRecordReadAdapter` + Dao；`CslItemMapper`、`CiteprocCitationFormatterAdapter`；`CitationExportAdapter` 与四个写出器；`V10`（引用列）、`V11`（来源表、进度表改名）；读端日期 / 卡片 / 排序 SQL |
| adapter | XXL `pubmedImportJob`、`pubmedReparseJob`（删除 `pubmedBaselineImportJob`、`pubmedUpdateSyncJob`）；新控制器 `PortalCitationController`（同前缀 `/portal/publications`）提供 `export`、`citations`；响应加 `publishedDate`；`PublicationEndpointImpl` 带 `publishedDate` |
| infra 配置 | `patra-infra/nacos/dev/patra-catalog.yaml`：`update-scope` 改键名 `scope` |
| 文档 | catalog README（导入流水线、来源表、导出与引用、样式库许可） |

## 9. D：回填 runbook（mini）

每一步执行前等用户明确同意。

| # | 步骤 | 验证 |
|---|---|---|
| 0 | 记录磁盘剩余与 `docker stats`（release spec D12）；在 XXL-Admin **停用**每日增量任务（`pubmedUpdateSyncJob`），避免部署后旧 handler 不存在而报错、或与手动执行抢锁 | 剩余 ≥ 20 GB；任务状态为停止 |
| 1 | 合并后由 CD 部署 catalog；Flyway 执行 `V10`、`V11` | 启动日志 `Successfully applied 2 migrations`；`\d cat_publication_source` 存在 |
| 2 | Nacos 推送 `patra-catalog.yaml`（`scope: EXISTING_ONLY` 新键名）。第 1、2 步之间旧键名不再生效，但代码默认值已改为 `EXISTING_ONLY`，且每日任务已停用，所以这段时间不会按 `ALL` 跑 | `nacos-verify.sh` 通过 |
| 3 | XXL-Admin：登记 `pubmedImportJob`、`pubmedReparseJob`；把每日增量任务的 handler 改成 `pubmedImportJob`（参数留空，保持停用）；删除 `pubmedBaselineImportJob` 任务 | 执行器列表里能看到新 handler |
| 4 | 手动执行 `pubmedImportJob`：`kind=UPDATE,from=1335,to=1379,scope=EXISTING_ONLY,reprocess=true` | 每个文件 COMPLETED；`1379` 这次通过（md5 守住下载完整） |
| 5 | 手动执行 `pubmedImportJob`：`kind=BASELINE,from=1333,scope=EXISTING_ONLY,reprocess=true` | COMPLETED；被 update 修订过的文献计入 `skipped_stale` |
| 6 | SQL 核对 | `SELECT count(*) FROM cat_publication p LEFT JOIN cat_publication_source s USING (pmid) WHERE s.pmid IS NULL` = 0；软删数与第 4 步前对比（`1379` 第一次成功处理，它的 `DeleteCitation` 可能让软删数增加，增加的 PMID 应在该文件的删除列表里）；`date_precision` 分布里出现 `year` / `month`；`medline_pgn` 非空占比 |
| 7 | 启用每日增量任务 | 次日自动跑完，从 `1380` 续跑 |
| 8 | 再记录一次磁盘与 `docker stats` | ≥ 20 GB |

第 4、5 步顺序可以对调（第 5.2 节守卫保证收敛），先跑 update 让第 5 步少覆盖一些。两步共重新下载 46 个文件（baseline 1 个 + update 45 个），经 md5 校验。

## 10. 测试策略

### 10.1 单元测试

- `Pagination`：第 4.5 节五行；`ArticleNumber`：四行。
- `SourcePosition`：`pubmed26n1333` → `261333`；`pubmed27n0001` > `pubmed26n1999`；非法文件名抛出。
- `PublicationDate.toIsoString()`：三种精度；季节 / `MedlineDate` 为年精度。
- 解析策略（用最小 XML 片段）：第 4.1、4.2 节每一行；2 月 30 日降为 `month`。
- 守卫（`PubmedArticleSyncProcessor`，mock 仓储）：第 5.2 节表格每一行。
- 文件名生成：中文、非法字符、空名、Clock 固定在 `Asia/Shanghai`。
- 四个写出器：三篇样例的完整输出（RIS 的 `DA` 三种精度、BibTeX 无 `month`、CSV 的 BOM 与引号转义、CSL-JSON 的 `date-parts`）。
- `CiteprocCitationFormatterAdapter`：第 7.4 节夹具；APA 顺序为字母序；`label` / `text` 拆分。
- `PubmedFileFetcher`：md5 相符通过；不符重试 3 次后抛出；`.md5` 内容格式不对抛出（WireMock 或本地 HTTP 桩）。

### 10.2 集成测试

- infra（Testcontainers PG17）：
  - `V10`、`V11` 在干净库上建成，`raw_xml` 的压缩方式为 `lz4`（查 `pg_attribute.attcompression`）。
  - 来源表与守卫：「文件 26n1350 软删 PMID X → 重放 26n1333 → X 仍软删；重放 26n1360 含 X → 复活」；「库里 rank 为 26n1340 → 重放 26n1333 跳过、计 `skipped_stale`」；「无来源行的旧数据 → 覆盖」。
  - 删除守卫：「1360 复活 X 后重放 1340 的 `DeleteCitation` → X 不被软删、rank 不降」；「`ALL` 范围下删除一个库里没有的 PMID → 写墓碑 → 重放更早的 baseline 不新增它」。
  - `last_synced_at`：同 rank 覆盖与 rank 为空的覆盖保留原值；更大的 rank 刷新。
  - 坏记录：一篇月份为 `13`、一篇年份 `1700`、一篇缺标题的文件 → 文件 COMPLETED，`skipped_invalid = 2`（月份 13 降为年精度、不计跳过），其余文献正常写入。
  - 续跑：进度表里有失败的 BASELINE 行时，UPDATE 续跑起点不受影响。
  - 本地重算：改写 `raw_xml` 中的页码后跑 `pubmedReparseJob`，文献行更新、`last_synced_at` 不变；软删文献与墓碑不被重算。
  - `CitationRecordReadAdapter`：两条 SQL、500 个 ID 只发 2 条语句（Hibernate `Statistics`，与 PAP-109 同法）、软删不返回、缩写兜底链。
  - 检索排序：同年 `2021`、`2021-03`、`2021-03-05`、`2021-12` 的顺序符合第 4.6 节。
- adapter（`@WebMvcTest`）：
  - 导出：用 **MockMvc 的异步分派**（`asyncDispatch`）读 `StreamingResponseBody`——`RestTestClient` 读不到流式响应体（记忆里的计划缺陷）；断言 `Content-Type`、`Content-Disposition`（含 `filename*`）、正文；5,000 个 ID 时导出服务对读端口调用 10 次、每次 ≤ 500 个（mock 读端口计数），证明分批而非一次加载。
  - 引用：200 结构；101 个 ID、未知样式 → 422。
  - 卡片 / 详情 / 解析接口：`publishedDate` 三种精度；详情 `dates[].date` 为降精度字符串。
- e2e：`PubmedUpdateSyncE2E` 改写为 `PubmedFileImportE2E`，沿用夹具 `pubmed26n9000-baseline.xml` / `pubmed26n9001.xml`（文件名可按新命名调整）：BASELINE `ALL` 导入 → UPDATE 覆盖 → 软删 → 重放 baseline 不回退 → 本地重算。

### 10.3 门控

`./gradlew spotlessApply` → `./gradlew check` → `:patra-api:patra-catalog:patra-catalog-infra:integrationTest`、`:patra-api:patra-catalog:patra-catalog-adapter:integrationTest`、`:patra-api:patra-catalog:patra-catalog-boot:e2eTest`。

## 11. 交给其他 Issue 的约束

**PAP-109（本会话已设计，尚未实现）**：`PublicationResolutionResp` 加 `publishedDate`（降精度 ISO 字符串，可空）。PAP-109 先合入时没有这个字段，PAP-110 合入后加上，属于新增字段。

**PAP-113（interaction）**：

- 登记表快照里存 `publishedDate`，收藏夹「按出版日期」排序按第 4.6 节同样的规则（年、月、日依次降序，缺的排后）。
- 导出编排只负责把范围展开成文献 ID（≤ 5,000）和已失效计数；文件由门户调 catalog 的 `/portal/publications/export` 生成。

**PAP-114 / PAP-115（门户）**：

- 卡片 `publishedDate` 只显示年；详情与文献库按长度判断精度显示「2019 / 2019 年 3 月 / 2019 年 3 月 15 日」；「文献档案」直接显示字符串。
- 导出：`POST /patra-catalog/portal/publications/export`，`name` 传范围名（收藏夹名 / `library` / `selection`），文件名由 catalog 生成。
- 复制引用：`label` 非空时拼 `label + " " + text`；单篇复制可以只用 `text`。APA 的条目顺序是字母序，门户不要重排。界面写「GB/T 7714-2015」。

## 12. AC 对照

| AC | 怎么证明 |
|---|---|
| 只有出版年份的文献，接口与导出里都不出现编造的月日 | 解析单测（年精度不补月日）；卡片 / 详情 / 解析接口切片测试（`publishedDate = "2023"`）；四个写出器与引用夹具里的年精度样例；runbook 第 6 步的精度分布 |
| RIS 导入 Zotero 后作者（全名、顺序）、标题、期刊、卷、期、页码或文章号、年份、DOI、PMID 齐全 | RIS 写出器夹具（逐行断言）；release spec D6 在 mini 上手工导入 Zotero，结果回写第 6.4 节 |
| 四种引用格式与测试夹具里的标准输出一致 | `CiteprocCitationFormatterAdapter` 夹具测试（第 7.4 节） |
| 5,000 条导出以流式完成，不一次性加载进内存 | 导出切片测试：读端口被调 10 次、每次 ≤ 500；MockMvc 异步读出完整正文 |

## 13. 设计中由我定的点

1. **守卫改用来源文件位置，而不是讨论时说的修订日期**（第 5.2 节，理由三条）。请重点评审。
2. 原始 XML 按 PMID 存在 `cat_publication_source`，lz4 压缩；语义等价、不逐字节相同。
3. 本地重算每次全量，不记解析器版本。
4. XXL 合并成 `pubmedImportJob` 一个入口（含每日增量），`fromFile` 改成 `reprocess`；进度表改名 `cat_pubmed_file`；配置键 `update-scope` 改名 `scope`、默认值改为 `EXISTING_ONLY`。
5. md5 校验最多重试 3 次。
6. ISO 缩写放文献行（`journal_iso_abbreviation`），不放期刊表；缩写兜底链 ISO → MedlineTA → 全称。
7. ELocationID 的 PII 进标识符表；文章号只在没有页码、且 PII 不含括号时成立。
8. 对外日期统一用降精度 ISO 字符串，不加精度字段。
9. 检索按出版日期排序时，同年里只到年的排在有月份的之后（NULLS LAST）。
10. 导出每批 500 条、两条 SQL、每批一个只读事务；不存在与软删的 ID 跳过。
11. RIS 同时写 `AN`、`M2`、`C2`、`UR`；`M2` 由手工导入 Zotero 验证。
12. BibTeX 输出 UTF-8，不转 TeX 重音；CSV 带 BOM。
13. 引用返回 `label` 与 `text` 分开；APA 按样式顺序；GB/T 用 `en-US` locale。
14. 每个引用请求新建一个 `CSL` 实例，不缓存。
15. 共享内核 `PublicationDates` 改用 `PartialDate`，ingest 的转换器跟着包一层（第 8 节）。
16. 删除也走位置守卫；`ALL` 范围下为库里没有的被删 PMID 写墓碑来源行。
17. `last_synced_at` 只在拿到真正更新的版本时刷新，重处理与回填不刷新。
18. 单篇的领域断言类异常只跳过这一篇；数据库异常仍让文件失败。
19. 导入与本地重算共用一把任务锁；UPDATE 续跑只看 UPDATE 的进度行。

用户已定：三层改造与原始 XML 保留都进本 Issue；GB/T 用 2015 版（2026-10-24）。
