# 医学文献领域实体参考

本文档提供医学文献、期刊领域的核心实体定义和建模指南。

## 核心实体概览

### 1. 文献 (Publication)

学术出版物，包括期刊文章、会议论文、预印本等。

**核心属性**：
- `title` - 标题（必填）
- `abstract` - 摘要
- `publicationType` - 出版类型（Article/Review/Letter/Editorial 等）
- `publicationDate` - 出版日期
- `language` - 语言（ISO 639-3）

**标识符**：
| 标识符 | 格式 | 来源 | 说明 |
|--------|------|------|------|
| DOI | `10.xxxx/xxxxx` | Crossref | 数字对象标识符，最权威 |
| PMID | `12345678` | PubMed | PubMed 唯一 ID |
| PMCID | `PMC1234567` | PMC | PubMed Central ID |
| OpenAlex ID | `W1234567890` | OpenAlex | Work ID |
| arXiv ID | `2301.12345` | arXiv | 预印本 ID |

**关联关系**：
- `authors` - 作者列表（1:N，有序）
- `affiliations` - 机构列表（通过作者关联）
- `venue` - 发表载体（期刊/会议/仓库）
- `references` - 参考文献（N:M）
- `citedBy` - 被引用（N:M）
- `meshTerms` - MeSH 主题词（N:M，带权重）
- `keywords` - 关键词（1:N）
- `grants` - 资助信息（1:N）

### 2. 期刊/载体 (Venue)

学术出版载体，包括期刊、仓库、会议等。

**载体类型** (VenueType)：
| 类型 | 说明 | 示例 |
|------|------|------|
| JOURNAL | 学术期刊 | Nature, Science |
| REPOSITORY | 预印本仓库 | arXiv, bioRxiv |
| CONFERENCE | 学术会议 | NeurIPS, ACL |
| EBOOK_PLATFORM | 电子书平台 | SpringerLink |
| BOOK_SERIES | 图书系列 | Lecture Notes |
| PROCEEDINGS_SERIES | 会议论文集 | ACM Proceedings |
| OTHER | 其他 | - |

**标识符**：
| 标识符 | 格式 | 说明 |
|--------|------|------|
| ISSN | `1234-5678` | 国际标准连续出版物号 |
| ISSN-L | `1234-5678` | Linking ISSN（去重用） |
| eISSN | `1234-567X` | 电子版 ISSN |
| NLM ID | `101528555` | NLM 唯一标识符 |
| OpenAlex ID | `S1234567` | Source ID |
| CODEN | `ABCDEF` | 6字符编码 |

**评级体系**：
| 系统 | 说明 | 值域 |
|------|------|------|
| JCR | Journal Citation Reports | Q1/Q2/Q3/Q4 |
| CAS | 中科院分区 | 1区/2区/3区/4区 |
| Impact Factor | 影响因子 | 数值（如 42.778） |
| CiteScore | Scopus 评分 | 数值 |
| h-index | H 指数 | 整数 |

### 3. 作者 (Author)

学术著作的作者。

**核心属性**：
- `name` - 姓名（结构化：family/given/suffix）
- `orcid` - ORCID（格式：0000-0000-0000-0000）
- `email` - 联系邮箱
- `equalContribution` - 是否等同贡献

**标识符**：
| 标识符 | 格式 | 说明 |
|--------|------|------|
| ORCID | `0000-0000-0000-0000` | 开放研究者标识符 |
| Scopus Author ID | `12345678900` | Scopus 作者 ID |
| OpenAlex ID | `A1234567890` | Author ID |
| ResearcherID | `A-1234-5678` | Web of Science ID |

### 4. 机构 (Affiliation)

研究机构、大学、医院等。

**机构类型**：
- UNIVERSITY - 大学
- HOSPITAL - 医院
- RESEARCH_INSTITUTE - 研究所
- COMPANY - 企业
- GOVERNMENT - 政府机构
- NONPROFIT - 非营利组织

**标识符**：
| 标识符 | 格式 | 说明 |
|--------|------|------|
| ROR | `https://ror.org/xxxxx` | Research Organization Registry |
| GRID | `grid.xxxxx.x` | Global Research Identifier Database |
| ISNI | `0000 0001 2345 6789` | 国际标准名称标识符 |
| OpenAlex ID | `I1234567890` | Institution ID |

### 5. MeSH 主题词 (MeSH Term)

NLM 医学主题词表，用于文献标引和检索。

**描述符类型** (DescriptorClass)：
| 类型 | 代码 | 说明 |
|------|------|------|
| Topical | 1 | 主题性描述符（最常见） |
| Publication Type | 2 | 出版类型描述符 |
| Geographicals | 3 | 地理描述符 |
| Check Tag | 4 | 检查标签 |

**UI 格式**：
- 描述符：`D000001` - `D999999`
- 限定词：`Q000001` - `Q999999`
- 补充概念：`C000001` - `C999999`

**树形编号**：
表示主题词在层级树中的位置，如：
- `C01.539.248` - 传染病 > 细菌感染 > 革兰阳性菌感染
- 一个主题词可有多个树形编号（平均 2.3 个）

**关联关系**：
- `treeNumbers` - 树形位置（1:N）
- `concepts` - 概念（1:N，有首选概念）
- `entryTerms` - 入口术语/同义词（1:N）
- `allowableQualifiers` - 允许的限定词（N:M）
- `pharmacologicalActions` - 药理作用（1:N）

## 数据源特点

### PubMed / MEDLINE

**特点**：
- 生物医学文献最权威来源
- 严格的 MeSH 标引
- 提供 PMID、PMCID 标识

**数据格式**：
- XML（PubMed Baseline/Daily Update）
- DTD 定义严格

### OpenAlex

**特点**：
- 开放的学术知识图谱
- 涵盖 2.5 亿+作品
- 实体消歧（机构、作者）
- 提供 OpenAlex ID

**实体 ID 前缀**：
- Works: `W`
- Authors: `A`
- Sources: `S`
- Institutions: `I`
- Concepts: `C`

### Crossref

**特点**：
- DOI 注册机构
- 丰富的引用元数据
- 资助信息

### DOAJ

**特点**：
- 开放获取期刊目录
- OA 状态权威来源
- APC 信息

## 常见建模陷阱

### 1. 标识符去重

**问题**：同一实体在不同数据源有不同 ID
**方案**：使用 `DedupKey` 或 Linking ID（如 ISSN-L）

### 2. 作者消歧

**问题**：同名不同人，或同人不同写法
**方案**：优先使用 ORCID，结合机构信息消歧

### 3. 多数据源融合

**问题**：不同源的数据质量和覆盖度不同
**方案**：
- 定义数据源优先级
- 使用 Provenance 追踪数据来源
- 保留原始数据供回溯

### 4. MeSH 版本

**问题**：MeSH 每年更新，主题词可能新增/废弃/合并
**方案**：
- 记录 `meshVersion`（年份）
- 保留历史映射关系
