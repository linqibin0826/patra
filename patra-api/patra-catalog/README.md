# patra-catalog 目录管理服务

## 📋 概述

patra-catalog 是 Patra 医学文献数据平台的目录管理微服务，负责管理医学文献的分类体系、主题词表、作者、机构、期刊等核心目录数据。本服务采用六边形架构设计，遵循 DDD 战术设计原则。

主要职责：
- 管理 MeSH（Medical Subject Headings）医学主题词表数据模型
- 维护期刊（Venue）目录和影响因子
- 管理作者（Author）和机构（Organization）信息
- 提供文献分类和标引服务
- PubMed 文献导入（baseline + updatefiles 一条可重放的流水线）：让文献记录跟上 PubMed（PMCID、撤稿、勘误、删除），任何文件可随时重放、不会回退
- PMC 开放获取全文的抓取、JATS 解析入库，以及门户的全文、PDF、图片读端点

## 🏗️ 架构位置

在 Patra 微服务架构中的位置：
- **外部数据源**：NLM（PubMed baseline / updatefiles、MeSH）、PMC Cloud Service（公开 S3 桶 `pmc-oa-opendata`）、OpenAlex、Scopus、LetPub
- **调用方**：patra-gateway 转发的门户（patra-portal）读端点 `/portal/**`；patra-identity 经 HTTP Interface 直连（`lb://patra-catalog`）的内部接口 `/_internal/**`（保存资料时校验 ROR ID / MeSH UI 并取名称快照）；patra-interaction 同样直连内部接口，按内部 ID 或 PMID 批量解析文献、按期刊 ID 或 NLM ID 批量解析期刊（对象登记表首次登记与每日对账）
- **依赖服务**：patra-registry（解析国家、语言等数据字典）；MinIO（期刊封面、全文 XML / PDF / 图片）

## 🗂️ 分包

模块按层（domain / app / infra / adapter / api / boot），模块内先按子域、再按组件类型：`dev.patra.catalog.{层}.{子域}.{组件}`。模板见 `.claude/rules/tech/package-layout.md`，由 `CatalogArchitectureTest` 的子域规则与六边形规则在 `check` 里强制。

| 子域 | 内容 |
|---|---|
| `kernel`（底座） | 异常基类、数据源与字典枚举、ORCID、书目值对象（出版日期、页码）、`VenueId`、全文状态与可读判定、文件下载、运行锁、字典解析、StAX 解析基座、LIKE 转义 |
| `publication` | 文献主数据、PubMed 同步与重解析、按 PMID / ID 解析、语言查找、期刊卷期实例 |
| `venue` | 期刊主数据：NLM LSIOU 导入、LetPub / Scopus / OpenAlex 富化、封面、解析 |
| `mesh` | MeSH 主题词、限定词、SCR；名称 → UI 查询 |
| `organization` | ROR 导入、建议、资助机构查找 |
| `author` | computed author 导入；ORCID → 作者 ID |
| `fulltext` | PMC 抓取、JATS 解析；全文正文、PDF、图片端点 |
| `portal` | 门户读子域：feed、检索与 facets、详情、卡片、期刊浏览、引文格式化与导出；读适配器用 `JdbcClient` 写 SQL |

子域之间：`publication → venue / organization / author`、`venue → mesh` 是依赖边（只经对方 `domain.{子域}.{port.read, model.read, event}`）；`publication → fulltext` 是订阅边（全文抓取后发 `PmcFullTextFetchedEvent`，文献子域同步监听、同一事务标记开放获取）。底座对所有子域公开；`portal` 只依赖底座。

## 🔧 主要功能

### 已实现功能
1. **文献目录管理**
   - 文献（Publication）基本信息管理
   - 作者（Author）信息维护
   - 机构（Organization）数据管理
   - 期刊（Venue）目录维护

2. **MeSH 数据模型**
   - 主题词（Descriptor）领域模型
   - 限定词（Qualifier）领域模型
   - 补充概念记录（SCR）领域模型
   - 树形编号（TreeNumber）层次结构
   - 入口术语（EntryTerm）同义词
   - 概念（Concept）关系

3. **MeSH 数据批量导入**（Spring Batch 实现）
   - 使用 Spring Batch 实现大规模数据导入（约 35,000 条主题词）
   - **临时文件下载**：从 NLM 官方服务器下载 XML 到临时目录，从本地文件解析（解耦下载与处理）
   - **配置驱动**：URL 从配置文件读取，版本号从文件名自动推断（如 `desc2026.xml` → `2026`）
   - 支持断点续传（Job 级别幂等执行，失败时需重新下载）
   - **一次性初始化语义**：表中有数据时拒绝导入，需手动清空后重新执行
   - 批量 SQL 插入优化（单条 SQL 多行值）
   - XXL-Job 调度入口（`meshDescriptorImportJob`）

4. **MeSH 限定词导入**
   - 使用 StAX 流式解析 XML（约 80 条限定词）
   - **配置驱动**：URL 从配置文件读取，版本号从文件名自动推断（如 `qual2026.xml` → `2026`）
   - XXL-Job 调度入口（`meshQualifierImportJob`）
   - **一次性初始化语义**：表中有数据时拒绝导入，需手动清空后重新执行

5. **MeSH SCR（补充概念记录）导入**（Spring Batch 实现）
   - 使用 Spring Batch 实现大规模 SCR 数据导入（约 350,000 条）
   - **临时文件下载**：从 NLM 官方服务器下载 XML 到临时目录，从本地文件解析（解耦下载与处理）
   - **配置驱动**：URL 从配置文件读取，版本号从文件名自动推断（如 `supp2026.xml` → `2026`）
   - 批量 SQL 插入优化（单条 SQL 多行值）
   - XXL-Job 调度入口（`meshScrImportJob`）
   - **一次性初始化语义**：表中有数据时拒绝导入，需手动清空后重新执行

6. **ROR 机构数据批量导入**（Spring Batch 实现）
   - **临时文件下载**：从 ROR Data Dump ZIP 下载到临时目录，从本地文件解压并解析
   - **一次性初始化语义**：表中有数据时拒绝导入，需手动清空后重新执行
   - 支持断点续传（Job 级别幂等执行，失败时需重新下载）
   - Job 入口：`rorOrganizationImportJob`（参数：`downloadUrl`、`rorVersion`）

