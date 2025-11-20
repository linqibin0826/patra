---
name: java-spring-development
description: |
  Spring Boot 微服务开发专家，提供技术实现模式和最佳实践。

  **自动激活场景**：
  - 创建 REST Controller（@RestController、@RequestMapping）
  - 实现 Orchestrator/Coordinator（@Service、@Transactional）
  - 开发 Repository（MyBatis-Plus、@Mapper）
  - 配置 MapStruct 转换器
  - 创建 XXL-Job 定时任务（@XxlJob）
  - 配置 Nacos 动态配置（@RefreshScope）

  **触发关键词**：Spring Boot、Controller、Orchestrator、Coordinator、
  Repository、MyBatis-Plus、MapStruct、XXL-Job、Nacos、@Transactional、
  @Service、@RestController、@Mapper、REST API、定时任务、事务管理。

  **技术栈**：Spring Boot 3.5.7、MyBatis-Plus 3.5.x、MapStruct 1.5.x、XXL-Job 2.4.x。
allowed-tools: Read, Edit, Write, Grep, Glob, Bash, mcp__serena__get_symbols_overview, mcp__serena__find_symbol, mcp__serena__replace_symbol_body, mcp__serena__rename_symbol, mcp__context7__resolve-library-id, mcp__context7__get-library-docs, mcp__ide__getDiagnostics
---

# Spring Boot 微服务开发专家

## 快速开发指南

### Controller 开发模式

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
}
```

### Orchestrator 编排模式

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class ResourceOrchestrator {
    private final ResourcePort resourcePort;
    private final EventPublisher eventPublisher;

    @Transactional  // 事务边界
    public ResourceResult create(CreateResourceCommand command) {
        // 1. 组装领域对象
        var resource = Resource.create(
            command.getName(),
            command.getType()
        );

        // 2. 业务逻辑处理
        resource.validate();

        // 3. 持久化
        resourcePort.save(resource);

        // 4. 发布领域事件
        eventPublisher.publish(new ResourceCreatedEvent(resource));

        return ResourceResult.from(resource);
    }

    @Transactional(readOnly = true)
    public Optional<ResourceResult> findById(Long id) {
        return resourcePort.findById(id)
            .map(ResourceResult::from);
    }
}
```

### MyBatis-Plus 数据访问

```java
// DO 实体
@TableName("t_resource")
@Data
public class ResourceDO {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("resource_name")
    private String name;

    @TableField("resource_type")
    private String type;

    @Version  // 乐观锁
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

// Mapper 接口
@Mapper
public interface ResourceMapper extends BaseMapper<ResourceDO> {
    // MyBatis-Plus 提供基础 CRUD

    // 自定义复杂查询
    @Select("""
        SELECT * FROM t_resource
        WHERE type = #{type}
        AND create_time >= #{startTime}
        ORDER BY create_time DESC
        """)
    List<ResourceDO> findByTypeAfter(@Param("type") String type,
                                      @Param("startTime") LocalDateTime startTime);
}

// Repository 实现
@Repository
@RequiredArgsConstructor
public class ResourceRepositoryImpl implements ResourcePort {
    private final ResourceMapper mapper;
    private final ResourceConverter converter;

    @Override
    public void save(Resource resource) {
        ResourceDO dataObject = converter.toDO(resource);
        if (dataObject.getId() == null) {
            mapper.insert(dataObject);
        } else {
            mapper.updateById(dataObject);
        }
        // 回写 ID
        resource.setId(dataObject.getId());
    }

    @Override
    public Optional<Resource> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id))
            .map(converter::toDomain);
    }
}
```

### MapStruct 对象转换

```java
@Mapper(componentModel = "spring", imports = {LocalDateTime.class})
public interface ResourceConverter {

    ResourceDO toDO(Resource domain);

    Resource toDomain(ResourceDO dataObject);

    @Mapping(target = "id", ignore = true)
    void updateDO(@MappingTarget ResourceDO target, Resource source);
}
```

### XXL-Job 定时任务

