# Controller 开发模式

## 核心原则

### ✅ Controller 的唯一职责
1. **接收 HTTP 请求**并验证格式（`@Valid`）
2. **通过 Converter 转换** Request → Command（反腐层）
3. **调用 CommandBus**（写操作）或 **QueryService**（查询操作）
4. **通过 Converter 转换** Result → Response
5. **返回业务数据**（不使用 ResponseEntity）
6. **异常由全局处理器处理**（不在 Controller 层 try-catch）

### ❌ Controller 禁止做的事
- ❌ 包含业务逻辑（留给 Handler/QueryService）
- ❌ 直接调用 Repository
- ❌ 直接返回领域对象（使用 Response DTO）
- ❌ 使用 ResponseEntity 包装（直接返回业务数据）
- ❌ 在方法内 try-catch 异常
- ❌ 直接将 Request 传递给 Application 层（必须转换为 Command）

---

## 分层与转换

### 架构分层（六边形架构）

```
┌─────────────────────────────────────────────────────────────┐
│ Adapter 层 (外部协议)                                         │
│ - CreateResourceRequest  (HTTP 请求体)                       │
│ - ResourceResponse       (HTTP 响应体)                       │
└─────────────────────────────────────────────────────────────┘
                              ↓ Converter (反腐层)
┌─────────────────────────────────────────────────────────────┐
│ Application 层 (应用逻辑)                                     │
│ - CreateResourceCommand  (用例输入)                          │
│ - ResourceResult         (用例输出)                          │
└─────────────────────────────────────────────────────────────┘
                              ↓ Handler / QueryService
┌─────────────────────────────────────────────────────────────┐
│ Domain 层 (业务逻辑)                                          │
│ - Resource               (聚合根)                            │
│ - ResourceCreated        (领域事件)                          │
└─────────────────────────────────────────────────────────────┘
```

### 为什么需要分层？

**Request/Response DTO** （Adapter 层）：
- 属于外部协议（HTTP、gRPC、GraphQL）
- 包含协议特定的校验注解（`@NotBlank`、`@Size`）
- 可能包含协议特定的字段（如 `@JsonProperty`）
- 变化频繁（前端需求变化）

**Command/Result DTO** （Application 层）：
- 属于应用逻辑边界
- 表达用例的输入和输出
- 独立于外部协议
- 相对稳定

**反腐层（Anti-Corruption Layer）**：
- 使用 MapStruct Converter 隔离外部协议和应用逻辑
- 防止外部协议变化污染应用层
- 支持多种协议接入（REST、gRPC、MQ）

---

## 标准 Controller 模板

基于实际代码 `MeshImportController`：

```java
@Slf4j
@RestController
@RequestMapping("/api/v1/resources")
@RequiredArgsConstructor
@Validated
public class ResourceController {

  private final CommandBus commandBus;              // 写操作
  private final ResourceQueryService queryService;  // 查询操作
  private final ResourceApiConverter converter;     // MapStruct 转换器

  /// 方法的注释
  @PostMapping
  public ResourceResponse create(@Valid @RequestBody CreateResourceRequest request) {
    log.info("收到创建资源请求，名称：{}", request.name());

    // 1. Request → Command（反腐层）
    CreateResourceCommand command = converter.toCommand(request);

    // 2. 调用 CommandBus（写操作）
    ResourceResult result = commandBus.handle(command);

    // 3. Result → Response
    return converter.toResponse(result);
  }

  /// GET 查询资源
  @GetMapping("/{id}")
  public ResourceResponse getById(@PathVariable @NotNull Long id) {
    log.info("查询资源，ID：{}", id);

    ResourceQuery result = queryService.findById(id);

    return converter.toResponse(result);
  }

  /// PUT 更新资源
  @PutMapping("/{id}")
  public ResourceResponse update(
      @PathVariable @NotNull Long id,
      @Valid @RequestBody UpdateResourceRequest request) {
    log.info("更新资源，ID：{}", id);

    UpdateResourceCommand command = converter.toCommand(id, request);
    ResourceResult result = commandBus.handle(command);

    return converter.toResponse(result);
  }

  /// DELETE 删除资源
  @DeleteMapping("/{id}")
  public void delete(@PathVariable @NotNull Long id) {
    log.info("删除资源，ID：{}", id);
    commandBus.handle(new DeleteResourceCommand(id));
  }

  /// 内部 DTO（使用 record）
  public record CreateResourceRequest(
      @NotBlank(message = "名称不能为空")
      @Size(max = 100, message = "名称最长 100 字符")
      String name,

      @NotNull(message = "类型不能为空")
      String type) {}

  public record UpdateResourceRequest(
      @NotBlank String name,
      @NotNull String type) {}

  public record ResourceResponse(
      Long id,
      String name,
      String type,
      LocalDateTime createdAt) {}
}
```

