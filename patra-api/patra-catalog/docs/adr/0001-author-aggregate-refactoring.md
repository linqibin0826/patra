# ADR-0001: Author 聚合根重构

## 状态

已采纳（2026-01-09）

## 背景

原有 Author 模型是简化的单表设计，仅包含 `normalizedKey` 和 `displayName` 两个核心属性。
随着系统演进，需要适配 PubMed Computed Authors 数据源，存储更丰富的作者信息：

1. **多名字变体**：同一作者可能有多种名字形式（全名、缩写、多语言名称）
2. **ORCID 标识符**：少数作者拥有多个 ORCID（约 25% 的作者有 ORCID）
3. **状态管理**：支持作者合并（MERGED）、停用（INACTIVE）等状态

## 决策

将 Author 从单表结构重构为独立聚合根，采用 1:N 子实体模式：

### 1. 聚合根结构

```
AuthorAggregate (聚合根)
├── AuthorNameVariant (子实体, 1:N) - 名字变体
└── Orcid (值对象集合, 1:N) - ORCID 标识符
```

### 2. 数据库表设计

| 表名 | 说明 | 预估数据量 |
|------|------|-----------|
| `cat_author` | 聚合根表 | 初始 2100万，年增 100万 |
| `cat_author_name_variant` | 名字变体表 | 初始 4200万（平均 2 个变体/作者） |
| `cat_author_orcid` | ORCID 表 | 初始 500万（约 25% 作者有 ORCID） |

### 3. 业务键设计

- **主业务键**：`normalized_key`（对齐 PubMed Computed Authors）
- **辅助查询键**：`orcid`（全局唯一）

### 4. 子实体审计字段策略

所有实体（包括子实体）统一继承 `BaseJpaEntity`，采用完整审计字段：

- `id`：雪花 ID
- `version`：乐观锁
- `created_at`、`updated_at`：时间戳
- `created_by`、`created_by_name`：创建人
- `updated_by`、`updated_by_name`：更新人
- `record_remarks`、`ip_address`：扩展审计信息

**理由**：统一继承规范可降低认知负担，所有实体具有一致的审计能力，便于追踪数据变更和问题排查。

### 5. ID 生成策略

**决策**：使用雪花算法（Snowflake ID），不使用数据库自增。

**理由**：
- 支持分布式环境下的 ID 生成
- 支持批量插入时预分配 ID
- 与其他聚合根保持一致

## 后果

### 正面影响

1. **数据完整性**：能够存储 PubMed 完整的作者信息
2. **查询灵活性**：支持按名字变体、ORCID 等多维度查询
3. **扩展性好**：预留 `ext_data` JSON 字段支持未来扩展（如 ORCID API 补充信息）
4. **与 Publication 解耦**：Author 成为独立聚合根，通过关联表建立 N:M 关系

### 需要注意

1. **批量导入性能**：需要使用 `saveAll()` + 批量 flush 策略
2. **数据迁移**：需要从 PubMed 重新导入完整作者数据
3. **查询优化**：高频查询（按 normalizedKey、ORCID）需要建立索引

## 相关文件

- `V0.1.2__create_author.sql` - DDL 脚本
- `AuthorAggregate.java` - 聚合根实现
- `AuthorNameVariant.java` - 名字变体值对象
- `AuthorRepositoryAdapter.java` - 仓储实现