```java
@Component
@Slf4j
public class ResourceSyncJob {
    private final ResourceSyncOrchestrator orchestrator;

    @XxlJob("resourceDailySync")
    public void dailySync() {
        XxlJobHelper.log("开始执行资源同步任务");

        try {
            var result = orchestrator.syncResources();
            XxlJobHelper.log("同步完成，处理记录数：{}", result.getProcessedCount());

            // 设置任务执行结果
            XxlJobHelper.handleSuccess("同步成功：" + result.getProcessedCount());
        } catch (Exception e) {
            XxlJobHelper.log("同步失败：{}", e.getMessage());
            XxlJobHelper.handleFail("同步失败：" + e.getMessage());
        }
    }

    @XxlJob("resourceBatchProcess")
    public void batchProcess() {
        // 分片处理
        int shardIndex = XxlJobHelper.getShardIndex();
        int shardTotal = XxlJobHelper.getShardTotal();

        XxlJobHelper.log("分片执行：{}/{}", shardIndex, shardTotal);
        orchestrator.processBatch(shardIndex, shardTotal);
    }
}
```

### 事务管理最佳实践

```java
@Service
@RequiredArgsConstructor
public class TransactionalOrchestrator {

    @Transactional(rollbackFor = Exception.class)
    public void process() {
        // 默认事务配置
    }

    @Transactional(
        propagation = Propagation.REQUIRES_NEW,
        isolation = Isolation.READ_COMMITTED,
        timeout = 30
    )
    public void processWithNewTransaction() {
        // 独立事务
    }

    @Transactional(readOnly = true)
    public List<Resource> query() {
        // 只读事务，性能优化
    }

    @Retryable(
        value = {OptimisticLockingFailureException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 100)
    )
    @Transactional
    public void processWithRetry() {
        // 乐观锁重试
    }
}
```

### 错误处理模式

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException e) {
        var problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setTitle("业务错误");
        problemDetail.setDetail(e.getMessage());
        problemDetail.setProperty("errorCode", e.getErrorCode());
        return problemDetail;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException e) {
        var problemDetail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problemDetail.setTitle("资源未找到");
        problemDetail.setDetail(e.getMessage());
        return problemDetail;
    }
}
```

## 配置管理

### Nacos 配置中心

```yaml
spring:
  cloud:
    nacos:
      config:
        server-addr: ${NACOS_SERVER:localhost:8848}
        namespace: ${NACOS_NAMESPACE:dev}
        group: ${NACOS_GROUP:DEFAULT_GROUP}
        file-extension: yml
        shared-configs:
          - data-id: common.yml
            refresh: true
          - data-id: database.yml
            refresh: true
```

### 动态配置刷新

```java
@Component
@RefreshScope
@ConfigurationProperties(prefix = "app.feature")
@Data
public class FeatureConfig {
    private boolean enableNewFeature;
    private Integer batchSize;
    private String apiEndpoint;
}
```

## 开发规范

### 命名约定

| 类型 | 后缀 | 示例 |
|-----|------|------|
| REST 控制器 | Controller | ResourceController |
| 编排者 | Orchestrator | ResourceOrchestrator |
| 协调者 | Coordinator | NotificationCoordinator |
| 数据对象 | DO | ResourceDO |
| Mapper | Mapper | ResourceMapper |
| 转换器 | Converter | ResourceConverter |

### 包结构组织

```
com.patra.{service}
├── adapter
│   ├── controller    # REST 控制器
│   ├── job          # 定时任务
│   └── listener     # 消息监听器
├── app
│   ├── orchestrator # 编排器
│   └── coordinator  # 协调器
├── infra
│   ├── repository   # 仓储实现
│   ├── mapper       # MyBatis Mapper
│   ├── converter    # MapStruct 转换器
│   └── config       # 配置类
```

## 性能优化建议

1. **批量操作**：使用 MyBatis-Plus 的 `saveBatch`、`updateBatchById`
2. **查询优化**：合理使用索引，避免 N+1 查询
3. **缓存策略**：使用 Spring Cache + Redis
4. **异步处理**：使用 @Async 或消息队列
5. **连接池调优**：HikariCP 配置优化

## 详细资源

需要深入了解时，查看以下资源文件：

- [adapter-layer-patterns.md](resources/adapter-layer-patterns.md) - REST/Job/Consumer 模式
- [orchestrator-coordinator-patterns.md](resources/orchestrator-coordinator-patterns.md) - 编排模式
- [mybatis-plus-patterns.md](resources/mybatis-plus-patterns.md) - 数据访问模式
- [transaction-error-handling.md](resources/transaction-error-handling.md) - 事务与错误处理
- [event-driven-architecture.md](resources/event-driven-architecture.md) - 事件驱动架构
- [outbox-pattern.md](resources/outbox-pattern.md) - 发件箱模式