---

## MapStruct Converter 模板

### Converter 接口

```java
package com.patra.catalog.adapter.rest.converter;

import com.patra.catalog.adapter.rest.ResourceController.CreateResourceRequest;
import com.patra.catalog.adapter.rest.ResourceController.UpdateResourceRequest;
import com.patra.catalog.adapter.rest.ResourceController.ResourceResponse;
import com.patra.catalog.app.usecase.resource.dto.CreateResourceCommand;
import com.patra.catalog.app.usecase.resource.dto.UpdateResourceCommand;
import com.patra.catalog.app.usecase.resource.dto.ResourceResult;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/// 资源 API 转换器（反腐层）。
///
/// **职责**：
///
/// - 将 HTTP 请求 DTO（Request）转换为应用层命令（Command）
///   - 将应用层结果（Result）转换为 HTTP 响应 DTO（Response）
///
/// **设计原则**：
///
/// - Request/Response 属于 Adapter 层（外部协议）
///   - Command/Result 属于 Application 层（应用逻辑）
///   - Converter 是反腐层（Anti-Corruption Layer），隔离外部协议和应用逻辑
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ResourceApiConverter {

  /// 将创建请求转换为创建命令。
  ///
  /// @param request HTTP 请求 DTO
  /// @return 应用层命令 DTO
  CreateResourceCommand toCommand(CreateResourceRequest request);

  /// 将更新请求转换为更新命令。
  ///
  /// @param id 资源 ID（来自路径参数）
  /// @param request HTTP 请求 DTO
  /// @return 应用层命令 DTO
  @Mapping(target = "id", source = "id")
  UpdateResourceCommand toCommand(Long id, UpdateResourceRequest request);

  /// 将应用层结果转换为响应 DTO。
  ///
  /// @param result 应用层结果 DTO
  /// @return HTTP 响应 DTO
  ResourceResponse toResponse(ResourceResult result);

  /// 将应用层结果列表转换为响应 DTO 列表。
  ///
  /// @param results 应用层结果列表
  /// @return HTTP 响应 DTO 列表
  List<ResourceResponse> toResponse(List<ResourceResult> results);
}
```

### 使用示例

```java
// Controller 中使用
@PostMapping
public ResourceResponse create(@Valid @RequestBody CreateResourceRequest request) {
  // ✅ 通过 Converter 转换
  CreateResourceCommand command = converter.toCommand(request);

  ResourceResult result = commandBus.handle(command);

  // ✅ 通过 Converter 转换
  return converter.toResponse(result);
}
```

---

## 分页列表查询模板

查询 Controller 不注入 CommandBus，仅使用 QueryService + ApiConverter：

```java
@RestController
@RequestMapping("/venues")
@RequiredArgsConstructor
public class VenueController {

    private final VenueQueryService venueQueryService;
    private final VenueApiConverter venueApiConverter;

    /// 查询 Venue 分页列表。
    @GetMapping
    public PageResult<VenueItemResponse> listVenues(VenueListRequest request) {
        VenueListQuery query = venueApiConverter.toQuery(request);
        return venueQueryService.listVenues(query).map(venueApiConverter::toItemResponse);
    }
}
```

**关键要点**：
- 不注入 CommandBus（纯查询 Controller）
- 使用 `PageResult.map()` 进行跨层类型转换
- 数据流：Request → Query → PageResult\<ReadModel> → PageResult\<ItemResponse>
- Request/Response 使用独立子包（`request/`、`response/`）

### 查询 Controller 子包结构

```
adapter/rest/{entity}/
├── {Entity}Controller.java
├── request/{Entity}ListRequest.java      # 查询参数 DTO
├── response/{Entity}ItemResponse.java    # 列表项响应 DTO
└── mapper/{Entity}ApiConverter.java      # MapStruct 转换器
```

