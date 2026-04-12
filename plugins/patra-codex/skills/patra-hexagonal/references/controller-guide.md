# Controller 与 Adapter 层开发指南

## 目录

- REST Controller 模板
- MapStruct ApiConverter 模板
- 异常处理策略
- XXL-Job 定时任务模板
- 常见错误对比

## REST Controller 模板

### 混合 Controller（读 + 写）

```java
@Slf4j
@RestController
@RequestMapping("/api/v1/resources")
@RequiredArgsConstructor
@Validated
public class ResourceController {

    private final CommandBus commandBus;              // 写操作
    private final ResourceQueryService queryService;  // 读操作
    private final ResourceApiConverter converter;     // MapStruct 转换器

    /// 创建资源。
    @PostMapping
    public ResourceResponse create(@Valid @RequestBody CreateResourceRequest request) {
        log.info("收到创建资源请求，名称：{}", request.name());
        CreateResourceCommand command = converter.toCommand(request);
        ResourceResult result = commandBus.handle(command);
        return converter.toResponse(result);
    }

    /// 查询资源详情。
    @GetMapping("/{id}")
    public ResourceResponse getById(@PathVariable @NotNull Long id) {
        ResourceQuery result = queryService.findById(id);
        return converter.toResponse(result);
    }
}
```

### 纯查询 Controller

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

**要点**：不注入 CommandBus，使用 `PageResult.map()` 跨层转换。

### Controller 子包结构

```
adapter/rest/{entity}/
├── {Entity}Controller.java
├── request/{Entity}ListRequest.java      # 查询参数 DTO
├── response/{Entity}ItemResponse.java    # 列表项响应 DTO
└── mapper/{Entity}ApiConverter.java      # MapStruct 转换器
```

## MapStruct ApiConverter 模板

```java
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ResourceApiConverter {

    /// 将创建请求转换为创建命令（反腐层）。
    CreateResourceCommand toCommand(CreateResourceRequest request);

    /// 将更新请求转换为更新命令。
    @Mapping(target = "id", source = "id")
    UpdateResourceCommand toCommand(Long id, UpdateResourceRequest request);

    /// 将应用层结果转换为响应 DTO。
    ResourceResponse toResponse(ResourceResult result);

    /// 将查询请求转换为查询参数（读端）。
    ResourceListQuery toQuery(ResourceListRequest request);

    /// 将读模型转换为列表项响应（读端）。
    ResourceItemResponse toItemResponse(ResourceSummaryReadModel readModel);
}
```

## 异常处理策略

Controller 不捕获异常，由全局处理器统一处理：

```
DomainException(NOT_FOUND)        → 404
DomainException(CONFLICT)         → 409
DomainException(RULE_VIOLATION)   → 422
ApplicationException              → 由 ErrorCodeLike.httpStatus() 映射
MethodArgumentNotValidException   → 400
未知异常                           → 500
```

## XXL-Job 定时任务模板

```java
@Slf4j
@Component
public class PubmedHarvestJob extends AbstractProvenanceScheduleJob {

    @Override
    protected ProvenanceCode getProvenanceCode() {
        return ProvenanceCode.PUBMED;
    }

    @Override
    protected OperationCode getOperationCode() {
        return OperationCode.HARVEST;
    }

    @XxlJob("pubmedHarvest")
    public void run() {
        executeScheduleJob(XxlJobHelper.getJobParam());
    }
}
```

**基类 `AbstractProvenanceScheduleJob`** 提供：解析参数 → 调用 CommandBus → 报告结果的通用流程。

### XXL-Job 错误处理

```java
// 在基类或自定义 Job 中：
try {
    RelayReport report = commandBus.handle(command);
    XxlJobHelper.handleSuccess("成功: " + report.count());
} catch (Exception ex) {
    log.error("作业失败", ex);
    XxlJobHelper.handleFail("失败: " + ex.getMessage());
    // 不再抛出异常，handleFail 已标记任务失败
}
```

## 常见错误对比

| 错误 | 正确 |
|------|------|
| Controller 包含业务逻辑 | 委托给 CommandBus / QueryService |
| 直接传递 Request 给 App 层 | 通过 ApiConverter 转换为 Command |
| 使用 `ResponseEntity` 包装 | 直接返回业务数据 |
| 在 Controller 中 try-catch | 让全局异常处理器处理 |
| 返回领域对象 | 返回 Response DTO |
| Job 中直接访问 Repository | 通过 CommandBus 调用 Handler |
