# 数据模型设计

## 领域模型（Domain Layer）

### 1. 聚合根：MeshImportAggregate

**职责**：管理 MeSH 数据导入任务的完整生命周期

```java
/**
 * MeSH 导入任务聚合根。
 *
 * <p>管理整个 MeSH 数据导入任务的生命周期，包括：
 * <ul>
 *   <li>任务状态转换（PENDING → PROCESSING → SUCCESS/FAILED）
 *   <li>进度追踪（各表的处理进度）
 *   <li>错误恢复（失败批次管理和重试）
 *   <li>数据完整性验证（文件 MD5 校验）
 * </ul>
 *
 * <p>领域事件：
 * <ul>
 *   <li>{@code MeshImportStarted} - 任务启动时发布
 *   <li>{@code MeshImportCompleted} - 任务成功完成时发布
 *   <li>{@code MeshImportFailed} - 任务失败时发布
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeshImportAggregate extends AggregateRoot<MeshImportId> {

    /** 身份标识（强类型 ID） */
    private MeshImportId id;

    /** 任务名称 */
    private String taskName;

    /** 任务状态（PENDING/PROCESSING/SUCCESS/FAILED/CANCELLED） */
    private MeshImportTaskStatus status;

    /** 开始时间 */
    private Instant startTime;

    /** 结束时间 */
    private Instant endTime;

    /** NLM 数据源 URL */
    private String sourceUrl;

    /** XML 文件 MD5 哈希（验证完整性） */
    private String xmlFileHash;

    /** XML 文件大小（字节） */
    private Long xmlFileSize;

    /** 各表导入进度（值对象列表） */
    private List<TableProgress> tableProgressList;

    /** 总记录数（约 350,000） */
    private Integer totalRecords;

    /** 已处理记录数 */
    private Integer processedRecords;

    /** 失败批次数 */
    private Integer failedBatchCount;

    /** 最后错误信息 */
    private String lastErrorMessage;

    // =============== 领域行为 ===============

    /**
     * 开始导入任务。
     *
     * <p>前置条件：任务状态为 PENDING
     * <p>后置条件：任务状态变为 PROCESSING，发布 MeshImportStarted 事件
     *
     * @throws IllegalStateException 如果任务不是 PENDING 状态
     */
    public void startImport() {
        if (this.status != MeshImportTaskStatus.PENDING) {
            throw new IllegalStateException("只有 PENDING 状态的任务可以开始导入");
        }
        this.status = MeshImportTaskStatus.PROCESSING;
        this.startTime = Instant.now();
        // 发布领域事件
        registerEvent(new MeshImportStarted(this.id, this.sourceUrl, this.startTime));
    }

    /**
     * 更新指定表的进度。
     *
     * <p>用于断点续传，记录每张表的最后处理批次号
     *
     * @param tableName 表名
     * @param processedCount 已处理数
     * @param lastBatchNum 最后批次号
     */
    public void updateTableProgress(String tableName, Integer processedCount, Integer lastBatchNum) {
        TableProgress progress = findTableProgress(tableName);
        TableProgress updated = progress.updateProgress(processedCount, lastBatchNum);
        replaceTableProgress(tableName, updated);
        recalculateOverallProgress();
    }

    /**
     * 标记任务完成。
     *
     * <p>前置条件：所有表的状态为 COMPLETED
     * <p>后置条件：任务状态变为 SUCCESS，发布 MeshImportCompleted 事件
     */
    public void markAsCompleted() {
        if (!allTablesCompleted()) {
            throw new IllegalStateException("所有表必须完成才能标记任务完成");
        }
        this.status = MeshImportTaskStatus.SUCCESS;
        this.endTime = Instant.now();
        long elapsedSeconds = Duration.between(startTime, endTime).getSeconds();
        registerEvent(new MeshImportCompleted(this.id, this.totalRecords, elapsedSeconds, this.endTime));
    }

    /**
     * 标记任务失败。
     *
     * @param errorMessage 失败原因
     */
    public void markAsFailed(String errorMessage) {
        this.status = MeshImportTaskStatus.FAILED;
        this.lastErrorMessage = errorMessage;
        this.endTime = Instant.now();
        registerEvent(new MeshImportFailed(this.id, errorMessage, this.processedRecords, this.endTime));
    }

    /**
     * 重试失败任务。
     *
     * <p>前置条件：任务状态为 FAILED
     * <p>后置条件：任务状态变为 PROCESSING，重置失败批次计数
     */
    public void retry() {
        if (this.status != MeshImportTaskStatus.FAILED) {
            throw new IllegalStateException("只有 FAILED 状态的任务可以重试");
        }
        this.status = MeshImportTaskStatus.PROCESSING;
        this.failedBatchCount = 0;
        this.lastErrorMessage = null;
    }

    // =============== 私有辅助方法 ===============

    private TableProgress findTableProgress(String tableName) {
        return tableProgressList.stream()
            .filter(p -> p.getTableName().equals(tableName))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("表不存在: " + tableName));
    }

    private void replaceTableProgress(String tableName, TableProgress updated) {
        tableProgressList.removeIf(p -> p.getTableName().equals(tableName));
        tableProgressList.add(updated);
    }

    private void recalculateOverallProgress() {
        this.processedRecords = tableProgressList.stream()
            .mapToInt(TableProgress::getProcessedCount)
            .sum();
    }

    private boolean allTablesCompleted() {
        return tableProgressList.stream()
            .allMatch(p -> p.getStatus() == MeshTableImportStatus.COMPLETED);
    }
}
```

