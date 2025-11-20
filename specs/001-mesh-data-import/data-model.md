# 数据模型设计

## 领域模型（Domain Layer）

### 1. 聚合根：MeshImportAggregate

**职责**：管理 MeSH 数据导入任务的完整生命周期

```java
public class MeshImportAggregate {
    // 身份标识
    private MeshImportId id;                // 强类型 ID

    // 任务基本信息
    private String taskName;                 // 任务名称
    private ImportStatus status;             // 任务状态
    private LocalDateTime startTime;         // 开始时间
    private LocalDateTime endTime;           // 结束时间

    // 数据源信息
    private String sourceUrl;                // NLM 数据源 URL
    private String xmlFileHash;              // XML 文件 MD5 哈希
    private Long xmlFileSize;                // XML 文件大小（字节）

    // 进度追踪
    private List<TableProgress> tableProgressList;  // 各表导入进度
    private Integer totalRecords;            // 总记录数
    private Integer processedRecords;        // 已处理记录数

    // 错误处理
    private Integer failedBatchCount;        // 失败批次数
    private String lastErrorMessage;         // 最后错误信息

    // 领域行为
    public void startImport() { }           // 开始导入
    public void updateTableProgress() { }   // 更新表进度
    public void markAsCompleted() { }       // 标记完成
    public void markAsFailed() { }          // 标记失败
    public void retry() { }                  // 重试失败任务
}
```

### 2. 值对象：TableProgress

**职责**：表示单张表的导入进度

```java
@Value
public class TableProgress {
    private final String tableName;          // 表名
    private final Integer totalCount;        // 总记录数
    private final Integer processedCount;    // 已处理数
    private final Integer failedCount;       // 失败数
    private final TableStatus status;        // 表状态
    private final LocalDateTime lastUpdateTime; // 最后更新时间

    public Double getProgressPercentage() {
        return (processedCount * 100.0) / totalCount;
    }
}
```

### 3. 值对象：强类型 ID

```java
@Value
public class MeshImportId {
    private final String value;

    public static MeshImportId generate() {
        return new MeshImportId(UUID.randomUUID().toString());
    }
}

@Value
public class DescriptorId {
    private final String value;  // 格式：D + 6位数字，如 D000001
}

@Value
public class QualifierId {
    private final String value;  // 格式：Q + 6位数字，如 Q000001
}
```

### 4. 枚举：状态定义

```java
public enum ImportStatus {
    PENDING("待处理"),
    PROCESSING("处理中"),
    SUCCESS("成功"),
    FAILED("失败"),
    CANCELLED("已取消");
}

public enum TableStatus {
    NOT_STARTED("未开始"),
    IN_PROGRESS("进行中"),
    COMPLETED("已完成"),
    FAILED("失败");
}
```

### 5. 领域事件

```java
public class MeshImportStarted extends DomainEvent {
    private final MeshImportId importId;
    private final String sourceUrl;
    private final LocalDateTime startTime;
}

public class MeshImportCompleted extends DomainEvent {
    private final MeshImportId importId;
    private final Integer totalRecords;
    private final Long elapsedSeconds;
    private final LocalDateTime completedTime;
}

public class MeshImportFailed extends DomainEvent {
    private final MeshImportId importId;
    private final String failureReason;
    private final Integer processedRecords;
    private final LocalDateTime failedTime;
}
```

### 6. MeSH 实体定义

```java
// 主题词实体
@Entity
public class MeshDescriptor {
    private DescriptorId id;
    private String ui;                      // Unique Identifier (D000001)
    private String name;                    // 主题词名称
    private String chineseName;             // 中文名称
    private LocalDate dateCreated;          // 创建日期
    private LocalDate dateRevised;          // 修订日期
    private String descriptorClass;         // 分类（1-4）
    private List<TreeNumber> treeNumbers;   // 树形编号列表
    private List<EntryTerm> entryTerms;     // 入口术语列表
    private List<Concept> concepts;         // 概念列表
}

// 限定词实体
@Entity
public class MeshQualifier {
    private QualifierId id;
    private String ui;                      // Unique Identifier (Q000001)
    private String name;                    // 限定词名称
    private String chineseName;             // 中文名称
    private String abbreviation;            // 缩写
    private LocalDate dateCreated;
    private LocalDate dateRevised;
}

// 树形编号值对象
@Value
public class TreeNumber {
    private final String value;             // 如 A01.111.222
    private final String descriptorUi;      // 关联的主题词 UI

    public String getCategory() {
        return value.substring(0, 1);      // 返回大类（A-Z）
    }
}

// 入口术语值对象
@Value
public class EntryTerm {
    private final String term;              // 术语文本
    private final String descriptorUi;      // 关联的主题词 UI
    private final Boolean isMajorTopic;     // 是否主要主题
}

// 概念值对象
@Value
public class Concept {
    private final String ui;                // 概念 UI
    private final String name;              // 概念名称
    private final String descriptorUi;      // 关联的主题词 UI
    private final Boolean isPreferred;      // 是否首选概念
}
```

