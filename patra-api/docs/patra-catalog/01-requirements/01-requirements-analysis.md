# 需求分析文档 - patra_catalog 数据库设计

> 文档版本：v1.5
> 更新日期：2025-01-18
> 最新修订：更正表数量统计（实际完成设计36张表）
> 历史版本：
>   - v1.4: 完善MeSH表结构以支持PubMed完整数据导入（4.3节）
>   - v1.3: 新增日期字段设计（5.8节）、优化 venue_instance 日期字段
>   - v1.2: 新增开放获取（OA）状态管理表
>   - v1.1: 增加标识符冗余设计（5.5节）
>   - v1.0: 初始版本
> 作者：Patra Lin

## 一、项目背景

**项目名称**：Patra 医学文献数据平台目录服务
**数据库名称**：patra_catalog
**服务模块**：/Users/linqibin/Desktop/Patra-api/patra-catalog

## 二、业务需求

### 2.1 数据流程
```
patra-ingest (采集) → 消息队列 → patra_catalog (存储) → ES (检索)
```

### 2.2 核心需求
- **数据来源**：从消息队列接收 CanonicalPublication 模型数据
- **数据规模**：初始 200万条文献记录，持续增长
- **更新策略**：定期更新补充 MeSH 词表和文献数据
- **版本管理**：不保留历史版本，始终存储最新数据
- **审核流程**：无需审核，数据直接入库
- **主键策略**：雪花算法生成 19 位数字 ID

### 2.3 技术约束
- 表前缀：`cat_`
- 主键字段：统一使用 `id` (BIGINT)
- 标识符可空：PMID、DOI 等外部标识符允许为 NULL
- 检索方案：使用 Elasticsearch 进行全文检索
- 设计原则：实体独立、便于扩展、避免单表过大

## 三、数据模型分析

基于 `CanonicalPublication` 模型（位于 patra-common-model），识别出以下数据特征：

- **多样化载体**：不仅限于期刊，包括书籍、会议论文集等
- **复杂关联**：作者、机构、MeSH、资助等多对多关系
- **医学特色**：MeSH 标引、物质、基因、研究者等医学领域特有数据
- **元数据丰富**：标识符、日期、历史事件等辅助信息

## 四、实体设计与表清单

### 4.1 核心实体表（6张）

| 表名 | 中文名 | 说明 | 预估规模 |
|------|--------|------|---------|
| `cat_publication` | 出版物主表 | 存储文献基础信息，冗余 PMID/DOI 便于高频查询 | 200万+ |
| `cat_venue` | 出版载体表 | 期刊/书籍/会议等载体信息 | 3万+ |
| `cat_venue_instance` | 载体实例表 | 具体的卷/期/版次信息 | 50万+ |
| `cat_identifier` | 标识符表 | 存储所有类型标识符（PMID/DOI/PMC/PII/arXiv等） | 800万+ |
| `cat_author` | 作者表 | 作者基本信息 | 500万+ |
| `cat_abstract` | 摘要表 | 结构化/非结构化摘要 | 200万+ |

### 4.2 出版载体设计（重点）

采用多态设计模式处理不同类型的出版载体。venue 表存储共同属性（标题、ISSN/ISBN、出版商），venue_instance 表存储具体发行信息（卷期、版次、会议日期）。通过 venue_type 字段区分期刊、书籍、会议等不同类型，实现统一管理和灵活扩展。

```
venue (出版载体)
├── Journal (期刊)
│   └── instance: Volume 25, Issue 3
├── Book (书籍)
│   └── instance: 第3版, 2024年印刷
├── Conference (会议)
│   └── instance: ICML 2024
└── Others (其他)
    └── instance: 预印本服务器等
```

### 4.3 分类与索引表（12张）

