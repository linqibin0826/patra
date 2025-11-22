# patra-catalog-app — 目录管理应用层

## 📋 概述

`patra-catalog-app` 是 patra-catalog 服务的**应用层（Application Layer）**，负责用例编排（Use Case Orchestration），协调 Domain 层和 Infrastructure 层，管理事务边界。

本模块在六边形架构中位于**中间层**，遵循以下原则：
- **薄应用层**：不包含业务逻辑，只编排领域对象和 Port 接口
- **事务边界**：在 Orchestrator 方法上使用 `@Transactional` 定义事务边界
- **依赖倒置**：通过 Port 接口调用 Infrastructure 层，不直接依赖技术实现
- **异常映射**：通过 `ErrorMappingContributor` SPI 将领域异常映射为 HTTP 状态码

---

## 🏗️ 模块结构

```
patra-catalog-app/
└─ src/main/java/.../app/
   ├─ usecase/                     # 用例编排器
   │  └─ meshimport/               # MeSH 导入用例
   │     ├─ MeshImportOrchestrator.java           # MeSH 导入编排器（核心）
   │     ├─ MeshProgressQueryOrchestrator.java    # 进度查询编排器
   │     ├─ command/                              # 命令对象
   │     │  └─ StartImportCommand.java            # 开始导入命令
   │     ├─ dto/                                  # 数据传输对象
   │     │  └─ MeshImportResultDTO.java           # 导入结果 DTO
   │     └─ validator/                            # 数据验证器
   │        └─ MeshDataValidator.java             # MeSH 数据量验证器
   ├─ error/                       # 异常映射
   │  └─ MeshImportErrorMappingContributor.java   # MeSH 导入异常映射贡献者
   └─ config/                      # 配置类
      └─ MeshImportConfig.java                    # MeSH 导入配置属性
```

---

## 🔑 核心职责

### 1. 用例编排

**职责**：编排复杂业务流程，调用 Domain 层和 Infrastructure 层完成业务目标。

**示例用例**：
- MeSH 数据首次导入
- MeSH 数据增量更新
- 导入进度查询
- 失败任务重试
- 清除数据重新开始

### 2. 事务管理

**职责**：定义事务边界（`@Transactional`），确保数据一致性。

**事务策略**：
- **导入任务创建**：整个导入流程在一个事务中（包括下载、解析、持久化）
- **进度更新**：独立事务，支持断点续传
- **批量导入**：使用 `@Transactional(propagation = Propagation.REQUIRES_NEW)` 确保批次隔离

### 3. 异常映射

**职责**：将领域异常映射为 HTTP 状态码和错误码。

**映射规则**：
| 异常类型 | HTTP 状态码 | 错误码 | 说明 |
|---------|-----------|--------|------|
| `IllegalStateException` | 409 Conflict | CATALOG-0409 | 业务状态冲突（如已有运行中任务） |
| `IllegalArgumentException`（"任务不存在"） | 404 Not Found | CATALOG-0404 | 资源不存在 |
| `IllegalArgumentException`（其他） | 400 Bad Request | CATALOG-0400 | 参数错误 |
| `RuntimeException` | 500 Internal Server Error | CATALOG-0500 | 服务器内部错误 |

### 4. 数据验证

**职责**：协调数据验证器，验证导入数据的完整性和一致性。

**验证维度**：
- **数据量验证**：检查各表导入记录数是否在预期范围内（允许 ±5% 偏差）
- **完整性验证**：检查关联数据是否完整（如主题词是否有树形编号）
- **一致性验证**：检查数据是否与 MeSH 官方规范一致

### 5. DTO 转换

**职责**：将领域对象转换为 DTO，供 Adapter 层使用。

**转换方向**：
- Domain → DTO（查询场景）
- Command → Domain（命令场景）

---

## 🎯 核心组件

### 1. MeshImportOrchestrator (MeSH 导入编排器)

**职责**：编排 MeSH 数据导入的完整流程。

**核心方法**：

#### startImport(StartImportCommand command)

开始 MeSH 数据导入任务。

