# 六边形架构与整洁架构

本文档基于 Alistair Cockburn 的六边形架构（端口与适配器）和 Robert C. Martin 的整洁架构，结合 Patra 项目实践总结。

## 核心原则

### 依赖倒置原则

**核心规则**：内层不依赖外层，外层依赖内层。

```
外部世界
    ↓
[适配器层 - Adapter]
    ↓
[基础设施层 - Infrastructure]
    ↓
[应用层 - Application]
    ↓
[领域层 - Domain] ← 核心，不依赖任何外层
```

### 端口与适配器

- **端口 (Port)**：定义在内层的接口，描述内层需要什么能力
- **适配器 (Adapter)**：外层实现端口接口，连接外部系统

```
[外部系统] ←→ [适配器] ←→ [端口] ←→ [领域核心]
```

## Patra 分层架构

### 层级职责

| 层级 | 模块 | 职责 | 依赖 |
|------|------|------|------|
| Domain | `*-domain` | 领域模型、业务规则 | 无（纯 Java） |
| Application | `*-app` | 用例编排、事务管理 | Domain |
| Infrastructure | `*-infra` | 端口实现、技术细节 | Domain, Application |
| Adapter | `*-adapter` | 协议转换、入口适配 | Application |
| API | `*-api` | 服务契约、DTO 定义 | 无 |
| Boot | `*-boot` | 启动配置、依赖组装 | 全部 |

### 依赖规则

```
Boot ─────────────────────────────────┐
  │                                   │
  ├─→ Adapter ─→ Application ─→ Domain
  │       │           │           ↑
  │       │           │           │
  │       └───→ Infrastructure ───┘
  │                   │
  └─→ API ←───────────┘
```

## Domain 层设计

### 核心约束

1. **纯 Java**：禁止 Spring、JPA 等框架注解
2. **无外部依赖**：只能依赖 `patra-common-core`
3. **业务规则内聚**：所有不变量在领域对象内验证

### 包结构

```
com.patra.{service}.domain/
├── model/
│   ├── aggregate/     # 聚合根
│   ├── entity/        # 聚合内实体
│   ├── vo/            # 值对象
│   └── enums/         # 领域枚举
├── port/
│   └── repository/    # 仓储接口（出站端口）
├── service/           # 领域服务
└── event/             # 领域事件
```

### 端口定义

```java
// Domain 层定义仓储接口
public interface VenueRepository {
    void save(VenueAggregate venue);
    Optional<VenueAggregate> findById(Long id);
    Optional<VenueAggregate> findByIssnL(String issnL);
}

// Domain 层定义外部服务端口
public interface MeshParserPort {
    MeshDescriptorAggregate parse(InputStream xmlStream);
}
```

## Application 层设计

### 核心职责

1. **用例编排**：协调领域对象完成业务流程
2. **事务管理**：唯一的 `@Transactional` 位置
3. **端口调用**：通过端口与外部系统交互

### 命名约定

| 类型 | 命名 | 说明 |
|------|------|------|
| 命令 | `{Action}{Entity}Command` | 命令对象，含验证 |
| 处理器 | `{Action}{Entity}Handler` | CommandHandler 实现 |
| 结果 | `{Action}{Entity}Result` | 结果对象 |

### 示例

```java
// 命令对象（实现 Command 接口）
public record ImportVenueCommand(
    String issnL,
    String title
) implements Command<ImportVenueResult> {
    public ImportVenueCommand {
        Objects.requireNonNull(issnL, "issnL must not be null");
    }
}

// CommandHandler 实现
@Component
@RequiredArgsConstructor
public class ImportVenueHandler implements CommandHandler<ImportVenueCommand, ImportVenueResult> {

    private final VenueRepository venueRepository;
    private final VenueDeduplicationService deduplicationService;

    @Override
    @Transactional
    public ImportVenueResult handle(ImportVenueCommand command) {
        // 1. 检查重复
        Optional<VenueAggregate> existing = deduplicationService.findDuplicate(command);

        // 2. 创建或更新
        VenueAggregate venue = existing
            .map(v -> v.mergeWith(command))
            .orElseGet(() -> VenueAggregate.fromCommand(command));

        // 3. 保存
        venueRepository.save(venue);

        return ImportVenueResult.of(venue);
    }
}
```

## Infrastructure 层设计

### 核心职责

1. **实现端口**：仓储、外部服务适配
2. **技术细节**：ORM 映射、HTTP 客户端、消息队列

### 适配器命名

| 端口类型 | 适配器命名 | 位置 |
|----------|-----------|------|
| `{Entity}Repository` | `{Entity}RepositoryAdapter` | `adapter/persistence/` |
| `{Function}Port` | `{Function}Adapter` | `adapter/{function}/` |
| `{Service}Client` | `{Service}ClientAdapter` | 原位置 |