## 基础设施层数据模型（Infrastructure Layer）

### 数据库表设计

#### 1. 导入任务表：cat_mesh_import_task

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | VARCHAR(64) | 主键，任务 ID |
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
| created_time | DATETIME | 创建时间 |
| updated_time | DATETIME | 更新时间 |

#### 2. 表进度记录表：cat_mesh_table_progress

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | 主键，自增 |
| import_id | VARCHAR(64) | 关联任务 ID |
| table_name | VARCHAR(50) | 表名 |
| total_count | INT | 总记录数 |
| processed_count | INT | 已处理数 |
| failed_count | INT | 失败数 |
| status | VARCHAR(20) | 表状态 |
| last_batch_num | INT | 最后处理批次号 |
| created_time | DATETIME | 创建时间 |
| updated_time | DATETIME | 更新时间 |

#### 3. 批次详情表：cat_mesh_batch_detail

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT | 主键，自增 |
| import_id | VARCHAR(64) | 关联任务 ID |
| table_name | VARCHAR(50) | 表名 |
| batch_num | INT | 批次序号 |
| batch_size | INT | 批次大小 |
| status | VARCHAR(20) | 批次状态 |
| retry_count | INT | 重试次数 |
| error_message | TEXT | 错误信息 |
| start_time | DATETIME | 开始时间 |
| end_time | DATETIME | 结束时间 |
| created_time | DATETIME | 创建时间 |

#### 4. MeSH 数据表（已存在，复用）

- **cat_mesh_descriptor**：主题词表
- **cat_mesh_qualifier**：限定词表
- **cat_mesh_tree_number**：树形编号表
- **cat_mesh_entry_term**：入口术语表
- **cat_mesh_concept**：概念表
- **cat_publication_mesh**：文献-MeSH 关联表

### DO 对象设计

```java
@TableName("cat_mesh_import_task")
public class MeshImportTaskDO {
    @TableId
    private String id;
    private String taskName;
    private String status;
    private String sourceUrl;
    private String xmlFileHash;
    private Long xmlFileSize;
    private Integer totalRecords;
    private Integer processedRecords;
    private Integer failedBatchCount;
    private String lastErrorMessage;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}

@TableName("cat_mesh_table_progress")
public class MeshTableProgressDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String importId;
    private String tableName;
    private Integer totalCount;
    private Integer processedCount;
    private Integer failedCount;
    private String status;
    private Integer lastBatchNum;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}

@TableName("cat_mesh_batch_detail")
public class MeshBatchDetailDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String importId;
    private String tableName;
    private Integer batchNum;
    private Integer batchSize;
    private String status;
    private Integer retryCount;
    private String errorMessage;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
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
-- 导入任务表索引
CREATE INDEX idx_import_task_status ON cat_mesh_import_task(status);
CREATE INDEX idx_import_task_created ON cat_mesh_import_task(created_time);

-- 表进度索引
CREATE INDEX idx_table_progress_import ON cat_mesh_table_progress(import_id);
CREATE INDEX idx_table_progress_table ON cat_mesh_table_progress(table_name);

-- 批次详情索引
CREATE INDEX idx_batch_detail_import ON cat_mesh_batch_detail(import_id);
CREATE INDEX idx_batch_detail_table_batch ON cat_mesh_batch_detail(table_name, batch_num);

-- MeSH 数据索引（已存在）
CREATE UNIQUE INDEX idx_descriptor_ui ON cat_mesh_descriptor(ui);
CREATE UNIQUE INDEX idx_qualifier_ui ON cat_mesh_qualifier(ui);
CREATE INDEX idx_tree_number_descriptor ON cat_mesh_tree_number(descriptor_ui);
CREATE INDEX idx_entry_term_descriptor ON cat_mesh_entry_term(descriptor_ui);
CREATE INDEX idx_concept_descriptor ON cat_mesh_concept(descriptor_ui);
```