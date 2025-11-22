# patra-catalog-api — 目录管理对外契约

## 📋 概述

`patra-catalog-api` 是 patra-catalog 服务的**对外契约层（API Layer）**，定义了供其他服务调用的接口、DTO 和错误码。

本模块在六边形架构中扮演**契约定义**角色，遵循以下原则：
- **稳定契约**：API 一旦发布，需保持向后兼容
- **语义化版本**：使用语义化版本控制（SemVer）
- **最小依赖**：不依赖任何框架，只包含纯 Java 对象和接口
- **文档先行**：API 设计优先于实现

**注意**：本模块**不包含 REST Controller**，REST API 定义在 `patra-catalog-adapter` 模块。本模块定义的是**供其他服务调用的 Java 接口契约**（如 Feign Client）。

---

## 🏗️ 模块结构

```
patra-catalog-api/
└─ src/main/java/.../api/
   ├─ dto/                         # 数据传输对象
   │  └─ MeshProgressDTO.java                 # MeSH 导入进度 DTO（规划中）
   ├─ endpoint/                    # Feign Client 接口（规划中）
   │  └─ CatalogEndpoint.java                 # 目录管理接口
   └─ error/                       # 错误码定义（规划中）
      └─ CatalogErrorCode.java                # 目录管理错误码
```

---

## 🔑 核心职责

### 1. DTO 定义

**职责**：定义服务间通信的数据传输对象。

**设计原则**：
- **不可变**：使用 `@Value` 或 Record 确保不可变性
- **序列化友好**：支持 JSON 序列化/反序列化
- **文档完整**：每个字段都有 JavaDoc 说明
- **验证规则**：包含 Bean Validation 注解（如 `@NotNull`）

### 2. 接口定义（规划中）

**职责**：定义供其他服务调用的 Feign Client 接口。

**设计原则**：
- **幂等性**：所有接口支持幂等调用
- **错误码**：定义明确的错误码
- **版本控制**：接口路径包含版本号（如 `/api/v1/...`）

### 3. 错误码定义（规划中）

**职责**：定义服务特定的错误码。

**错误码规范**：
- **格式**：`CATALOG-XXXX`
- **分类**：
  - `0XXX`：成功
  - `4XXX`：客户端错误
  - `5XXX`：服务器错误

---

## 🎯 核心组件

### 1. MeshProgressDTO (MeSH 导入进度 DTO)

**职责**：传输 MeSH 导入进度信息。

**字段**：
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeshProgressDTO {

    /// 任务 ID
    @NotNull
    private Long taskId;

    /// 任务名称
    @NotBlank
    private String taskName;

    /// 任务状态
    @NotNull
    private String status;

    /// 整体进度百分比（0-100）
    @Min(0)
    @Max(100)
    private Double overallProgress;

    /// 各表导入进度
    private List<TableProgressDTO> tableProgressList;

    /// 开始时间
    private Instant startedAt;

    /// 完成时间
    private Instant completedAt;

    /// 错误信息（如果失败）
    private String errorMessage;
}
```

**文件**：`dto/MeshProgressDTO.java`

### 2. CatalogEndpoint (目录管理接口 - 规划中)

**职责**：定义供其他服务调用的 Feign Client 接口。

**示例**：
```java
@FeignClient(name = "patra-catalog", path = "/api/v1/catalog")
public interface CatalogEndpoint {

    /// 查询 MeSH 导入进度
/// 
/// @param taskId 任务 ID
/// @return 导入进度 DTO
    @GetMapping("/mesh/import/progress/{taskId}")
    MeshProgressDTO queryProgress(@PathVariable("taskId") Long taskId);

    /// 开始 MeSH 导入任务
/// 
/// @param request 导入请求
/// @return 导入结果 DTO
    @PostMapping("/mesh/import/start")
    MeshImportResultDTO startImport(@RequestBody StartImportRequest request);
}
```

**文件**：`endpoint/CatalogEndpoint.java`（规划中）

### 3. CatalogErrorCode (目录管理错误码 - 规划中)

**职责**：定义目录管理服务的错误码。

**示例**：
```java
public enum CatalogErrorCode implements ErrorCodeLike {

    // 成功
    SUCCESS("CATALOG-0000", "成功"),

    // 客户端错误
    INVALID_PARAMS("CATALOG-0400", "参数错误"),
    TASK_NOT_FOUND("CATALOG-0404", "任务不存在"),
    TASK_ALREADY_RUNNING("CATALOG-0409", "已有正在运行的任务"),

