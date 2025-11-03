# patra-registry-app

## 概述

`patra-registry-app` 是 patra-registry 服务的**应用层模块**,负责编排用例和协调领域逻辑。本模块作为六边形架构的应用服务层,接收来自适配器层的请求,调用领域层的仓储接口和领域模型,将领域对象转换为查询 DTO 返回给适配器层。

在六边形架构中,本模块处于适配器层和领域层之间,定义了入站端口的实现(用例编排器),协调领域逻辑的执行,不包含业务规则(业务规则在领域层)。

## 核心职责

- **用例编排**: 组织多个领域操作完成业务用例
- **事务边界**: 定义事务边界,确保数据一致性
- **对象转换**: 将领域对象转换为查询 DTO(通过 MapStruct Assembler)
- **日志记录**: 记录关键业务操作和异常
- **参数验证**: 验证输入参数,调用领域仓储接口

## 模块结构

```
patra-registry-app/
└── src/main/java/com/patra/registry/app/
    ├── converter/                          # MapStruct 转换器
    │   ├── ProvenanceQueryAssembler.java   # 数据源对象转换器
    │   └── ExprQueryAssembler.java         # 表达式对象转换器
    └── service/                            # 用例编排器
        ├── ProvenanceConfigOrchestrator.java  # 数据源配置编排器
        └── ExprQueryOrchestrator.java         # 表达式查询编排器
```

## 主要组件

### ProvenanceConfigOrchestrator

数据源配置查询用例的编排器,协调数据源元数据和配置的查询操作。

**核心方法**:
- `listProvenances()`: 列出所有数据源
- `findProvenance(ProvenanceCode)`: 查询单个数据源
- `loadConfiguration(ProvenanceCode, String, Instant)`: 加载完整配置聚合

**职责**:
1. 调用 `ProvenanceConfigRepository` 查询领域对象
2. 通过 `ProvenanceQueryAssembler` 转换为查询 DTO
3. 记录查询日志

**使用示例**:
```java
@Service
@RequiredArgsConstructor
public class ProvenanceConfigOrchestrator {

  private final ProvenanceConfigRepository repository;
  private final ProvenanceQueryAssembler assembler;

  public Optional<ProvenanceConfigQuery> loadConfiguration(
      ProvenanceCode provenanceCode, String operationType, Instant at) {
    Instant effectiveTime = at != null ? at : Instant.now();

    return repository
        .findProvenanceByCode(provenanceCode)
        .flatMap(provenance ->
            repository.loadConfiguration(provenance.id(), operationType, effectiveTime)
                .map(assembler::toQuery));
  }
}
```

### ExprQueryOrchestrator

表达式配置查询用例的编排器,协调表达式元数据的查询操作。

**核心方法**:
- `loadSnapshot(String, String, String, Instant)`: 加载完整表达式快照

**职责**:
1. 解析数据源代码(`ProvenanceCode.parse`)
2. 调用 `ExprRepository` 查询表达式快照
3. 通过 `ExprQueryAssembler` 转换为查询 DTO
4. 记录快照统计信息(字段数、能力数、规则数)

**使用示例**:
```java
@Service
@RequiredArgsConstructor
public class ExprQueryOrchestrator {

  private final ExprRepository exprRepository;
  private final ExprQueryAssembler assembler;

  public ExprSnapshotQuery loadSnapshot(
      String provenanceCode, String operationType, String endpointName, Instant at) {
    ProvenanceCode code = ProvenanceCode.parse(provenanceCode);
    ExprSnapshot domainSnapshot = exprRepository.loadSnapshot(code, operationType, endpointName, at);
    return assembler.toQuery(domainSnapshot);
  }
}
```

### ProvenanceQueryAssembler

MapStruct 转换器,将数据源领域对象转换为查询 DTO。

