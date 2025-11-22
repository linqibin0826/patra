# patra-catalog-domain — 目录管理领域模型

## 📋 概述

`patra-catalog-domain` 是 patra-catalog 服务的**领域核心层**，包含纯 Java 业务逻辑，无任何框架依赖。遵循**六边形架构(Hexagonal Architecture)**和**领域驱动设计(DDD)**原则，封装医学文献目录管理的核心业务规则。

本模块在六边形架构中位于**最内层**，定义了：
- **聚合根(Aggregates)**：MeshImportAggregate、MeshDescriptorAggregate、PublicationAggregate 等
- **实体(Entities)**：MeshTreeNumber、MeshEntryTerm、MeshConcept 等
- **值对象(Value Objects)**：MeshImportId、TableProgress、DescriptorId 等
- **领域事件(Domain Events)**：MeshImportStarted、MeshImportCompleted、MeshImportFailed
- **仓储端口(Repository Ports)**：供外层实现的接口契约
- **外部服务端口(Service Ports)**：MeshFileDownloadPort、XmlParserPort 等

**架构约束**：通过 `maven-enforcer-plugin` 强制执行领域层纯净性，禁止依赖 Spring、MyBatis、JPA 等框架。

---

## 🏗️ 模块结构

```
patra-catalog-domain/
└─ src/main/java/.../domain/
   ├─ model/                       # 领域模型
   │  ├─ aggregate/                # 聚合根
   │  │  ├─ MeshImportAggregate.java      # MeSH 导入任务聚合根
   │  │  ├─ MeshDescriptorAggregate.java  # MeSH 主题词聚合根
   │  │  ├─ PublicationAggregate.java     # 文献聚合根
   │  │  ├─ AuthorAggregate.java          # 作者聚合根
   │  │  ├─ AffiliationAggregate.java     # 机构聚合根
   │  │  └─ VenueAggregate.java           # 期刊聚合根
   │  ├─ entity/                   # 领域实体
   │  │  ├─ MeshTreeNumber.java           # MeSH 树形编号
   │  │  ├─ MeshEntryTerm.java            # MeSH 入口术语
   │  │  ├─ MeshConcept.java              # MeSH 概念
   │  │  └─ VenueInstance.java            # 期刊实例
   │  ├─ valueobject/              # 值对象
   │  │  ├─ MeshImportId.java             # 导入任务强类型 ID
   │  │  ├─ TableProgress.java            # 表导入进度值对象
   │  │  └─ FailedBatch.java              # 失败批次值对象
   │  ├─ vo/                       # 细粒度值对象
   │  │  ├─ mesh/                         # MeSH 相关值对象
   │  │  ├─ publication/                  # 文献相关值对象
   │  │  ├─ author/                       # 作者相关值对象
   │  │  ├─ affiliation/                  # 机构相关值对象
   │  │  ├─ venue/                        # 期刊相关值对象
   │  │  └─ common/                       # 通用值对象
   │  └─ enums/                    # 领域枚举
   │     ├─ MeshImportTaskStatus.java     # 导入任务状态
   │     ├─ MeshTableImportStatus.java    # 表导入状态
   │     ├─ DescriptorClass.java          # 主题词分类
   │     └─ PublicationStatus.java        # 文献状态
   ├─ event/                       # 领域事件
   │  ├─ MeshImportStarted.java           # 导入任务启动事件
   │  ├─ MeshImportCompleted.java         # 导入任务完成事件
   │  └─ MeshImportFailed.java            # 导入任务失败事件
   └─ port/                        # 端口接口（由 infra 实现）
      ├─ MeshImportRepository.java              # MeSH 导入任务仓储
      ├─ MeshDescriptorRepository.java          # MeSH 主题词仓储
      ├─ MeshBatchDetailRepository.java         # MeSH 批次详情仓储
      ├─ PublicationRepository.java             # 文献仓储
      ├─ AuthorRepository.java                  # 作者仓储
      ├─ AffiliationRepository.java             # 机构仓储
      ├─ VenueRepository.java                   # 期刊仓储
      ├─ XmlParserPort.java               # XML 解析器端口
      └─ MeshFileDownloadPort.java        # 文件下载端口
```