### 2. 值对象：TableProgress

**职责**：表示单张表的导入进度（不可变对象）

```java
/**
 * 表导入进度值对象。
 *
 * <p>表示单张表的导入进度，支持断点续传和进度计算。
 * <p>不可变对象，任何修改都会返回新实例。
 *
 * @author linqibin
 * @since 0.2.0
 */
@Value
public class TableProgress {

    /** 表名（如 "cat_mesh_descriptor"） */
    private final String tableName;

    /** 总记录数 */
    private final Integer totalCount;

    /** 已处理数 */
    private final Integer processedCount;

    /** 失败数 */
    private final Integer failedCount;

    /** 表状态（NOT_STARTED/IN_PROGRESS/COMPLETED/FAILED） */
    private final MeshTableImportStatus status;

    /** 最后处理批次号（用于断点续传） */
    private final Integer lastBatchNum;

    /** 最后更新时间 */
    private final Instant lastUpdateTime;

    /**
     * 计算进度百分比。
     *
     * @return 进度百分比（0.0 ~ 100.0）
     */
    public Double getProgressPercentage() {
        if (totalCount == null || totalCount == 0) {
            return 0.0;
        }
        return (processedCount * 100.0) / totalCount;
    }

    /**
     * 更新进度（返回新实例）。
     *
     * @param newProcessedCount 新的已处理数
     * @param newLastBatchNum 新的最后批次号
     * @return 更新后的新实例
     */
    public TableProgress updateProgress(Integer newProcessedCount, Integer newLastBatchNum) {
        MeshTableImportStatus newStatus = calculateStatus(newProcessedCount);
        return new TableProgress(
            this.tableName,
            this.totalCount,
            newProcessedCount,
            this.failedCount,
            newStatus,
            newLastBatchNum,
            Instant.now()
        );
    }

    /**
     * 增加失败数（返回新实例）。
     *
     * @param increment 增量
     * @return 更新后的新实例
     */
    public TableProgress incrementFailedCount(Integer increment) {
        return new TableProgress(
            this.tableName,
            this.totalCount,
            this.processedCount,
            this.failedCount + increment,
            this.status,
            this.lastBatchNum,
            Instant.now()
        );
    }

    private MeshTableImportStatus calculateStatus(Integer processedCount) {
        if (processedCount == 0) {
            return MeshTableImportStatus.NOT_STARTED;
        } else if (processedCount.equals(totalCount)) {
            return MeshTableImportStatus.COMPLETED;
        } else {
            return MeshTableImportStatus.IN_PROGRESS;
        }
    }
}
```

