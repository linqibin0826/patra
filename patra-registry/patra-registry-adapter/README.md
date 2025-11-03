# patra-registry-adapter

## 概述

`patra-registry-adapter` 是 patra-registry 服务的**适配器层模块**,负责实现 API 契约接口,将外部 HTTP 请求适配为应用层用例调用。本模块作为六边形架构的入站适配器,处理 REST 端点实现、参数验证、异常处理和响应转换。

在六边形架构中,本模块实现 `patra-registry-api` 模块定义的接口契约,调用 `patra-registry-app` 模块的编排器,将查询 DTO 转换为 API 响应 DTO。

## 核心职责

- **REST 端点实现**: 实现 API 契约接口(`ProvenanceEndpoint`、`ExprEndpoint`)
- **参数验证**: 验证请求参数,处理无效输入
- **对象转换**: 将查询 DTO 转换为 API 响应 DTO(通过 MapStruct Converter)
- **异常映射**: 将领域异常映射为 HTTP 错误响应
- **日志记录**: 记录请求和响应,便于调试和审计

## 模块结构

```
patra-registry-adapter/
└── src/main/java/com/patra/registry/
    ├── adapter/                         # 适配器实现
    │   └── rest/                        # REST 适配器
    │       ├── ProvenanceEndpointImpl.java
    │       ├── ExprEndpointImpl.java
    │       ├── converter/               # API 转换器
    │       │   ├── ProvenanceApiConverter.java
    │       │   └── ExprApiConverter.java
    │       └── package-info.java
    └── config/                          # 配置类
        └── RegistryErrorMappingContributor.java
```

## 主要组件

### ProvenanceEndpointImpl

数据源 API 端点的实现,提供数据源元数据和配置查询服务。

**核心方法**:
- `listProvenances()`: 列出所有数据源
- `getProvenance(ProvenanceCode)`: 根据代码获取单个数据源
- `getConfiguration(ProvenanceCode, String, Instant)`: 加载完整配置聚合

**职责**:
1. 接收 HTTP 请求参数
2. 调用 `ProvenanceConfigOrchestrator` 执行用例
3. 通过 `ProvenanceApiConverter` 转换为 API 响应 DTO
4. 处理异常,抛出 `ProvenanceNotFoundException`

**使用示例**:
```java
@RestController
@RequiredArgsConstructor
public class ProvenanceEndpointImpl implements ProvenanceEndpoint {

  private final ProvenanceConfigOrchestrator orchestrator;
  private final ProvenanceApiConverter converter;

  @Override
  public ProvenanceConfigResp getConfiguration(
      ProvenanceCode code, String operationType, Instant at) {
    log.debug("Received provenance configuration request for code [{}]", code.getCode());

    return orchestrator
        .loadConfiguration(code, operationType, at)
        .map(converter::toResp)
        .orElseThrow(() -> new ProvenanceNotFoundException(...));
  }
}
```

### ExprEndpointImpl

表达式 API 端点的实现,提供表达式快照查询服务。

**核心方法**:
- `getSnapshot(String, String, String, Instant)`: 获取完整表达式快照

**职责**:
1. 接收 HTTP 请求参数
2. 调用 `ExprQueryOrchestrator` 执行用例
3. 通过 `ExprApiConverter` 转换为 API 响应 DTO

**使用示例**:
```java
@RestController
@RequiredArgsConstructor
public class ExprEndpointImpl implements ExprEndpoint {

  private final ExprQueryOrchestrator orchestrator;
  private final ExprApiConverter converter;

  @Override
  public ExprSnapshotResp getSnapshot(
      String provenanceCode, String operationType, String endpointName, Instant at) {
    log.debug("Received expression snapshot request for provenance [{}]", provenanceCode);

    ExprSnapshotQuery snapshot = orchestrator.loadSnapshot(
        provenanceCode, operationType, endpointName, at);
    return converter.toResp(snapshot);
  }
}
```

### ProvenanceApiConverter

MapStruct 转换器,将数据源查询 DTO 转换为 API 响应 DTO。

**转换方法**:
- `toResp(ProvenanceQuery)`: 转换数据源查询对象
- `toResp(List<ProvenanceQuery>)`: 转换数据源查询对象列表
- `toResp(ProvenanceConfigQuery)`: 转换配置聚合查询对象
- `toResp(WindowOffsetQuery)`: 转换时间窗口偏移查询对象
- ... (其他配置维度转换)

**设计要点**:
- 使用 `@Mapper(componentModel = "spring")` 注解
- 字段映射通常是一对一,无需自定义逻辑
- 列表转换自动生成