---

## 🔑 核心概念

### 1. MeSH (Medical Subject Headings)

**MeSH** 是美国国家医学图书馆（NLM）维护的医学主题词表，用于文献索引和检索。

#### MeSH 数据结构

```
MeSH Descriptor (主题词)
  ├─ Tree Numbers (树形编号，反映层次结构)
  │  └─ 例如: C01.252.400.700 表示 "Bacterial Infections > Specific Infections"
  ├─ Entry Terms (入口术语，同义词)
  │  └─ 例如: "Cancer" → "Neoplasms"
  ├─ Concepts (概念，语义关系)
  │  └─ 例如: "Diabetes Mellitus" 有多个概念（Type 1、Type 2）
  └─ Qualifiers (限定词，用于组合主题词)
     └─ 例如: "diagnosis"、"therapy"、"epidemiology"
```

### 2. Publication (文献)

医学文献的核心聚合根，包含：
- 基本信息：标题、摘要、作者、发表日期
- 标识符：PMID、DOI、PubMed Central ID (PMCID)
- 分类信息：MeSH 标引、关键词
- 引用信息：参考文献、被引用次数

### 3. 作者与机构

- **Author (作者)**：包含姓名、ORCID、所属机构
- **Affiliation (机构)**：包含机构名称、ROR ID、GRID ID、地理位置

### 4. Venue (期刊)

- 期刊基本信息：ISSN、影响因子、出版社
- 期刊实例：特定年份的期刊信息

---

## 🎯 核心组件说明

### 聚合根

#### 1. MeshImportAggregate (MeSH 导入任务聚合根)

**职责**：管理 MeSH 数据导入任务的完整生命周期和状态转换。

**状态机**：
```
PENDING → PROCESSING → COMPLETED
             ↓
          FAILED
```

**核心属性**：
- `id`: MeshImportId - 强类型 ID
- `taskName`: String - 任务名称
- `status`: MeshImportTaskStatus - 任务状态
- `sourceUrl`: String - MeSH XML 文件下载地址
- `tableProgressList`: List<TableProgress> - 各表导入进度
- `totalRecords`: Integer - 总记录数
- `processedRecords`: Integer - 已处理记录数
- `failedBatches`: List<FailedBatch> - 失败批次列表

**关键方法**：
- `startImport()`: 开始导入（状态转换：PENDING → PROCESSING）
- `updateTableProgress(String tableName, Integer processedCount, Integer batchIndex)`: 更新表进度
- `markAsCompleted()`: 标记为完成
- `markAsFailed(String errorMessage)`: 标记为失败
- `calculateOverallProgress()`: 计算整体进度百分比

**文件**：`model/aggregate/MeshImportAggregate.java`

#### 2. MeshDescriptorAggregate (MeSH 主题词聚合根)

**职责**：管理 MeSH 主题词及其关联的树形编号、入口术语和概念。

**核心属性**：
- `descriptorUI`: String - 主题词唯一标识（如 D000001）
- `descriptorName`: String - 主题词名称
- `descriptorClass`: DescriptorClass - 主题词分类（1/2/3/4）
- `treeNumbers`: List<MeshTreeNumber> - 树形编号列表
- `entryTerms`: List<MeshEntryTerm> - 入口术语列表
- `concepts`: List<MeshConcept> - 概念列表

**关键方法**：
- `addTreeNumber(String treeNumber)`: 添加树形编号
- `addEntryTerm(String term, LexicalTag tag)`: 添加入口术语
- `addConcept(MeshConcept concept)`: 添加概念

**文件**：`model/aggregate/MeshDescriptorAggregate.java`

#### 3. PublicationAggregate (文献聚合根)