    // 服务器错误
    IMPORT_FAILED("CATALOG-0500", "导入失败"),
    FILE_DOWNLOAD_FAILED("CATALOG-0501", "文件下载失败"),
    XML_PARSE_FAILED("CATALOG-0502", "XML 解析失败");

    private final String code;
    private final String message;

    CatalogErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
```

**文件**：`error/CatalogErrorCode.java`（规划中）

---

## 📦 依赖关系

### 上游依赖

- **无框架依赖**：只包含纯 Java 对象和接口
- `Lombok`：编译时注解（@Data、@Builder、@Value）
- `Spring Validation`：Bean Validation 注解（@NotNull、@NotBlank、@Min、@Max）
- `Spring Cloud OpenFeign`：@FeignClient 注解（规划中）

### 下游消费者

- **其他微服务**：通过 Feign Client 调用 patra-catalog 服务
- **前端应用**：通过 REST API 调用 patra-catalog 服务（实际由 `patra-catalog-adapter` 提供）

**依赖方向**：其他服务 → patra-catalog-api（符合契约优先原则）

---

## 💡 使用示例

### 示例 1：其他服务调用 patra-catalog（Feign Client）

```java
@FeignClient(name = "patra-catalog", path = "/api/v1/catalog")
public interface CatalogEndpoint {

    @GetMapping("/mesh/import/progress/{taskId}")
    MeshProgressDTO queryProgress(@PathVariable("taskId") Long taskId);
}
```

**调用方**：
```java
@Service
@RequiredArgsConstructor
public class SomeService {

    private final CatalogEndpoint catalogEndpoint;

    public void checkMeshImportProgress(Long taskId) {
        MeshProgressDTO progress = catalogEndpoint.queryProgress(taskId);
        log.info("MeSH 导入进度：{}%", progress.getOverallProgress());
    }
}
```

### 示例 2：DTO 使用示例

```java
// 构建 DTO
MeshProgressDTO dto = MeshProgressDTO.builder()
    .taskId(123L)
    .taskName("2025年MeSH数据导入")
    .status("PROCESSING")
    .overallProgress(45.5)
    .startedAt(Instant.now())
    .build();

// JSON 序列化
ObjectMapper objectMapper = new ObjectMapper();
String json = objectMapper.writeValueAsString(dto);

// JSON 反序列化
MeshProgressDTO parsedDto = objectMapper.readValue(json, MeshProgressDTO.class);
```

---

## 🎨 设计模式

### 1. 契约优先模式 (Contract-First Pattern)

**核心思想**：先定义 API 契约，再实现服务。

**优点**：
- 并行开发（服务提供方和调用方可并行开发）
- 接口稳定（契约一旦定义，实现可随时替换）
- 文档自动生成（基于契约生成 API 文档）

### 2. DTO 模式 (Data Transfer Object Pattern)

**核心思想**：使用专门的对象在服务间传输数据，避免暴露内部领域模型。

**优点**：
- 解耦（DTO 与领域模型独立演化）
- 序列化友好（DTO 专门为序列化设计）
- 向后兼容（DTO 可通过版本控制保持兼容）

---

## 🧪 测试覆盖

| 测试类型 | 覆盖率目标 | 当前覆盖率 |
|---------|-----------|-----------|
| 单元测试 | 100%（DTO 序列化/反序列化） | [待测试运行后更新] |

**关键测试类**：
- `MeshProgressDTOTest` - DTO 序列化/反序列化测试

---

## 🛠️ 技术栈

- **Java**：25
- **Lombok**：编译时注解
- **Spring Validation**：Bean Validation（JSR-303）
- **Spring Cloud OpenFeign**：@FeignClient（规划中）
- **Jackson**：JSON 序列化/反序列化

---

## 📚 相关文档

- [patra-catalog 模块总览](../README.md)
- [MeSH 导入 API 规范](../../specs/001-mesh-data-import/contracts/mesh-import-api.yaml)
- [语义化版本控制规范](https://semver.org/lang/zh-CN/)
- [Feign 官方文档](https://spring.io/projects/spring-cloud-openfeign)

---

**最后更新**：2025-11-22
**Maven 坐标**：`com.patra:patra-catalog-api:0.2.0-SNAPSHOT`
**作者**：Patra Team