### ApiConverter 查询端模板

```java
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface VenueApiConverter {

    /// 将列表请求转换为查询参数。
    VenueListQuery toQuery(VenueListRequest request);

    /// 将读模型转换为列表项响应。
    VenueItemResponse toItemResponse(VenueSummaryReadModel readModel);
}
```

---

## 常见模式

### 1. 参数校验

```java
/// 路径参数校验
@GetMapping("/{id}")
public ResourceResponse getById(@PathVariable @NotNull Long id) {
  ResourceQuery result = queryService.findById(id);
  return converter.toResponse(result);
}

/// 请求体校验
@PostMapping
public ResourceResponse create(@Valid @RequestBody CreateResourceRequest request) {
  CreateResourceCommand command = converter.toCommand(request);
  ResourceResult result = commandBus.handle(command);
  return converter.toResponse(result);
}

/// 查询参数校验
@GetMapping
public List<ResourceResponse> search(@Valid ResourceSearchQuery query) {
  List<ResourceQuery> results = queryService.search(query);
  return converter.toResponse(results);
}
```

**校验注解**：
- `@NotNull` - 不能为 null
- `@NotBlank` - 不能为空字符串
- `@Size(min=1, max=100)` - 长度限制
- `@Pattern(regexp="...")` - 正则匹配
- `@Valid` - 嵌套对象校验

### 2. 请求/响应 DTO

```java
/// 请求 DTO（Adapter 层）
public record CreateResourceRequest(
    @NotBlank(message = "名称不能为空")
    @Size(max = 100, message = "名称最长 100 字符")
    String name,

    @NotNull(message = "类型不能为空")
    String type
) {}

/// 响应 DTO（Adapter 层）
public record ResourceResponse(
    Long id,
    String name,
    String type,
    LocalDateTime createdAt
) {}
```

### 3. 文件上传

```java
@PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public UploadResponse upload(@RequestParam("file") MultipartFile file) {
  if (file.isEmpty()) {
    throw new IllegalArgumentException("文件不能为空");
  }

  UploadCommand command = converter.toCommand(file);
  UploadResult result = commandBus.handle(command);

  return converter.toResponse(result);
}
```

### 4. 批量操作

```java
@PostMapping("/batch")
public List<ResourceResponse> batchCreate(
    @Valid @RequestBody @Size(min = 1, max = 100) List<CreateResourceRequest> requests
) {
  List<CreateResourceCommand> commands = converter.toCommands(requests);
  List<ResourceResult> results = commandBus.handle(new BatchCreateResourceCommand(commands));
  return converter.toResponse(results);
}

@DeleteMapping("/batch")
public void batchDelete(@RequestParam("ids") @NotEmpty Set<Long> ids) {
  commandBus.handle(new BatchDeleteResourceCommand(ids));
}
```

---

## 异常处理策略

### ✅ 正确做法：直接抛出异常

```java
@PostMapping("/start")
public TaskResponse startTask(@Valid @RequestBody StartTaskRequest request) {
  // ✅ 直接抛出异常，让全局处理器处理
  if (taskRepository.hasRunningTask()) {
    throw new IllegalStateException("已有任务正在运行");
  }

  StartTaskCommand command = converter.toCommand(request);
  TaskResult result = commandBus.handle(command);

  return converter.toResponse(result);
}
```

### ❌ 错误做法：在 Controller 捕获异常

```java
@PostMapping("/start")
public TaskResponse startTask(@Valid @RequestBody StartTaskRequest request) {
  // ❌ 不要在 Controller 捕获异常
  try {
    StartTaskCommand command = converter.toCommand(request);
    TaskResult result = commandBus.handle(command);
    return converter.toResponse(result);
  } catch (Exception e) {
    log.error("任务启动失败", e);
    throw new RuntimeException("任务启动失败");
  }
}
```

### 异常映射（由全局处理器处理）

```
IllegalStateException         → 409 Conflict
IllegalArgumentException      → 400 Bad Request / 404 Not Found
ConstraintViolationException  → 400 Bad Request
DomainException               → 422 Unprocessable Entity
```

---

## 响应设计原则

### HTTP 语义化设计

使用 HTTP 状态码表达成功/失败，不需要响应体中的 `code` 字段：