### ExprApiConverter

MapStruct 转换器,将表达式查询 DTO 转换为 API 响应 DTO。

**转换方法**:
- `toResp(ExprSnapshotQuery)`: 转换表达式快照查询对象
- `toResp(ExprFieldQuery)`: 转换字段查询对象
- `toResp(ExprCapabilityQuery)`: 转换能力查询对象
- ... (其他表达式对象转换)

### RegistryErrorMappingContributor

错误映射贡献者,将领域异常映射为 HTTP 错误响应。

**映射规则**:
- `ProvenanceNotFoundException` → `404 NOT_FOUND`
- `RegistryNotFound` → `404 NOT_FOUND`
- `RegistryConflict` → `409 CONFLICT`
- `RegistryQuotaExceeded` → `429 TOO_MANY_REQUESTS`
- `RegistryRuleViolation` → `400 BAD_REQUEST`

**使用示例**:
```java
@Component
public class RegistryErrorMappingContributor implements ErrorMappingContributor {

  @Override
  public void contribute(Map<Class<? extends Exception>, HttpStatus> mappings) {
    mappings.put(ProvenanceNotFoundException.class, HttpStatus.NOT_FOUND);
    mappings.put(RegistryConflict.class, HttpStatus.CONFLICT);
  }
}
```

## 依赖关系

**上游依赖**:
- `patra-registry-app`: 应用层编排器
- `patra-registry-api`: API 契约接口和响应 DTO
- `patra-spring-boot-starter-web`: Web 配置和异常处理
- `org.mapstruct:mapstruct`: 对象映射框架
- `org.springframework:spring-tx`: 事务和 DAO 异常

**下游消费者**:
- `patra-registry-boot`: 组装本模块并提供 REST 服务

## 设计模式

### 1. 适配器模式

- 适配外部 HTTP 协议到内部应用层调用
- 隔离外部表示和内部领域模型
- 易于替换协议(如 gRPC、GraphQL)

### 2. 转换器模式

- 使用 MapStruct 自动生成转换代码
- 查询 DTO → API 响应 DTO
- 保持转换逻辑的一致性

### 3. 异常映射

- 统一的异常处理机制
- 领域异常 → HTTP 状态码
- 标准化的错误响应格式

## 请求流程

1. **HTTP 请求**: 客户端发送 HTTP 请求到 REST 端点
2. **参数验证**: Spring 验证请求参数(通过 `@Valid`、`@NotNull` 等注解)
3. **端点实现**: `*EndpointImpl` 接收请求,调用编排器
4. **用例编排**: `*Orchestrator` 执行业务用例,返回查询 DTO
5. **对象转换**: `*ApiConverter` 将查询 DTO 转换为 API 响应 DTO
6. **异常处理**: 如果发生异常,`ErrorMappingContributor` 映射为 HTTP 错误响应
7. **HTTP 响应**: 返回 JSON 响应给客户端

## 异常处理

### 全局异常处理器

通过 `patra-spring-boot-starter-web` 提供的全局异常处理器:
- 捕获所有未处理异常
- 映射为标准化的错误响应
- 记录异常日志

### 错误响应格式

```json
{
  "timestamp": "2025-01-12T10:00:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Provenance not found for code [PUBMED]",
  "path": "/_internal/provenances/PUBMED"
}
```

## 使用示例

### 调用 REST API

```bash
# 列出所有数据源
curl -X GET http://localhost:8081/_internal/provenances

# 获取单个数据源
curl -X GET http://localhost:8081/_internal/provenances/PUBMED

# 加载配置聚合(带时态切片)
curl -X GET "http://localhost:8081/_internal/provenances/PUBMED/config?operationType=HARVEST&at=2025-01-12T10:00:00Z"

# 获取表达式快照
curl -X GET "http://localhost:8081/_internal/expr/snapshot?provenanceCode=PUBMED&operationType=HARVEST"
```

### 在其他服务中通过 Feign 客户端调用

```java
@Component
@RequiredArgsConstructor
public class ConfigLoader {

  private final ProvenanceClient provenanceClient;

  public ProvenanceConfigResp loadConfig(ProvenanceCode code) {
    return provenanceClient.getConfiguration(code, "HARVEST", Instant.now());
  }
}
```

## 相关文档

- [patra-registry 顶层文档](../README.md)
- [patra-registry-api 模块](../patra-registry-api/README.md) - API 契约接口
- [patra-registry-app 模块](../patra-registry-app/README.md) - 应用层编排器

---

**最后更新**: 2025-01-12