### 5. 强类型 ID（Domain 层）

**职责**：为聚合根和实体提供类型安全的身份标识

```java
/**
 * MeSH 导入任务强类型 ID。
 *
 * <p>包装 Long 类型的雪花 ID，提供编译期类型安全。
 * <p>不可变值对象。
 *
 * @author linqibin
 * @since 0.2.0
 */
@Value
public class MeshImportId {

    /** 雪花 ID 值 */
    private final Long value;

    /**
     * 从 Long 创建强类型 ID。
     *
     * @param value 雪花 ID
     * @return MeshImportId 实例
     */
    public static MeshImportId of(Long value) {
        return new MeshImportId(value);
    }
}

/**
 * MeSH 主题词强类型 ID。
 *
 * @author linqibin
 * @since 0.2.0
 */
@Value
public class DescriptorId {

    /** 雪花 ID 值 */
    private final Long value;

    public static DescriptorId of(Long value) {
        return new DescriptorId(value);
    }
}

/**
 * MeSH 限定词强类型 ID。
 *
 * @author linqibin
 * @since 0.2.0
 */
@Value
public class QualifierId {

    /** 雪花 ID 值 */
    private final Long value;

    public static QualifierId of(Long value) {
        return new QualifierId(value);
    }
}
```

### 6. 枚举：状态定义

```java
/**
 * MeSH 导入任务状态枚举。
 *
 * <p>定义导入任务的生命周期状态。
 *
 * @author linqibin
 * @since 0.2.0
 */
@Getter
@AllArgsConstructor
public enum MeshImportTaskStatus {

    /** 待处理（任务已创建，等待执行） */
    PENDING("待处理", "pending"),

    /** 处理中（任务正在执行） */
    PROCESSING("处理中", "processing"),

    /** 成功（任务已完成，所有表导入成功） */
    SUCCESS("成功", "success"),

    /** 失败（任务失败，遇到不可恢复错误） */
    FAILED("失败", "failed"),

    /** 已取消（任务被手动取消） */
    CANCELLED("已取消", "cancelled");

    /** 状态显示名称（中文） */
    private final String displayName;

    /** 状态编码（用于数据库存储和 API） */
    private final String code;
}

/**
 * MeSH 表导入状态枚举。
 *
 * <p>定义单张表的导入状态。
 *
 * @author linqibin
 * @since 0.2.0
 */
@Getter
@AllArgsConstructor
public enum MeshTableImportStatus {

    /** 未开始（表尚未开始处理） */
    NOT_STARTED("未开始", "not_started"),

    /** 进行中（表正在处理） */
    IN_PROGRESS("进行中", "in_progress"),

    /** 已完成（表已成功导入） */
    COMPLETED("已完成", "completed"),

    /** 失败（表导入失败） */
    FAILED("失败", "failed");

    /** 状态显示名称（中文） */
    private final String displayName;

    /** 状态编码（用于数据库存储和 API） */
    private final String code;
}

/**
 * MeSH 批次处理状态枚举。
 *
 * <p>定义单个批次的处理状态。
 *
 * @author linqibin
 * @since 0.2.0
 */
@Getter
@AllArgsConstructor
public enum MeshBatchStatus {

    /** 待处理（批次等待处理） */
    PENDING("待处理", "pending"),

    /** 处理中（批次正在处理） */
    PROCESSING("处理中", "processing"),

    /** 成功（批次已成功处理） */
    SUCCESS("成功", "success"),

    /** 失败（批次处理失败） */
    FAILED("失败", "failed");

    /** 状态显示名称（中文） */
    private final String displayName;

    /** 状态编码（用于数据库存储和 API） */
    private final String code;
}
```

### 3. 领域事件

**职责**：记录 MeSH 导入任务的关键生命周期事件