**职责**：管理文献的完整信息和生命周期。

**核心属性**：
- `id`: Long - 文献 ID
- `dedupKey`: DedupKey - 去重键（基于 PMID/DOI）
- `title`: String - 标题
- `publicationDate`: LocalDate - 发表日期
- `authors`: List<AuthorAggregate> - 作者列表
- `venue`: VenueAggregate - 期刊信息
- `meshDescriptors`: List<MeshDescriptorAggregate> - MeSH 标引

**文件**：`model/aggregate/PublicationAggregate.java`

### 值对象

#### TableProgress (表导入进度值对象)

**职责**：跟踪单个表的导入进度，支持断点续传。

**核心属性**：
- `tableName`: String - 表名（如 "descriptor"）
- `status`: MeshTableImportStatus - 导入状态
- `totalCount`: Integer - 总记录数
- `processedCount`: Integer - 已处理记录数
- `currentBatchIndex`: Integer - 当前批次索引

**关键方法**：
- `updateProgress(Integer processedCount, Integer batchIndex)`: 更新进度
- `markAsCompleted()`: 标记为完成
- `markAsFailed()`: 标记为失败
- `calculateProgress()`: 计算进度百分比

**文件**：`model/valueobject/TableProgress.java`

#### MeshImportId (导入任务强类型 ID)

**职责**：提供类型安全的导入任务 ID，避免原始类型（Long）泛滥。

**设计模式**：值对象模式 + Factory 模式

**使用示例**：
```java
// 创建新 ID（未分配）
MeshImportId id = MeshImportId.notAssigned();

// 从数据库 ID 创建
MeshImportId id = MeshImportId.of(123L);

// 判断是否已分配
if (id.isAssigned()) {
    Long value = id.value();
}
```

**文件**：`model/valueobject/MeshImportId.java`

### 领域事件

#### MeshImportStarted (导入任务启动事件)

**触发时机**：调用 `MeshImportAggregate.startImport()` 时发布。

**用途**：通知其他模块导入任务已开始，用于监控、日志记录等。

**文件**：`event/MeshImportStarted.java`

#### MeshImportCompleted (导入任务完成事件)

**触发时机**：调用 `MeshImportAggregate.markAsCompleted()` 时发布。

**用途**：触发后续流程（如索引重建、缓存更新）。

**文件**：`event/MeshImportCompleted.java`

#### MeshImportFailed (导入任务失败事件)

**触发时机**：调用 `MeshImportAggregate.markAsFailed()` 时发布。

**用途**：触发告警、日志记录、失败恢复流程。

**文件**：`event/MeshImportFailed.java`

### 端口接口

**端口命名规范**（遵循六边形架构最佳实践）：

- **Repository 后缀**：用于聚合根/实体的持久化端口，直接操作本地数据库，由 MyBatis-Plus/JPA 实现
  - 示例：`MeshImportRepository`、`MeshDescriptorRepository`、`PublicationRepository`
- **Port 后缀**：用于外部服务和技术基础设施端口，调用外部 API/微服务/技术组件，由 HTTP Client/SDK/框架适配器实现
  - 示例：`XmlParserPort`（XML 解析能力）、`MeshFileDownloadPort`（文件下载能力）

这种命名方式清晰区分了两类端口的职责：Repository 面向数据持久化，Port 面向外部能力调用。

---

#### MeshImportRepository (MeSH 导入任务仓储接口)

**职责**：定义 MeSH 导入任务的持久化契约。

**核心方法**：
```java
public interface MeshImportRepository {
    MeshImportAggregate save(MeshImportAggregate aggregate);
    Optional<MeshImportAggregate> findById(MeshImportId id);
    Optional<MeshImportAggregate> findLatest();
    boolean existsRunningTask();
    void deleteAll();
}
```

**文件**：`port/MeshImportRepository.java`

#### XmlParserPort (XML 解析器端口)

