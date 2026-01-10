# ADR-0001: Author 聚合根设计

## 状态

已采纳（2026-01-10）

## 背景

Patra 需要导入 PubMed Computed Authors 数据源，存储已消歧的学术作者信息。

**PubMed Computed Authors 数据特点**：

1. 每条记录代表一个**已消歧的独立作者**
2. `name` 字段是**姓名规范化格式**（如 "SMITH+R" = 姓 Smith + 首字母 R）
3. 同一 `name` 下可能有多个不同的作者（姓名格式相似但实为不同人）
4. 约 25% 的作者有 ORCID 标识符，少数作者有多个 ORCID

**示例**：`SMITH+R` 包含 2800+ 个不同的人：
- Richard D Smith (ORCID: 0000-0002-2381-2349, 922 篇论文)
- Richard JH Smith (ORCID: 0000-0003-1201-6731, 500 篇论文)
- Roger Smith (ORCID: 0000-0003-4720-0280, 459 篇论文)
- ... 等

## 决策

将 Author 设计为独立聚合根，采用 1:N 子实体模式：

### 1. 聚合根结构

```
AuthorAggregate (聚合根)
├── AuthorNameVariant (子实体, 1:N) - 名字变体
└── Orcid (值对象集合, 1:N) - ORCID 标识符
```

### 2. 标识符设计

| 标识符 | 类型 | 说明 |
|--------|------|------|
| `id` | 主键 | 系统内部唯一标识（雪花 ID） |
| `normalizedKey` | 查询键 | 姓名规范化格式，**非唯一**，用于分组查询 |
| `orcid` | 辅助键 | 外部标识符，全局唯一（通过子表约束） |

**关键决策**：`normalizedKey` 使用普通索引而非唯一索引，因为同一格式下可能有多个不同的已消歧作者。

### 3. 数据库表设计

| 表名 | 说明 | 预估数据量 |
|------|------|-----------|
| `cat_author` | 聚合根表 | 初始 2100万，年增 100万 |
| `cat_author_name_variant` | 名字变体表 | 初始 4200万（平均 2 个变体/作者） |
| `cat_author_orcid` | ORCID 表 | 初始 500万（约 25% 作者有 ORCID） |

### 4. 索引设计

| 索引名 | 类型 | 用途 |
|--------|------|------|
| `idx_normalized_key` | 普通索引 | 按姓名规范化格式分组查询 |
| `uk_orcid` | 唯一索引 | ORCID 全局唯一约束 |
| `idx_status` | 普通索引 | 状态筛选 |
| `idx_display_name` | 前缀索引 | 展示名称模糊查询 |

### 5. 子实体审计字段策略

所有实体（包括子实体）统一继承 `BaseJpaEntity`，采用完整审计字段：

- `id`：雪花 ID
- `version`：乐观锁
- `created_at`、`updated_at`：时间戳
- `created_by`、`created_by_name`：创建人
- `updated_by`、`updated_by_name`：更新人
- `record_remarks`、`ip_address`：扩展审计信息

### 6. ORCID 去重与合并策略

PubMed 数据源中，同一个 ORCID 可能出现在多条记录中（消歧算法的误差）。
由于 ORCID 是全球唯一的个人标识符，这些记录实际上是同一个人。

**两级去重策略**：

| 级别 | 处理时机 | 策略 |
|------|---------|------|
| **第一级** | 批次内 | 按**所有 ORCID** 聚合（非仅主要 ORCID），合并名字变体 |
| **第二级** | 跨批次 | UPSERT 模式：已存在则**合并**名字变体并更新，不存在则新增 |

**关键决策**：采用「合并」而非「跳过」策略，确保不丢失名字变体信息。

```
示例：
批次 1（已写入）：作者A (ORCID=X, variants=[Smith,Anna,A])
批次 2（待写入）：作者B (ORCID=X, variants=[Smith,Anne,A])

处理结果：
作者A（更新）：variants=[Smith,Anna,A, Smith,Anne,A]  ← 合并名字变体
```

**多 ORCID 场景**（约 5% 作者有多个 ORCID）：

- 如果作者的任一 ORCID 与已存在作者的任一 ORCID 匹配，则合并
- 例：作者 A [X, Y] 与作者 B [Y] 应合并（共享 ORCID Y）

### 7. 名字变体去重策略

使用 **ICU4J Collator** 实现与 MySQL `utf8mb4_0900_ai_ci` 精确匹配的去重逻辑：

- 大小写不敏感：`SMITH` = `Smith` = `smith`
- 重音不敏感：`García` = `Garcia`
- 德语变音不敏感：`Müller` = `Mueller` = `Muller`

## 后果

### 正面影响

1. **数据完整性**：完整保留 PubMed 的作者消歧信息
2. **查询灵活性**：支持按 normalizedKey 分组、按 ORCID 精确查询
3. **扩展性好**：预留 `ext_data` JSON 字段支持未来扩展
4. **批量导入友好**：无唯一键冲突，支持高效批量插入
5. **名字变体完整**：跨批次 UPSERT 策略确保不丢失任何名字变体

### 需要注意

1. **查询返回多条**：按 `normalizedKey` 查询可能返回多个作者，业务层需处理
2. **JPA 操作顺序**：跨批次更新时需先 flush 删除操作，避免唯一约束冲突

## 相关文件

- `V0.1.2__create_author.sql` - DDL 脚本
- `AuthorAggregate.java` - 聚合根实现（含 `mergeNameVariantsFrom()` 合并逻辑）
- `AuthorNameVariant.java` - 名字变体值对象
- `AuthorRepository.java` - 仓储接口（含 `findAuthorsByAnyOrcid()` 跨批次查询）
- `AuthorRepositoryAdapter.java` - 仓储实现（含 UPSERT 分批处理逻辑）
- `AuthorItemWriter.java` - 批量写入器（实现两级去重策略）
- `PubMedComputedAuthorParser.java` - 数据解析器（ICU4J 名字变体去重）