```java
/**
 * MeSH 导入任务启动事件。
 *
 * <p>当任务从 PENDING 转换为 PROCESSING 状态时发布。
 *
 * @author linqibin
 * @since 0.2.0
 */
@Value
public class MeshImportStarted extends DomainEvent {

    /** 任务 ID（强类型 ID） */
    private final MeshImportId importId;

    /** 数据源 URL */
    private final String sourceUrl;

    /** 开始时间 */
    private final Instant startTime;
}

/**
 * MeSH 导入任务完成事件。
 *
 * <p>当所有表导入成功完成时发布。
 *
 * @author linqibin
 * @since 0.2.0
 */
@Value
public class MeshImportCompleted extends DomainEvent {

    /** 任务 ID（强类型 ID） */
    private final MeshImportId importId;

    /** 总记录数 */
    private final Integer totalRecords;

    /** 耗时（秒） */
    private final Long elapsedSeconds;

    /** 完成时间 */
    private final Instant completedTime;
}

/**
 * MeSH 导入任务失败事件。
 *
 * <p>当任务遇到不可恢复错误时发布。
 *
 * @author linqibin
 * @since 0.2.0
 */
@Value
public class MeshImportFailed extends DomainEvent {

    /** 任务 ID（强类型 ID） */
    private final MeshImportId importId;

    /** 失败原因 */
    private final String failureReason;

    /** 已处理记录数 */
    private final Integer processedRecords;

    /** 失败时间 */
    private final Instant failedTime;
}
```

### 4. MeSH 领域实体定义

**说明**：Domain 层实体为纯 POJO，不包含持久化注解（与 Infrastructure 层的 DO 通过 MapStruct 转换）

```java
/**
 * MeSH 主题词领域实体。
 *
 * <p>代表医学主题词的核心业务概念，包含主题词的基本信息、树形编号、入口术语和概念。
 * <p>纯领域对象，不包含持久化逻辑。
 *
 * @author linqibin
 * @since 0.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeshDescriptor {

    /** 主键 ID（强类型 ID） */
    private DescriptorId id;

    /** MeSH 唯一标识符（格式：D000001-D999999） */
    private String ui;

    /** 主题词名称（首选术语，英文） */
    private String name;

    /** 中文名称（可选） */
    private String chineseName;

    /** 主题词类型（1=Topical, 2=PublicationType, 3=Geographicals, 4=CheckTag） */
    private String descriptorClass;

    /** 范围说明（定义和使用指南） */
    private String scopeNote;

    /** 创建日期（格式：YYYYMMDD） */
    private String dateCreated;

    /** 修订日期（格式：YYYYMMDD） */
    private String dateRevised;

    /** 确立日期（格式：YYYYMMDD） */
    private String dateEstablished;

    /** 是否有效（true=有效，false=已废弃） */
    private Boolean activeStatus;

    /** MeSH 版本年份（如 "2025"） */
    private String meshVersion;

    /** 树形编号列表（聚合关系） */
    private List<TreeNumber> treeNumbers;

    /** 入口术语列表（聚合关系） */
    private List<EntryTerm> entryTerms;

    /** 概念列表（聚合关系） */
    private List<Concept> concepts;
}

/**
 * MeSH 限定词领域实体。
 *
 * <p>代表 MeSH 限定词（Qualifier），用于细化主题词的含义。
 *
 * @author linqibin
 * @since 0.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeshQualifier {

    /** 主键 ID（强类型 ID） */
    private QualifierId id;

    /** MeSH 唯一标识符（格式：Q000001-Q999999） */
    private String ui;

    /** 限定词名称 */
    private String name;

    /** 中文名称（可选） */
    private String chineseName;

    /** 缩写 */
    private String abbreviation;

    /** 创建日期（格式：YYYYMMDD） */
    private String dateCreated;

    /** 修订日期（格式：YYYYMMDD） */
    private String dateRevised;

    /** 是否有效 */
    private Boolean activeStatus;

    /** MeSH 版本年份 */
    private String meshVersion;
}

/**
 * 树形编号值对象。
 *
 * <p>表示 MeSH 主题词在层次结构中的位置（如 A01.111.222）。
 * <p>不可变对象。
 *
 * @author linqibin
 * @since 0.2.0
 */
@Value
public class TreeNumber {

    /** 树形编号值（如 A01.111.222） */
    private final String value;

    /** 关联的主题词 UI */
    private final String descriptorUi;

    /**
     * 获取顶级分类字母（A-Z）。
     *
     * @return 分类字母（如 "A" 表示解剖学）
     */
    public String getCategory() {
        return value.substring(0, 1);
    }

    /**
     * 获取层级深度。
     *
     * @return 层级数（如 A01.111.222 的层级为 3）
     */
    public int getDepth() {
        return value.split("\\.").length;
    }
}

/**
 * 入口术语值对象。
 *
 * <p>表示主题词的同义词或别名（用于索引和检索）。
 * <p>不可变对象。
 *
 * @author linqibin
 * @since 0.2.0
 */
@Value
public class EntryTerm {

    /** 术语文本 */
    private final String term;

    /** 关联的主题词 UI */
    private final String descriptorUi;

    /** 是否主要主题 */
    private final Boolean isMajorTopic;
}

/**
 * 概念值对象。
 *
 * <p>表示主题词的语义概念（一个主题词可能包含多个相关概念）。
 * <p>不可变对象。
 *
 * @author linqibin
 * @since 0.2.0
 */
@Value
public class Concept {

    /** 概念 UI（唯一标识符） */
    private final String ui;

    /** 概念名称 */
    private final String name;

    /** 关联的主题词 UI */
    private final String descriptorUi;

    /** 是否首选概念 */
    private final Boolean isPreferred;
}
```