7. **PubMed Venue 数据导入**
   - 数据来源：NLM LSIOU（List of Serials Indexed for Online Users），约 15,000 条 MEDLINE 索引期刊
   - 增量覆盖模式：NLM ID → ISSN-L → ISSN 三级降级匹配策略（NLM 与 ISSN-L 指向不同期刊时 NLM 胜出）
   - NLM 身份：NLM ID 全局唯一（主 NLM 与副 NLM 都只属于一个期刊，V12 两条唯一约束）；期刊的主 NLM 第一次写入后不再改，之后匹配上的其他 NLM 是副 NLM；NLM 属于已软删期刊的记录跳过
   - 批内去重：NLM ID + ISSN-L 双维度防重复创建
   - 已删除 Serial 过滤：跳过 `deletedTimestamp` 非空的记录
   - **OpenAlex 富化**：通过 REST API 查询 OpenAlex Sources，按 ISSN-L 批量获取期刊引用指标（H 指数、i10 指数、两年平均被引）、年度发文统计及开放获取信息（OA 状态、DOAJ 收录、APC 定价）（`OpenAlexEnrichmentQueryPort` → `OpenAlexEnrichmentQueryAdapter`）
   - 通用评级表设计：支持 JCR、中科院分区、Scopus CiteScore 等多评价体系
   - 数据源溯源：记录各数据源原始 JSON 和提取字段
   - 多标识符匹配：ISSN-L、NLM ID、ISSN 组合匹配

8. **LetPub 期刊评价数据富化**（Worker Loop + XXL-Job 调度）
   - 数据来源：LetPub 期刊详情页（Apache HttpClient 5 走青果隧道代理抓取 HTML，Jsoup + 正则解析）
   - **候选筛选**：按 `targetYear + minCitedByCount` 从 `cat_venue` 捞取 `cat_venue_jcr_rating` 中尚未填充 `targetYear` 的 venue
   - **Worker Loop 架构**：`LetPubEnrichmentRunner`（协调器 + 游标分页 + 合作式中断）→ `LetPubEnrichmentWorker`（单 venue 处理单元）→ `LetPubEnrichmentPersister`（事务边界收紧到 bean，HTTP 调用在事务外执行）
   - **数据映射**：`LetPubDataMapper` 将 `LetPubVenueData` 展开为 JCR 评级（10 年 IF 趋势 + 最新年分区详情）、CAS 评级（多版本分区：升级版/新锐版/基础版）、CAS 预警三组强类型实体
   - **机会主义填充**：单次调用 `targetYear=2025` 可顺带填充 2016-2024 的历史年份（Persister 按 `(venue_id, year[:edition])` 过滤，仅插入缺失年份）
   - **封面同步下载**：同步下载 LetPub 封面图并上传对象存储，`cat_venue.image_object_key` 以 UPDATE 方式回写
   - **跨 Job 自愈**：失败 venue 在下次调度时被候选查询重新捞起（`lastId=0` 重置游标）
   - XXL-Job 调度入口（`venueLetPubEnrichJob`，参数：`targetYear[,minCitedByCount]`）

9. **Scopus 引用指标富化**（Worker Loop + XXL-Job 调度）
   - 数据来源：Elsevier Scopus Serial Title API（`SCOPUS_API_KEY` 认证）
   - **限速策略**：Scopus API 限速 2-3 次/秒，`ScopusEnrichmentRunner` 在两次 venue 处理间主动等待 400ms
   - **Worker Loop 架构**：与 LetPub 结构对称，`ScopusEnrichmentRunner` + `ScopusEnrichmentWorker` + `ScopusEnrichmentPersister`
   - **数据映射**：`ScopusDataMapper` 为最新年份填充 SJR / SNIP / CiteScoreTracker / 分区 / 百分位全量字段，历史年份仅填 CiteScore + 发文/引用/被引率基础字段
   - **幂等去重**：Persister 按 `(venue_id, year)` 过滤已存在行，跨次调用不会重复插入同一年份
   - XXL-Job 调度入口（`venueScopusEnrichJob`，参数：`targetYear[,minCitedByCount]`）

10. **PubMed 文献导入**（Spring Batch，baseline 与 updatefiles 共用一条流水线，PAP-110）
   - **一条链路**：下载 `.xml.gz` 并按 NCBI 的 `.md5` 旁文件校验（不符重试，共 3 次）→ 逐篇切出原始 `<PubmedArticle>` → 解析 → 来源位置守卫 → 组装 → 新增 / 覆盖文献并 upsert 来源表 → 最后处理 `DeleteCitation`
   - **来源位置守卫**：文件名 `pubmed{yy}n{seq}.xml.gz` 换算成 `rank = yy × 10000 + seq`，记在来源表 `cat_publication_source`（每个 PMID 一行，含逐篇原文 `raw_xml`，lz4 压缩）。比库里旧的记录跳过（计 `skipped_stale`）、相同或更新的覆盖；软删与墓碑只被更新的文件覆盖；`DeleteCitation` 只删 rank 不大于删除文件的文献；`ALL` 范围删除库里没有的 PMID 时写墓碑，防止重放更早的 baseline 把它加回来；`last_synced_at` 只在 rank 前进时刷新
   - **覆盖更新**：内部 ID 不变；PubMed 管的列与子表全部替换；`is_oa`、`citation_count`、`has_full_text`、非 PubMed 标识符、OA 位置、全文记录等别的流程写入的数据保留；新 DOI 属于别的文献时保留旧 DOI
   - **范围**：`EXISTING_ONLY`（开发环境默认）只覆盖库里已有的，`ALL` 也新增；配置 `patra.catalog.pubmed.scope`
   - **坏记录隔离**：单篇解析或校验失败（年份不在 1800 ~ 2100、缺标题、缺 PMID 等）跳过并计 `skipped_invalid`，文件照常完成；数据库等基础设施错误仍让文件失败
   - **日期按实际精度**：`year` / `month` / `day` 三种精度，不补月日；日对该月不合法（2 月 30 日）降为月精度，月份非法降为年精度，季节原文另存；门户与解析接口输出 `yyyy` / `yyyy-MM` / `yyyy-MM-dd`
   - **引用字段**：页码（`MedlinePgn` / `StartPage` / `EndPage`）、出版月日、期刊 ISO 缩写进 `cat_publication`；PII 进标识符表
   - **进度表** `cat_pubmed_file`（`kind` = `BASELINE` / `UPDATE`）：每个文件一行，记状态、范围、作业 generation 与各类计数；UPDATE 无参数时从最小的未完成 UPDATE 文件续跑（没有则最后一个成功文件的下一个），只看 UPDATE 行；失败即停、下次续跑同一个作业实例；`reprocess=true` 重放已完成的文件时换新的 generation
   - **本地重算**（`pubmedReparseJob`）：不联网，从来源表读原文按 PMID 键集分页重新解析覆盖，用于解析器升级后回填；只处理未软删的文献，rank 不变、`last_synced_at` 不变
   - 导入与本地重算共用一把任务锁（`catalog.pubmed-import`），另一个实例在跑时本次跳过并写明
   - **组装沿用的能力**：`VenueLookupPort` 双索引缓存（NLM ID + ISSN）、ISO 639-3 → BCP 47 语言转换、资助机构匹配、按卷期创建或复用期刊实例、补充 MeSH 概念、翻译摘要、标识符、结构化摘要、非作者研究人员、人物主题、关键词（多 Owner 分组）；标题优先 `ArticleTitle`，为空回退 `VernacularTitle`
   - XXL-Job 调度入口：`pubmedImportJob`（参数均可省：`kind=BASELINE|UPDATE,from=N,to=M,maxFiles=K,scope=EXISTING_ONLY|ALL,reprocess=true|false`；无参数 = 每日增量；`kind=BASELINE` 必须给 `from`）、`pubmedReparseJob`（无参数）
   - 设计与回填 runbook：[文献引用数据、可重放导入与导出](../../docs/patra/specs/2026-10-24-catalog-citation-data-export-design.md)（第 5 节流水线、第 9 节 mini 回填步骤）