**流程**：
```
1. 前置检查：检查是否已有运行中的任务
2. 创建任务聚合根：初始化任务状态和表进度
3. 下载 XML 文件：从 NLM 官网下载 MeSH 数据文件
4. 校验文件：验证文件完整性（文件大小、MD5 校验）
5. 开始导入：状态转换（PENDING → PROCESSING）
6. 解析并批量导入数据：
   - descriptor（主题词）~35,000 条
   - qualifier（限定词）~100 条
   - tree-number（树形编号）~80,000 条
   - entry-term（入口术语）~250,000 条
   - concept（概念）~180,000 条
7. 验证数据量：检查各表数据量是否正常
8. 标记任务完成：状态转换（PROCESSING → COMPLETED）
9. 发布领域事件：MeshImportCompleted
10. 返回结果 DTO
```

**事务策略**：`@Transactional`（整个流程在一个事务中）

**异常处理**：
- 捕获所有异常，标记任务为失败（FAILED）
- 发布 `MeshImportFailed` 事件
- 向上抛出 `RuntimeException`，由全局异常处理器处理

**文件**：`usecase/meshimport/MeshImportOrchestrator.java`

#### retryFailedTask(MeshImportId taskId)

重试失败的导入任务。

**流程**：
```
1. 加载任务聚合根
2. 校验任务状态（必须是 FAILED）
3. 重置任务状态（FAILED → PENDING）
4. 调用 startImport() 重新执行
```

**文件**：`usecase/meshimport/MeshImportOrchestrator.java`

#### clearAndRestart()

清除所有 MeSH 数据，重新开始导入。

**流程**：
```
1. 检查是否有运行中的任务（有则拒绝）
2. 清空所有 MeSH 数据表
3. 删除所有导入任务记录
4. 创建新导入任务
5. 开始导入
```

**警告**：此操作会删除所有 MeSH 数据，仅用于首次导入或数据完全损坏时。

**文件**：`usecase/meshimport/MeshImportOrchestrator.java`

### 2. MeshProgressQueryOrchestrator (进度查询编排器)

**职责**：查询 MeSH 导入任务的进度信息。

**核心方法**：

#### queryProgress(MeshImportId taskId)

查询指定任务的导入进度。

**返回信息**：
- 任务整体进度百分比
- 各表导入状态和进度
- 当前处理的批次信息
- 预计剩余时间

**文件**：`usecase/meshimport/MeshProgressQueryOrchestrator.java`

### 3. MeshDataValidator (数据量验证器)

**职责**：验证导入数据的数量是否在预期范围内。

**核心方法**：

#### validateDataCounts(Map<String, Integer> actualCounts)

验证各表数据量。

**预期数据量**（2025 版 MeSH）：
| 表名 | 预期记录数 | 允许偏差 |
|-----|----------|---------|
| descriptor | 35,000 | ±5% (33,250 ~ 36,750) |
| qualifier | 100 | ±5% (95 ~ 105) |
| tree-number | 80,000 | ±5% (76,000 ~ 84,000) |
| entry-term | 250,000 | ±5% (237,500 ~ 262,500) |
| concept | 180,000 | ±5% (171,000 ~ 189,000) |

**验证逻辑**：
- 如果实际数量在允许偏差范围内 → 通过
- 如果超出偏差范围 → 记录警告（但不阻止导入完成）

**文件**：`usecase/meshimport/validator/MeshDataValidator.java`

### 4. MeshImportErrorMappingContributor (异常映射贡献者)

**职责**：将 MeSH 导入相关异常映射为 HTTP 状态码和错误码。

**实现方式**：实现 `ErrorMappingContributor` SPI 接口。

**映射逻辑**：
```java
@Override
public Optional<ErrorCodeLike> mapException(Throwable exception) {
    // IllegalStateException → 409 Conflict
    if (exception instanceof IllegalStateException) {
        return Optional.of(SimpleErrorCode.create("CATALOG", "0409"));
    }

    // IllegalArgumentException（"任务不存在"）→ 404 Not Found
    if (exception instanceof IllegalArgumentException ex) {
        String message = ex.getMessage();
        if (message != null && message.contains("任务不存在")) {
            return Optional.of(SimpleErrorCode.create("CATALOG", "0404"));
        }
        // 其他 IllegalArgumentException → 400 Bad Request
        return Optional.of(SimpleErrorCode.create("CATALOG", "0400"));
    }

    return Optional.empty();
}
```