## 基础设施层数据模型（Infrastructure Layer）

### 数据库表设计

#### 1. 导入任务表：cat_mesh_import_task

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | 主键，雪花 ID（MyBatis-Plus 自动生成） |
| task_name | VARCHAR(100) | 任务名称 |
| status | VARCHAR(20) | 任务状态 |
| source_url | VARCHAR(500) | 数据源 URL |
| xml_file_hash | VARCHAR(32) | XML 文件 MD5 |
| xml_file_size | BIGINT | 文件大小（字节） |
| total_records | INT | 总记录数 |
| processed_records | INT | 已处理记录数 |
| failed_batch_count | INT | 失败批次数 |
| last_error_message | TEXT | 最后错误信息 |
| start_time | DATETIME | 开始时间 |
| end_time | DATETIME | 结束时间 |
| record_remarks | VARCHAR(1000) | 记录备注（JSON 格式） |
| created_at | DATETIME | 创建时间（自动填充） |
| created_by | BIGINT | 创建人 ID（自动填充） |
| created_by_name | VARCHAR(100) | 创建人姓名（自动填充） |
| updated_at | DATETIME | 更新时间（自动填充） |
| updated_by | BIGINT | 更新人 ID（自动填充） |
| updated_by_name | VARCHAR(100) | 更新人姓名（自动填充） |
| version | BIGINT | 乐观锁版本号 |
| ip_address | VARBINARY(16) | 客户端 IP 地址 |
| deleted | TINYINT(1) | 软删除标志（0=未删除，1=已删除） |

#### 2. 表进度记录表：cat_mesh_table_progress

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | 主键，雪花 ID（MyBatis-Plus 自动生成） |
| import_id | BIGINT | 关联任务 ID（外键：cat_mesh_import_task.id） |
| table_name | VARCHAR(50) | 表名 |
| total_count | INT | 总记录数 |
| processed_count | INT | 已处理数 |
| failed_count | INT | 失败数 |
| status | VARCHAR(20) | 表状态 |
| last_batch_num | INT | 最后处理批次号 |
| record_remarks | VARCHAR(1000) | 记录备注（JSON 格式） |
| created_at | DATETIME | 创建时间（自动填充） |
| created_by | BIGINT | 创建人 ID（自动填充） |
| created_by_name | VARCHAR(100) | 创建人姓名（自动填充） |
| updated_at | DATETIME | 更新时间（自动填充） |
| updated_by | BIGINT | 更新人 ID（自动填充） |
| updated_by_name | VARCHAR(100) | 更新人姓名（自动填充） |
| version | BIGINT | 乐观锁版本号 |
| ip_address | VARBINARY(16) | 客户端 IP 地址 |
| deleted | TINYINT(1) | 软删除标志（0=未删除，1=已删除） |