#### MeSH 相关表（6张）
| 表名 | 中文名 | 说明 | 预估规模 |
|------|--------|------|---------|
| `cat_mesh_descriptor` | MeSH主题词表 | 医学主题词标准词表 | 3万+ |
| `cat_mesh_qualifier` | MeSH限定词表 | 主题词的限定修饰 | 100+ |
| `cat_mesh_tree_number` | MeSH树形编号表 | 主题词的层次位置 | 7万+ |
| `cat_mesh_entry_term` | MeSH入口术语表 | 同义词和入口术语 | 30万+ |
| `cat_mesh_concept` | MeSH概念表 | 概念层级结构 | 9万+ |
| `cat_publication_mesh` | 文献-MeSH关联表 | 多对多关联 | 2000万+ |

#### 关键词和类型表（4张）
| 表名 | 中文名 | 说明 | 预估规模 |
|------|--------|------|---------|
| `cat_keyword` | 关键词表 | 多来源关键词 | 100万+ |
| `cat_publication_keyword` | 文献-关键词关联表 | 多对多关联 | 500万+ |
| `cat_publication_type` | 出版类型表 | 受控词表 | 100+ |
| `cat_publication_type_mapping` | 文献-类型关联表 | 多对多关联 | 300万+ |

#### 物质相关表（2张）
| 表名 | 中文名 | 说明 | 预估规模 |
|------|--------|------|---------|
| `cat_substance` | 物质表 | 化学物质/药物/生物制品 | 5万+ |
| `cat_publication_substance` | 文献-物质关联表 | 多对多关联 | 300万+ |

### 4.4 人员与机构表（6张）

| 表名 | 中文名 | 说明 | 预估规模 |
|------|--------|------|---------|
| `cat_publication_author` | 文献-作者关联表 | 包含作者顺序 | 1000万+ |
| `cat_affiliation` | 机构表 | 研究机构信息 | 10万+ |
| `cat_author_affiliation` | 作者-机构关联表 | 多对多关联 | 1200万+ |
| `cat_investigator` | 研究者表 | 非作者研究人员 | 20万+ |
| `cat_publication_investigator` | 文献-研究者关联表 | 多对多关联 | 30万+ |
| `cat_personal_name_subject` | 人物主题表 | 传记等文献的主题人物 | 1万+ |

### 4.5 关联信息表（7张）

| 表名 | 中文名 | 说明 | 预估规模 |
|------|--------|------|---------|
| `cat_funding` | 资助信息表 | 基金/项目资助 | 100万+ |
| `cat_publication_funding` | 文献-资助关联表 | 多对多关联 | 150万+ |
| `cat_reference` | 参考文献表 | 引用的其他文献 | 4000万+ |
| `cat_external_reference` | 外部引用表 | 基因库/临床试验等 | 50万+ |
| `cat_related_item` | 相关项目表 | 撤稿/勘误/评论 | 10万+ |
| `cat_supplemental_object` | 补充对象表 | 图表/数据集等 | 100万+ |
| `cat_publication_history` | 发布历史表 | 时间线事件记录 | 600万+ |

### 4.6 辅助管理表（5张）

| 表名 | 中文名 | 说明 | 预估规模 |
|------|--------|------|---------|
| `cat_publication_date` | 日期信息表 | 多种日期类型 | 400万+ |
| `cat_publication_metadata` | 元数据表 | 索引方法/状态等 | 200万+ |
| `cat_alternative_abstract` | 其他语言摘要表 | 多语言版本 | 20万+ |
| `cat_language_mapping` | 语言映射表 | 原始语言值到标准代码的映射 | 1000+ |
| `cat_oa_location` | 开放获取位置表 | OA全文链接和版本信息 | 300万+ |

**总计：36张表**（核心实体6张 + 分类索引12张 + 人员机构6张 + 关联信息7张 + 辅助管理5张）

## 五、关键设计决策

### 5.1 出版载体多态设计

**决策**：采用单表继承模式
- `cat_venue` 表包含所有载体类型的共同字段
- 使用 `venue_type` 枚举区分类型（JOURNAL/BOOK/CONFERENCE/OTHER）
- 特定类型的专有字段使用 JSON 扩展字段存储