11. **PMC 全文抓取与 JATS 解析**（Runner / Worker / Persister）
   - 抓取：从 PMC Cloud Service 选版本（开放获取、优先非手稿），下载 XML、PDF、正文图片（含 webp，读像素宽高）写入 MinIO；暂不可用的按放开日期或退避再查
   - 解析：JDK DOM 解析 `<body>` / `<back>` 为 `FullTextBody`（JSONB），只做格式转换不改写内容；`PARSER_VERSION` 提升后全量重新解析，不访问 PMC
   - 三个任务用父子任务串起来，PG advisory lock 防同类任务并发
   - XXL-Job 调度入口（`pmcFullTextFetchJob`、`jatsFullTextParseJob`）

12. **门户全文读端点**
   - 详情接口的 `retracted`、`hasFullText`、`hasPdf`、`pdfSizeBytes`、`estimatedReadMin`
   - `GET /portal/publications/{id}/fulltext`（正文、许可、署名）、`/fulltext/pdf`、`/fulltext/figures/{fileName}`
   - 撤稿文献（发表类型 `D016441`，或抓取时 PMC 标为撤稿）在读端自动下线全文

13. **机构（ROR）与 MeSH 的建议接口 + 内部查询接口**（v0.12 用户中心，PAP-94）
   - 门户：`GET /portal/organizations/suggest?q=&limit=`、`GET /portal/mesh/suggest?q=&limit=`，响应 `{ "items": [...] }`；`q` 归一（NFKC、去空白）后 2 ~ 100 字，`limit` 1 ~ 20 缺省 10，非法 422
   - 机构按名称、中文名、别名、缩写搜：含中日韩文字走任意位置子串，拉丁文字只做前缀与词首；排序 = 命中方式（精确 > 前缀 > 词首 > 子串）→ 中国优先 → 医疗机构优先 → 名称类型 → 短名优先；只给 `active` 且未软删的机构；`zhName` 取 Patra 别名表的 LABEL > ROR `label` > ROR `alias`
   - MeSH 按主题词名与非排列入口词搜（前缀 / 词首），精确命中排最前，经入口词命中的返回主题词并带 `matchedTerm`；只给主题类（class 1）且 active 的主题词；`treeNumbers` 主树号在前
   - 内部（给 identity，网关对外 403）：`GET /_internal/organizations/{rorId}`、`GET /_internal/mesh/descriptors/{ui}`，契约在 `patra-catalog-api` 的 `OrganizationEndpoint` / `MeshEndpoint`（`@HttpExchange`），ID 格式不对、不存在、机构非 active 都 404
   - Patra 自维护的机构别名表 `cat_organization_alias` 补 ROR 缺的中文名与简称，种子在可重复迁移 `R__cat_organization_alias_seed.sql`（手工三甲清单 + Wikidata zh 标签），改文件即改数据
   - 设计：[机构与 MeSH 建议接口](../../docs/patra/specs/2026-10-21-catalog-org-mesh-suggest-design.md)
14. **文献批量解析与批量卡片**（v0.13 文献库，PAP-109）
   - 内部（给 interaction，网关对外 403）：`POST /_internal/publications/resolve/by-ids`、`POST /_internal/publications/resolve/by-pmids`，请求体 1 ~ 100 个键；每个请求键一行（按请求顺序、去重），软删的文献照样返回 `state = DELETED`，查不到的 `exists = false`；契约在 `patra-catalog-api` 的 `PublicationEndpoint`
   - 门户：`GET /portal/publications/batch?id=…&id=…`（1 ~ 100），响应 `{ "items": [...] }`，元素与 feed / 检索列表项同形；不存在与软删的 ID 不返回，按请求顺序；整批一条 SQL
   - 按主键与 `uk_pmid` 命中，不加索引；门户响应不再有写死为 0 的 `bookmarks`
   - 设计：[文献批量解析与批量卡片](../../docs/patra/specs/2026-10-24-catalog-publication-resolve-cards-design.md)