#### 3. 批次详情表：cat_mesh_batch_detail

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | 主键，雪花 ID（MyBatis-Plus 自动生成） |
| import_id | BIGINT | 关联任务 ID（外键：cat_mesh_import_task.id） |
| table_name | VARCHAR(50) | 表名 |
| batch_num | INT | 批次序号 |
| batch_size | INT | 批次大小 |
| status | VARCHAR(20) | 批次状态 |
| retry_count | INT | 重试次数 |
| error_message | TEXT | 错误信息 |
| start_time | DATETIME | 开始时间 |
| end_time | DATETIME | 结束时间 |
| record_remarks | VARCHAR(1000) | 记录备注（JSON 格式） |
| created_at | DATETIME | 创建时间（自动填充） |
| created_by | BIGINT | 创建人 ID（自动填充） |
| created_by_name | VARCHAR(100) | 创建人姓名（自动填充） |
| updated_at | DATETIME | 更新时间（自动填充） |
| updated_by | BIGINT | 更新人 ID（自动填充） |
| updated_by_name | VARCHAR(100) | 更新人姓名（自动填充） |
| version | BIGINT | 乐观锁版本号 |
| ip_address | VARBINARY(16) | 客户端 IP 地址 |
| deleted | TINYINT(1) | 软删除标志（0=未删除，1=已删除） |

#### 4. MeSH 数据表（已存在，复用）

- **cat_mesh_descriptor**：主题词表
- **cat_mesh_qualifier**：限定词表
- **cat_mesh_tree_number**：树形编号表
- **cat_mesh_entry_term**：入口术语表
- **cat_mesh_concept**：概念表
- **cat_publication_mesh**：文献-MeSH 关联表

### DO 对象设计