**理由**：
- 避免多表联接的性能开销
- 便于统一管理和查询
- 支持未来新载体类型的扩展

### 5.2 作者顺序保留

**决策**：在 `cat_publication_author` 关联表中添加 `author_order` 字段
- 保证作者列表的顺序性
- 支持第一作者、通讯作者标记

### 5.3 MeSH 词表存储策略

**决策**：存储完整 MeSH 词表
- 便于本地查询和展示
- 支持离线词表更新
- 提供词表版本管理

### 5.4 大文本字段处理

**决策**：摘要等大文本独立表存储
- 避免影响主表查询性能
- 支持结构化摘要存储
- 便于全文检索集成

### 5.5 MeSH 完整结构设计（新增）

**决策**：扩展 MeSH 表结构以完整支持 PubMed 数据导入
- 从 3 张表扩展到 6 张核心表
- 独立存储树形编号（支持多位置）
- 分离概念层级和入口术语
- 预留补充概念表（可选）

**理由**：
1. **数据完整性**：
   - PubMed MeSH XML 包含复杂的层次结构
   - 一个主题词可能有多个树形位置
   - 同义词和入口术语需要独立管理

2. **导入效率**：
   - 分表存储便于批量导入
   - 避免 JSON 字段的解析开销
   - 支持增量更新

3. **查询优化**：
   - 树形编号独立索引，支持层次查询
   - 入口术语独立索引，支持同义词检索
   - 概念层级支持语义分析

4. **表结构映射**：
   ```
   desc2025.xml → cat_mesh_descriptor（主表）
                → cat_mesh_tree_number（树形位置）
                → cat_mesh_concept（概念）
                → cat_mesh_entry_term（同义词）
   qual2025.xml → cat_mesh_qualifier（限定词）
   ```

### 5.6 标识符冗余设计

**决策**：主表冗余高频标识符 + 独立标识符表存储全量
- `cat_publication` 表冗余字段：
  - `pmid` VARCHAR(20) - PubMed ID（医学文献最常用）
  - `doi` VARCHAR(255) - 数字对象标识符（跨学科通用）
- `cat_identifier` 表存储所有标识符类型

**理由**：
1. **性能优化**：
   - 避免 90% 以上的 JOIN 操作
   - PMID 和 DOI 是最高频的查询条件
   - 直接在主表查询，响应时间从 ms 级降到 μs 级

2. **查询简化**：
   ```sql
   -- 优化前：需要 JOIN
   SELECT p.* FROM cat_publication p
   JOIN cat_identifier i ON p.id = i.publication_id
   WHERE i.type = 'pmid' AND i.value = '12345678';

   -- 优化后：直接查询
   SELECT * FROM cat_publication WHERE pmid = '12345678';
   ```

3. **数据一致性保证**：
   - 冗余字段通过应用层同步更新
   - 定期数据校验任务确保一致性
   - 标识符表作为权威数据源

4. **扩展性考虑**：
   - 其他标识符（PMC、PII、arXiv 等）仍存储在标识符表
   - 未来可根据使用频率调整冗余策略
   - 不影响新标识符类型的添加

### 5.6 语言字段三层设计

**决策**：原始值保留 + 映射表标准化 + 生成列基础语种

- `cat_publication` 表语言字段（3 个）：
  - `language_raw` VARCHAR(50) - 原始语言值（外部采集，如 "eng", "Chinese", "中文"）
  - `language_code` VARCHAR(10) - 标准语言代码（应用层通过映射表处理，如 "en", "zh-CN"）
  - `language_base` VARCHAR(5) - 基础语种（生成列，如 "en", "zh"）
- `cat_language_mapping` 表维护映射关系

**理由**：

1. **数据完整性**：
   - 保留原始值 `language_raw`，永不丢失外部采集的原始数据
   - 避免因标准化失败导致数据丢失