**职责**：定义 MeSH XML 文件的解析契约。

**核心方法**：
```java
public interface XmlParserPort {
    Stream<MeshDescriptorAggregate> parseDescriptors(InputStream inputStream);
    Stream<MeshQualifier> parseQualifiers(InputStream inputStream);
}
```

**设计理念**：
- 返回 `Stream` 支持流式处理，避免内存溢出
- 支持大文件处理（~35 万条记录）

**文件**：`port/XmlParserPort.java`

#### MeshFileDownloadPort (文件下载端口)

**职责**：定义 MeSH 文件下载和校验的契约。

**核心方法**：
```java
public interface MeshFileDownloadPort {
    File download(String sourceUrl);
    boolean validateChecksum(File xmlFile, String expectedHash);
}
```

**文件**：`port/MeshFileDownloadPort.java`

---

## 📦 依赖关系

### 上游依赖

- `patra-common-core`：通用工具类和枚举
- `Lombok`：编译时注解（@Data、@Builder、@Value）
- `Hutool`：工具类（StrUtil、CollUtil、DateUtil）
- `Jackson`：JSON 序列化/反序列化（仅用于配置快照）

### 下游消费者

- `patra-catalog-app`：应用层（用例编排器）
- `patra-catalog-infra`：基础设施层（仓储实现、XML 解析实现）

**依赖方向**：Domain ← App ← Infra/Adapter（符合六边形架构）

---

## 🎨 设计模式

### 1. 聚合根模式 (Aggregate Pattern)

每个聚合根负责维护自己的一致性边界：
- MeshImportAggregate 管理导入任务状态机和表进度
- MeshDescriptorAggregate 管理主题词及其关联数据

### 2. 值对象模式 (Value Object Pattern)

不可变值对象封装业务概念：
- MeshImportId 封装强类型 ID
- TableProgress 封装表导入进度（支持不可变更新）

### 3. 仓储模式 (Repository Pattern)

通过接口定义持久化契约，由基础设施层实现：
- MeshImportRepository、MeshDescriptorRepository 等

### 4. 领域事件模式 (Domain Event Pattern)

通过事件驱动跨聚合的最终一致性：
- MeshImportStarted、MeshImportCompleted、MeshImportFailed

### 5. 端口适配器模式 (Port-Adapter Pattern)

通过 Port 接口定义边界，实现依赖倒置：
- Domain 层定义 Port 接口
- Infrastructure 层提供 Adapter 实现

---

## 🔒 架构约束

### Maven Enforcer 规则

通过 `maven-enforcer-plugin` 强制执行领域层纯净性：

```xml
<bannedDependencies>
    <excludes>
        <exclude>org.springframework:*</exclude>
        <exclude>org.springframework.boot:*</exclude>
        <exclude>jakarta.persistence:*</exclude>
        <exclude>com.baomidou:*</exclude>
        <exclude>org.mybatis:*</exclude>
    </excludes>
    <message>
❌ Domain layer MUST be framework-free (Hexagonal Architecture)!
   Only allowed: patra-common, Lombok, Hutool, Jackson
    </message>
</bannedDependencies>
```

**允许的依赖**：
- ✅ patra-common（通用工具）
- ✅ Lombok（编译时注解）
- ✅ Hutool（工具类）
- ✅ Jackson（JSON 序列化）

**禁止的依赖**：
- ❌ Spring Framework
- ❌ MyBatis / JPA
- ❌ Servlet API
- ❌ 任何基础设施框架

---

## 💡 使用示例

### 示例 1：创建 MeSH 导入任务

