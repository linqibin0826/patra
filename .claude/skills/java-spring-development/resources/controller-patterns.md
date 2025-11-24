# Controller 开发模式详细指南

## 基础 Controller 模板

```java
@RestController
@RequestMapping("/api/v1/resources")
@RequiredArgsConstructor
@Validated
public class ResourceController {
    private final ResourceOrchestrator orchestrator;

    @PostMapping
    public ResponseEntity<ResourceResponse> create(
        @Valid @RequestBody CreateResourceCommand command
    ) {
        var result = orchestrator.create(command);
        return ResponseEntity.ok(ResourceResponse.from(result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResourceResponse> getById(@PathVariable Long id) {
        return orchestrator.findById(id)
            .map(ResourceResponse::from)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ResourceResponse> update(
        @PathVariable Long id,
        @Valid @RequestBody UpdateResourceCommand command
    ) {
        var result = orchestrator.update(id, command);
        return ResponseEntity.ok(ResourceResponse.from(result));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        orchestrator.delete(id);
    }

    @GetMapping
    public ResponseEntity<PageResponse<ResourceResponse>> search(
        @Valid ResourceQuery query,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        var page = orchestrator.search(query, pageable);
        return ResponseEntity.ok(PageResponse.from(page, ResourceResponse::from));
    }
}
```

## 分页查询模式

```java
@GetMapping
public ResponseEntity<PageResponse<ResourceResponse>> search(
    @Valid ResourceQuery query,
    @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
) {
    var page = orchestrator.search(query, pageable);
    return ResponseEntity.ok(PageResponse.from(page, ResourceResponse::from));
}
```

## 文件上传模式

```java
@PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<UploadResponse> upload(
    @RequestParam("file") MultipartFile file,
    @RequestParam(value = "type", required = false) String type
) {
    if (file.isEmpty()) {
        throw new IllegalArgumentException("文件不能为空");
    }

    var result = orchestrator.uploadFile(file, type);
    return ResponseEntity.ok(UploadResponse.from(result));
}
```

## 批量操作模式

```java
@PostMapping("/batch")
public ResponseEntity<BatchResponse<ResourceResponse>> batchCreate(
    @Valid @RequestBody @Size(min = 1, max = 100) List<CreateResourceCommand> commands
) {
    var results = orchestrator.batchCreate(commands);
    return ResponseEntity.ok(BatchResponse.from(results, ResourceResponse::from));
}

@DeleteMapping("/batch")
@ResponseStatus(HttpStatus.NO_CONTENT)
public void batchDelete(@RequestParam("ids") Set<Long> ids) {
    if (ids.isEmpty()) {
        throw new IllegalArgumentException("ID 列表不能为空");
    }
    orchestrator.batchDelete(ids);
}
```

## 异步任务模式

```java
@PostMapping("/async-export")
@ResponseStatus(HttpStatus.ACCEPTED)
public ResponseEntity<TaskResponse> asyncExport(@Valid @RequestBody ExportRequest request) {
    var taskId = orchestrator.startExportTask(request);
    return ResponseEntity.accepted()
        .location(URI.create("/api/v1/tasks/" + taskId))
        .body(TaskResponse.of(taskId));
}

@GetMapping("/tasks/{taskId}")
public ResponseEntity<TaskStatusResponse> getTaskStatus(@PathVariable String taskId) {
    return orchestrator.getTaskStatus(taskId)
        .map(TaskStatusResponse::from)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
}
```

## 响应封装模式

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceResponse {
    private Long id;
    private String name;
    private String type;
    private LocalDateTime createdAt;

    public static ResourceResponse from(ResourceResult result) {
        return ResourceResponse.builder()
            .id(result.getId())
            .name(result.getName())
            .type(result.getType())
            .createdAt(result.getCreatedAt())
            .build();
    }
}

@Data
@Builder
public class PageResponse<T> {
    private List<T> content;
    private long totalElements;
    private int totalPages;
    private int size;
    private int number;

    public static <T, R> PageResponse<T> from(Page<R> page, Function<R, T> mapper) {
        return PageResponse.<T>builder()
            .content(page.getContent().stream().map(mapper).toList())
            .totalElements(page.getTotalElements())
            .totalPages(page.getTotalPages())
            .size(page.getSize())
            .number(page.getNumber())
            .build();
    }
}
```

## 异常处理最佳实践

Controller 层不直接处理异常，依赖全局异常处理器：

```java
// ✅ 正确：抛出业务异常，让全局处理器处理
if (!orchestrator.exists(id)) {
    throw new ResourceNotFoundException("资源不存在: " + id);
}

// ❌ 错误：不要在 Controller 层 catch 异常
try {
    return orchestrator.create(command);
} catch (Exception e) {
    // 不要这样做
}
```

## 参数校验模式

```java
@Data
public class CreateResourceCommand {
    @NotBlank(message = "名称不能为空")
    @Size(min = 1, max = 100, message = "名称长度必须在 1-100 之间")
    private String name;

    @NotNull(message = "类型不能为空")
    @Pattern(regexp = "^(TYPE1|TYPE2|TYPE3)$", message = "类型必须是 TYPE1、TYPE2 或 TYPE3")
    private String type;

    @Valid
    @NotNull(message = "配置不能为空")
    private ConfigDTO config;
}

@Data
public class ResourceQuery {
    @Size(max = 50, message = "关键字长度不能超过 50")
    private String keyword;

    private String type;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startTime;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endTime;

    @AssertTrue(message = "开始时间不能晚于结束时间")
    private boolean isValidTimeRange() {
        return startTime == null || endTime == null || !startTime.isAfter(endTime);
    }
}
```

## 常见错误与正确做法

### ❌ 错误：Controller 包含业务逻辑
```java
@PostMapping
public ResponseEntity<ResourceResponse> create(@RequestBody CreateResourceCommand command) {
    // ❌ 错误：业务逻辑应该在 Orchestrator/Service 层
    if (repository.existsByName(command.getName())) {
        throw new DuplicateException("名称已存在");
    }
    var entity = converter.toEntity(command);
    entity.setCreatedAt(LocalDateTime.now());
    repository.save(entity);
    return ResponseEntity.ok(converter.toResponse(entity));
}
```

### ✅ 正确：Controller 只负责协议转换
```java
@PostMapping
public ResponseEntity<ResourceResponse> create(@Valid @RequestBody CreateResourceCommand command) {
    // ✅ 正确：委托给编排层处理
    var result = orchestrator.create(command);
    return ResponseEntity.ok(ResourceResponse.from(result));
}
```

### ❌ 错误：直接返回领域对象
```java
@GetMapping("/{id}")
public Resource getById(@PathVariable Long id) {
    // ❌ 错误：不应该暴露领域对象
    return resourceService.findById(id);
}
```

### ✅ 正确：使用 DTO 响应
```java
@GetMapping("/{id}")
public ResponseEntity<ResourceResponse> getById(@PathVariable Long id) {
    // ✅ 正确：转换为 DTO
    return orchestrator.findById(id)
        .map(ResourceResponse::from)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
}
```