**文件**：`error/MeshImportErrorMappingContributor.java`

---

## 📦 依赖关系

### 上游依赖

- `patra-catalog-domain`：领域模型和 Port 接口
- `patra-common-core`：通用工具和错误码
- `Spring Boot`：@Service、@Transactional 等
- `Lombok`：@RequiredArgsConstructor

### 下游消费者

- `patra-catalog-adapter`：适配器层（REST Controller、Job Executor）

**依赖方向**：Domain ← App ← Adapter（符合六边形架构）

---

## 💡 使用示例

### 示例 1：开始 MeSH 导入

```java
@Service
@RequiredArgsConstructor
public class MeshImportOrchestrator {

    private final MeshImportPort meshImportPort;
    private final XmlParserPort xmlParserPort;
    private final MeshFileDownloadPort meshFileDownloadPort;
    private final MeshDescriptorPort meshDescriptorPort;
    private final MeshDataValidator meshDataValidator;

    @Transactional
    public MeshImportResultDTO startImport(StartImportCommand command) {
        // 1. 前置检查
        if (meshImportPort.existsRunningTask()) {
            throw new IllegalStateException("已有正在运行的导入任务");
        }

        // 2. 创建任务聚合根
        MeshImportAggregate aggregate = createPendingTask(command.sourceUrl());

        try {
            // 3. 下载 XML 文件
            File xmlFile = meshFileDownloadPort.download(command.sourceUrl());

            // 4. 校验文件完整性
            if (!meshFileDownloadPort.validateChecksum(xmlFile, command.expectedMd5())) {
                throw new IllegalStateException("文件校验失败");
            }

            // 5. 开始导入（状态转换）
            aggregate.startImport();
            aggregate = meshImportPort.save(aggregate);

            // 6. 解析并批量导入数据
            Map<String, Integer> importedCounts = importAllData(xmlFile, aggregate);

            // 7. 验证数据量
            meshDataValidator.validateDataCounts(importedCounts);

            // 8. 标记任务完成
            aggregate.markAsCompleted();
            aggregate = meshImportPort.save(aggregate);

            return buildSuccessResult(aggregate);

        } catch (Exception ex) {
            aggregate.markAsFailed(ex.getMessage());
            meshImportPort.save(aggregate);
            throw new RuntimeException("MeSH 数据导入失败", ex);
        }
    }

    private Map<String, Integer> importAllData(File xmlFile, MeshImportAggregate aggregate) {
        Map<String, Integer> counts = new HashMap<>();

        // 导入 descriptor
        counts.put("descriptor", importDescriptors(xmlFile, aggregate));

        // 导入 qualifier、tree-number、entry-term、concept...
        // ...

        return counts;
    }

    private int importDescriptors(File xmlFile, MeshImportAggregate aggregate) {
        try (InputStream is = new FileInputStream(xmlFile)) {
            Stream<MeshDescriptorAggregate> descriptors = xmlParserPort.parseDescriptors(is);

            AtomicInteger count = new AtomicInteger(0);
            descriptors.forEach(descriptor -> {
                meshDescriptorPort.save(descriptor);
                count.incrementAndGet();

                // 每 1000 条更新一次进度
                if (count.get() % 1000 == 0) {
                    aggregate.updateTableProgress("descriptor", count.get(), count.get() / 1000);
                    meshImportPort.save(aggregate);
                }
            });

            return count.get();
        }
    }
}
```

### 示例 2：异常映射（自动映射到 HTTP 状态码）

```java
@Component
public class MeshImportErrorMappingContributor implements ErrorMappingContributor {

    @Override
    public Optional<ErrorCodeLike> mapException(Throwable exception) {
        // IllegalStateException → 409 Conflict
        if (exception instanceof IllegalStateException) {
            return Optional.of(SimpleErrorCode.create("CATALOG", "0409"));
        }

        // IllegalArgumentException（"任务不存在"）→ 404 Not Found
        if (exception instanceof IllegalArgumentException ex) {
            String message = ex.getMessage();
            if (message != null && message.contains("任务不存在")) {
                return Optional.of(SimpleErrorCode.create("CATALOG", "0404"));
            }
            // 其他 IllegalArgumentException → 400 Bad Request
            return Optional.of(SimpleErrorCode.create("CATALOG", "0400"));
        }

        return Optional.empty();
    }
}
```