### 示例

```java
// 仓储适配器
@Repository
@RequiredArgsConstructor
public class VenueRepositoryAdapter implements VenueRepository {

    private final VenueMapper venueMapper;
    private final VenueAssembler assembler;

    @Override
    public void save(VenueAggregate venue) {
        VenueDO venueDO = assembler.toDO(venue);
        if (venue.getId() == null) {
            venueMapper.insert(venueDO);
            // 回写 ID
            venue.assignId(venueDO.getId());
        } else {
            venueMapper.updateById(venueDO);
        }
    }

    @Override
    public Optional<VenueAggregate> findById(Long id) {
        return Optional.ofNullable(venueMapper.selectById(id))
            .map(assembler::toAggregate);
    }
}
```

## Adapter 层设计

### 核心职责

1. **协议转换**：HTTP → Command，Result → Response
2. **入口适配**：Controller、Job、EventListener
3. **验证触发**：触发 Command 的 Bean Validation

### 约束

- ❌ 禁止业务逻辑
- ❌ 禁止直接调用 Repository
- ❌ 禁止调用 Domain 层
- ❌ 禁止直接注入 Handler
- ✅ 通过 CommandBus 调度命令

### 示例

```java
@RestController
@RequestMapping("/api/v1/venues")
@RequiredArgsConstructor
public class VenueController {

    private final CommandBus commandBus;  // 唯一注入

    @PostMapping
    public ResponseEntity<VenueResponse> importVenue(
            @Valid @RequestBody VenueRequest request) {
        // 1. 转换为 Command
        var command = new ImportVenueCommand(
            request.issnL(),
            request.title()
        );

        // 2. 通过 CommandBus 调度
        ImportVenueResult result = commandBus.handle(command);

        // 3. 转换为 Response
        return ResponseEntity.ok(VenueAssembler.toResponse(result));
    }
}
```

## DDD 与六边形架构结合

### 映射关系

| DDD 概念 | 六边形架构位置 |
|----------|---------------|
| 聚合根、实体、值对象 | Domain 层 `model/` |
| 仓储接口 | Domain 层 `port/repository/` |
| 仓储实现 | Infrastructure 层 `adapter/persistence/` |
| 领域服务 | Domain 层 `service/` |
| 应用服务 | Application 层 `*Handler`（CommandHandler） |
| 领域事件 | Domain 层 `event/` |

### 关键设计决策

1. **Repository 接口在 Domain**：领域层定义需要的持久化能力
2. **Repository 实现在 Infrastructure**：技术细节与领域解耦
3. **事务在 Application**：领域层无感知事务
4. **Assembler 在各层**：DO/DTO/Entity 转换各层独立

## 常见问题

### Q: 领域服务需要调用仓储怎么办？

**A**: 通过构造函数注入仓储接口。领域服务仍然是纯 Java，只依赖接口。

```java
public class VenueDeduplicationService {
    private final VenueRepository venueRepository;

    public VenueDeduplicationService(VenueRepository venueRepository) {
        this.venueRepository = venueRepository;
    }

    // ✅ 可以：查询同类型聚合用于去重判断
    public Optional<VenueAggregate> findDuplicate(String issnL) {
        return venueRepository.findByIssnL(issnL);
    }
}
```

> ⚠️ **注意**：领域服务可以注入**单个仓储**执行单一职责的业务逻辑（如去重查询）。但**跨聚合协调**（读取多个不同类型的聚合）应该在 Application 层编排。

### Q: 聚合需要查询其他聚合怎么办？

**A**: 在 Application 层编排，不在 Domain 层直接查询。

```java
// Application 层编排（CommandHandler）
@Component
public class ProcessPublicationHandler implements CommandHandler<ProcessPublicationCommand, Void> {

    @Override
    @Transactional
    public Void handle(ProcessPublicationCommand cmd) {
        Publication pub = publicationRepository.findById(cmd.pubId());
        Venue venue = venueRepository.findById(pub.getVenueId());
        // 编排逻辑
        return null;
    }
}
```

### Q: 如何处理跨服务调用？

**A**: 定义 Client 接口在 Domain 或 Application 层，实现在 Infrastructure 层。

```java
// Domain 层定义
public interface RegistryClient {
    ProvenanceConfig getProvenance(String code);
}

// Infrastructure 层实现（HTTP Interface）
@HttpExchange(url = "/api/v1/provenances")
public interface RegistryEndpoint extends RegistryClient {
    @GetExchange("/{code}")
    ProvenanceConfig getProvenance(@PathVariable String code);
}
```