```java
/**
 * MeSH 导入任务数据库实体，映射到表 {@code cat_mesh_import_task}。
 *
 * <p>表结构：管理 MeSH 数据导入任务的生命周期和进度跟踪
 *
 * <p>关键字段说明：
 * <ul>
 *   <li>{@code task_name} - 任务名称（如 "2025年MeSH数据首次导入"）
 *   <li>{@code status} - 任务状态（PENDING/PROCESSING/SUCCESS/FAILED/CANCELLED）
 *   <li>{@code xml_file_hash} - XML 文件 MD5 哈希，用于验证数据完整性
 *   <li>{@code total_records} - 总记录数（约 350,000）
 *   <li>{@code processed_records} - 已处理记录数（用于进度计算）
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_mesh_import_task")
public class MeshImportTaskDO extends BaseDO {

    /** 任务名称 */
    @TableField("task_name")
    private String taskName;

    /** 任务状态（PENDING/PROCESSING/SUCCESS/FAILED/CANCELLED） */
    @TableField("status")
    private String status;

    /** 数据源 URL */
    @TableField("source_url")
    private String sourceUrl;

    /** XML 文件 MD5 哈希 */
    @TableField("xml_file_hash")
    private String xmlFileHash;

    /** 文件大小（字节） */
    @TableField("xml_file_size")
    private Long xmlFileSize;

    /** 总记录数 */
    @TableField("total_records")
    private Integer totalRecords;

    /** 已处理记录数 */
    @TableField("processed_records")
    private Integer processedRecords;

    /** 失败批次数 */
    @TableField("failed_batch_count")
    private Integer failedBatchCount;

    /** 最后错误信息 */
    @TableField("last_error_message")
    private String lastErrorMessage;

    /** 开始时间 */
    @TableField("start_time")
    private Instant startTime;

    /** 结束时间 */
    @TableField("end_time")
    private Instant endTime;
}

/**
 * MeSH 表进度记录数据库实体，映射到表 {@code cat_mesh_table_progress}。
 *
 * <p>表结构：跟踪每张表的导入进度，支持断点续传
 *
 * <p>关键字段说明：
 * <ul>
 *   <li>{@code import_id} - 关联任务 ID（外键：cat_mesh_import_task.id）
 *   <li>{@code table_name} - 表名（如 "cat_mesh_descriptor"）
 *   <li>{@code last_batch_num} - 最后处理批次号（断点续传关键字段）
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_mesh_table_progress")
public class MeshTableProgressDO extends BaseDO {

    /** 关联任务 ID（外键：cat_mesh_import_task.id） */
    @TableField("import_id")
    private Long importId;

    /** 表名 */
    @TableField("table_name")
    private String tableName;

    /** 总记录数 */
    @TableField("total_count")
    private Integer totalCount;

    /** 已处理数 */
    @TableField("processed_count")
    private Integer processedCount;

    /** 失败数 */
    @TableField("failed_count")
    private Integer failedCount;

    /** 表状态（NOT_STARTED/IN_PROGRESS/COMPLETED/FAILED） */
    @TableField("status")
    private String status;

    /** 最后处理批次号（断点续传关键字段） */
    @TableField("last_batch_num")
    private Integer lastBatchNum;
}

/**
 * MeSH 批次详情数据库实体，映射到表 {@code cat_mesh_batch_detail}。
 *
 * <p>表结构：记录每个批次的处理详情，用于错误追踪和重试管理
 *
 * <p>关键字段说明：
 * <ul>
 *   <li>{@code import_id} - 关联任务 ID（外键：cat_mesh_import_task.id）
 *   <li>{@code table_name} - 表名
 *   <li>{@code batch_num} - 批次序号（从 1 开始）
 *   <li>{@code retry_count} - 重试次数（最多 3 次）
 * </ul>
 *
 * @author linqibin
 * @since 0.2.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_mesh_batch_detail")
public class MeshBatchDetailDO extends BaseDO {

    /** 关联任务 ID（外键：cat_mesh_import_task.id） */
    @TableField("import_id")
    private Long importId;

    /** 表名 */
    @TableField("table_name")
    private String tableName;

    /** 批次序号（从 1 开始） */
    @TableField("batch_num")
    private Integer batchNum;

    /** 批次大小 */
    @TableField("batch_size")
    private Integer batchSize;

    /** 批次状态（PENDING/PROCESSING/SUCCESS/FAILED） */
    @TableField("status")
    private String status;

    /** 重试次数（最多 3 次） */
    @TableField("retry_count")
    private Integer retryCount;

    /** 错误信息 */
    @TableField("error_message")
    private String errorMessage;

    /** 开始时间 */
    @TableField("start_time")
    private Instant startTime;

    /** 结束时间 */
    @TableField("end_time")
    private Instant endTime;
}
```

## 转换器设计（Converter）

使用 MapStruct 进行对象转换：

```java
@Mapper(componentModel = "spring")
public interface MeshImportConverter {

    // 聚合根转换
    MeshImportAggregate toDomain(MeshImportTaskDO taskDO,
                                  List<MeshTableProgressDO> progressDOList);

    MeshImportTaskDO toTaskDO(MeshImportAggregate aggregate);

    List<MeshTableProgressDO> toProgressDOList(MeshImportAggregate aggregate);

    // 值对象转换
    TableProgress toTableProgress(MeshTableProgressDO progressDO);

    MeshTableProgressDO toProgressDO(TableProgress progress);

    // MeSH 实体转换
    MeshDescriptor toDescriptor(MeshDescriptorDO descriptorDO);

    MeshDescriptorDO toDescriptorDO(MeshDescriptor descriptor);
}
```

## 数据流设计

### 导入流程数据流