15. **引用导出与复制引用**（v0.13 文献库，PAP-110 计划二）
   - 门户：`POST /portal/publications/export`，请求体 `{ "ids": [...], "format": "ris|bibtex|csv|csl-json", "name": "…" }`（1 ~ 5,000 个 ID，`name` ≤ 100 字）；附件流式写出，文件名 `patra-<name>-<YYYY-MM-DD>.<ext>`（北京时间当天，RFC 5987 `filename*`）；不存在与软删的跳过，按请求顺序去重
   - 门户：`POST /portal/publications/citations`，请求体 `{ "ids": [...], "style": "gb-t-7714-2015|vancouver|apa-7|ama-11" }`（1 ~ 100 个）；响应 `{ "style", "entries": [{ "label", "text" }] }`，序号与正文分开；编号样式按请求顺序，APA 按作者字母序、`label` 为 null
   - 读端 `CitationRecordReadPort`：每批 ≤ 500 个 ID、两条 SQL（主行 + 作者）；标题去内联标记，期刊缩写按 ISO → MedlineTA → 全称兜底，文章号只在没有页码时取 PII
   - 导出服务按 500 个一批读 → 写 → 丢弃，每批一个只读事务（响应体在控制器返回后执行）；写出器有状态（CSV 的 BOM 与表头、JSON 数组括号）。RIS 与 CSV 行尾 CRLF，CSV 带 BOM 便于 Excel 打开中文；日期按实际精度（RIS `DA` 留空、BibTeX 只到年时不写 `month`、CSL-JSON `date-parts` 按精度）
   - 引用用 [citeproc-java](https://github.com/michel-kraemer/citeproc-java)（Apache-2.0）渲染官方 [CSL 样式库](https://github.com/citation-style-language/styles)与 [locales](https://github.com/citation-style-language/locales)（CC BY-SA 3.0，经 `org.citationstyles:styles` / `locales` jar 引入）；四种样式对应 `china-national-standard-gb-t-7714-2015-numeric`、`nlm-citation-sequence`、`apa`、`american-medical-association`，全部 `en-US`（GB/T 西文文献写 et al.）
   - 设计：[文献引用数据、可重放导入与导出](../../docs/patra/specs/2026-10-24-catalog-citation-data-export-design.md)（第 6、7 节）

16. **期刊身份与批量接口**（v0.14 讨论，PAP-117）
   - 内部（给 interaction，网关对外 403）：`POST /_internal/venues/resolve/by-ids`、`POST /_internal/venues/resolve/by-nlm-ids`，请求体 1 ~ 100 个键；每个请求键一行（按请求顺序、去重），软删的期刊照样返回 `state = DELETED`，查不到的与非期刊类型 `exists = false`；按 NLM 时主、副 NLM 都能命中，返回的 `nlmId` 总是主 NLM；契约在 `patra-catalog-api` 的 `VenueEndpoint`
   - NLM ID 一律按字符串处理、保留前导零；写法 `^[0-9]{1,19}[A-Z]?$`（有 `R` / `A` 后缀与 16 位新式 ID），区分大小写
   - 门户：`GET /portal/venues/batch?id=…&id=…`（1 ~ 100），响应 `{ "items": [...] }`，元素与期刊浏览列表项同形；不存在、软删、非期刊的不返回，按请求顺序；期刊卡片（浏览与批量）带 `nlmId`
   - 设计与上线步骤：[期刊身份与批量接口](../../docs/patra/specs/2026-10-27-catalog-venue-identity-design.md)（第 11 节）

运维步骤见 [v0.11 运维手册](../../docs/patra/runbooks/v0.11-fulltext-runbook.md)。

### 计划功能
- MeSH 年度版本更新（需先手动清空旧数据）
- 作者消歧和合并
- 机构标准化和映射

## 📦 模块依赖

### 内部依赖
- `patra-tool-core`：DDD 基类与错误类型
- `patra-spring-boot-starter-jpa`：数据访问层支持
- `patra-spring-boot-starter-batch`：批处理支持
- `patra-spring-boot-starter-rest-client`：HTTP 客户端支持（文件下载）
- `patra-spring-boot-starter-core`：核心基础设施
- `patra-spring-boot-starter-http-interface`：HTTP Interface 远程调用支持
- `patra-registry-api`：Registry 服务契约（DictionaryEndpoint 字典解析）

### 外部依赖
- Spring Boot 4.0.6
- Spring Cloud 2025.1.1
- Spring Data JPA + Hibernate 7.1
- Nacos（服务发现）

## ⚙️ 配置说明

配置从 Nacos 读（`patra-common.yaml`、`patra-catalog.yaml`，源文件在 `patra-infra/nacos/dev/`）：库地址、批处理库、Redis 密码引用、青果代理开关（dev 关闭）和外部凭据引用都在那里。外部凭据（`SCOPUS_API_KEY`、`PROXY_AUTH_KEY`、`PROXY_AUTH_PWD`）的值放在 `~/.patra/secrets/catalog.env`，没配时服务照常启动，用到对应数据源时才失败。下面各数据源的 URL 等随代码变的默认值在 jar 的 `application.yml`。

### MeSH 数据源配置

```yaml
patra:
  catalog:
    mesh:
      descriptor-url: https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2026.xml
      qualifier-url: https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/qual2026.xml
      scr-url: https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/supp2026.xml
```

| 配置项 | 说明 | 示例 |
|--------|------|------|
| `descriptor-url` | MeSH 主题词 XML 文件 URL | `desc2026.xml` |
| `qualifier-url` | MeSH 限定词 XML 文件 URL | `qual2026.xml` |
| `scr-url` | MeSH 补充概念记录 XML 文件 URL | `supp2026.xml` |

**版本号推断规则**：从文件名自动提取 4 位年份，支持格式：
- `desc{year}.xml`（如 `desc2026.xml` → 版本 `2026`）
- `qual{year}.xml`（如 `qual2026.xml` → 版本 `2026`）
- `supp{year}.xml`（如 `supp2026.xml` → 版本 `2026`）

### LSIOU 数据源配置

```yaml
patra:
  catalog:
    lsiou:
      url: ftp://ftp.nlm.nih.gov/online/journals/lsi2024.xml
```

| 配置项 | 说明 | 示例 |
|--------|------|------|
| `url` | NLM LSIOU XML 文件 URL（支持 FTP/HTTP/HTTPS） | `lsi2024.xml` |

**版本号推断规则**：从文件名自动提取 4 位年份，支持格式：
- `lsi{year}.xml`（如 `lsi2024.xml` → 版本 `2024`）

**回退规则**：
- 若主目录文件不存在，将自动回退到 `ftp://ftp.nlm.nih.gov/online/journals/archive`

**LSIOU vs SerfileBase**：
- LSIOU（List of Serials Indexed for Online Users）：约 15,000 条 MEDLINE 索引期刊
- SerfileBase：约 35,000 条 NLM Catalog 完整记录（包含未被 MEDLINE 索引的期刊）

### ROR 数据源配置

```yaml
patra:
  catalog:
    ror:
      download-url: https://zenodo.org/records/17953395/files/v2.0-2025-12-16-ror-data.zip?download=1
```

| 配置项 | 说明 | 示例 |
|--------|------|------|
| `download-url` | ROR Data Dump ZIP 文件下载 URL | `v2.0-2025-12-16-ror-data.zip` |

**版本号推断规则**：从文件名自动提取版本号，支持格式：
- `v{version}-{date}-ror-data.zip`（如 `v2.0-2025-12-16-ror-data.zip` → 版本 `v2.0`）

**数据源说明**：
- ROR 数据发布在 Zenodo，每个版本有独立的下载链接
- 最新版本下载地址：<https://ror.readme.io/docs/data-dump>
- ZIP 文件约 60MB（压缩），包含约 120,000 条机构记录
- 从 v2.0 开始仅提供 schema v2 格式

## 🎯 核心类说明

### Domain 层
- **聚合根**
  - `MeshDescriptorAggregate`：MeSH 主题词聚合根
  - `MeshQualifierAggregate`：MeSH 限定词聚合根
  - `MeshScrAggregate`：MeSH 补充概念记录聚合根
  - `VenueAggregate`：载体（期刊/仓储/会议）聚合根
  - `AuthorAggregate`：作者聚合根（含名字变体、ORCID 子实体）
  - `OrganizationAggregate`：机构聚合根（基于 ROR Schema v2.0）
  - `PublicationAggregate`：文献聚合根

- **实体**
  - `MeshConcept`：MeSH 概念实体
  - `MeshEntryTerm`：MeSH 入口术语实体
  - `MeshTreeNumber`：MeSH 树形编号实体

- **Record 值对象**（不可变，通过 `VenueRepository` 统一管理）
  - `VenueSourceData`：载体数据源溯源（存储各数据源原始/提取数据）
  - `VenueIdentifier`：载体标识符（ISSN/OpenAlex ID/NLM ID 等，保留在聚合内）
  - `VenuePublicationStats`：载体年度发文统计（发表量/被引量/OA 比例）
  - `VenueMesh`：载体 MeSH 主题词（MeSH 主题分类，来源 Serfile）
  - `VenueRelation`：载体关联关系（期刊演变关系：前刊/后刊/合并/分拆）
  - `VenueIndexingHistory`：载体索引历史（MEDLINE/PMC 索引收录变迁）

- **读模型**（CQRS 读端查询投影）
  - `OrganizationSummary` / `OrganizationSuggestion`：机构摘要（ROR ID、显示名、中文名、国家、城市、类型）与建议候选（摘要 + 命中的名称）
  - `MeshDescriptorSummary` / `MeshDescriptorSuggestion`：MeSH 主题词摘要（UI、英文名、树号）与建议候选（摘要 + 命中的入口词）
  - `SuggestQuery`（`domain/kernel/model/vo/`）：归一化、已验证的建议查询（文本、`MatchMode`、limit）
  - `VenueSummaryReadModel`：期刊列表摘要读模型
  - `VenueDetailReadModel`：期刊详情读模型（`@Builder`，17 字段）
  - `VenueFilter`：期刊筛选条件（`@Builder`，支持 keyword/countryCode/issnL/nlmId 独立筛选）
  - `PublicationSummaryReadModel`：文献列表摘要读模型
  - `PublicationDetailReadModel`：文献详情读模型（`@Builder`，24 字段，含嵌套 record：AbstractInfo、IdentifierInfo、KeywordInfo、MeshHeadingInfo）
  - `PublicationFilter`：文献筛选条件（`@Builder`，支持 keyword/yearFrom/yearTo/languageBase/isOa/oaStatus/venueId/pmid/doi/provenanceCode/publicationStatus 独立筛选）

- **领域异常**
  - `VenueNotFoundException`：期刊不存在异常（携带 `StandardErrorTrait.NOT_FOUND`，自动映射为 HTTP 404）
  - `PublicationNotFoundException`：文献不存在异常（携带 `StandardErrorTrait.NOT_FOUND`，自动映射为 HTTP 404）

- **嵌入式值对象**（JSON 存储，随聚合根一起保存）
  - `PublicationProfile`：出版概况（缩写标题、备选标题、宿主机构、国家代码）
  - `CitationMetrics`：引用指标（作品数、被引数、H 指数、i10 指数、两年平均被引）
  - `OpenAccessInfo`：开放获取信息（OA 状态 + APC 定价合并）
  - `Society`：关联学会（学会名称、URL）

- **查询传输值对象**（非持久化，用于跨端口传递查询结果）
  - `VenueOpenAlexEnrichment`：OpenAlex 富化数据（OpenAlex Source ID、引用指标快照、年度发文统计、开放获取信息）

- **聚合根 ID 值对象**（编译时类型安全，防止 ID 混淆）
  - `VenueId`：载体聚合根 ID
  - `VenueInstanceId`：载体实例聚合根 ID
  - `MeshDescriptorId`：MeSH 主题词聚合根 ID
  - `MeshQualifierId`：MeSH 限定词聚合根 ID
  - `MeshScrId`：MeSH 补充概念记录聚合根 ID
  - `OrganizationId`：机构聚合根 ID
  - `AuthorId`：作者聚合根 ID
  - `PublicationId`：出版物聚合根 ID

- **值对象（author 子包）**：作者领域模型（适配 PubMed Computed Authors）
  - `AuthorNameVariant`：作者名字变体（姓/名/缩写/原始字符串）
  - `Orcid`：ORCID 标识符值对象（格式：0000-0001-2345-6789）
  - `AffiliationIdentifiers`：机构标识符集合（ROR ID / Ringgold ID / GRID ID，支持消歧优先级选择）

- **值对象**
  - `MeshUI`：MeSH 唯一标识符（支持 Descriptor/Qualifier/SCR/Concept/Term）
  - `EntryCombination`：MeSH 组合条目（ECIN/ECOUT 映射）
  - `AllowableQualifier`：允许的限定词
  - `PharmacologicalAction`：药理作用
  - `SeeRelatedDescriptor`：相关主题词引用
  - `ConceptRelation`：概念关系
  - `HeadingMappedTo`：SCR 到 Descriptor 的映射关系（含 majorTopic 标记，NLM 用 `*` 前缀表示主要主题词）
  - `ScrSource`：SCR 数据来源
  - `IndexingInfo`：SCR 索引信息（mesh 包）
  - `HostOrganization`：主办机构信息
  - `ProvenanceInfo`：数据来源信息
  - `PublicationHistory`：出版历史（创刊/停刊年份）
  - `IndexingInfo`：MEDLINE 索引收录信息（venue 包）
  - `OaStatus`：开放获取状态
  - `VenueLanguages`：期刊语言信息（主要语言 + 摘要语言列表，来源 Serfile）

- **值对象（organization 子包）**：基于 ROR Schema v2.0 的机构领域模型
  - `RorId`：ROR 标识符值对象（支持完整 URL 或纯 ID 解析，9 位字符格式）
  - `OrganizationId`：机构聚合根内部 ID（数据库主键包装）
  - `OrganizationName`：机构名称（含类型集合：ror_display/label/alias/acronym，支持多语言）
  - `ExternalId`：外部标识符（GRID/ISNI/Wikidata/FundRef/Ringgold，含首选值和全部值）
  - `GeoLocation`：地理位置（基于 GeoNames，含洲/国家/省/市/经纬度）
  - `OrganizationRelation`：机构关系（层级：parent/child，关联：related，时序：successor/predecessor）
  - `OrganizationLink`：机构链接（官网/维基百科，支持语言标记）
  - `AdminInfo`：管理信息（ROR 创建/更新日期和版本号）

- **值对象（publication 子包）**：文献批量导入关联数据的值对象（`PublicationRepository.insertAllWithAssociations()` 使用）
  - `PublicationCompleteData`：完整文献数据封装（聚合根 + 所有关联数据），Repository 批量写入接口的参数类型
  - `PublicationMeshHeading`：文献 MeSH 标引值对象（含 Descriptor UI、主题标记、顺序）
  - `MeshQualifier`：MeSH 限定词值对象（嵌套在 MeshHeading 中）
  - `PublicationKeyword`：文献关键词值对象（来源、主/副标记、顺序）
  - `PublicationFunding`：文献资助信息值对象（资助机构、项目编号、数据来源）
  - `PublicationTypeInfo`：文献出版类型值对象（类型 ID、类型值、词汇来源）
  - `PublicationSupplMesh`：文献补充 MeSH 概念值对象（SCR UI、顺序）
  - `PublicationInvestigator`：文献研究者值对象（姓名、ORCID、机构、dedupKey）
  - `PublicationPersonalNameSubject`：文献人物主题值对象（传记类/历史类文献的主题人物）

- **值对象（venue/pubmed 子包）**：NLM LSIOU 解析结果的完整领域模型（解析器直接产出）
  - `PubmedSerialData`：PubMed 期刊解析数据（完整版，44 字段，解析器直接产出 Domain 模型）
  - `PubmedLanguage`：PubMed 期刊语言信息（主要/次要语言）
  - `PubmedMeshHeading`：PubMed 期刊 MeSH 主题词（含限定符、UI、类型）
  - `PubmedTitleRelation`：PubMed 期刊关联关系（前刊/后刊，含 ISSN 和 RecordIds）
  - `PubmedIndexingHistory`：PubMed 期刊索引历史（含日期、覆盖范围、索引状态）
  - `PubmedCurrentIndexing`：PubMed 期刊当前索引子集信息（含子集内容）
  - `PubmedBroadHeading`：PubMed 广泛期刊分类
  - `PubmedCrossReference`：PubMed 交叉引用（XrType: A/X/S）
  - `PubmedGeneralNote`：PubMed 通用备注（含 NoteType 属性）
  - `PubmedRecordId`：PubMed 记录 ID（含 Source 属性：NLM/LC/OCLC）

- **枚举**
  - `MeshDataType`：MeSH 数据类型（QUALIFIER、DESCRIPTOR、TREE_NUMBER、ENTRY_TERM、CONCEPT）
  - `DescriptorClass`：主题词分类
  - `ScrClass`：SCR 分类（1-6 六种类型：化学物质、化疗方案、疾病、生物体、人群组、解剖结构）
  - `MeshRecordType`：MeSH 记录类型（DESCRIPTOR、SCR，用于共享表区分记录类型）
  - `LexicalTag`：词汇标记
  - `VenueType`：载体类型（JOURNAL、REPOSITORY、CONFERENCE、EBOOK_PLATFORM、BOOK_SERIES、METADATA、IGSN_CATALOG、RAID_REGISTRY、OTHER）
  - `VenueIdentifierType`：标识符类型（ISSN、OPENALEX、NLM、MAG、FATCAT、WIKIDATA、DOAJ、CROSSREF_MEMBER、JCR、CODEN）
  - `DataSourceCode`：数据源代码（OPENALEX、PUBMED、DOAJ、CROSSREF、JCR）
  - `VenueRelationType`：载体关联类型（PRECEDING、PRECEDING_IN_PART、SUCCEEDING、SUCCEEDING_IN_PART、ABSORBED、ABSORBED_BY、ABSORBED_IN_PART、ABSORBED_IN_PART_BY、MERGED_TO、MERGER_OF、SPLIT_FROM、SPLIT_TO、SUPERSEDES、SUPERSEDED_BY、SUPERSEDES_IN_PART、SUPERSEDED_IN_PART_BY、ANALYTIC、RELATED、REVERSION、SERIES、SERIES_AUTHORITY、TRANSLATED、OTHER、UNDETERMINED）
  - `CitationSubset`：引用子集（IM、AIM、N、D、H、K、T、E、S、X、B、C、F、Q、J、OM、P、QIS、R）
  - `IndexingTreatment`：索引处理方式（FULL、SELECTIVE、UNKNOWN、REFERENCED_IN、REFERENCED_IN_NO_DETAILS）

- **枚举（organization 子包）**：机构领域枚举
  - `OrganizationType`：机构类型（EDUCATION、HEALTHCARE、COMPANY、GOVERNMENT、NONPROFIT、FACILITY、ARCHIVE、FUNDER、OTHER）
  - `OrganizationStatus`：机构状态（ACTIVE、INACTIVE、WITHDRAWN）
  - `ExternalIdType`：外部标识符类型（GRID、ISNI、WIKIDATA、FUNDREF、RINGGOLD）
  - `LinkType`：链接类型（WEBSITE、WIKIPEDIA）
  - `OrganizationNameType`：名称类型（ROR_DISPLAY、LABEL、ALIAS、ACRONYM）
  - `OrganizationRelationType`：关系类型（PARENT、CHILD、RELATED、SUCCESSOR、PREDECESSOR）

- **枚举（消歧相关）**：机构消歧领域枚举
  - `DisambiguationStatus`：消歧状态（PENDING、MATCHED、UNMATCHED、AMBIGUOUS）
  - `DisambiguationMethod`：消歧方法（ROR_ID、RINGGOLD、GRID、NAME_MATCH、MANUAL）

### Infrastructure 层
- `MeshDescriptorRepositoryAdapter`：主题词仓储适配器
- `MeshQualifierRepositoryAdapter`：限定词仓储适配器
- `MeshScrRepositoryAdapter`：补充概念记录仓储适配器
- `VenueRepositoryAdapter`：载体聚合根仓储适配器（含 identifiers 和补充数据）
- `AuthorRepositoryAdapter`：作者聚合根仓储适配器（含名字变体、ORCID 子实体级联保存）
- `OrganizationRepositoryAdapter`：机构聚合根仓储适配器（支持批量插入/更新，子表变更追踪）
- `PublicationRepositoryAdapter`：文献聚合根仓储适配器（支持批量插入/更新，含 17 种关联数据写入）
- `MeshDescriptorJpaMapper`：主题词 Entity ↔ Aggregate 映射器（MapStruct）
- `MeshQualifierJpaMapper`：限定词 Entity ↔ Aggregate 映射器（MapStruct）
- `MeshScrJpaMapper`：SCR Entity ↔ Aggregate 映射器（MapStruct）
- `VenueJpaMapper`：载体聚合根 Entity ↔ Aggregate 映射器（含快速访问字段提取）
- `OrganizationJpaMapper`：机构聚合根 Entity ↔ Aggregate 映射器（支持 5 张子表双向转换）
- `OpenAlexEnrichmentQueryAdapter`：OpenAlex 富化查询适配器（实现 `OpenAlexEnrichmentQueryPort`，通过 REST API 批量查询引用指标和年度统计）
- `OpenAlexSourcesClient`：OpenAlex Sources API 查询客户端（构建 ISSN 过滤查询、子批次拆分、JSON 解析）
- `LetPubEnrichmentAdapter` / `LetPubScrapingClient` / `LetPubDetailPageParser`：LetPub 外部抓取适配器链（Apache HttpClient 5 + 青果隧道代理 + Jsoup/正则解析详情页 HTML）
- `LetPubEnrichmentPersistAdapter` + `LetPubDataMapper`：LetPub 富化持久化适配器（实现 `LetPubEnrichmentPersistPort`，展开 `LetPubVenueData` 为 JCR/CAS/Warning 多行，按 `(venue_id, year[:edition])` 去重写入三张表 + 封面回写）
- `ScopusEnrichmentAdapter` / `ScopusApiClient`：Scopus 引用指标查询适配器链（Elsevier Scopus Serial Title API，API Key 认证）
- `ScopusEnrichmentPersistAdapter` + `ScopusDataMapper`：Scopus 富化持久化适配器（实现 `ScopusEnrichmentPersistPort`，按 `(venue_id, year)` 去重写入 `cat_venue_scopus_rating`）
- `VenueCoverImageDownloadAdapter`：Venue 封面图下载适配器（下载 LetPub 封面并上传到对象存储，返回 `image_object_key`）
- `FileDownloadAdapter`：文件下载适配器（下载到临时目录，供 Batch ItemReader 使用）
- `DictionaryResolverAdapter`：字典解析适配器（调用 patra-registry 字典服务，支持国家、语言等多种字典类型的批量解析）

- **ReadPort 实现**（CQRS 读端适配器）
  - `VenueReadAdapter`：期刊读适配器（实现 `VenueReadPort`，支持多条件独立筛选分页查询和详情查询）
  - `VenueReadModelMapper`：读模型映射器（MapStruct，Entity → ReadModel 转换）
  - `PublicationReadAdapter`：文献读适配器（实现 `PublicationReadPort`，支持多条件独立筛选分页查询和详情查询，含摘要/标识符/关键词/MeSH 标引关联数据组装）
  - `PublicationReadModelMapper`：文献读模型映射器（MapStruct，Entity → ReadModel 转换）
  - `OrganizationReadAdapter`：机构建议与按 ROR ID 查询（实现 `OrganizationReadPort`；native SQL 在 `OrganizationSuggestSql`，名称表与别名表候选打分、截 500、每家机构只留最好的一条再排序）
  - `MeshDescriptorReadAdapter`：MeSH 建议与按 UI 查询（实现 `MeshDescriptorReadPort`；native SQL 在 `MeshDescriptorSuggestSql`）

- **LookupPort 实现**（装饰器模式，支持批处理缓存优化）
  - `DefaultVenueLookupAdapter` / `CachingVenueLookupDecorator` / `BatchVenueLookupAdapter`：Venue 查找（NLM ID + ISSN 双索引）
  - `DefaultLanguageLookupAdapter` / `CachingLanguageLookupDecorator` / `BatchLanguageLookupAdapter`：语言查找（ISO 639-3 → BCP 47）
  - `DefaultFunderLookupAdapter` / `CachingFunderLookupDecorator` / `BatchFunderLookupAdapter`：资助机构查找（FundRef ID + ROR ID）

## 📊 数据模型

### 核心数据表
- **MeSH 相关表**
  - `cat_mesh_descriptor`：主题词表（~35,000 条）
  - `cat_mesh_qualifier`：限定词表（~100 条）
  - `cat_mesh_tree_number`：树形编号表（~80,000 条）
  - `cat_mesh_entry_term`：入口术语表（~250,000 条）
  - `cat_mesh_concept`：概念表（~180,000 条，支持 Descriptor 和 SCR）
  - `cat_mesh_entry_combination`：组合条目表（~500 条）
  - `cat_mesh_scr`：SCR 主表（~350,000 条）
  - `cat_mesh_scr_heading_mapped_to`：SCR 到 Descriptor 映射关系表（含 major_topic 字段）
  - `cat_mesh_scr_source`：SCR 数据来源表
  - `cat_mesh_scr_indexing_info`：SCR 索引信息表
  - `cat_mesh_scr_pharmacological_action`：SCR 药理作用表
  - `cat_publication_mesh_heading`：文献-MeSH 主题标引表（一对多：一篇文献多个标引）
  - `cat_publication_mesh_qualifier`：文献-MeSH 限定词表（一对多：一个标引多个限定词）
  - `cat_publication_suppl_mesh`：文献-补充 MeSH 概念关联表（关联 SCR：化学物质、疾病、方案等）

- **Keyword（关键词）相关表**
  - `cat_keyword`：关键词主表（~800 万条，作者/编辑/索引机构提供的自由关键词，含规范化词形去重、频次统计）
  - `cat_publication_keyword`：文献-关键词关联表（~8500 万条，多对多关系，含主/副关键词标记、来源集合、顺序）

- **PublicationType（出版类型）相关表**
  - `cat_publication_type`：文献出版类型关联表（一对多，含类型 ID、类型值、词汇来源、顺序）

- **Publication（文献）补充数据表**
  - `cat_publication_abstract`：文献摘要表（独立存储，支持结构化摘要 JSON 和纯文本摘要，含版权信息）
  - `cat_publication_alternative_abstract`：文献翻译摘要表（支持同语言多来源类型摘要，含 language_code、source_type、translation_type、官方/专业翻译标记）
  - `cat_publication_date`：文献日期表（支持不完整日期，含年/月/日三种精度，日期类型：received/accepted/published/revised/epublish/entrezdate 等）
  - `cat_publication_source`：PubMed 来源表（每个 PMID 一行：最后写入或软删它的文件、`source_rank`、逐篇原文 `raw_xml`；只被软删、没写过原文的为墓碑行）
  - `cat_pubmed_file`：PubMed 文件进度表（baseline 与 updatefiles，`kind` 区分；状态、范围、作业 generation、各类计数含 `skipped_stale_count`）
  - `cat_publication_identifier`：文献标识符表（支持 PMID/DOI/PMC/PII 等多种标识符类型，建立反向索引）

- **Investigator（研究者）相关表**
  - `cat_investigator`：研究者主表（非作者研究人员，如临床试验 PI，支持 ORCID 和 dedupKey 去重）
  - `cat_publication_investigator`：文献-研究者关联表（多对多关系，含角色、顺序、职责信息）
  - `cat_publication_personal_name_subject`：人物主题表（传记类/历史类文献的主题人物，按文献独立存储）

- **Venue（载体）相关表**
  - `cat_venue`：载体主表（含 image_object_key 封面对象存储键字段（LetPub 下载后上传到对象存储），4 个 JSON 嵌入式值对象：publication_profile、citation_metrics、open_access、affiliated_societies）
  - `cat_venue_identifier`：载体标识符表（ISSN/OpenAlex ID/NLM ID/DOAJ/JCR 等）
  - `cat_venue_publication_stats`：载体年度发文统计表（发表量/被引量/OA 发文量）
  - `cat_venue_source_data`：载体数据源表（各数据源原始 JSON 和提取字段）
  - `cat_venue_jcr_rating`：JCR 期刊评级表（Impact Factor 年度趋势 + 最新年分区/排名/百分位详情，来源 LetPub）
  - `cat_venue_cas_rating`：CAS 中科院分区表（支持多版本：升级版/新锐版/基础版，来源 LetPub）
  - `cat_venue_cas_warning`：CAS 预警名单表（按发布年月 + 版本标签记录历史预警等级，来源 LetPub）
  - `cat_venue_scopus_rating`：Scopus 引用指标年度表（最新年：SJR/SNIP/CiteScoreTracker/分区/百分位；历史年：CiteScore + 发文/引用/被引率，来源 Elsevier Scopus API）
  - `cat_venue_mesh`：载体 MeSH 主题表（MeSH 主题词分类）
  - `cat_venue_relation`：载体关联表（期刊演变关系：前刊/后刊/合并/分拆）
  - `cat_venue_indexing_history`：载体索引历史表（MEDLINE/PMC 索引收录变迁）

- **Author（作者）相关表**（适配 PubMed Computed Authors）
  - `cat_author`：作者主表（~2100 万条，含 normalized_key、display_name、status）
  - `cat_author_name_variant`：作者名字变体表（~4200 万条，平均 2 个变体/作者）
  - `cat_author_orcid`：作者 ORCID 表（~500 万条，约 25% 作者有 ORCID）

- **Publication-Author 关联表**
  - `cat_publication_author`：文献-作者关联表（~1.4 亿条，含作者顺序、角色标记）
  - `cat_publication_author_affiliation`：作者-机构归属表（~2.1 亿条，支持多机构归属和延迟消歧）

- **Organization（机构）相关表**（基于 ROR Schema v2.0）
  - `cat_organization`：机构主表（含 JSON 字段：types、domains、links、admin_info）
  - `cat_organization_name`：机构名称表（多语言名称，含类型：ror_display/label/alias/acronym）
  - `cat_organization_external_id`：机构外部标识符表（GRID/ISNI/Wikidata/FundRef/Ringgold）
  - `cat_organization_relation`：机构关系表（层级关系：parent/child，关联关系：related，时序关系：successor/predecessor）
  - `cat_organization_location`：机构地理位置表（基于 GeoNames，含洲/国家/省/市/经纬度）
  - `cat_organization_alias`：Patra 自维护的机构别名（按 `ror_id` 关联，ROR 重导不影响；`alias_type` LABEL / ALIAS，`source` SEED / WIKIDATA；种子在 `R__cat_organization_alias_seed.sql`；一家机构只能有一条 zh LABEL，由局部唯一索引守住）
  - 2 码点的中文子串查询走 `lower()` 条件稳定顺序扫描：`%协和%` 凑不出三元组，预编译语句的通用计划会把 trigram 索引用成全索引扫描
  - 建议接口的索引（`V9`，`pg_trgm`）：`cat_organization_name.name_value`、`cat_organization_alias.alias` 的 GIN trigram；MeSH 主题词名与入口词的局部 GIN trigram（谓词与建议查询的 WHERE 逐字相同）

- **Funding（资助）相关表**
  - `cat_publication_funding`：文献-资助关联表（含资助机构 ID、项目编号、原始数据字段、数据来源追踪）

## 🧪 测试覆盖

| 层级 | 测试类型 | 覆盖率目标 |
|------|---------|-----------|
| Domain | 单元测试 | ≥80% |
| Application | 单元测试 | ≥70% |
| Infrastructure | 单元+集成测试 | ≥70% |
| Adapter | 单元+切片测试 | ≥70% |
| Boot | E2E 测试 | 核心流程 |

## 📝 变更日志

- 2026-10-24（v0.13，PAP-110 计划二）：门户引用导出（RIS / BibTeX / CSV / CSL-JSON，流式、每批 500）与复制引用（GB/T 7714-2015、Vancouver、APA 7、AMA 11，citeproc-java + 官方 CSL 样式）。
- 2026-10-24（v0.13，PAP-110 计划一）：baseline 与 updatefiles 合成一条可重放的导入流水线（`pubmedImportJob`、`pubmedReparseJob`，删除 `pubmedBaselineImportJob` / `pubmedUpdateSyncJob`）；`V10` 引用字段（出版月日、页码、ISO 缩写），`V11` 来源表 `cat_publication_source` 与进度表改名 `cat_pubmed_file`；md5 校验、坏记录隔离；日期按实际精度输出（门户与解析接口新增 `publishedDate`，详情 `dates[].date` 降精度），检索 `sort=year` 按年月日排序；配置键 `update-scope` 改名 `scope`。
- 2026-10-24（v0.13，PAP-109）：文献批量解析内部接口与 `PublicationEndpoint` 契约；门户批量卡片接口；删除门户响应的 `bookmarks`。
- 2026-10-21（v0.12，PAP-94）：机构与 MeSH 建议接口、内部查询接口与 `patra-catalog-api` 契约；`V9` 加 `pg_trgm` 索引与 `cat_organization_alias`；别名种子第一版。

## 📖 相关文档

- [patra-spring-boot-starter-batch](../patra-spring-boot-starter-batch/README.md)