2. **应对数据不规范**：
   - 外部数据源的语言字段格式不统一（ISO 639-2、全称、中文描述等）
   - 通过映射表灵活处理各种格式：
     ```
     "eng" → "en"
     "chi" → "zh"
     "Chinese" → "zh"
     "中文" → "zh"
     "zh-Hans" → "zh-CN"
     ```

3. **生成列优化**（MySQL 8.0）：
   - `language_base` 使用 STORED 生成列自动提取基础语种
   - 支持按基础语种统计（如所有中文文献，包括简繁体）
   - 存储成本：5字节 × 200万 ≈ 10MB，可接受

4. **映射表动态学习**：
   - 预置常见映射（200+ 条）
   - 记录置信度，支持人工审核
   - 跟踪使用频率，优化高频映射

### 5.7 开放获取（OA）状态设计

**决策**：主表精简冗余 + 独立 OA 位置表存储详细信息

- `cat_publication` 表冗余字段（仅 2 个）：
  - `is_oa` BOOLEAN - 是否有任何形式的开放获取
  - `oa_status` VARCHAR(20) - 最佳 OA 状态（gold/green/hybrid/bronze/closed）
- `cat_oa_location` 表存储所有 OA 位置详情

**理由**：

1. **精简冗余**：
   - 主表仅保留最关键的两个字段
   - 避免主表臃肿，保持查询性能
   - `is_oa` 支持快速筛选，`oa_status` 提供分类统计

2. **OA 状态优先级规则**：
   - Gold OA（出版商官网，正式版本）> Green OA（机构仓储）> Hybrid OA > Bronze OA（免费无许可证）> 预印本
   - 通过优先级字段自动确定最佳 OA 状态
   - 应用层负责同步更新主表冗余字段

3. **详细位置管理**：
   - 一篇文献可能有多个 OA 来源（出版商、PubMed Central、机构仓储、预印本等）
   - 记录每个位置的版本类型（publishedVersion/acceptedVersion/submittedVersion）
   - 保留证据来源和检查时间，支持数据溯源

4. **扩展性考虑**：
   - 支持动态添加新的 OA 类型
   - 可记录禁发期（Embargo）信息
   - 许可证信息独立存储，便于法律合规查询

### 5.8 日期字段设计

**决策**：分离字段存储 + publication_year 冗余

#### 不完整日期的处理

医学文献的出版日期并非总是完整的年月日：
- **只有年份**：约 30% 的文献（如早期文献、年刊）
- **年+月**：约 40% 的文献（电子优先出版）
- **完整日期**：约 30% 的文献

传统的 DATE 类型（必须是完整的 YYYY-MM-DD）会导致**虚假精度**问题：将 "2023-06" 强制存储为 "2023-06-01" 违背了数据真实性原则。

#### 分离字段设计

`cat_venue_instance` 表采用分离字段存储原始日期精度：

```sql
publication_year SMALLINT NOT NULL     -- 出版年份（必填）
publication_month TINYINT NULL         -- 出版月份 1-12（可选）
publication_day TINYINT NULL           -- 出版日期 1-31（可选）
```

**优势**：
1. **精确表达不完整性**：NULL 表示"不存在此精度"而非"未知"
2. **避免虚假精度**：不会将 "2023-06" 存为 "2023-06-01"
3. **数值类型优势**：索引效率高，排序友好，存储紧凑（6字节）
4. **完整性约束**：可设置 CHECK 约束（month 1-12, day 1-31）

#### publication_year 冗余设计

`cat_publication` 主表冗余 `publication_year` 字段：

**理由**：
1. **查询频率极高**：按年份筛选是医学文献检索的最高频操作（>60% 查询）
2. **避免 JOIN 开销**：不冗余时每次按年份查询都需要 JOIN venue_instance
3. **存储成本低**：仅 2 字节/行（200 万行 = 4MB）
4. **索引友好**：SMALLINT 类型索引效率远高于 DATE