### 示例 3：数据量验证器

```java
@Component
public class MeshDataValidator {

    public ValidationResult validateDataCounts(Map<String, Integer> actualCounts) {
        Map<String, Integer> expectedCounts = Map.of(
            "descriptor", 35000,
            "qualifier", 100,
            "tree-number", 80000,
            "entry-term", 250000,
            "concept", 180000
        );

        List<String> warnings = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : actualCounts.entrySet()) {
            String tableName = entry.getKey();
            Integer actual = entry.getValue();
            Integer expected = expectedCounts.get(tableName);

            if (expected != null) {
                double deviation = Math.abs((double) (actual - expected) / expected);
                if (deviation > 0.05) { // 超过 5% 差异
                    warnings.add(String.format(
                        "%s 数据量差异超过 5%%：预期 %d，实际 %d",
                        tableName, expected, actual
                    ));
                }
            }
        }

        return new ValidationResult(warnings);
    }
}
```

---

## 🔧 配置属性

### MeshImportConfig

```java
@Component
@ConfigurationProperties(prefix = "mesh.import")
@Data
public class MeshImportConfig {

    /// MeSH 数据源 URL
    private String sourceUrl = "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml";

    /// 批量插入大小
    private int batchSize = 1000;

    /// 是否启用数据量验证
    private boolean enableDataValidation = true;

    /// 数据量偏差阈值（百分比）
    private double deviationThreshold = 0.05; // 5%

    /// 下载超时时间（秒）
    private int downloadTimeout = 300; // 5 分钟

    /// 是否启用文件校验
    private boolean enableChecksumValidation = true;
}
```

**配置示例**（`application.yml`）：
```yaml
mesh:
  import:
    source-url: https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml
    batch-size: 1000
    enable-data-validation: true
    deviation-threshold: 0.05
    download-timeout: 300
    enable-checksum-validation: true
```

---

## 🎨 设计模式

### 1. 编排器模式 (Orchestrator Pattern)

**核心思想**：编排器负责协调多个领域对象和 Port 接口完成复杂业务流程。

**特点**：
- 编排器本身不包含业务逻辑
- 业务逻辑封装在领域对象中
- 编排器只负责调用和协调

### 2. 命令模式 (Command Pattern)

**核心思想**：将请求封装为命令对象，便于验证、日志记录、重试等。

**示例**：
```java
public record StartImportCommand(
    String sourceUrl,
    String expectedMd5
) {}
```

### 3. SPI 模式 (Service Provider Interface)

**核心思想**：通过 SPI 机制实现异常映射，支持扩展。

**实现**：
- `ErrorMappingContributor` 接口
- Spring 自动扫描所有实现类
- 全局异常处理器自动应用映射规则

---

## 🧪 测试覆盖

| 测试类型 | 覆盖率目标 | 当前覆盖率 |
|---------|-----------|-----------|
| 单元测试 | ≥70% | [待测试运行后更新] |
| 集成测试 | 核心用例 100% | [待测试运行后更新] |

**关键测试类**：
- `MeshImportOrchestratorTest` - 导入流程测试
- `MeshProgressQueryOrchestratorTest` - 进度查询测试
- `MeshDataValidatorTest` - 数据验证测试

---

## 🛠️ 技术栈

- **Spring Boot**：3.5.7
- **Spring Framework**：6.2.x
- **Lombok**：编译时注解
- **MapStruct**：对象转换（规划中）

---

## 📚 相关文档

- [patra-catalog 模块总览](../README.md)
- [patra-catalog-domain 领域层文档](../patra-catalog-domain/README.md)
- [patra-catalog-infra 基础设施层文档](../patra-catalog-infra/README.md)
- [patra-catalog-adapter 适配器层文档](../patra-catalog-adapter/README.md)
- [MeSH 导入功能规格](../../specs/001-mesh-data-import/spec.md)

---

**最后更新**：2025-11-22
**Maven 坐标**：`com.patra:patra-catalog-app:0.2.0-SNAPSHOT`
**作者**：Patra Team