```
1. XML 下载
   NLM Server → HttpClient → Local File System

2. XML 解析
   Local XML File → StAX Parser → Domain Objects

3. 批量持久化
   Domain Objects → Converter → DO Objects → MyBatis Batch Insert

4. 进度更新
   Batch Result → TableProgress → MeshImportAggregate → Database
```

### 表导入顺序

基于数据依赖关系，按以下顺序串行导入：

1. **cat_mesh_descriptor**（主题词）- 独立表
2. **cat_mesh_qualifier**（限定词）- 独立表
3. **并行导入**：
   - cat_mesh_tree_number（依赖 descriptor）
   - cat_mesh_entry_term（依赖 descriptor）
   - cat_mesh_concept（依赖 descriptor）
4. **cat_publication_mesh**（文献关联）- 依赖前面所有表

## 索引设计

```sql
-- ===============================================
-- 导入任务表索引
-- ===============================================

-- 主键索引（自动创建）
-- PRIMARY KEY (id)

-- 任务状态索引（用于筛选正在运行的任务）
CREATE INDEX idx_import_task_status ON cat_mesh_import_task(status);

-- 创建时间索引（用于按时间查询任务列表）
CREATE INDEX idx_import_task_created_at ON cat_mesh_import_task(created_at);

-- 软删除标志索引（MyBatis-Plus 自动使用）
CREATE INDEX idx_import_task_deleted ON cat_mesh_import_task(deleted);

-- ===============================================
-- 表进度记录表索引
-- ===============================================

-- 主键索引（自动创建）
-- PRIMARY KEY (id)

-- 任务 ID 索引（用于查询某任务的所有表进度）
CREATE INDEX idx_table_progress_import_id ON cat_mesh_table_progress(import_id);

-- 表名索引（用于快速定位特定表的进度）
CREATE INDEX idx_table_progress_table_name ON cat_mesh_table_progress(table_name);

-- 复合索引（任务 ID + 表名，用于断点续传查询）
CREATE UNIQUE INDEX uk_table_progress_import_table
    ON cat_mesh_table_progress(import_id, table_name);

-- 软删除标志索引
CREATE INDEX idx_table_progress_deleted ON cat_mesh_table_progress(deleted);

-- ===============================================
-- 批次详情表索引
-- ===============================================

-- 主键索引（自动创建）
-- PRIMARY KEY (id)

-- 任务 ID 索引（用于查询某任务的所有批次）
CREATE INDEX idx_batch_detail_import_id ON cat_mesh_batch_detail(import_id);

-- 复合索引（任务 ID + 表名 + 批次号，用于唯一标识批次）
CREATE UNIQUE INDEX uk_batch_detail_import_table_batch
    ON cat_mesh_batch_detail(import_id, table_name, batch_num);

-- 批次状态索引（用于筛选失败批次）
CREATE INDEX idx_batch_detail_status ON cat_mesh_batch_detail(status);

-- 软删除标志索引
CREATE INDEX idx_batch_detail_deleted ON cat_mesh_batch_detail(deleted);

-- ===============================================
-- MeSH 数据索引（已存在，复用）
-- ===============================================

-- MeSH 主题词表
CREATE UNIQUE INDEX uk_mesh_descriptor_ui ON cat_mesh_descriptor(ui);
CREATE INDEX idx_mesh_descriptor_name ON cat_mesh_descriptor(name);
CREATE INDEX idx_mesh_descriptor_active_version
    ON cat_mesh_descriptor(active_status, mesh_version);

-- MeSH 限定词表
CREATE UNIQUE INDEX uk_mesh_qualifier_ui ON cat_mesh_qualifier(ui);

-- MeSH 树形编号表
CREATE INDEX idx_mesh_tree_number_descriptor_ui ON cat_mesh_tree_number(descriptor_ui);

-- MeSH 入口术语表
CREATE INDEX idx_mesh_entry_term_descriptor_ui ON cat_mesh_entry_term(descriptor_ui);

-- MeSH 概念表
CREATE INDEX idx_mesh_concept_descriptor_ui ON cat_mesh_concept(descriptor_ui);
```