```java
// ✅ 成功：200 + 业务数据
@GetMapping("/{id}")
public ResourceResponse getById(@PathVariable Long id) {
  ResourceQuery result = queryService.findById(id);
  return converter.toResponse(result);  // 200 OK
}

// ✅ 失败：全局处理器自动返回 ProblemDetail
// 例如：404 + {"type":"...", "title":"资源不存在", "status":404}
```

### 前端对接方式

```typescript
// 统一通过 HTTP 状态码判断成功/失败
async function getResource(id: number): Promise<ResourceResponse> {
  const response = await fetch(`/api/v1/resources/${id}`);

  if (!response.ok) {
    // 失败：解析 RFC7807 ProblemDetail
    const problem = await response.json();
    throw new ApiError(problem);
  }

  // 成功：直接解析业务数据
  return response.json();
}
```

---

## 常见错误对比

### 1. Request → Command 转换缺失

```java
// ❌ 错误：直接将 Request 传递给 Application 层
@PostMapping
public ResourceResponse create(@Valid @RequestBody CreateResourceRequest request) {
  // ❌ Request 属于 Adapter 层，不应该传入 Application 层
  ResourceResult result = commandBus.handle(request);  // 错误！
  return converter.toResponse(result);
}

// ✅ 正确：通过 Converter 转换
@PostMapping
public ResourceResponse create(@Valid @RequestBody CreateResourceRequest request) {
  // ✅ 通过反腐层转换
  CreateResourceCommand command = converter.toCommand(request);
  ResourceResult result = commandBus.handle(command);
  return converter.toResponse(result);
}
```

### 2. 业务逻辑位置

```java
// ❌ 错误：Controller 包含业务逻辑
@PostMapping
public ResourceResponse create(@RequestBody CreateResourceRequest request) {
  if (repository.existsByName(request.name())) {
    throw new DuplicateException("名称已存在");
  }
  var entity = converter.toEntity(request);
  entity.setCreatedAt(LocalDateTime.now());
  repository.save(entity);
  return converter.toResponse(entity);
}

// ✅ 正确：委托给 CommandBus
@PostMapping
public ResourceResponse create(@Valid @RequestBody CreateResourceRequest request) {
  CreateResourceCommand command = converter.toCommand(request);
  ResourceResult result = commandBus.handle(command);
  return converter.toResponse(result);
}
```

### 3. 返回值类型

```java
// ❌ 错误：直接返回领域对象
@GetMapping("/{id}")
public Resource getById(@PathVariable Long id) {
  return resourceService.findById(id);
}

// ✅ 正确：返回 Response DTO
@GetMapping("/{id}")
public ResourceResponse getById(@PathVariable Long id) {
  ResourceQuery result = queryService.findById(id);
  return converter.toResponse(result);
}
```

### 4. 响应包装

```java
// ❌ 错误：使用 ResponseEntity 包装
@GetMapping("/{id}")
public ResponseEntity<ResourceResponse> getById(@PathVariable Long id) {
  ResourceQuery result = queryService.findById(id);
  ResourceResponse response = converter.toResponse(result);
  return ResponseEntity.ok(response);
}

// ✅ 正确：直接返回业务数据
@GetMapping("/{id}")
public ResourceResponse getById(@PathVariable Long id) {
  ResourceQuery result = queryService.findById(id);
  return converter.toResponse(result);
}
```

---

## 检查清单

开发 Controller 时，确保满足以下条件：

- [ ] 类注解：`@RestController` + `@RequestMapping` + `@RequiredArgsConstructor` + `@Validated`
- [ ] 依赖注入：使用 `final` 字段 + 构造器注入（Lombok 自动生成）
- [ ] **反腐层转换**：注入 MapStruct `Converter`，Request → Command → Result → Response
- [ ] 方法返回：直接返回 Response DTO，不使用 `ResponseEntity`
- [ ] 参数校验：使用 `@Valid` + `@NotNull` 等校验注解
- [ ] 异常处理：直接抛出异常，不在方法内 try-catch
- [ ] 日志记录：使用 `@Slf4j` + 关键操作日志
- [ ] JavaDoc：使用 `///` 格式注释每个方法
- [ ] DTO 类型：使用 `record` 定义 Request/Response 模型
- [ ] **禁止直传**：Request/Response 不得传入 Application 层或从 Domain 层返回