**转换方法**:
- `toQuery(Provenance)`: 转换数据源值对象
- `toQuery(WindowOffsetConfig)`: 转换时间窗口偏移配置
- `toQuery(PaginationConfig)`: 转换分页配置
- `toQuery(HttpConfig)`: 转换 HTTP 配置
- `toQuery(BatchingConfig)`: 转换批处理配置
- `toQuery(RetryConfig)`: 转换重试配置
- `toQuery(RateLimitConfig)`: 转换速率限制配置
- `toQuery(ProvenanceConfiguration)`: 转换配置聚合根

**设计模式**: 使用 MapStruct 自动生成映射代码,避免手写样板代码

### ExprQueryAssembler

MapStruct 转换器,将表达式领域对象转换为查询 DTO。

**转换方法**:
- `toQuery(ExprSnapshot)`: 转换表达式快照
- `toQuery(ExprField)`: 转换字段定义
- `toQuery(ExprCapability)`: 转换能力定义
- `toQuery(ApiParamMapping)`: 转换参数映射
- `toQuery(ExprRenderRule)`: 转换渲染规则

## 依赖关系

**上游依赖**:
- `patra-registry-domain`: 领域模型、仓储接口、查询对象
- `patra-registry-api`: API 契约(查询 DTO)
- `patra-spring-boot-starter-core`: Spring 核心配置
- `org.mapstruct:mapstruct`: 对象映射框架
- `org.hibernate.validator:hibernate-validator`: 参数验证

**下游消费者**:
- `patra-registry-adapter`: 调用编排器实现 REST 端点

## 设计模式

### 1. 编排器模式

编排器负责协调多个领域操作,不包含业务逻辑:
- 业务规则在领域层(`domain`)
- 编排器仅负责调用顺序和对象转换
- 事务边界在编排器方法上(通过 `@Transactional`)

### 2. CQRS 模式

查询和命令分离:
- 查询操作返回只读 DTO(`*Query`)
- 查询 DTO 与领域对象隔离,避免暴露内部实现

### 3. 依赖倒置

- 编排器依赖领域层的仓储接口(抽象)
- 不依赖基础设施层的具体实现
- 易于单元测试(Mock 仓储接口)

## 使用示例

### 在适配器层调用编排器

```java
@RestController
@RequiredArgsConstructor
public class ProvenanceEndpointImpl implements ProvenanceEndpoint {

  private final ProvenanceConfigOrchestrator orchestrator;
  private final ProvenanceApiConverter converter;

  @Override
  public ProvenanceConfigResp getConfiguration(
      ProvenanceCode code, String operationType, Instant at) {
    return orchestrator
        .loadConfiguration(code, operationType, at)
        .map(converter::toResp)
        .orElseThrow(() -> new ProvenanceNotFoundException(...));
  }
}
```

### 单元测试编排器

```java
@ExtendWith(MockitoExtension.class)
class ProvenanceConfigOrchestratorTest {

  @Mock
  private ProvenanceConfigRepository repository;

  @Mock
  private ProvenanceQueryAssembler assembler;

  @InjectMocks
  private ProvenanceConfigOrchestrator orchestrator;

  @Test
  void shouldLoadConfiguration() {
    // Given
    Provenance provenance = new Provenance(...);
    ProvenanceConfiguration config = new ProvenanceConfiguration(...);
    when(repository.findProvenanceByCode(any())).thenReturn(Optional.of(provenance));
    when(repository.loadConfiguration(any(), any(), any())).thenReturn(Optional.of(config));

    // When
    Optional<ProvenanceConfigQuery> result = orchestrator.loadConfiguration(
        ProvenanceCode.PUBMED, "HARVEST", Instant.now());

    // Then
    assertTrue(result.isPresent());
    verify(repository).findProvenanceByCode(ProvenanceCode.PUBMED);
  }
}
```

## 相关文档

- [patra-registry 顶层文档](../README.md)
- [patra-registry-domain 模块](../patra-registry-domain/README.md) - 领域模型和仓储接口
- [patra-registry-adapter 模块](../patra-registry-adapter/README.md) - 编排器的调用方

---

**最后更新**: 2025-01-12
