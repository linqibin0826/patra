# 全文阅读后端 工程 spec（PAP-84 ~ PAP-87）

> **Issue**：[PAP-84](https://linear.app/papertrace/issue/PAP-84) PubMed 增量同步（原"Europe PMC 补 PMCID"，本文改写，见第 2 节）· [PAP-85](https://linear.app/papertrace/issue/PAP-85) PMC 全文抓取 · [PAP-86](https://linear.app/papertrace/issue/PAP-86) JATS 解析入库 · [PAP-87](https://linear.app/papertrace/issue/PAP-87) 门户全文读端点
> **版本**：[v0.11 Full Text Reader](../release-specs/v0.11-fulltext-reader.md)
> **日期**：2026-10-19
> **状态**：已批准（2026-10-19）
> **基线**：`feat/v0.11-fulltext-api`（从 main `afb9cf210` 切出，v0.10 工具层重组之后）。下文"现状"都指这个基线。

## 1. 这份文档做什么

release spec 定了版本目标、范围和决策。本文把四个后端 Issue 落到能直接写 plan 的程度：表结构、状态流转、任务编排、外部数据怎么取、正文结构长什么样、接口的形状、测试怎么做。plan 按第 4 ~ 7 节的顺序切任务。

写本文前在规划会话里做了两轮调研（catalog 架构、对象存储、网关与门户、历史文档、PMC / Europe PMC 外部数据；baseline 导入内部、字段归属、基建约定），结论在下文对应位置，不另附原件。

### 1.1 设计前提（用户在规划会话里定的）

- **按最终规模设计**：catalog 最终是几百万篇以上的文献、可读全文也很多。开发环境（mini）的数据量和磁盘不作为设计约束。
- **全文越快越好**，上限由上游决定：PubMed 每天发一次 updatefiles，目标是"当天发布、当天可读"。
- **全文不做定期复查**：只抓一次；页面标抓取日期并链接 PMC 最新版（满足 PMC 条款"保持最新或明示可能不是最新"的后一种）。
- **用 PubMed updatefiles 增量同步取代 Europe PMC 回查**：根因是 catalog 的记录停在 baseline 生成那一刻；增量同步让记录跟上 PubMed，PMCID、撤稿、勘误、删除都随之同步。
- **mini 上增量同步只更新库里已有的文献**；导入范围做成配置，全量环境切成"新文献也导入"。

### 1.2 对 release spec 与 Linear 的改动

以下改动已随本文一并提交（内容见附录 A）：

- release spec：第 2 节 PAP-84 改为"PubMed 增量同步"；第 3 节删除边界 A（不导入 updatefiles），新增"全文不做定期复查"；第 4 节改写决策 A / D / E，新增决策 H（增量同步）；第 5 节改 D2、D3，删 D7 并换成覆盖更新的保护测试。
- Linear：PAP-84 改标题与描述；PAP-85 描述去掉复查、候选来源改为"有 PMCID 且没有全文记录"；Project 描述同步。

## 2. 总体结构

### 2.1 数据流

```
NLM updatefiles ──(每日)──► [PAP-84 增量同步] ──► cat_publication 及子表（含 pmc 标识符、文献类型）
                                                          │
                              有 PMCID 且没有全文记录 ─────┘
                                                          ▼
PMC Cloud Service ─────────► [PAP-85 全文抓取] ──► MinIO（XML / 图片 / PDF）
（公开 S3 桶）                                     └► cat_publication_fulltext（状态 FETCHED / 不可用原因）
                                                      cat_publication.is_oa、PMC OA 位置
                                                          ▼
                             [PAP-86 JATS 解析] ──► cat_publication_fulltext_body（JSONB）
                                                     状态 PARSED、字数、has_full_text
                                                          ▼
门户 ◄── Route Handler ◄── 网关 ◄── [PAP-87 读端点] 详情四字段 / 全文 / PDF / 图片
```

### 2.2 任务编排

三个任务都在 catalog，靠状态表衔接，不用领域事件（上游按天来，秒级延迟没有意义，事件只会多出投递、重试和顺序问题）。

| XXL-Job handler | 触发 | 子任务 | 每次运行做什么 |
|---|---|---|---|
| `pubmedUpdateSyncJob` | cron 每天 05:00（Asia/Shanghai） | `pmcFullTextFetchJob` | 从进度表最小的未完成文件（没有则最后一个成功文件的下一个）开始，按文件号依次处理到最新，跳过已成功的文件；遇到失败的文件停止 |
| `pmcFullTextFetchJob` | 父任务成功后；另有一条每小时的兜底任务 | `jatsFullTextParseJob` | 按批处理所有到期候选（第 5.2 节）直到取不到为止，批量大小可配 |
| `jatsFullTextParseJob` | 父任务成功后；另有一条每小时的兜底任务 | — | 按批处理所有待解析（第 6.4 节）直到取不到为止 |

- NLM 的 updatefiles 在美东时间 14:00 前后发布（n1686 为 `2026-10-17 14:03`），即北京时间次日 02:00 ~ 03:00；05:00 的 cron 留出余量。
- XXL-Job 的任务目前都在控制台手工建，没有 cron 与子任务的先例。本版照旧手工建，建任务的步骤（handler 名、cron、子任务 ID、参数）写进 `docs/patra/runbooks/v0.11-fulltext-runbook.md`。是否补种子 SQL 不在本版范围（`mysql-ops` 的 init 脚本只在新建卷时执行，补了也不影响现有环境）。
- 兜底任务是同一个 handler 在控制台多建一条，只配 cron、不配子任务。
- **实施时要实测**：父任务"成功"才触发子任务，失败（`handleFail`）不触发。
- **防重复运行**（我定的）：子任务与兜底任务在 XXL-Job 里是两条任务，会撞车。每个 handler 开始时取 PostgreSQL 会话级 advisory lock（`pg_try_advisory_lock`，每个任务类型一个固定键），取不到就记日志、`handleSuccess` 直接返回，结束时释放。行级再用 `INSERT … ON CONFLICT (publication_id)` 兜底，两层保证同一篇不会被并发写两次。

### 2.3 模块与包

全部在 `patra-api/patra-catalog`，沿用现有分层。新增的包：

| 层 | 包 | 内容 |
|---|---|---|
| domain | `model/vo/fulltext/`、`model/enums/` | 全文状态、不可用原因、正文结构的值对象 |
| domain | `port/repository/PublicationFullTextRepository` | 全文记录与正文的持久化 |
| domain | `port/source/PmcArticleSourcePort` | 列版本、读元数据、下载文件 |
| domain | `port/storage/FullTextObjectStoragePort` | 写入与读取全文文件 |
| domain | `port/parse/JatsBodyParserPort` | JATS → 正文结构 |
| domain | `port/read/PublicationFullTextReadPort` | 门户读端 |
| app | `usecase/publication/update/` | 增量同步的命令与 Handler |
| app | `usecase/fulltext/fetch/`、`usecase/fulltext/parse/` | Runner / Worker / Persister |
| app | `usecase/portal/` | `PortalPublicationFullTextQueryService` |
| infra | `batch/publication/` | 增量同步 Job（与 baseline 共用 Reader / Processor） |
| infra | `adapter/integration/pmc/` | PMC S3 客户端 |
| infra | `parser/jats/` | JATS 解析器（与现有 PubMed 解析器同在 `infra/parser/`） |
| infra | `adapter/persistence/`、`adapter/read/`、`adapter/storage/` | 仓储、读端、文件存取实现 |
| adapter | `scheduler/job/` | 三个 XXL-Job 入口 |
| adapter | `rest/portal/` | 全文、PDF、图片端点 |

全文记录是 publication 的补充数据（与 `PublicationOaLocation` 同一种建模），但不往现有 `PublicationRepository`（实现已 1,481 行）里加，新建 `PublicationFullTextRepository`。

## 3. 数据模型

迁移走新文件 `V8__create_fulltext.sql`（新增表与列不改已有文件的 checksum，mini 的库不用重建）。增量同步的进度表与全文表放在同一个 V8 里。

### 3.1 `cat_pubmed_update_file`（增量同步进度）

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT PK | |
| `file_name` | VARCHAR(40) UNIQUE | `pubmed26n1335.xml.gz` |
| `file_seq` | INT | 文件号 1335，排序用 |
| `status` | VARCHAR(20) | `RUNNING` / `COMPLETED` / `FAILED` |
| `scope` | VARCHAR(20) | 本次运行的导入范围：`EXISTING_ONLY` / `ALL` |
| `created_count` / `updated_count` / `deleted_count` | INT | |
| `skipped_absent_count` | INT | 范围为 `EXISTING_ONLY` 时库里没有而跳过的 |
| `skipped_invalid_count` | INT | 处理器判为无效（无 venue、非期刊等）而跳过的 |
| `doi_conflict_count` | INT | 见第 4.4 节 |
| `started_at` / `finished_at` | TIMESTAMPTZ | |
| `last_error` | VARCHAR(2000) | |
| `job_generation` | VARCHAR(64) | 这次处理所用的作业 generation（首次为空，重放为 `replay-<毫秒>`）；失败后重试沿用它，续跑失败的那个 JobInstance |

文件处理的断点续传仍交给 Spring Batch（一个文件一个 JobInstance）；这张表是业务层面的"跑到第几个、每个文件改了多少"，也是 runbook 里查进度的地方。

### 3.2 `cat_publication_fulltext`（全文记录与状态）

一篇文献一行。行在抓取任务首次处理该文献时创建（不预先登记"待抓取"行，候选靠查询得到，见第 5.2 节）。

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT PK | |
| `publication_id` | BIGINT UNIQUE | |
| `pmcid` | VARCHAR(20) | 抓取时用的 PMCID（`PMC1234567`） |
| `pmc_version` | VARCHAR(30) | 选中的版本前缀（`PMC1234567.2`） |
| `status` | VARCHAR(20) | 见第 3.4 节 |
| `unavailable_reason` | VARCHAR(30) | `NOT_IN_BUCKET` / `NOT_OPEN_ACCESS` / `RETRACTED` / `NO_BODY`，仅 `UNAVAILABLE` 时有值 |
| `license_code` | VARCHAR(30) | PMC 元数据原值，可为空 |
| `is_manuscript` | BOOLEAN | |
| `object_prefix` | VARCHAR(200) | MinIO 前缀，见第 5.4 节 |
| `xml_md5` | CHAR(32) | |
| `pdf_object_key` | VARCHAR(300) | 无 PDF 时为空 |
| `pdf_size` | BIGINT | |
| `pdf_md5` | CHAR(32) | 用作 PDF 的 ETag |
| `media` | JSONB | 已下载的正文图片清单 `[{ "name", "width", "height" }]`，宽高为像素，读不出时为 `null`；无图片时为 `[]`。解析器据此匹配图片、写图块宽高，图片接口据此判断文件是否属于该篇（第 5.3、6.3、7.5 节） |
| `fetched_at` | TIMESTAMPTZ | 页面显示的"抓取日期" |
| `parser_version` | INT | 解析成功时的解析器版本 |
| `parsed_at` | TIMESTAMPTZ | |
| `word_count` | INT | |
| `attempt_count` | INT | 连续失败次数，成功后清零 |
| `last_error` | VARCHAR(2000) | |
| `next_attempt_at` | TIMESTAMPTZ | 失败后的下次重试时间 |

索引：`(status, next_attempt_at)`、`(status, parser_version)`、`pmcid`。

### 3.3 `cat_publication_fulltext_body`（解析后的正文）

| 列 | 类型 | 说明 |
|---|---|---|
| `publication_id` | BIGINT PK | |
| `body` | JSONB | 正文文档，结构见第 6.2 节 |
| `parser_version` | INT | |
| `created_at` / `updated_at` | TIMESTAMPTZ | |

正文单独一张表：状态表要被抓取和解析任务频繁扫描，几百万行时不能被大文档拖慢。正文只按 `publication_id` 整篇读取，不在 JSONB 里查询，不建 GIN 索引。

### 3.4 状态流转

```
（候选：有 PMCID 且无记录）
        │ 抓取
        ├──► UNAVAILABLE（NOT_IN_BUCKET / NOT_OPEN_ACCESS）──(到 next_attempt_at 再查)──► …
        ├──► UNAVAILABLE（RETRACTED）   终态
        ├──► FETCH_FAILED ──(到 next_attempt_at 重试)──► …
        └──► FETCHED
                 │ 解析
                 ├──► UNAVAILABLE（NO_BODY）   终态，PDF 仍可下载
                 ├──► PARSE_FAILED ──(解析器版本提升后重试)──► …
                 └──► PARSED
                         │ 解析器版本提升
                         └──► 重新解析（状态保持 PARSED，失败则 PARSE_FAILED）
```

- **暂不可用要再查**（我定的）：`NOT_IN_BUCKET` / `NOT_OPEN_ACCESS` 不是终态。有两类文章会先落到这里，之后才能读：禁发期内的文章（作者手稿常见 12 个月禁发期），以及 PubMed 已写入 PMCID、PMC 桶当天还没到的文章。再查时间：
  - 文献有 PMC 放开日期且在未来 → `next_attempt_at` = 该日期（精确，不浪费请求）。放开日期来自 PubMed 记录的 `<PubMedPubDate PubStatus="pmc-release">`：现在的解析器不读它，`PublicationDateType`（`PublicationDateType.java:29-53`）也没有这个值。PAP-84 补上：新增 `PMC_RELEASE` 类型，baseline 与增量同步都写入 `cat_publication_date`，抓取任务从这里读；
  - 否则按 1 天、3 天、7 天、30 天，之后每 90 天再查。
  - 这和"全文不做定期复查"不冲突：那条针对已抓到的全文是否保鲜，这里是还没拿到的文章何时再试。
- **抓取失败**按 `attempt_count` 指数退避（1 小时、4 小时、1 天，之后每 3 天），重试上限可配（默认 8 次），超过后停在 `FETCH_FAILED` 等人工处理。
- **解析失败**不按时间重试（同一份 XML 用同一个解析器结果不会变），只在 `parser_version` 提升后重新尝试。
- **PMCID 变化**：增量同步改了某篇的 pmc 标识符（罕见）时，抓取候选查询会发现记录里的 `pmcid` 与当前标识符不一致，按新 PMCID 重新抓取（第 5.2 节）。
- **手动重抓单篇**：抓取任务参数 `publicationIds=1,2,3`，把这些记录视为候选，忽略状态。

### 3.5 已有字段的写入

| 字段 | 由谁写 | 何时写 |
|---|---|---|
| `cat_publication.is_oa` | 抓取 | 确认 PMC 开放获取且未撤稿时置 `true`；之后不回写为 `false` |
| `cat_publication.oa_status` | 不写 | PMC 不提供金色 / 绿色之分，保持空 |
| `cat_publication_oa_location` | 抓取 | 写一条 PMC 位置。只替换 `location_type = PUBMED_CENTRAL` 的行，不用会整篇清空的 `replaceOaLocationsBatch` |

现有 `PublicationOaLocation.ofPmc(pmcid, isBest)`（`PublicationOaLocation.java:104-116`）与这里的需要不符：不接收许可、不设 `evidenceSource`、`versionType` 写死为 `ACCEPTED`、链接用旧域名。改写为（我定的）：`ofPmc(pmcid, license, isManuscript)`，`oaStatus = GREEN`（位置级的 OA 状态：PMC 是仓储副本，按 Unpaywall 的口径是绿色；这一列 NOT NULL。文献级 `cat_publication.oa_status` 仍保持空）、`locationType = PUBMED_CENTRAL`、`versionType` 手稿为 `ACCEPTED` 否则 `PUBLISHED`、`url = https://pmc.ncbi.nlm.nih.gov/articles/{PMCID}/`、`hostDomain = pmc.ncbi.nlm.nih.gov`、`evidenceSource = "PMC"`、`isBest = true`。绿地项目，直接改原工厂方法，不保留旧签名。
| `cat_publication_metadata.has_full_text` | 解析 | `PARSED` 时 `true`；解析为 `NO_BODY` 或失败时 `false` |
| `cat_publication_metadata.full_text_url` | 不写 | 保持外链含义（"去全文"按钮），与站内全文无关 |

## 4. PAP-84 PubMed 增量同步

### 4.1 做什么

新增 Spring Batch Job `pubmedUpdateImportJob`，处理 `https://ftp.ncbi.nlm.nih.gov/pubmed/updatefiles/` 下的文件（2026 年从 `pubmed26n1335` 开始，规划时最新为 `n1687`）。与 baseline 导入共用下载、读取、处理代码，按"库里有没有这个 PMID"分流：

**分流必须在处理器之前**（我定的）：现有 `PubmedArticleItemProcessor` 对每条记录都调用 `findOrCreateJournalInstance`（`PubmedArticleItemProcessor.java:163`），它是 `REQUIRES_NEW` 事务（`VenueInstanceGatewayImpl.java:57`）。如果到写入时才分流，`EXISTING_ONLY` 模式追赶 353 个文件时，会为上千万条库里没有的记录建出 venue instance，并且每条开一个事务。所以更新模式的 Step 用组合处理器：第一段按 PMID 查库（走 `uk_pmid` 索引，包含已软删除的行），库里没有且范围为 `EXISTING_ONLY` 的直接返回 null 并计数；命中的记下已有 ID，再交给现有处理器。

分流规则：

| 情况 | 范围 `EXISTING_ONLY` | 范围 `ALL` |
|---|---|---|
| 库里有（含已软删除） | 覆盖更新（第 4.3 节） | 覆盖更新 |
| 库里没有 | 跳过，计入 `skipped_absent_count` | 新建（走 baseline 的插入路径） |
| `<DeleteCitation>` 里的 PMID | 软删除 | 软删除 |

范围由配置 `patra.catalog.pubmed.update.scope` 决定（默认 `ALL`；mini 的 Nacos 配成 `EXISTING_ONLY`）。

### 4.2 读取与文件编号

- `PublicationImportParams` 现把文件名模板与 `TOTAL_FILE_COUNT=1334` 写死。改为：baseline 与 updatefiles 各自从配置拿年份前缀（`pubmed26n`）；updatefiles 的"最新文件号"不写死，任务启动时读 updatefiles 目录列表（HTML 索引，只取 `pubmed26nNNNN.xml.gz`）得到。
- 下载 URL 用 `PubmedDataSourceProperties.updateUrl`（已有、未被使用），删掉"暂不支持"的注释。
- `importBatch` 前缀从 `baseline-` 扩展为 `update-<文件名>`。
- **识别删除**：现在的 StAX 解析只认 `PubmedArticle`。新增对 `<DeleteCitation>` 的识别，产出"删除 PMID 列表"。updatefiles 里 `DeleteCitation` 位于文件末尾、所有 `PubmedArticle` 之后（实施时用 n1687 核实）；Job 拆成两个 Step：先处理文章，再处理删除，保证同一文件内顺序正确。`PubmedBookArticle` 继续忽略。
- **同一 PMID 多次出现**：文件内后出现的覆盖先出现的。现在写入器的 chunk 内去重是"先到先得"，更新模式下改为"后到为准"（chunk 内按 PMID 只保留最后一条；跨 chunk 时后一个 chunk 自然覆盖前一个）。跨文件靠严格按文件号顺序处理保证。

### 4.3 覆盖更新的规则

原则：**内部 ID 不变；PubMed 管的部分全部替换；别的流程写的字段保持原值。**

**主表 `cat_publication`**：用显式列名的 UPDATE（不复用 `toEntity` / `updateEntity`：`create()` 会把 `is_oa` 重置为 false、`citation_count` 重置为 0）。

| 更新 | 保留 |
|---|---|
| `provenance_code`、`doi`（见 4.4）、`title`、`original_title`、`language_raw`、`language_code`、`publication_status`、`media_type`、`publication_year`、`authors_complete`、`number_of_references`、`conflict_of_interest`、`venue_id`、`venue_instance_id`（按新记录重新匹配）、`last_synced_at`、`deleted_at`（复活时清空） | `is_oa`、`oa_status`、`citation_count`、`ext_data`、`record_remarks` |

`last_synced_at` 更新为本次同步时间。这一列的门户用法本来就是"更新 / 同步"的含义：`/papers` 排序项"最近更新"（`PaperSearchBar.tsx:20`，后端 `PublicationSearchDao.java:92`）与信息行"最近同步 yyyy-MM-dd"（`PapersInfoRow.tsx`，后端 `PublicationSearchDao.java:263`）。不更新的话，"最近同步"会永远停在 baseline 导入那天，列名也名不副实。更新后，"最近更新"排序会把当天同步过的文献（全量环境下以新增为主，加上修订）排在前面，与标签一致。列表卡片的 `minutesAgo` 门户未展示（`types/portal.ts:159`），不受影响。

**`cat_publication_metadata`**：只 UPDATE 七个 PubMed 列（`indexing_status`、`indexing_method`、`owner`、`citation_subset`、`data_source`、`import_batch`、`import_date`），其余列（含 `has_full_text`、`full_text_url`）保留。不用 `replaceMetadataBatch`（整行删除重插会清掉 `has_full_text`）。

**`cat_publication_identifier`**：只删除并重建 `source = 'PubMed'` 的行。

**整体替换的子表**（按 `publication_id` 先删后插，删除顺序先子后父）：`abstract`、`alternative_abstract`、`date`、`type`、`mesh_qualifier` → `mesh_heading`、`suppl_mesh`、`keyword`（只删关联行）、`funding`、`investigator`（只删关联行）、`personal_name_subject`、`author_affiliation` → `author`。

- 实现方式：在 `PublicationRepositoryAdapter` 旁新建 `PublicationOverwriteAdapter`（或同等的独立类），提供 `deleteImportOwnedChildren(Collection<Long> publicationIds)`，按上面的顺序批量删除；然后让被覆盖的文献**带着已有 ID** 走现有 `insertAllWithAssociations` 的子表写入部分。不新写一套子表插入。
- 共享实体（`cat_keyword`、`cat_investigator`、`cat_venue` / `cat_venue_instance`、`cat_organization`、MeSH 字典）只重新关联，不删除。替换后不再被引用的关键词 / 研究者行保留（孤儿行对读端无影响，本版不清理）。
- 不碰：`cat_publication_oa_location`、全文两张表，以及无 Entity 的 5 张表（reference、external_reference、related_item、supplemental_object、history）。

**事务**：一个 chunk 一个事务（Spring Batch 默认）。chunk 内先批量删子表，再批量更新主表与 metadata，再批量插子表。chunk 上限沿用现有 `MAX_CHUNK_SIZE` 的约束（`IN` 绑定参数上限）。

### 4.4 边角情况

| 情况 | 处理 |
|---|---|
| **软删除后同一 PMID 再出现** | `uk_pmid` 不含 `deleted_at`，查已有记录时要包含已软删除的行（绕过 `@SoftDelete` 过滤的原生查询）；命中则清空 `deleted_at` 并覆盖更新 |
| **新 DOI 已属于另一篇文献** | 不改 DOI（保留旧值），其余照常覆盖，计入 `doi_conflict_count` 并记 warn 日志。不跳过整条记录 |
| **处理器判为无效**（无标题、匹配不到 venue、非期刊） | 跳过，计入 `skipped_invalid_count`；库里的旧记录保持原样 |
| **`DeleteCitation` 的 PMID 库里没有** | 忽略 |
| **跳过被吞** | 现有 `faultTolerant` 对 `DataIntegrityViolation` 的 skip 在更新模式下保留，但每次 skip 都计数并记 error 日志，计数写进进度表；单文件 skip 超过上限（沿用 1000）则该文件失败、任务停止 |

### 4.5 任务入口与参数

`pubmedUpdateSyncJob`（adapter 层）：

- 无参数：从进度表最后一个 `COMPLETED` 的文件号 +1 开始，依次处理到目录里的最新文件；一个文件失败就停，`handleFail`。
- `fromFile=1335`：从指定文件号开始重放（已 `COMPLETED` 的文件用新的 `generation` 重新跑；未完成的文件沿用进度表 `job_generation` 记下的上次 generation 续跑）。用途：导入了一个较早的 baseline 文件后，让那批文献也追上修订。覆盖是幂等的（后到为准），重放不会出错。
- `maxFiles=N`：本次最多处理 N 个文件（首次追赶 353 个文件时分批跑）。
- `scope=EXISTING_ONLY|ALL`：覆盖配置，仅本次生效。

首次在 mini 上追赶：`EXISTING_ONLY`，从 1335 开始。353 个文件每个几十 MB，下载走 mini 直连。

### 4.6 测试

- 单元：PMID 分流（有 / 没有 / 已软删除 × 两种范围），`EXISTING_ONLY` 下库里没有的记录不调用 `findOrCreateJournalInstance`；chunk 内后到为准；DOI 冲突保留旧值；`DeleteCitation` 解析；`pmc-release` 日期解析为 `PMC_RELEASE`。
- 集成（Testcontainers PG）：
  - 构造一个最小 updatefile 夹具（3 条修订 + 1 条新增 + 1 个 `DeleteCitation`），下载端用 `@MockitoBean FileDownloadPort` 指向夹具（沿用 `MeshScrImportE2E` 的做法）。
  - **保护测试**：先写入一篇带 `is_oa = true`、`citation_count = 42`、`has_full_text = true`、一条 PMC OA 位置、一条 `source != 'PubMed'` 的标识符和全文两表记录的文献，用修订记录覆盖后断言这些值全部保留，而标题、作者、MeSH 已换成新值、内部 ID 不变、子表没有残留旧行。
  - 软删除后复活；同一 PMID 在两个文件里依次出现，以后一个为准。
- 进度表：失败文件后再次运行从该文件续跑。

### 4.7 验收

- 上面的测试全绿，`./gradlew check` 与 `:patra-catalog-infra:integrationTest` 全绿。
- mini 以 `EXISTING_ONLY` 追完 n1335 至最新，记录补全前后的 PMCID 覆盖率（release spec 新 D2：≥ 40%）与进度表里各计数的合计。

## 5. PAP-85 PMC 全文抓取

### 5.1 PMC Cloud Service 的用法

公开 S3 桶 `pmc-oa-opendata`，HTTPS 匿名访问，不引入 AWS SDK。

- **列版本**：`GET https://pmc-oa-opendata.s3.amazonaws.com/?list-type=2&prefix={PMCID}.&delimiter=/`，返回 XML 里的 `CommonPrefixes` 即各版本前缀（`PMC123.1/`、`PMC123.2/`）。没有任何前缀 → `NOT_IN_BUCKET`（没有开放许可的 PMC 文章不在桶里）。
- **读元数据**：每个版本 `GET {prefix}{PMCID}.{v}.json`，取 `is_pmc_openaccess`、`is_manuscript`、`is_retracted`、`license_code`、`xml_url`、`pdf_url`、`media_urls` 及各自 md5。
- **选版本**：候选为 `is_pmc_openaccess = true` 的版本；有多个时优先非手稿，再取版本号最大的。版本号不代表新旧（PMC FAQ），这里只作同等条件下的平局规则。
  - 没有候选 → `NOT_OPEN_ACCESS`（作者手稿的许可为 `TDM`，`is_pmc_openaccess = false`，在此排除）。
  - 选中版本 `is_retracted = true` → `RETRACTED`。
- **下载**：XML 必下；`pdf_url` 存在则下 PDF；`media_urls` 里扩展名为图片（jpg / jpeg / png / gif / webp）的下载，其余（补充材料、视频、tif）不下（release spec 边界 E）。用 md5 校验每个文件。2026-10-19 实测 BMJ Open、JAMA Netw Open 的正文图片都是 `.webp`（如 `PMC13583816.1/bmjopen-16-9-g001.webp`），XML 里的 `xlink:href` 与文件名一致。
- **图片尺寸**：下载后读一次图片头取像素宽高，写入全文记录的 `media`（第 3.2 节）。JDK 的 ImageIO 不认 WebP，引入 TwelveMonkeys 的 `com.twelvemonkeys.imageio:imageio-webp:3.15.2` 插件（版本登记在 `PatraDependencyManagement.kt`，只加到 catalog infra；SPI 自动注册，只读文件头，不解码整张图）；读不出时宽高为 `null`，不算抓取失败。
- **HTTP 客户端**：在 catalog 按 OpenAlex 客户端的写法新建专用 `RestClient`（连接 5 秒、读取 60 秒、User-Agent 标明 Patra）。不走隧道代理。客户端要区分"404 不存在"与"出错"，不能像现有客户端那样吞掉异常返回空。大文件（PDF）复用 `DownloadClient.downloadToTemp`（自带重试与退避）。
- **并发与限流**：Worker 并发数可配（默认 4），请求间隔可配（默认 0）。PMC 未公布限流，出现 429 / 503 时按抓取失败退避。
- **署名**：PMC 条款要求标注 NLM 为来源、不得使用 PMC 标志或暗示 NIH 背书。读端点返回的 `source` 字段固定文案见第 7.3 节。

### 5.2 候选

抓取任务每次运行用 keyset 游标（按 `publication_id`）分批取候选，三类：

1. 有 pmc 标识符、没有全文记录、文献未删除；
2. 有全文记录、`status = FETCH_FAILED`、`next_attempt_at <= now()`、未超过重试上限；
3. 有全文记录、记录里的 `pmcid` 与当前 pmc 标识符不一致（PMCID 变了）；
4. 有全文记录、`status = UNAVAILABLE`、`unavailable_reason ∈ (NOT_IN_BUCKET, NOT_OPEN_ACCESS)`、`next_attempt_at <= now()`（暂不可用的再查，第 3.4 节）。

pmc 标识符的值以 `cat_publication_identifier.type = 'pmc'` 为准（PubMed 解析已写入、带 `PMC` 前缀）。候选查询要能在几百万行上走索引：第 1 类用 `NOT EXISTS` + `cat_publication_identifier (type, identifier_value)` 索引，实施时用 `EXPLAIN` 核实。

### 5.3 一篇的处理（Runner / Worker / Persister）

沿用 LetPub / Scopus 补全任务的三段式：Runner 取批、Worker 不开事务做 HTTP 与 MinIO、Persister 用 `REQUIRES_NEW` 写库。

1. 列版本、读元数据、选版本 → 不可用则 Persister 写 `UNAVAILABLE` + 原因，结束。
2. 下载 XML、PDF、图片，校验 md5，写 MinIO。
3. Persister 一个事务内：全文记录 `FETCHED`（含 `pmc_version`、许可、对象前缀、PDF 信息、图片清单 `media`、`fetched_at`）；`is_oa = true`；替换 PMC OA 位置。
4. 任何一步出错 → `FETCH_FAILED`，`attempt_count + 1`，算 `next_attempt_at`；已写入 MinIO 的文件不清理（下次覆盖写）。

一篇失败不影响同批其他篇。返回统计：处理数、`FETCHED` 数、各不可用原因数、失败数。

### 5.4 MinIO 布局

- 桶：`patra-catalog-fulltext`，配置 `patra.catalog.fulltext.bucket`（record + `@DefaultValue`，默认值写在 record 里），私有。MinIO provider 上传时懒建桶。
- 对象键沿用 PMC 的目录结构：`pmc/{PMCID}/{PMCID}.{v}/{原文件名}`，例如 `pmc/PMC13138198/PMC13138198.1/PMC13138198.1.xml`、`…/BLOODA_ADV-2025-017641-ga1.jpg`。`object_prefix` 存到版本目录为止。
- 文件直写，不经 `patra-object-storage` 服务登记（与期刊封面一致）。

### 5.5 测试与验收

- 单元：选版本规则（OA / 手稿 / 撤稿 / 多版本平局）；退避与再查时间计算（含有放开日期的情况）；候选四类的判定；改写后的 `ofPmc`；图片尺寸读取（jpg / png / webp 各一张，坏文件返回 `null`）。
- 集成：WireMock 模拟 S3 列表、JSON、文件；MinIO 用现成的 `CatalogITMinIOContainerInitializer`；PG 用 Testcontainers。覆盖：正常抓取写入 MinIO 与三张表；`NOT_IN_BUCKET` / `NOT_OPEN_ACCESS` 到期再查后成功；`RETRACTED` 不再查；下载失败退避；PMCID 变化重抓；手动重抓参数；两个任务实例同时运行时第二个直接返回（advisory lock）。
- mini 跑完后记录：可读全文篇数（等解析）、有 PDF 篇数、各不可用原因篇数、`is_oa = true` 篇数（release spec D3）。

## 6. PAP-86 JATS 解析入库

### 6.1 解析器

- 实现：StAX 或 DOM 自写（没有成熟的 Java JATS 模型库；XSLT 转 HTML 不符合 release spec 决策 C）。文章 XML 一般在几十 KB 到几 MB，用 DOM 更好写；实施时以 DOM（JDK 自带，关闭外部实体与 DTD 加载）为准。
- 范围：只解析 `<body>` 与 `<back>`；`<front>` 里的标题、作者、摘要已在 catalog 里，不重复。
- 解析器有整数常量 `PARSER_VERSION`；改动解析规则时 +1，解析任务据此重新解析旧结果。

### 6.2 正文结构（存进 `body` 的 JSON）

顶层：

```json
{
  "schemaVersion": 1,
  "blocks": [ Block ],
  "sections": [ Section ],
  "back": [ Section ],
  "references": [ Reference ],
  "wordCount": 5230
}
```

- **顶层 `blocks`**：`<body>` 里第一个 `<sec>` 之前的直接内容（如 BMJ Open 开头的 "Strengths and limitations" 框）。`<sec>` 之后又出现的直接内容归入一个无标题的 Section。只取 `<article>` 的直接子元素 `<body>` / `<back>`，`<sub-article>`（审稿意见等）里的不取。
- **Section**：`{ "id", "title": [Inline], "sections": [Section], "blocks": [Block] }`。`id` 取 JATS 的 `id` 属性，没有则生成（供目录锚点）。`back` 收致谢、脚注组、附录等 `<back>` 下除参考文献以外的内容。
- **Block**（`kind` 区分）：

| kind | 字段 | 来源 |
|---|---|---|
| `paragraph` | `content: [Inline]` | `<p>`；`<p>` 里嵌着块级元素（`disp-formula`、`fig`、`table-wrap`、`list` 等，Wiley 与 BMC 常见）时在块的两侧切开，块单独输出 |
| `figure` | `id`、`label`、`caption: [Block]`、`image`（MinIO 里的文件名）、`width` / `height`（像素，取自 `media`，未知为 `null`） | `<fig>` + `<graphic xlink:href>` |
| `table` | `id`、`label`、`caption: [Block]`、`head` / `body: [[Cell]]`、`footnotes: [Block]`；Cell = `{ content: [Inline], colspan, rowspan, header }` | `<table-wrap>` 内的 XHTML 表格模型 |
| `formula` | `id`、`label`、`mathml`（字符串）；只有公式图片时改为 `image`、`width`、`height` | `<disp-formula>`：取值顺序 MathML（含 `<alternatives>` 里的）→ `tex-math`（输出 `code`）→ `graphic`；MathML 去掉命名空间前缀 |
| `list` | `ordered`、`items: [[Block]]` | `<list>` |
| `quote` | `blocks: [Block]` | `<disp-quote>` |
| `box` | `id`、`title: [Inline]`、`blocks: [Block]` | `<boxed-text>` |
| `code` | `text` | `<preformat>`、`<code>` |

- **Inline**（`kind` 区分）：`text`（`text`）、`style`（`style` ∈ italic / bold / sup / sub / underline / smallcaps / monospace，`children`）、`xref`（`refType` ∈ bibr / fig / table / disp-formula / sec / fn / boxed-text，`targets: [id]`，`children`；其他类型或目标在本篇找不到时不生成 `xref`，只输出子节点并计警告）、`link`（`href`，`children`；只保留 http / https / doi）、`math`（`mathml` 字符串；**任何位置**的 MathML `math` 元素都输出为它，不只 `<inline-formula>` 里的——Wiley PMC12670376 的 941 个 MathML 里 882 个直接挂在 `p` / `td` / `th` / `title` 下）、`break`。
- **Reference**：`{ "id", "label", "content": [Inline], "pmid", "doi" }`。`<ref>` 下可能是 `citation-alternatives` 包着两种写法（BMC、Nat Commun 几乎全是）；`content` 优先由 `<mixed-citation>` 原样转换，没有时由 `<element-citation>` 按"作者. 标题. 期刊. 年;卷:页."拼出；`pmid` / `doi` 从 `<pub-id>` 取（门户只做外链，不做站内跳转，release spec 边界 G）。
- **公式**：优先 MathML。2026-10-19 预检实测纠正了原先"桶里没有公式图片"的判断：PLOS 的公式是 `alternatives(graphic | mml:math)`，个别（PMC12779161 有 5 个）只有 `graphic`，图片在桶里（该篇 `media_urls` 有 259 个文件，多数是公式图）。只有图片的公式输出 `formula` 的 `image` 并计警告；只有 `<tex-math>` 没有 MathML 的公式，输出 `code` 块放 TeX 并计一条解析警告。BMC 与 Sci Rep 的 `<tex-math>` 是整段 `\documentclass{minimal}…\begin{document}$$…$$\end{document}`（样本 PMC12632047.1）：有 `\begin{document}` 时只取它与 `\end{document}` 之间的内容，再去掉外层的 `$$…$$` 或 `$…$`，两端去空白；没有外壳的原样输出。
- **门户渲染约定**：行内节点与门户摘要的 `RichInlineText` 白名单渲染对齐；MathML 字符串由门户的 MathML 白名单解析。前端类型与 zod 校验以本节为准（PAP-88）。
- **MathML 白名单要扩充**（我定的，落在 PAP-88）：门户现有 `MATH_TAGS`（`rich-inline-text.ts:41-59`）没有 `mtable`、`mtr`、`mtd`、`mroot`、`mfenced`、`menclose`、`mpadded`、`mphantom`、`mmultiscripts` 等行间公式常用标签，会被剥掉；`FORMATTING_TAGS`（第 38 行）也没有 smallcaps / monospace（这两种在本结构里是 `style` 节点，不走字符串标签）。后端只做命名空间前缀去除，不做 MathML 规范化或裁剪——白名单是安全边界，放在渲染端统一维护。PAP-86 的夹具里收集到的 MathML 标签全集，整理成清单交给 PAP-88。

### 6.3 不认识的元素与失败

- 不认识的块级元素：取其纯文本作为 `paragraph`，记一条解析警告（警告数写日志，不入库）。不认识的行内元素：保留其子内容。
- `<supplementary-material>`、`<media>` 跳过（release spec 边界 E）。
- 没有 `<body>` 或 `<body>` 为空（部分 OA 记录只有扫描 PDF）：`UNAVAILABLE` + `NO_BODY`，PDF 照常可下载。
- XML 不合法或解析抛异常：`PARSE_FAILED` + `last_error`。读 MinIO 出错不算解析失败：保持 `FETCHED`，下次运行再试。
- `NO_BODY` 也记 `parser_version`，解析器版本提升后同样重新解析（防止误判无正文后救不回来）。
- 图片：在全文记录的 `media` 清单里匹配 `<graphic xlink:href>`：先按全名，找不到再按"去扩展名后同名"（少数 href 不带扩展名）；匹配上则写 `image` 与宽高，找不到则该图 `image` 为空并计警告。解析不访问 MinIO 判断文件是否存在。

### 6.4 解析任务

- 候选：`status = FETCHED`，或 `status ∈ (PARSED, PARSE_FAILED)` 且 `parser_version < PARSER_VERSION`。keyset 分批。
- 一篇：从 MinIO 读 XML（`object_prefix` + `{PMCID}.{v}.xml`）→ 解析 → Persister 一个事务内写 `cat_publication_fulltext_body`（upsert）、全文记录 `PARSED` / `UNAVAILABLE(NO_BODY)` / `PARSE_FAILED`、`word_count`、`parser_version`、`parsed_at`、`metadata.has_full_text`。
- 字数：顶层 `blocks`、正文与 `back` 里的文本按空白切分计数，章节标题、图题、表题、表注照计。不计：参考文献、公式（`formula` 块与 `math` 行内节点）、表格单元格（`head` / `body`，表题与表注照计）、`code` 块。口径来自设计简报附录 A.4：PMC12670376 把 MathML 记号算进去是 19,335 词、约 85 分钟，按此口径约 11,000 词、49 分钟。
- 全量重新解析 = 把 `PARSER_VERSION` +1 后跑解析任务，全程不访问 PMC。

### 6.5 测试与验收

- 夹具：从 PMC 桶取至少 20 篇真实开放获取文章的 XML，覆盖不同出版商（Elsevier、Springer Nature、Wiley、Frontiers、PLOS、MDPI、BMC、OUP 等）以及以下特征：多级章节、图、复杂表格（合并单元格）、行间与行内 MathML、只有 TeX 的公式、列表、`boxed-text`、`mixed-citation` 与 `element-citation` 两种参考文献、无 `<body>`。夹具放 `src/test/resources/jats/`（都是 CC 许可文章，文件头注明来源 PMCID 与许可）。
- 单元：每篇夹具断言章节数、块类型分布、公式为 MathML、引用的 `targets` 都能在 `references` / 图表里找到、图片文件名与夹具清单一致。
- 集成：MinIO + PG，跑解析任务，断言状态、正文表、`has_full_text`；`PARSER_VERSION` 提升后重新解析且不发任何 HTTP 请求（WireMock 无请求记录）。
- mini：解析出错 < 2% 且每条有原因（release spec D3）。

## 7. PAP-87 门户全文读端点

### 7.1 可读判定

一篇文献"可读全文"当且仅当：文献未删除，全文记录 `status = PARSED`，且文献不是撤稿文献（`cat_publication_type` 不含 `Retracted Publication`）。PDF 可下载当且仅当：文献未删除、不是撤稿文献、全文记录有 `pdf_object_key`（不要求 `PARSED`，`NO_BODY` 的文章也能下 PDF）。图片可取当且仅当可读全文。全文接口（第 7.3 节）在可读全文或 PDF 可下载时返回 200。

撤稿判定放在读端，而不是只看抓取时 PMC 的 `is_retracted`：增量同步会把 PubMed 的撤稿标记带进来，读端判定能让已抓取的全文在文献被撤稿后自动停止展示。

### 7.2 详情接口

`GET /portal/publications/{id}` 的 `PortalPublicationDetailResponse` 新增 `hasFullText`、`hasPdf`、`pdfSizeBytes`（PDF 可下载时为 `pdf_size`，否则为空；下载按钮旁显示文件大小）；已有的 `estimatedReadMin` 从恒为空改为 `ceil(word_count / 230)`（可读全文时，否则为空；`PortalApiConverter.java:284` 的 `TODO(read-time)` 随之删除）。详情接口不返回正文。

详情接口另给 `retracted`：PubMed 发表类型含 `D016441`，或全文记录的不可用原因是 `RETRACTED`（抓取时 PMC 已标撤稿，而 PubMed 记录还没跟上——2026-10-20 在 mini 上见到 3 篇）。可读、可下载两项也按这个合并后的判定算；门户据它显示撤稿说明和「已撤稿」胶囊，不再自己看文献类型。

列表卡片 `PortalPaperResponse` 也有一个恒为空的 `estimatedReadMin`（`PortalApiConverter.java:49` 的 `TODO(read-time)`）。本版不做（我定的）：release spec 只要求详情页速览栏显示阅读时长，列表接口加关联查询会影响 `/papers` 的检索性能，留着 TODO 不动。

### 7.3 全文接口

`GET /portal/publications/{id}/fulltext`

```json
{
  "publicationId": "123",
  "pmcid": "PMC13138198",
  "pmcUrl": "https://pmc.ncbi.nlm.nih.gov/articles/PMC13138198/",
  "license": { "code": "CC BY-NC-ND", "url": "https://creativecommons.org/licenses/by-nc-nd/4.0/" },
  "source": "数据来源：美国国家医学图书馆（NLM）PubMed Central",
  "fetchedAt": "2026-10-20T05:12:00Z",
  "wordCount": 5230,
  "estimatedReadMin": 23,
  "isManuscript": false,
  "body": { "schemaVersion": 1, "sections": [], "back": [], "references": [] }
}
```

- `license.url`：常见 CC 许可（BY、BY-NC、BY-NC-ND、BY-ND、BY-SA、BY-NC-SA、CC0）映射到 CC 4.0 / 1.0 的官方链接；`license_code` 为空或不认识时 `license` 为 `null`，门户显示"许可见原文"。
- 图片在 `body` 里只存文件名，门户拼成自己的转发地址（第 7.5 节）。
- **有 PDF 无正文**（不可读全文但 PDF 可下载，典型是 `NO_BODY`）→ 200，`body`、`wordCount`、`estimatedReadMin` 为 `null`，署名字段（`pmcid`、`pmcUrl`、`license`、`source`、`fetchedAt`、`isManuscript`）照常返回：页面在分发 PDF，同样要署名（release spec 决策 D，设计简报附录 A.1）。
- 既不可读也不能下 PDF → 404（新增 `FullTextNotAvailableException`，`StandardErrorTrait.NOT_FOUND`，由现有错误解析引擎映射）。

### 7.4 PDF 接口

`GET /portal/publications/{id}/fulltext/pdf`

- 从 MinIO 流式读出（`ObjectStorageOperations.download` 返回的流），用 `InputStreamResource` 同步写回，不整篇读进内存。不用 `StreamingResponseBody`：它走异步分派，受 Tomcat 默认 30 秒异步超时限制（网关是 60 秒），`RestTestClient` 也读不到它的响应体。打开对象放在返回响应之前，对象缺失时仍能返回 404。全仓库没有二进制响应的先例，这是第一个。
- catalog 关闭 OSIV（`spring.jpa.open-in-view: false`）：默认开着时，下载请求从查库起一直占着 JDBC 连接直到写完，连接池只有 20 条。
- 响应头：`Content-Type: application/pdf`、`Content-Length`、`ETag: "<pdf_md5>"`、`Cache-Control: public, max-age=86400`、`Content-Disposition: attachment; filename="<PMCID>.pdf"`。支持 `If-None-Match` 返回 304。
- 网关不补缓存头（安全链 `headers().disable()`），这些头由 catalog 设置；网关读超时是 60 秒空闲超时（下游持续发数据就不断，见网关设计第 6 节），由 release spec D6 实测最大 PDF 兜底。
- 二进制端点不经 `PortalApiConverter`，错误仍走现有错误解析（不可下载 → 404）。

### 7.5 图片接口

`GET /portal/publications/{id}/fulltext/figures/{fileName}`

- `fileName` 只允许 `[A-Za-z0-9._-]+`（防路径穿越），且必须在该篇全文记录的 `media` 清单里；否则 404。
- `Content-Type` 按扩展名；`Cache-Control: public, max-age=604800, immutable`（同一版本的文件不会变）；`ETag` 用 MinIO 的 ETag。

门户这侧（PAP-88）：浏览器访问不到网关，PDF 与图片经门户同源 Route Handler（如 `/api/papers/[id]/pdf`、`/api/papers/[id]/figures/[file]`）调 `gatewayFetch` 转发，透传上述响应头。

### 7.6 测试与验收

- 集成（`@SpringBootTest` + Testcontainers PG + MinIO）：可读 / 不可读 / 撤稿 / `NO_BODY` 有 PDF 四种组合下三个端点的状态码（`NO_BODY` 有 PDF 时全文接口 200 且 `body` 为 `null`）；PDF 与图片响应头；`If-None-Match` 返回 304；非法文件名与不在 `media` 里的文件名 404；详情接口四个字段。
- 详情接口响应体不含正文（断言字段集合）。
- mini：经网关下载库内最大 PDF，md5 与 PMC 元数据一致（release spec D6）。

## 8. 配置汇总

| 键 | 默认 | 放哪 |
|---|---|---|
| `patra.catalog.pubmed.update.scope` | `ALL` | 默认在 record；mini 在 `nacos/dev/patra-catalog.yaml` 配 `EXISTING_ONLY` |
| `patra.catalog.pubmed.update-url` | 已有 | `PubmedDataSourceProperties` |
| `patra.catalog.pubmed.file-prefix` | `pubmed26n` | 新增，baseline 与 updatefiles 共用 |
| `patra.catalog.fulltext.bucket` | `patra-catalog-fulltext` | record |
| `patra.catalog.fulltext.pmc.base-url` | `https://pmc-oa-opendata.s3.amazonaws.com/` | record |
| `patra.catalog.fulltext.fetch.batch-size` / `concurrency` / `max-attempts` | 200 / 4 / 8 | record |
| `patra.catalog.fulltext.parse.batch-size` | 500 | record |

不涉及密钥，全部默认值写在 `@ConfigurationProperties` record 里；只有环境差异（mini 的 `scope`）进 Nacos。MinIO 凭据已在 `patra-common.yaml`，不新增。

## 9. 留给实施者决定的

- 正文 JSON 的字段命名细节（以第 6.2 节的结构为准，名字可在实施中微调，但要与 PAP-88 的前端类型同时改）。
- 解析器内部用 DOM 还是 StAX（第 6.1 节倾向 DOM）。
- 各任务批量大小与并发的具体默认值（第 8 节为起点）。
- 新类的具体命名，遵守 `.claude/rules/tech/port-service.md`。

## 附录 A：release spec 与 Linear 的改动草稿

2026-10-19 已按此修改 release spec 与 Linear，随本文一起提交。

**release spec `v0.11-fulltext-reader.md`**

- 第 2 节 PAP-84 一行改为：`[BE] PubMed updatefiles 增量同步：按文件号处理，已有文献覆盖更新（内部 ID 不变、其他流程的字段保留），删除记录软删除；导入范围可配（mini 只更新已有）；补解析 PMC 放开日期`。PAP-85 一行去掉"定期复查保持最新"，改为"暂不可用的按放开日期或退避再查"。PR 归属与执行顺序不变。
- 第 3 节：删除边界 A（不导入 updatefiles）；新增"已抓到的全文不做定期复查：只抓一次，页面标抓取日期并链接 PMC 最新版"。
- 第 4 节：
  - 决策 A 删掉"Europe PMC 客户端在 catalog 内新建、补 PMCID 任务沿用补全任务写法、追加 PMCID 不能用 `replaceIdentifiersBatch`"，改为"增量同步与 baseline 导入共用解析与写入代码"。
  - 决策 D 删掉"Europe PMC 只用来找候选"；撤稿判定改为"抓取时看 PMC 的 `is_retracted`，展示时看 PubMed 文献类型是否含 `Retracted Publication`"。
  - 决策 E 删掉"定期复查"整段，改为"已抓到的不复查；暂不可用的按放开日期或退避再查"。
  - 新增决策 H"用 PubMed updatefiles 增量同步取代 Europe PMC 回查"：根因、覆盖规则（ID 不变、PubMed 管的部分全替换、别的流程写的字段保留）、导入范围可配。
- 第 5 节：
  - D2 改为"增量同步以 `EXISTING_ONLY` 追完 n1335 至最新后，带 PMCID 的文献占比 ≥ 40%"。
  - D3 的"另导入较早 baseline 文件"补一句"并用 `fromFile` 重放增量同步"。
  - D7 改为"覆盖更新不冲掉别的流程的数据：集成测试证明 `is_oa`、`citation_count`、`has_full_text`、PMC OA 位置、非 PubMed 标识符、全文记录在覆盖后保留，内部 ID 不变"。
- 规划时数据现状一节补一句：根因已核实，改用 updatefiles。

**Linear**

- PAP-84：标题改为 `[BE] PubMed updatefiles 增量同步`；Overview / To-Do / Acceptance Criteria 按本文第 4 节重写。
- PAP-85：To-Do 去掉复查任务，加"暂不可用的再查"与"改写 `ofPmc`"；候选来源改为"有 PMCID 且没有全文记录"；AC 中 D7 一条改为"暂不可用的到期再查、撤稿不再查"。
- PAP-88：To-Do 加"扩充 MathML 白名单（清单由 PAP-86 提供）"。
- Project P-PAP-8 描述：不做的事、关键决策 A / D / E / H、Done D2 / D3 / D7 同步上面的改动。

## 附录 B：审阅中已定的问题

1. **`last_synced_at` 随覆盖更新**（2026-10-19 与用户讨论）：门户对它的用法是"最近更新"排序与"最近同步"日期，本来就是同步时间的含义；不更新则永远不动、名不副实。见第 4.3 节。
2. **设计简报附录 A 的五条补充**（2026-10-19 用户同意收入）：详情接口加 `pdfSizeBytes`（第 7.2 节）；有 PDF 无正文时全文接口返回 200、`body` 为空，署名照常给（第 7.3 节）；图块带像素宽高，抓取时记入 `media` 清单，解析与图片接口都改为查清单（第 3.2、5.1、6.3、7.5 节）；只有 TeX 的公式去掉 `documentclass` 外壳（第 6.2 节）；字数不计公式、表格单元格与代码块（第 6.4 节）。核对时另发现正文图片多为 `.webp`，下载范围补上（第 5.1 节）。
3. **开工前预检对真实 JATS 的修正**（2026-10-19，我定的）：`<p>` 内嵌块级元素时切开段落；任何位置的 MathML 都输出 `math`；`citation-alternatives`；公式取值顺序与公式图片；顶层 `blocks` 与 `sub-article` 排除；`xref` 类型白名单与降级；`box` 带 `id`。见第 6.2、6.3 节。PDF / 图片改为 `InputStreamResource` 并关闭 OSIV，见第 7.4 节。