```java
// 初始化表进度列表
List<TableProgress> tableProgressList = List.of(
    new TableProgress("descriptor", MeshTableImportStatus.PENDING, 35000, 0, 0),
    new TableProgress("qualifier", MeshTableImportStatus.PENDING, 100, 0, 0),
    new TableProgress("tree-number", MeshTableImportStatus.PENDING, 80000, 0, 0),
    new TableProgress("entry-term", MeshTableImportStatus.PENDING, 250000, 0, 0),
    new TableProgress("concept", MeshTableImportStatus.PENDING, 180000, 0, 0)
);

// 创建导入任务聚合根
MeshImportAggregate aggregate = new MeshImportAggregate(
    null, // ID 由仓储生成
    "2025年MeSH数据导入",
    MeshImportTaskStatus.PENDING,
    null, null,
    "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml",
    null, null,
    tableProgressList,
    0, 0, 0, null
);

// 开始导入（状态转换：PENDING → PROCESSING）
aggregate.startImport();

// 持久化（通过仓储）
meshImportPort.save(aggregate);
```

### 示例 2：更新导入进度（断点续传）

```java
// 从仓储加载任务
MeshImportAggregate aggregate = meshImportPort.findById(taskId).orElseThrow();

// 更新 descriptor 表进度（已处理 5000 条，批次索引 5）
aggregate.updateTableProgress("descriptor", 5000, 5);

// 持久化更新
meshImportPort.save(aggregate);

// 查询整体进度
double progress = aggregate.calculateOverallProgress(); // 返回百分比（如 12.5%）
```

### 示例 3：处理导入完成

```java
// 从仓储加载任务
MeshImportAggregate aggregate = meshImportPort.findById(taskId).orElseThrow();

// 标记任务完成（发布 MeshImportCompleted 事件）
aggregate.markAsCompleted();

// 持久化并发布事件
meshImportPort.save(aggregate);

// 获取领域事件
List<DomainEvent> events = aggregate.getDomainEvents();
// 通过事件总线发布到其他模块
```

### 示例 4：创建 MeSH 主题词聚合根

```java
// 创建主题词聚合根
MeshDescriptorAggregate descriptor = MeshDescriptorAggregate.builder()
    .descriptorUI("D000001")
    .descriptorName("Calcimycin")
    .descriptorClass(DescriptorClass.CLASS_1)
    .treeNumbers(List.of(
        new MeshTreeNumber("D03.438.221.173"),
        new MeshTreeNumber("D23.946.123.632.121")
    ))
    .entryTerms(List.of(
        new MeshEntryTerm("A-23187", LexicalTag.ENTRY),
        new MeshEntryTerm("Antibiotic A23187", LexicalTag.ENTRY)
    ))
    .concepts(List.of())
    .build();

// 持久化
meshDescriptorPort.save(descriptor);
```

---

## 🧪 测试覆盖

| 测试类型 | 覆盖率目标 | 当前覆盖率 |
|---------|-----------|-----------|
| 单元测试 | ≥80% | [待测试运行后更新] |
| 领域模型测试 | 100%（核心业务规则） | [待测试运行后更新] |

**关键测试类**：
- `MeshImportAggregateTest` - 导入任务状态机测试
- `TableProgressTest` - 进度计算测试
- `MeshImportIdTest` - 强类型 ID 测试

---

## 🛠️ 技术栈

- **Java**：25（利用 Record、Sealed Interface、Pattern Matching）
- **Lombok**：编译时注解（@Data、@Builder、@Value、@RequiredArgsConstructor）
- **Hutool**：工具类（StrUtil、CollUtil、DateUtil）
- **Jackson**：JSON 序列化/反序列化

---

## 📚 相关文档

- [patra-catalog 模块总览](../README.md)
- [patra-catalog-app 应用层文档](../patra-catalog-app/README.md)
- [patra-catalog-infra 基础设施层文档](../patra-catalog-infra/README.md)
- [MeSH 导入功能规格](../../specs/001-mesh-data-import/spec.md)
- [MeSH 数据模型设计](../../specs/001-mesh-data-import/data-model.md)

---

**最后更新**：2025-11-22
**Maven 坐标**：`com.patra:patra-catalog-domain:0.2.0-SNAPSHOT`
**作者**：Patra Team