**典型查询场景**：
```sql
-- 不冗余：需要 JOIN
SELECT p.* FROM cat_publication p
JOIN cat_venue_instance vi ON p.venue_instance_id = vi.id
WHERE vi.publication_year BETWEEN 2020 AND 2023;

-- 冗余后：直接查询，性能提升 50%+
SELECT * FROM cat_publication
WHERE publication_year BETWEEN 2020 AND 2023;
```

**不使用生成列的原因**：
- 生成列只能基于同表字段计算
- publication_year 的源数据在 venue_instance 表
- 冗余字段由应用层在插入/更新时同步

## 六、扩展性设计

### 6.1 预留扩展机制

1. **JSON 扩展字段**
   - 每个主要实体表包含 `ext_data` JSON 字段
   - 存储非结构化的附加信息

2. **支持新数据源**
   - 标识符表支持任意类型标识符
   - 载体表支持新的出版形式

3. **批量更新优化**
   - 使用软删除（deleted 字段）
   - 版本号支持乐观锁
   - 时间戳记录数据变更

## 七、数据完整性保证

### 7.1 外键约束
- 所有关联表设置外键约束
- 配置级联删除策略
- 防止孤立数据产生

### 7.2 审计字段
```sql
created_at  TIMESTAMP(6) -- 创建时间
updated_at  TIMESTAMP(6) -- 更新时间
deleted     TINYINT(1)   -- 软删除标记
version     BIGINT       -- 乐观锁版本
```

### 7.3 数据验证
- 应用层数据校验（领域模型验证）
- 数据库层约束检查（NOT NULL、UNIQUE、CHECK）
- 触发器验证（复杂业务规则）

## 八、性能考虑（预留设计）

虽然当前不设计索引，但预留以下优化点：

### 8.1 分区策略预留
- 按时间分区（created_at）
- 按数据源分区（数据来源标识）

### 8.2 索引设计预留
- 主键索引（自动创建）
- 外键索引（关联查询）
- **冗余字段索引**（cat_publication.pmid、cat_publication.doi - 唯一索引）
- 业务查询索引（其他标识符、作者名等）
- 复合索引（多条件查询）

### 8.3 查询优化预留
- 读写分离架构支持
- 缓存层集成点（Redis）
- 物化视图（统计分析）

## 九、下一步计划

### 阶段 1 - ER图设计
- 绘制完整的实体关系图
- 明确主外键关系
- 标注基数关系

### 阶段 2 - 表结构详细设计
- 定义每个表的具体字段
- 确定数据类型和长度
- 设置约束条件

### 阶段 3 - SQL DDL生成
- 生成建表语句
- 包含注释说明
- 提供示例数据

### 阶段 4 - 领域模型映射
- 设计 Java 实体类
- 定义仓储接口
- 提取值对象

### 阶段 5 - 架构决策记录
- 记录设计决策（ADR）
- 评估风险点
- 制定优化计划

## 附录A：数据源说明

主要数据源包括：
- PubMed/MEDLINE
- Europe PMC
- EMBASE
- 其他医学数据库

## 附录B：参考标准

- PubMed/MEDLINE 元数据标准
- MeSH 医学主题词表
- Dublin Core 元数据标准
- Schema.org ScholarlyArticle 规范

## 附录C：术语表

| 术语 | 说明 |
|------|------|
| MeSH | Medical Subject Headings，医学主题词表 |
| PMID | PubMed Identifier，PubMed 唯一标识符 |
| DOI | Digital Object Identifier，数字对象标识符 |
| PMC | PubMed Central，生物医学全文数据库 |
| Venue | 出版载体，包括期刊、书籍、会议等 |
| Instance | 载体实例，如具体的卷期、版次 |

---

*本文档为 patra_catalog 数据库设计的需求分析阶段成果，后续将根据需求变化持续更新。*